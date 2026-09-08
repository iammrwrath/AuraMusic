package com.metrolist.music.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

object DesktopStorage {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val dataDir: File by lazy {
        val os = System.getProperty("os.name").lowercase()
        val baseDir = when {
            os.contains("win") -> {
                val appData = System.getenv("APPDATA")
                if (!appData.isNullOrBlank()) File(appData, "AuraMusic")
                else File(System.getProperty("user.home"), ".auramusic")
            }
            os.contains("mac") -> File(System.getProperty("user.home"), "Library/Application Support/AuraMusic")
            else -> File(System.getProperty("user.home"), ".auramusic")
        }
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        baseDir
    }

    private val storageFile: File by lazy {
        File(dataDir, "user_data.json")
    }

    private val _userData = MutableStateFlow(loadInitialData())
    val userData: StateFlow<DesktopUserData> = _userData.asStateFlow()

    private fun loadInitialData(): DesktopUserData {
        return try {
            if (storageFile.exists()) {
                val content = storageFile.readText()
                json.decodeFromString<DesktopUserData>(content)
            } else {
                DesktopUserData()
            }
        } catch (e: Exception) {
            println("[DesktopStorage] Error loading user data: ${e.message}")
            DesktopUserData()
        }
    }

    suspend fun save() = withContext(Dispatchers.IO) {
        try {
            val content = json.encodeToString(DesktopUserData.serializer(), _userData.value)
            val tempFile = File(dataDir, "user_data.json.tmp")
            tempFile.writeText(content)
            if (tempFile.exists()) {
                if (storageFile.exists()) {
                    storageFile.delete()
                }
                tempFile.renameTo(storageFile)
            }
        } catch (e: Exception) {
            println("[DesktopStorage] Error saving user data: ${e.message}")
        }
    }

    fun isFavorite(trackId: String): Boolean {
        return _userData.value.favorites.any { it.id == trackId }
    }

    suspend fun toggleFavorite(track: DesktopTrack) {
        val current = _userData.value.favorites
        val isFav = current.any { it.id == track.id }
        val updated = if (isFav) {
            current.filter { it.id != track.id }
        } else {
            listOf(track) + current
        }
        _userData.value = _userData.value.copy(favorites = updated)
        save()
    }

    suspend fun addToHistory(track: DesktopTrack) {
        val current = _userData.value.history.filter { it.id != track.id }
        val updated = (listOf(track) + current).take(100)
        _userData.value = _userData.value.copy(history = updated)
        save()
    }

    suspend fun updateSettings(transform: (DesktopUserSettings) -> DesktopUserSettings) {
        val current = _userData.value.settings
        _userData.value = _userData.value.copy(settings = transform(current))
        save()
    }
}
