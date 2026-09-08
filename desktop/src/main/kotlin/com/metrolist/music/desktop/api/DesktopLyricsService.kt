package com.metrolist.music.desktop.api

import com.metrolist.music.desktop.data.LyricLine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object DesktopLyricsService {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 8000
            connectTimeoutMillis = 5000
        }
    }

    suspend fun getLyrics(trackName: String, artistName: String, durationSeconds: Int): List<LyricLine> = withContext(Dispatchers.IO) {
        try {
            val response = httpClient.get("https://lrclib.net/api/get") {
                parameter("track_name", trackName)
                parameter("artist_name", artistName)
                if (durationSeconds > 0) {
                    parameter("duration", durationSeconds)
                }
            }

            val bodyText = response.body<String>()
            val root = json.parseToJsonElement(bodyText).jsonObject
            val syncedLyrics = root["syncedLyrics"]?.jsonPrimitive?.contentOrNull
            val plainLyrics = root["plainLyrics"]?.jsonPrimitive?.contentOrNull

            if (!syncedLyrics.isNullOrBlank()) {
                return@withContext parseLrc(syncedLyrics)
            } else if (!plainLyrics.isNullOrBlank()) {
                return@withContext plainLyrics.lines().mapIndexed { index, line ->
                    LyricLine(timeMs = index * 4000L, text = line)
                }
            }
        } catch (e: Exception) {
            println("[DesktopLyricsService] Error fetching lyrics for $trackName: ${e.message}")
        }
        emptyList()
    }

    fun parseLrc(lrcContent: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val regex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""")

        for (rawLine in lrcContent.lines()) {
            val match = regex.find(rawLine.trim()) ?: continue
            val min = match.groupValues[1].toLongOrNull() ?: 0L
            val sec = match.groupValues[2].toLongOrNull() ?: 0L
            val msPart = match.groupValues[3]
            val ms = if (msPart.length == 2) msPart.toLong() * 10 else msPart.toLong()
            val totalMs = (min * 60 + sec) * 1000 + ms
            val text = match.groupValues[4].trim()

            if (text.isNotBlank()) {
                lines.add(LyricLine(timeMs = totalMs, text = text))
            }
        }
        return lines.sortedBy { it.timeMs }
    }
}
