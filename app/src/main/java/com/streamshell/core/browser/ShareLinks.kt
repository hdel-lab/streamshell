package com.streamshell.core.browser

/** Shared links without tracking parameters. Pure, unit-tested. */
object ShareLinks {
    /** Kept on YouTube links (video, start time, playlist); everything else (si, feature, pp, …) goes. */
    private val youtubeKeep = setOf("v", "t", "list", "index")
    private val tracking = Regex("^(utm_.*|fbclid|gclid|dclid|msclkid|igshid|mc_eid|mc_cid|si|ref_src)$")

    fun clean(url: String): String {
        val host = UrlHosts.hostOf(url) ?: return url
        val base = url.substringBefore('#').substringBefore('?')
        val fragment = url.substringAfter('#', "").takeIf { it.isNotEmpty() }?.let { "#$it" }.orEmpty()
        val query = url.substringBefore('#').substringAfter('?', "")
        if (query.isEmpty()) return url
        val yt = host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
        val kept = query.split('&').filter { it.isNotEmpty() }.filter { p ->
            val k = p.substringBefore('=')
            if (yt) k in youtubeKeep else !tracking.matches(k)
        }
        return base + (if (kept.isEmpty()) "" else "?" + kept.joinToString("&")) + fragment
    }
}
