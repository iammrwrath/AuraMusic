### 🤖 AI Self-Healing Diagnosis for Issue #9

The diagnostic logs reveal repeated SQLite exceptions whenever the Room database is opened or initialized:
```text
android.database.sqlite.SQLiteException: no such column: songId (code 1 SQLITE_ERROR): , while compiling: CREATE INDEX IF NOT EXISTS idx_format_songId ON format (songId)
	at com.metrolist.music.db.MusicDatabaseKt.applyPragmaSettings(...)
```

### Root Cause
In `MusicDatabase.kt`, the helper function `applyPragmaSettings(db)` executes index creation statements for performance tuning on database callbacks (`onCreate` and `onOpen`). One of the statements attempts to create an index on the `format` table:
```sql
CREATE INDEX IF NOT EXISTS idx_format_songId ON format (songId)
```
However, the `format` table (`FormatEntity`) uses `id` as its primary key column representing the song identifier, rather than `songId`. Attempting to reference `songId` results in `SQLiteException: no such column: songId`, causing `applyPragmaSettings()` to fail and log errors on every database open operation.

### Solution
Update the SQL query in `applyPragmaSettings()` to reference the existing `id` column (`CREATE INDEX IF NOT EXISTS idx_format_id ON format (id)`), ensuring the index builds cleanly without throwing exceptions.

```diff
diff --git a/app/src/main/kotlin/com/metrolist/music/db/MusicDatabase.kt b/app/src/main/kotlin/com/metrolist/music/db/MusicDatabase.kt
--- a/app/src/main/kotlin/com/metrolist/music/db/MusicDatabase.kt
+++ b/app/src/main/kotlin/com/metrolist/music/db/MusicDatabase.kt
@@ -226,7 +226,7 @@ private fun applyPragmaSettings(db: SupportSQLiteDatabase) {
         db.execSQL("CREATE INDEX IF NOT EXISTS idx_song_totalPlayTime ON song (totalPlayTime)")
         db.execSQL("CREATE INDEX IF NOT EXISTS idx_playlist_song_map_playlistId ON playlist_song_map (playlistId)")
         db.execSQL("CREATE INDEX IF NOT EXISTS idx_playlist_song_map_songId ON playlist_song_map (songId)")
-        db.execSQL("CREATE INDEX IF NOT EXISTS idx_format_songId ON format (songId)")
+        db.execSQL("CREATE INDEX IF NOT EXISTS idx_format_id ON format (id)")
     } catch (e: Exception) {
         Timber.tag("MusicDatabase").e(e, "Failed to set PRAGMA settings")
     }

```
