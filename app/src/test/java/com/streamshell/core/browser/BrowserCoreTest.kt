package com.streamshell.core.browser

import com.streamshell.core.model.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressInputTest {
    private fun r(t: String) = AddressInput.resolve(t, SearchEngine.GOOGLE)

    @Test fun addresses() {
        assertEquals(AddressTarget.Url("https://youtube.com"), r("youtube.com"))
        assertEquals(AddressTarget.Url("https://m.youtube.com/feed/subscriptions"), r("  m.youtube.com/feed/subscriptions "))
        assertEquals(AddressTarget.Url("https://example.com/a?b=1"), r("https://example.com/a?b=1"))
        assertEquals(AddressTarget.Url("https://example.com/"), r("http://example.com/"))
        assertEquals(AddressTarget.Url("https://example.co.uk:8443/x"), r("example.co.uk:8443/x"))
    }

    @Test fun searches() {
        val s = r("lofi hip hop") as AddressTarget.Search
        assertEquals("https://www.google.com/search?q=lofi+hip+hop", s.url)
        assertTrue(r("kotlin") is AddressTarget.Search)
        assertTrue(r("javascript:alert(1)") is AddressTarget.Search) // never executed, only searched
        assertTrue(r("file:///etc/passwd") is AddressTarget.Search)
        assertTrue(r("version 1.2") is AddressTarget.Search)
        assertTrue(r("1.5") is AddressTarget.Search) // no TLD
        assertEquals("https://m.youtube.com/results?search_query=a%26b", (AddressInput.resolve("a&b", SearchEngine.YOUTUBE) as AddressTarget.Search).url)
    }

    @Test fun emptyAndDisplay() {
        assertSame(AddressTarget.Empty, r("   "))
        assertEquals("youtube.com/watch?v=x", AddressInput.display("https://www.youtube.com/watch?v=x"))
        assertEquals("example.com", AddressInput.display("https://example.com/"))
        assertEquals("", AddressInput.display(null))
    }
}

class NavigationPolicyTest {
    @Test fun webPagesLoadInAppAndHttpIsUpgraded() {
        assertEquals(NavigationDecision.InApp("https://vimeo.com/1"), NavigationPolicy.decide("https://vimeo.com/1", false))
        assertEquals(NavigationDecision.InApp("https://example.com/a"), NavigationPolicy.decide("http://example.com/a", false))
        assertEquals(NavigationDecision.InApp("about:blank"), NavigationPolicy.decide("about:blank", false))
    }

    @Test fun appLinksNeedAGesture() {
        assertEquals(NavigationDecision.External("mailto:a@b.de"), NavigationPolicy.decide("mailto:a@b.de", true))
        assertEquals(NavigationDecision.Refuse, NavigationPolicy.decide("mailto:a@b.de", false))
        assertEquals(NavigationDecision.External("intent://x#Intent;scheme=y;end"), NavigationPolicy.decide("intent://x#Intent;scheme=y;end", true))
        assertEquals(NavigationDecision.Refuse, NavigationPolicy.decide("market://details?id=x", false))
    }

    @Test fun dangerousSchemesNeverNavigate() {
        for (u in listOf("javascript:alert(1)", "file:///sdcard/x", "content://x/y", "data:text/html,hi", "chrome://flags", "https://", "weird:thing")) {
            assertEquals(u, NavigationDecision.Refuse, NavigationPolicy.decide(u, true))
        }
    }

    @Test fun intentFallbackMustBeHttps() {
        assertEquals("https://m.youtube.com/watch?v=1", NavigationPolicy.intentFallback("intent://x#Intent;scheme=vnd.youtube;S.browser_fallback_url=https%3A%2F%2Fm.youtube.com%2Fwatch%3Fv%3D1;end"))
        assertNull(NavigationPolicy.intentFallback("intent://x#Intent;S.browser_fallback_url=javascript%3Aalert(1);end"))
        assertNull(NavigationPolicy.intentFallback("intent://x#Intent;scheme=y;end"))
    }
}

