/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.video

import android.content.Context
import android.net.ConnectivityManager
import android.os.Looper
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
import kotlinx.coroutines.flow.firstOrNull
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

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            scope.launch(Dispatchers.Main) {
                block()
            }
        }
    }

    private val _isVideoMode = MutableStateFlow(false)
    val isVideoMode: StateFlow<Boolean> = _isVideoMode.asStateFlow()

    private val _isVideoAvailable = MutableStateFlow(true)
    val isVideoAvailable: StateFlow<Boolean> = _isVideoAvailable.asStateFlow()

    private val _isVideoLoading = MutableStateFlow(false)
    val isVideoLoading: StateFlow<Boolean> = _isVideoLoading.asStateFlow()

    private val _isVideoPlaying = MutableStateFlow(false)
    val isVideoPlaying: StateFlow<Boolean> = _isVideoPlaying.asStateFlow()

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
            _isVideoPlaying.value = false
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isVideoPlaying.value = isPlaying
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
                    _isVideoPlaying.value = false
                    if (_isVideoMode.value) {
                        playerProvider()?.seekToNext()
                    }
                }
                Player.STATE_IDLE -> {
                    _isVideoLoading.value = false
                    _isVideoPlaying.value = false
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

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (_isVideoMode.value && isPlaybackActive) {
                _videoPlayer.value?.playWhenReady = playWhenReady
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

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (_isVideoMode.value && playbackState == Player.STATE_ENDED) {
                // If main player audio track ends earlier than the video, pause main player and let video continue
                playerProvider()?.pause()
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
                    _isVideoPlaying.value = false
                } else {
                    preloadMusicVideoId(newId, mediaItem)
                }
            }
        }
    }

    init {
        runOnMain {
            attachMainPlayerListener()
        }
    }

    private fun attachMainPlayerListener() {
        val player = playerProvider()
        if (player != null && player != boundMainPlayer) {
            boundMainPlayer?.removeListener(mainPlayerListener)
            boundMainPlayer = player
            player.addListener(mainPlayerListener)
            currentMediaId = player.currentMediaItem?.mediaId
            currentMediaId?.let { preloadMusicVideoId(it, player.currentMediaItem) }
        }
    }

    fun preloadMusicVideoId(mediaId: String, mediaItem: MediaItem? = null) {
        if (songToMusicVideoCache.get(mediaId) != null) return
        runOnMain {
            if (songToMusicVideoCache.get(mediaId) != null) return@runOnMain
            val currentItem = mediaItem ?: playerProvider()?.currentMediaItem
            val tagMeta = currentItem?.localConfiguration?.tag as? MediaMetadata
            if (tagMeta?.isVideoSong == true) {
                songToMusicVideoCache.put(mediaId, mediaId)
                _isVideoAvailable.value = true
                return@runOnMain
            }
            val songTitle = tagMeta?.title
                ?: (if (currentItem?.mediaId == mediaId) currentItem.mediaMetadata.title?.toString() else null)
            val artistName = tagMeta?.artists?.firstOrNull()?.name
                ?: (if (currentItem?.mediaId == mediaId) currentItem.mediaMetadata.artist?.toString() else null)
                ?: ""

            scope.launch(Dispatchers.IO) {
                try {
                    val resolved = resolveMusicVideoId(mediaId, songTitle, artistName)
                    if (resolved != null) {
                        songToMusicVideoCache.put(mediaId, resolved)
                        _isVideoAvailable.value = true
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Failed to preload music video for $mediaId")
                }
            }
        }
    }

    fun setVideoMode(enabled: Boolean) {
        runOnMain {
            if (_isVideoMode.value == enabled) return@runOnMain
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
                _isVideoPlaying.value = false
            }
        }
    }

    fun toggleVideoMode() {
        setVideoMode(!_isVideoMode.value)
    }

    fun togglePlayPause() {
        runOnMain {
            val video = _videoPlayer.value
            val main = playerProvider()
            if (video != null) {
                if (video.isPlaying) {
                    video.pause()
                    main?.pause()
                } else {
                    video.play()
                    main?.play()
                }
            } else {
                main?.let { if (it.isPlaying) it.pause() else it.play() }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        runOnMain {
            _videoPlayer.value?.seekTo(positionMs)
            playerProvider()?.seekTo(positionMs.coerceAtMost(playerProvider()?.duration ?: Long.MAX_VALUE))
        }
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
        runOnMain {
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
        runOnMain {
            attachMainPlayerListener()
            val mainPlayer = playerProvider()
            currentMediaId = mediaId
            _videoError.value = null

            // Immediately clear previous video frames to avoid showing stale frames
            _videoPlayer.value?.stop()
            _videoPlayer.value?.clearMediaItems()

            // Safely capture metadata and state on Main thread before launching IO
            val currentItem = mainPlayer?.currentMediaItem
            val tagMeta = currentItem?.localConfiguration?.tag as? MediaMetadata
            val isAlreadyVideo = tagMeta?.isVideoSong == true
            val itemTitle = tagMeta?.title
                ?: (if (currentItem?.mediaId == mediaId) currentItem.mediaMetadata.title?.toString() else null)
            val itemArtist = tagMeta?.artists?.firstOrNull()?.name
                ?: (if (currentItem?.mediaId == mediaId) currentItem.mediaMetadata.artist?.toString() else null)
                ?: ""

            val cachedVideoId = songToMusicVideoCache.get(mediaId) ?: mediaId
            val cached = videoStreamCache[cachedVideoId]
            if (cached?.videoUrl != null) {
                Timber.tag(TAG).d("Playing cached video stream for $mediaId (id=$cachedVideoId)")
                _isVideoAvailable.value = true
                val currentPos = mainPlayer?.currentPosition ?: 0L
                val isPlaying = mainPlayer?.playWhenReady ?: mainPlayer?.isPlaying ?: false
                setupExoPlayerWithStream(cached, currentPos, isPlaying)
                return@runOnMain
            }

            streamLoadJob?.cancel()
            _isVideoLoading.value = true

            streamLoadJob = scope.launch(Dispatchers.IO) {
                try {
                    val song = database.songEntity(mediaId)
                    val audioQuality = audioQualityProvider()
                    val contentHints = ContentHints(
                        isExplicit = song?.explicit,
                        isUploaded = song?.isUploaded,
                    )

                    var targetVideoId = songToMusicVideoCache.get(mediaId)
                    if (targetVideoId == null && !isAlreadyVideo) {
                        val songTitle = itemTitle ?: song?.title
                        val artistName = itemArtist.ifBlank {
                            database.song(mediaId).firstOrNull()?.artists?.firstOrNull()?.name.orEmpty()
                        }

                        val resolvedId = resolveMusicVideoId(mediaId, songTitle, artistName)
                        if (resolvedId != null) {
                            targetVideoId = resolvedId
                            songToMusicVideoCache.put(mediaId, resolvedId)
                            Timber.tag(TAG).i("Audio track $mediaId resolved to official music video $resolvedId")
                        } else {
                            targetVideoId = mediaId
                        }
                    } else if (targetVideoId == null) {
                        targetVideoId = mediaId
                    }

                    var result = InnerTubeXPlayer.videoStreamForPlayback(
                        videoId = targetVideoId,
                        audioQuality = audioQuality,
                        connectivityManager = connectivityManager,
                        contentHints = contentHints,
                    )
                    var data = result.getOrNull()

                    // If targetVideoId stream extraction failed, fallback to mediaId if different
                    if (data?.videoUrl == null && targetVideoId != mediaId) {
                        Timber.tag(TAG).w("Resolved music video $targetVideoId stream extraction failed, falling back to $mediaId")
                        result = InnerTubeXPlayer.videoStreamForPlayback(
                            videoId = mediaId,
                            audioQuality = audioQuality,
                            connectivityManager = connectivityManager,
                            contentHints = contentHints,
                        )
                        data = result.getOrNull()
                        targetVideoId = mediaId
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
                            val isPlaying = playerProvider()?.playWhenReady ?: playerProvider()?.isPlaying ?: false
                            setupExoPlayerWithStream(streamData, currentPos, isPlaying)
                        } else {
                            Timber.tag(TAG).w("No video stream returned for $mediaId")
                            _isVideoAvailable.value = false
                            _videoError.value = "Video not available"
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error loading video stream for $mediaId")
                    withContext(Dispatchers.Main) {
                        _isVideoLoading.value = false
                        _videoError.value = e.message ?: "Failed to load video"
                    }
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
        runOnMain {
            streamLoadJob?.cancel()
            boundMainPlayer?.removeListener(mainPlayerListener)
            boundMainPlayer?.volume = 1f
            boundMainPlayer = null
            val player = _videoPlayer.value
            player?.removeListener(videoPlayerListener)
            player?.release()
            _videoPlayer.value = null
            _isVideoPlaying.value = false
            videoStreamCache.evictAll()
            songToMusicVideoCache.evictAll()
        }
    }
}
