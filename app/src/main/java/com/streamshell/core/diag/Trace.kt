package com.streamshell.core.diag

// App-wide tracing ("Trace everything", 0.6.1): pure helpers, unit-tested. Nothing here reads addresses, titles or typed
// text; the log stays in memory until the user saves or shares it.

/** At most [perSecond] lines per second from one source, so tracing itself cannot make the app stutter. */
class RateLimiter(private val perSecond: Int, private val clock: () -> Long = System::currentTimeMillis) {
    private var start = Long.MIN_VALUE
    private var count = 0

    /** Lines refused since the start. */
    var refused = 0L
        private set

    @Synchronized
    fun allow(): Boolean {
        val now = clock()
        if (start == Long.MIN_VALUE || now - start >= WINDOW_MS) {
            start = now
            count = 0
        }
        val ok = ++count <= perSecond
        if (!ok) refused++
        return ok
    }

    private companion object {
        const val WINDOW_MS = 1000L
    }
}

/** What a finger did on the app's own screen. */
object Gestures {
    const val TAP_SLOP_DP = 10f
    const val LONG_PRESS_MS = 500L

    /**
     * "tap", "long press", "swipe left/right/up/down (N dp)" or "pinch" from the start and end of a gesture (dp).
     */
    fun classify(dxDp: Float, dyDp: Float, durationMs: Long, pointers: Int): String {
        if (pointers > 1) return "pinch / two fingers"
        val dist = kotlin.math.sqrt(dxDp * dxDp + dyDp * dyDp)
        if (dist < TAP_SLOP_DP) return if (durationMs >= LONG_PRESS_MS) "long press" else "tap"
        val dir = if (kotlin.math.abs(dxDp) >= kotlin.math.abs(dyDp)) {
            if (dxDp > 0) "right" else "left"
        } else if (dyDp > 0) {
            "down"
        } else {
            "up"
        }
        return "swipe $dir (${dist.toInt()} dp)"
    }
}

/** Settings changes as short lines: names always, values only when they are plain switches, numbers or short words. */
object SettingsDiff {
    /** Fields of a data class's `toString()` ("Name(a=1, b=[x, y], c=Foo(d=2))") at the top level, in order. */
    fun fields(text: String): Map<String, String> {
        val open = text.indexOf('(')
        if (open < 0 || !text.endsWith(")")) return emptyMap()
        val body = text.substring(open + 1, text.length - 1)
        val out = LinkedHashMap<String, String>()
        var depth = 0
        var from = 0
        fun take(end: Int) {
            val part = body.substring(from, end).trim()
            val eq = part.indexOf('=')
            if (eq > 0) out[part.substring(0, eq)] = part.substring(eq + 1)
        }
        body.forEachIndexed { i, ch ->
            when (ch) {
                '(', '[', '{' -> depth++

                ')', ']', '}' -> depth--

                ',' -> if (depth == 0) {
                    take(i)
                    from = i + 1
                }
            }
        }
        if (from < body.length) take(body.length)
        return out
    }

    private val shown = Regex("true|false|-?\\d+(\\.\\d+)?|[A-Z_]{1,24}|[a-z]{1,12}")

    fun lines(before: String, after: String): List<String> {
        val a = fields(before)
        val b = fields(after)
        return b.keys.filter { a[it] != b[it] }.map { k ->
            val old = a[k].orEmpty()
            val new = b[k].orEmpty()
            if (shown.matches(old) && shown.matches(new)) "setting $k: $old → $new" else "setting $k changed"
        }
    }
}

/** Summary at the top of a saved log (pure; reads only the lines). */
object DiagnosticsSummary {
    private val time = Regex("^(\\d\\d):(\\d\\d):(\\d\\d)(?:\\.(\\d{3}))?")
    private val pageLoad = Regex("page load \\(([^,]+), ([^)]+)\\)")
    private val startUp = Regex("start-up: (\\d+) ms")
    private val buffering = Regex("buffering (\\d+) ms")
    private val frames = Regex("frames: dropped (\\d+) of (\\d+) in 5 s")
    private val jank = Regex("jank: (\\d+) frames? over 50 ms.*worst (\\d+) ms")

    fun of(lines: List<String>, dropped: Long = 0, limited: Long = 0): String {
        val times = lines.mapNotNull { l -> time.find(l)?.groupValues?.let { g -> ((g[1].toLong() * 60 + g[2].toLong()) * 60 + g[3].toLong()) * 1000 + (g[4].toLongOrNull() ?: 0) } }
        val span = if (times.size >= 2) (times.last() - times.first()).let { if (it < 0) it + DAY_MS else it } else 0L
        val loads = lines.mapNotNull { pageLoad.find(it)?.groupValues?.get(1) }.groupingBy { it }.eachCount()
        val starts = lines.mapNotNull { startUp.find(it)?.groupValues?.get(1)?.toLongOrNull() }.sorted()
        val waits = lines.mapNotNull { buffering.find(it)?.groupValues?.get(1)?.toLongOrNull() }
        val fr = lines.mapNotNull { frames.find(it)?.groupValues }.map { it[1].toLong() to it[2].toLong() }
        val dropF = fr.sumOf { it.first }
        val allF = fr.sumOf { it.second }
        val jk = lines.mapNotNull { jank.find(it)?.groupValues }
        val opened = lines.count { it.contains("video opened") }
        val ads = lines.count { it.contains("ads: ad starts") }
        val skips = lines.count { it.contains("Skip button pressed") || it.contains("jump to end of") }
        val guard = lines.count { it.contains("tap guard:") }
        val errors = lines.count { l -> ERRORS.any { l.contains(it, ignoreCase = true) } }
        return buildList {
            add("== summary ==")
            add("session: ${clock(span)} · lines ${lines.size}" + (if (dropped > 0) " (oldest $dropped dropped)" else "") + (if (limited > 0) " · $limited lines refused by the rate limit" else ""))
            add("videos opened: $opened · started: ${starts.size}" + if (starts.isNotEmpty()) " · start-up median ${starts[starts.size / 2]} ms, worst ${starts.last()} ms" else "")
            add("buffering: ${waits.size} times, ${waits.sum()} ms in all" + if (waits.isNotEmpty()) ", longest ${waits.max()} ms" else "")
            add("dropped frames: " + if (allF > 0) "$dropF of $allF (${"%.2f".format(dropF * 100.0 / allF)} %)" else "no samples")
            add("screen stutter: " + if (jk.isEmpty()) "none logged" else "${jk.sumOf { it[1].toInt() }} frames over 50 ms, worst ${jk.maxOf { it[2].toInt() }} ms")
            add("page loads: " + if (loads.isEmpty()) "none" else loads.entries.joinToString(", ") { "${it.key} ${it.value}" })
            add("ads: $ads started, $skips skipped/jumped · tap guard blocked $guard · errors $errors")
            add("==")
        }.joinToString("\n")
    }

    private val ERRORS = listOf("error", "crash", "failed", "process ended")
    private const val DAY_MS = 86_400_000L

    private fun clock(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }
}
