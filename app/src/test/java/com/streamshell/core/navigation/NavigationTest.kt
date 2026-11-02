package com.streamshell.core.navigation

import com.streamshell.core.model.Overlay
import org.junit.Assert.assertEquals
import org.junit.Test

class NavReducerTest {
    private fun reduce(s: NavState, vararg a: NavAction) = a.fold(s) { acc, x -> NavReducer.reduce(acc, x) }

    @Test fun opensAndClosesPanels() {
        for (o in listOf(Overlay.Tabs, Overlay.Bookmarks, Overlay.History, Overlay.Settings)) {
            val s = reduce(NavState(), NavAction.Open(o))
            assertEquals(o, s.overlay)
            assertEquals(Overlay.None, reduce(s, NavAction.CloseOverlay).overlay)
        }
    }

    @Test fun privacyReturnsToWhereItWasOpened() {
        assertEquals(Overlay.Settings, reduce(NavState(), NavAction.Open(Overlay.Settings), NavAction.Open(Overlay.Privacy), NavAction.CloseOverlay).overlay)
        assertEquals(Overlay.None, reduce(NavState(), NavAction.Open(Overlay.Privacy), NavAction.CloseOverlay).overlay)
    }

    @Test fun routesRoundTrip() {
        Overlay.entries.filter { it.route != null }.forEach { assertEquals(it, Overlay.fromRoute(it.route)) }
        assertEquals(Overlay.None, Overlay.fromRoute("nonsense"))
    }
}

class BackPolicyTest {
    @Test fun strictPriority() {
        val panel = NavState(Overlay.Tabs)
        assertEquals(BackAction.ExitFullscreenVideo, BackPolicy.resolve(true, panel, true, true))
        assertEquals(BackAction.CloseOverlay, BackPolicy.resolve(false, panel, true, true))
        assertEquals(BackAction.PageBack, BackPolicy.resolve(false, NavState(), true, true))
        assertEquals(BackAction.ShowStartPage, BackPolicy.resolve(false, NavState(), false, true))
        assertEquals(BackAction.Finish, BackPolicy.resolve(false, NavState(), false, false))
    }
}
