/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.video

import android.content.Context
import android.net.ConnectivityManager
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.InnerTubeXPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber

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
    private val songToMusicVideoCache = LruCache<String, String>(100)

    private var currentMediaId: String? = null
    private var streamLoadJob: Job? = null
    private var boundMainPlayer: ExoPlayer? = null
    private var isPlaybackActive = true

    private val videoPlayerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Timber.tag(TAG).e(error, "Video ExoPlayer error: %s", error.message)
            currentMediaId?.let { videoStreamCache.remove(it) }
            _videoError.value = error.message ?: "Video playback error"
            _isVideoLoading.value = false
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _isVideoLoading.value = true
                }
                Player.STATE_READY -> {
                    _isVideoLoading.value = false
                    _videoError.value = null
                }
                Player.STATE_ENDED -> {
                    _isVideoLoading.value = false
                    if (_isVideoMode.value) {
                        playerProvider()?.seekToNext()
                    }
                }
                Player.STATE_IDLE -> {
                    _isVideoLoading.value = false
                }
            }
        }
    }

    private val mainPlayerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (_isVideoMode.value && isPlaybackActive) {
                _videoPlayer.value?.playWhenReady = isPlaying
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (_isVideoMode.value && isPlaybackActive) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    _videoPlayer.value?.seekTo(newPosition.positionMs)
                }
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
                    _videoPlayer.value?.clearMediaItems()
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
            mainPlayer?.volume = 0f
            val mediaId = mainPlayer?.currentMediaItem?.mediaId ?: currentMediaId
            if (mediaId != null) {
                loadAndPlayVideo(mediaId)
            }
        } else {
            val video = _videoPlayer.value
            val main = playerProvider()
            if (video != null && main != null) {
                val videoPos = video.currentPosition
                if (videoPos > 0 && main.duration > 0) {
                    main.seekTo(videoPos.coerceAtMost(main.duration))
                }
            }
            main?.volume = 1f
            _videoPlayer.value?.stop()
            _videoPlayer.value?.clearMediaItems()
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

    fun setPlaybackActive(active: Boolean) {
        isPlaybackActive = active
        if (!active) {
            _videoPlayer.value?.playWhenReady = false
        } else if (_isVideoMode.value) {
            val mainPlayer = playerProvider()
            val video = _videoPlayer.value
            if (mainPlayer != null && video != null) {
                video.playWhenReady = mainPlayer.isPlaying
            }
        }
    }

    private suspend fun resolveMusicVideoId(mediaId: String, songTitle: String?, artistName: String?): String? {
        val title = songTitle?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val artist = artistName?.trim().orEmpty()
        val query = if (artist.isNotBlank()) "$title $artist" else title

        Timber.tag(TAG).d("Resolving official music video for: '$query'")
        val searchResult = YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull() ?: return null
        val items = searchResult.items
        if (items.isEmpty()) return null

        val isTitleContainingLyrics = title.contains("lyrics", ignoreCase = true)
        val firstArtist = artist.split(',', '&', '/').firstOrNull()
            ?.replace(Regex("(?i)feat\\.?|ft\\.?"), "")
            ?.trim()
            .orEmpty()

        for (item in items) {
            val itemTitle = item.title
            val isLyrics = !isTitleContainingLyrics && itemTitle.contains("lyrics", ignoreCase = true)
            val isCover = itemTitle.contains("cover", ignoreCase = true) || itemTitle.contains("karaoke", ignoreCase = true)
            if (isLyrics || isCover) continue

            if (firstArtist.isNotBlank()) {
                val matchesArtist = (item as? SongItem)?.artists?.any {
                    it.name.contains(firstArtist, ignoreCase = true)
                } == true || itemTitle.contains(firstArtist, ignoreCase = true)
                if (matchesArtist) {
                    Timber.tag(TAG).i("Resolved music video [${item.title}] id=${item.id} for '$query'")
                    return item.id
                }
            }
        }

        // Fallback to the first non-lyrics result, or first result
        val fallback = items.firstOrNull { !it.title.contains("lyrics", ignoreCase = true) } ?: items.first()
        Timber.tag(TAG).i("Fallback resolved music video [${fallback.title}] id=${fallback.id} for '$query'")
        return fallback.id
    }

    fun loadAndPlayVideo(mediaId: String) {
        attachMainPlayerListener()
        val mainPlayer = playerProvider()
        currentMediaId = mediaId
        _videoError.value = null

        // Immediately clear previous video frames to avoid showing stale frames
        _videoPlayer.value?.stop()
        _videoPlayer.value?.clearMediaItems()

        val cachedVideoId = songToMusicVideoCache.get(mediaId) ?: mediaId
        val cached = videoStreamCache[cachedVideoId]
        if (cached?.videoUrl != null) {
            Timber.tag(TAG).d("Playing cached video stream for $mediaId (id=$cachedVideoId)")
            _isVideoAvailable.value = true
            setupExoPlayerWithStream(cached, mainPlayer?.currentPosition ?: 0L, mainPlayer?.isPlaying ?: false)
            return
        }

        streamLoadJob?.cancel()
        _isVideoLoading.value = true

        streamLoadJob = scope.launch(Dispatchers.IO) {
            val song = database.songEntity(mediaId)
            val audioQuality = audioQualityProvider()
            val contentHints = ContentHints(
                isExplicit = song?.explicit,
                isUploaded = song?.isUploaded,
            )

            var targetVideoId = songToMusicVideoCache.get(mediaId) ?: mediaId
            var result = InnerTubeXPlayer.videoStreamForPlayback(
                videoId = targetVideoId,
                audioQuality = audioQuality,
                connectivityManager = connectivityManager,
                contentHints = contentHints,
            )

            var data = result.getOrNull()

            // If mediaId has no video formats (it's an ATV audio track), resolve the official music video
            if (data?.videoUrl == null && targetVideoId == mediaId) {
                val currentItem = playerProvider()?.currentMediaItem
                val tagMeta = currentItem?.localConfiguration?.tag as? MediaMetadata
                val songTitle = tagMeta?.title
                    ?: (if (currentItem?.mediaId == mediaId) currentItem.mediaMetadata.title?.toString() else null)
                    ?: song?.title
                val artistName = tagMeta?.artists?.firstOrNull()?.name
                    ?: (if (currentItem?.mediaId == mediaId) currentItem.mediaMetadata.artist?.toString() else null)
                    ?: ""

                val resolvedId = resolveMusicVideoId(mediaId, songTitle, artistName)
                if (resolvedId != null && resolvedId != mediaId) {
                    Timber.tag(TAG).i("ATV track $mediaId resolved to music video $resolvedId. Extracting stream...")
                    songToMusicVideoCache.put(mediaId, resolvedId)
                    targetVideoId = resolvedId
                    result = InnerTubeXPlayer.videoStreamForPlayback(
                        videoId = targetVideoId,
                        audioQuality = audioQuality,
                        connectivityManager = connectivityManager,
                        contentHints = contentHints,
                    )
                    data = result.getOrNull()
                }
            }

            withContext(Dispatchers.Main) {
                // Guard against race conditions when user rapidly skips songs
                if (currentMediaId != mediaId) return@withContext

                _isVideoLoading.value = false
                if (data?.videoUrl != null) {
                    val streamData = data
                    Timber.tag(TAG).i("Successfully resolved video stream for $mediaId (target=$targetVideoId): itag=${streamData.videoItag} ${streamData.videoWidth}x${streamData.videoHeight}")
                    videoStreamCache.put(mediaId, streamData)
                    videoStreamCache.put(targetVideoId, streamData)
                    _isVideoAvailable.value = true
                    val currentPos = playerProvider()?.currentPosition ?: 0L
                    val isPlaying = playerProvider()?.isPlaying ?: false
                    setupExoPlayerWithStream(streamData, currentPos, isPlaying)
                } else {
                    Timber.tag(TAG).w("No video stream returned for $mediaId")
                    _isVideoAvailable.value = false
                    _videoError.value = "Video not available"
                }
            }
        }
    }

    private fun setupExoPlayerWithStream(data: InnerTubeXPlayer.PlaybackData, startPositionMs: Long, isPlaying: Boolean) {
        val videoUrl = data.videoUrl ?: return
        var player = _videoPlayer.value

        if (player == null) {
            val renderersFactory = DefaultRenderersFactory(context)

            player = ExoPlayer.Builder(context, renderersFactory)
                .build()
                .apply {
                    volume = 1f
                    addListener(videoPlayerListener)
                }

            _videoPlayer.value = player
        }

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

        val videoSource = DefaultMediaSourceFactory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(videoUrl))

        val finalSource = if (!data.streamUrl.isNullOrBlank() && data.streamUrl != videoUrl) {
            val audioSource = DefaultMediaSourceFactory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(data.streamUrl))
            MergingMediaSource(videoSource, audioSource)
        } else {
            videoSource
        }

        // Mute main player so native music video audio plays without overlap
        playerProvider()?.volume = 0f
        player.volume = 1f
        player.setMediaSource(finalSource, startPositionMs)
        player.prepare()
        player.playbackParameters = playerProvider()?.playbackParameters ?: PlaybackParameters.DEFAULT
        player.playWhenReady = isPlaying && isPlaybackActive
    }

    fun onAppForegrounded() {
        attachMainPlayerListener()
        setPlaybackActive(true)
    }

    fun onAppBackgrounded() {
        setPlaybackActive(false)
    }

    fun release() {
        streamLoadJob?.cancel()
        boundMainPlayer?.removeListener(mainPlayerListener)
        boundMainPlayer?.volume = 1f
        boundMainPlayer = null
        val player = _videoPlayer.value
        player?.removeListener(videoPlayerListener)
        player?.release()
        _videoPlayer.value = null
        videoStreamCache.evictAll()
        songToMusicVideoCache.evictAll()
    }
}
