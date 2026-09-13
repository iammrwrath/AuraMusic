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
import kotlinx.coroutines.delay
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
    private var preloadJob: Job? = null
    private var preloadingMediaId: String? = null
    private var boundMainPlayer: ExoPlayer? = null
    private var isPlaybackActive = true
    private var isSwitchingToSong = false
    private var isInternalSeek = false

    private var cachedOkHttpClient: OkHttpClient? = null
    private var lastProxy = YouTube.proxy
    private var lastProxyAuth = YouTube.proxyAuth

    private fun getOkHttpClient(): OkHttpClient {
        val currentProxy = YouTube.proxy
        val currentAuth = YouTube.proxyAuth
        val client = cachedOkHttpClient
        if (client != null && currentProxy == lastProxy && currentAuth == lastProxyAuth) {
            return client
        }
        lastProxy = currentProxy
        lastProxyAuth = currentAuth
        val newClient = OkHttpClient.Builder()
            .proxy(currentProxy)
            .apply {
                currentAuth?.let { auth ->
                    proxyAuthenticator { _, response ->
                        response.request.newBuilder()
                            .header("Proxy-Authorization", auth)
                            .build()
                    }
                }
            }
            .build()
        cachedOkHttpClient = newClient
        return newClient
    }

    private fun completeSongToVideoHandoff() {
        val video = _videoPlayer.value ?: return
        if (video.volume >= 1f) return
        val mainPlayer = playerProvider()

        if (mainPlayer != null) {
            val mainPos = mainPlayer.currentPosition
            val videoPos = video.currentPosition
            val drift = kotlin.math.abs(videoPos - mainPos)
            // If drift is significant (> 1500ms), seek video; otherwise let it play smoothly to avoid re-buffering
            if (drift > 1500L) {
                video.seekTo(mainPos.coerceAtLeast(0L))
            }
            mainPlayer.volume = 0f
        }
        video.volume = 1f
        _isVideoLoading.value = false
    }

    private fun completeVideoToSongHandoff() {
        if (!isSwitchingToSong) return
        isSwitchingToSong = false
        val main = playerProvider()
        val video = _videoPlayer.value

        // Unmute main audio now that it is decoded and ready to output sound
        main?.volume = 1f

        // Stop the video player cleanly
        video?.volume = 0f
        video?.stop()
        video?.clearMediaItems()
        _isVideoLoading.value = false
        _isVideoPlaying.value = false
    }

    private val videoPlayerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Timber.tag(TAG).e(error, "Video ExoPlayer error: %s", error.message)
            currentMediaId?.let { videoStreamCache.remove(it) }
            _videoError.value = error.message ?: "Video playback error"
            _isVideoLoading.value = false
            _isVideoPlaying.value = false
            // Keep main audio player audible if video fails
            playerProvider()?.volume = 1f
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isVideoPlaying.value = isPlaying
        }

        override fun onRenderedFirstFrame() {
            if (_isVideoMode.value) {
                completeSongToVideoHandoff()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    if (_videoPlayer.value?.volume == 0f) {
                        _isVideoLoading.value = true
                    }
                }
                Player.STATE_READY -> {
                    _videoError.value = null
                    if (_isVideoMode.value && _videoPlayer.value?.volume == 0f) {
                        // Fallback in case onRenderedFirstFrame was not called or delayed
                        scope.launch(Dispatchers.Main) {
                            delay(100)
                            if (_isVideoMode.value && _videoPlayer.value?.volume == 0f) {
                                completeSongToVideoHandoff()
                            }
                        }
                    } else if (_isVideoMode.value) {
                        _isVideoLoading.value = false
                    }
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
            if (isSwitchingToSong && isPlaying) {
                completeVideoToSongHandoff()
            } else if (_isVideoMode.value && isPlaybackActive && !isSwitchingToSong) {
                _videoPlayer.value?.playWhenReady = isPlaying
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (_isVideoMode.value && isPlaybackActive && !isSwitchingToSong) {
                _videoPlayer.value?.playWhenReady = playWhenReady
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (isInternalSeek) {
                isInternalSeek = false
                return
            }
            if (_isVideoMode.value && isPlaybackActive && !isSwitchingToSong) {
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
            if (isSwitchingToSong && playbackState == Player.STATE_READY) {
                completeVideoToSongHandoff()
            }
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
        if (songToMusicVideoCache.get(mediaId) != null && videoStreamCache.get(mediaId) != null) return
        runOnMain {
            if (songToMusicVideoCache.get(mediaId) != null && videoStreamCache.get(mediaId) != null) return@runOnMain
            val currentItem = mediaItem ?: playerProvider()?.currentMediaItem
            val tagMeta = currentItem?.localConfiguration?.tag as? MediaMetadata
            val isAlreadyVideo = tagMeta?.isVideoSong == true
            if (isAlreadyVideo) {
                songToMusicVideoCache.put(mediaId, mediaId)
                _isVideoAvailable.value = true
            }
            val songTitle = tagMeta?.title
                ?: (if (currentItem?.mediaId == mediaId) currentItem.mediaMetadata.title?.toString() else null)
            val artistName = tagMeta?.artists?.firstOrNull()?.name
                ?: (if (currentItem?.mediaId == mediaId) currentItem.mediaMetadata.artist?.toString() else null)
                ?: ""

            preloadingMediaId = mediaId
            preloadJob?.cancel()
            preloadJob = scope.launch(Dispatchers.IO) {
                try {
                    var targetVideoId = songToMusicVideoCache.get(mediaId)
                    if (targetVideoId == null && !isAlreadyVideo) {
                        val resolved = resolveMusicVideoId(mediaId, songTitle, artistName)
                        if (resolved != null) {
                            targetVideoId = resolved
                            songToMusicVideoCache.put(mediaId, resolved)
                            _isVideoAvailable.value = true
                        } else {
                            targetVideoId = mediaId
                        }
                    } else if (targetVideoId == null) {
                        targetVideoId = mediaId
                    }

                    if (videoStreamCache.get(targetVideoId) == null && videoStreamCache.get(mediaId) == null) {
                        val audioQuality = audioQualityProvider()
                        val song = database.songEntity(mediaId)
                        val contentHints = ContentHints(
                            isExplicit = song?.explicit,
                            isUploaded = song?.isUploaded,
                        )
                        val result = InnerTubeXPlayer.videoStreamForPlayback(
                            videoId = targetVideoId,
                            audioQuality = audioQuality,
                            connectivityManager = connectivityManager,
                            contentHints = contentHints,
                        )
                        val data = result.getOrNull()
                        if (data?.videoUrl != null) {
                            videoStreamCache.put(mediaId, data)
                            videoStreamCache.put(targetVideoId, data)
                            _isVideoAvailable.value = true
                            Timber.tag(TAG).d("Preloaded video stream for $mediaId (target=$targetVideoId)")
                        }
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
                isSwitchingToSong = false
                val mainPlayer = playerProvider()
                val mediaId = mainPlayer?.currentMediaItem?.mediaId ?: currentMediaId
                if (mediaId != null) {
                    loadAndPlayVideo(mediaId)
                }
            } else {
                val video = _videoPlayer.value
                val main = playerProvider()
                if (video == null || main == null) {
                    main?.volume = 1f
                    _videoPlayer.value?.stop()
                    _videoPlayer.value?.clearMediaItems()
                    _isVideoLoading.value = false
                    _isVideoPlaying.value = false
                    return@runOnMain
                }

                isSwitchingToSong = true
                val videoPos = video.currentPosition
                val wasPlaying = video.isPlaying || video.playWhenReady

                // 1. Keep video playing smoothly so user hears zero silence!
                // 2. Prepare mainPlayer at the exact video position, muted
                main.volume = 0f
                val targetDuration = if (main.duration > 0) main.duration else Long.MAX_VALUE
                isInternalSeek = true
                main.seekTo(videoPos.coerceIn(0L, targetDuration))
                main.playWhenReady = wasPlaying
                if (wasPlaying) {
                    main.play()
                } else {
                    main.pause()
                }

                if (!wasPlaying) {
                    completeVideoToSongHandoff()
                } else {
                    // Safety fallback: if mainPlayer takes longer than 800ms to buffer, complete handoff
                    scope.launch(Dispatchers.Main) {
                        delay(800)
                        if (isSwitchingToSong) {
                            completeVideoToSongHandoff()
                        }
                    }
                }
            }
        }
    }

    fun toggleVideoMode() {
        setVideoMode(!_isVideoMode.value)
    }

    fun seekTo(positionMs: Long) {
        runOnMain {
            val safePos = positionMs.coerceAtLeast(0L)
            _videoPlayer.value?.seekTo(safePos)
            val mainPlayer = playerProvider()
            if (mainPlayer != null) {
                isInternalSeek = true
                val target = if (mainPlayer.duration > 0) safePos.coerceAtMost(mainPlayer.duration) else safePos
                mainPlayer.seekTo(target)
            }
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

    fun togglePlayPause() {
        runOnMain {
            val video = _videoPlayer.value
            val main = playerProvider()
            if (video != null && _isVideoMode.value) {
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
            val cached = videoStreamCache[cachedVideoId] ?: videoStreamCache[mediaId]
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
                    // If a preload job is actively running for this track, await its completion
                    if (preloadingMediaId == mediaId && preloadJob?.isActive == true) {
                        preloadJob?.join()
                        val preloaded = videoStreamCache[cachedVideoId] ?: videoStreamCache[mediaId]
                        if (preloaded?.videoUrl != null) {
                            withContext(Dispatchers.Main) {
                                if (currentMediaId != mediaId) return@withContext
                                _isVideoAvailable.value = true
                                val currentPos = playerProvider()?.currentPosition ?: 0L
                                val isPlaying = playerProvider()?.playWhenReady ?: playerProvider()?.isPlaying ?: false
                                setupExoPlayerWithStream(preloaded, currentPos, isPlaying)
                            }
                            return@launch
                        }
                    }

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
                            _isVideoLoading.value = false
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
                    volume = 0f // Start muted while buffering
                    addListener(videoPlayerListener)
                }

            _videoPlayer.value = player
        } else {
            player.volume = 0f // Mute while buffering
        }

        val okHttpClient = getOkHttpClient()
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

        val safeStartPos = startPositionMs.coerceAtLeast(0L)
        player.setMediaSource(finalSource, safeStartPos)
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
            isSwitchingToSong = false
            isInternalSeek = false
            preloadJob?.cancel()
            preloadJob = null
            preloadingMediaId = null
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
            cachedOkHttpClient = null
        }
    }
}
