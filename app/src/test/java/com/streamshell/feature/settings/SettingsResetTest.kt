package com.streamshell.feature.settings

import com.streamshell.core.model.AppSettings
import com.streamshell.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsResetTest {
    @Test fun appSettingsBackToDefaultsPrivacyKept() {
        val cur = AppSettings(
            themeMode = ThemeMode.LIGHT, autoplay = false, playbackSpeed = 150, defaultInstance = "YOUTUBE",
            siteBars = mapOf("YOUTUBE" to listOf("home")),
            filteringEnabled = false, customBlockedDomains = setOf("ads.example"), filterExemptSites = setOf("site.example"), updateIntervalHours = 72
        )
        val r = SettingsReset.apply(cur)
        assertEquals(AppSettings().themeMode, r.themeMode)
        assertEquals(true, r.autoplay)
        assertEquals(100, r.playbackSpeed)
        assertEquals("", r.defaultInstance)
        assertEquals(emptyMap<String, List<String>>(), r.siteBars)
        assertEquals(false, r.filteringEnabled)
        assertEquals(setOf("ads.example"), r.customBlockedDomains)
        assertEquals(setOf("site.example"), r.filterExemptSites)
        assertEquals(72, r.updateIntervalHours)
    }
}
