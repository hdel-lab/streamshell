package com.streamshell.core.browser

import com.streamshell.core.model.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteAppsTest {
    @Test fun hosts() {
        for (h in listOf("m.youtube.com", "www.youtube.com", "youtube.com")) assertEquals(h, SiteApp.YOUTUBE, SiteApp.of(h))
        assertEquals(SiteApp.YOUTUBE_MUSIC, SiteApp.of("music.youtube.com"))
        for (h in listOf("youtube.com.evil.test", "notyoutube.com", "vimeo.com", null)) assertNull("$h", SiteApp.of(h))
        assertTrue(YouTubeSite.isYouTube("m.youtube.com"))
        assertFalse(YouTubeSite.isYouTube("music.youtube.com")) // Music keeps its own bars (search lives there)
    }

    @Test fun youtubeDestinations() {
        val yt = SiteApp.YOUTUBE
        assertEquals("home", yt.destinationOf("https://m.youtube.com/?app=m")?.id)
        assertEquals("shorts", yt.destinationOf("https://m.youtube.com/shorts/abc")?.id)
        assertEquals("subscriptions", yt.destinationOf("https://m.youtube.com/feed/subscriptions")?.id)
        assertEquals("you", yt.destinationOf("https://m.youtube.com/feed/you")?.id)
        assertNull(yt.destinationOf("https://m.youtube.com/watch?v=x"))
        assertNull(yt.destinationOf("https://music.youtube.com/"))
    }

    @Test fun musicDestinations() {
        val m = SiteApp.YOUTUBE_MUSIC
        assertEquals("home", m.destinationOf("https://music.youtube.com/")?.id)
        assertEquals("explore", m.destinationOf("https://music.youtube.com/explore")?.id)
        assertEquals("library", m.destinationOf("https://music.youtube.com/library/playlists")?.id)
        assertNull(m.destinationOf("https://m.youtube.com/"))
    }

    @Test fun everyDestinationIsAnInAppPageThatMapsToItself() {
        for (app in SiteApp.entries) {
            for (d in app.destinations) {
                assertEquals(d, app.destinationOf(d.url))
                assertTrue(NavigationPolicy.decide(d.url, false) is NavigationDecision.InApp)
            }
        }
    }

    @Test fun siteSearch() {
        assertEquals("https://m.youtube.com/results?search_query=lofi+beats", SiteApp.YOUTUBE.searchUrlFor(" lofi beats "))
        assertEquals("https://music.youtube.com/search?q=a%26b", SiteApp.YOUTUBE_MUSIC.searchUrlFor("a&b"))
        assertEquals(SearchEngine.YOUTUBE, YouTubeSite.searchEngineFor(true, SearchEngine.GOOGLE))
        assertEquals(SearchEngine.DUCKDUCKGO, YouTubeSite.searchEngineFor(false, SearchEngine.DUCKDUCKGO))
    }

    @Test fun styleOnlyHidesYouTubesOwnBarsAndNeverThePlayerOrAds() {
        val css = YouTubeSite.CSS
        assertTrue(css.contains("ytm-mobile-topbar-renderer") && css.contains("ytm-pivot-bar-renderer"))
        for (bad in listOf("ad-", "ytp-", "#movie_player", "html5-video", "ad_")) assertFalse(bad, css.contains(bad))
        // Never the <video> element itself (list items such as ytm-video-with-context-renderer are fine).
        assertFalse(Regex("(^|[\\s,>}])video([\\s,{.:#\\[]|$)").containsMatchIn(css))
        val js = YouTubeSite.INSTALL
        assertTrue(js.contains(YouTubeSite.STYLE_ID))
        // The fixer must leave media, frames and scripts alone and must not read or send data.
        assertTrue(js.contains("t==='VIDEO'||t==='IFRAME'"))
        for (bad in listOf("cookie", "localStorage", "fetch(", "XMLHttpRequest", "innerHTML", "eval(", ".pause(", ".play(", "currentTime", "skip")) assertFalse(bad, js.contains(bad))
        assertTrue(js.contains("window.__streamshellCompact")) // runs once per document
    }
}

