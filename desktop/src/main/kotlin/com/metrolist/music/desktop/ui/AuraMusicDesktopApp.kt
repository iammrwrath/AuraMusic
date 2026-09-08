package com.metrolist.music.desktop.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metrolist.music.desktop.ui.components.DesktopLyricsPanel
import com.metrolist.music.desktop.ui.components.DesktopNavDestination
import com.metrolist.music.desktop.ui.components.DesktopPlayerBar
import com.metrolist.music.desktop.ui.components.DesktopQueuePanel
import com.metrolist.music.desktop.ui.components.DesktopSidebar
import com.metrolist.music.desktop.ui.components.DesktopTopHeader
import com.metrolist.music.desktop.ui.components.LoginDialog
import com.metrolist.music.desktop.ui.screens.ExploreScreen
import com.metrolist.music.desktop.ui.screens.HomeScreen
import com.metrolist.music.desktop.ui.screens.LibraryScreen
import com.metrolist.music.desktop.ui.screens.SearchScreen
import com.metrolist.music.desktop.ui.screens.SettingsScreen
import com.metrolist.music.desktop.ui.theme.AuraDesktopTheme

@Composable
fun AuraMusicDesktopApp() {
    AuraDesktopTheme {
        var currentDestination by remember { mutableStateOf(DesktopNavDestination.HOME) }
        val navHistory = remember { mutableStateListOf(DesktopNavDestination.HOME) }
        var historyIndex by remember { mutableStateOf(0) }

        var isSidebarManuallyToggled by remember { mutableStateOf<Boolean?>(null) }
        var isLyricsOpen by remember { mutableStateOf(false) }
        var isQueueOpen by remember { mutableStateOf(false) }
        var isLoginDialogOpen by remember { mutableStateOf(false) }
        var activeSearchQuery by remember { mutableStateOf("") }

        fun navigateTo(dest: DesktopNavDestination) {
            if (dest != currentDestination) {
                while (navHistory.size > historyIndex + 1) {
                    navHistory.removeAt(navHistory.size - 1)
                }
                navHistory.add(dest)
                historyIndex = navHistory.size - 1
                currentDestination = dest
            }
        }

        fun goBack() {
            if (historyIndex > 0) {
                historyIndex--
                currentDestination = navHistory[historyIndex]
            }
        }

        fun goForward() {
            if (historyIndex < navHistory.size - 1) {
                historyIndex++
                currentDestination = navHistory[historyIndex]
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            val isWindowWide = maxWidth >= 1100.dp
            val isSidebarExpanded = isSidebarManuallyToggled ?: isWindowWide

            Column(modifier = Modifier.fillMaxSize()) {
                // Top Global Header Bar
                DesktopTopHeader(
                    isSidebarExpanded = isSidebarExpanded,
                    onToggleSidebar = {
                        isSidebarManuallyToggled = !isSidebarExpanded
                    },
                    searchQuery = activeSearchQuery,
                    onSearchQueryChange = { q ->
                        activeSearchQuery = q
                        if (q.isNotBlank() && currentDestination != DesktopNavDestination.SEARCH) {
                            navigateTo(DesktopNavDestination.SEARCH)
                        }
                    },
                    onSearchSubmit = { q ->
                        if (q.isNotBlank()) {
                            activeSearchQuery = q
                            navigateTo(DesktopNavDestination.SEARCH)
                        }
                    },
                    canGoBack = historyIndex > 0,
                    onGoBack = ::goBack,
                    canGoForward = historyIndex < navHistory.size - 1,
                    onGoForward = ::goForward,
                    onSignInClick = { isLoginDialogOpen = true }
                )

                // Main Workspace: Sidebar + Screen + Drawers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Left Navigation Sidebar
                    DesktopSidebar(
                        selectedDestination = currentDestination,
                        onDestinationSelected = { dest ->
                            navigateTo(dest)
                        },
                        isExpanded = isSidebarExpanded,
                        onSignInClick = { isLoginDialogOpen = true }
                    )

                    // Central Dynamic Screen Pane
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Crossfade(targetState = currentDestination) { dest ->
                            when (dest) {
                                DesktopNavDestination.HOME -> HomeScreen(
                                    onNavigateToSearch = { query ->
                                        activeSearchQuery = query
                                        navigateTo(DesktopNavDestination.SEARCH)
                                    }
                                )
                                DesktopNavDestination.SEARCH -> SearchScreen(
                                    initialQuery = activeSearchQuery,
                                    onQueryChange = { activeSearchQuery = it }
                                )
                                DesktopNavDestination.EXPLORE -> ExploreScreen(
                                    onCategoryClick = { categoryName ->
                                        activeSearchQuery = categoryName
                                        navigateTo(DesktopNavDestination.SEARCH)
                                    }
                                )
                                DesktopNavDestination.LIBRARY -> LibraryScreen(
                                    onSignInClick = { isLoginDialogOpen = true }
                                )
                                DesktopNavDestination.SETTINGS -> SettingsScreen(
                                    onSignInClick = { isLoginDialogOpen = true }
                                )
                            }
                        }
                    }

                    // Slide-over Drawers (Right)
                    DesktopLyricsPanel(
                        isOpen = isLyricsOpen,
                        onClose = { isLyricsOpen = false }
                    )

                    DesktopQueuePanel(
                        isOpen = isQueueOpen,
                        onClose = { isQueueOpen = false }
                    )
                }

                // Bottom Persistent Player Bar
                DesktopPlayerBar(
                    isLyricsOpen = isLyricsOpen,
                    onToggleLyrics = {
                        isLyricsOpen = !isLyricsOpen
                        if (isLyricsOpen) isQueueOpen = false
                    },
                    isQueueOpen = isQueueOpen,
                    onToggleQueue = {
                        isQueueOpen = !isQueueOpen
                        if (isQueueOpen) isLyricsOpen = false
                    }
                )
            }

            // Account Sign-in Dialog
            LoginDialog(
                isOpen = isLoginDialogOpen,
                onDismiss = { isLoginDialogOpen = false },
                onLoginSuccess = { isLoginDialogOpen = false }
            )
        }
    }
}
