package com.streamshell.core.watch

import com.streamshell.core.library.Bookmark
import com.streamshell.core.library.HistoryEntry
import com.streamshell.core.model.AppSettings
import com.streamshell.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val YT = "https://m.youtube.com/watch?v=dQw4w9WgXcQ"

class WatchRulesTest {
    @Test fun positionsAreRememberedAndResumedSensibly() {
        var l = WatchRules.record(emptyList(), YT, "Song", 125.0, 600.0, 1)
        assertEquals(125.0, WatchRules.resumeAt(l, "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=3")) // same video, other URL
        l = WatchRules.record(l, YT, "", 130.0, 600.0, 2)
        assertEquals(1, l.size)
        assertEquals("Song", l[0].title)
        assertTrue(WatchRules.record(l, YT, "Song", 590.0, 600.0, 3).isEmpty()) // finished: removed
        assertTrue(WatchRules.record(emptyList(), YT, "x", 5.0, 600.0, 1).isEmpty()) // barely started
        assertTrue(WatchRules.record(emptyList(), YT, "x", 20.0, 30.0, 1).isEmpty()) // short clip
        assertTrue(WatchRules.record(emptyList(), "http://a.b/v", "x", 20.0, 300.0, 1).isEmpty())
        assertNull(WatchRules.resumeAt(l, "https://vimeo.com/1"))
    }

    @Test fun resumeDecisionSaysWhy() {
        val l = WatchRules.record(emptyList(), YT, "Song", 83.0, 483.0, 1)
        assertEquals(WatchRules.Resume(83.0, "saved"), WatchRules.resumeDecision(l, YT, 0.0))
        // 0.4.3 log: the same video handed on to the player tab starts there at 0 and is continued again
        assertEquals(83.0, WatchRules.resumeDecision(l, YT, 0.2).at)
        assertEquals("already at 207 s", WatchRules.resumeDecision(l, YT, 207.0).reason)
        assertEquals("no saved position", WatchRules.resumeDecision(emptyList(), YT, 0.0).reason)
        val nearEnd = listOf(l[0].copy(position = 470.0))
        assertNull(WatchRules.resumeDecision(nearEnd, YT, 0.0).at)
        assertTrue(WatchRules.resumeDecision(nearEnd, YT, 0.0).reason.startsWith("saved 470 s"))
    }

    @Test fun feedPreviewsAreNeverRememberedOrContinued() {
        // 0.4.4 log: the home feed's muted tile preview was saved under the home page and "continued" at 83 s on every start
        val home = "https://m.youtube.com/"
        assertTrue(WatchRules.record(emptyList(), home, "YouTube", 83.0, 810.0, 1).isEmpty())
        val old = listOf(WatchPosition("https://m.youtube.com/", home, "YouTube", 83.0, 810.0, 1))
        assertNull(WatchRules.resumeAt(old, home))
        assertEquals("not a video page", WatchRules.resumeDecision(old, home, 0.0).reason)
        val kept = WatchRules.record(old, YT, "Song", 120.0, 600.0, 2)
        assertEquals(listOf(YT), WatchRules.withoutPageEntries(kept).map { it.url })
        // other sites are unchanged
        assertEquals(1, WatchRules.record(emptyList(), "https://vimeo.com/1", "v", 120.0, 600.0, 1).size)
    }

    @Test fun continueWatchingNewestFirstAndCapped() {
        var l = emptyList<WatchPosition>()
        repeat(250) { i -> l = WatchRules.record(l, "https://vimeo.com/$i", "v$i", 100.0, 1000.0, i.toLong()) }
        assertEquals(WatchRules.MAX_POSITIONS, l.size)
        assertEquals("v249", WatchRules.continueWatching(l).first().title)
        assertEquals(12, WatchRules.continueWatching(l).size)
    }

    @Test fun watchLaterToggles() {
        var l = WatchRules.toggleLater(emptyList(), YT, "Song", 1)
        assertTrue(WatchRules.isLater(l, "https://youtu.be/dQw4w9WgXcQ".let { YT }))
        l = WatchRules.toggleLater(l, YT, "Song", 2)
        assertFalse(WatchRules.isLater(l, YT))
    }

    @Test fun statsKeepThirtyDaysAndWeekHasSevenDays() {
        var s = emptyMap<String, Long>()
        val start = java.time.LocalDate.of(2026, 1, 1)
        repeat(40) { i -> s = WatchRules.addWatchTime(s, start.plusDays(i.toLong()).toString(), 60) }
        assertEquals(30, s.size)
        s = WatchRules.addWatchTime(s, start.plusDays(39).toString(), 90)
        val week = WatchRules.lastDays(s, start.plusDays(39), 7)
        assertEquals(7, week.size)
        assertEquals(150L, week.last().second)
        assertEquals("1 h 5 min", WatchRules.formatDuration(3900))
        assertEquals("1:02:03", WatchRules.formatClock(3723.0))
    }
}

