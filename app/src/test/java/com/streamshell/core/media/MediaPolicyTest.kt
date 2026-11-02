package com.streamshell.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPolicyTest {
    @Test fun probeResultParsing() {
        assertEquals(MediaState.PLAYING, MediaScripts.parse("\"playing\""))
        assertEquals(MediaState.PAUSED, MediaScripts.parse("\"paused\""))
        assertEquals(MediaState.NONE, MediaScripts.parse("\"none\""))
        assertEquals(MediaState.NONE, MediaScripts.parse(null))
        assertEquals(MediaState.NONE, MediaScripts.parse("null"))
        assertEquals(MediaState.NONE, MediaScripts.parse("{\"evil\":1}"))
    }

    @Test fun probeWithVideoRect() {
        val p = MediaScripts.parseProbe("\"playing|10|20|360|202.5|3\"")
        assertEquals(MediaState.PLAYING, p.state)
        assertEquals(VideoRect(30f, 60f, 1080f, 607.5f), p.video)
        assertEquals(null, MediaScripts.parseProbe("\"paused|x|1|2|3|1\"").video)
        assertEquals(MediaState.PAUSED, MediaScripts.parseProbe("\"paused|x|1|2|3|1\"").state)
        assertEquals(null, MediaScripts.parseProbe("\"playing|0|0|0|0|2\"").video)
        assertEquals(null, MediaScripts.parseProbe("\"playing|0|0|10|10|99\"").video) // absurd pixel ratio
    }

    @Test fun pipCropFillsTheWindowWithTheVideo() {
        // 1080x2000 page, video 1080x608 at the top; PiP window 480x270.
        val c = MediaPolicy.pipCrop(1080, 2000, VideoRect(0f, 0f, 1080f, 608f), 480, 270)!!
        assertEquals(480f / 1080f, c.scale, 0.001f)
        assertEquals(0f, c.translateX, 0.5f)
        // Video below a header: shifted up by its top offset.
        val d = MediaPolicy.pipCrop(1080, 2000, VideoRect(0f, 150f, 1080f, 608f), 480, 270)!!
        assertEquals(-150f * d.scale, d.translateY, 1f)
        // Tiny or missing video: no crop, the whole page is shown.
        assertEquals(null, MediaPolicy.pipCrop(1080, 2000, VideoRect(0f, 0f, 100f, 60f), 480, 270))
        assertEquals(null, MediaPolicy.pipCrop(1080, 2000, null, 480, 270))
        // Partly scrolled out of view: only the visible part counts.
        val e = MediaPolicy.pipCrop(1080, 2000, VideoRect(0f, -300f, 1080f, 608f), 480, 270)!!
        assertEquals(minOf(480f / 1080f, 270f / 308f), e.scale, 0.001f)
    }

    @Test fun scriptsOnlyUseStandardMediaCalls() {
        for (s in listOf(MediaScripts.PROBE, MediaScripts.PLAY, MediaScripts.PAUSE)) {
            assertTrue(s.contains("querySelectorAll('video,audio')"))
            for (bad in listOf("innerHTML", "fetch(", "XMLHttpRequest", "cookie", "localStorage", "src=", "currentTime=", "playbackRate", "eval(")) assertFalse("$bad in $s", s.contains(bad))
        }
    }

    @Test fun leavingTheApp() {
        assertEquals(LeaveAction.KEEP_FOR_PIP, MediaPolicy.onLeave(inPip = true, backgroundEnabled = false, media = MediaState.PLAYING))
        assertEquals(LeaveAction.CONTINUE_IN_BACKGROUND, MediaPolicy.onLeave(false, true, MediaState.PLAYING))
        assertEquals(LeaveAction.PAUSE, MediaPolicy.onLeave(false, true, MediaState.PAUSED))
        assertEquals(LeaveAction.PAUSE, MediaPolicy.onLeave(false, false, MediaState.PLAYING))
        assertEquals(LeaveAction.PAUSE, MediaPolicy.onLeave(false, true, MediaState.NONE))
    }

    @Test fun autoPip() {
        assertTrue(MediaPolicy.shouldAutoPip(true, true, false, MediaState.PLAYING))
        assertTrue(MediaPolicy.shouldAutoPip(true, true, true, MediaState.PAUSED))
        assertFalse(MediaPolicy.shouldAutoPip(true, true, false, MediaState.PAUSED))
        assertFalse(MediaPolicy.shouldAutoPip(false, true, true, MediaState.PLAYING))
        assertFalse(MediaPolicy.shouldAutoPip(true, false, true, MediaState.PLAYING))
    }

    @Test fun playbackServiceLifecycle() {
        assertTrue(MediaPolicy.wantsPlaybackService(true, MediaState.PLAYING, false))
        assertTrue(MediaPolicy.wantsPlaybackService(true, MediaState.PAUSED, true)) // keep controls to resume
        assertFalse(MediaPolicy.wantsPlaybackService(true, MediaState.PAUSED, false)) // never start for paused media
        assertFalse(MediaPolicy.wantsPlaybackService(true, MediaState.NONE, true))
        assertFalse(MediaPolicy.wantsPlaybackService(false, MediaState.PLAYING, true))
    }

    @Test fun pipAspectIsClamped() {
        assertEquals(16 to 9, MediaPolicy.pipAspect(0, 0))
        assertEquals(1920 to 1080, MediaPolicy.pipAspect(1920, 1080))
        assertEquals(239 to 100, MediaPolicy.pipAspect(3000, 500))
        assertEquals(100 to 239, MediaPolicy.pipAspect(300, 3000))
    }

    @Test fun bridgeNotifiesOnlyOnChange() {
        var n = 0
        MediaBridge.listener = { n++ }
        MediaBridge.update("A", MediaState.PLAYING)
        MediaBridge.update("A", MediaState.PLAYING)
        MediaBridge.update("A", MediaState.PAUSED)
        MediaBridge.listener = null
        assertEquals(2, n)
    }
}
