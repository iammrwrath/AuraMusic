/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.metrolist.music.BuildConfig
import java.io.File
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val description: String,
    val releaseDate: String,
    val assets: List<ReleaseAsset>
) {
    val cleanVersionName: String
        get() = tagName.removePrefix("v")
}

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val architecture: String,
    val variant: String // "foss" or "gms"
)

object Updater {
    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(Duration.ofSeconds(15))
        .readTimeout(Duration.ofSeconds(15))
        .build()

    private val noRedirectClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(10))
        .build()

    var lastCheckTime = -1L
        private set
    
    private var cachedReleaseInfo: ReleaseInfo? = null
    private var cachedAllReleases: List<ReleaseInfo> = emptyList()
    
    private const val CHECK_INTERVAL_MILLIS = 2 * 60 * 60 * 1000L // 2 hours
    private const val GITHUB_API_BASE = "https://api.github.com/repos/iammrwrath/AuraMusic"
    const val APK_NAME = "AuraMusic.apk"

    private fun newApiRequestBuilder(url: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .header("User-Agent", "AuraMusic-Android/${BuildConfig.BASE_VERSION_NAME}")
            .header("Accept", "application/vnd.github.v3+json")
    }

    /**
     * Extracts numeric version components, handling prefixes like 'v' or suffixes/words.
     * E.g. "v13.7.1" -> [13, 7, 1], "AuraMusic v13.7.2 (Patch Release)" -> [13, 7, 2]
     */
     private fun extractVersionParts(version: String): List<Int> {
        val match = Regex("""(\d+(?:\.\d+)*)""").find(version)
        val cleanStr = match?.value ?: version.removePrefix("v").trim()
        return cleanStr.split(".").mapNotNull { segment ->
            segment.takeWhile { it.isDigit() }.toIntOrNull()
        }
    }

    /**
     * Compares two version strings.
     * Returns: 1 if v1 > v2, -1 if v1 < v2, 0 if equal
     */
    fun compareVersions(v1: String, v2: String): Int {
        val v1Parts = extractVersionParts(v1)
        val v2Parts = extractVersionParts(v2)
        val maxLength = maxOf(v1Parts.size, v2Parts.size)
        
        for (i in 0 until maxLength) {
            val part1 = v1Parts.getOrNull(i) ?: 0
            val part2 = v2Parts.getOrNull(i) ?: 0
            when {
                part1 > part2 -> return 1
                part1 < part2 -> return -1
            }
        }
        return 0
    }

    /**
     * Checks if the latest version is newer than the current version.
     * Returns true if an update is available (latestVersion > currentVersion)
     */
    fun isUpdateAvailable(currentVersion: String, latestVersion: String): Boolean {
        return compareVersions(latestVersion, currentVersion) > 0
    }

    /**
     * Get the current app's architecture and variant
     */
    private fun getCurrentAppVariant(): Pair<String, String> {
        val architecture = BuildConfig.ARCHITECTURE
        val variant = if (BuildConfig.CAST_AVAILABLE) "gms" else "foss"
        return architecture to variant
    }

    /**
     * Parse release assets from GitHub API response
     */
    private fun parseAssets(assetsArray: JSONArray): List<ReleaseAsset> {
        val assets = mutableListOf<ReleaseAsset>()
        
        for (i in 0 until assetsArray.length()) {
            val asset = assetsArray.optJSONObject(i) ?: continue
            val name = asset.optString("name", "")
            
            // Skip non-APK files
            if (!name.endsWith(".apk")) continue
            
            val downloadUrl = asset.optString("browser_download_url", "")
            if (downloadUrl.isEmpty()) continue
            val size = asset.optLong("size", 0L)
            
            // Parse architecture and variant from filename
            val (arch, variant) = when {
                name == "AuraMusic.apk" || name == "app-foss-debug.apk" || name == "app-foss-release.apk" -> "universal" to "foss"
                name == "AuraMusic-with-Google-Cast.apk" || name == "app-gms-release.apk" || name == "app-gms-debug.apk" -> "universal" to "gms"
                name.startsWith("app-") && name.endsWith("-release.apk") -> {
                    val arch = name.removePrefix("app-").removeSuffix("-release.apk")
                    arch to "foss"
                }
                name.startsWith("app-") && name.endsWith("-with-Google-Cast.apk") -> {
                    val arch = name.removePrefix("app-").removeSuffix("-with-Google-Cast.apk")
                    arch to "gms"
                }
                else -> "universal" to "foss"
            }
            
            assets.add(ReleaseAsset(name, downloadUrl, size, arch, variant))
        }
        
        return assets
    }

    /**
     * Fallback to GitHub web redirect which has zero API rate limits
     */
    private fun fetchLatestReleaseFromWebRedirect(): ReleaseInfo? {
        return try {
            val headRequest = Request.Builder()
                .url("https://github.com/iammrwrath/AuraMusic/releases/latest")
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .head()
                .build()

            val headResponse = noRedirectClient.newCall(headRequest).execute()
            var tag: String? = null

            val location = headResponse.header("Location")
            if (!location.isNullOrEmpty() && location.contains("/tag/")) {
                tag = location.substringAfterLast("/tag/").substringAfterLast("/").trim()
            }

            if (tag.isNullOrEmpty() || tag.contains("latest")) {
                val getRequest = Request.Builder()
                    .url("https://github.com/iammrwrath/AuraMusic/releases/latest")
                    .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                    .build()
                val getResponse = httpClient.newCall(getRequest).execute()
                val finalUrl = getResponse.request.url.toString()
                if (finalUrl.contains("/tag/")) {
                    tag = finalUrl.substringAfterLast("/tag/").substringAfterLast("/").trim()
                }
            }

            if (!tag.isNullOrEmpty() && !tag.contains("latest")) {
                val cleanTag = tag.removePrefix("v")
                val fallbackAssets = listOf(
                    ReleaseAsset(
                        name = APK_NAME,
                        downloadUrl = "https://github.com/iammrwrath/AuraMusic/releases/download/$tag/$APK_NAME",
                        size = 0L,
                        architecture = "universal",
                        variant = "foss"
                    ),
                    ReleaseAsset(
                        name = "AuraMusic-$tag.apk",
                        downloadUrl = "https://github.com/iammrwrath/AuraMusic/releases/download/$tag/AuraMusic-$tag.apk",
                        size = 0L,
                        architecture = "universal",
                        variant = "foss"
                    ),
                    ReleaseAsset(
                        name = "AuraMusic-$tag.zip",
                        downloadUrl = "https://github.com/iammrwrath/AuraMusic/releases/download/$tag/AuraMusic-$tag.zip",
                        size = 0L,
                        architecture = "universal",
                        variant = "foss"
                    )
                )
                ReleaseInfo(
                    tagName = tag,
                    versionName = "AuraMusic v$cleanTag",
                    description = "AuraMusic v$cleanTag Release",
                    releaseDate = "",
                    assets = fallbackAssets
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Web redirect fallback failed")
            null
        }
    }

    /**
     * Fetch latest release from GitHub API with fallback
     */
    suspend fun getLatestRelease(forceRefresh: Boolean = false): Result<ReleaseInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Return cached if available and not forcing refresh
                if (cachedReleaseInfo != null && !forceRefresh) {
                    return@runCatching cachedReleaseInfo!!
                }

                var releaseInfo: ReleaseInfo? = null

                // 1. Try GitHub REST API with User-Agent & Accept headers
                try {
                    val request = newApiRequestBuilder("$GITHUB_API_BASE/releases/latest").build()
                    val response = httpClient.newCall(request).execute()
                    val bodyString = response.body?.string()

                    if (response.isSuccessful && !bodyString.isNullOrEmpty()) {
                        val json = JSONObject(bodyString)
                        if (json.has("tag_name")) {
                            releaseInfo = ReleaseInfo(
                                tagName = json.getString("tag_name"),
                                versionName = json.optString("name", json.getString("tag_name")),
                                description = json.optString("body", ""),
                                releaseDate = json.optString("published_at", ""),
                                assets = parseAssets(json.optJSONArray("assets") ?: JSONArray())
                            )
                        } else {
                            Timber.w("GitHub API response lacked tag_name: %s", bodyString)
                        }
                    } else {
                        Timber.w("GitHub API returned code %d: %s", response.code, bodyString)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "GitHub API release check failed, attempting fallback")
                }

                // 2. Fallback: Rate-limit-free web redirect check
                if (releaseInfo == null) {
                    releaseInfo = fetchLatestReleaseFromWebRedirect()
                }

                if (releaseInfo != null) {
                    cachedReleaseInfo = releaseInfo
                    lastCheckTime = System.currentTimeMillis()
                    releaseInfo
                } else {
                    throw Exception("Could not retrieve latest release information. Please check your internet connection.")
                }
            }
        }

    /**
     * Fetch all releases from GitHub API (paginated)
     */
    suspend fun getAllReleases(forceRefresh: Boolean = false): Result<List<ReleaseInfo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (cachedAllReleases.isNotEmpty() && !forceRefresh) {
                    return@runCatching cachedAllReleases
                }

                val releases = mutableListOf<ReleaseInfo>()
                var page = 1
                var hasMore = true

                while (hasMore && page <= 5) {
                    val request = newApiRequestBuilder("$GITHUB_API_BASE/releases?page=$page&per_page=30").build()
                    val response = httpClient.newCall(request).execute()
                    val bodyString = response.body?.string()

                    if (!response.isSuccessful || bodyString.isNullOrEmpty() || !bodyString.trimStart().startsWith("[")) {
                        break
                    }

                    val json = JSONArray(bodyString)
                    if (json.length() == 0) {
                        hasMore = false
                        break
                    }

                    for (i in 0 until json.length()) {
                        val releaseObj = json.optJSONObject(i) ?: continue
                        val tagName = releaseObj.optString("tag_name", "")
                        if (tagName.isEmpty()) continue

                        releases.add(
                            ReleaseInfo(
                                tagName = tagName,
                                versionName = releaseObj.optString("name", tagName),
                                description = releaseObj.optString("body", ""),
                                releaseDate = releaseObj.optString("published_at", ""),
                                assets = parseAssets(releaseObj.optJSONArray("assets") ?: JSONArray())
                            )
                        )
                    }

                    page++
                }

                if (releases.isEmpty() && cachedReleaseInfo != null) {
                    releases.add(cachedReleaseInfo!!)
                }

                if (releases.isNotEmpty()) {
                    cachedAllReleases = releases
                }
                releases
            }
        }



    /**
     * Get the download URL for the correct app variant
     */
    fun getDownloadUrlForCurrentVariant(releaseInfo: ReleaseInfo): String? {
        val (currentArch, currentVariant) = getCurrentAppVariant()
        
        return releaseInfo.assets
            .find { it.architecture == currentArch && it.variant == currentVariant }
            ?.downloadUrl
            ?: releaseInfo.assets.find { it.architecture == "universal" && it.variant == currentVariant }?.downloadUrl
            ?: releaseInfo.assets.find { it.variant == currentVariant }?.downloadUrl
            ?: releaseInfo.assets.firstOrNull()?.downloadUrl
    }

    /**
     * Get all available download URLs for a release
     */
    fun getAllDownloadUrls(releaseInfo: ReleaseInfo): Map<String, String> {
        return releaseInfo.assets.associate { "${it.architecture}-${it.variant}" to it.downloadUrl }
    }

    /**
     * Check if update is needed (respects 2-hour cache)
     */
    suspend fun checkForUpdate(forceRefresh: Boolean = false): Result<Pair<ReleaseInfo?, Boolean>> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Check if we should fetch (2 hour interval)
                val shouldFetch = forceRefresh || 
                    (System.currentTimeMillis() - lastCheckTime) > CHECK_INTERVAL_MILLIS
                
                if (!shouldFetch && cachedReleaseInfo != null) {
                    val hasUpdate = isUpdateAvailable(
                        BuildConfig.BASE_VERSION_NAME,
                        cachedReleaseInfo!!.tagName
                    )
                    return@runCatching cachedReleaseInfo!! to hasUpdate
                }
                
                val result = getLatestRelease(forceRefresh = true)
                if (result.isSuccess) {
                    val releaseInfo = result.getOrThrow()
                    val hasUpdate = isUpdateAvailable(
                        BuildConfig.BASE_VERSION_NAME,
                        releaseInfo.tagName
                    )
                    releaseInfo to hasUpdate
                } else {
                    throw result.exceptionOrNull() ?: Exception("Unknown error")
                }
            }
        }

    /**
     * Get the download URL for the correct app variant
     * Returns null if no matching asset is found
     */
    fun getLatestDownloadUrl(): String? {
        return cachedReleaseInfo?.let { getDownloadUrlForCurrentVariant(it) }
    }
    
    /**
     * Get the latest release info (cached)
     */
    fun getCachedLatestRelease(): ReleaseInfo? = cachedReleaseInfo

    /**
     * Downloads the APK file directly to cache directory with progress reporting.
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val okHttpClient = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .readTimeout(java.time.Duration.ofMinutes(5))
                .build()

            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "AuraMusic-Updater")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }

            val body = response.body ?: throw Exception("Empty response body from update server")
            val totalBytes = body.contentLength()

            val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updateDir, "AuraMusic-update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            var bytesRead = 0L
            body.byteStream().use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (totalBytes > 0) {
                            onProgress((bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                }
            }

            if (!apkFile.exists() || apkFile.length() < 1_000_000) {
                throw Exception("Downloaded file is incomplete (${apkFile.length()} bytes)")
            }

            onProgress(1.0f)
            apkFile
        }
    }

    /**
     * Triggers the Android package installer for the downloaded APK.
     * Returns true if the installer was launched, false if unknown sources permission is required.
     */
    fun installApk(context: Context, file: File): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return false
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.FileProvider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to launch package installer")
            false
        }
    }
}
