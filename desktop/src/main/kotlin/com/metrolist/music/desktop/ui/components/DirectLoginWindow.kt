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
            dialog.setSize(620, 780)
            dialog.minimumSize = Dimension(520, 640)
            dialog.setLocationRelativeTo(null)
            dialog.layout = BorderLayout()

            val topPanel = JPanel(BorderLayout()).apply {
                background = Color(24, 24, 27)
                border = BorderFactory.createEmptyBorder(12, 18, 12, 18)
            }
            val statusLabel = JLabel("Sign in with your Google / YouTube Music account").apply {
                foreground = Color.WHITE
                font = font.deriveFont(java.awt.Font.BOLD, 13f)
            }
            topPanel.add(statusLabel, BorderLayout.WEST)

            val progressBar = JProgressBar().apply {
                isIndeterminate = true
                isVisible = false
                preferredSize = Dimension(120, 16)
            }
            topPanel.add(progressBar, BorderLayout.EAST)
            dialog.add(topPanel, BorderLayout.NORTH)

            val jfxPanel = JFXPanel()
            dialog.add(jfxPanel, BorderLayout.CENTER)

            val bottomPanel = JPanel(BorderLayout()).apply {
                background = Color(24, 24, 27)
                border = BorderFactory.createEmptyBorder(10, 18, 10, 18)
            }
            val hintLabel = JLabel("Sign in directly. Once completed, your library will sync automatically.").apply {
                foreground = Color(160, 160, 160)
                font = font.deriveFont(11f)
            }
            val reloadBtn = JButton("Reload Page").apply {
                isFocusPainted = false
            }
            bottomPanel.add(hintLabel, BorderLayout.WEST)
            bottomPanel.add(reloadBtn, BorderLayout.EAST)
            dialog.add(bottomPanel, BorderLayout.SOUTH)

            var webViewRef: WebView? = null

            Platform.runLater {
                val webView = WebView()
                webViewRef = webView
                val engine = webView.engine
                engine.userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                engine.isJavaScriptEnabled = true

                val scene = Scene(webView)
                jfxPanel.scene = scene

                reloadBtn.addActionListener {
                    Platform.runLater {
                        engine.load("https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com")
                    }
                }

                engine.locationProperty().addListener { _, _, newLoc ->
                    println("[DirectLogin] Navigated to: ")
                    if (newLoc != null && (newLoc.contains("music.youtube.com") || newLoc.contains("youtube.com/"))) {
                        SwingUtilities.invokeLater {
                            statusLabel.text = "Detecting YouTube Music session..."
                            progressBar.isVisible = true
                        }
                    }
                }

                engine.load("https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com")
            }

            // Start background poll to detect successful authentication
            pollJob?.cancel()
            pollJob = scope.launch(Dispatchers.IO) {
                var loginDetected = false
                while (isActive && !loginDetected && dialog.isVisible) {
                    delay(800)
                    try {
                        var docCookie = ""
                        var currentLoc = ""
                        Platform.runLater {
                            try {
                                currentLoc = webViewRef?.engine?.location.orEmpty()
                                docCookie = (webViewRef?.engine?.executeScript("document.cookie") as? String).orEmpty()
                            } catch (ignored: Exception) {}
                        }
                        delay(200)

                        val uri = URI.create("https://music.youtube.com/")
                        val handler = CookieHandler.getDefault()
                        val headerMap = handler?.get(uri, emptyMap())
                        val netCookies = headerMap?.get("Cookie")?.joinToString("; ").orEmpty()

                        val combined = listOf(docCookie, netCookies)
                            .filter { it.isNotBlank() }
                            .joinToString("; ")

                        if (combined.contains("SAPISID") || combined.contains("__Secure-3PAPISID") || combined.contains("APISID")) {
                            loginDetected = true
                            SwingUtilities.invokeLater {
                                statusLabel.text = "Connected! Finalizing account profile..."
                                progressBar.isVisible = true
                            }

                            val result = YouTubeDesktopClient.validateAndLogin(combined)
                            if (result.isSuccess) {
                                SwingUtilities.invokeLater {
                                    statusLabel.text = "Signed in successfully!"
                                    dialog.dispose()
                                    onSuccess()
                                }
                            } else {
                                loginDetected = false
                                SwingUtilities.invokeLater {
                                    statusLabel.text = "Validating session..."
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("[DirectLogin] Poll error: ")
                    }
                }
            }

            dialog.addWindowListener(object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent?) {
                    pollJob?.cancel()
                }
            })

            dialog.isVisible = true
        }
    }
}