class YouTubeAdsTest {
    @Test fun skipperActsOnlyInTheAdStateAndRestoresSound() {
        val js = YouTubeAds.INSTALL
        assertTrue(js.contains("classList.contains('ad-showing')"))
        assertTrue(js.contains(".ytp-ad-skip-button") && js.contains(".ytp-skip-ad-button"))
        assertTrue(js.contains("v.muted=true") && js.contains("mp.unMute()")) // sound comes back after the ad, also in YouTube's player
        assertTrue(js.contains("if(window.__streamshellAds)return")) // installed once per document
        for (bad in listOf("cookie", "fetch(", "XMLHttpRequest", "eval(", "ytInitialPlayerResponse")) assertFalse(bad, js.contains(bad))
        // localStorage only for StreamShell's own two flags, through one helper.
        assertEquals(3, Regex("localStorage\\.").findAll(js).count())
        assertTrue(js.contains(YouTubeAds.MUTED_KEY) && js.contains(YouTubeAds.REPAIR_KEY))
    }

    @Test fun actionsAreTiedToTheAdMediaAndSkipUsesLayoutBoxes() {
        val js = YouTubeAds.INSTALL
        assertTrue(js.contains("var same=src===adSrc")) // nothing is done to media that is not the ad that started the break
        assertTrue(js.contains("now-changedAt<1000")) // wait 1 s when the <video> switched while the marker lingers
        assertTrue(js.contains("d<=${YouTubeAds.MAX_AD_SECONDS}")) // the real video (longer) is never sped up or thrown to its end
        assertTrue(js.contains("getClientRects().length>0")) // fixed-position Skip buttons count as on screen
        assertFalse(js.contains("offsetParent"))
        assertTrue(js.contains("v.playbackRate=prevRate")) // speed restored
    }

    @Test fun feedAdCardsAreHiddenButNotVideos() {
        assertTrue(YouTubeAds.CSS.contains("ad-slot-renderer") && YouTubeAds.CSS.contains("ytm-promoted-sparkles-web-renderer"))
        assertFalse(YouTubeAds.CSS.contains("video{") || YouTubeAds.CSS.contains("ytm-video-with-context-renderer,"))
    }
}

class YouTubeAutoplayTest {
    @Test fun startsEachVideoOnceOnlyWhenVisibleAndResumesAfterAds() {
        val js = YouTubeAutoplay.INSTALL
        assertTrue(js.contains("if(started[id])return")) // a video you paused stays paused
        assertTrue(js.contains("document.visibilityState!=='visible'")) // background tabs never start by themselves
        assertTrue(js.contains("if(v.paused&&!v.ended)go(v)"))
        assertTrue(js.contains("(tries[id]||0)<15")) // start-up pauses retried for up to 6 s
        assertTrue(js.contains("v.currentTime-from[id]>=0.8")) // only a pause after real playback counts as yours
        assertTrue(js.contains("getVideoPlaybackQuality") && js.contains("resumeAt=now+300")) // black picture: one pause + play
        assertTrue(js.contains("if(!kicked&&now>=checkAt&&now-seekAt>3000&&v.readyState>=3){kicked=true")) // once per ad break, not right after your jump, not while buffering
        for (bad in listOf("cookie", "localStorage", "fetch(", "XMLHttpRequest", "eval(", "playbackRate")) assertFalse(bad, js.contains(bad))
    }
}

class YouTubeRouterTest {
    @Test fun routesOnlyWatchLinksAndOnlyWhenEnabled() {
        val js = YouTubeRouter.INSTALL
        org.junit.Assert.assertTrue(js.contains("__streamshellRouteVideos"))
        org.junit.Assert.assertTrue(js.contains("preventDefault"))
        org.junit.Assert.assertTrue(js.contains(YouTubeRouter.CHANNEL))
        org.junit.Assert.assertTrue(YouTubeRouter.FLAG.contains("__streamshellRouteVideos=true"))
    }
}

class YouTubeAdsReloadTest {
    @Test fun reloadsThroughThePlayerAndAsksForARealTap() {
        val js = YouTubeAds.INSTALL
        org.junit.Assert.assertTrue(js.contains("mp.loadVideoById(id,Math.floor(start||0))") && js.contains("var start=lastPos[id]")) // last position, else the URL timestamp
        org.junit.Assert.assertTrue(js.contains("if(r.n>=${YouTubeAds.MAX_RELOADS})return false"))
        org.junit.Assert.assertTrue(js.contains("'${YouTubeAds.TAP}|'"))
        org.junit.Assert.assertTrue(js.contains("taps>=3"))
    }
}

