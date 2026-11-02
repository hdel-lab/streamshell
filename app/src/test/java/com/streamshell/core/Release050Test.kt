package com.streamshell.core

import com.streamshell.core.browser.BrowserTab
import com.streamshell.core.browser.Chapter
import com.streamshell.core.browser.ChapterRules
import com.streamshell.core.browser.PageKind
import com.streamshell.core.browser.TabsReducer
import com.streamshell.core.browser.TabsSerializer
import com.streamshell.core.browser.TabsState
import com.streamshell.core.browser.YouTubeExtras
import com.streamshell.core.browser.YouTubeRouter
import com.streamshell.core.media.PlayerScripts
import com.streamshell.core.model.AppSettings
import com.streamshell.core.settings.SettingsSanitizer
import com.streamshell.core.watch.Backup
import com.streamshell.core.watch.BackupContent
import com.streamshell.core.watch.ChannelRules
import com.streamshell.core.watch.NewPipeImport
import com.streamshell.core.watch.Playlist
import com.streamshell.core.watch.Playlists
import com.streamshell.core.watch.SubscriptionImport
import com.streamshell.core.watch.WatchData
import com.streamshell.core.watch.WatchLaterItem
import com.streamshell.core.watch.WatchPosition
import com.streamshell.core.watch.WatchSerializer
import com.streamshell.feature.settings.SettingsSearch
import com.streamshell.widget.WidgetSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val V1 = "https://m.youtube.com/watch?v=aaaaaaaaaaa"
private const val V2 = "https://m.youtube.com/watch?v=bbbbbbbbbbb"
private const val V3 = "https://m.youtube.com/watch?v=ccccccccccc"