class TabsTest {
    @Test fun instanceAndPlayerRolesSurviveRestartAndBackgroundTabsDoNotSteal() {
        val base = TabsState.initial()
        val inst = TabsReducer.update(base, base.activeId) { it.copy(url = "https://m.youtube.com/", showingStart = false, instance = "YOUTUBE") }
        val (withPlayer, pid) = TabsReducer.newBackgroundTab(inst) { it.copy(playerFor = base.activeId, url = "https://m.youtube.com/watch?v=dQw4w9WgXcQ", showingStart = false) }
        assertEquals(base.activeId, withPlayer.activeId)
        assertEquals(pid, TabsReducer.playerTabOf(withPlayer, base.activeId)?.id)
        assertEquals(base.activeId, TabsReducer.instanceTab(withPlayer, "YOUTUBE")?.id)
        assertEquals(withPlayer, TabsSerializer.decode(TabsSerializer.encode(withPlayer)))
        assertEquals(1, TabsReducer.visibleCount(withPlayer)) // the player counts as part of its instance
    }

    @Test fun newSelectClose() {
        var s = TabsState.initial()
        s = TabsReducer.newTab(s, "https://a.example/")
        s = TabsReducer.newTab(s)
        assertEquals(listOf(1L, 2L, 3L), s.tabs.map { it.id })
        assertEquals(3L, s.activeId)
        assertTrue(s.active.showingStart)
        assertTrue(!s.tabs[1].showingStart)
        s = TabsReducer.select(s, 2)
        assertEquals(2L, s.activeId)
        s = TabsReducer.close(s, 2) // closing the active tab activates its neighbour
        assertEquals(3L, s.activeId)
        assertEquals(s, TabsReducer.select(s, 99))
    }

    @Test fun closingTheLastTabLeavesAFreshStartTab() {
        val s = TabsReducer.close(TabsState.initial(), 1)
        assertEquals(1, s.tabs.size)
        assertTrue(s.active.showingStart && s.active.url == null && s.active.id != 1L)
    }

    @Test fun tabLimit() {
        var s = TabsState.initial()
        repeat(20) { s = TabsReducer.newTab(s) }
        assertEquals(TabsReducer.MAX_TABS, s.tabs.size)
        assertSame(s, TabsReducer.newTab(s))
    }

    @Test fun serializerRoundTripAndCorruption() {
        var s = TabsReducer.newTab(TabsState.initial(), "https://a.example/x")
        s = TabsReducer.update(s, 2) { it.copy(title = "A") }
        assertEquals(s, TabsSerializer.decode(TabsSerializer.encode(s)))
        assertEquals(TabsState.initial(), TabsSerializer.decode("{nope"))
        assertEquals(TabsState.initial(), TabsSerializer.decode(null))
        // Non-https URLs from a tampered store are dropped (tab falls back to the start page).
        val tampered = """{"tabs":[{"id":5,"u":"javascript:alert(1)","t":"x","s":false}],"active":5}"""
        val d = TabsSerializer.decode(tampered)
        assertNull(d.active.url)
        assertTrue(d.active.showingStart)
    }
}

class SearchSuggestionsTest {
    @org.junit.Test fun parsesYouTubeSuggestions() {
        val r = SearchSuggestions.parse("""["gta",["gta 6","GTA 6","gta 5 online"],[],{}]""")
        org.junit.Assert.assertEquals(listOf("gta 6", "gta 5 online"), r)
        org.junit.Assert.assertTrue(SearchSuggestions.parse("<html>").isEmpty())
        org.junit.Assert.assertTrue(SearchSuggestions.parse(null).isEmpty())
    }

    @org.junit.Test fun emptyFieldShowsRecentTypingMergesWithoutDuplicates() {
        val recent = listOf("gta trailer", "lofi", "gta 6")
        org.junit.Assert.assertEquals(recent, SearchSuggestions.merge("  ", recent, listOf("x")))
        org.junit.Assert.assertEquals(listOf("gta trailer", "gta 6", "gta 5"), SearchSuggestions.merge("gta", recent, listOf("GTA 6", "gta 5")))
    }

