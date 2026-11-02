package com.streamshell.core

import com.streamshell.core.browser.YouTubeExtras
import com.streamshell.core.diag.DiagnosticsSummary
import com.streamshell.core.diag.Gestures
import com.streamshell.core.diag.RateLimiter
import com.streamshell.core.diag.SettingsDiff
import com.streamshell.core.model.AppSettings
import com.streamshell.feature.settings.SettingsSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.6.1: "Trace everything" — app-wide diagnostics. */
class Release061Test {
    @Test fun rateLimiterAllowsPerSecondAndCountsRefused() {
        var now = 0L
        val r = RateLimiter(3) { now }
        assertEquals(listOf(true, true, true, false, false), List(5) { r.allow() })
        assertEquals(2L, r.refused)
        now = 1000
        assertTrue(r.allow())
    }

    @Test fun gestures() {
        assertEquals("tap", Gestures.classify(2f, 3f, 120, 1))
        assertEquals("long press", Gestures.classify(1f, 0f, 700, 1))
        assertEquals("swipe left (80 dp)", Gestures.classify(-80f, 5f, 200, 1))
        assertEquals("swipe down (120 dp)", Gestures.classify(0f, 120f, 200, 1))
        assertEquals("swipe up (40 dp)", Gestures.classify(0f, -40f, 200, 1))
        assertEquals("pinch / two fingers", Gestures.classify(0f, 0f, 200, 2))
    }

    @Test fun settingsDiffNamesEveryChangeButShowsOnlyPlainValues() {
        val a = AppSettings()
        val b = a.copy(autoplay = !a.autoplay, playbackSpeed = 150, blockedKeywords = setOf("secret word"), accent = "red", searchEngine = a.searchEngine)
        val lines = SettingsDiff.lines(a.toString(), b.toString())
        assertTrue(lines.contains("setting autoplay: ${a.autoplay} → ${!a.autoplay}"))
        assertTrue(lines.contains("setting playbackSpeed: ${a.playbackSpeed} → 150"))
        assertTrue(lines.contains("setting blockedKeywords changed"))
        assertTrue(lines.contains("setting accent: blue → red"))
        assertFalse(lines.any { it.contains("secret") })
        assertEquals(4, lines.size)
        assertTrue(SettingsDiff.lines(a.toString(), a.toString()).isEmpty())
    }

    @Test fun fieldsSplitAtTopLevelOnly() {
        val f = SettingsDiff.fields("X(a=1, b=[p, q], c=Y(d=2, e=3), s=)")
        assertEquals(mapOf("a" to "1", "b" to "[p, q]", "c" to "Y(d=2, e=3)", "s" to ""), f)
    }

    @Test fun summaryOfALog() {
        val lines = listOf(
            "10:00:00.000 tab 3 · page load (app, video)",
            "10:00:00.100 app · video opened in tab 3",
            "10:00:01.200 app · start-up: 1200 ms (tap to playing)",
            "10:00:05.000 tab 3 · buffering 800 ms",
            "10:00:10.000 tab 3 · frames: dropped 3 of 300 in 5 s (total 3/300), quality hd720",
            "10:00:15.000 tab 3 · frames: dropped 1 of 100 in 5 s (total 4/400)",
            "10:00:20.000 frames · jank: 2 frames over 50 ms in 5 s, worst 130 ms",
            "10:00:30.000 tab 3 · ads: ad starts, media 15 s",
            "10:00:31.000 tab 3 · ads: Skip button pressed",
            "10:00:40.000 tab 2 · tap guard: moved, not opened",
            "10:01:00.000 tab 3 · page load (reload, home)",
            "10:01:05.500 tab 3 · page error: boom"
        )
        val s = DiagnosticsSummary.of(lines, dropped = 7, limited = 2)
        assertTrue(s, s.contains("session: 0:01:05 · lines 12 (oldest 7 dropped) · 2 lines refused by the rate limit"))
        assertTrue(s, s.contains("videos opened: 1 · started: 1 · start-up median 1200 ms, worst 1200 ms"))
        assertTrue(s, s.contains("buffering: 1 times, 800 ms in all, longest 800 ms"))
        assertTrue(s, s.contains("dropped frames: 4 of 400 (1.00 %)") || s.contains("dropped frames: 4 of 400 (1,00 %)"))
        assertTrue(s, s.contains("screen stutter: 2 frames over 50 ms, worst 130 ms"))
        assertTrue(s, s.contains("page loads: app 1, reload 1"))
        assertTrue(s, s.contains("ads: 1 started, 1 skipped/jumped · tap guard blocked 1 · errors 1"))
        assertTrue(DiagnosticsSummary.of(emptyList()).contains("no samples"))
    }

    @Test fun traceFlagReachesThePageAndImpliesDiagnostics() {
        val c = YouTubeExtras.config("", dislikes = false, deArrow = false, markers = false, diag = false, trace = true)
        assertTrue(c.contains("\"trace\":true") && c.contains("\"diag\":true"))
        val off = YouTubeExtras.config("", dislikes = false, deArrow = false, markers = false)
        assertTrue(off.contains("\"trace\":false") && off.contains("\"diag\":false"))
        assertTrue(YouTubeExtras.INSTALL.contains("function trace()") && YouTubeExtras.INSTALL.contains("terr('feed filter'"))
    }

    @Test fun settingsDefaultsAndSearch() {
        assertFalse(AppSettings().traceEverything)
        assertTrue(AppSettings().logButton)
        assertTrue(SettingsSearch.matches("trace", SettingsSearch.YOUTUBE))
        assertTrue(SettingsSearch.matches("admin logging", SettingsSearch.YOUTUBE))
    }
}
