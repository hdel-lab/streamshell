package com.streamshell.car

/**
 * Size rules for the car screen (pure, unit-tested). Android Auto reports a density for its surface that does not suit
 * a web page on wide head units (DS7 Crossback, 0.5.6 photos: the page was far too large). StreamShell picks its own
 * density so the page gets about [TARGET_HEIGHT_DP] of height — like a phone held sideways — but never less than
 * [MIN_WIDTH_DP] of width, within [MIN_DPI]..[MAX_DPI].
 */
object CarDisplay {
    const val TARGET_HEIGHT_DP = 480
    const val MIN_WIDTH_DP = 640
    const val MIN_DPI = 120
    const val MAX_DPI = 320

    /** Control rail width and play bar height (dp); touch targets stay above Android Auto's 76 dp minimum minus padding. */
    const val RAIL_DP = 84
    const val BAR_DP = 84

    fun optimalDpi(widthPx: Int, heightPx: Int, hostDpi: Int): Int {
        if (widthPx <= 0 || heightPx <= 0) return hostDpi.coerceIn(MIN_DPI, MAX_DPI)
        val forHeight = heightPx * BASE / TARGET_HEIGHT_DP
        val widthLimit = widthPx * BASE / MIN_WIDTH_DP
        return minOf(forHeight, widthLimit).coerceIn(MIN_DPI, MAX_DPI)
    }

    /** Size in dp of [px] pixels at [dpi]. */
    fun dp(px: Int, dpi: Int): Int = if (dpi <= 0) px else px * BASE / dpi

    /** "1:05", "12:00", "1:02:03"; "" for unknown. */
    fun clock(seconds: Double): String {
        if (!seconds.isFinite() || seconds < 0) return ""
        val s = seconds.toLong()
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    /** Progress 0..1000 of [position] in [duration]; 0 when unknown. */
    fun progress(position: Double, duration: Double): Int {
        val known = position.isFinite() && duration.isFinite() && duration > 0
        return if (known) (position / duration * PROGRESS_MAX).toInt().coerceIn(0, PROGRESS_MAX) else 0
    }

    /** Page script: scrolls the page by most of a screen, up (-1) or down (1). */
    fun scrollScript(direction: Int): String {
        val sign = if (direction < 0) -1 else 1
        return "(function(){window.scrollBy({top:$sign*Math.round(window.innerHeight*0.7),behavior:'smooth'});})()"
    }

    const val PROGRESS_MAX = 1000
    private const val BASE = 160
}
