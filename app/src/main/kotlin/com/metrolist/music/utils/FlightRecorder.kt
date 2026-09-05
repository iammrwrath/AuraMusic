/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.metrolist.music.BuildConfig
import timber.log.Timber
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

object FlightRecorder {
    private const val MAX_LOG_ENTRIES = 600

    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val priority: Int,
        val tag: String?,
        val message: String,
        val throwable: Throwable? = null,
    )

    private val logBuffer = ConcurrentLinkedDeque<LogEntry>()

    val tree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            record(priority, tag, message, t)
        }
    }

    fun record(priority: Int, tag: String?, message: String, t: Throwable? = null) {
        logBuffer.add(LogEntry(priority = priority, tag = tag, message = message, throwable = t))
        while (logBuffer.size > MAX_LOG_ENTRIES) {
            logBuffer.pollFirst()
        }
    }

    fun recordException(throwable: Throwable, tag: String = "FlightRecorder") {
        val stackTrace = StringWriter().apply {
            throwable.printStackTrace(PrintWriter(this))
        }.toString()
        record(Log.ERROR, tag, "Exception: ${throwable.message}\n$stackTrace", throwable)
    }

    fun getRecentLogs(maxCount: Int = 300): String {
        val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val entries = logBuffer.toList().takeLast(maxCount)
        return entries.joinToString("\n") { entry ->
            val time = dateFormat.format(Date(entry.timestamp))
            val level = when (entry.priority) {
                Log.VERBOSE -> "V"
                Log.DEBUG -> "D"
                Log.INFO -> "I"
                Log.WARN -> "W"
                Log.ERROR -> "E"
                Log.ASSERT -> "A"
                else -> "?"
            }
            val tag = (entry.tag ?: "Metrolist").padEnd(15).take(15)
            val line = "[$time] $level/$tag: ${entry.message}"
            if (entry.throwable != null) {
                val sw = StringWriter()
                entry.throwable.printStackTrace(PrintWriter(sw))
                "$line\n$sw"
            } else {
                line
            }
        }
    }

    fun buildDiagnosticReport(context: Context, throwable: Throwable? = null): String {
        val stackTrace = throwable?.let {
            StringWriter().apply { it.printStackTrace(PrintWriter(this)) }.toString()
        }

        return buildString {
            appendLine("### 📱 Device & Environment")
            appendLine("- **Manufacturer / Model:** ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("- **Android OS:** ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("- **App Version:** ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("- **Build Flavor:** ${BuildConfig.FLAVOR}")
            appendLine("- **Timestamp:** ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())}")
            appendLine()

            if (!stackTrace.isNullOrBlank()) {
                appendLine("### 💥 Exception Stack Trace")
                appendLine("```text")
                appendLine(stackTrace.trim())
                appendLine("```")
                appendLine()
            }

            appendLine("### 📋 Flight Recorder Logs (Last Events)")
            appendLine("```text")
            appendLine(getRecentLogs(300))
            appendLine("```")
        }
    }

    fun getGitHubIssueUrl(
        repositoryOwnerRepo: String = "iammrwrath/AuraMusic",
        title: String,
        body: String,
        labels: List<String> = listOf("ai-fix", "bug"),
    ): String {
        val encodedTitle = URLEncoder.encode(title, "UTF-8")
        val encodedBody = URLEncoder.encode(body, "UTF-8")
        val encodedLabels = URLEncoder.encode(labels.joinToString(","), "UTF-8")
        return "https://github.com/$repositoryOwnerRepo/issues/new?title=$encodedTitle&body=$encodedBody&labels=$encodedLabels"
    }

    fun shareReport(context: Context, report: String, subject: String = "AuraMusic Diagnostic Report") {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "auramusic_diagnostic_$timestamp.txt"
            val reportFile = File(context.cacheDir, fileName)
            reportFile.writeText(report)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.FileProvider",
                reportFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Diagnostic Report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, report)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Diagnostic Report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
