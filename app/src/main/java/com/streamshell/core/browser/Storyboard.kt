package com.streamshell.core.browser

/** YouTube's timeline preview pattern for one video ("storyboard"), as the page reported it. */
data class StoryboardInfo(val videoId: String, val durationSec: Double, val spec: String)

/** One preview picture: a [w]×[h] piece at ([x], [y]) of the sprite sheet at [url]. */
data class StoryboardFrame(val url: String, val x: Int, val y: Int, val w: Int, val h: Int)

/**
 * The previews YouTube shows while you drag its timeline come as sprite sheets on i.ytimg.com. The spec is
 * `base|level0|level1|…`, base with `$L` (level) and `$N` (sheet name), each level
 * `width#height#count#columns#rows#intervalMs#name#sigh` where the name may contain `$M` (sheet number).
 * Pure and unit-tested; anything unexpected gives null (the app then shows the video's thumbnail).
 */
object Storyboard {
    private data class Level(val w: Int, val h: Int, val count: Int, val cols: Int, val rows: Int, val intervalMs: Long, val name: String, val sigh: String)

    fun frame(spec: String, durationSec: Double, atSec: Int, maxWidth: Int = 160): StoryboardFrame? {
        val parts = spec.split('|')
        if (parts.size < 2) return null
        val base = parts[0]
        if (!base.startsWith("https://i.ytimg.com/") || !base.contains("\$L") || !base.contains("\$N")) return null
        val levels = parts.drop(1).map { p ->
            val f = p.split('#')
            if (f.size < 8) return@map null
            val v = f.take(6).mapNotNull { it.toLongOrNull() }
            if (v.size != 6) return@map null
            if (v[0] <= 0 || v[1] <= 0 || v[2] <= 0 || v[3] <= 0 || v[4] <= 0) return@map null
            Level(v[0].toInt(), v[1].toInt(), v[2].toInt(), v[3].toInt(), v[4].toInt(), v[5], f[6], f[7])
        }
        val pick = levels.indices.lastOrNull { levels[it]?.let { l -> l.w <= maxWidth } == true } ?: levels.indices.firstOrNull { levels[it] != null } ?: return null
        val lv = levels[pick] ?: return null
        val t = atSec.coerceAtLeast(0)
        val idx = when {
            lv.intervalMs > 0 -> (t * 1000L / lv.intervalMs).toInt()
            durationSec > 0 -> (t / durationSec * lv.count).toInt()
            else -> 0
        }.coerceIn(0, lv.count - 1)
        val perSheet = lv.cols * lv.rows
        val sheet = idx / perSheet
        val within = idx % perSheet
        val name = lv.name.replace("\$M", sheet.toString())
        val url = base.replace("\$L", pick.toString()).replace("\$N", name) + if (lv.sigh.isNotEmpty()) "&sigh=" + lv.sigh else ""
        if (url.length > 2048) return null
        return StoryboardFrame(url, (within % lv.cols) * lv.w, (within / lv.cols) * lv.h, lv.w, lv.h)
    }
}
