package com.metrolist.music.desktop.audio

import com.metrolist.music.desktop.api.DesktopLyricsService
import com.metrolist.music.desktop.api.YouTubeDesktopClient
import com.metrolist.music.desktop.data.DesktopPlaybackState
import com.metrolist.music.desktop.data.DesktopRepeatMode
import com.metrolist.music.desktop.data.DesktopStorage
import com.metrolist.music.desktop.data.DesktopTrack
import com.metrolist.music.desktop.data.LyricLine
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

object DesktopAudioPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _playbackState = MutableStateFlow(DesktopPlaybackState.IDLE)
    val playbackState: StateFlow<DesktopPlaybackState> = _playbackState.asStateFlow()

    private val _currentTrack = MutableStateFlow<DesktopTrack?>(null)
    val currentTrack: StateFlow<DesktopTrack?> = _currentTrack.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _volume = MutableStateFlow(0.8f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _queue = MutableStateFlow<List<DesktopTrack>>(emptyList())
    val queue: StateFlow<List<DesktopTrack>> = _queue.asStateFlow()

    private val _queueIndex = MutableStateFlow(0)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private val _repeatMode = MutableStateFlow(DesktopRepeatMode.NONE)
    val repeatMode: StateFlow<DesktopRepeatMode> = _repeatMode.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    private val _currentLyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val currentLyrics: StateFlow<List<LyricLine>> = _currentLyrics.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val isPlatformInitialized = AtomicBoolean(false)

    init {
        try {
            Platform.startup {
                isPlatformInitialized.set(true)
            }
        } catch (e: IllegalStateException) {
            // Already initialized
            isPlatformInitialized.set(true)
        } catch (t: Throwable) {
            println("[DesktopAudioPlayer] JavaFX Platform init note: ${t.message}")
        }
    }

    private fun runOnFx(action: () -> Unit) {
        if (isPlatformInitialized.get()) {
            Platform.runLater(action)
        } else {
            action()
        }
    }

    fun playTrack(track: DesktopTrack, newQueue: List<DesktopTrack> = listOf(track)) {
        scope.launch {
            _queue.value = newQueue
            val idx = newQueue.indexOfFirst { it.id == track.id }
            _queueIndex.value = if (idx >= 0) idx else 0
            startTrackPlayback(track)
        }
    }

    private suspend fun startTrackPlayback(track: DesktopTrack) {
        _currentTrack.value = track
        _playbackState.value = DesktopPlaybackState.BUFFERING
        _currentPositionMs.value = 0L
        _durationMs.value = track.durationSeconds * 1000L
        _currentLyrics.value = emptyList()

        DesktopStorage.addToHistory(track)

        // Fetch lyrics concurrently
        _isLoadingLyrics.value = true
        scope.launch {
            val lyrics = DesktopLyricsService.getLyrics(track.title, track.artist, track.durationSeconds)
            _currentLyrics.value = lyrics
            _isLoadingLyrics.value = false
        }

        // Fetch streaming URL from YouTube
        val streamUrl = YouTubeDesktopClient.getStreamUrl(track.id)
        if (streamUrl.isNullOrBlank()) {
            println("[DesktopAudioPlayer] Failed to get stream URL for ${track.title}")
            _playbackState.value = DesktopPlaybackState.ERROR
            return
        }

        runOnFx {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.dispose()

                val media = Media(streamUrl)
                val player = MediaPlayer(media).apply {
                    volume = _volume.value.toDouble()

                    setOnReady {
                        val dur = media.duration
                        if (dur != null && !dur.isUnknown) {
                            _durationMs.value = dur.toMillis().toLong()
                        }
                        _playbackState.value = DesktopPlaybackState.PLAYING
                        startProgressTracker()
                    }

                    setOnPlaying {
                        _playbackState.value = DesktopPlaybackState.PLAYING
                    }

                    setOnPaused {
                        _playbackState.value = DesktopPlaybackState.PAUSED
                    }

                    setOnEndOfMedia {
                        onTrackEnded()
                    }

                    setOnError {
                        println("[DesktopAudioPlayer] MediaPlayer error: ${error?.message}")
                        _playbackState.value = DesktopPlaybackState.ERROR
                    }
                }
                mediaPlayer = player
                player.play()
            } catch (e: Exception) {
                println("[DesktopAudioPlayer] Playback initialization error: ${e.message}")
                _playbackState.value = DesktopPlaybackState.ERROR
            }
        }
    }

    fun togglePlayPause() {
        when (_playbackState.value) {
            DesktopPlaybackState.PLAYING -> pause()
            DesktopPlaybackState.PAUSED -> resume()
            DesktopPlaybackState.IDLE, DesktopPlaybackState.ERROR -> {
                _currentTrack.value?.let { track ->
                    scope.launch { startTrackPlayback(track) }
                }
            }
            DesktopPlaybackState.BUFFERING -> { /* no-op while buffering */ }
        }
    }

    fun pause() {
        runOnFx {
            mediaPlayer?.pause()
            _playbackState.value = DesktopPlaybackState.PAUSED
        }
    }

    fun resume() {
        runOnFx {
            mediaPlayer?.play()
            _playbackState.value = DesktopPlaybackState.PLAYING
        }
    }

    fun seekTo(positionMs: Long) {
        _currentPositionMs.value = positionMs
        runOnFx {
            mediaPlayer?.seek(Duration.millis(positionMs.toDouble()))
        }
    }

    fun setVolume(newVolume: Float) {
        val clamped = newVolume.coerceIn(0.0f, 1.0f)
        _volume.value = clamped
        runOnFx {
            mediaPlayer?.volume = clamped.toDouble()
        }
        scope.launch {
            DesktopStorage.updateSettings { it.copy(volume = clamped) }
        }
    }

    fun next() {
        val q = _queue.value
        if (q.isEmpty()) return
        val nextIdx = if (_shuffle.value) {
            q.indices.random()
        } else {
            (_queueIndex.value + 1) % q.size
        }
        _queueIndex.value = nextIdx
        scope.launch {
            startTrackPlayback(q[nextIdx])
        }
    }

    fun previous() {
        val q = _queue.value
        if (q.isEmpty()) return
        if (_currentPositionMs.value > 3000L) {
            seekTo(0L)
            return
        }
        val prevIdx = if (_queueIndex.value - 1 < 0) q.size - 1 else _queueIndex.value - 1
        _queueIndex.value = prevIdx
        scope.launch {
            startTrackPlayback(q[prevIdx])
        }
    }

    fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            DesktopRepeatMode.NONE -> DesktopRepeatMode.ALL
            DesktopRepeatMode.ALL -> DesktopRepeatMode.ONE
            DesktopRepeatMode.ONE -> DesktopRepeatMode.NONE
        }
    }

    fun toggleShuffle() {
        _shuffle.value = !_shuffle.value
    }

    fun play() = resume()
    fun skipToNext() = next()
    fun skipToPrevious() = previous()
    fun toggleRepeatMode() = toggleRepeat()

    fun playQueue(tracks: List<DesktopTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val validIndex = startIndex.coerceIn(0, tracks.size - 1)
        playTrack(tracks[validIndex], tracks)
    }

    fun removeFromQueue(index: Int) {
        val current = _queue.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _queue.value = current
            if (_queueIndex.value >= current.size) {
                _queueIndex.value = (current.size - 1).coerceAtLeast(0)
            }
        }
    }

    fun clearQueue() {
        _queue.value = emptyList()
        _queueIndex.value = 0
    }


    private fun onTrackEnded() {
        when (_repeatMode.value) {
            DesktopRepeatMode.ONE -> {
                seekTo(0L)
                resume()
            }
            DesktopRepeatMode.ALL -> {
                next()
            }
            DesktopRepeatMode.NONE -> {
                val q = _queue.value
                if (_queueIndex.value + 1 < q.size) {
                    next()
                } else {
                    _playbackState.value = DesktopPlaybackState.IDLE
                    _currentPositionMs.value = 0L
                }
            }
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _playbackState.value == DesktopPlaybackState.PLAYING) {
                runOnFx {
                    val current = mediaPlayer?.currentTime
                    if (current != null && !current.isUnknown) {
                        _currentPositionMs.value = current.toMillis().toLong()
                    }
                }
                delay(200)
            }
        }
    }
}
