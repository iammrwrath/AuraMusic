package com.metrolist.music.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.metrolist.music.desktop.ui.AuraMusicDesktopApp
import java.awt.Dimension

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "AuraMusic Desktop",
        state = WindowState(width = 1280.dp, height = 820.dp)
    ) {
        window.minimumSize = Dimension(960, 600)
        AuraMusicDesktopApp()
    }
}
