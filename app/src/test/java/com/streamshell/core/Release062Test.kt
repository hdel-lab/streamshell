package com.streamshell.core

import android.media.AudioAttributes
import android.media.AudioManager
import com.streamshell.car.CarBrowser
import com.streamshell.core.browser.MediaSessionScripts
import com.streamshell.core.browser.MusicSite
import com.streamshell.core.browser.SiteApp
import com.streamshell.core.diag.CrashReport
import com.streamshell.core.media.AudioYieldRules
import com.streamshell.core.media.AudioYieldRules.Do
import com.streamshell.core.media.MediaScripts
import com.streamshell.core.model.AppSettings
import com.streamshell.core.watch.Backup
import com.streamshell.core.watch.BackupContent
import com.streamshell.core.watch.WatchData
import com.streamshell.feature.settings.SettingsSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.6.2: crash report, calls and navigation voices, YouTube Music in the car and in app mode. */
class Release062Test {
    @Test fun crashReportHasTheStackWithoutAddresses() {
        val e = IllegalStateException("bad page https://music.youtube.com/watch?v=x&token=1", RuntimeException("inner"))
        val r = CrashReport.describe("main", e, 0, listOf("10:00:00.000 app · switched"))
        assertTrue(r.startsWith("crash ") && r.contains("on thread main"))
        assertTrue(r.contains("IllegalStateException: bad page <address>"))
        assertTrue(r.contains("Caused by: java.lang.RuntimeException: inner"))
        assertTrue(r.contains("at com.streamshell.core.Release062Test"))
        assertTrue(r.contains("last log lines before the crash:\n10:00:00.000 app · switched"))
        assertFalse(r.contains("token=1"))
    }

    @Test fun callsPauseAndResumeOnlyWhatTheyPaused() {
        assertEquals(Do.PAUSE, AudioYieldRules.callStarts(playing = true))
        assertEquals(Do.NOTHING, AudioYieldRules.callStarts(playing = false))
        assertEquals(Do.PLAY, AudioYieldRules.callEnds(pausedByCall = true))
        assertEquals(Do.NOTHING, AudioYieldRules.callEnds(pausedByCall = false))
        assertTrue(AudioYieldRules.isCallMode(AudioManager.MODE_IN_CALL))
        assertTrue(AudioYieldRules.isCallMode(AudioManager.MODE_IN_COMMUNICATION))
        assertTrue(AudioYieldRules.isCallMode(AudioManager.MODE_RINGTONE))
        assertFalse(AudioYieldRules.isCallMode(AudioManager.MODE_NORMAL))
    }

    @Test fun navigationVoicesDuckAndRecover() {
        assertEquals(Do.DUCK, AudioYieldRules.voiceStarts(playing = true, inCall = false))
        assertEquals(Do.NOTHING, AudioYieldRules.voiceStarts(playing = true, inCall = true))
        assertEquals(Do.NOTHING, AudioYieldRules.voiceStarts(playing = false, inCall = false))
        assertEquals(Do.UNDUCK, AudioYieldRules.voiceEnds(ducked = true, wasPlaying = true, playingNow = true))
        assertEquals(Do.UNDUCK_AND_PLAY, AudioYieldRules.voiceEnds(ducked = true, wasPlaying = true, playingNow = false))
        assertEquals(Do.NOTHING, AudioYieldRules.voiceEnds(ducked = false, wasPlaying = true, playingNow = false))
        assertTrue(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE in AudioYieldRules.VOICE_USAGES)
        assertFalse(AudioAttributes.USAGE_MEDIA in AudioYieldRules.VOICE_USAGES) // StreamShell's own (and other media) never count
        assertTrue(MediaScripts.duck(true).contains("0.25") && MediaScripts.duck(false).contains("__ssVol=null"))
    }

    @Test fun mediaSessionScripts() {
        assertTrue(MediaSessionScripts.origins.containsAll(setOf("https://m.youtube.com", "https://music.youtube.com")))
        assertTrue(MediaSessionScripts.action("nexttrack").contains("('nexttrack')") && MediaSessionScripts.action("nexttrack").contains(".next-button"))
        assertTrue(MediaSessionScripts.action("anything").contains("('previoustrack')"))
        assertEquals("Song" to "Band", MediaSessionScripts.parseNowPlaying("\"Song\\u0001Band\""))
        assertEquals("Song" to "", MediaSessionScripts.parseNowPlaying("\"Song\\u0001\""))
        assertNull(MediaSessionScripts.parseNowPlaying("\"\""))
        assertNull(MediaSessionScripts.parseNowPlaying(null))
        assertNull(MediaSessionScripts.parseNowPlaying("null"))
    }

    @Test fun musicAppModeHidesOnlyItsBottomTabs() {
        assertTrue(MusicSite.CSS.contains("ytmusic-pivot-bar-renderer{display:none"))
        assertFalse(MusicSite.CSS.contains("ytmusic-nav-bar")) // its top bar (search, account) stays
        assertTrue(MusicSite.isMusic("music.youtube.com") && !MusicSite.isMusic("m.youtube.com"))
        assertTrue(MusicSite.INSTALL.contains(MusicSite.STYLE_ID))
    }

    @Test fun carSourceAndSettings() {
        assertEquals("https://music.youtube.com/", CarBrowser.homeOf(SiteApp.YOUTUBE_MUSIC))
        assertEquals("https://m.youtube.com/", CarBrowser.homeOf(SiteApp.YOUTUBE))
        val d = AppSettings()
        assertEquals("youtube", d.carSource)
        assertTrue(d.yieldAudio)
        val r = Backup.decode(Backup.encode(BackupContent(emptyList(), emptyList(), WatchData(), d.copy(carSource = "music", yieldAudio = false)), now = 1))
        assertEquals("music", r.settings.carSource)
        assertFalse(r.settings.yieldAudio)
        assertTrue(SettingsSearch.matches("radar", SettingsSearch.PLAYBACK) && SettingsSearch.matches("music", SettingsSearch.PLAYBACK))
    }
}
