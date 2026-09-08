package com.metrolist.music.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.music.desktop.api.YouTubeDesktopClient
import com.metrolist.music.desktop.audio.DesktopAudioPlayer
import com.metrolist.music.desktop.data.DesktopPlaybackState
import com.metrolist.music.desktop.data.DesktopStorage
import com.metrolist.music.desktop.data.DesktopTrack
import com.metrolist.music.desktop.ui.theme.AuraBorder
import com.metrolist.music.desktop.ui.theme.AuraPrimary
import com.metrolist.music.desktop.ui.theme.AuraSurface
import com.metrolist.music.desktop.ui.theme.AuraSurfaceGlass
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    initialQuery: String = "",
    onQueryChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<DesktopTrack>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }
    val currentTrack by DesktopAudioPlayer.currentTrack.collectAsState()
    val playbackState by DesktopAudioPlayer.playbackState.collectAsState()
    val isPlaying = playbackState == DesktopPlaybackState.PLAYING
    val userData by DesktopStorage.userData.collectAsState()

    fun performSearch() {
        if (query.isBlank()) return
        coroutineScope.launch {
            isSearching = true
            results = YouTubeDesktopClient.search(query)
            isSearching = false
        }
    }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank() && initialQuery != query) {
            query = initialQuery
            performSearch()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        // Search Input Bar
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onQueryChange(it)
            },
            placeholder = { Text("Search songs, artists, albums, or playlists...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = AuraPrimary
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        query = ""
                        results = emptyList()
                        onQueryChange("")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { performSearch() }),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = AuraSurfaceGlass,
                unfocusedContainerColor = AuraSurfaceGlass,
                focusedBorderColor = AuraPrimary,
                unfocusedBorderColor = AuraBorder
            ),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Filter Category Chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf("All", "Songs", "Artists", "Albums").forEach { filter ->
                val isSelected = selectedFilter == filter
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedFilter = filter
                        if (query.isNotBlank()) performSearch()
                    },
                    label = { Text(filter, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AuraPrimary,
                        selectedLabelColor = Color.White,
                        containerColor = Color.White.copy(alpha = 0.05f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) AuraPrimary else AuraBorder
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isSearching) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AuraPrimary, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Searching across millions of songs...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (results.isEmpty() && query.isNotBlank()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No tracks found for \"$query\". Try a different keyword.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (results.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Explore & discover your favorite tracks on YouTube Music",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(results, key = { index, track -> "${track.id}_$index" }) { index, track ->
                    val isTrackActive = currentTrack?.id == track.id
                    val isFav = userData.favorites.any { it.id == track.id }
                    val interactionSource = remember { MutableInteractionSource() }
                    val isHovered by interactionSource.collectIsHoveredAsState()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isTrackActive) AuraPrimary.copy(alpha = 0.12f)
                                else if (isHovered) Color.White.copy(alpha = 0.05f)
                                else Color.Transparent
                            )
                            .border(
                                width = 1.dp,
                                color = if (isTrackActive) AuraPrimary.copy(alpha = 0.3f) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .hoverable(interactionSource)
                            .clickable {
                                DesktopAudioPlayer.playQueue(results, startIndex = index)
                            }
                            .padding(horizontal = 14.dp)
                    ) {
                        // Index or Playing Equalizer
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.width(32.dp)
                        ) {
                            if (isTrackActive && isPlaying) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Playing",
                                    tint = AuraPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else if (isHovered) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = AuraPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Album Thumbnail
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AuraSurface)
                        ) {
                            if (!track.thumbnailUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = track.thumbnailUrl,
                                    contentDescription = track.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp).align(Alignment.Center)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Title & Artist
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isTrackActive) FontWeight.Bold else FontWeight.SemiBold
                                ),
                                color = if (isTrackActive) AuraPrimary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist.ifBlank { "Unknown Artist" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Favorite Icon
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    DesktopStorage.toggleFavorite(track)
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFav) AuraPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Duration Badge
                        if (track.durationSeconds > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = track.formattedDuration,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.width(48.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