class TakeoutAndFeedTest {
    @Test fun takeoutCsvInAnyLanguageWithQuotes() {
        val csv = """Kanal-ID,Kanal-URL,Kanaltitel
UCuAXFkgsw1L7xaCfnd5JJOw,http://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw,"Rick, Astley"
UCuAXFkgsw1L7xaCfnd5JJOw,http://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw,Duplicate
,,
garbage line"""
        val ch = TakeoutImport.parse(csv)
        assertEquals(1, ch.size)
        assertEquals("Rick, Astley", ch[0].title)
        assertEquals("https://m.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw", ch[0].url)
    }

    @Test fun feedParsingAndMerging() {
        val xml = """<feed><author><name>Chan &amp; Co</name></author>
<entry><yt:videoId>dQw4w9WgXcQ</yt:videoId><title>New &quot;one&quot;</title><published>2026-10-18T10:00:00+00:00</published><author><name>Chan &amp; Co</name></author></entry>
<entry><yt:videoId>aaaaaaaaaaa</yt:videoId><title>Old</title><published>2026-01-01T10:00:00+00:00</published></entry></feed>"""
        val v = ChannelFeed.parse(xml)
        assertEquals(2, v.size)
        assertEquals("New \"one\"", v[0].title)
        assertEquals("Chan & Co", v[0].channel)
        val merged = ChannelFeed.merge(listOf(v.reversed(), v))
        assertEquals(listOf("dQw4w9WgXcQ", "aaaaaaaaaaa"), merged.map { it.videoId })
        assertEquals("https://www.youtube.com/feeds/videos.xml?channel_id=UCx", ChannelFeed.urlOf("UCx"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BackupTest {
    @Test fun roundTripAndRejectsForeignFiles() {
        val watch = WatchData(
            positions = WatchRules.record(emptyList(), YT, "Song", 100.0, 600.0, 5),
            later = WatchRules.toggleLater(emptyList(), YT, "Song", 6),
            stats = mapOf("2026-10-19" to 3600L),
            channels = listOf(Channel("UCuAXFkgsw1L7xaCfnd5JJOw", "Rick")),
            groups = mapOf("Music" to listOf("UCuAXFkgsw1L7xaCfnd5JJOw"))
        )
        val settings = AppSettings(
            themeMode = ThemeMode.LIGHT, playbackSpeed = 150, breakReminderMinutes = 60, customBlockedDomains = setOf("ads.example"),
            seekSeconds = 15, qualityWifi = "hd1080", qualityMobile = "large", swipeControls = false, hiddenFeedParts = setOf("shorts"),
            deArrow = true, returnDislikes = true, thirdPartyCookies = false, searchSuggestions = false, sponsorMarkers = false, cleanShareLinks = false, siteBars = mapOf("YOUTUBE" to listOf("home", "search"))
        )
        val c = BackupContent(listOf(Bookmark("https://vimeo.com/", "Vimeo", 1)), listOf(HistoryEntry("https://arte.tv/", "ARTE", 2)), watch, settings)
        val back = Backup.decode(Backup.encode(c))
        assertEquals(c.bookmarks, back.bookmarks)
        assertEquals(c.history, back.history)
        assertEquals(watch, back.watch)
        assertEquals(settings, back.settings)
        for (bad in listOf("{}", "not json", """{"format":"streamshell-backup","version":99}""")) {
            assertTrue(runCatching { Backup.decode(bad) }.exceptionOrNull() is IllegalArgumentException)
        }
    }
}

class PlayerScriptsTest {
    @Test fun scripts() {
        assertEquals(12.5 to 600.0, com.streamshell.core.media.PlayerScripts.parsePosition("\"12.5|600\""))
        assertNull(com.streamshell.core.media.PlayerScripts.parsePosition("\"\""))
        assertNull(com.streamshell.core.media.PlayerScripts.parsePosition("\"1|Infinity\""))
        assertTrue(com.streamshell.core.media.PlayerScripts.speed(150).endsWith("(1.5)"))
        assertTrue(com.streamshell.core.media.PlayerScripts.speed(900).endsWith("(2.0)"))
        assertTrue(com.streamshell.core.media.PlayerScripts.audio(200, true).endsWith("(2.0,true)"))
        assertTrue(com.streamshell.core.media.PlayerScripts.audio(200, true).contains("createMediaElementSource"))
        assertTrue(com.streamshell.core.media.PlayerScripts.youtubeQuality(true).contains("'tiny'"))
        val s = com.streamshell.core.settings.SettingsSanitizer.sanitize(AppSettings(playbackSpeed = 333, volumeBoost = 999, breakReminderMinutes = 7))
        assertEquals(100, s.playbackSpeed)
        assertEquals(100, s.volumeBoost)
        assertEquals(0, s.breakReminderMinutes)
    }
}
