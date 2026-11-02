package com.streamshell.car

import com.streamshell.core.diag.DiagnosticsHub
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.6.1 architecture review of the car part: the picture rule stays in the car, transitions are reported. */
class CarArchitectureTest {
    private val src = File("src/main/java/com/streamshell")

    @Test fun parkGateIsUsedOnlyByTheCarPart() {
        assertTrue("sources not found from ${File(".").absolutePath}", src.isDirectory)
        val users = src.walk().filter { it.isFile && it.extension == "kt" && it.readText().contains("ParkGate") }
            .map { it.relativeTo(src).path.replace('\\', '/') }.toSet()
        assertTrue(users.isNotEmpty())
        assertTrue("ParkGate used outside car/: $users", users.all { it.startsWith("car/") })
    }

    @Test fun theGateNeverTouchesAudio() {
        val gateUsers = listOf("car/ParkGate.kt", "car/CarBrowser.kt", "car/CarScreens.kt", "car/StreamShellCarService.kt").map { File(src, it).readText() }
        // Pausing or muting is only done by the play button (togglePlay), never on a gate change.
        val browser = File(src, "car/CarBrowser.kt").readText()
        val refresh = browser.substringAfter("fun refresh()").substringBefore("\n    }\n")
        assertFalse(refresh.contains("pauseMedia") || refresh.contains("playMedia") || refresh.contains("silent(") || refresh.contains("muted"))
        val session = File(src, "car/StreamShellCarService.kt").readText().substringAfter("fun listenToSpeed").substringBefore("speedListener = l")
        assertFalse(session.contains("pauseMedia") || session.contains("silent"))
        assertTrue(gateUsers.none { it.contains("MediaBridge") })
    }

    @Test fun gateReportsChanges() {
        val g = ParkGate()
        assertNull(g.update(null)) // unknown → unknown, picture stays off
        val stop = g.update(0f)
        assertNotNull(stop)
        assertEquals("speed unknown → stopped, picture off → on", stop.toString())
        assertNull(g.update(0.2f))
        val move = g.update(5f)
        assertEquals("speed stopped → moving, picture on → off", move.toString())
        assertNull(g.confirm()) // refused while moving
        val lost = g.update(null)
        assertEquals("speed moving → unknown, picture off → off, waiting for \"Parked\"", lost.toString())
        val parked = g.confirm()
        assertTrue(parked != null && parked.videoAfter && !parked.videoBefore)
    }

    @Test fun hubWritesOnlyWhileDiagnosticsIsOn() {
        DiagnosticsHub.log.clear()
        DiagnosticsHub.enabled = false
        DiagnosticsHub.add("car", "x")
        assertTrue(DiagnosticsHub.log.snapshot().isEmpty())
        DiagnosticsHub.enabled = true
        DiagnosticsHub.add("car", "gate: y")
        assertTrue(DiagnosticsHub.log.snapshot().single().endsWith("car · gate: y"))
        DiagnosticsHub.enabled = false
        DiagnosticsHub.log.clear()
    }
}
