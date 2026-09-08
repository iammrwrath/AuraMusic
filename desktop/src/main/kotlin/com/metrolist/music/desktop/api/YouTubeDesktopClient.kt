package com.metrolist.music.desktop.api

import com.metrolist.music.desktop.data.DesktopTrack
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object YouTubeDesktopClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15000
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 15000
        }
        defaultRequest {
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            header("Origin", "https://music.youtube.com")
            header("Referer", "https://music.youtube.com/")
            header("X-YouTube-Client-Name", "67")
            header("X-YouTube-Client-Version", "1.20240901.01.00")
        }
    }

    private fun clientContext(clientName: String = "WEB_REMIX", clientVersion: String = "1.20240901.01.00"): String {
        return """
            {
                "context": {
                    "client": {
                        "clientName": "$clientName",
                        "clientVersion": "$clientVersion",
                        "hl": "en",
                        "gl": "US"
                    }
                }
            }
        """.trimIndent()
    }

    suspend fun search(query: String): List<DesktopTrack> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val bodyPayload = """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB_REMIX",
                            "clientVersion": "1.20240901.01.00",
                            "hl": "en",
                            "gl": "US"
                        }
                    },
                    "query": "${query.replace("\"", "\\\"")}"
                }
            """.trimIndent()

            val response = httpClient.post("https://music.youtube.com/youtubei/v1/search") {
                contentType(ContentType.Application.Json)
                setBody(bodyPayload)
            }

            val root = json.parseToJsonElement(response.body<String>()).jsonObject
            parseSearchTracks(root)
        } catch (e: Exception) {
            println("[YouTubeDesktopClient] Search error: ${e.message}")
            emptyList()
        }
    }

    suspend fun getHomeFeed(): List<DesktopTrack> = withContext(Dispatchers.IO) {
        try {
            val bodyPayload = """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB_REMIX",
                            "clientVersion": "1.20240901.01.00",
                            "hl": "en",
                            "gl": "US"
                        }
                    },
                    "browseId": "FEmusic_home"
                }
            """.trimIndent()

            val response = httpClient.post("https://music.youtube.com/youtubei/v1/browse") {
                contentType(ContentType.Application.Json)
                setBody(bodyPayload)
            }

            val root = json.parseToJsonElement(response.body<String>()).jsonObject
            parseBrowseTracks(root)
        } catch (e: Exception) {
            println("[YouTubeDesktopClient] Home feed error: ${e.message}")
            emptyList()
        }
    }

    suspend fun getStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val bodyPayload = """
                {
                    "context": {
                        "client": {
                            "clientName": "ANDROID",
                            "clientVersion": "19.29.35",
                            "hl": "en",
                            "gl": "US",
                            "androidSdkVersion": 34
                        }
                    },
                    "videoId": "$videoId",
                    "contentCheckOk": true,
                    "racyCheckOk": true
                }
            """.trimIndent()

            val response = httpClient.post("https://www.youtube.com/youtubei/v1/player") {
                contentType(ContentType.Application.Json)
                setBody(bodyPayload)
            }

            val root = json.parseToJsonElement(response.body<String>()).jsonObject
            val streamingData = root["streamingData"]?.jsonObject ?: return@withContext null
            val adaptiveFormats = streamingData["adaptiveFormats"]?.jsonArray ?: JsonArray(emptyList())

            // Pick standard audio format (preferably AAC audio/mp4 itag 140 or highest audio bitrate)
            var bestUrl: String? = null
            var bestBitrate = 0

            for (fmtElem in adaptiveFormats) {
                val fmt = fmtElem.jsonObject
                val mimeType = fmt["mimeType"]?.jsonPrimitive?.contentOrNull ?: ""
                val url = fmt["url"]?.jsonPrimitive?.contentOrNull
                val bitrate = fmt["bitrate"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                val itag = fmt["itag"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0

                if (mimeType.startsWith("audio/") && url != null) {
                    if (itag == 140) { // AAC 128kbps is universally supported by JavaFX Media
                        return@withContext url
                    }
                    if (bitrate > bestBitrate) {
                        bestBitrate = bitrate
                        bestUrl = url
                    }
                }
            }
            bestUrl
        } catch (e: Exception) {
            println("[YouTubeDesktopClient] getStreamUrl error for $videoId: ${e.message}")
            null
        }
    }

    private fun parseSearchTracks(root: JsonObject): List<DesktopTrack> {
        val tracks = mutableListOf<DesktopTrack>()
        try {
            val contents = root["contents"]?.jsonObject
                ?.get("tabbedSearchResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray ?: return emptyList()

            for (section in contents) {
                val shelf = section.jsonObject["musicShelfRenderer"]?.jsonObject
                    ?: section.jsonObject["musicCardShelfRenderer"]?.jsonObject
                    ?: continue

                val items = shelf["contents"]?.jsonArray ?: continue
                for (item in items) {
                    val renderer = item.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject ?: continue
                    val track = parseListItem(renderer)
                    if (track != null) {
                        tracks.add(track)
                    }
                }
            }
        } catch (e: Exception) {
            println("[YouTubeDesktopClient] parseSearchTracks error: ${e.message}")
        }
        return tracks
    }

    private fun parseBrowseTracks(root: JsonObject): List<DesktopTrack> {
        val tracks = mutableListOf<DesktopTrack>()
        try {
            val sections = root["contents"]?.jsonObject
                ?.get("singleColumnBrowseResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray ?: return emptyList()

            for (section in sections) {
                val carousel = section.jsonObject["musicCarouselShelfRenderer"]?.jsonObject
                val shelf = section.jsonObject["musicShelfRenderer"]?.jsonObject
                val items = carousel?.get("contents")?.jsonArray ?: shelf?.get("contents")?.jsonArray ?: continue

                for (item in items) {
                    val responsive = item.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject
                    val twoRow = item.jsonObject["musicTwoRowItemRenderer"]?.jsonObject

                    if (responsive != null) {
                        val track = parseListItem(responsive)
                        if (track != null) tracks.add(track)
                    } else if (twoRow != null) {
                        val track = parseTwoRowItem(twoRow)
                        if (track != null) tracks.add(track)
                    }
                }
            }
        } catch (e: Exception) {
            println("[YouTubeDesktopClient] parseBrowseTracks error: ${e.message}")
        }
        return tracks
    }

    private fun parseListItem(renderer: JsonObject): DesktopTrack? {
        val flexColumns = renderer["flexColumns"]?.jsonArray ?: return null
        if (flexColumns.size < 2) return null

        val titleRuns = flexColumns[0].jsonObject["musicResponsiveListItemFlexColumnRenderer"]?.jsonObject
            ?.get("text")?.jsonObject?.get("runs")?.jsonArray ?: return null
        val title = titleRuns.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull ?: return null

        val subtitleRuns = flexColumns[1].jsonObject["musicResponsiveListItemFlexColumnRenderer"]?.jsonObject
            ?.get("text")?.jsonObject?.get("runs")?.jsonArray ?: JsonArray(emptyList())

        val artist = subtitleRuns.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"

        val videoId = renderer["playlistItemData"]?.jsonObject?.get("videoId")?.jsonPrimitive?.contentOrNull
            ?: titleRuns.firstOrNull()?.jsonObject?.get("navigationEndpoint")?.jsonObject
                ?.get("watchEndpoint")?.jsonObject?.get("videoId")?.jsonPrimitive?.contentOrNull
            ?: renderer["overlay"]?.jsonObject?.get("musicItemThumbnailOverlayRenderer")?.jsonObject
                ?.get("content")?.jsonObject?.get("musicPlayButtonRenderer")?.jsonObject
                ?.get("playNavigationEndpoint")?.jsonObject?.get("watchEndpoint")?.jsonObject
                ?.get("videoId")?.jsonPrimitive?.contentOrNull
            ?: return null

        val thumbnails = renderer["thumbnail"]?.jsonObject
            ?.get("musicThumbnailRenderer")?.jsonObject
            ?.get("thumbnail")?.jsonObject
            ?.get("thumbnails")?.jsonArray

        val thumbUrl = thumbnails?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull

        return DesktopTrack(
            id = videoId,
            title = title,
            artist = artist,
            thumbnailUrl = thumbUrl,
            durationSeconds = 180,
        )
    }

    private fun parseTwoRowItem(renderer: JsonObject): DesktopTrack? {
        val title = renderer["title"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull ?: return null

        val videoId = renderer["navigationEndpoint"]?.jsonObject
            ?.get("watchEndpoint")?.jsonObject?.get("videoId")?.jsonPrimitive?.contentOrNull
            ?: renderer["onTap"]?.jsonObject?.get("watchEndpoint")?.jsonObject
                ?.get("videoId")?.jsonPrimitive?.contentOrNull ?: return null

        val subtitle = renderer["subtitle"]?.jsonObject?.get("runs")?.jsonArray
            ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: "" } ?: "YouTube Music"

        val thumbUrl = renderer["thumbnailRenderer"]?.jsonObject
            ?.get("musicThumbnailRenderer")?.jsonObject
            ?.get("thumbnail")?.jsonObject
            ?.get("thumbnails")?.jsonArray?.lastOrNull()
            ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull

        return DesktopTrack(
            id = videoId,
            title = title,
            artist = subtitle,
            thumbnailUrl = thumbUrl,
            durationSeconds = 200,
        )
    }
}
