### 🤖 AI Self-Healing Diagnosis for Issue #15

The bug occurs because `MusicService` only prefetches and caches lyrics in the local database when `ShowLyricsKey` (the toggle for showing lyrics inside the main app UI) is enabled (`dataStore.data.map { it[ShowLyricsKey] ?: false }`). 

When `ShowLyricsKey` is `false` in app settings, `MusicService` skips fetching and upserting lyrics into the database (`database.lyrics(mediaMetadata.id)` remains `null`). Consequently, Android Auto (which relies on `MusicService` prefetching lyrics into the database) finds no lyrics available when playing songs like *Ay Bendito*. In-app lyrics continue to work when explicitly navigated to because the app UI layer calls `LyricsHelper` directly on demand.

### Solution
Update the flow in `MusicService` to trigger lyrics prefetching if **either** `ShowLyricsKey` OR `AndroidAutoLyricsKey` is enabled (`(it[ShowLyricsKey] ?: false) || (it[AndroidAutoLyricsKey] ?: true)`). This ensures lyrics are fetched and saved to the database whenever Android Auto lyrics functionality is active, regardless of the in-app UI toggle setting.

```diff
--- a/app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt
+++ b/app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt
@@ -604,3 +604,3 @@ class MusicService :
         combine(
             currentMediaMetadata.distinctUntilChangedBy { it?.id },
-            dataStore.data.map { it[ShowLyricsKey] ?: false }.distinctUntilChanged(),
+            dataStore.data.map { (it[ShowLyricsKey] ?: false) || (it[AndroidAutoLyricsKey] ?: true) }.distinctUntilChanged(),
         ) { mediaMetadata, showLyrics ->

```
