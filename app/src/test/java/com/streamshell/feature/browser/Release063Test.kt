package com.streamshell.feature.browser

import com.streamshell.core.browser.YouTubeSite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.6.3: YouTube Music player bar and swipe, mini player without the page's own buttons. */
class Release063Test {
    @Test fun musicBarHasItsSectionsAndTrackButtons() {
        val k = PlayerBar.musicKeys()
        assertEquals(listOf("search", "home", "explore", "library", "previous", "next", "later", "speed", "repeat", "more"), k)
        assertEquals("previous", PlayerBar.firstAction(k))
        assertEquals("later", PlayerBar.firstAction(PlayerBar.keys(false))) // YouTube unchanged
        val first = PlayerBar.homeFirst(k, PlayerBar.MUSIC_START)
        assertEquals(3, first) // Library · Previous · Next · Later · Speed
        assertEquals("next", k[first + PlayerBar.VISIBLE / 2])
        assertEquals(5, PlayerBar.homeFirst(PlayerBar.keys(false))) // default start (Speed) still the same
    }

    @Test fun trackSwipe() {
        assertEquals(true, PlayerBar.trackSwipe(-120f, 10f)) // left → next
        assertEquals(false, PlayerBar.trackSwipe(120f, -20f)) // right → previous
        assertNull(PlayerBar.trackSwipe(-50f, 0f)) // too short
        assertNull(PlayerBar.trackSwipe(-120f, 80f)) // mostly vertical (scroll / minimize)
    }

    @Test fun miniPlayerHidesThePlayersOwnButtonsButKeepsTheVideo() {
        val css = YouTubeSite.MINI_CSS
        assertTrue(css.contains("html.ss-mini #movie_player [class*=\"player-controls\"]"))
        assertTrue(css.contains("html.ss-mini #movie_player [class*=\"ytp-ad\"]"))
        assertTrue(css.contains("html.ss-mini #movie_player .ytp-skip-ad"))
        assertTrue(css.contains("html.ss-mini video{visibility:visible!important}"))
        assertTrue(css.contains("ytmusic-player #song-image"))
        assertFalse(css.contains("display:none")) // hidden, not removed: the ad skipper can still press Skip
    }
}

/** 0.6.4: mini player controls next to the player; YouTube Music gestures and ads. */
class Release064Test {
    @Test fun miniHidesControlsBesideThePlayer() {
        val css = YouTubeSite.MINI_CSS
        assertTrue(css.contains("html.ss-mini #player-container-id ytm-custom-control"))
        assertTrue(css.contains("html.ss-mini .player-container [class*=\"player-control\"]"))
        assertTrue(css.contains("html.ss-mini #player-container-id [class*=\"time-display\"] *"))
        assertTrue(YouTubeSite.MINI_CONTROLS.none { it.contains("video") }) // the picture itself is never hidden
    }
}
