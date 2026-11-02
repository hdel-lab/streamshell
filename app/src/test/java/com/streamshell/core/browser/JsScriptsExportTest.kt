package com.streamshell.core.browser

import java.io.File
import org.junit.Test

/** Writes the exact page scripts to build/js-sim so tools/js-sim/run.sh can simulate them in node. */
class JsScriptsExportTest {
    @Test fun export() {
        val dir = File("build/js-sim").apply { mkdirs() }
        File(dir, "ads.js").writeText(YouTubeAds.INSTALL)
        File(dir, "autoplay.js").writeText(YouTubeAutoplay.INSTALL)
        File(dir, "compact.js").writeText(YouTubeSite.INSTALL)
        File(dir, "router.js").writeText(YouTubeRouter.INSTALL)
        File(dir, "chaptertap.js").writeText(YouTubeTimestamps.chapterTitleAt(300f, 1200f, listOf("Intro", "Initial deployment")))
        File(dir, "timestamps.js").writeText(YouTubeTimestamps.INSTALL)
        File(dir, "live.js").writeText(YouTubeLiveChat.INSTALL)
        File(dir, "extras.js").writeText(YouTubeExtras.INSTALL)
        File(dir, "extras-diag.js").writeText(YouTubeExtras.config("", dislikes = false, deArrow = false, markers = false, diag = true))
        File(dir, "ms-capture.js").writeText(MediaSessionScripts.CAPTURE)
        File(dir, "ms-next.js").writeText(MediaSessionScripts.action(MediaSessionScripts.NEXT))
        File(dir, "ms-prev.js").writeText(MediaSessionScripts.action(MediaSessionScripts.PREVIOUS))
        File(dir, "ms-now.js").writeText(MediaSessionScripts.NOW_PLAYING)
        File(dir, "duck-on.js").writeText(com.streamshell.core.media.MediaScripts.duck(true))
        File(dir, "duck-off.js").writeText(com.streamshell.core.media.MediaScripts.duck(false))
        File(dir, "extras-trace.js").writeText(YouTubeExtras.config("", dislikes = false, deArrow = false, markers = false, diag = true, trace = true))
        File(dir, "seekby.js").writeText(com.streamshell.core.media.MediaScripts.seekBy(10))
        // Further page scripts, exported for ESLint (tools/lint/static-analysis.sh).
        val more = mapOf(
            "lint-sponsor.js" to com.streamshell.core.sponsor.SponsorScripts.install("dQw4w9WgXcQ", listOf(com.streamshell.core.sponsor.SkipSegment(10.0, 30.0, "sponsor", "a"))),
            "lint-probe.js" to com.streamshell.core.media.MediaScripts.PROBE, "lint-play.js" to com.streamshell.core.media.MediaScripts.PLAY,
            "lint-pause.js" to com.streamshell.core.media.MediaScripts.PAUSE, "lint-resume.js" to com.streamshell.core.media.MediaScripts.RESUME,
            "lint-position.js" to com.streamshell.core.media.PlayerScripts.POSITION, "lint-seekto.js" to com.streamshell.core.media.PlayerScripts.seekTo(12.0),
            "lint-speed.js" to com.streamshell.core.media.PlayerScripts.speed(150), "lint-audio.js" to com.streamshell.core.media.PlayerScripts.audio(150, true),
            "lint-quality.js" to com.streamshell.core.media.PlayerScripts.quality("hd720"), "lint-tsseek.js" to YouTubeTimestamps.seekScript(42),
            "lint-router-flag.js" to YouTubeRouter.FLAG,
            "lint-zoom.js" to com.streamshell.core.media.PlayerScripts.zoom(true), "lint-channel.js" to com.streamshell.core.media.PlayerScripts.CHANNEL,
            "lint-captions.js" to com.streamshell.core.media.PlayerScripts.captions("on", "de"), "lint-captions-off.js" to com.streamshell.core.media.PlayerScripts.captions("off", "")
        )
        more.forEach { (name, js) -> File(dir, name).writeText(js) }
        File(dir, "silent-on.js").writeText(com.streamshell.core.media.MediaScripts.silent(true))
        File(dir, "silent-off.js").writeText(com.streamshell.core.media.MediaScripts.silent(false))
        File(dir, "mini-on.js").writeText(YouTubeSite.mini(true))
        File(dir, "mini-off.js").writeText(YouTubeSite.mini(false))
        File(dir, "extras-config.js").writeText(YouTubeExtras.config(FeedFilter.css(setOf("shorts")), dislikes = true, deArrow = true, markers = true))
        File(dir, "extras-hide.js").writeText(
            YouTubeExtras.config("", dislikes = false, deArrow = false, markers = false, watched = listOf("aaaaaaaaaaa"), channels = listOf("Spam Channel"), words = listOf("trailer"))
        )
        File(dir, "repeat.js").writeText(com.streamshell.core.media.PlayerScripts.repeat(false, 10.0, 20.0))
        File(dir, "router-on.js").writeText(YouTubeRouter.FLAG + "window.__ssShortsAsVideos=true;" + YouTubeRouter.INSTALL)
        File(dir, "extras-answers.js").writeText(YouTubeExtras.dislikes("dQw4w9WgXcQ", 12345) + YouTubeExtras.deArrowTitles(mapOf("aaaaaaaaaaa" to "Clear title", "dQw4w9WgXcQ" to "Watch title")))
    }
}