class MediaNotificationTest {
    @Test fun thumbnailOnlyForYouTubeVideosAndOnlyFromYouTubesImageHost() {
        com.streamshell.core.media.MediaBridge.update("t", com.streamshell.core.media.MediaState.PLAYING, "https://m.youtube.com/watch?v=dQw4w9WgXcQ")
        org.junit.Assert.assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", com.streamshell.core.media.MediaBridge.thumbnailUrl())
        com.streamshell.core.media.MediaBridge.update("t", com.streamshell.core.media.MediaState.PLAYING, "https://vimeo.com/123")
        org.junit.Assert.assertNull(com.streamshell.core.media.MediaBridge.thumbnailUrl())
        org.junit.Assert.assertTrue(com.streamshell.core.media.Thumbnails.allowed("https://i.ytimg.com/vi/x/hqdefault.jpg"))
        org.junit.Assert.assertFalse(com.streamshell.core.media.Thumbnails.allowed("http://i.ytimg.com/vi/x/hqdefault.jpg"))
        org.junit.Assert.assertFalse(com.streamshell.core.media.Thumbnails.allowed("https://evil.example/i.ytimg.com.jpg"))
    }

    @Test fun seekScriptStaysInsideTheMedia() {
        val js = com.streamshell.core.media.MediaScripts.seekBy(-10)
        org.junit.Assert.assertTrue(js.endsWith("(-10)"))
        org.junit.Assert.assertTrue(js.contains("Math.max(0,Math.min(v.duration-0.5,v.currentTime+d))"))
        // On YouTube the jump goes through YouTube's own player, before any direct <video> write.
        org.junit.Assert.assertTrue(js.indexOf("mp.seekTo(") in 0 until js.indexOf("v.currentTime="))
        org.junit.Assert.assertEquals(10, com.streamshell.core.media.PlaybackService.SEEK_SECONDS)
    }
}

class PageColorsTest {
    @Test fun parsesOpaqueColoursOnly() {
        org.junit.Assert.assertEquals(0xFFFFFFFF.toInt(), PageColors.parse("\"rgb(255, 255, 255)\""))
        org.junit.Assert.assertEquals(0xFF0F0F0F.toInt(), PageColors.parse("\"rgba(15, 15, 15, 1)\""))
        org.junit.Assert.assertNull(PageColors.parse("\"rgba(0, 0, 0, 0)\""))
        org.junit.Assert.assertNull(PageColors.parse("\"\""))
    }
}

class YouTubeTimestampsTest {
    @Test fun parsesYouTubeTimeParameters() {
        org.junit.Assert.assertEquals(135, YouTubeTimestamps.seconds("135"))
        org.junit.Assert.assertEquals(135, YouTubeTimestamps.seconds("135s"))
        org.junit.Assert.assertEquals(125, YouTubeTimestamps.seconds("2m5s"))
        org.junit.Assert.assertEquals(3723, YouTubeTimestamps.seconds("1h2m3s"))
        org.junit.Assert.assertNull(YouTubeTimestamps.seconds("abc"))
        org.junit.Assert.assertNull(YouTubeTimestamps.seconds(""))
        org.junit.Assert.assertTrue(YouTubeTimestamps.seekScript(90).endsWith("(90)"))
        // The router never sends a link to the video already on the page (timestamps stay in place).
        org.junit.Assert.assertTrue(YouTubeRouter.INSTALL.contains("searchParams.get('v')===u.searchParams.get('v'))return"))
    }
}

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34], application = android.app.Application::class)
class ChaptersTest {
    @Test fun parsesSortsAndFindsTheCurrentChapter() {
        val list = Chapters.parse("""[{"t":45,"l":"Hits"},{"t":0,"l":"Intro"},{"t":20,"l":"Night"},{"t":20,"l":"dup"},{"x":1}]""")
        org.junit.Assert.assertEquals(listOf(0, 20, 45), list.map { it.start })
        org.junit.Assert.assertEquals(1, Chapters.current(list, 30.0))
        org.junit.Assert.assertEquals(-1, Chapters.current(listOf(Chapter(10, "a")), 3.0))
        org.junit.Assert.assertTrue(Chapters.parse("nope").isEmpty())
    }
}
