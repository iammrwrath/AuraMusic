package com.metrolist.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
    val userData by DesktopStorage.userData.collectAsState()

    val isFavorite = currentTrack?.let { track ->
        userData.favorites.any { it.id == track.id }
    } ?: false

    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableStateOf(0f) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .height(90.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
            .padding(horizontal = 20.dp, vertical = 8.dp)
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    if (!currentTrack?.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = currentTrack?.thumbnailUrl,
                            contentDescription = currentTrack?.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(54.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp).align(Alignment.Center)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack?.title ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
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
                    }
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Text(
                    text = "No track playing",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Playback Controls & Timeline (Center)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f).padding(horizontal = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Shuffle
                IconButton(onClick = { DesktopAudioPlayer.toggleShuffle() }) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Previous
                IconButton(onClick = { DesktopAudioPlayer.previous() }) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Play / Pause / Buffering
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { DesktopAudioPlayer.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    when (playbackState) {
                        DesktopPlaybackState.BUFFERING -> {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        DesktopPlaybackState.PLAYING -> {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "Pause",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                // Next
                IconButton(onClick = { DesktopAudioPlayer.next() }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Repeat
                IconButton(onClick = { DesktopAudioPlayer.toggleRepeat() }) {
                    val repeatIcon = when (repeatMode) {
                        DesktopRepeatMode.ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    }
                    val repeatTint = when (repeatMode) {
                        DesktopRepeatMode.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Icon(
                        imageVector = repeatIcon,
                        contentDescription = "Repeat",
                        tint = repeatTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Timeline Scrub Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val currentSec = (if (isSeeking) seekPosition.toLong() else positionMs) / 1000
                val totalSec = durationMs / 1000

                Text(
                    text = "%d:%02d".format(currentSec / 60, currentSec % 60),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
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
                    valueRange = 0f..durationMs.coerceAtLeast(1000L).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                )

                Text(
                    text = "%d:%02d".format(totalSec / 60, totalSec % 60),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }

        // Auxiliary Controls (Right: Lyrics, Queue, Volume)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.width(280.dp)
        ) {
            // Lyrics Toggle
            IconButton(onClick = onToggleLyrics) {
                Icon(
                    imageVector = Icons.Default.FormatQuote,
                    contentDescription = "Lyrics",
                    tint = if (isLyricsOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Queue Toggle
            IconButton(onClick = onToggleQueue) {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = "Queue",
                    tint = if (isQueueOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Volume Control
            val volumeIcon = when {
                volume <= 0.01f -> Icons.Default.VolumeMute
                volume < 0.5f -> Icons.Default.VolumeDown
                else -> Icons.Default.VolumeUp
            }

            IconButton(onClick = {
                if (volume > 0f) DesktopAudioPlayer.setVolume(0f) else DesktopAudioPlayer.setVolume(0.7f)
            }) {
                Icon(
                    imageVector = volumeIcon,
                    contentDescription = "Volume",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Slider(
                value = volume,
                onValueChange = { DesktopAudioPlayer.setVolume(it) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.width(100.dp)
            )
        }
    }
}