    @org.junit.Test fun remembersNewestFirstWithoutDuplicates() {
        val r = SearchSuggestions.remember(listOf("a", "Lofi", "b"), " lofi ")
        org.junit.Assert.assertEquals(listOf("lofi", "a", "b"), r)
        org.junit.Assert.assertEquals(SearchSuggestions.MAX_RECENT, (1..40).fold(emptyList<String>()) { l, i -> SearchSuggestions.remember(l, "q$i") }.size)
    }

    @org.junit.Test fun urlCarriesOnlyQueryAndLanguage() {
        val u = SearchSuggestions.url("a&b c", "bs")
        org.junit.Assert.assertTrue(u.startsWith("https://suggestqueries.google.com/complete/search?"))
        org.junit.Assert.assertTrue(u.endsWith("&hl=bs&q=a%26b+c"))
    }
}

class FreshStartTest {
    private val yt = SiteApp.YOUTUBE.name

    @org.junit.Test fun startPageInsteadOfLastVideo() {
        val s = TabsState(listOf(BrowserTab(1, "https://vimeo.com/1", showingStart = false), BrowserTab(2, "https://m.youtube.com/watch?v=dQw4w9WgXcQ", showingStart = false)), 2)
        val r = TabsReducer.freshStart(s, null, null)
        org.junit.Assert.assertTrue(r.active.showingStart)
        org.junit.Assert.assertNull(r.active.url)
        org.junit.Assert.assertEquals(3, r.tabs.size)
    }

    @org.junit.Test fun emptyTabIsReused() {
        val s = TabsState(listOf(BrowserTab(1, null), BrowserTab(2, "https://vimeo.com/1", showingStart = false)), 2)
        val r = TabsReducer.freshStart(s, null, null)
        org.junit.Assert.assertEquals(1L, r.activeId)
        org.junit.Assert.assertEquals(2, r.tabs.size)
    }

    @org.junit.Test fun defaultInstanceGoesHomeAndPlayerIsDropped() {
        val s = TabsState(
            listOf(
                BrowserTab(1, "https://m.youtube.com/feed/subscriptions", showingStart = false, instance = yt),
                BrowserTab(2, "https://m.youtube.com/watch?v=dQw4w9WgXcQ", showingStart = false, playerFor = 1)
            ),
            2
        )
        val r = TabsReducer.freshStart(s, yt, "https://m.youtube.com/")
        org.junit.Assert.assertEquals(listOf(1L), r.tabs.map { it.id })
        org.junit.Assert.assertEquals(1L, r.activeId)
        org.junit.Assert.assertEquals("https://m.youtube.com/", r.active.url)
    }

    @org.junit.Test fun fullTabListStillShowsStartPage() {
        val s = TabsState((1L..TabsReducer.MAX_TABS).map { BrowserTab(it, "https://vimeo.com/$it", showingStart = false) }, 5)
        val r = TabsReducer.freshStart(s, null, null)
        org.junit.Assert.assertTrue(r.active.showingStart)
    }
}

class SiteBarTest {
    private val yt = SiteApp.YOUTUBE

    @org.junit.Test fun defaultIsSearchAndMainSections() {
        // 0.5.0: StreamShell's Library is on the default YouTube bar
        org.junit.Assert.assertEquals(listOf("search", "home", "subscriptions", "you", "ss-library"), SiteBar.items(yt, null))
        org.junit.Assert.assertEquals(listOf("shorts", "live"), SiteBar.hidden(yt, null).map { it.id })
    }

    @org.junit.Test fun unknownAndDuplicateKeysAreDroppedEmptyMeansDefault() {
        org.junit.Assert.assertEquals(listOf("live", "home"), SiteBar.items(yt, listOf("live", "bogus", "home", "live")))
        org.junit.Assert.assertEquals(SiteBar.default(yt), SiteBar.items(yt, listOf("bogus")))
    }

