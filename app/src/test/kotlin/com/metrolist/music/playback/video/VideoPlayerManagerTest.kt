package com.metrolist.music.playback.video

import android.content.Context
import android.net.ConnectivityManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.db.InternalDatabase
import com.metrolist.music.db.MusicDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VideoPlayerManagerTest {
    private lateinit var context: Context
    private lateinit var database: MusicDatabase
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var manager: VideoPlayerManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val internalDb = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database = MusicDatabase(internalDb)
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        manager = VideoPlayerManager(
            context = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            playerProvider = { null },
            database = database,
            connectivityManager = connectivityManager,
            audioQualityProvider = { AudioQuality.AUTO },
        )
    }

    @Test
    fun `video mode defaults to false and toggles correctly`() {
        assertFalse(manager.isVideoMode.value)

        manager.setVideoMode(true)
        assertTrue(manager.isVideoMode.value)

        manager.setVideoMode(false)
        assertFalse(manager.isVideoMode.value)

        manager.toggleVideoMode()
        assertTrue(manager.isVideoMode.value)

        manager.toggleVideoMode()
        assertFalse(manager.isVideoMode.value)
    }

    @Test
    fun `fullscreen state defaults to false and toggles correctly`() {
        assertFalse(manager.isFullscreen.value)

        manager.setFullscreen(true)
        assertTrue(manager.isFullscreen.value)

        manager.toggleFullscreen()
        assertFalse(manager.isFullscreen.value)
    }

    @Test
    fun `controls visibility defaults to true and toggles correctly`() {
        assertTrue(manager.areControlsVisible.value)

        manager.setControlsVisible(false)
        assertFalse(manager.areControlsVisible.value)

        manager.toggleControlsVisible()
        assertTrue(manager.areControlsVisible.value)
    }

    @Test
    fun `playback active toggles without throwing`() {
        manager.setVideoMode(true)
        manager.setPlaybackActive(false)
        manager.setPlaybackActive(true)
        manager.onAppBackgrounded()
        manager.onAppForegrounded()
        assertTrue(manager.isVideoMode.value)
    }

    @Test
    fun `release resets player state without throwing`() {
        manager.setVideoMode(true)
        manager.release()
        assertEquals(null, manager.videoPlayer.value)
    }
}
