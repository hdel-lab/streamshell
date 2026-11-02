package com.streamshell.core.browser

/**
 * Messages YouTube's page (StreamShell's page scripts) sends to the app on the "streamShellSponsor" channel. Everything is
 * untrusted text from a web page: [parse] accepts only the known formats, checks ids, lengths and ranges, and returns
 * null for anything else. Pure (no Android types), unit-tested.
 */
sealed interface PageMessage {
    /** The ad skipper finished or jumped over an ad. */
    data object AdSkipped : PageMessage

    /** Press YouTube's Skip button at (x, y) of a page of size (w, h) CSS pixels. */
    data class TapSkip(val x: Int, val y: Int, val w: Int, val h: Int) : PageMessage
    data class Live(val live: Boolean) : PageMessage
    data class Board(val info: StoryboardInfo) : PageMessage
    data object OpenChapters : PageMessage
    data class ChapterList(val chapters: List<Chapter>) : PageMessage
    data class DislikesRequest(val videoId: String) : PageMessage
    data class DeArrowRequest(val videoIds: List<String>) : PageMessage
    data class Diagnostics(val line: String) : PageMessage
    data class Panel(val open: Boolean) : PageMessage
    data class SponsorSkipped(val category: String, val seconds: Int) : PageMessage

    companion object {
        private val videoId = Regex("[A-Za-z0-9_-]{11}")

        fun parse(data: String?): PageMessage? {
            val d = data ?: return null
            if (d.length > 50_000) return null
            return when {
                d == YouTubeAds.MESSAGE -> AdSkipped

                d.startsWith(YouTubeAds.TAP + "|") -> {
                    val p = d.split('|').drop(1).mapNotNull { it.toIntOrNull() }
                    if (p.size != 4 || p[2] <= 0 || p[3] <= 0 || p[0] !in 0..p[2] || p[1] !in 0..p[3]) null else TapSkip(p[0], p[1], p[2], p[3])
                }

                d.startsWith(YouTubeLiveChat.MESSAGE) -> Live(d == YouTubeLiveChat.MESSAGE + "1")

                d.startsWith(YouTubeTimestamps.STORYBOARD) -> {
                    val f = d.removePrefix(YouTubeTimestamps.STORYBOARD).split('|', limit = 3)
                    val vid = f.getOrNull(0)?.takeIf { videoId.matches(it) }
                    if (vid != null && f.size == 3 && f[2].length < 4000) Board(StoryboardInfo(vid, f[1].toDoubleOrNull() ?: 0.0, f[2])) else null
                }

                d.startsWith(YouTubeTimestamps.CHAPTERS) -> {
                    val body = d.removePrefix(YouTubeTimestamps.CHAPTERS)
                    when {
                        body == "open" -> OpenChapters
                        body.length < 40_000 -> ChapterList(Chapters.parse(body))
                        else -> null
                    }
                }

                d.startsWith(YouTubeExtras.DISLIKES) -> d.removePrefix(YouTubeExtras.DISLIKES).takeIf { videoId.matches(it) }?.let { DislikesRequest(it) }

                d.startsWith(YouTubeExtras.DIAG) -> Diagnostics(d.removePrefix(YouTubeExtras.DIAG).take(300))

                d.startsWith(YouTubeExtras.DEARROW) -> d.removePrefix(YouTubeExtras.DEARROW).split(',').filter { videoId.matches(it) }.take(60)
                    .takeIf { it.isNotEmpty() }?.let { DeArrowRequest(it) }

                d.startsWith(YouTubeSite.PANEL_MESSAGE) -> Panel(d == YouTubeSite.PANEL_MESSAGE + "1")

                else -> {
                    val parts = d.split('|')
                    val cat = parts.getOrNull(0)?.takeIf { com.streamshell.core.sponsor.SponsorCategory.of(it) != null } ?: return null
                    SponsorSkipped(cat, (parts.getOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, 86_400))
                }
            }
        }
    }
}
