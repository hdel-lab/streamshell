package com.streamshell.core

import com.streamshell.car.CarDisplay
import com.streamshell.core.browser.PlayerOpen
import com.streamshell.core.model.AppSettings
import com.streamshell.core.watch.Backup
import com.streamshell.core.watch.BackupContent
import com.streamshell.core.watch.WatchData
import com.streamshell.feature.settings.SettingsSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.6.0: prepared-player race (log 2026-11-01 16:11) and the Android Auto layout rules. */
class Release060Test {
    // ------------------------------------------------------------ R1–R3 opening a video in the player tab

    @Test fun inPageOnlyWhenThePlayerPageHasLoaded() {
        assertEquals(PlayerOpen.Mode.IN_PAGE, PlayerOpen.mode(hasPlayerPage = true, pageLoading = false))
        assertEquals(PlayerOpen.Mode.FULL_LOAD_STILL_LOADING, PlayerOpen.mode(hasPlayerPage = true, pageLoading = true))
        assertEquals(PlayerOpen.Mode.FULL_LOAD, PlayerOpen.mode(hasPlayerPage = false, pageLoading = false))
        assertEquals(PlayerOpen.Mode.FULL_LOAD, PlayerOpen.mode(hasPlayerPage = false, pageLoading = true))
    }

    @Test fun checkParsesIdAndState() {
        assertEquals(PlayerOpen.Check("abcdefghijk", 1), PlayerOpen.parse("\"abcdefghijk|1\""))
        assertEquals(PlayerOpen.Check("", -9), PlayerOpen.parse("\"|-9\""))
        assertEquals(PlayerOpen.Check("", PlayerOpen.UNKNOWN), PlayerOpen.parse(null))
        assertEquals(PlayerOpen.Check("abcdefghijk", PlayerOpen.UNKNOWN), PlayerOpen.parse("abcdefghijk|x"))
    }

    @Test fun switchNeedsNewIdAndAStartedPlayerWithAutoplay() {
        val id = "abcdefghijk"
        // The 16:11 case: new ID but the player never started
        assertFalse(PlayerOpen.switched(PlayerOpen.Check(id, -1), id, autoplay = true))
        assertFalse(PlayerOpen.switched(PlayerOpen.Check(id, 5), id, autoplay = true))
        assertFalse(PlayerOpen.switched(PlayerOpen.Check(id, PlayerOpen.UNKNOWN), id, autoplay = true))
        assertFalse(PlayerOpen.switched(PlayerOpen.Check("zzzzzzzzzzz", 1), id, autoplay = true))
        assertTrue(PlayerOpen.switched(PlayerOpen.Check(id, 1), id, autoplay = true))
        assertTrue(PlayerOpen.switched(PlayerOpen.Check(id, 3), id, autoplay = true))
        assertTrue(PlayerOpen.switched(PlayerOpen.Check(id, 2), id, autoplay = true))
        // Autoplay off: a cued video is what the user asked for
        assertTrue(PlayerOpen.switched(PlayerOpen.Check(id, 5), id, autoplay = false))
        assertEquals("not started", PlayerOpen.stateName(-1))
        assertEquals("buffering", PlayerOpen.stateName(3))
    }

    // ------------------------------------------------------------ C1 density for the car screen

    @Test fun wideCarScreenGetsAbout480dpOfHeight() {
        // 1920×720 (wide head units such as the DS7 Crossback's): 240 dpi → 1280×480 dp
        val dpi = CarDisplay.optimalDpi(1920, 720, 320)
        assertEquals(240, dpi)
        assertEquals(1280, CarDisplay.dp(1920, dpi))
        assertEquals(480, CarDisplay.dp(720, dpi))
        // The car's own figure does not matter for a valid surface
        assertEquals(240, CarDisplay.optimalDpi(1920, 720, 160))
    }

    @Test fun commonCarScreens() {
        assertEquals(160, CarDisplay.optimalDpi(800, 480, 160))
        assertEquals(240, CarDisplay.optimalDpi(1280, 720, 213))
        assertEquals(320, CarDisplay.optimalDpi(1920, 1080, 320)) // 360 capped at 320 → 1080×… still ≥ 640 dp wide
        assertTrue(CarDisplay.dp(1920, CarDisplay.optimalDpi(1920, 1080, 320)) >= CarDisplay.MIN_WIDTH_DP)
    }

    @Test fun narrowScreensKeepAtLeast640dpWidth() {
        // 1024×768 (nearly square): height rule says 256 dpi (400 dp wide), the width rule limits to 256 → 640 dp
        val dpi = CarDisplay.optimalDpi(1024, 768, 200)
        assertTrue(CarDisplay.dp(1024, dpi) >= CarDisplay.MIN_WIDTH_DP)
        // Very small surfaces stay at the lower limit
        assertEquals(CarDisplay.MIN_DPI, CarDisplay.optimalDpi(400, 240, 160))
        // An invalid surface falls back to the car's figure (within limits)
        assertEquals(213, CarDisplay.optimalDpi(0, 0, 213))
        assertEquals(CarDisplay.MAX_DPI, CarDisplay.optimalDpi(0, 720, 480))
    }

    // ------------------------------------------------------------ C3 play bar

    @Test fun playBarTimeAndProgress() {
        assertEquals("0:05", CarDisplay.clock(5.9))
        assertEquals("12:00", CarDisplay.clock(720.0))
        assertEquals("1:02:03", CarDisplay.clock(3723.0))
        assertEquals("", CarDisplay.clock(Double.NaN))
        assertEquals("", CarDisplay.clock(-1.0))
        assertEquals(500, CarDisplay.progress(30.0, 60.0))
        assertEquals(0, CarDisplay.progress(1.0, 0.0))
        assertEquals(1000, CarDisplay.progress(90.0, 60.0))
    }

    @Test fun scrollScriptDirection() {
        assertTrue(CarDisplay.scrollScript(-1).contains("top:-1*"))
        assertTrue(CarDisplay.scrollScript(1).contains("top:1*"))
        assertTrue(CarDisplay.scrollScript(7).contains("top:1*"))
    }

    // ------------------------------------------------------------ C4 rail side setting

    @Test fun railSideIsBackedUpAndSearchable() {
        assertTrue(AppSettings().carRailLeft)
        val r = Backup.decode(Backup.encode(BackupContent(emptyList(), emptyList(), WatchData(), AppSettings(carRailLeft = false)), now = 1))
        assertFalse(r.settings.carRailLeft)
        assertTrue(SettingsSearch.matches("android auto", SettingsSearch.PLAYBACK))
        assertTrue(SettingsSearch.matches("rail", SettingsSearch.PLAYBACK))
    }
}