    @org.junit.Test fun toggleKeepsAtLeastOneAndAtMostMax() {
        org.junit.Assert.assertEquals(listOf("home"), SiteBar.toggle(yt, listOf("home"), "home"))
        val full = listOf("search", "home", "subscriptions", "you", "shorts")
        org.junit.Assert.assertEquals(full, SiteBar.toggle(yt, full, "live"))
        org.junit.Assert.assertEquals(SiteBar.default(yt), SiteBar.toggle(yt, null, "live")) // full: nothing added
        org.junit.Assert.assertEquals(listOf("search", "home", "subscriptions", "you", "live"), SiteBar.toggle(yt, listOf("search", "home", "subscriptions", "you"), "live"))
        org.junit.Assert.assertEquals(listOf("search", "home", "subscriptions", "you"), SiteBar.toggle(yt, null, "ss-library"))
    }

    @org.junit.Test fun moveStaysInsideTheBar() {
        org.junit.Assert.assertEquals(listOf("home", "search", "subscriptions", "you", "ss-library"), SiteBar.move(yt, null, "search", 1))
        org.junit.Assert.assertEquals(SiteBar.default(yt), SiteBar.move(yt, null, "search", -1))
    }

    @org.junit.Test fun encodesPerApp() {
        val m = mapOf("YOUTUBE" to listOf("home", "search"), "YOUTUBE_MUSIC" to listOf("library"), "NOPE" to listOf("home"))
        val r = SiteBar.decode(SiteBar.encode(m))
        org.junit.Assert.assertEquals(mapOf("YOUTUBE" to listOf("home", "search"), "YOUTUBE_MUSIC" to listOf("library")), r)
        org.junit.Assert.assertTrue(SiteBar.decode("garbage;;=").isEmpty())
    }
}

class StoryboardTest {
    private val spec = "https://i.ytimg.com/sb/dQw4w9WgXcQ/storyboard3_L\$L/\$N.jpg?sqp=abc" +
        "|48#27#100#10#10#0#default#rs\$AAA" +
        "|80#45#107#10#10#2000#M\$M#rs\$BBB" +
        "|160#90#107#5#5#2000#M\$M#rs\$CCC"

    @org.junit.Test fun picksTheFrameOfTheSecond() {
        // 41 s at 2 s per picture = picture 20 → sheet 0, column 0, row 4 of a 5×5 sheet of 160×90 pictures
        val f = Storyboard.frame(spec, 214.0, 41)!!
        org.junit.Assert.assertEquals("https://i.ytimg.com/sb/dQw4w9WgXcQ/storyboard3_L2/M0.jpg?sqp=abc&sigh=rs\$CCC", f.url)
        org.junit.Assert.assertEquals(StoryboardFrame(f.url, 0, 360, 160, 90), f)
        val later = Storyboard.frame(spec, 214.0, 120)!! // picture 60 → sheet 2, index 10 → column 0, row 2
        org.junit.Assert.assertTrue(later.url.contains("/M2.jpg"))
        org.junit.Assert.assertEquals(0 to 180, later.x to later.y)
    }

    @org.junit.Test fun endIsClampedAndBadSpecsGiveNull() {
        org.junit.Assert.assertNotNull(Storyboard.frame(spec, 214.0, 99_999))
        org.junit.Assert.assertNull(Storyboard.frame("", 10.0, 1))
        org.junit.Assert.assertNull(Storyboard.frame("https://evil.example/\$L/\$N.jpg|160#90#10#5#5#0#M\$M#x", 10.0, 1))
        org.junit.Assert.assertNull(Storyboard.frame("https://i.ytimg.com/sb/x/\$L/\$N.jpg|bad", 10.0, 1))
    }

    @org.junit.Test fun withoutIntervalUsesTheDuration() {
        val s = "https://i.ytimg.com/sb/x/L\$L/\$N.jpg?a=1|160#90#10#5#2#0#M\$M#s"
        val f = Storyboard.frame(s, 100.0, 50)!! // half way → picture 5 → column 0, row 1
        org.junit.Assert.assertEquals(0 to 90, f.x to f.y)
    }
}

