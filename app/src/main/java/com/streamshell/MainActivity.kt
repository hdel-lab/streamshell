package com.streamshell

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.streamshell.core.adaptive.FoldInfo
import com.streamshell.core.adaptive.FoldOrientation
import com.streamshell.core.adaptive.FoldState
import com.streamshell.core.adaptive.WindowLayoutPolicy
import com.streamshell.core.media.MediaPolicy
import com.streamshell.core.media.MediaState
import com.streamshell.core.navigation.BackAction
import com.streamshell.core.navigation.BackPolicy
import com.streamshell.core.navigation.NavAction
import com.streamshell.designsystem.StreamColors
import com.streamshell.designsystem.StreamShellTheme
import com.streamshell.designsystem.resolveDark
import com.streamshell.feature.browser.BrowserActions
import com.streamshell.feature.browser.BrowserChrome
import com.streamshell.feature.browser.BrowserScreen
import com.streamshell.feature.browser.BrowserViewModel
import com.streamshell.feature.browser.LogButton
import com.streamshell.feature.browser.traceTouches
import com.streamshell.feature.privacy.PrivacyActions
import com.streamshell.feature.privacy.PrivacyScreen
import com.streamshell.feature.privacy.PrivacyViewModel
import com.streamshell.feature.settings.SettingsActions
import com.streamshell.feature.settings.SettingsScreen
import com.streamshell.feature.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Single activity. It owns only window concerns: edge-to-edge, fullscreen video (orientation and system
 * bars), picture-in-picture and lifecycle forwarding. Browser state lives in [BrowserViewModel], the
 * engine in BrowserEngine. Configuration changes (fold, rotate, resize) never recreate it (see manifest).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val browser: BrowserViewModel by viewModels()
    private val settingsVm: SettingsViewModel by viewModels()
    private val privacyVm: PrivacyViewModel by viewModels()

    private var inPip by mutableStateOf(false)
    private var askedNotifications = false
    private val notificationPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    /** Android 13+: the media notification needs this permission to be shown. Asked once, the first time media plays with background playback on. */
    private fun maybeAskNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || askedNotifications) return
        askedNotifications = true
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ---------------------------------------------------------------- files (Storage Access Framework: no storage permission)

    private val importSubscriptions = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readText(uri)?.let(browser::importSubscriptions)
    }
    private val restoreBackup = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readText(uri)?.let(browser::importBackup)
    }
    private val createBackup = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val ok = runCatching {
                val json = browser.exportBackup()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    requireNotNull(contentResolver.openOutputStream(uri, "wt")) { "no stream" }.use { it.write(json.toByteArray()) }
                }
            }.isSuccess
            browser.showMessage(if (ok) "Backup saved" else "Could not save the backup")
        }
    }

    /** Diagnostics › Save as file: header, summary and every line, never cut. */
    private val saveLog = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val text = browser.diagnosticsText()
            val ok = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    requireNotNull(contentResolver.openOutputStream(uri, "wt")) { "no stream" }.use { it.write(text.toByteArray()) }
                }
            }.isSuccess
            browser.showMessage(if (ok) "Log saved" else "Could not save the log")
        }
    }

    /** Trace everything: screen stutter and memory while the app is in front. */
    private val frames = com.streamshell.feature.browser.FrameMonitor { line -> browser.trace("frames", line) }
    private fun tracing() = browser.state.value.settings.let { it.diagnostics && it.traceEverything }

    /** Reads a small text file the user picked (max. 8 MB). */
    private fun readText(uri: android.net.Uri): String? = runCatching {
        requireNotNull(contentResolver.openInputStream(uri)) { "no stream" }.use { input ->
            val bytes = input.readBytes()
            require(bytes.size <= com.streamshell.core.watch.Backup.MAX_BYTES)
            String(bytes, Charsets.UTF_8)
        }
    }.getOrElse {
        browser.showMessage("Could not read the file")
        null
    }

    private val pipSupported by lazy { packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) }

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_PIP_TOGGLE) browser.togglePlay()
        }
    }

    private fun applySystemBars(dark: Boolean) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        val bg = (if (dark) StreamColors.Dark else StreamColors.Light).background.toArgb()
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bg))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Notification "Exit": close the app's task (playback was already stopped).
        com.streamshell.core.media.MediaBridge.onExit = { runOnUiThread { finishAndRemoveTask() } }
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        // Until the saved settings are read (a few ms, without blocking the main thread) the window shows the system tone.
        applySystemBars(systemDark)
        ContextCompat.registerReceiver(this, pipReceiver, IntentFilter(ACTION_PIP_TOGGLE), ContextCompat.RECEIVER_NOT_EXPORTED)

        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        browser.onForeground()
                        browser.trace("app", "in front")
                        if (tracing()) frames.start()
                    }

                    Lifecycle.Event.ON_STOP -> {
                        browser.trace("app", "in background" + if (isInPictureInPictureMode) " (picture-in-picture)" else "")
                        frames.stop()
                        browser.onBackground(isInPictureInPictureMode)
                    }

                    else -> Unit
                }
            }
        )
        lifecycleScope.launch {
            browser.awaitReady()
            applySystemBars(resolveDark(browser.state.value.settings.themeMode, systemDark))
            setContent { Root() }
            // A link or shortcut that started the app is handled after the start page is chosen (not overwritten by it).
            if (savedInstanceState == null) handleViewIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lifecycleScope.launch {
            browser.awaitReady()
            handleViewIntent(intent)
        }
    }

    /** A link from another app opens in a new tab (http is upgraded to https); launcher shortcuts. */
    private fun handleViewIntent(intent: Intent?) {
        when (intent?.action) {
            SHORTCUT_YOUTUBE -> {
                browser.openSiteApp(com.streamshell.core.browser.SiteApp.YOUTUBE)
                return
            }

            SHORTCUT_SEARCH -> {
                browser.requestSearch()
                return
            }

            SHORTCUT_CONTINUE -> {
                browser.continueLatest()
                return
            }

            SHORTCUT_WATCH_LATER -> {
                browser.dispatch(NavAction.Open(com.streamshell.core.model.Overlay.WatchLater))
                return
            }

            SHORTCUT_LIBRARY -> {
                browser.dispatch(NavAction.Open(com.streamshell.core.model.Overlay.Library))
                return
            }
        }
        if (intent?.action != Intent.ACTION_VIEW) return
        val url = intent.dataString ?: return
        val target = when {
            url.startsWith("https://", true) -> url
            url.startsWith("http://", true) -> "https://" + url.substring(7)
            else -> return
        }
        // A YouTube video from another app (watch or youtu.be link) plays in the YouTube instance's player, like a video
        // tapped in the app; everything else opens in a new tab.
        com.streamshell.core.browser.ExternalLinks.youtubeWatch(target)?.let {
            browser.openVideo(it)
            return
        }
        browser.openInNewTab(target)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipReceiver) }
        com.streamshell.core.media.MediaBridge.onExit = null
        super.onDestroy()
    }

    // ------------------------------------------------------------ picture-in-picture

    private fun pipParams(): PictureInPictureParams {
        val fs = browser.engine.fullscreenView.value
        val v = browser.engine.video.value
        // Window shaped like the video: the fullscreen view, else the video element in the page, else 16:9.
        val (w, h) = when {
            fs != null -> MediaPolicy.pipAspect(fs.width, fs.height)
            v != null -> MediaPolicy.pipAspect(v.width.toInt(), v.height.toInt())
            else -> 16 to 9
        }
        val playing = browser.engine.media.value == MediaState.PLAYING
        val b = PictureInPictureParams.Builder().setAspectRatio(Rational(w, h))
        browser.engine.visibleRect(browser.state.value.active.id)?.let { b.setSourceRectHint(it) }
        val pi = PendingIntent.getBroadcast(this, 1, Intent(ACTION_PIP_TOGGLE).setPackage(packageName), PendingIntent.FLAG_IMMUTABLE)
        val icon = Icon.createWithResource(this, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        b.setActions(listOf(RemoteAction(icon, if (playing) "Pause" else "Play", if (playing) "Pause" else "Play", pi)))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val s = browser.state.value
            b.setAutoEnterEnabled(MediaPolicy.shouldAutoPip(s.settings.autoPip, pipSupported, fs != null, s.media))
            b.setSeamlessResizeEnabled(true)
        }
        return b.build()
    }

    private fun refreshPipParams() {
        if (pipSupported) runCatching { setPictureInPictureParams(pipParams()) }
    }

    fun enterPip() {
        if (pipSupported) runCatching { enterPictureInPictureMode(pipParams()) }
    }

    /** Android 8-11: no auto-enter flag, so PiP starts when the user presses Home while media plays. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val s = browser.state.value
            if (MediaPolicy.shouldAutoPip(s.settings.autoPip, pipSupported, browser.engine.fullscreenView.value != null, s.media)) enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        browser.onPipChanged(isInPictureInPictureMode)
        // Leaving PiP while the activity is not coming back to the front means the PiP window was closed.
        if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) browser.onPipDismissed()
    }

    // ------------------------------------------------------------ UI

    @Composable
    private fun Root() {
        val s by browser.state.collectAsStateWithLifecycle()
        val fullscreen by browser.engine.fullscreenView.collectAsStateWithLifecycle()
        val video by browser.engine.video.collectAsStateWithLifecycle()
        val settingsState by settingsVm.state.collectAsStateWithLifecycle()
        val privacyState by privacyVm.state.collectAsStateWithLifecycle()
        val diagLines by browser.diagnostics.all.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        val view = LocalView.current

        val dark = resolveDark(s.settings.themeMode, androidx.compose.foundation.isSystemInDarkTheme())
        LaunchedEffect(dark) { applySystemBars(dark) }
        // Gesture handle / nav buttons readable on the site bar, which takes the page's tone.
        val pageBg = s.page.background.takeIf { s.siteApp != null && !s.showingStart }
        LaunchedEffect(dark, pageBg) {
            val lightNav = pageBg?.let { androidx.compose.ui.graphics.Color(it).luminance() >= 0.5f } ?: !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = lightNav
        }

        val config = LocalConfiguration.current
        val fold by produceState<FoldInfo?>(null) {
            WindowInfoTracker.getOrCreate(this@MainActivity).windowLayoutInfo(this@MainActivity).collect { info ->
                value = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()?.let { f ->
                    FoldInfo(
                        state = if (f.state == FoldingFeature.State.HALF_OPENED) FoldState.HalfOpened else FoldState.Flat,
                        orientation = if (f.orientation == FoldingFeature.Orientation.HORIZONTAL) FoldOrientation.Horizontal else FoldOrientation.Vertical,
                        isSeparating = f.isSeparating,
                        boundsLeft = f.bounds.left,
                        boundsTop = f.bounds.top,
                        boundsRight = f.bounds.right,
                        boundsBottom = f.bounds.bottom
                    )
                }
            }
        }
        val layout = WindowLayoutPolicy.compute(config.screenWidthDp, config.screenHeightDp, fold)

        // Fullscreen video: landscape (sensor), no system bars. Back to normal afterwards.
        LaunchedEffect(fullscreen) {
            val controller = WindowCompat.getInsetsController(window, view)
            if (fullscreen != null) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        LaunchedEffect(fullscreen, s.media, s.settings.autoPip, video) { refreshPipParams() }
        val trace = s.settings.diagnostics && s.settings.traceEverything
        LaunchedEffect(trace) { if (trace) frames.start() else frames.stop() }
        LaunchedEffect(s.media, s.settings.backgroundPlayback) {
            if (s.media == MediaState.PLAYING && s.settings.backgroundPlayback) maybeAskNotificationPermission()
        }

        LaunchedEffect(s.message) {
            s.message?.let {
                snackbar.showSnackbar(it)
                browser.messageShown()
            }
        }
        LaunchedEffect(settingsState.message) {
            settingsState.message?.let {
                snackbar.showSnackbar(it)
                settingsVm.messageShown()
            }
        }
        LaunchedEffect(privacyState.message) {
            privacyState.message?.let {
                snackbar.showSnackbar(it)
                privacyVm.messageShown()
            }
        }

        val back = BackPolicy.resolve(fullscreen != null, s.nav, pageCanGoBack = !s.showingStart && s.page.canGoBack, canReturnToStart = !s.showingStart && s.active.openedFromStart)
        // Back from an instance's video player: shrink it / return to the instance (like the YouTube app).
        val leavePlayer = s.nav.overlay == com.streamshell.core.model.Overlay.None && fullscreen == null &&
            s.active.playerFor != null && s.tabs.tabs.any { it.id == s.active.playerFor }
        BackHandler(enabled = (back != BackAction.Finish || leavePlayer) && !inPip) {
            if (leavePlayer) {
                browser.leavePlayer()
                return@BackHandler
            }
            when (back) {
                BackAction.ExitFullscreenVideo -> browser.engine.exitFullscreen()
                BackAction.CloseOverlay -> browser.dispatch(NavAction.CloseOverlay)
                BackAction.PageBack -> browser.engine.goBack(s.active.id)
                BackAction.ShowStartPage -> browser.showStart()
                BackAction.Finish -> Unit
            }
        }

        val actions = BrowserActions(
            onSubmitAddress = browser::submitAddress,
            onOpen = browser::open,
            onOpenInNewTab = browser::openInNewTab,
            onBack = browser::back,
            onForward = browser::forward,
            onHome = browser::showStart,
            onReload = browser::reload,
            onStop = browser::stopLoading,
            onNav = browser::dispatch,
            onNewTab = browser::newTab,
            onSelectTab = browser::selectTab,
            onCloseTab = browser::closeTab,
            onCloseAllTabs = browser::closeAllTabs,
            onToggleBookmark = browser::toggleBookmark,
            onRemoveBookmark = browser::removeBookmark,
            onRemoveHistory = browser::removeHistory,
            onClearHistory = browser::clearHistory,
            onToggleSiteExempt = browser::toggleSiteExempt,
            onResolveBlocked = browser::resolveBlockedNavigation,
            onPip = ::enterPip,
            onSiteDestination = { d -> browser.open(d.url) },
            onSiteSearch = browser::searchInSite,
            searchSuggestions = { q -> browser.searchSuggestions(q) },
            onClearRecentSearches = browser::clearRecentSearches,
            onSiteBarChange = browser::setSiteBar,
            onMinimize = browser::minimize,
            isPageAtTop = browser::isPageAtTop,
            onOpenSite = browser::openFromStart,
            onSwitchInstance = browser::switchInstance,
            onRestoreMini = browser::restoreMini,
            onCloseMini = browser::closeMini,
            onToggleMiniPlay = browser::togglePlay,
            onOpenVideo = browser::openVideo,
            onToggleWatchLater = browser::toggleWatchLater,
            onRemoveWatchLater = browser::removeWatchLater,
            onSetSpeed = browser::setPlaybackSpeed,
            onSeek = browser::seekStep,
            onTrack = browser::track,
            onStartOver = browser::startOver,
            onSleep = browser::setSleepTimer,
            onImportSubscriptions = { runCatching { importSubscriptions.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/json", "application/octet-stream")) } },
            onRefreshFeed = browser::refreshFeed,
            onOpenChannel = browser::openChannel,
            onRemoveChannel = browser::removeChannel,
            onSeekTo = browser::seekTo,
            onQueryPosition = browser::queryPosition,
            onFullscreenTap = browser::fullscreenTap,
            onWideChat = browser::setWideChat,
            onRecommendationsColumn = browser::setRecommendationsColumn,
            onPlayAllLater = browser::playAllWatchLater,
            onAddToQueue = browser::addToQueue,
            onPlayQueueFrom = browser::playQueueFrom,
            onRemoveFromQueue = browser::removeFromQueue,
            onClearQueue = browser::clearQueue,
            onSetChannelGroup = browser::setChannelGroup,
            onExitFullscreen = browser.engine::exitFullscreen,
            onVideoZoom = browser::setVideoZoom,
            onSetRepeatVideo = browser::setRepeatVideo,
            onMarkRepeat = browser::markRepeat,
            onClearRepeat = browser::clearRepeatPart,
            onNextChapter = browser::nextChapter,
            onSetChannelSpeed = browser::setChannelSpeed,
            onBlockChannel = browser::blockCurrentChannel,
            onCreatePlaylist = browser::createPlaylist,
            onRenamePlaylist = browser::renamePlaylist,
            onDeletePlaylist = browser::deletePlaylist,
            onAddToPlaylist = browser::addToPlaylist,
            onRemoveFromPlaylist = browser::removeFromPlaylist,
            onMovePlaylistItem = browser::movePlaylistItem,
            onPlayPlaylist = browser::playPlaylist,
            onNewPrivateTab = browser::newPrivateTab,
            onShare = { url ->
                val link = if (s.settings.cleanShareLinks) com.streamshell.core.browser.ShareLinks.clean(url) else url
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
                runCatching { startActivity(Intent.createChooser(send, "Share page")) }
            }
        )
        val settingsActions = SettingsActions(
            onUpdate = settingsVm::update,
            onOpenPrivacy = { browser.dispatch(NavAction.Open(com.streamshell.core.model.Overlay.Privacy)) },
            onResetSettings = settingsVm::resetSettings,
            onOpenExternal = { url -> browser.openInNewTab(url) },
            onClearData = browser::clearBrowsingData,
            onBackup = { runCatching { createBackup.launch("StreamShell-backup-${java.time.LocalDate.now()}.json") } },
            onRestore = { runCatching { restoreBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) } }
        )
        val privacyActions = PrivacyActions(
            onUpdate = privacyVm::update,
            onUpdateNow = privacyVm::updateNow,
            onSetListEnabled = privacyVm::setListEnabled,
            onRemoveList = privacyVm::removeList,
            onAddList = privacyVm::addList,
            onCancelTrust = privacyVm::cancelTrust,
            onAddBlocked = privacyVm::addBlocked,
            onAddAllowed = privacyVm::addAllowed,
            onRemoveBlocked = privacyVm::removeBlocked,
            onRemoveAllowed = privacyVm::removeAllowed,
            onAddExempt = privacyVm::addExempt,
            onRemoveExempt = privacyVm::removeExempt,
            onClearStats = privacyVm::clearStats,
            onResetDefaults = privacyVm::resetDefaults,
            explain = privacyVm::explain,
            onOpenExternal = { url -> browser.openInNewTab(url) }
        )

        val pageDark = resolveDark(s.settings.themeMode, androidx.compose.foundation.isSystemInDarkTheme())
        androidx.compose.runtime.LaunchedEffect(pageDark) { browser.engine.setPageBackground(if (pageDark) 0xFF0F0F0F.toInt() else android.graphics.Color.WHITE) }
        StreamShellTheme(
            s.settings.themeMode,
            dynamicColor = s.settings.dynamicColor,
            amoledBlack = s.settings.amoledBlack,
            accent = com.streamshell.core.model.AccentColors.OPTIONS.firstOrNull { it.first == s.settings.accent && it.first != "blue" }?.second
        ) {
            val density = androidx.compose.ui.platform.LocalDensity.current.density
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().traceTouches(trace, { (view as? androidx.compose.ui.node.RootForTest)?.semanticsOwner }, density) { line -> browser.trace("ui", line) }
            ) {
                BrowserScreen(
                    state = s,
                    layout = layout,
                    actions = actions,
                    page = { tabId, m -> TabSurface(tabId, m) },
                    fullscreen = fullscreen,
                    chrome = BrowserChrome(inPip = inPip, pipSupported = pipSupported, pipVideo = video),
                    snackbar = snackbar,
                    settingsContent = { onClose -> SettingsScreen(settingsState, settingsActions, onClose) },
                    privacyContent = { onClose -> PrivacyScreen(privacyState, privacyActions, onClose) },
                    diagnosticsContent = { onClose ->
                        com.streamshell.feature.browser.DiagnosticsPanel(
                            diagLines,
                            tracing = trace,
                            onShare = {
                                val text = browser.diagnosticsText()
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "StreamShell diagnostics").putExtra(Intent.EXTRA_TEXT, text)
                                runCatching { startActivity(Intent.createChooser(send, "Share diagnostics")) }
                            },
                            onSave = {
                                val t = java.time.LocalDateTime.now().withNano(0).toString().replace(':', '-').replace('T', '-')
                                runCatching { saveLog.launch("StreamShell-log-$t.txt") }
                            },
                            onClear = { browser.diagnostics.clear() },
                            onClose = onClose,
                            crash = com.streamshell.core.diag.DiagnosticsHub.lastCrash
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
                val diagOpen = s.nav.overlay == com.streamshell.core.model.Overlay.Diagnostics
                if (s.settings.diagnostics && s.settings.logButton && fullscreen == null && !inPip && !diagOpen) {
                    LogButton(diagLines.size) { browser.dispatch(NavAction.Open(com.streamshell.core.model.Overlay.Diagnostics)) }
                }
            }
        }
    }

    /**
     * Binds a tab's long-lived WebView inside a small container. The same tab can move between surfaces
     * (page <-> mini player) in one frame, so a surface only detaches the WebView if it still holds it.
     */
    @Composable
    private fun TabSurface(tabId: Long, modifier: Modifier) {
        val versions by browser.engine.versions.collectAsStateWithLifecycle()
        androidx.compose.runtime.key(tabId, versions[tabId] ?: 0) {
            AndroidView(
                factory = { ctx -> android.widget.FrameLayout(ctx).apply { addView(browser.engine.attach(tabId, ctx)) } },
                onRelease = { container ->
                    if (container.childCount > 0) {
                        container.removeAllViews()
                        browser.engine.detach(tabId)
                    }
                },
                modifier = modifier
            )
        }
    }

    private companion object {
        const val SHORTCUT_YOUTUBE = "com.streamshell.action.YOUTUBE"
        const val SHORTCUT_SEARCH = "com.streamshell.action.SEARCH"
        const val SHORTCUT_CONTINUE = "com.streamshell.action.CONTINUE"
        const val SHORTCUT_WATCH_LATER = "com.streamshell.action.WATCH_LATER"
        const val SHORTCUT_LIBRARY = "com.streamshell.action.LIBRARY"
        const val ACTION_PIP_TOGGLE = "com.streamshell.pip.TOGGLE"
    }
}
