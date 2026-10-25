package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncSnapshotTest {
    @Test
    fun `idsRemovedRemotely keeps local-only items when no previous snapshot exists`() {
        assertEquals(
            emptySet<String>(),
            idsRemovedRemotely(
                localIds = listOf("local-1", "remote-1"),
                previousRemoteIds = null,
                remoteIds = setOf("remote-1"),
            ),
        )
    }

    @Test
    fun `idsRemovedRemotely removes only items present in previous remote snapshot and absent now`() {
        assertEquals(
            setOf("removed-remotely"),
            idsRemovedRemotely(
                localIds = listOf("local-only", "removed-remotely", "still-remote"),
                previousRemoteIds = setOf("removed-remotely", "still-remote"),
                remoteIds = setOf("still-remote"),
            ),
        )
    }

    @Test
    fun `hasCompleteLikedSongsResponse validates advertised count`() {
        assertTrue(hasCompleteLikedSongsResponse(fetchedCount = 10, advertisedCount = null))
        assertTrue(hasCompleteLikedSongsResponse(fetchedCount = 10, advertisedCount = 10))
        assertFalse(hasCompleteLikedSongsResponse(fetchedCount = 9, advertisedCount = 10))
    }
}
