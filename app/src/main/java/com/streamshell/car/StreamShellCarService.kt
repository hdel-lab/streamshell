package com.streamshell.car

import android.content.Intent
import androidx.car.app.AppManager
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Speed
import androidx.car.app.validation.HostValidator
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.streamshell.core.browser.BrowserEngine
import com.streamshell.core.browser.EngineListener
import com.streamshell.core.browser.NetworkMonitor
import com.streamshell.core.browser.YouTubeExtras
import com.streamshell.core.diag.DiagnosticsHub
import com.streamshell.core.model.AppSettings
import com.streamshell.core.privacy.FilterDecision
import com.streamshell.core.privacy.PrivacyCoordinator
import com.streamshell.core.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Android Auto entry point (unofficial use): StreamShell presents itself as a navigation app to get a drawing surface on
 * the car screen and shows its browser there. Android Auto only lists such an app when it was installed with Google
 * Play as installer (see docs/ANDROID_AUTO.md).
 */
@AndroidEntryPoint
class StreamShellCarService : CarAppService() {
    @Inject lateinit var privacy: PrivacyCoordinator

    @Inject lateinit var network: NetworkMonitor

    @Inject lateinit var settings: SettingsRepository

    /**
     * Only Android Auto / Android Automotive hosts signed by Google may connect (res/values/car_hosts.xml: package names and
     * signing-certificate digests from the Car App Library); any host only in debug builds, for testing. Before 0.3.7 every app on the phone could
     * bind as a "car" and drive the browser.
     */
    override fun createHostValidator(): HostValidator = if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    } else {
        HostValidator.Builder(applicationContext).addAllowedHosts(com.streamshell.R.array.car_hosts_allowlist).build()
    }

    override fun onCreateSession(): Session = CarSession(makeEngine = { BrowserEngine(applicationContext, privacy, network) }, settings = settings.settings)
}

/**
 * One car connection. Lifecycles are kept apart (0.6.1):
 *  - **page and sound**: the session's own [BrowserEngine] (created here, destroyed in onDestroy) — independent of the
 *    phone's screens and of the car surface;
 *  - **picture**: the surface Android Auto hands to [CarBrowser] (presentation on a private virtual display), created and
 *    destroyed by the host;
 *  - **picture rule**: [ParkGate], fed by the car's speed; it only switches [CarBrowser]'s cover and page taps.
 * Every transition goes to the shared diagnostics log ("car · …") while Diagnostics is on.
 */
class CarSession(private val makeEngine: () -> BrowserEngine, private val settings: Flow<AppSettings>) : Session() {
    private val gate = ParkGate()
    private var engine: BrowserEngine? = null
    private var browser: CarBrowser? = null
    private var speedListener: OnCarDataAvailableListener<Speed>? = null

    private fun log(line: String) = DiagnosticsHub.add("car", line)

