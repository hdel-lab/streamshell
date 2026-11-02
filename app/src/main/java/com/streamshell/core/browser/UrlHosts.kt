package com.streamshell.core.browser

import java.net.URI
import java.net.URISyntaxException

object UrlHosts {
    fun hostOf(url: String?): String? {
        if (url == null) return null
        return try {
            URI(url).host?.lowercase()?.trimEnd('.')?.takeIf { it.isNotEmpty() }
        } catch (_: URISyntaxException) {
            null
        }
    }

    fun isHttps(url: String): Boolean = try {
        URI(url).let { it.scheme.equals("https", true) && !it.host.isNullOrEmpty() }
    } catch (_: URISyntaxException) {
        false
    }

    private val youtubeSuffixes = listOf("youtube.com", "youtu.be", "youtube-nocookie.com")

    fun isYouTube(host: String?): Boolean = host != null && youtubeSuffixes.any { host == it || host.endsWith(".$it") }
}

/** What kind of YouTube page an address is — for diagnostics (no address in the log) and the feed-preview rule. */
object PageKind {
    fun of(url: String?): String {
        if (url.isNullOrBlank()) return "empty"
        val host = UrlHosts.hostOf(url) ?: return "other"
        if (!UrlHosts.isYouTube(host)) return "other site"
        val path = url.substringAfter(host).substringBefore('?').substringBefore('#')
        return when {
            path == "" || path == "/" -> "home"
            path.startsWith("/watch") -> "video"
            path.startsWith("/shorts/") -> "shorts"
            path.startsWith("/live/") -> "live"
            path.startsWith("/results") -> "search"
            path.startsWith("/feed/") -> "feed"
            path.startsWith("/@") || path.startsWith("/channel/") || path.startsWith("/c/") || path.startsWith("/user/") -> "channel"
            path.startsWith("/playlist") -> "playlist"
            else -> "other"
        }
    }

    /** A page that plays one video itself (watch, Shorts, live, embed). */
    fun isVideoPage(url: String?): Boolean = of(url) in setOf("video", "shorts", "live") || url?.contains("/embed/") == true

    /** "https://m.youtube.com/shorts/ID…" → the normal watch address of that video, else null. */
    fun shortsAsWatch(url: String?): String? {
        if (of(url) != "shorts") return null
        val id = Regex("/shorts/([A-Za-z0-9_-]{11})").find(url.orEmpty())?.groupValues?.get(1) ?: return null
        return "https://m.youtube.com/watch?v=$id"
    }
}
