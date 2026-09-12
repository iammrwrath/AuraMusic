/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.metrolist.music.extensions.togglePlayPause
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.ThumbnailCornerRadius
import com.metrolist.music.playback.video.VideoPlayerManager
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerVideoView(
    videoPlayerManager: VideoPlayerManager,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val videoPlayer by videoPlayerManager.videoPlayer.collectAsState()
    val isVideoLoading by videoPlayerManager.isVideoLoading.collectAsState()
    val videoError by videoPlayerManager.videoError.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val isFullscreen by videoPlayerManager.isFullscreen.collectAsState()
    val areControlsVisible by videoPlayerManager.areControlsVisible.collectAsState()

    // Auto-hide controls after 3 seconds when playing
    LaunchedEffect(areControlsVisible, isPlaying) {
        if (areControlsVisible && isPlaying) {
            delay(3500)
            videoPlayerManager.setControlsVisible(false)
        }
    }

    val videoContent: @Composable (Boolean) -> Unit = { inFullscreen ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { videoPlayerManager.toggleControlsVisible() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        setKeepContentOnPlayerReset(true)
                        this.player = videoPlayer
                    }
                },
                update = { playerView ->
                    if (playerView.player != videoPlayer) {
                        playerView.player = videoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Buffering / Loading Indicator
            var isBuffering by remember { mutableStateOf(false) }
            DisposableEffect(videoPlayer) {
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isBuffering = playbackState == Player.STATE_BUFFERING
                    }
                }
                videoPlayer?.addListener(listener)
                isBuffering = videoPlayer?.playbackState == Player.STATE_BUFFERING
                onDispose {
                    videoPlayer?.removeListener(listener)
                }
            }

            if (isVideoLoading || isBuffering) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color.White,
                        strokeWidth = 3.dp,
                    )
                }
            }

            // Error Overlay
            if (videoError != null && !isVideoLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = videoError ?: stringResource(R.string.error_unknown),
                            color = Color.White,
                            fontSize = 14.sp,
                        )
                        TextButton(
                            onClick = {
                                playerConnection.mediaMetadata.value?.id?.let {
                                    videoPlayerManager.loadAndPlayVideo(it)
                                }
                            },
                        ) {
                            Text(stringResource(R.string.retry), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Controls Overlay (Play/Pause & Fullscreen)
            AnimatedVisibility(
                visible = areControlsVisible && videoError == null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                ) {
                    // Center Play/Pause button
                    IconButton(
                        onClick = { playerConnection.player.togglePlayPause() },
                        modifier = Modifier
                            .size(64.dp)
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                    ) {
                        Icon(
                            painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }

                    // Fullscreen Button
                    IconButton(
                        onClick = { videoPlayerManager.toggleFullscreen() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                    ) {
                        Icon(
                            painter = painterResource(if (inFullscreen) R.drawable.fullscreen_exit else R.drawable.fullscreen),
                            contentDescription = if (inFullscreen) stringResource(R.string.exit_fullscreen) else stringResource(R.string.enter_fullscreen),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    // In fullscreen, top-left back button
                    if (inFullscreen) {
                        IconButton(
                            onClick = { videoPlayerManager.setFullscreen(false) },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f)),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = stringResource(R.string.exit_fullscreen),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // Normal Inline Video View
    val containerShape = RoundedCornerShape(ThumbnailCornerRadius)
    Box(
        modifier = modifier
            .then(
                if (isLandscape) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                }
            )
            .clip(containerShape),
        contentAlignment = Alignment.Center,
    ) {
        videoContent(false)
    }

    // Fullscreen Overlay Dialog
    if (isFullscreen) {
        Dialog(
            onDismissRequest = { videoPlayerManager.setFullscreen(false) },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            BackHandler {
                videoPlayerManager.setFullscreen(false)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                videoContent(true)
            }
        }
    }
}
