package com.metrolist.music.playback.queues

import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class QueueStatusFilterTest {
    private fun item(
        id: String,
        explicit: Boolean = false,
        isVideoSong: Boolean = false,
    ) = MediaMetadata(
        id = id,
        title = id,
        artists = emptyList(),
        duration = 180,
        explicit = explicit,
        musicVideoType = if (isVideoSong) "MUSIC_VIDEO_TYPE_OMV" else null,
    ).toMediaItem()

    @Test
    fun `filterExplicit preserves selected song index and position when earlier songs are removed`() {
        val status = Queue.Status(
            title = "Playlist",
            items = listOf(
                item("1", explicit = true),
                item("2", explicit = false),
                item("3", explicit = true),
                item("4", explicit = false),
            ),
            mediaItemIndex = 3,
            position = 42_000L,
        )

        val filtered = status.filterExplicit(enabled = true)
        assertEquals(listOf("2", "4"), filtered.items.map { it.mediaId })
        assertEquals(1, filtered.mediaItemIndex)
        assertEquals(42_000L, filtered.position)
    }

    @Test
    fun `filterVideoSongs resets position to zero when the selected song itself is removed`() {
        val status = Queue.Status(
            title = "Playlist",
            items = listOf(
                item("1", isVideoSong = false),
                item("2", isVideoSong = true),
                item("3", isVideoSong = false),
            ),
            mediaItemIndex = 1,
            position = 15_000L,
        )

        val filtered = status.filterVideoSongs(disableVideos = true)
        assertEquals(listOf("1", "3"), filtered.items.map { it.mediaId })
        assertEquals(1, filtered.mediaItemIndex)
        assertEquals(0L, filtered.position)
    }
}
