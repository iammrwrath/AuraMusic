/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.video

import android.content.Context
import android.net.ConnectivityManager
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.metrolist.innertube.YouTube
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.InnerTubeXPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import kotlin.math.abs

@OptIn(UnstableApi::class)
class VideoPlayerManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val playerProvider: () -> ExoPlayer?,
    private val database: MusicDatabase,
    private val connectivityManager: ConnectivityManager,
    private val audioQualityProvider: () -> AudioQuality,
) {
    companion object {
        private const val TAG = "VideoPlayerManager"
        private const val DRIFT_SYNC_INTERVAL_MS = 500L
        private const val DRIFT_TOLERANCE_MS = 150L
    }

    private val _isVideoMode = MutableStateFlow(false)
    val isVideoMode: StateFlow<Boolean> = _isVideoMode.asStateFlow()

    private val _isVideoAvailable = MutableStateFlow(true)
    val isVideoAvailable: StateFlow<Boolean> = _isVideoAvailable.asStateFlow()

    private val _isVideoLoading = MutableStateFlow(false)
    val isVideoLoading: StateFlow<Boolean> = _isVideoLoading.asStateFlow()

    private val _videoError = MutableStateFlow<String?>(null)
    val videoError: StateFlow<String?> = _videoError.asStateFlow()

    private val _videoPlayer = MutableStateFlow<ExoPlayer?>(null)
    val videoPlayer: StateFlow<ExoPlayer?> = _videoPlayer.asStateFlow()

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    private val _areControlsVisible = MutableStateFlow(true)
    val areControlsVisible: StateFlow<Boolean> = _areControlsVisible.asStateFlow()

    private val videoStreamCache = LruCache<String, InnerTubeXPlayer.PlaybackData>(30)

    private var currentMediaId: String? = null
    private var streamLoadJob: Job? = null
    private var syncJob: Job? = null
    private var boundMainPlayer: ExoPlayer? = null

    private val mainPlayerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (_isVideoMode.value) {
                _videoPlayer.value?.playWhenReady = isPlaying
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (_isVideoMode.value) {
                _videoPlayer.value?.seekTo(newPosition.positionMs)
            }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            if (_isVideoMode.value) {
                _videoPlayer.value?.playbackParameters = playbackParameters
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val newId = mediaItem?.mediaId
            if (newId != currentMediaId) {
                currentMediaId = newId
                if (_isVideoMode.value && newId != null) {
                    loadAndPlayVideo(newId)
                } else if (newId == null) {
                    _videoPlayer.value?.stop()
                }
            }
        }
    }

    init {
        attachMainPlayerListener()
    }

    private fun attachMainPlayerListener() {
        val player = playerProvider()
        if (player != null && player != boundMainPlayer) {
            boundMainPlayer?.removeListener(mainPlayerListener)
            boundMainPlayer = player
            player.addListener(mainPlayerListener)
            currentMediaId = player.currentMediaItem?.mediaId
        }
    }

    fun setVideoMode(enabled: Boolean) {
        if (_isVideoMode.value == enabled) return
        _isVideoMode.value = enabled
        attachMainPlayerListener()

        if (enabled) {
            val mainPlayer = playerProvider()
            val mediaId = mainPlayer?.currentMediaItem?.mediaId ?: currentMediaId
            if (mediaId != null) {
                loadAndPlayVideo(mediaId)
            }
            startDriftSync()
        } else {
            stopDriftSync()
            _videoPlayer.value?.stop()
            _isVideoLoading.value = false
        }
    }

    fun toggleVideoMode() {
        setVideoMode(!_isVideoMode.value)
    }

    fun setFullscreen(fullscreen: Boolean) {
        _isFullscreen.value = fullscreen
    }

    fun toggleFullscreen() {
        _isFullscreen.value = !_isFullscreen.value
    }

    fun setControlsVisible(visible: Boolean) {
        _areControlsVisible.value = visible
    }

    fun toggleControlsVisible() {
        _areControlsVisible.value = !_areControlsVisible.value
    }

    fun loadAndPlayVideo(mediaId: String) {
        attachMainPlayerListener()
        val mainPlayer = playerProvider()
        currentMediaId = mediaId
        _videoError.value = null

        val cached = videoStreamCache[mediaId]
        if (cached?.videoUrl != null) {
            Timber.tag(TAG).d("Playing cached video stream for $mediaId")
            _isVideoAvailable.value = true
            setupExoPlayerWithStream(cached, mainPlayer?.currentPosition ?: 0L, mainPlayer?.isPlaying ?: false)
            return
        }

        streamLoadJob?.cancel()
        _isVideoLoading.value = true

        streamLoadJob = scope.launch(Dispatchers.IO) {
            val song = database.songEntity(mediaId)
            val audioQuality = audioQualityProvider()
            val result = InnerTubeXPlayer.videoStreamForPlayback(
                videoId = mediaId,
                audioQuality = audioQuality,
                connectivityManager = connectivityManager,
                contentHints = ContentHints(
                    isExplicit = song?.explicit,
                    isUploaded = song?.isUploaded,
                ),
            )

            withContext(Dispatchers.Main) {
                _isVideoLoading.value = false
                result.fold(
                    onSuccess = { data ->
                        if (data.videoUrl != null) {
                            Timber.tag(TAG).i("Successfully resolved video stream for $mediaId: itag=${data.videoItag} ${data.videoWidth}x${data.videoHeight}")
                            videoStreamCache.put(mediaId, data)
                            _isVideoAvailable.value = true
                            val currentPos = playerProvider()?.currentPosition ?: 0L
                            val isPlaying = playerProvider()?.isPlaying ?: false
                            setupExoPlayerWithStream(data, currentPos, isPlaying)
                        } else {
                            Timber.tag(TAG).w("No video stream returned for $mediaId")
                            _isVideoAvailable.value = false
                            _videoError.value = "Video not available"
                        }
                    },
                    onFailure = { error ->
                        Timber.tag(TAG).e(error, "Failed to resolve video stream for $mediaId")
                        _isVideoAvailable.value = false
                        _videoError.value = error.message ?: "Failed to load video"
                    }
                )
            }
        }
    }

    private fun setupExoPlayerWithStream(data: InnerTubeXPlayer.PlaybackData, startPositionMs: Long, isPlaying: Boolean) {
        val videoUrl = data.videoUrl ?: return
        var player = _videoPlayer.value

        if (player == null) {
            val okHttpClient = OkHttpClient.Builder()
                .proxy(YouTube.proxy)
                .apply {
                    YouTube.proxyAuth?.let { auth ->
                        proxyAuthenticator { _, response ->
                            response.request.newBuilder()
                                .header("Proxy-Authorization", auth)
                                .build()
                        }
                    }
                }.build()

            val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
                .setDefaultRequestProperties(data.streamHeaders)

            val renderersFactory = DefaultRenderersFactory(context)

            player = ExoPlayer.Builder(context, renderersFactory)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build()
                .apply {
                    volume = 0f // Audio is played through main player
                    playWhenReady = isPlaying
                }

            _videoPlayer.value = player
        }

        val mediaItem = MediaItem.Builder()
            .setUri(videoUrl)
            .build()

        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.playbackParameters = playerProvider()?.playbackParameters ?: PlaybackParameters.DEFAULT
        player.playWhenReady = isPlaying
    }

    private fun startDriftSync() {
        syncJob?.cancel()
        syncJob = scope.launch(Dispatchers.Main) {
            while (isActive && _isVideoMode.value) {
                delay(DRIFT_SYNC_INTERVAL_MS)
                val main = playerProvider() ?: continue
                val video = _videoPlayer.value ?: continue

                if (main.isPlaying && video.playbackState == Player.STATE_READY) {
                    val drift = main.currentPosition - video.currentPosition
                    if (abs(drift) > DRIFT_TOLERANCE_MS) {
                        Timber.tag(TAG).d("Correcting video drift: ${drift}ms")
                        video.seekTo(main.currentPosition)
                    }
                }
            }
        }
    }

    private fun stopDriftSync() {
        syncJob?.cancel()
        syncJob = null
    }

    fun onAppForegrounded() {
        attachMainPlayerListener()
        if (_isVideoMode.value) {
            val mainPlayer = playerProvider()
            val video = _videoPlayer.value
            if (mainPlayer != null && video != null) {
                video.seekTo(mainPlayer.currentPosition)
                video.playWhenReady = mainPlayer.isPlaying
                startDriftSync()
            }
        }
    }

    fun onAppBackgrounded() {
        stopDriftSync()
        _videoPlayer.value?.pause()
    }

    fun release() {
        stopDriftSync()
        streamLoadJob?.cancel()
        boundMainPlayer?.removeListener(mainPlayerListener)
        boundMainPlayer = null
        _videoPlayer.value?.release()
        _videoPlayer.value = null
        videoStreamCache.evictAll()
    }
}
