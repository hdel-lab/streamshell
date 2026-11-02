package com.streamshell.core.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowLayoutPolicyTest {
    // Representative window sizes in dp. Not device specs: the policy only sees dp numbers.
    // Cover-display-like (narrow phone) and inner-display-like (large, nearly square) windows.
    private val coverW = 360
    private val coverH = 800
    private val innerW = 880
    private val innerH = 800

    @Test fun narrowWindowUsesBottomBarAndFullScreenAccount() {
        val l = WindowLayoutPolicy.compute(coverW, coverH, null)
        assertEquals(WidthClass.Compact, l.widthClass)
        assertEquals(NavigationStyle.BottomBar, l.navigation)
        assertEquals(AccountPresentation.FullScreen, l.account)
        assertNull(l.hinge)
    }

    @Test fun wideWindowUsesRailAndSidePanel() {
        val l = WindowLayoutPolicy.compute(innerW, innerH, null)
        assertEquals(WidthClass.Expanded, l.widthClass)
        assertEquals(NavigationStyle.Rail, l.navigation)
        assertEquals(AccountPresentation.SidePanel, l.account)
    }

    @Test fun mediumWindowUsesRailButFullScreenAccount() {
        val l = WindowLayoutPolicy.compute(700, 900, null)
        assertEquals(WidthClass.Medium, l.widthClass)
        assertEquals(NavigationStyle.Rail, l.navigation)
        assertEquals(AccountPresentation.FullScreen, l.account)
    }

    @Test fun breakpointsAreExact() {
        assertEquals(WidthClass.Compact, WindowLayoutPolicy.widthClass(599))
        assertEquals(WidthClass.Medium, WindowLayoutPolicy.widthClass(600))
        assertEquals(WidthClass.Medium, WindowLayoutPolicy.widthClass(839))
        assertEquals(WidthClass.Expanded, WindowLayoutPolicy.widthClass(840))
        assertEquals(HeightClass.Compact, WindowLayoutPolicy.heightClass(479))
        assertEquals(HeightClass.Expanded, WindowLayoutPolicy.heightClass(900))
    }

    @Test fun splitScreenShrinksToCompactEvenOnLargeDisplay() {
        // Same physical display, but the app only gets a 400dp-wide split-screen window.
        assertEquals(NavigationStyle.BottomBar, WindowLayoutPolicy.compute(400, 800, null).navigation)
    }

    @Test fun landscapePhoneIsMediumWidthWithCompactHeight() {
        val l = WindowLayoutPolicy.compute(780, 380, null)
        assertEquals(WidthClass.Medium, l.widthClass)
        assertEquals(HeightClass.Compact, l.heightClass)
    }

    private fun fold(state: FoldState, o: FoldOrientation, separating: Boolean, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) = FoldInfo(state, o, separating, l, t, r, b)

    @Test fun tabletopWhenHalfOpenHorizontalSeparating() {
        val l = WindowLayoutPolicy.compute(innerW, innerH, fold(FoldState.HalfOpened, FoldOrientation.Horizontal, true, 0, 1000, 2000, 1000))
        assertEquals(HingeSplit.Tabletop(1000, 1000), l.hinge)
    }

    @Test fun bookWhenHalfOpenVerticalSeparating() {
        val l = WindowLayoutPolicy.compute(innerW, innerH, fold(FoldState.HalfOpened, FoldOrientation.Vertical, true, 1100, 0, 1100, 2000))
        assertEquals(HingeSplit.Book(1100, 1100), l.hinge)
    }

    @Test fun flatFoldDoesNotSplit() {
        assertNull(WindowLayoutPolicy.compute(innerW, innerH, fold(FoldState.Flat, FoldOrientation.Vertical, true, 1100, 0, 1100, 2000)).hinge)
    }

    @Test fun nonSeparatingFoldDoesNotSplit() {
        assertNull(WindowLayoutPolicy.hingeSplit(fold(FoldState.HalfOpened, FoldOrientation.Horizontal, false, 0, 1000, 2000, 1000)))
    }

    @Test fun degenerateBoundsDoNotSplit() {
        assertNull(WindowLayoutPolicy.hingeSplit(fold(FoldState.HalfOpened, FoldOrientation.Horizontal, true, 0, 0, 2000, 0)))
        assertNull(WindowLayoutPolicy.hingeSplit(fold(FoldState.HalfOpened, FoldOrientation.Vertical, true, 0, 0, 0, 2000)))
    }

    @Test fun deviceWithoutFoldFeatureStillWorks() {
        assertTrue(WindowLayoutPolicy.compute(innerW, innerH, null).hinge == null)
    }
}
