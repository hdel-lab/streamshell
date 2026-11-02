package com.streamshell.core.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

/**
 * "Make room for calls and navigation voices" (0.6.2). The WebView handles audio focus on its own; asking for focus as
 * well made the two take it from each other (see [PlaybackService]). So StreamShell does not hold focus but *watches*:
 *  - a phone or internet call (audio mode not normal, or another app playing voice-call audio) → playing pages pause;
 *    when the call ends they play again (only those this rule paused),
 *  - another app's navigation / radar / assistant voice → pages play at a quarter of their volume while it speaks; if the
 *    page stopped meanwhile (the other app took the audio focus for good), it plays again afterwards.
 * Nothing needs a permission (AudioManager playback callback and audio mode). Pure decisions in [AudioYieldRules].
 */
interface AudioTarget {
    val label: String

    /** Asks the page whether it is playing (asynchronous). */
    fun probe(result: (Boolean) -> Unit)
    fun pause()
    fun play()
    fun duck(on: Boolean)
}

/** What to do with one page (pure, unit-tested). */
object AudioYieldRules {
    enum class Do { NOTHING, PAUSE, PLAY, DUCK, UNDUCK, UNDUCK_AND_PLAY }

    fun callStarts(playing: Boolean): Do = if (playing) Do.PAUSE else Do.NOTHING
    fun callEnds(pausedByCall: Boolean): Do = if (pausedByCall) Do.PLAY else Do.NOTHING
    fun voiceStarts(playing: Boolean, inCall: Boolean): Do = if (playing && !inCall) Do.DUCK else Do.NOTHING
    fun voiceEnds(ducked: Boolean, wasPlaying: Boolean, playingNow: Boolean): Do = when {
        !ducked -> Do.NOTHING
        wasPlaying && !playingNow -> Do.UNDUCK_AND_PLAY
        else -> Do.UNDUCK
    }

    /** Other apps' audio that counts as a navigation / radar / assistant voice. */
    val VOICE_USAGES = setOf(
        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
        AudioAttributes.USAGE_ASSISTANT,
        AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
    )
    val CALL_USAGES = setOf(AudioAttributes.USAGE_VOICE_COMMUNICATION, AudioAttributes.USAGE_NOTIFICATION_RINGTONE)

    fun isCallMode(mode: Int): Boolean = mode != AudioManager.MODE_NORMAL && mode != AudioManager.MODE_INVALID && mode != AudioManager.MODE_CURRENT
}

/** One per process: the phone's pages and the car's page register as targets. */
object AudioCoordinator {
    @Volatile var enabled = true

    /** Diagnostics line. */
    var log: (String) -> Unit = {}

    private val main = Handler(Looper.getMainLooper())
    private val targets = CopyOnWriteArrayList<AudioTarget>()
    private var audio: AudioManager? = null
    private var started = false
    private var inCall = false
    private var voice = false
    private val pausedByCall = HashSet<AudioTarget>()
    private val ducked = HashMap<AudioTarget, Boolean>()

    fun register(context: Context, t: AudioTarget) {
        if (t !in targets) targets += t
        start(context.applicationContext)
    }

    fun unregister(t: AudioTarget) {
        targets -= t
        pausedByCall -= t
        ducked -= t
    }

    private fun start(context: Context) {
        if (started) return
        val am = context.getSystemService(AudioManager::class.java) ?: return
        audio = am
        started = true
        am.registerAudioPlaybackCallback(
            object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) = onConfigs(configs)
            },
            main
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.addOnModeChangedListener({ r -> main.post(r) }) { mode -> onCall(AudioYieldRules.isCallMode(mode) || callPlaying) }
        } else {
            main.post(modePoll)
        }
    }

    private var callPlaying = false

    private val modePoll = object : Runnable {
        override fun run() {
            audio?.let { onCall(AudioYieldRules.isCallMode(it.mode) || callPlaying) }
            main.postDelayed(this, MODE_POLL_MS)
        }
    }

    private fun onConfigs(configs: List<AudioPlaybackConfiguration>) {
        val usages = configs.map { it.audioAttributes.usage }
        callPlaying = usages.any { it in AudioYieldRules.CALL_USAGES }
        val mode = audio?.mode ?: AudioManager.MODE_NORMAL
        onCall(AudioYieldRules.isCallMode(mode) || callPlaying)
        val v = usages.any { it in AudioYieldRules.VOICE_USAGES }
        main.removeCallbacks(voiceOff)
        if (v) {
            onVoice(true)
        } else if (voice) {
            main.postDelayed(voiceOff, VOICE_END_MS)
        }
    }

    private val voiceOff = Runnable { onVoice(false) }

    private fun onCall(now: Boolean) {
        if (now == inCall) return
        inCall = now
        if (!enabled) return
        if (now) {
            log("call started")
            targets.forEach { t ->
                t.probe { playing ->
                    if (AudioYieldRules.callStarts(playing) == AudioYieldRules.Do.PAUSE) {
                        pausedByCall += t
                        t.pause()
                        log("call: ${t.label} paused")
                    }
                }
            }
        } else {
            log("call ended")
            val list = pausedByCall.toList()
            pausedByCall.clear()
            main.postDelayed({
                list.forEach { t ->
                    if (AudioYieldRules.callEnds(true) == AudioYieldRules.Do.PLAY && t in targets) {
                        t.play()
                        log("call ended: ${t.label} plays again")
                    }
                }
            }, RESUME_AFTER_CALL_MS)
        }
    }

    private fun onVoice(now: Boolean) {
        if (now == voice) return
        voice = now
        if (!enabled) return
        if (now) {
            targets.forEach { t ->
                t.probe { playing ->
                    if (AudioYieldRules.voiceStarts(playing, inCall) == AudioYieldRules.Do.DUCK) {
                        ducked[t] = true
                        t.duck(true)
                        log("navigation voice: ${t.label} quieter")
                    }
                }
            }
        } else {
            ducked.keys.toList().forEach { t ->
                ducked.remove(t)
                t.probe { playingNow ->
                    when (AudioYieldRules.voiceEnds(ducked = true, wasPlaying = true, playingNow = playingNow)) {
                        AudioYieldRules.Do.UNDUCK_AND_PLAY -> {
                            t.duck(false)
                            t.play()
                            log("navigation voice over: ${t.label} back to full volume, played again (it had stopped)")
                        }

                        else -> {
                            t.duck(false)
                            log("navigation voice over: ${t.label} back to full volume")
                        }
                    }
                }
            }
        }
    }

    private const val MODE_POLL_MS = 2000L
    private const val VOICE_END_MS = 700L
    private const val RESUME_AFTER_CALL_MS = 1200L
}
