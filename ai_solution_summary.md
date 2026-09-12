### 🤖 AI Self-Healing Diagnosis for Issue #7

The crash is caused by accessing `ExoPlayer` (`mediaSession.player`) off the main thread in `MediaLibrarySessionCallback.onPlaybackResumption`. 

In `MediaLibrarySessionCallback.kt`, `onPlaybackResumption` launches a coroutine with `scope.future(Dispatchers.IO)`. Inside this block running on a background worker thread (`DefaultDispatcher-worker-24`), the code inspects player properties such as `mediaSession.player.mediaItemCount`, `getMediaItemAt(...)`, `currentMediaItemIndex`, and `currentPosition`. 

Media3 / ExoPlayer strictly verifies that application thread calls are performed on the player's main looper thread. Invoking player methods on background dispatchers triggers an `IllegalStateException: Player is accessed on the wrong thread`.

### Fix
Change `scope.future(Dispatchers.IO)` in `onPlaybackResumption` to `scope.future(Dispatchers.Main)` so player access occurs safely on the main thread. Database queries that fetch fallback songs are explicitly wrapped with `withContext(Dispatchers.IO)`.

```diff
--- a/app/src/main/kotlin/com/metrolist/music/playback/MediaLibrarySessionCallback.kt
+++ b/app/src/main/kotlin/com/metrolist/music/playback/MediaLibrarySessionCallback.kt
@@ -134,7 +134,7 @@ class MediaLibrarySessionCallback
     override fun onPlaybackResumption(
         mediaSession: MediaSession,
         controller: MediaSession.ControllerInfo
     ): ListenableFuture<MediaItemsWithStartPosition> =
-        scope.future(Dispatchers.IO) {
+        scope.future(Dispatchers.Main) {
             // If the player already has items, resume at current index/position
             if (mediaSession.player.mediaItemCount > 0) {
                 val currentItems = List(mediaSession.player.mediaItemCount) { i ->
@@ -145,23 +145,25 @@ class MediaLibrarySessionCallback
                 return@future MediaItemsWithStartPosition(currentItems, currentIndex, currentPosition)
             }
 
-            // Otherwise, load user's liked songs or recent songs to resume playback immediately
-            val likedSongs = database.likedSongs(SongSortType.CREATE_DATE, descending = true).first()
-            if (likedSongs.isNotEmpty()) {
-                return@future MediaItemsWithStartPosition(
-                    likedSongs.map { it.toMediaItem() },
-                    0,
-                    C.TIME_UNSET
-                )
-            }
-
-            val recentSongs = database.songsByCreateDateAsc().first()
-            if (recentSongs.isNotEmpty()) {
-                return@future MediaItemsWithStartPosition(
-                    recentSongs.map { it.toMediaItem() },
-                    0,
-                    C.TIME_UNSET
-                )
+            // Otherwise, load user's liked songs or recent songs on IO dispatcher to resume playback immediately
+            withContext(Dispatchers.IO) {
+                val likedSongs = database.likedSongs(SongSortType.CREATE_DATE, descending = true).first()
+                if (likedSongs.isNotEmpty()) {
+                    return@withContext MediaItemsWithStartPosition(
+                        likedSongs.map { it.toMediaItem() },
+                        0,
+                        C.TIME_UNSET
+                    )
+                }
+
+                val recentSongs = database.songsByCreateDateAsc().first()
+                if (recentSongs.isNotEmpty()) {
+                    return@withContext MediaItemsWithStartPosition(
+                        recentSongs.map { it.toMediaItem() },
+                        0,
+                        C.TIME_UNSET
+                    )
+                }
+
+                MediaItemsWithStartPosition(emptyList(), 0, C.TIME_UNSET)
             }
-
-            MediaItemsWithStartPosition(emptyList(), 0, C.TIME_UNSET)
         }

```