class ChapterTapScriptTest {
    @org.junit.Test fun titlesAreJsonEscapedAndCoordinatesPassed() {
        val js = YouTubeTimestamps.chapterTitleAt(12.5f, 40f, listOf("A \"quoted\" one", "</script>"))
        org.junit.Assert.assertTrue(js.endsWith("(12.5,40.0,[\"A \\\"quoted\\\" one\",\"<\\/script>\"])"))
        org.junit.Assert.assertTrue(js.contains("elementFromPoint(px/r,py/r)"))
    }
}

class ChapterPicturesTest {
    @org.junit.Test fun pictureOnlyFromYouTubeImageHost() {
        val l = Chapters.parse("""[{"t":0,"l":"Intro","i":"https://i.ytimg.com/vi/x/hqdefault_0.jpg"},{"t":41,"l":"Deploy","i":"https://evil.example/a.jpg"},{"t":90,"l":"End"}]""")
        org.junit.Assert.assertEquals("https://i.ytimg.com/vi/x/hqdefault_0.jpg", l[0].picture)
        org.junit.Assert.assertNull(l[1].picture)
        org.junit.Assert.assertNull(l[2].picture)
    }
}

class ChatWindowTest {
    private val d = 2.625f

    @org.junit.Test fun fillsTheRoomUnderThePlayerOnTheRight() {
        // The user's Fold: about 704 × 638 dp of content; YouTube's wide layout makes the player about 300 dp high.
        val w = (704 * d).toInt()
        val h = (638 * d).toInt()
        val player = (300 * d).toInt()
        val c = com.streamshell.feature.browser.ChatWindow.compute(w, h, d, player)
        org.junit.Assert.assertEquals(player + (12 * d).toInt(), c.top) // starts right under the video
        org.junit.Assert.assertEquals(h - (12 * d).toInt(), c.bottom)
        org.junit.Assert.assertTrue(c.height >= (300 * d).toInt()) // room to read
        org.junit.Assert.assertTrue(c.width >= (300 * d).toInt() && c.right == w - (12 * d).toInt())
    }

    @org.junit.Test fun littleRoomOverlapsThePlayerEdgeButStaysReadable() {
        val w = (704 * d).toInt()
        val h = (500 * d).toInt()
        val c = com.streamshell.feature.browser.ChatWindow.compute(w, h, d, (396 * d).toInt())
        org.junit.Assert.assertTrue(c.height >= (260 * d).toInt() - 1)
    }

    @org.junit.Test fun withoutPlayerInfoAssumes16by9() {
        val w = (800 * d).toInt()
        val h = (1200 * d).toInt()
        val c = com.streamshell.feature.browser.ChatWindow.compute(w, h, d)
        org.junit.Assert.assertTrue(c.top >= w * 9 / 16)
    }
}

class MiniDockTest {
    private val dock = com.streamshell.feature.browser.MiniDock
    private val w = 1000f
    private val mw = 400f
    private val m = 30f

    @org.junit.Test fun restsAtTheNearerSide() {
        org.junit.Assert.assertEquals(dock.LEFT to false, dock.settle(80f, mw, w))
        org.junit.Assert.assertEquals(dock.RIGHT to false, dock.settle(450f, mw, w))
    }

    @org.junit.Test fun overAnEdgeByAThirdHidesThere() {
        org.junit.Assert.assertEquals(dock.LEFT to true, dock.settle(-150f, mw, w))
        org.junit.Assert.assertEquals(dock.RIGHT to true, dock.settle(750f, mw, w))
        org.junit.Assert.assertEquals(dock.LEFT to false, dock.settle(-100f, mw, w)) // only a quarter out: not hidden
    }

    @org.junit.Test fun hiddenIsFullyOffScreen() {
        org.junit.Assert.assertTrue(dock.restX(dock.LEFT, true, w, mw, m) + mw <= 0f)
        org.junit.Assert.assertTrue(dock.restX(dock.RIGHT, true, w, mw, m) >= w)
        org.junit.Assert.assertEquals(m, dock.restX(dock.LEFT, false, w, mw, m))
        org.junit.Assert.assertEquals(w - mw - m, dock.restX(dock.RIGHT, false, w, mw, m))
    }
}

