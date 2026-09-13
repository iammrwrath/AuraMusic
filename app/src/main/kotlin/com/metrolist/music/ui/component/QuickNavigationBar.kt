/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.R
import com.metrolist.music.constants.ChipSortTypeKey
import com.metrolist.music.constants.LibraryFilter
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.launch

private data class QuickNavItem(
    val titleRes: Int,
    val iconRes: Int,
    val onClick: (NavController, kotlinx.coroutines.CoroutineScope, android.content.Context) -> Unit,
)

@Composable
fun QuickNavigationBar(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val items = listOf(
        QuickNavItem(
            titleRes = R.string.filter_songs,
            iconRes = R.drawable.music_note,
            onClick = { nav, sc, ctx ->
                sc.launch {
                    ctx.safeDataStoreEdit { it[ChipSortTypeKey] = LibraryFilter.SONGS.name }
                    nav.navigate("library")
                }
            },
        ),
        QuickNavItem(
            titleRes = R.string.filter_albums,
            iconRes = R.drawable.album,
            onClick = { nav, sc, ctx ->
                sc.launch {
                    ctx.safeDataStoreEdit { it[ChipSortTypeKey] = LibraryFilter.ALBUMS.name }
                    nav.navigate("library")
                }
            },
        ),
        QuickNavItem(
            titleRes = R.string.filter_artists,
            iconRes = R.drawable.artist,
            onClick = { nav, sc, ctx ->
                sc.launch {
                    ctx.safeDataStoreEdit { it[ChipSortTypeKey] = LibraryFilter.ARTISTS.name }
                    nav.navigate("library")
                }
            },
        ),
        QuickNavItem(
            titleRes = R.string.filter_playlists,
            iconRes = R.drawable.playlist_play,
            onClick = { nav, sc, ctx ->
                sc.launch {
                    ctx.safeDataStoreEdit { it[ChipSortTypeKey] = LibraryFilter.PLAYLISTS.name }
                    nav.navigate("library")
                }
            },
        ),
        QuickNavItem(
            titleRes = R.string.mood_and_genres,
            iconRes = R.drawable.explore_outlined,
            onClick = { nav, _, _ ->
                nav.navigate("mood_and_genres")
            },
        ),
        QuickNavItem(
            titleRes = R.string.charts,
            iconRes = R.drawable.trending_up,
            onClick = { nav, _, _ ->
                nav.navigate("charts_screen")
            },
        ),
        QuickNavItem(
            titleRes = R.string.history,
            iconRes = R.drawable.history,
            onClick = { nav, _, _ ->
                nav.navigate("history")
            },
        ),
        QuickNavItem(
            titleRes = R.string.stats,
            iconRes = R.drawable.stats,
            onClick = { nav, _, _ ->
                nav.navigate("stats")
            },
        ),
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        items(items, key = { it.titleRes }) { item ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        item.onClick(navController, scope, context)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(id = item.iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = item.titleRes),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
