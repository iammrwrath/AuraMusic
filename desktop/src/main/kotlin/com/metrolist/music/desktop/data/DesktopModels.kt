package com.metrolist.music.desktop.data

import kotlinx.serialization.Serializable

@Serializable
data class DesktopTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val albumId: String? = null,
    val durationSeconds: Int = 0,
    val thumbnailUrl: String? = null,
) {
    val formattedDuration: String
        get() {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}

@Serializable
data class DesktopPlaylist(
    val id: String,
    val title: String,
    val tracks: List<DesktopTrack> = emptyList(),
    val thumbnailUrl: String? = null,
    val trackCount: Int = 0,
)

@Serializable
data class DesktopAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val year: String? = null,
    val thumbnailUrl: String? = null,
    val tracks: List<DesktopTrack> = emptyList(),
)

enum class DesktopPlaybackState {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    ERROR,
}

enum class DesktopRepeatMode {
    NONE,
    ALL,
    ONE,
}

enum class DesktopAudioQuality(val label: String, val itag: Int) {
    HIGH("High (256 kbps)", 141),
    STANDARD("Standard (128 kbps)", 140),
    DATA_SAVER("Data Saver (64 kbps)", 249),
}

@Serializable
data class DesktopUserSettings(
    val volume: Float = 0.8f,
    val audioQuality: String = "STANDARD",
    val darkTheme: Boolean = true,
    val pureBlack: Boolean = false,
    val lyricsAutoScroll: Boolean = true,
)

@Serializable
data class DesktopAccount(
    val name: String = "",
    val email: String = "",
    val channelHandle: String = "",
    val avatarUrl: String? = null,
    val cookie: String = "",
    val isLoggedIn: Boolean = false,
)

@Serializable
data class DesktopUserData(
    val favorites: List<DesktopTrack> = emptyList(),
    val history: List<DesktopTrack> = emptyList(),
    val playlists: List<DesktopPlaylist> = emptyList(),
    val settings: DesktopUserSettings = DesktopUserSettings(),
    val account: DesktopAccount = DesktopAccount(),
)

data class LyricLine(
    val timeMs: Long,
    val text: String,
)
