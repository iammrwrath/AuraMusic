package com.metrolist.music.desktop.ui.components

import com.metrolist.music.desktop.api.YouTubeDesktopClient
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.SwingUtilities

object DirectLoginManager {
    private val scope = CoroutineScope(Dispatchers.Swing)
    private var currentDialog: JDialog? = null
    private var pollJob: Job? = null

    init {
        // Ensure default cookie manager is active
        if (CookieHandler.getDefault() == null) {
            CookieHandler.setDefault(CookieManager(null, CookiePolicy.ACCEPT_ALL))
        }
    }

    fun openDirectLogin(
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        if (currentDialog?.isVisible == true) {
            currentDialog?.toFront()
            return
        }

        SwingUtilities.invokeLater {
            val dialog = JDialog()
            currentDialog = dialog
            dialog.title = "Sign In with Google - YouTube Music"
            dialog.isModal = false
            dialog.setSize(640, 800)
            dialog.minimumSize = Dimension(540, 640)
            dialog.setLocationRelativeTo(null)
            dialog.layout = BorderLayout()

            val topPanel = JPanel(BorderLayout(12, 0)).apply {
                background = Color(24, 24, 27)
                border = BorderFactory.createEmptyBorder(12, 18, 12, 18)
            }
            val statusLabel = JLabel("Loading Google Sign-In...").apply {
                foreground = Color.WHITE
                font = font.deriveFont(java.awt.Font.BOLD, 13f)
            }
            topPanel.add(statusLabel, BorderLayout.CENTER)

            val progressBar = JProgressBar().apply {
                isIndeterminate = true
                isVisible = true
                preferredSize = Dimension(120, 16)
            }
            topPanel.add(progressBar, BorderLayout.EAST)
            dialog.add(topPanel, BorderLayout.NORTH)

            val jfxPanel = JFXPanel()
            jfxPanel.preferredSize = Dimension(640, 700)
            dialog.add(jfxPanel, BorderLayout.CENTER)

            val bottomPanel = JPanel(BorderLayout()).apply {
                background = Color(24, 24, 27)
                border = BorderFactory.createEmptyBorder(10, 18, 10, 18)
            }
            val hintLabel = JLabel("Sign in with your Google account. AuraMusic will detect and sync your session.").apply {
                foreground = Color(160, 160, 160)
                font = font.deriveFont(11f)
            }
            bottomPanel.add(hintLabel, BorderLayout.WEST)

            val btnGroup = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                isOpaque = false
            }
            val reloadBtn = JButton("Reload Page").apply {
                isFocusPainted = false
            }
            val checkBtn = JButton("I'm Signed In").apply {
                isFocusPainted = false
            }
            btnGroup.add(reloadBtn)
            btnGroup.add(checkBtn)
            bottomPanel.add(btnGroup, BorderLayout.EAST)
            dialog.add(bottomPanel, BorderLayout.SOUTH)

            var webViewRef: WebView? = null

            fun collectAllCookies(): String {
                val collected = mutableMapOf<String, String>()

                // 1. From default CookieManager store
                val handler = CookieHandler.getDefault()
                if (handler is CookieManager) {
                    try {
                        for (cookie in handler.cookieStore.cookies) {
                            if (cookie.name.isNotBlank() && cookie.value.isNotBlank()) {
                                collected[cookie.name] = cookie.value
                            }
                        }
                    } catch (_: Exception) {}
                }

                // 2. From URI lookups
                val testUris = listOf(
                    "https://music.youtube.com/",
                    "https://www.youtube.com/",
                    "https://accounts.google.com/",
                    "https://google.com/"
                )
                for (uriStr in testUris) {
                    try {
                        val headerMap = handler?.get(URI.create(uriStr), emptyMap())
                        val cookies = headerMap?.get("Cookie")
                        cookies?.forEach { cStr ->
                            cStr.split(";").forEach { pair ->
                                val parts = pair.trim().split("=", limit = 2)
                                if (parts.size == 2 && parts[0].isNotBlank()) {
                                    collected[parts[0].trim()] = parts[1].trim()
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                // 3. From JavaScript DOM
                try {
                    Platform.runLater {
                        try {
                            val docCookie = (webViewRef?.engine?.executeScript("document.cookie") as? String).orEmpty()
                            if (docCookie.isNotBlank()) {
                                docCookie.split(";").forEach { pair ->
                                    val parts = pair.trim().split("=", limit = 2)
                                    if (parts.size == 2 && parts[0].isNotBlank()) {
                                        collected[parts[0].trim()] = parts[1].trim()
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}

                return collected.entries.joinToString("; ") { "${it.key}=${it.value}" }
            }

            fun verifyAndCompleteSession(cookieStr: String) {
                scope.launch(Dispatchers.IO) {
                    val result = YouTubeDesktopClient.validateAndLogin(cookieStr)
                    if (result.isSuccess) {
                        val account = result.getOrNull()
                        SwingUtilities.invokeLater {
                            statusLabel.text = "Signed in as ${account?.name ?: "User"}! Syncing library..."
                            progressBar.isVisible = false
                        }
                        delay(900)
                        SwingUtilities.invokeLater {
                            dialog.dispose()
                            onSuccess()
                        }
                    } else {
                        SwingUtilities.invokeLater {
                            statusLabel.text = "Session found, finalizing authentication..."
                        }
                    }
                }
            }

            Platform.runLater {
                try {
                    val webView = WebView()
                    webViewRef = webView
                    val engine = webView.engine
                    engine.userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                    engine.isJavaScriptEnabled = true

                    val scene = Scene(webView, 640.0, 700.0)
                    jfxPanel.scene = scene

                    engine.loadWorker.stateProperty().addListener { _, _, newState ->
                        SwingUtilities.invokeLater {
                            when (newState) {
                                javafx.concurrent.Worker.State.SCHEDULED, javafx.concurrent.Worker.State.RUNNING -> {
                                    progressBar.isVisible = true
                                    statusLabel.text = "Loading page..."
                                }
                                javafx.concurrent.Worker.State.SUCCEEDED -> {
                                    progressBar.isVisible = false
                                    val loc = engine.location.orEmpty()
                                    if (loc.contains("music.youtube.com")) {
                                        statusLabel.text = "YouTube Music loaded. Verifying login session..."
                                    } else {
                                        statusLabel.text = "Please enter your Google / YouTube account credentials."
                                    }
                                }
                                javafx.concurrent.Worker.State.FAILED -> {
                                    progressBar.isVisible = false
                                    val ex = engine.loadWorker.exception
                                    statusLabel.text = "Page load error: ${ex?.message ?: "Check internet connection"}"
                                }
                                else -> {}
                            }
                        }
                    }

                    engine.locationProperty().addListener { _, _, newLoc ->
                        println("[DirectLogin] Navigated to: $newLoc")
                        if (newLoc != null && (newLoc.contains("music.youtube.com") || newLoc.contains("youtube.com/"))) {
                            SwingUtilities.invokeLater {
                                statusLabel.text = "Detected YouTube Music! Connecting account..."
                                progressBar.isVisible = true
                            }
                            val cookies = collectAllCookies()
                            if (cookies.contains("SAPISID") || cookies.contains("__Secure-3PAPISID") || cookies.contains("APISID")) {
                                verifyAndCompleteSession(cookies)
                            }
                        }
                    }

                    reloadBtn.addActionListener {
                        Platform.runLater {
                            engine.load("https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com")
                        }
                    }

                    checkBtn.addActionListener {
                        SwingUtilities.invokeLater {
                            statusLabel.text = "Checking session cookies..."
                            progressBar.isVisible = true
                        }
                        val cookies = collectAllCookies()
                        if (cookies.contains("SAPISID") || cookies.contains("__Secure-3PAPISID") || cookies.contains("APISID")) {
                            verifyAndCompleteSession(cookies)
                        } else {
                            SwingUtilities.invokeLater {
                                progressBar.isVisible = false
                                statusLabel.text = "Session not detected yet. Complete login in the window."
                            }
                        }
                    }

                    engine.load("https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com")
                } catch (e: Throwable) {
                    e.printStackTrace()
                    SwingUtilities.invokeLater {
                        statusLabel.text = "Initialization error: ${e.message}"
                        progressBar.isVisible = false
                    }
                }
            }

            // Start background poll to detect successful authentication
            pollJob?.cancel()
            pollJob = scope.launch(Dispatchers.IO) {
                var loginDetected = false
                while (isActive && !loginDetected && dialog.isVisible) {
                    delay(1000)
                    try {
                        val cookies = collectAllCookies()
                        if (cookies.contains("SAPISID") || cookies.contains("__Secure-3PAPISID") || cookies.contains("APISID")) {
                            loginDetected = true
                            SwingUtilities.invokeLater {
                                statusLabel.text = "Connected! Validating session..."
                                progressBar.isVisible = true
                            }
                            val result = YouTubeDesktopClient.validateAndLogin(cookies)
                            if (result.isSuccess) {
                                val account = result.getOrNull()
                                SwingUtilities.invokeLater {
                                    statusLabel.text = "Signed in as ${account?.name ?: "User"}! Syncing library..."
                                    progressBar.isVisible = false
                                }
                                delay(900)
                                SwingUtilities.invokeLater {
                                    dialog.dispose()
                                    onSuccess()
                                }
                            } else {
                                loginDetected = false
                            }
                        }
                    } catch (e: Exception) {
                        println("[DirectLogin] Poll error: ${e.message}")
                    }
                }
            }

            dialog.addWindowListener(object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent?) {
                    pollJob?.cancel()
                    currentDialog = null
                }
                override fun windowClosed(e: WindowEvent?) {
                    pollJob?.cancel()
                    currentDialog = null
                }
            })

            dialog.isVisible = true
        }
    }
}
