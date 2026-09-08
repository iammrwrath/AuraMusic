package com.metrolist.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.music.desktop.audio.DesktopAudioPlayer
import com.metrolist.music.desktop.data.DesktopPlaybackState
import com.metrolist.music.desktop.data.DesktopRepeatMode
import com.metrolist.music.desktop.data.DesktopStorage
import com.metrolist.music.desktop.ui.theme.AuraBorder
import com.metrolist.music.desktop.ui.theme.AuraNeonGradient
import com.metrolist.music.desktop.ui.theme.AuraPrimary
import com.metrolist.music.desktop.ui.theme.AuraSurface
import com.metrolist.music.desktop.ui.theme.AuraSurfaceGlass
import kotlinx.coroutines.launch

@Composable
fun DesktopPlayerBar(
    isLyricsOpen: Boolean,
    onToggleLyrics: () -> Unit,
    isQueueOpen: Boolean,
    onToggleQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val currentTrack by DesktopAudioPlayer.currentTrack.collectAsState()
    val playbackState by DesktopAudioPlayer.playbackState.collectAsState()
    val positionMs by DesktopAudioPlayer.currentPositionMs.collectAsState()
    val durationMs by DesktopAudioPlayer.durationMs.collectAsState()
    val volume by DesktopAudioPlayer.volume.collectAsState()
    val repeatMode by DesktopAudioPlayer.repeatMode.collectAsState()
    val shuffle by DesktopAudioPlayer.shuffle.collectAsState()
    val queue by DesktopAudioPlayer.queue.collectAsState()
    val userData by DesktopStorage.userData.collectAsState()

    val isFavorite = currentTrack?.let { track ->
        userData.favorites.any { it.id == track.id }
    } ?: false

    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableStateOf(0f) }
    var previousVolume by remember { mutableStateOf(1f) }

    fun formatTime(ms: Long): String {
        val totalSecs = (ms / 1000).coerceAtLeast(0)
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return "%d:%02d".format(mins, secs)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp)
            .background(AuraSurfaceGlass)
            .border(width = 1.dp, color = AuraBorder)
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        // Track Information (Left)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(280.dp)
        ) {
            if (currentTrack != null) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AuraSurface)
                ) {
                    if (!currentTrack?.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = currentTrack?.thumbnailUrl,
                            contentDescription = currentTrack?.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp).align(Alignment.Center)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack?.title ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentTrack?.artist ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        currentTrack?.let { track ->
                            coroutineScope.launch {
                                DesktopStorage.toggleFavorite(track)
                            }
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) AuraPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.04f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "Select a song to play",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // Center Controls & Scrubber
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f).padding(horizontal = 24.dp)
        ) {
            // Control Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Shuffle
                IconButton(
                    onClick = { DesktopAudioPlayer.toggleShuffle() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffle) AuraPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Previous
                IconButton(
                    onClick = { DesktopAudioPlayer.skipToPrevious() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Play / Pause (Glowing Neon Gradient Button)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AuraNeonGradient)
                        .shadow(10.dp, CircleShape)
                        .clickable {
                            if (playbackState == DesktopPlaybackState.PLAYING) {
                                DesktopAudioPlayer.pause()
                            } else {
                                DesktopAudioPlayer.play()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (playbackState == DesktopPlaybackState.BUFFERING) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (playbackState == DesktopPlaybackState.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playbackState == DesktopPlaybackState.PLAYING) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Next
                IconButton(
                    onClick = { DesktopAudioPlayer.skipToNext() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Repeat Mode
                IconButton(
                    onClick = { DesktopAudioPlayer.toggleRepeatMode() },
                    modifier = Modifier.size(32.dp)
                ) {
                    val icon = when (repeatMode) {
                        DesktopRepeatMode.ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Repeat",
                        tint = if (repeatMode != DesktopRepeatMode.NONE) AuraPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Scrubber Bar with Elapsed & Total Duration
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(26.dp)
            ) {
                val currentPos = if (isSeeking) seekPosition.toLong() else positionMs
                Text(
                    text = formatTime(currentPos),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(38.dp)
                )

                Slider(
                    value = if (isSeeking) seekPosition else positionMs.toFloat(),
                    onValueChange = {
                        isSeeking = true
                        seekPosition = it
                    },
                    onValueChangeFinished = {
                        DesktopAudioPlayer.seekTo(seekPosition.toLong())
                        isSeeking = false
                    },
                    valueRange = 0f..(durationMs.coerceAtLeast(1L).toFloat()),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = AuraPrimary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )

                Text(
                    text = formatTime(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(38.dp)
                )
            }
        }

        // Action Drawers & Volume (Right)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.width(280.dp)
        ) {
            // Lyrics Toggle
            IconButton(
                onClick = onToggleLyrics,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isLyricsOpen) AuraPrimary.copy(alpha = 0.2f) else Color.Transparent)
            ) {
                Icon(
                    imageVector = Icons.Default.FormatQuote,
                    contentDescription = "Lyrics",
                    tint = if (isLyricsOpen) AuraPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Queue Toggle with live track count badge
            IconButton(
                onClick = onToggleQueue,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isQueueOpen) AuraPrimary.copy(alpha = 0.2f) else Color.Transparent)
            ) {
                Box {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Queue",
                        tint = if (isQueueOpen) AuraPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    if (queue.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(AuraPrimary)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Volume Icon (Click to Mute / Restore)
            IconButton(
                onClick = {
                    if (volume > 0f) {
                        previousVolume = volume
                        DesktopAudioPlayer.setVolume(0f)
                    } else {
                        DesktopAudioPlayer.setVolume(if (previousVolume > 0f) previousVolume else 0.8f)
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                val volIcon = when {
                    volume <= 0f -> Icons.Default.VolumeMute
                    volume < 0.5f -> Icons.Default.VolumeDown
                    else -> Icons.Default.VolumeUp
                }
                Icon(
                    imageVector = volIcon,
                    contentDescription = "Volume",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Volume Slider
            Slider(
                value = volume,
                onValueChange = { DesktopAudioPlayer.setVolume(it) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = AuraPrimary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                ),
                modifier = Modifier.width(100.dp)
            )
        }
    }
}
