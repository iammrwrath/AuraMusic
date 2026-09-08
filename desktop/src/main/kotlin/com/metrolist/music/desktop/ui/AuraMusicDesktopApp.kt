package com.metrolist.music.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.metrolist.music.desktop.ui.components.DesktopLyricsPanel
import com.metrolist.music.desktop.ui.components.DesktopNavDestination
import com.metrolist.music.desktop.ui.components.DesktopPlayerBar
import com.metrolist.music.desktop.ui.components.DesktopQueuePanel
import com.metrolist.music.desktop.ui.components.DesktopSidebar
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
        var isLyricsOpen by remember { mutableStateOf(false) }
        var isQueueOpen by remember { mutableStateOf(false) }
        var isLoginDialogOpen by remember { mutableStateOf(false) }
        var activeSearchQuery by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Main Top Workspace (Sidebar + Screen + Drawers)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Left Navigation Sidebar
                DesktopSidebar(
                    selectedDestination = currentDestination,
                    onDestinationSelected = { destination ->
                        currentDestination = destination
                    },
                    onSignInClick = {
                        isLoginDialogOpen = true
                    }
                )

                // Central Dynamic Screen Pane
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    when (currentDestination) {
                        DesktopNavDestination.HOME -> HomeScreen()
                        DesktopNavDestination.SEARCH -> SearchScreen(initialQuery = activeSearchQuery)
                        DesktopNavDestination.EXPLORE -> ExploreScreen(
                            onCategoryClick = { categoryName ->
                                activeSearchQuery = categoryName
                                currentDestination = DesktopNavDestination.SEARCH
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
