/**
 * AuraMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.BuildConfig
import com.metrolist.music.utils.ReleaseInfo
import com.metrolist.music.utils.ReleaseAsset
import com.metrolist.music.utils.Updater

private val markdownLinkRegex = Regex("(@[a-zA-Z0-9_-]+)|(https?://[\\w-]+(\\.[\\w-]+)+[\\w.,@?^=%&:/~+#-]*[\\w@?^=%&/~+#-])")

/** Built-in static changelog shown when GitHub releases haven't been published yet. */
private val AURAMUSIC_STATIC_CHANGELOG = listOf(
    ReleaseInfo(
        tagName = BuildConfig.VERSION_NAME,
        versionName = "AuraMusic ${BuildConfig.VERSION_NAME}",
        releaseDate = "2026-09-05",
        description = """
## 🎧 AuraMusic v13.7.0 — Performance & Stability (Minor Release)

### ⚡ Performance & Stutter Elimination
- **True R8 Release Pipeline**: Transitioned build to `:app:assembleFossRelease` with full R8 code shrinking, dead code removal, and AOT compilation, cutting APK size by 49% (from 50.2 MB to 25.5 MB) and removing all Compose debug tracing overhead.
- **Root Subcomposition Elimination**: Removed `BoxWithConstraints` at the root of `MainActivity`, eliminating deferred two-pass measurement and layout across the entire UI tree on every frame.
- **Pixel Physical Resolution Matching**: Enhanced display refresh rate mode selection to match the active physical display resolution (1080p vs 1440p) before locking to 120Hz/90Hz, preventing compositor hitches.
- **Smooth Navigation Transitions**: Optimized bottom navigation tab switches with lightweight fades (`fadeIn`/`fadeOut`) to eliminate heavy concurrent two-screen horizontal translations during tab switches.
- **Instant Thumbnail Rendering**: Disabled redundant Coil crossfade animations on list and grid thumbnails (`crossfade(false)`) so cached artwork renders immediately without alpha-blending overhead during flings.

### 🚀 Google Pixel & Scrolling Performance
- **120Hz Smooth Display Fix**: Resolved an issue where Pixel devices throttled down to 60Hz during Compose scrolling. Hardware display modes are now dynamically queried and locked to the maximum available refresh rate (120Hz/90Hz).
- **List & Grid Scrolling Overhaul**: Eliminated hundreds of redundant DataStore flow subscriptions across song lists and album grids by hoisting swipe and album art preferences to root `CompositionLocal`s, drastically lowering garbage collection overhead and eliminating fling stutter.
- **Scroll Behavior Optimization**: Removed duplicate nested scroll connections that fired redundant computations on every scroll event.

### 🎨 Visual Identity & Android 13+ Themed Icons
- **Refreshed App Identity**: Cleanly branded as **AuraMusic** across all launcher targets and settings without debug suffixes.
- **New App Icon**: Minimalist iridescent glowing musical aura ring with an integrated neon music note.
- **Pixel Adaptive & Themed Icons**: High-resolution foregrounds tailored for Android 12+ adaptive masks, plus a native vector monochrome icon for Android 13+ Material You "Themed Icons".

### ℹ️ About Screen & Developer Support
- **Maintainer Focus**: Streamlined the About screen to highlight **@iammrwrath** as the sole Creator & Maintainer.
- **1-Tap Developer Support**: Added direct buttons for the GitHub profile and developer support email (`iammrwrath@gmail.com`).
- **Framework Attribution**: Clean attribution cards honoring foundational upstream engines (Metrolist & BitChord).
- **Cleaned Community Links**: Removed obsolete third-party chat and coffee donation links.

### 🎛️ Studio-Grade Automix DJ Transitions
- **Constant Equal-Power Law**: Sinusoidal (sin² + cos² ≡ 1.0) volume curves ensure consistent acoustic power throughout crossfades, eliminating midpoint volume dips.
- **Smart Automix (DJ Mode)**: Automatically scales transition duration to 15% of track length and trims encoder pre-roll silence (~200ms) for punchy beat transitions.
- **Bass-Swap Crossover**: Smoothly filters out outgoing low frequencies past 55% to eliminate muddy low-end clashes between kicks and basslines.
- **Customizable**: Off / Equal-Power Crossfade / Smart Automix selectable in Player Settings.

### 🚗 Android Auto Live Lyrics Projection
- **Real-Time Karaoke Projection**: Streams synchronized lyrics directly onto car head unit displays via the MediaSession subtitle field.
- **Interlude Awareness**: Displays 🎤 [Lyrics] during singing and 🎵 [Intro] or 🎵 [Instrumental] during instrumental breaks.
- **Car Screen Controls**: Dedicated on-screen toggle button to enable or disable lyric projection with a single tap.

### ⚡ Engine & Architecture Scalability
- **Non-Blocking Background Dispatchers**: Migrated heavy YouTube API calls, artist queries, and playlist repair transactions off the Main thread onto Dispatchers.IO.
- **Atomic Database Transactions**: Batched Android Auto search queries into single SQLite transactions, preventing disk thrashing and UI freezes.
- **Zero-Block Player Transitions**: Replaced synchronous runBlocking calls during playback crossfades with fast in-memory player state checks.
- **AI Diagnostics & Flight Recorder**: 1-tap issue reporter and flight recorder log exporter to quickly capture and diagnose session errors.

### 📦 Universal Release
- **Single Universal APK**: Unified build pipeline delivering one single universal APK (`AuraMusic.apk`) for seamless installation and updates.
        """.trimIndent(),
        assets = emptyList()
    )
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChangelogScreen(
    onDismiss: () -> Unit
) {
    var releases by remember { mutableStateOf<List<ReleaseInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) {
        Updater.getAllReleases().onSuccess { allReleases ->
            releases = if (allReleases.isNotEmpty()) {
                allReleases.filter { release ->
                    Updater.compareVersions(BuildConfig.BASE_VERSION_NAME, release.tagName) >= 0
                }
            } else {
                AURAMUSIC_STATIC_CHANGELOG
            }
            isLoading = false
        }.onFailure {
            releases = AURAMUSIC_STATIC_CHANGELOG
            isLoading = false
        }
    }

    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)

    val showFab by remember {
        derivedStateOf { sheetState.targetValue != SheetValue.Hidden }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.changelog),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                item {
                    val density = LocalDensity.current
                    val stroke = remember(density) {
                        Stroke(width = with(density) { 3.dp.toPx() }, cap = StrokeCap.Round)
                    }
                    LinearWavyProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent,
                        stroke = stroke,
                        trackStroke = stroke,
                        amplitude = { 1f }
                    )
                }

                if (isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (releases.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text(text = stringResource(R.string.changelog_empty))
                        }
                    }
                } else {
                    items(releases) { release ->
                        ReleaseItem(release)
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showFab,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                val githubReleasesUrl = stringResource(R.string.github_releases_url)
                ExtendedFloatingActionButton(
                    onClick = { uriHandler.openUri(githubReleasesUrl) },
                    icon = { Icon(painterResource(R.drawable.github), contentDescription = null, modifier = Modifier.size(24.dp)) },
                    text = { Text(stringResource(R.string.view_on_github)) },
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ReleaseItem(release: ReleaseInfo) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape
            ) {
                Text(
                    text = release.tagName,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Text(
                text = release.releaseDate.split("T").firstOrNull() ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                MarkdownText(release.description)
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
fun MarkdownText(text: String) {
    val lines = text.split("\n")
    val uriHandler = LocalUriHandler.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.filter { it.isNotBlank() }.forEach { line ->
            val trimmedLine = line.trim()

            if (trimmedLine.startsWith("#")) {
                val level = trimmedLine.takeWhile { it == '#' }.length
                val headerText = trimmedLine.substring(level).trim()
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = headerText,
                        style = when (level) {
                            1 -> MaterialTheme.typography.headlineMedium
                            2 -> MaterialTheme.typography.headlineSmall
                            else -> MaterialTheme.typography.titleMedium
                        },
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val isListItem = trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ")
                val contentText = if (isListItem) {
                    trimmedLine.substring(2).trim()
                } else {
                    trimmedLine
                }

                val annotatedString = buildAnnotatedString {
                    var lastIndex = 0
                    markdownLinkRegex.findAll(contentText).forEach { result ->
                        append(contentText.substring(lastIndex, result.range.first))
                        
                        val match = result.value
                        val link = if (match.startsWith("@")) "https://github.com/${match.substring(1)}" else match
                        
                        pushStringAnnotation(tag = "URL", annotation = link)
                        withStyle(style = SpanStyle(
                            color = MaterialTheme.colorScheme.primary, 
                            fontWeight = if (match.startsWith("@")) FontWeight.Bold else FontWeight.Normal,
                            textDecoration = if (match.startsWith("@")) TextDecoration.None else TextDecoration.Underline
                        )) {
                            append(match)
                        }
                        pop()
                        lastIndex = result.range.last + 1
                    }
                    append(contentText.substring(lastIndex))
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        if (isListItem) {
                            Text(
                                text = stringResource(R.string.list_bullet),
                                modifier = Modifier.padding(end = 8.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        ClickableText(
                            text = annotatedString,
                            style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            onClick = { offset ->
                                annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                    .firstOrNull()?.let { annotation ->
                                        uriHandler.openUri(annotation.item)
                                    }
                            }
                        )
                    }
                    
                    if (isListItem) {
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}
