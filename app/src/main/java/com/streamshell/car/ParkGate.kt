package com.streamshell.car

/**
 * Video on the car screen only while the car stands still; the sound always plays. Pure and unit-tested.
 *
 * The speed comes from the car (Android Auto's CarInfo). Hysteresis: moving above [MOVING_MPS] (about 7 km/h), stopped
 * below [STOPPED_MPS]; in between the last state stays. When the car does not report its speed (permission refused or
 * not supported) the driver confirms "Parked" once; any reported movement takes that back.
 *
 * Scope (0.6.1 architecture review): used only by the car session ([CarSession] / [CarBrowser]). It decides whether the
 * car screen shows the page's picture and takes taps on the page; it never pauses, mutes or stops audio, and the phone's
 * own screens, background playback, picture-in-picture and external displays do not consult it.
 */
class ParkGate {
    enum class Speed { UNKNOWN, STOPPED, MOVING }

    var speed: Speed = Speed.UNKNOWN
        private set
    var confirmed: Boolean = false
        private set

    /** A change of the gate's outcome, for the log (null from [onSpeed] / [confirmParked] when nothing changed). */
    data class Change(val speedBefore: Speed, val speedAfter: Speed, val videoBefore: Boolean, val videoAfter: Boolean, val needsConfirmation: Boolean) {
        override fun toString(): String = "speed ${speedBefore.name.lowercase()} → ${speedAfter.name.lowercase()}, picture " +
            (if (videoBefore) "on" else "off") + " → " + (if (videoAfter) "on" else "off") + if (needsConfirmation) ", waiting for \"Parked\"" else ""
    }

    /** [onSpeed] and what changed (null: same speed class and same picture rule). */
    fun update(metersPerSecond: Float?): Change? {
        val s0 = speed
        val v0 = videoAllowed
        onSpeed(metersPerSecond)
        return if (s0 == speed && v0 == videoAllowed) null else Change(s0, speed, v0, videoAllowed, needsConfirmation)
    }

    /** [confirmParked] and what changed; null when refused (the car reports movement). */
    fun confirm(): Change? {
        val v0 = videoAllowed
        return if (confirmParked()) Change(speed, speed, v0, videoAllowed, needsConfirmation) else null
    }

    /** [metersPerSecond] null = the car gives no (valid) speed. */
    fun onSpeed(metersPerSecond: Float?) {
        speed = when {
            metersPerSecond == null || metersPerSecond.isNaN() -> Speed.UNKNOWN

            metersPerSecond > MOVING_MPS -> Speed.MOVING

            metersPerSecond < STOPPED_MPS -> Speed.STOPPED

            speed == Speed.UNKNOWN -> Speed.MOVING

            // between the thresholds without history: treat as driving
            else -> speed
        }
        if (speed == Speed.MOVING) confirmed = false
    }

    /** The driver says the car is parked; refused while the car reports movement. */
    fun confirmParked(): Boolean {
        if (speed == Speed.MOVING) return false
        confirmed = true
        return true
    }

    val videoAllowed: Boolean get() = speed == Speed.STOPPED || (speed == Speed.UNKNOWN && confirmed)
    val needsConfirmation: Boolean get() = speed == Speed.UNKNOWN && !confirmed

    companion object {
        const val MOVING_MPS = 2.0f
        const val STOPPED_MPS = 0.5f
    }
}
