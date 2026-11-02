package com.streamshell.core.diag

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Ring buffer of diagnostics lines with a timestamp each (to the millisecond); observable for the log screen. Nothing is
 * written to disk or sent anywhere — the user saves or shares the text himself. With [schedule] the observable list is
 * published at most every [PUBLISH_MS] (tracing everything may add many lines per second); without it, at once.
 */
class DiagnosticsLog(private val clock: () -> Long = System::currentTimeMillis, private val schedule: ((Runnable, Long) -> Unit)? = null) {
    private val buf = ArrayDeque<String>()
    private val lines = MutableStateFlow<List<String>>(emptyList())
    val all: StateFlow<List<String>> = lines
    private var pending = false
    private val publishNow = Runnable { publish() }

    /** Oldest lines pushed out by [MAX]. */
    var dropped = 0L
        private set

    fun add(line: String) {
        val t = java.time.Instant.ofEpochMilli(clock()).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        val text = TIME.format(t) + " " + line
        var now = false
        synchronized(this) {
            buf.addLast(text)
            while (buf.size > MAX) {
                buf.removeFirst()
                dropped++
            }
            val s = schedule
            if (s == null) {
                now = true
            } else if (!pending) {
                pending = true
                s(publishNow, PUBLISH_MS)
            }
        }
        if (now) publish()
    }

    private fun publish() {
        val snap = synchronized(this) {
            pending = false
            buf.toList()
        }
        lines.value = snap
    }

    fun clear() {
        synchronized(this) {
            buf.clear()
            dropped = 0
        }
        lines.value = emptyList()
    }

    fun snapshot(): List<String> = synchronized(this) { buf.toList() }
    fun text(): String = snapshot().joinToString("\n")

    companion object {
        /** Tracing everything fills 3000 lines in minutes; 20000 keep a long drive or evening session (memory only). */
        const val MAX = 20000
        const val PUBLISH_MS = 250L
        private val TIME = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    }
}

/**
 * The one diagnostics log of the app process: the phone screens (view model) and the car screen (CarSession) write to
 * it, so a car session shows up in the same log. In memory only.
 */
object DiagnosticsHub {
    val log = DiagnosticsLog(schedule = { r, ms -> android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(r, ms) })

    /** Settings › Diagnostics log (set by the phone's settings; the car session reads it). */
    @Volatile var enabled = false

    @Volatile var traceEverything = false

    /** Report of the crash before this start (see [CrashReport]), or null. */
    @Volatile var lastCrash: String? = null

    fun add(source: String, line: String) {
        if (enabled) log.add("$source · $line")
    }
}
