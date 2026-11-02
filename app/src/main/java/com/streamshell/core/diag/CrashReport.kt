package com.streamshell.core.diag

import android.content.Context
import java.io.File

/**
 * Keeps the stack trace of the last crash (0.6.2: the user reported a crash when switching from YouTube to YouTube
 * Music, but a crash never reaches the in-memory log). The default handler writes it to a private file before Android
 * closes the app; the next start moves it into [DiagnosticsHub.lastCrash] (shown at the top of the diagnostics log and
 * of a saved log). Addresses in messages are removed. Nothing leaves the device.
 */
object CrashReport {
    private const val FILE = "last-crash.txt"
    private const val MAX_CHARS = 12_000

    fun install(context: Context) {
        val app = context.applicationContext
        DiagnosticsHub.lastCrash = runCatching { File(app.filesDir, FILE).takeIf { it.exists() }?.let { f -> f.readText().also { f.delete() } } }.getOrNull()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(app.filesDir, FILE).writeText(describe(thread.name, error, System.currentTimeMillis(), DiagnosticsHub.log.snapshot().takeLast(LAST_LINES))) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The report: time, thread, the exception chain with its stack and the last log lines (pure, unit-tested). */
    fun describe(thread: String, error: Throwable, now: Long, lastLines: List<String>): String {
        val sw = java.io.StringWriter()
        error.printStackTrace(java.io.PrintWriter(sw))
        val time = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().withNano(0)
        return ("crash $time on thread $thread\n" + clean(sw.toString()) + (if (lastLines.isEmpty()) "" else "\nlast log lines before the crash:\n" + lastLines.joinToString("\n")))
            .take(MAX_CHARS)
    }

    fun clean(s: String): String = s.replace(Regex("https?://\\S+"), "<address>")

    private const val LAST_LINES = 40
}