class PositionOwnerTest {
    private val scripts = com.streamshell.core.media.PlayerScripts

    @org.junit.Test fun positionCarriesThePlayingVideoId() {
        org.junit.Assert.assertEquals(Triple(61.5, 300.0, "dQw4w9WgXcQ"), scripts.parsePositionAndId("\"61.5|300|dQw4w9WgXcQ\""))
        org.junit.Assert.assertEquals(Triple(61.5, 300.0, ""), scripts.parsePositionAndId("61.5|300|"))
        org.junit.Assert.assertEquals(61.5 to 300.0, scripts.parsePosition("61.5|300|dQw4w9WgXcQ"))
    }

    @org.junit.Test fun aPositionOfAnotherVideoIsNotThisVideos() {
        org.junit.Assert.assertFalse(scripts.belongsTo("OLDvideo000", "https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
        org.junit.Assert.assertFalse(scripts.belongsTo("", "https://m.youtube.com/watch?v=dQw4w9WgXcQ")) // player not ready yet
        org.junit.Assert.assertTrue(scripts.belongsTo("dQw4w9WgXcQ", "https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=5"))
        org.junit.Assert.assertTrue(scripts.belongsTo("", "https://vimeo.com/123")) // other sites: no id check
    }
}

class ExternalLinksTest {
    @Test fun youtubeVideoLinksGoToThePlayer() {
        org.junit.Assert.assertEquals("https://m.youtube.com/watch?v=dQw4w9WgXcQ", ExternalLinks.youtubeWatch("https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=share"))
        org.junit.Assert.assertEquals("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=75s", ExternalLinks.youtubeWatch("https://youtu.be/dQw4w9WgXcQ?t=75"))
        org.junit.Assert.assertEquals("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=135s", ExternalLinks.youtubeWatch("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=2m15s"))
    }

    @Test fun everythingElseOpensAsATab() {
        for (u in listOf(
            "https://m.youtube.com/shorts/dQw4w9WgXcQ",
            "https://www.youtube.com/@channel",
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://example.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/watch?v=short"
        )) {
            org.junit.Assert.assertNull(u, ExternalLinks.youtubeWatch(u))
        }
    }
}

class PageScriptFramesTest {
    /** Page scripts run in YouTube's own top page only — not in the live chat frame or in players embedded on other sites. */
    @Test fun layoutScriptsStayOutOfFrames() {
        val guard = "if(window.top&&window.top!==window)return 'frame';"
        for (js in listOf(YouTubeSite.INSTALL, YouTubeAutoplay.INSTALL, YouTubeRouter.INSTALL, YouTubeTimestamps.INSTALL, YouTubeLiveChat.INSTALL)) {
            org.junit.Assert.assertTrue(js.contains(guard))
        }
        // The ad skipper also works in embedded players.
        org.junit.Assert.assertFalse(YouTubeAds.INSTALL.contains(guard))
    }

    @Test fun playAndPauseUseYouTubesPlayer() {
        org.junit.Assert.assertTrue(com.streamshell.core.media.MediaScripts.PLAY.contains("mp.playVideo()"))
        org.junit.Assert.assertTrue(com.streamshell.core.media.MediaScripts.PAUSE.contains("mp.pauseVideo()"))
    }

    @Test fun speedChosenInYouTubesMenuIsKept() {
        val js = com.streamshell.core.media.PlayerScripts.speed(150)
        org.junit.Assert.assertTrue(js.contains("'ratechange'") && js.contains("Date.now()-tapAt>4000") && js.contains("window.__ssSetting"))
    }
}

class ExtrasTest {
    @Test fun shareLinksLoseTracking() {
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42", ShareLinks.clean("https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=abc&t=42&pp=xyz"))
        assertEquals("https://youtu.be/dQw4w9WgXcQ", ShareLinks.clean("https://youtu.be/dQw4w9WgXcQ?si=Zz"))
        assertEquals("https://example.com/a?id=5#top", ShareLinks.clean("https://example.com/a?utm_source=x&id=5&fbclid=y#top"))
        assertEquals("https://example.com/a", ShareLinks.clean("https://example.com/a"))
    }

    @Test fun feedFilterStyle() {
        assertEquals("", FeedFilter.css(emptySet()))
        val css = FeedFilter.css(setOf("shorts", "unknown"))
        assertTrue(css.contains("ytm-reel-shelf-renderer") && css.endsWith("{display:none!important}") && !css.contains("backstage"))
    }

    @Test fun extrasConfigAndAnswersAreSafeScripts() {
        val c = YouTubeExtras.config("a{b:c}", dislikes = true, deArrow = false, markers = true)
        assertTrue(c.startsWith("window.__ssExtras={") && c.contains("\"dislikes\":true"))
        assertEquals("window.__ssDislikes&&window.__ssDislikes(\"dQw4w9WgXcQ\",-1);", YouTubeExtras.dislikes("dQw4w9WgXcQ", null))
        val t = YouTubeExtras.deArrowTitles(mapOf("dQw4w9WgXcQ" to "A \"quoted\" </script> title"))
        assertTrue(t.contains("\\\"quoted\\\"") && t.contains("<\\/script>"))
        assertTrue(YouTubeExtras.INSTALL.contains("if(window.top&&window.top!==window)return 'frame';"))
    }
}

class PrivacyDefaultsTest {
    @Test fun thirdPartyTransfersOffOrOptional() {
        val d = com.streamshell.core.model.AppSettings()
        assertTrue(!d.returnDislikes) // video ID to a third party only after switching on
        assertTrue(!d.deArrow)
        assertTrue(d.searchSuggestions && d.thirdPartyCookies) // switchable, on for the features the user uses
    }
}

class PageMessageTest {
    @Test fun knownMessagesAreTyped() {
        assertEquals(PageMessage.AdSkipped, PageMessage.parse("ad|0"))
        assertEquals(PageMessage.TapSkip(10, 20, 400, 800), PageMessage.parse("adtap|10|20|400|800"))
        assertEquals(PageMessage.Live(true), PageMessage.parse("live|1"))
        assertEquals(PageMessage.OpenChapters, PageMessage.parse("chapters|open"))
        assertEquals(PageMessage.DislikesRequest("dQw4w9WgXcQ"), PageMessage.parse("rytd|dQw4w9WgXcQ"))
        assertEquals(PageMessage.DeArrowRequest(listOf("dQw4w9WgXcQ")), PageMessage.parse("dearrow|dQw4w9WgXcQ,bad,"))
        assertEquals(PageMessage.Panel(false), PageMessage.parse("panel|0"))
        assertEquals(PageMessage.SponsorSkipped("sponsor", 86_400), PageMessage.parse("sponsor|999999"))
        assertEquals(PageMessage.Diagnostics("x".repeat(300)), PageMessage.parse("diag|" + "x".repeat(500)))
        assertEquals(2, (PageMessage.parse("""chapters|[{"t":0,"l":"A"},{"t":30,"l":"B"}]""") as PageMessage.ChapterList).chapters.size)
    }

    @Test fun anythingElseIsIgnored() {
        for (bad in listOf(
            null, "", "hello", "adtap|5|5|0|0", "adtap|900|5|400|800", "adtap|1|2|3", "rytd|short", "dearrow|", "unknown|5",
            "storyboard|bad|1|x", "x".repeat(60_000)
        )) {
            assertNull(bad?.take(20), PageMessage.parse(bad))
        }
    }
}

class DiagnosticsLogTest {
    @Test fun keepsTheNewestLinesWithTime() {
        val log = com.streamshell.feature.browser.DiagnosticsLog({ 0L }) { _, _ -> }
        repeat(com.streamshell.feature.browser.DiagnosticsLog.MAX + 5) { log.add("line $it") }
        val all = log.snapshot()
        assertEquals(com.streamshell.feature.browser.DiagnosticsLog.MAX, all.size)
        assertEquals(5L, log.dropped)
        assertTrue(all.last().endsWith("line ${com.streamshell.feature.browser.DiagnosticsLog.MAX + 4}"))
        assertTrue(Regex("^\\d\\d:\\d\\d:\\d\\d\\.\\d{3} line").containsMatchIn(all.first()))
        assertTrue(log.text().contains("\n"))
        log.clear()
        assertTrue(log.snapshot().isEmpty() && log.all.value.isEmpty() && log.dropped == 0L)
    }

    @Test fun publishesAtOnceWithoutSchedulerAndLaterWithOne() {
        val now = com.streamshell.feature.browser.DiagnosticsLog({ 0L })
        now.add("a")
        assertEquals(1, now.all.value.size)
        val queued = ArrayList<Runnable>()
        val later = com.streamshell.feature.browser.DiagnosticsLog({ 0L }) { r, _ -> queued += r }
        repeat(50) { later.add("x$it") }
        assertEquals(1, queued.size) // one publish for a burst
        assertTrue(later.all.value.isEmpty())
        queued.single().run()
        assertEquals(50, later.all.value.size)
    }
}

class LogFindingsTest {
    // 0.4.1 changes driven by the user's diagnostics log (2026-10-28).
    @Test fun adSkipperReloadsOnlyAsLastResort() {
        val js = YouTubeAds.INSTALL
        // no reload at the start of an ad any more; only after RELOAD_AFTER_MS for an ad that cannot be jumped
        assertTrue(!js.contains("muteAd(v);if(reload())"))
        assertTrue(js.contains("Date.now()-adAt>${YouTubeAds.RELOAD_AFTER_MS}"))
    }

    @Test fun fullscreenGuardWaitsForReadyMediaAndTriesOnce() {
        val js = YouTubeTimestamps.INSTALL
        assertTrue(js.contains("guardN<1") && js.contains("v.readyState>=2&&isFinite(v.duration)&&v.duration>guardPos"))
    }

    @Test fun openingAVideoFromTheMiniPlayerNeverHandsPlaybackToTheFeed() {
        // 0.4.2 log: mini player (tab 4) plays, a video is tapped in the feed (tab 3). The order of the two steps decides
        // which tab may play in between: clearing the mini player first gave the feed tab a moment (its preview loaded).
        val feedActive = TabsState(listOf(BrowserTab(3), BrowserTab(4)), 3)
        val playerActive = feedActive.copy(activeId = 4)
        assertEquals(4L, TabsState.mediaTab(feedActive, 4)) // before: mini plays
        assertEquals(4L, TabsState.mediaTab(playerActive, 4)) // 0.4.3 step 1: player tab selected
        assertEquals(4L, TabsState.mediaTab(playerActive, null)) // 0.4.3 step 2: mini player cleared
        assertEquals(3L, TabsState.mediaTab(feedActive, null)) // the old order's in-between state (the bug)
        assertEquals(3L, TabsState.mediaTab(feedActive, 99)) // a closed mini tab never counts
    }

    @Test fun fullscreenGuardAlsoEarlyInTheVideo() {
        val js = YouTubeTimestamps.INSTALL
        assertTrue(js.contains("keepT>1.5") && js.contains("guardPos>1.5&&v.currentTime<Math.min(2,guardPos-1)"))
        assertTrue(!js.contains("keepT>5") && !js.contains("guardPos>5"))
    }

    @Test fun silentPagesPauseAutostartingMedia() {
        val on = com.streamshell.core.media.MediaScripts.silent(true)
        assertTrue(on.endsWith("(true)") && on.contains("'playing'") && on.contains("window.__ssSilent"))
    }
}

class DiagnosticsCapacityTest {
    @Test fun logKeepsEnoughLines() {
        val log = com.streamshell.feature.browser.DiagnosticsLog({ 0L }) { _, _ -> }
        repeat(20500) { log.add("line $it") }
        assertEquals(20000, log.snapshot().size)
        assertTrue(log.snapshot().last().endsWith("line 20499") && log.snapshot().first().endsWith("line 500"))
    }
}
