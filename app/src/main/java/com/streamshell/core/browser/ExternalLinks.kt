package com.streamshell.core.browser

/** Links handed to StreamShell by other apps. Pure, unit-tested. */
object ExternalLinks {
    /**
     * A YouTube video link (youtube.com/watch?v=…, youtu.be/…) as the mobile watch address with its start time kept, or
     * null for anything else (Shorts, live, channels, other sites, YouTube Music open as they are).
     */
    fun youtubeWatch(url: String): String? {
        val host = UrlHosts.hostOf(url) ?: return null
        val isWatch = (host == "youtu.be") ||
            (
                (host == "youtube.com" || host.endsWith(".youtube.com")) && host != "music.youtube.com" &&
                    url.substringAfter("://").substringAfter('/', "").startsWith("watch")
                )
        if (!isWatch) return null
        val id = com.streamshell.core.sponsor.SponsorBlockApi.videoIdOf(url)?.takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) } ?: return null
        val query = url.substringAfter('?', "").substringBefore('#')
        val t = query.split('&').firstOrNull { it.startsWith("t=") }?.substring(2)?.let { YouTubeTimestamps.seconds(it) }
        return "https://m.youtube.com/watch?v=$id" + (t?.let { "&t=${it}s" } ?: "")
    }
}
