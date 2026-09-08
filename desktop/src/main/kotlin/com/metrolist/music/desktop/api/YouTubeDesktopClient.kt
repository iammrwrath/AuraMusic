package com.metrolist.music.desktop.api

import com.metrolist.music.desktop.data.DesktopAccount
import com.metrolist.music.desktop.data.DesktopPlaylist
import com.metrolist.music.desktop.data.DesktopStorage
import com.metrolist.music.desktop.data.DesktopTrack
import java.security.MessageDigest
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

    fun parseCookieString(rawCookie: String): Map<String, String> {
        return rawCookie.split(";")
            .mapNotNull {
                val parts = it.trim().split("=", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
            }.toMap()
    }

    fun getSapisidHash(sapisid: String, origin: String = "https://music.youtube.com"): String {
        val time = System.currentTimeMillis() / 1000
        val md = MessageDigest.getInstance("SHA-1")
        val hashBytes = md.digest("$time $sapisid $origin".toByteArray(Charsets.UTF_8))
        val hash = hashBytes.joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${time}_$hash"
    }

    fun normalizeCookie(input: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return try {
                val array = json.parseToJsonElement(trimmed).jsonArray
                array.mapNotNull {
                    val obj = it.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull
                    val value = obj["value"]?.jsonPrimitive?.contentOrNull
                    if (name != null && value != null) "$name=$value" else null
                }.joinToString("; ")
            } catch (e: Exception) {
                trimmed
            }
        }
        if (trimmed.startsWith("Cookie:", ignoreCase = true)) {
            return trimmed.substringAfter(":").trim()
        }
        return trimmed
    }

    suspend fun validateAndLogin(rawInput: String): Result<DesktopAccount> = withContext(Dispatchers.IO) {
        val cookie = normalizeCookie(rawInput)
        val cookieMap = parseCookieString(cookie)
        val sapisid = cookieMap["SAPISID"] ?: cookieMap["__Secure-3PAPISID"]
            ?: cookieMap["__Secure-1PAPISID"] ?: cookieMap["APISID"]

        if (sapisid.isNullOrBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("No SAPISID cookie found. Make sure you are logged into https://music.youtube.com and copied your session cookies.")
            )
        }

        val authHeader = getSapisidHash(sapisid)
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
                    }
                }
            """.trimIndent()

            val response = httpClient.post("https://music.youtube.com/youtubei/v1/account/account_menu") {
                contentType(ContentType.Application.Json)
                header("Cookie", cookie)
                header("Authorization", authHeader)
                header("X-Origin", "https://music.youtube.com")
                setBody(bodyPayload)
            }

            val root = json.parseToJsonElement(response.body<String>()).jsonObject
            val actions = root["actions"]?.jsonArray
            val renderer = actions?.firstOrNull()?.jsonObject
                ?.get("openPopupAction")?.jsonObject
                ?.get("popup")?.jsonObject
                ?.get("multiPageMenuRenderer")?.jsonObject
                ?.get("header")?.jsonObject
                ?.get("activeAccountHeaderRenderer")?.jsonObject

            val name = renderer?.get("accountName")?.jsonObject
                ?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull ?: "YouTube Music User"

            val email = renderer?.get("email")?.jsonObject
                ?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull ?: ""

            val handle = renderer?.get("channelHandle")?.jsonObject
                ?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull ?: ""

            val avatarUrl = renderer?.get("accountPhoto")?.jsonObject
                ?.get("thumbnails")?.jsonArray?.lastOrNull()?.jsonObject
                ?.get("url")?.jsonPrimitive?.contentOrNull

            val account = DesktopAccount(
                name = name,
                email = email,
                channelHandle = handle,
                avatarUrl = avatarUrl,
                cookie = cookie,
                isLoggedIn = true
            )

            DesktopStorage.saveAccount(account)
            Result.success(account)
        } catch (e: Exception) {
            val fallbackAccount = DesktopAccount(
                name = "Connected Account",
                cookie = cookie,
                isLoggedIn = true
            )
            DesktopStorage.saveAccount(fallbackAccount)
            Result.success(fallbackAccount)
        }
    }

    suspend fun getUserPlaylists(): List<DesktopPlaylist> = withContext(Dispatchers.IO) {
        val account = DesktopStorage.userData.value.account
        if (!account.isLoggedIn || account.cookie.isBlank()) return@withContext emptyList()
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
                    "browseId": "FEmusic_liked_playlists"
                }
            """.trimIndent()

            val response = httpClient.post("https://music.youtube.com/youtubei/v1/browse") {
                contentType(ContentType.Application.Json)
                header("Cookie", account.cookie)
                val sapisid = parseCookieString(account.cookie)["SAPISID"] ?: parseCookieString(account.cookie)["__Secure-3PAPISID"]
                if (!sapisid.isNullOrBlank()) {
                    header("Authorization", getSapisidHash(sapisid))
                }
                setBody(bodyPayload)
            }
            val root = json.parseToJsonElement(response.body<String>()).jsonObject
            parseBrowsePlaylists(root)
        } catch (e: Exception) {
            println("[YouTubeDesktopClient] getUserPlaylists error: ${e.message}")
            emptyList()
        }
    }

    suspend fun getPlaylistTracks(playlistId: String): List<DesktopTrack> = withContext(Dispatchers.IO) {
        val account = DesktopStorage.userData.value.account
        try {
            val browseId = if (playlistId.startsWith("VL") || playlistId.startsWith("FE")) playlistId else "VL$playlistId"
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
                    "browseId": "$browseId"
                }
            """.trimIndent()

            val response = httpClient.post("https://music.youtube.com/youtubei/v1/browse") {
                contentType(ContentType.Application.Json)
                if (account.isLoggedIn && account.cookie.isNotBlank()) {
                    header("Cookie", account.cookie)
                    val sapisid = parseCookieString(account.cookie)["SAPISID"] ?: parseCookieString(account.cookie)["__Secure-3PAPISID"]
                    if (!sapisid.isNullOrBlank()) {
                        header("Authorization", getSapisidHash(sapisid))
                    }
                }
                setBody(bodyPayload)
            }
            val root = json.parseToJsonElement(response.body<String>()).jsonObject
            parsePlaylistTracks(root)
        } catch (e: Exception) {
            println("[YouTubeDesktopClient] getPlaylistTracks error: ${e.message}")
            emptyList()
        }
    }

    suspend fun getHomeFeed(): List<DesktopTrack> = withContext(Dispatchers.IO) {
        val account = DesktopStorage.userData.value.account
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
                if (account.isLoggedIn && account.cookie.isNotBlank()) {
                    header("Cookie", account.cookie)
                    val sapisid = parseCookieString(account.cookie)["SAPISID"] ?: parseCookieString(account.cookie)["__Secure-3PAPISID"]
                    if (!sapisid.isNullOrBlank()) {
                        header("Authorization", getSapisidHash(sapisid))
                    }
                }
                setBody(bodyPayload)
            }

            val root = json.parseToJsonElement(response.body<String>()).jsonObject
            val parsed = parseBrowseTracks(root)
            if (parsed.isNotEmpty()) {
                return@withContext parsed
            }
        } catch (e: Exception) {
            println("[YouTubeDesktopClient] Home feed error: ${e.message}")
        }
        // Fallback: search for top trending music so feed is never empty
        search("Top Trending Music")
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

    private fun parseBrowsePlaylists(root: JsonObject): List<DesktopPlaylist> {
        val playlists = mutableListOf<DesktopPlaylist>()
        try {
            val sections = root["contents"]?.jsonObject
                ?.get("singleColumnBrowseResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray ?: return emptyList()

            for (section in sections) {
                val grid = section.jsonObject["gridRenderer"]?.jsonObject
                val items = grid?.get("items")?.jsonArray ?: continue
                for (item in items) {
                    val twoRow = item.jsonObject["musicTwoRowItemRenderer"]?.jsonObject ?: continue
                    val title = twoRow["title"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull ?: continue
                    val playlistId = twoRow["navigationEndpoint"]?.jsonObject
                        ?.get("browseEndpoint")?.jsonObject?.get("browseId")?.jsonPrimitive?.contentOrNull ?: continue
                    val thumbUrl = twoRow["thumbnailRenderer"]?.jsonObject
                        ?.get("musicThumbnailRenderer")?.jsonObject
                        ?.get("thumbnail")?.jsonObject
                        ?.get("thumbnails")?.jsonArray?.lastOrNull()
                        ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull

                    playlists.add(DesktopPlaylist(id = playlistId, title = title, thumbnailUrl = thumbUrl))
                }
            }
        } catch (e: Exception) {
            println("[YouTubeDesktopClient] parseBrowsePlaylists error: ${e.message}")
        }
        return playlists
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

    private fun parsePlaylistTracks(root: JsonObject): List<DesktopTrack> {
        val tracks = mutableListOf<DesktopTrack>()
        try {
            fun searchForListItems(element: JsonElement) {
                when (element) {
                    is JsonObject -> {
                        val renderer = element["musicResponsiveListItemRenderer"]?.jsonObject
                        if (renderer != null) {
                            val track = parseListItem(renderer)
                            if (track != null) tracks.add(track)
                        } else {
                            for (child in element.values) {
                                searchForListItems(child)
                            }
                        }
                    }
                    is JsonArray -> {
                        for (child in element) {
                            searchForListItems(child)
                        }
                    }
                    else -> {}
                }
            }
            searchForListItems(root)
        } catch (e: Exception) {
            println("[YouTubeDesktopClient] parsePlaylistTracks error: ${e.message}")
        }
        return tracks
    }
}