    override fun onCreateScreen(intent: Intent): Screen {
        val e = makeEngine().also { engine = it }
        e.setYouTubeCompact(true)
        e.setYouTubeAutoplay(true)
        e.listener = PageLog { line -> log("page · $line") }
        val b = CarBrowser(carContext, e, gate) { line -> log(line) }.also { browser = it }
        val screen = CarBrowserScreen(carContext, b, gate) { log("\"Parked\" tapped: " + (it?.toString() ?: "refused, the car reports movement")) }
        // Settings first (at most 1.5 s), then the surface is asked for, so the rail is built on the chosen side.
        // Defaults if they cannot be read: ads skipped, rail left. Later changes (ads, diagnostics) apply at once.
        lifecycleScope.launch {
            val first = withTimeoutOrNull(SETTINGS_WAIT_MS) { runCatching { settings.first() }.getOrNull() }
            first?.let {
                apply(it, e, b)
                b.source = if (it.carSource == "music") com.streamshell.core.browser.SiteApp.YOUTUBE_MUSIC else com.streamshell.core.browser.SiteApp.YOUTUBE
            }
            b.start()
            log("session started (${b.source.title}, rail ${if (b.railLeft) "left" else "right"}, settings ${if (first == null) "not read, defaults" else "read"})")
            carContext.getCarService(AppManager::class.java).setSurfaceCallback(b)
            settings.drop(if (first == null) 0 else 1).collect { apply(it, e, b) }
        }
        val audio = object : com.streamshell.core.media.AudioTarget {
            override val label = "car"
            override fun probe(result: (Boolean) -> Unit) = e.probeMedia(CarBrowser.PAGE_ID) { result(it == com.streamshell.core.media.MediaState.PLAYING) }
            override fun pause() = e.pauseMedia(CarBrowser.PAGE_ID)
            override fun play() = e.playMedia(CarBrowser.PAGE_ID)
            override fun duck(on: Boolean) = e.duckMedia(CarBrowser.PAGE_ID, on)
        }
        com.streamshell.core.media.AudioCoordinator.log = { line -> DiagnosticsHub.add("audio", line) }
        com.streamshell.core.media.AudioCoordinator.register(carContext, audio)
        // The car's speed decides whether the picture may show (sound always plays).
        carContext.requestPermissions(listOf(SPEED_PERMISSION)) { granted, _ ->
            val ok = SPEED_PERMISSION in granted
            log("car speed permission " + if (ok) "granted" else "refused: picture only after \"Parked\"")
            if (ok) listenToSpeed(screen)
        }
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                log("session ended: picture and page closed")
                com.streamshell.core.media.AudioCoordinator.unregister(audio)
                speedListener?.let { l -> runCatching { carContext.getCarService(CarHardwareManager::class.java).carInfo.removeSpeedListener(l) } }
                b.release()
                e.destroy()
            }
        })
        return screen
    }

    private fun apply(s: AppSettings, e: BrowserEngine, b: CarBrowser) {
        DiagnosticsHub.enabled = s.diagnostics
        DiagnosticsHub.traceEverything = s.diagnostics && s.traceEverything
        e.setSkipYouTubeAds(s.skipYouTubeAds)
        com.streamshell.core.media.AudioCoordinator.enabled = s.yieldAudio
        e.trace = s.diagnostics && s.traceEverything
        e.setExtras(YouTubeExtras.config("", dislikes = false, deArrow = false, markers = false, diag = s.diagnostics, trace = s.diagnostics && s.traceEverything))
        b.railLeft = s.carRailLeft
    }

    private fun listenToSpeed(screen: Screen) {
        val l = OnCarDataAvailableListener<Speed> { s ->
            val v = s.displaySpeedMetersPerSecond.takeIf { it.status == CarValue.STATUS_SUCCESS } ?: s.rawSpeedMetersPerSecond
            val change = gate.update(if (v.status == CarValue.STATUS_SUCCESS) v.value else null)
            if (change != null) {
                log("gate: $change")
                browser?.refresh()
                screen.invalidate()
            }
        }
        speedListener = l
        runCatching { carContext.getCarService(CarHardwareManager::class.java).carInfo.addSpeedListener(ContextCompat.getMainExecutor(carContext), l) }
            .onSuccess { log("listening to the car's speed") }
            .onFailure { log("car speed not available (${it.javaClass.simpleName}): picture only after \"Parked\"") }
    }

    /** The car engine's page events: only its diagnostics lines are used (the car has no tabs, history or downloads). */
    private class PageLog(private val line: (String) -> Unit) : EngineListener {
        override fun onUrlChanged(tabId: Long, url: String) = Unit
        override fun onTitle(tabId: Long, url: String?, title: String) = Unit
        override fun onVisited(tabId: Long, url: String, title: String) = Unit
        override fun onNavigationBlocked(tabId: Long, url: String, host: String, decision: FilterDecision.Block) = Unit
        override fun onExternal(uri: String) = Unit
        override fun onDownload(url: String, userAgent: String, contentDisposition: String?, mimeType: String?) = Unit
        override fun onDiagnostics(tabId: Long, line: String) = line(line)
    }

    companion object {
        const val SETTINGS_WAIT_MS = 1500L
        const val SPEED_PERMISSION = "com.google.android.gms.permission.CAR_SPEED"
    }
}
