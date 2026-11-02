package com.streamshell.car

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkGateTest {
    @Test fun noSpeedNeedsConfirmationBeforeVideo() {
        val g = ParkGate()
        assertFalse(g.videoAllowed)
        assertTrue(g.needsConfirmation)
        assertTrue(g.confirmParked())
        assertTrue(g.videoAllowed)
    }

    @Test fun standingStillShowsVideoDrivingHidesIt() {
        val g = ParkGate()
        g.onSpeed(0f)
        assertTrue(g.videoAllowed)
        g.onSpeed(8f)
        assertFalse(g.videoAllowed) // about 29 km/h
        g.onSpeed(1.2f)
        assertFalse(g.videoAllowed) // slowing down: still counts as driving
        g.onSpeed(0.2f)
        assertTrue(g.videoAllowed)
    }

    @Test fun parkedCannotBeConfirmedWhileMovingAndMovementTakesItBack() {
        val g = ParkGate()
        g.onSpeed(5f)
        assertFalse(g.confirmParked())
        val h = ParkGate()
        h.confirmParked()
        assertTrue(h.videoAllowed)
        h.onSpeed(4f)
        assertFalse(h.videoAllowed)
        h.onSpeed(null)
        assertFalse(h.videoAllowed) // speed lost after moving: confirm again
        assertTrue(h.needsConfirmation)
    }

    @Test fun firstReadingBetweenThresholdsCountsAsDriving() {
        val g = ParkGate()
        g.onSpeed(1.0f)
        assertFalse(g.videoAllowed)
    }
}
