/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.metrolist.innertube.YouTube
import com.metrolist.music.playback.video.VideoPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

private fun isWifiConnected(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}

/**
 * Ambient video canvas component:
 * Plays the official YouTube/InnerTube visualizer or music video on a muted, seamless loop
 * cropped to fill 9:16 vertical view (RESIZE_MODE_ZOOM), with a subtle dark gradient scrim
 * for crystal clear readability of floating controls, marquee text, and lyrics.
 *
 * Automatically falls back to [GenerativeMotionCanvas] when:
 * 1. Procedural-only mode is toggled ON in preferences.
 * 2. Wi-Fi only mode is enabled and user is on mobile data.
 * 3. Video stream is loading, resolving, or unavailable for the current track.
 */
@OptIn(UnstableApi::class)
@Composable
fun AmbientVideoCanvas(
    mediaId: String?,
    gradientColors: List<Color>,
    isPlaying: Boolean,
    videoPlayerManager: VideoPlayerManager,
    wifiOnly: Boolean = false,
    proceduralOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val isOnWifi = remember(context) { isWifiConnected(context) }
    val canStreamVideo = !proceduralOnly && (!wifiOnly || isOnWifi) && mediaId != null

    var ambientExoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isVideoReady by remember { mutableStateOf(false) }

    // Manage dedicated ambient muted ExoPlayer instance
    DisposableEffect(context) {
        val renderersFactory = DefaultRenderersFactory(context.applicationContext)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

        val player = ExoPlayer.Builder(context.applicationContext, renderersFactory)
            .build()
            .apply {
                volume = 0f // Muted ambient canvas
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = true
            }

        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                isVideoReady = true
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    isVideoReady = true
                } else if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_BUFFERING) {
                    if (playbackState == Player.STATE_IDLE) {
                        isVideoReady = false
                    }
                }
            }
        }
        player.addListener(listener)
        ambientExoPlayer = player

        onDispose {
            player.removeListener(listener)
            player.release()
            ambientExoPlayer = null
            isVideoReady = false
        }
    }

    // Sync play / pause state
    LaunchedEffect(isPlaying, ambientExoPlayer) {
        ambientExoPlayer?.let { player ->
            player.playWhenReady = isPlaying
        }
    }

    // Load video stream for current mediaId
    LaunchedEffect(mediaId, canStreamVideo, ambientExoPlayer) {
        val player = ambientExoPlayer ?: return@LaunchedEffect
        if (!canStreamVideo) {
            isVideoReady = false
            player.stop()
            player.clearMediaItems()
            return@LaunchedEffect
        }

        isVideoReady = false
        val streamData = withContext(Dispatchers.IO) {
            videoPlayerManager.resolveStreamData(mediaId)
        }

        val videoUrl = streamData?.videoUrl
        if (videoUrl != null && ambientExoPlayer != null) {
            val okHttpClient = OkHttpClient.Builder()
                .proxy(YouTube.proxy)
                .build()

            val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
                .setUserAgent("com.google.android.youtube")
                .setDefaultRequestProperties(streamData.streamHeaders)

            val mediaSource = DefaultMediaSourceFactory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(videoUrl))

            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = isPlaying
        } else {
            isVideoReady = false
            player.stop()
            player.clearMediaItems()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Base layer: Procedural generative motion art (always active as underlying ambiance / fallback)
        GenerativeMotionCanvas(
            colors = gradientColors,
            isPlaying = isPlaying,
            modifier = Modifier.fillMaxSize(),
        )

        // Video Canvas layer (Fades in seamlessly when video frame decodes)
        AnimatedVisibility(
            visible = canStreamVideo && isVideoReady,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            ambientExoPlayer?.let { player ->
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            setKeepContentOnPlayerReset(true)
                            this.player = player
                        }
                    },
                    update = { playerView ->
                        if (playerView.player != player) {
                            playerView.player = player
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Overlay scrim: Smooth top & bottom vignette to guarantee pristine text, lyrics, and button contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.55f),
                        0.35f to Color.Black.copy(alpha = 0.20f),
                        0.70f to Color.Black.copy(alpha = 0.45f),
                        1.0f to Color.Black.copy(alpha = 0.75f),
                    ),
                ),
        )
    }
}