/** 0.5.0: playlists, channel rules, imports, backup v2, private tabs, page kinds, settings, widget, scripts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release050Test {
    @Test fun playlistsCreateAddMoveRemoveRenameDelete() {
        var l = Playlists.create(emptyList(), "  Music  ")
        assertEquals(listOf("Music"), l.map { it.name })
        assertEquals(l, Playlists.create(l, "music")) // same name without case: unchanged
        assertEquals(l, Playlists.create(l, "   ")) // empty name
        l = Playlists.add(l, "Music", V1, "One", 1)
        l = Playlists.add(l, "Music", V2, "Two", 2)
        l = Playlists.add(l, "Music", "https://www.youtube.com/watch?v=aaaaaaaaaaa", "dup", 3) // same video
        assertEquals(listOf(V1, V2), l[0].items.map { it.url })
        l = Playlists.add(l, "Later", V3, "Three", 4) // new list on the fly
        assertEquals(listOf("Music", "Later"), l.map { it.name })
        l = Playlists.move(l, "Music", 0, 1)
        assertEquals(listOf(V2, V1), l[0].items.map { it.url })
        assertEquals(l, Playlists.move(l, "Music", 1, 1)) // past the end: unchanged
        l = Playlists.remove(l, "Music", 0)
        assertEquals(listOf(V1), l[0].items.map { it.url })
        assertTrue(Playlists.contains(l, "Music", V1))
        assertFalse(Playlists.contains(l, "Music", V2))
        assertEquals(l, Playlists.rename(l, "Music", "later")) // clash with another list
        l = Playlists.rename(l, "Music", "Songs")
        assertEquals(listOf("Songs", "Later"), l.map { it.name })
        l = Playlists.delete(l, "Later")
        assertEquals(listOf("Songs"), l.map { it.name })
        assertEquals(l, Playlists.add(l, "Songs", "http://insecure.example/v", "x", 5))
    }

    @Test fun channelSpeedAndStats() {
        var sp = ChannelRules.setSpeed(emptyMap(), " Some  Channel ", 175)
        assertEquals(175, ChannelRules.speedOf(sp, "some channel"))
        sp = ChannelRules.setSpeed(sp, "Other", 900)
        assertEquals(200, ChannelRules.speedOf(sp, "Other")) // clamped
        sp = ChannelRules.setSpeed(sp, "some channel", null)
        assertNull(ChannelRules.speedOf(sp, "Some Channel"))
        var st = ChannelRules.addTime(emptyMap(), "A", 60)
        st = ChannelRules.addTime(st, "B", 300)
        st = ChannelRules.addTime(st, "A", 30)
        st = ChannelRules.addTime(st, "", 999) // no channel: ignored
        assertEquals(listOf("B" to 300L, "A" to 90L), ChannelRules.top(st))
    }

    @Test fun newPipeAndTakeoutImportsAreDetected() {
        val json = """{"app_version":"0.27","subscriptions":[
            {"service_id":0,"url":"https://www.youtube.com/channel/UCaaaaaaaaaaaaaaaaaaaaaa","name":"First"},
            {"service_id":1,"url":"https://soundcloud.com/x","name":"Other service"},
            {"service_id":0,"url":"https://www.youtube.com/@handle","name":"No id"},
            {"service_id":0,"url":"https://www.youtube.com/channel/UCaaaaaaaaaaaaaaaaaaaaaa","name":"Dup"}]}"""
        val ch = SubscriptionImport.parse(json)
        assertEquals(listOf("UCaaaaaaaaaaaaaaaaaaaaaa" to "First"), ch.map { it.id to it.title })
        assertTrue(NewPipeImport.parse("not json").isEmpty())
        val csv = "Channel Id,Channel Url,Channel Title\nUCbbbbbbbbbbbbbbbbbbbbbb,http://www.youtube.com/channel/UCbbbbbbbbbbbbbbbbbbbbbb,Second\n"
        assertEquals(listOf("UCbbbbbbbbbbbbbbbbbbbbbb"), SubscriptionImport.parse(csv).map { it.id })
    }

    @Test fun watchDataRoundTripWithNewParts() {
        val d = WatchData(
            playlists = listOf(Playlist("Mix", listOf(WatchLaterItem(V1, "One", 5)))),
            channelSpeeds = mapOf("talk" to 150),
            channelStats = mapOf("Talk" to 120L)
        )
        val back = WatchSerializer.decode { WatchSerializer.encode(d)[it] }
        assertEquals(d.playlists, back.playlists)
        assertEquals(d.channelSpeeds, back.channelSpeeds)
        assertEquals(d.channelStats, back.channelStats)
        // Old stores (without the new parts) still read
        val old = WatchSerializer.decode { k -> if (k == "later") "[]" else null }
        assertTrue(old.playlists.isEmpty() && old.channelSpeeds.isEmpty())
    }

    @Test fun backupV2KeepsNewDataAndReadsV1() {
        val s = AppSettings(feedPreviews = true, blockedChannels = setOf("Spam"), leavePlayer = "close", accent = "red", amoledBlack = true, captions = "on", captionsLanguage = "de")
        val w = WatchData(playlists = listOf(Playlist("Mix", listOf(WatchLaterItem(V2, "Two", 1)))), channelSpeeds = mapOf("x" to 125))
        val text = Backup.encode(BackupContent(emptyList(), emptyList(), w, s), now = 1)
        val r = Backup.decode(text)
        assertEquals(w.playlists, r.watch.playlists)
        assertEquals(w.channelSpeeds, r.watch.channelSpeeds)
        assertEquals(true, r.settings.feedPreviews)
        assertEquals(setOf("Spam"), r.settings.blockedChannels)
        assertEquals("close", r.settings.leavePlayer)
        assertEquals("red", r.settings.accent)
        assertEquals(true, r.settings.amoledBlack)
        assertEquals("de", r.settings.captionsLanguage)
        val v1 = """{"format":"streamshell-backup","version":1,"watch":{"later":[]},"settings":{"autoplay":false}}"""
        val r1 = Backup.decode(v1)
        assertEquals(false, r1.settings.autoplay)
        assertEquals(AppSettings().leavePlayer, r1.settings.leavePlayer)
    }

    @Test fun sanitizerCleansNewSettings() {
        val s = SettingsSanitizer.sanitize(
            AppSettings(
                leavePlayer = "nope",
                captions = "maybe",
                captionsLanguage = "DE ",
                accent = "neon",
                blockedChannels = setOf("  A   B ", "a b", "", "x".repeat(200)),
                blockedKeywords = setOf("ok")
            )
        )
        assertEquals("mini", s.leavePlayer)
        assertEquals("default", s.captions)
        assertEquals("de", s.captionsLanguage)
        assertEquals("blue", s.accent)
        assertEquals(2, s.blockedChannels.size) // "A B" once (without case) + the long one cut to 60
        assertTrue(s.blockedChannels.all { it.length <= 60 })
        assertEquals("", SettingsSanitizer.sanitize(AppSettings(captionsLanguage = "<script>")).captionsLanguage)
    }

    @Test fun privateTabsAreNeverSaved() {
        var t = TabsState(listOf(BrowserTab(1, V1, showingStart = false)), 1)
        t = TabsReducer.newPrivateTab(t)
        assertTrue(t.active.private)
        val restored = TabsSerializer.decode(TabsSerializer.encode(t))
        assertEquals(listOf(1L), restored.tabs.map { it.id })
        assertEquals(1L, restored.activeId)
        assertFalse(restored.tabs.any { it.private })
    }

    @Test fun pageKindsAndShorts() {
        assertEquals("home", PageKind.of("https://m.youtube.com/"))
        assertEquals("video", PageKind.of(V1))
        assertEquals("shorts", PageKind.of("https://m.youtube.com/shorts/aaaaaaaaaaa"))
        assertEquals("channel", PageKind.of("https://m.youtube.com/@someone"))
        assertEquals("search", PageKind.of("https://m.youtube.com/results?search_query=x"))
        assertEquals("other site", PageKind.of("https://example.com/watch"))
        assertTrue(PageKind.isVideoPage(V1))
        assertFalse(PageKind.isVideoPage("https://m.youtube.com/feed/subscriptions"))
        assertEquals("https://m.youtube.com/watch?v=aaaaaaaaaaa", PageKind.shortsAsWatch("https://m.youtube.com/shorts/aaaaaaaaaaa?feature=share"))
        assertNull(PageKind.shortsAsWatch(V1))
    }

    @Test fun nextChapter() {
        val c = listOf(Chapter(0, "Intro"), Chapter(60, "Main"), Chapter(300, "End"))
        assertEquals("Main", ChapterRules.next(c, 10.0)?.title)
        assertEquals("End", ChapterRules.next(c, 59.5)?.title) // at the very start of a chapter: the one after
        assertNull(ChapterRules.next(c, 400.0))
    }

    @Test fun settingsSearchMatchesEveryWord() {
        assertTrue(SettingsSearch.matches("", SettingsSearch.ABOUT))
        assertTrue(SettingsSearch.matches("AMOLED", SettingsSearch.APPEARANCE))
        assertTrue(SettingsSearch.matches("blocked channels", SettingsSearch.FEED))
        assertFalse(SettingsSearch.matches("blocked theme", SettingsSearch.FEED))
    }

    @Test fun widgetSnapshotKeepsThreeHttpsEntries() {
        val list = (1..5).map { WatchPosition("k$it", "https://m.youtube.com/watch?v=aaaaaaaaaa$it", "T$it", 30.0, 600.0, it.toLong()) } +
            WatchPosition("x", "http://insecure/v", "no", 1.0, 2.0, 9)
        val items = WidgetSnapshot.decode(WidgetSnapshot.encode(list))
        assertEquals(3, items.size)
        assertEquals("T1", items[0].title)
        assertTrue(WidgetSnapshot.decode("garbage").isEmpty())
    }

    @Test fun themeAmoledAndAccent() {
        val dark = com.streamshell.designsystem.StreamColors.Dark
        val t = com.streamshell.designsystem.themed(dark, dark = true, amoledBlack = true, accent = 0xFF2ECC71)
        assertEquals(androidx.compose.ui.graphics.Color.Black, t.background)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF2ECC71), t.accent)
        val light = com.streamshell.designsystem.StreamColors.Light
        assertEquals(light.background, com.streamshell.designsystem.themed(light, dark = false, amoledBlack = true, accent = null).background) // light: no black
    }

    @Test fun scriptsCarryTheNewParts() {
        val r = YouTubeRouter.INSTALL
        assertTrue(r.contains("visibilityState") && r.contains("stopImmediatePropagation"))
        assertTrue(r.contains("tap guard") && r.contains("${YouTubeRouter.AFTER_SCROLL_MS}"))
        assertTrue(r.contains("__ssShortsAsVideos"))
        val cfg = YouTubeExtras.config("", false, false, false, watched = listOf("aaaaaaaaaaa", "bad id", "aaaaaaaaaaa"), channels = listOf(" Spam "), words = listOf("Trailer"))
        assertTrue(cfg.contains("\"watched\":[\"aaaaaaaaaaa\"]"))
        assertTrue(cfg.contains("\"chans\":[\"spam\"]") && cfg.contains("\"words\":[\"trailer\"]"))
        assertTrue(YouTubeExtras.INSTALL.contains("hideTiles"))
        assertTrue(PlayerScripts.zoom(true).contains("cover") && PlayerScripts.zoom(false).contains("removeProperty"))
        assertTrue(PlayerScripts.repeat(true, 10.0, 20.0).endsWith("(true,[10.0,20.0])"))
        assertTrue(PlayerScripts.repeat(false, 10.0, 10.2).endsWith("(false,null)")) // too short a part
        assertEquals("'default'", PlayerScripts.captions("default", "en"))
        assertTrue(PlayerScripts.captions("on", "de").endsWith("('on','de')"))
        assertTrue(PlayerScripts.captions("on", "x'); alert(1); ('").endsWith("('on','')")) // no injection
        assertTrue(com.streamshell.core.browser.YouTubeAds.INSTALL.contains("Math.abs(d-adDur)<=1.5"))
    }

    @Test fun slidingPlayerBarOrderAndCentre() {
        val bar = com.streamshell.feature.browser.PlayerBar
        assertEquals(listOf("search", "home", "subscriptions", "library", "later", "queue", "playlist", "speed", "repeat", "more"), bar.keys(false))
        assertTrue("chapter" in bar.keys(true) && bar.keys(true).last() == "more")
        assertTrue(bar.START in bar.keys(false) && bar.FIRST_ACTION in bar.keys(false))
        // items 64 px wide starting at 0, 64, 128 — centre 100 is nearest to the second (centre 96)
        assertEquals(1, bar.nearest(listOf(0 to 64, 64 to 64, 128 to 64), 100))
        assertEquals(-1, bar.nearest(emptyList(), 100))
        assertTrue(bar.ticks(3, 4, sliding = true))
        assertFalse(bar.ticks(-1, 4, sliding = true)) // first layout
        assertFalse(bar.ticks(4, 4, sliding = true))
        assertFalse(bar.ticks(3, 4, sliding = false)) // set by the app, not slid
        // Pulling on past an end: right at the start, left at the end; other moves start over
        assertEquals(30f, bar.push(10f, 20f, atStart = true, atEnd = false), 0f)
        assertEquals(15f, bar.push(0f, -15f, atStart = false, atEnd = true), 0f)
        assertEquals(0f, bar.push(25f, -5f, atStart = true, atEnd = false), 0f) // back towards the middle
        assertEquals(0f, bar.push(25f, 20f, atStart = false, atEnd = false), 0f) // not at an end
        assertEquals(25f, bar.push(25f, 0f, atStart = true, atEnd = false), 0f) // no move
        // Home position: Speed in the middle of five, the bar full
        assertEquals(5, bar.homeFirst(bar.keys(false))) // 10 items: Queue · Playlist · Speed · Repeat · More (the right end)
        assertEquals(5, bar.homeFirst(bar.keys(true))) // 11 items: Queue · Playlist · Speed · Repeat · Chapter
        assertEquals(0, bar.homeFirst(listOf("speed", "a")))
        // Rubber: grows with the pull, never past the maximum
        assertEquals(0f, bar.rubber(0f, 100f), 0f)
        assertEquals(50f, bar.rubber(100f, 100f), 0.01f)
        assertTrue(bar.rubber(10_000f, 100f) < 100f)
    }

    @Test fun miniPlayerSwipedAway() {
        val w = 1000f
        val mw = 300f
        assertTrue(com.streamshell.feature.browser.MiniDock.closes(-250f, mw, w)) // mostly beyond the left edge
        assertTrue(com.streamshell.feature.browser.MiniDock.closes(w - 50f, mw, w)) // mostly beyond the right edge
        assertFalse(com.streamshell.feature.browser.MiniDock.closes(-120f, mw, w)) // hidden at the edge, not closed
        assertFalse(com.streamshell.feature.browser.MiniDock.closes(100f, mw, w))
    }
}
