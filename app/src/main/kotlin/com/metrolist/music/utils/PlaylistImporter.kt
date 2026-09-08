/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.models.toMediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDateTime

object PlaylistImporter {

    data class ParsedEntry(
        val title: String,
        val artist: String? = null,
        val rawPathOrId: String? = null,
    )

    suspend fun importM3U(
        context: Context,
        uri: Uri,
        database: MusicDatabase,
    ): Result<Pair<String, Int>> = withContext(Dispatchers.IO) {
        try {
            var fileName = "Imported Playlist"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val rawName = cursor.getString(nameIndex)
                    if (!rawName.isNullOrBlank()) {
                        fileName = rawName.substringBeforeLast(".")
                    }
                }
            }

            var playlistTitle = fileName
            val entries = mutableListOf<ParsedEntry>()

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                var currentTitle: String? = null
                var currentArtist: String? = null

                reader.forEachLine { rawLine ->
                    val line = rawLine.trim()
                    when {
                        line.startsWith("#PLAYLIST:", ignoreCase = true) -> {
                            val customName = line.substringAfter(":").trim()
                            if (customName.isNotBlank()) {
                                playlistTitle = customName
                            }
                        }
                        line.startsWith("#EXTINF:", ignoreCase = true) -> {
                            val trackInfo = line.substringAfter(",").trim()
                            if (trackInfo.contains(" - ")) {
                                currentArtist = trackInfo.substringBefore(" - ").trim()
                                currentTitle = trackInfo.substringAfter(" - ").trim()
                            } else {
                                currentTitle = trackInfo
                                currentArtist = null
                            }
                        }
                        line.isNotBlank() && !line.startsWith("#") -> {
                            val fallbackTitle = if (currentTitle.isNullOrBlank()) {
                                line.substringAfterLast("/").substringAfterLast("\\").substringBeforeLast(".")
                            } else {
                                currentTitle!!
                            }
                            entries.add(
                                ParsedEntry(
                                    title = fallbackTitle,
                                    artist = currentArtist,
                                    rawPathOrId = line,
                                )
                            )
                            currentTitle = null
                            currentArtist = null
                        }
                    }
                }
            }

            if (entries.isEmpty()) {
                return@withContext Result.failure(Exception("No tracks found in playlist file"))
            }

            val matchedSongIds = mutableListOf<String>()

            for (entry in entries) {
                var foundId: String? = null

                // 1. Check if rawPathOrId is a YouTube Video ID or URL
                val raw = entry.rawPathOrId.orEmpty()
                val potentialVideoId = when {
                    raw.contains("v=") -> raw.substringAfter("v=").substringBefore("&")
                    raw.contains("youtu.be/") -> raw.substringAfter("youtu.be/").substringBefore("?")
                    raw.length == 11 && !raw.contains(" ") && !raw.contains(".") -> raw
                    else -> null
                }

                if (potentialVideoId != null) {
                    val localSong = database.getSongById(potentialVideoId)
                    if (localSong != null) {
                        foundId = localSong.id
                    } else {
                        foundId = potentialVideoId
                    }
                }

                // 2. Search local database by song title
                if (foundId == null) {
                    val searchFlow = database.searchSongs(entry.title, previewSize = 5)
                    val localMatches = searchFlow.firstOrNull().orEmpty()
                    val matched = if (!entry.artist.isNullOrBlank()) {
                        localMatches.firstOrNull { s ->
                            s.artists.any { it.name.contains(entry.artist, ignoreCase = true) }
                        } ?: localMatches.firstOrNull()
                    } else {
                        localMatches.firstOrNull()
                    }
                    if (matched != null) {
                        foundId = matched.song.id
                    }
                }

                // 3. Fallback to online YouTube search if not found in local DB
                if (foundId == null) {
                    val query = if (!entry.artist.isNullOrBlank()) "${entry.artist} - ${entry.title}" else entry.title
                    runCatching {
                        val searchResult = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                        val topSong = searchResult?.items?.filterIsInstance<SongItem>()?.firstOrNull()
                        if (topSong != null) {
                            database.query {
                                insert(topSong.toMediaMetadata())
                            }
                            foundId = topSong.id
                        }
                    }
                }

                if (foundId != null && !matchedSongIds.contains(foundId)) {
                    matchedSongIds.add(foundId)
                }
            }

            // Create Playlist Entity
            val newPlaylist = PlaylistEntity(
                name = playlistTitle,
                browseId = null,
                bookmarkedAt = LocalDateTime.now(),
                isEditable = true,
            )

            database.query {
                insert(newPlaylist)
                matchedSongIds.forEachIndexed { index, songId ->
                    insert(
                        PlaylistSongMap(
                            songId = songId,
                            playlistId = newPlaylist.id,
                            position = index,
                        )
                    )
                }
            }

            Result.success(Pair(playlistTitle, matchedSongIds.size))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
