package com.streamshell.core.settings

import com.streamshell.core.model.AppSettings
import com.streamshell.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSanitizerTest {
    @Test fun unknownSponsorCategoriesAreDropped() {
        assertEquals(setOf("sponsor"), SettingsSanitizer.sanitize(AppSettings(sponsorCategories = setOf("sponsor", "evil"))).sponsorCategories)
    }

    @Test fun exemptSitesAreNormalised() {
        val s = SettingsSanitizer.sanitize(AppSettings(filterExemptSites = setOf("WWW.Example.com", "bad site", "https://news.example/x")))
        assertEquals(setOf("www.example.com", "news.example"), s.filterExemptSites)
    }

    @Test fun badIntervalFallsBackToDefault() {
        assertEquals(24, SettingsSanitizer.sanitize(AppSettings(updateIntervalHours = 5)).updateIntervalHours)
        assertEquals(12, SettingsSanitizer.sanitize(AppSettings(updateIntervalHours = 12)).updateIntervalHours)
        assertEquals(0, SettingsSanitizer.sanitize(AppSettings(updateIntervalHours = 0)).updateIntervalHours)
    }

    @Test fun customDomainsAreNormalizedAndInvalidOnesDropped() {
        val s = SettingsSanitizer.sanitize(
            AppSettings(customBlockedDomains = setOf("Ads.Example.com", "https://x.test/path", "bad domain", "1.2.3.4", "ads.example.com"))
        )
        assertTrue("ads.example.com" in s.customBlockedDomains)
        assertEquals(1, s.customBlockedDomains.count { it == "ads.example.com" })
        assertTrue(s.customBlockedDomains.none { it.contains(' ') || it == "1.2.3.4" })
    }

    @Test fun defaultsAreSafe() {
        val d = AppSettings()
        assertEquals(ThemeMode.DARK, d.themeMode)
        assertTrue(d.filteringEnabled)
        assertTrue(d.saveHistory && d.backgroundPlayback && d.autoPip)
    }
}
