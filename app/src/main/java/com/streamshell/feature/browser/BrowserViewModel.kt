package com.streamshell.feature.browser

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamshell.core.browser.AddressInput
import com.streamshell.core.browser.AddressTarget
import com.streamshell.core.browser.BrowserEngine
import com.streamshell.core.browser.Downloads
import com.streamshell.core.browser.EngineListener
import com.streamshell.core.browser.ExternalNavigator
import com.streamshell.core.browser.NetworkMonitor
import com.streamshell.core.browser.PageState
import com.streamshell.core.browser.PlayerOpen
import com.streamshell.core.browser.TabsReducer
import com.streamshell.core.browser.TabsSerializer
import com.streamshell.core.browser.TabsState
import com.streamshell.core.browser.UrlHosts
import com.streamshell.core.library.Bookmark
import com.streamshell.core.library.HistoryEntry
import com.streamshell.core.library.LibraryRepository
import com.streamshell.core.media.LeaveAction
import com.streamshell.core.media.MediaBridge
import com.streamshell.core.media.MediaControls
import com.streamshell.core.media.MediaPolicy
import com.streamshell.core.media.MediaState
import com.streamshell.core.media.PlaybackService
import com.streamshell.core.model.AppSettings
import com.streamshell.core.model.Overlay
import com.streamshell.core.navigation.NavAction
import com.streamshell.core.navigation.NavReducer
import com.streamshell.core.navigation.NavState
import com.streamshell.core.privacy.DomainInput
import com.streamshell.core.privacy.FilterDecision
import com.streamshell.core.privacy.PrivacyCoordinator
import com.streamshell.core.settings.SettingsRepository
import com.streamshell.di.LibraryStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit

/** A top-level page the filter refused; the user decides. */
data class BlockedNavigation(val tabId: Long, val url: String, val host: String, val rule: String, val source: String?)

/** Sleep timer: playback pauses at [endsAt] (epoch ms) or at the end of the current video. */
data class SleepTimer(
    val endsAt: Long? = null,
    val atEndOfVideo: Boolean = false,
    /** The chosen length (for the selected chip). */
    val minutes: Int? = null
) {
    val active get() = endsAt != null || atEndOfVideo
}

/** Videos to play one after another (from Watch later or "Add to queue"); [pos] is the one playing, -1 = not started. */
data class PlayQueue(val items: List<QueueItem> = emptyList(), val pos: Int = -1) {
    val active get() = pos in items.indices
    val current get() = items.getOrNull(pos)
}

data class QueueItem(val url: String, val title: String)

/** Pure queue rules (unit-tested). */
object QueueRules {
    const val MAX = 200

    fun add(q: PlayQueue, item: QueueItem): PlayQueue {
        val key = com.streamshell.core.watch.WatchRules.keyOf(item.url)
        if (q.items.any { com.streamshell.core.watch.WatchRules.keyOf(it.url) == key }) return q
        return q.copy(items = (q.items + item).take(MAX))
    }

    fun remove(q: PlayQueue, index: Int): PlayQueue {
        if (index !in q.items.indices) return q
        val items = q.items.filterIndexed { i, _ -> i != index }
        val pos = when {
            q.pos < 0 -> -1
            index < q.pos -> q.pos - 1
            index == q.pos -> -1
            else -> q.pos
        }
        return PlayQueue(items, pos)
    }

    /** The next item after the current one, and the queue moved there (pos -1 at the end). */
    fun next(q: PlayQueue): Pair<PlayQueue, QueueItem?> {
        val n = q.pos + 1
        return if (n in q.items.indices) q.copy(pos = n) to q.items[n] else q.copy(pos = -1) to null
    }
}

/** The diagnostics log moved to core (shared by the phone screens and the car screen, 0.6.1). */
typealias DiagnosticsLog = com.streamshell.core.diag.DiagnosticsLog

/** "Latest" list built from the imported channels' public feeds. */
data class FeedState(val loading: Boolean = false, val videos: List<com.streamshell.core.watch.FeedVideo> = emptyList(), val error: String? = null, val updatedAt: Long = 0)

data class BrowserUiState(
    val tabs: TabsState = TabsState.initial(),
    val page: PageState = PageState(),
    val settings: AppSettings = AppSettings(),
    val bookmarks: List<Bookmark> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val nav: NavState = NavState(),
    val media: MediaState = MediaState.NONE,
    val blockedNavigation: BlockedNavigation? = null,
    val message: String? = null,
    /** A video minimized into the floating mini player (its tab keeps playing while you browse). */
    val miniTabId: Long? = null,
    val miniTitle: String = "",
    /** Positions, Watch later, watch time, imported channels (local). */
    val watch: com.streamshell.core.watch.WatchData = com.streamshell.core.watch.WatchData(),
    val sleep: SleepTimer = SleepTimer(),
    val feed: FeedState = FeedState(),
    /** Bumped to ask the start page to focus its search field (launcher shortcut "Search"). */
    val focusSearch: Int = 0,
    /** Page id of the live chat card above the recommendations (wide screens, live streams), or null. */
    val chatPageId: Long? = null,
    val queue: PlayQueue = PlayQueue(),
    /** The playing video's channel, repeat state and (mini player) position. */
    val player: PlayerExtras = PlayerExtras()
) {
    val isWatchLater get() = com.streamshell.core.watch.WatchRules.isLater(watch.later, currentUrl)
    val continueWatching get() = com.streamshell.core.watch.WatchRules.continueWatching(watch.positions)

    /** The current page is a video (YouTube watch / shorts / live link): Watch later applies. */
    val isVideoPage get() = com.streamshell.core.sponsor.SponsorBlockApi.videoIdOf(currentUrl) != null

    /** A YouTube video page can be minimized into the mini player. */
    val canMinimize get() = miniTabId == null && com.streamshell.core.sponsor.SponsorBlockApi.videoIdOf(currentUrl) != null
    val active get() = tabs.active
    val showingStart get() = active.showingStart || active.url == null
    val currentUrl: String? get() = if (showingStart) null else (page.url ?: active.url)
    val currentHost: String? get() = UrlHosts.hostOf(currentUrl)
    val isBookmarked get() = currentUrl != null && bookmarks.any { it.url == currentUrl }
    val siteExempt get() = currentHost?.let { h -> DomainInput.normalize(h)?.let { it in settings.filterExemptSites } } ?: false
    val canGoBack get() = !showingStart && (page.canGoBack || active.openedFromStart)

    /** A site shown as an "app in the app" (YouTube, YouTube Music): StreamShell's own bars give way to the site's bar. */
    val siteApp: com.streamshell.core.browser.SiteApp? get() =
        if (showingStart || !settings.youtubeCompact) null else com.streamshell.core.browser.SiteApp.of(currentHost)
    val onYouTube get() = siteApp == com.streamshell.core.browser.SiteApp.YOUTUBE
    val onMusic get() = siteApp == com.streamshell.core.browser.SiteApp.YOUTUBE_MUSIC
    val canGoForward get() = (showingStart && active.url != null) || (!showingStart && page.canGoForward)
}

/** Per-video player state the app keeps (reset for every new video). */
data class PlayerExtras(
    val channel: String = "",
    val loop: Boolean = false,
    val repeatFrom: Double? = null,
    val repeatTo: Double? = null,
    val position: Double = 0.0,
    val duration: Double = 0.0,
    /** The video the app has confirmed playing in the player (its ID; "" = none yet) — ends the loading band. */
    val readyVideoId: String = ""
) {
    val progress: Float get() = if (duration > 0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f
}

@HiltViewModel
@OptIn(FlowPreview::class)
class BrowserViewModel @Inject constructor(
    private val app: Application,
    private val settingsRepo: SettingsRepository,
    private val library: LibraryRepository,
    private val privacy: PrivacyCoordinator,
    private val external: ExternalNavigator,
    network: NetworkMonitor,
    @LibraryStore private val store: DataStore<Preferences>
) : AndroidViewModel(app),
    EngineListener,
    MediaControls {

    val engine = BrowserEngine(app, privacy, network).also { it.listener = this }

    private val tabsKey = stringPreferencesKey("tabs_json")

    /** Filled from storage in [init] (off the main thread's critical path); [awaitReady] waits for it. */
    private val tabs = MutableStateFlow(TabsState.initial())
    private val ready = kotlinx.coroutines.CompletableDeferred<Unit>()

    /** Suspends until the saved tabs and settings are loaded and the first page is chosen (used before showing the UI). */
    suspend fun awaitReady() = ready.await()
    private val nav = MutableStateFlow(NavState())
    private val blockedNav = MutableStateFlow<BlockedNavigation?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val miniTab = MutableStateFlow<Long?>(null)
    private val watchRepo = com.streamshell.core.watch.WatchRepository(store)
    private val watchData = watchRepo.data.stateIn(viewModelScope, SharingStarted.Eagerly, com.streamshell.core.watch.WatchData())
    private val sleep = MutableStateFlow(SleepTimer())
    private val feed = MutableStateFlow(FeedState())
    private val focusSearch = MutableStateFlow(0)
    private val chatPage = MutableStateFlow<Long?>(null)
    private val player = MutableStateFlow(PlayerExtras())

    /** Tabs whose page runs a channel's remembered speed (the next video of another channel gets the normal speed back). */
    private val channelSpeedTabs = HashSet<Long>()
    private val queue = MutableStateFlow(PlayQueue())
    private var chatVideo: String? = null
    private val settings = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    @Volatile private var foreground = true

    @Volatile private var inPip = false

    val state: StateFlow<BrowserUiState> = combine(
        combine(tabs, engine.pages, settings) { t, p, s -> Triple(t, p, s) },
        combine(library.bookmarks, library.history) { b, h -> b to h },
        combine(nav, engine.media, blockedNav, message, miniTab) { n, m, bn, msg, mini -> arrayOf<Any?>(n, m, bn, msg, mini) },
        combine(combine(watchData, sleep, feed) { w, sl, f -> Triple(w, sl, f) }, focusSearch, chatPage, queue, player) { t3, fs, cp, q, pl -> arrayOf<Any?>(t3.first, t3.second, t3.third, fs, cp, q, pl) }
    ) { (t, p, s), (b, h), x, y ->
        val mini = (x[4] as Long?)?.takeIf { id -> t.tabs.any { it.id == id } }
        BrowserUiState(
            tabs = t, page = p[t.activeId] ?: PageState(), settings = s, bookmarks = b, history = h,
            nav = x[0] as NavState, media = x[1] as MediaState, blockedNavigation = x[2] as BlockedNavigation?, message = x[3] as String?,
            miniTabId = mini, miniTitle = mini?.let { id -> p[id]?.title ?: t.tabs.first { it.id == id }.title }.orEmpty(),
            watch = y[0] as com.streamshell.core.watch.WatchData, sleep = y[1] as SleepTimer, feed = y[2] as FeedState, focusSearch = y[3] as Int,
            chatPageId = y[4] as Long?, queue = y[5] as PlayQueue, player = y[6] as PlayerExtras
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BrowserUiState())

    // Declared before init: the settings collector in init uses them on its first (immediate) emission.

    /** Diagnostics log (in memory only, last [DiagnosticsLog.MAX] lines). */
    val diagnostics: DiagnosticsLog = com.streamshell.core.diag.DiagnosticsHub.log

    private val community = CommunityLookups(
        scope = viewModelScope,
        settings = { settings.value },
        runInPage = { id, script -> engine.run(id, script) },
        applySegments = { id, vid, segs -> engine.applySponsorSegments(id, vid, segs) }
    )

    init {
        MediaBridge.controls = this
        viewModelScope.launch {
            settings.collect {
                engine.setYouTubeCompact(it.youtubeCompact)
                engine.setSkipYouTubeAds(it.skipYouTubeAds)
                engine.setYouTubeAutoplay(it.autoplay)
                engine.feedPreviews = it.feedPreviews
                engine.setShortsAsVideos(it.shortsAsVideos)
                if (!it.diagnostics) diagnostics.clear()
                com.streamshell.core.diag.DiagnosticsHub.enabled = it.diagnostics
                com.streamshell.core.media.AudioCoordinator.enabled = it.yieldAudio
                com.streamshell.core.diag.DiagnosticsHub.traceEverything = it.diagnostics && it.traceEverything
                engine.trace = it.diagnostics && it.traceEverything
                val before = lastSettingsText
                lastSettingsText = it.toString()
                if (before != null) com.streamshell.core.diag.SettingsDiff.lines(before, lastSettingsText.orEmpty()).forEach { l -> trace("settings", l) }
                MediaBridge.seekSeconds = it.seekSeconds
                engine.setThirdPartyCookies(it.thirdPartyCookies)
            }
        }
        // Page extras (feed filter, hidden tiles, dislikes, DeArrow, marks, diagnostics): settings plus the watched videos.
        viewModelScope.launch {
            combine(settings, library.history) { st, h -> st to (if (st.hideWatched) watchedIds(h) else emptyList()) }
                .distinctUntilChanged()
                .collect { (st, watched) ->
                    engine.setExtras(
                        com.streamshell.core.browser.YouTubeExtras.config(
                            com.streamshell.core.browser.FeedFilter.css(st.hiddenFeedParts),
                            st.returnDislikes,
                            st.deArrow,
                            st.sponsorMarkers && st.sponsorBlock,
                            st.diagnostics,
                            trace = st.diagnostics && st.traceEverything,
                            watched = watched,
                            channels = st.blockedChannels,
                            words = st.blockedKeywords
                        )
                    )
                }
        }
        // New SponsorBlock settings apply from the next video on.
        viewModelScope.launch {
            settings.drop(1).collect {
                community.forgetSegments()
                engine.clearSponsorSegments()
            }
        }
        viewModelScope.launch { tabs.drop(1).debounce(400).collect { s -> store.edit { it[tabsKey] = TabsSerializer.encode(s) } } }
        // Once (0.3.2): positions saved before the video-id check may belong to another video (they started videos in
        // the middle) — they are cleared; Watch later, watch time and subscriptions stay.
        viewModelScope.launch {
            val flag = androidx.datastore.preferences.core.booleanPreferencesKey("positions_checked_v1")
            if (store.data.first()[flag] != true) {
                watchRepo.update { it.copy(positions = emptyList()) }
                store.edit { it[flag] = true }
            }
        }
        // Once (0.4.5): positions saved for YouTube pages without a video (feed previews under the home page) are removed.
        viewModelScope.launch {
            val flag = androidx.datastore.preferences.core.booleanPreferencesKey("positions_pages_v1")
            if (store.data.first()[flag] != true) {
                watchRepo.update { it.copy(positions = com.streamshell.core.watch.WatchRules.withoutPageEntries(it.positions)) }
                store.edit { it[flag] = true }
            }
        }
        // Once (0.3.7, privacy by default): "Show dislikes" sends the video ID to a third party, so it is off until switched
        // on again in Settings.
        viewModelScope.launch {
            val flag = androidx.datastore.preferences.core.booleanPreferencesKey("privacy_defaults_v1")
            if (store.data.first()[flag] != true) {
                settingsRepo.update { it.copy(returnDislikes = false) }
                store.edit { it[flag] = true }
            }
        }
        // Start: saved tabs and settings are read in a coroutine (no runBlocking on the main thread); the Activity shows
        // its UI once this is done (awaitReady).
        viewModelScope.launch {
            val saved = runCatching { TabsSerializer.decode(store.data.first()[tabsKey]) }.getOrDefault(TabsState.initial())
            val s0 = runCatching { settingsRepo.settings.first() }.getOrDefault(AppSettings())
            engine.setYouTubeCompact(s0.youtubeCompact)
            engine.setSkipYouTubeAds(s0.skipYouTubeAds)
            engine.setYouTubeAutoplay(s0.autoplay)
            engine.setThirdPartyCookies(s0.thirdPartyCookies)
            tabs.value = saved
            engine.deletePrivateProfile() // leftovers of private tabs from before the restart
            // YouTube instances keep their page: their video taps go to their player tab.
            saved.tabs.filter { it.instance == com.streamshell.core.browser.SiteApp.YOUTUBE.name }.forEach { engine.setRouteVideos(it.id, true) }
            val startWith = com.streamshell.core.browser.SiteApp.entries.firstOrNull { it.name == s0.defaultInstance }
            // A new start of the app opens a fresh page (YouTube home / start page), not the video from last time.
            tabs.value = TabsReducer.freshStart(tabs.value, startWith?.name, startWith?.destinations?.first()?.url)
            if (startWith != null) openSiteApp(startWith) else ensureLoaded(tabs.value.activeId)
            ready.complete(Unit)
        }
        // Mini player: only the player of that page is painted (see YouTubeSite.mini); back to normal when it leaves.
        viewModelScope.launch {
            var prev: Long? = null
            miniTab.collect { cur ->
                if (cur != prev) appLog(if (cur != null) "mini player on (tab $cur)" else "mini player off")
                prev?.takeIf { it != cur }?.let { engine.run(it, com.streamshell.core.browser.YouTubeSite.mini(false)) }
                cur?.let { engine.run(it, com.streamshell.core.browser.YouTubeSite.mini(true)) }
                prev = cur
            }
        }
        // Home-screen widget: the "Continue watching" snapshot follows the list.
        viewModelScope.launch {
            watchData.map { com.streamshell.core.watch.WatchRules.continueWatching(it.positions, com.streamshell.widget.WidgetSnapshot.MAX) }
                .distinctUntilChanged()
                .collect { runCatching { com.streamshell.widget.ContinueWidget.publish(app, it) } }
        }
        // Player prepared in advance (0.5.6): once the YouTube instance's feed has loaded, its player tab is set up in the
        // background on YouTube's home (no video streams there), so the first video switches in the page (about 1 s)
        // instead of loading a whole page (about 3 s). Once per app start.
        viewModelScope.launch {
            combine(tabs, engine.pages) { t, p -> warmCandidate(t, p) }
                .distinctUntilChanged()
                .collect { inst ->
                    if (inst != null && !playerWarmed) {
                        playerWarmed = true
                        delay(WARM_DELAY_MS)
                        prewarmPlayer(inst)
                    }
                }
        }
        // Mini player: its video's position every second (progress bar), only while it shows.
        viewModelScope.launch {
            miniTab.collectLatest { id ->
                while (id != null) {
                    engine.run(id, com.streamshell.core.media.PlayerScripts.POSITION) { r ->
                        com.streamshell.core.media.PlayerScripts.parsePosition(r)?.let { (t, d) -> player.update { it.copy(position = t, duration = d) } }
                    }
                    delay(1000)
                }
            }
        }
        // Diagnostics: fullscreen on/off.
        viewModelScope.launch {
            engine.fullscreenView.map { it != null }.distinctUntilChanged().drop(1)
                .collect { appLog(if (it) "fullscreen on" else "fullscreen off") }
        }
        // One page plays at a time: the mini player's video, else the current page (feed previews in other tabs are paused).
        viewModelScope.launch {
            combine(tabs, miniTab) { t, m -> com.streamshell.core.browser.TabsState.mediaTab(t, m) }
                .distinctUntilChanged().collect {
                    appLog("playing tab: $it (others silenced)")
                    engine.setMediaTab(it)
                }
        }
        // Media watch: what the page plays decides the notification, background visibility and auto-PiP.
        viewModelScope.launch {
            while (true) {
                delay(1500)
                if (foreground || inPip || PlaybackService.running) probe()
                // Again after a page load inside the mini player (the page lost the mark).
                miniTab.value?.let { engine.run(it, com.streamshell.core.browser.YouTubeSite.mini(true)) }
            }
        }
    }

    private val activeId get() = tabs.value.activeId

    /** The tab whose media the notification, PiP and audio focus control: the mini player if one is open. */
    private val mediaTabId get() = com.streamshell.core.browser.TabsState.mediaTab(tabs.value, miniTab.value)

    private fun probe() {
        val t = tabs.value.tabs.firstOrNull { it.id == mediaTabId } ?: return
        if (t.showingStart || t.url == null) {
            onMedia(MediaState.NONE)
            return
        }
        engine.probeMedia(t.id) { onMedia(it) }
    }

    private fun onMedia(s: MediaState) {
        trackWatching(s)
        if (s != MediaState.NONE) checkQueue()
        val bg = settings.value.backgroundPlayback
        val id = mediaTabId
        val title = engine.pages.value[id]?.title.orEmpty()
        val pageUrl = engine.pages.value[id]?.url ?: tabs.value.tabs.firstOrNull { it.id == id }?.url
        MediaBridge.update(title.ifBlank { UrlHosts.hostOf(tabs.value.tabs.firstOrNull { it.id == id }?.url).orEmpty() }, s, pageUrl)
        val want = MediaPolicy.wantsPlaybackService(bg, s, PlaybackService.running)
        engine.setKeepVisible(if (bg && (s == MediaState.PLAYING || (want && PlaybackService.running))) id else null)
        when {
            want && !PlaybackService.running && foreground -> PlaybackService.start(app)
            !want && PlaybackService.running -> PlaybackService.stop(app)
        }
    }

    // ------------------------------------------------------------ lifecycle (called by the Activity)

    fun onForeground() {
        foreground = true
        appLog("app in front")
        if (loweredQuality) {
            loweredQuality = false
            appLog("quality back to ${preferredQuality()} (was lowest in the background)")
            engine.run(mediaTabId, com.streamshell.core.media.PlayerScripts.quality(preferredQuality()))
        }
        engine.resumeViews()
        probe()
    }

    fun onBackground(isInPip: Boolean) {
        foreground = false
        appLog(if (isInPip) "app left, in picture-in-picture" else "app left (media ${engine.media.value.name.lowercase()})")
        flushWatchTime()
        if (!isInPip && settings.value.backgroundPlayback && settings.value.backgroundAudioOnly && engine.media.value == MediaState.PLAYING &&
            com.streamshell.core.browser.YouTubeSite.isYouTube(UrlHosts.hostOf(mediaUrl()))
        ) {
            loweredQuality = true
            appLog("quality lowest (audio only in the background)")
            engine.run(mediaTabId, com.streamshell.core.media.PlayerScripts.quality("tiny"))
        }
        engine.flushCookies()
        when (MediaPolicy.onLeave(isInPip, settings.value.backgroundPlayback, engine.media.value)) {
            LeaveAction.KEEP_FOR_PIP, LeaveAction.CONTINUE_IN_BACKGROUND -> Unit

            LeaveAction.PAUSE -> {
                appLog(if (engine.media.value == MediaState.PLAYING) "media paused (no background playback)" else "nothing playing, stays paused")
                engine.setKeepVisible(null)
                engine.pauseAllMedia()
                engine.pauseViews()
            }
        }
    }

    fun onPipChanged(active: Boolean) {
        if (active != inPip) appLog(if (active) "picture-in-picture on" else "picture-in-picture off")
        inPip = active
    }

    /** The user swiped the PiP window away: that means "stop", even with background playback on. */
    fun onPipDismissed() {
        inPip = false
        stop()
    }

    // ------------------------------------------------------------ MediaControls (notification, PiP buttons, audio focus)

    override fun play() {
        engine.playMedia(mediaTabId)
        viewModelScope.launch {
            delay(300)
            probe()
        }
    }
    override fun pause() {
        engine.pauseMedia(mediaTabId)
        viewModelScope.launch {
            delay(300)
            probe()
        }
    }
    override fun seekBy(seconds: Int) {
        engine.seekMedia(mediaTabId, seconds)
    }

    /**
     * Next / previous: on YouTube Music the next or previous track (the page's own media session, else its buttons);
     * elsewhere a jump of the seek step, as headset buttons did before.
     */
    override fun track(next: Boolean) {
        val tab = mediaTabId
        val music = com.streamshell.core.browser.MusicSite.isMusic(UrlHosts.hostOf(engine.pages.value[tab]?.url))
        if (music) {
            engine.mediaAction(tab, if (next) com.streamshell.core.browser.MediaSessionScripts.NEXT else com.streamshell.core.browser.MediaSessionScripts.PREVIOUS) { how ->
                appLog((if (next) "next" else "previous") + " track ($how)")
            }
        } else {
            seekBy(if (next) settings.value.seekSeconds else -settings.value.seekSeconds)
        }
    }

    override fun exit() {
        stop()
        MediaBridge.onExit?.invoke()
    }

    override fun stop() {
        engine.pauseAllMedia()
        engine.setKeepVisible(null)
        PlaybackService.stop(app)
        if (!foreground) engine.pauseViews()
    }

    fun togglePlay() = if (engine.media.value == MediaState.PLAYING) pause() else play()

    // ------------------------------------------------------------ navigation

    fun dispatch(a: NavAction) {
        trace("ui", if (a is NavAction.Open) "open ${a.overlay.name}" else "close overlay")
        nav.update { NavReducer.reduce(it, a) }
    }

    /** The user's arrangement of a site app's bottom bar (items and order). */
    fun setSiteBar(app: com.streamshell.core.browser.SiteApp, keys: List<String>) {
        viewModelScope.launch { settingsRepo.update { it.copy(siteBars = it.siteBars + (app.name to keys)) } }
    }

    private val recentKey = stringPreferencesKey("recent_searches")

    private suspend fun recentSearches(): List<String> = runCatching {
        val a = org.json.JSONArray(store.data.first()[recentKey] ?: "[]")
        (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    /** Recent searches (empty field) or recent + YouTube's suggestions for [query]; never throws. */
    suspend fun searchSuggestions(query: String): List<String> {
        val recent = recentSearches()
        // Without the switch, nothing typed leaves the device: only your own recent searches are offered.
        if (query.isBlank() || !settings.value.searchSuggestions) return com.streamshell.core.browser.SearchSuggestions.merge(query, recent, emptyList())
        val remote = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.streamshell.core.browser.SearchSuggestions.fetch(query, java.util.Locale.getDefault().language) }.getOrDefault(emptyList())
        }
        return com.streamshell.core.browser.SearchSuggestions.merge(query, recent, remote)
    }

    fun clearRecentSearches() {
        viewModelScope.launch { store.edit { it.remove(recentKey) } }
    }

    fun searchInSite(query: String) {
        val app = state.value.siteApp ?: return
        if (query.isBlank()) return
        if (!isPrivate(activeId)) {
            viewModelScope.launch {
                store.edit { p ->
                    val cur = runCatching { org.json.JSONArray(p[recentKey] ?: "[]").let { a -> (0 until a.length()).map { a.optString(it) } } }.getOrDefault(emptyList())
                    p[recentKey] = org.json.JSONArray(com.streamshell.core.browser.SearchSuggestions.remember(cur, query)).toString()
                }
            }
        }
        open(app.searchUrlFor(query))
    }

    fun submitAddress(text: String) {
        // On YouTube, words typed into the bar search YouTube (its own search button is hidden in compact mode).
        val engineFor = com.streamshell.core.browser.YouTubeSite.searchEngineFor(state.value.onYouTube, settings.value.searchEngine)
        when (val t = AddressInput.resolve(text, engineFor)) {
            is AddressTarget.Url -> open(t.url)
            is AddressTarget.Search -> open(t.url)
            AddressTarget.Empty -> Unit
        }
    }

    /** Opens [url] in the current tab and closes any panel. */
    fun open(url: String) {
        val id = activeId
        val fromStart = tabs.value.active.showingStart || tabs.value.active.url == null
        nav.value = NavState()
        // A blocked or external target leaves the tab exactly as it was.
        if (engine.load(id, url) != BrowserEngine.LoadResult.STARTED) return
        tabs.update { s -> TabsReducer.update(s, id) { it.copy(url = url, showingStart = false, openedFromStart = if (fromStart) true else it.openedFromStart) } }
    }

    fun openInNewTab(url: String) {
        val before = tabs.value
        val after = TabsReducer.newTab(before)
        if (after === before) {
            message.value = "At most ${TabsReducer.MAX_TABS} tabs. Close one first."
            return
        }
        engine.pauseMedia(before.activeId)
        tabs.value = after
        open(url)
    }

    private fun ensureLoaded(id: Long) {
        val t = tabs.value.tabs.firstOrNull { it.id == id } ?: return
        if (!t.showingStart && t.url != null && !engine.hasView(id)) engine.load(id, t.url)
    }

    fun back() {
        val s = state.value
        if (s.page.canGoBack && !s.showingStart) engine.goBack(activeId) else showStart()
    }

    fun forward() {
        val s = state.value
        if (s.showingStart && s.active.url != null) {
            tabs.update { TabsReducer.update(it, activeId) { t -> t.copy(showingStart = false) } }
            ensureLoaded(activeId)
        } else {
            engine.goForward(activeId)
        }
    }

    fun showStart() {
        engine.pauseMedia(activeId)
        tabs.update { TabsReducer.update(it, activeId) { t -> t.copy(showingStart = true) } }
        nav.value = NavState()
    }

    fun reload() = engine.reload(activeId)
    fun stopLoading() = engine.stopLoading(activeId)

    /** Private tab: nothing about it is remembered (see [com.streamshell.core.browser.BrowserTab.private]). */
    private fun isPrivate(id: Long) = tabs.value.tabs.any { it.id == id && it.private }

    fun newPrivateTab() {
        val before = tabs.value
        val after = TabsReducer.newPrivateTab(before)
        if (after === before) {
            message.value = "At most ${TabsReducer.MAX_TABS} tabs. Close one first."
            return
        }
        engine.markPrivate(after.activeId)
        engine.pauseMedia(before.activeId)
        tabs.value = after
        nav.value = NavState()
        message.value = if (engine.privateProfileSupported) "Private tab: own cookies, nothing is remembered" else "Private tab: nothing is remembered (cookies are shared on this Android version)"
    }

    fun newTab() {
        val before = tabs.value
        val after = TabsReducer.newTab(before)
        if (after === before) {
            message.value = "At most ${TabsReducer.MAX_TABS} tabs. Close one first."
            return
        }
        engine.pauseMedia(before.activeId)
        tabs.value = after
        nav.value = NavState()
    }

    // ------------------------------------------------------------ mini player

    private companion object {
        /** Page id of the live chat window (outside the tab ids, which count up from 1). */
        const val CHAT_PAGE_ID = Long.MAX_VALUE - 8
        const val MINI_ANIMATION_MS = 420L
        const val BREAK_RESET_MS = 5 * 60_000L
        const val FEED_CHANNELS = 80
    }

    /**
     * Shrinks the current video into the floating mini player and opens the site's home in a new tab, so
     * you can keep browsing while it plays. The video stays in its own tab (nothing is re-loaded).
     */
    fun isPageAtTop(id: Long): Boolean = engine.isAtTop(id)

    fun showMessage(text: String) {
        message.value = text
    }

    fun fullscreenTap(x: Float, y: Float) {
        engine.fullscreenTap(x, y, state.value.page.chapters.map { it.title })
    }

    fun seekTo(seconds: Int) {
        engine.run(mediaTabId, com.streamshell.core.browser.YouTubeTimestamps.seekScript(seconds))
    }

    fun queryPosition(onResult: (Double) -> Unit) {
        engine.run(mediaTabId, com.streamshell.core.media.PlayerScripts.POSITION) { r ->
            com.streamshell.core.media.PlayerScripts.parsePosition(r)?.let { onResult(it.first) }
        }
    }

    fun minimize() {
        val s = state.value
        if (!s.canMinimize) return
        val videoTab = activeId
        // Player tab of an instance: shrink the video and show the instance exactly as it was (nothing reloads).
        val opener = s.active.playerFor?.takeIf { id -> tabs.value.tabs.any { it.id == id } }
        if (opener != null) {
            miniTab.value = videoTab
            tabs.update { TabsReducer.select(it, opener) }
            nav.value = NavState()
            ensureLoaded(opener)
            return
        }
        // An ordinary YouTube tab: hand the video to the site's instance (reused, never a new tab per minimize).
        val app = s.siteApp ?: com.streamshell.core.browser.SiteApp.YOUTUBE
        val inst = TabsReducer.instanceTab(tabs.value, app.name)
        if (inst != null && inst.id != videoTab) {
            val oldPlayer = TabsReducer.playerTabOf(tabs.value, inst.id)?.id?.takeIf { it != videoTab }
            tabs.update { st -> TabsReducer.update(st, videoTab) { it.copy(playerFor = inst.id) } }
            if (oldPlayer != null) closeTabQuietly(oldPlayer)
            miniTab.value = videoTab
            tabs.update { TabsReducer.select(it, inst.id) }
            nav.value = NavState()
            ensureLoaded(inst.id)
            return
        }
        val home = app.destinations.first().url
        // The new tab shows YouTube's home at once (never the StreamShell start page in between).
        val after = TabsReducer.newTab(tabs.value, home)
        if (after === tabs.value) {
            message.value = "At most ${TabsReducer.MAX_TABS} tabs. Close one to use the mini player."
            return
        }
        miniTab.value = videoTab
        val newTab = after.activeId
        // The new tab becomes the site's instance and this video its player, so later videos reuse both.
        tabs.value = TabsReducer.update(TabsReducer.update(after, newTab) { it.copy(instance = app.name) }, videoTab) { it.copy(playerFor = newTab) }
        if (app == com.streamshell.core.browser.SiteApp.YOUTUBE) engine.setRouteVideos(newTab, true)
        // Let the shrink animation run before the new page starts loading (loading competes for the main thread).
        viewModelScope.launch {
            delay(MINI_ANIMATION_MS)
            if (activeId == newTab) open(home)
        }
    }

    /** Back to the full video page. */
    fun restoreMini() {
        val id = miniTab.value ?: return
        // Select the video's tab first, then clear the mini player: the other way round the feed tab was the playing tab
        // for a moment and the video was paused (often for seconds; seen in the 0.4.3 log).
        tabs.update { TabsReducer.select(it, id) }
        miniTab.value = null
        nav.value = NavState()
    }

    /** Stop and close the mini player; its tab stays in the tab list. */
    fun closeMini() {
        val id = miniTab.value ?: return
        appLog("mini player closed (X)")
        engine.pauseMedia(id)
        miniTab.value = null
    }

    fun selectTab(id: Long) {
        if (id == miniTab.value) {
            restoreMini()
            return
        }
        if (id != activeId) engine.pauseMedia(activeId)
        tabs.update { TabsReducer.select(it, id) }
        nav.value = NavState()
        ensureLoaded(id)
    }

    fun closeTab(id: Long) {
        if (id == miniTab.value) miniTab.value = null
        // A closed instance leaves its player as a normal tab.
        tabs.update { s -> s.copy(tabs = s.tabs.map { if (it.playerFor == id) it.copy(playerFor = null) else it }) }
        engine.close(id)
        tabs.update { TabsReducer.close(it, id) }
        ensureLoaded(activeId)
    }

    /** Closes a tab that is not on screen (an old player replaced by a newer one). */
    private fun closeTabQuietly(id: Long) {
        if (id == activeId || id == miniTab.value) return
        engine.close(id)
        tabs.update { TabsReducer.close(it, id) }
    }

    fun closeAllTabs() {
        miniTab.value = null
        tabs.value.tabs.forEach { engine.close(it.id) }
        tabs.update { TabsReducer.closeAll(it) }
        nav.value = NavState()
    }

    // ------------------------------------------------------------ watching: resume, speed, sound, time, sleep

    @Volatile private var loweredQuality = false
    private var lastPlayingAt = 0L
    private var lastActiveAt = 0L
    private var pendingWatchMs = 0L
    private var sessionMs = 0L
    private var reminded = false
    private var lastPositionAt = 0L
    private var handledKey: String? = null
    private var lastKey: String? = null

    private var playerWarmed = false

    /** The tab prepared by [prewarmPlayer] and whether it has been used for a video (for the log). */
    private var warmTab: Long? = null
    private var warmUsed = false

    /** The YouTube instance whose feed has loaded and that has no player tab yet (not private), else null. */
    private fun warmCandidate(t: TabsState, p: Map<Long, PageState>): Long? {
        val inst = TabsReducer.instanceTab(t, com.streamshell.core.browser.SiteApp.YOUTUBE.name) ?: return null
        val loaded = inst.url != null && p[inst.id]?.let { !it.loading && it.url != null } == true
        return inst.id.takeIf { loaded && !inst.private && TabsReducer.playerTabOf(t, inst.id) == null }
    }

    private fun prewarmPlayer(instanceId: Long) {
        val s = tabs.value
        if (warmCandidate(s, engine.pages.value) != instanceId) return
        val home = com.streamshell.core.browser.SiteApp.YOUTUBE.destinations.first().url
        val (ns, id) = TabsReducer.newBackgroundTab(s) { it.copy(url = home, playerFor = instanceId, showingStart = false, openedFromStart = false) }
        if (id == null) return
        tabs.value = ns
        engine.markWarm(id)
        warmTab = id
        engine.load(id, home)
        appLog("player prepared in the background (tab $id)")
    }

    /** Right after a video opens: a few quick checks (instead of the next 1.5 s one), so "player ready" comes sooner. */
    private fun probeSoon() {
        probeSoonJob?.cancel()
        probeSoonJob = viewModelScope.launch {
            repeat(FAST_PROBES) {
                delay(FAST_PROBE_MS)
                probe()
            }
        }
    }
    private var probeSoonJob: kotlinx.coroutines.Job? = null

    /** Video IDs of watched videos (history, newest first) for "Hide watched videos". */
    private fun watchedIds(h: List<com.streamshell.core.library.HistoryEntry>): List<String> = h.mapNotNull { com.streamshell.core.sponsor.SponsorBlockApi.videoIdOf(it.url) }.distinct().take(com.streamshell.core.browser.YouTubeExtras.MAX_WATCHED)

    private fun mediaUrl(): String? = engine.pages.value[mediaTabId]?.url ?: tabs.value.tabs.firstOrNull { it.id == mediaTabId }?.url

    private fun trackWatching(s: MediaState) {
        val now = android.os.SystemClock.elapsedRealtime()
        val st = settings.value
        if (s == MediaState.PLAYING) {
            if (lastPlayingAt > 0) {
                val d = (now - lastPlayingAt).coerceIn(0, 5000)
                if (!isPrivate(mediaTabId)) pendingWatchMs += d
                sessionMs += d
            } else if (now - lastActiveAt > BREAK_RESET_MS) {
                sessionMs = 0
                reminded = false
            }
            lastPlayingAt = now
            lastActiveAt = now
        } else {
            lastPlayingAt = 0
        }
        if (pendingWatchMs >= 30_000) flushWatchTime()
        // Break reminder after N minutes of (almost) continuous watching.
        if (st.breakReminderMinutes > 0 && !reminded && sessionMs >= st.breakReminderMinutes * 60_000L) {
            reminded = true
            remind("You've been watching for ${st.breakReminderMinutes} minutes. Time for a short break?")
        }
        val url = mediaUrl() ?: return
        val key = com.streamshell.core.watch.WatchRules.keyOf(url)
        // Sleep timer "end of video": the next video starting means this one ended.
        if (s == MediaState.PLAYING && sleep.value.atEndOfVideo && sleepKey != null && key != sleepKey) {
            pause()
            sleep.value = SleepTimer()
            appLog("sleep timer: paused")
            message.value = "Sleep timer: playback stopped"
        }
        if (s != MediaState.PLAYING) return
        if (key != lastKey) {
            lastKey = key
        }
        // Once per video and tab, after any ad: speed, sound, resume. Per tab: a video handed on to the player tab (0.4.3
        // log: it started there at 0) is a new start there.
        val tabNow = mediaTabId
        val handled = "$tabNow|$key"
        if (handledKey != handled) {
            engine.run(tabNow, com.streamshell.core.media.PlayerScripts.POSITION) { r ->
                val pos = com.streamshell.core.media.PlayerScripts.parsePositionAndId(r) ?: return@run // ad or no video yet
                // The player still plays the previous video right after a switch: wait for this one.
                if (!com.streamshell.core.media.PlayerScripts.belongsTo(pos.third, url)) return@run
                if (handledKey == handled || mediaTabId != tabNow) return@run
                handledKey = handled
                applyPlayerSettings()
                onNewVideo(tabNow, st)
                if (sleep.value.atEndOfVideo && sleepKey == null) sleepKey = key
                if (st.resumePlayback) {
                    val d = com.streamshell.core.watch.WatchRules.resumeDecision(watchData.value.positions, url, pos.first)
                    if (d.at != null) {
                        engine.run(tabNow, com.streamshell.core.media.PlayerScripts.seekTo(d.at))
                        appLog("resume: continue at ${d.at.toInt()} s")
                        message.value = "Continuing at ${com.streamshell.core.watch.WatchRules.formatClock(d.at)}"
                    } else {
                        appLog("resume: none (${d.reason})")
                    }
                }
            }
        }
        // Sleep timer "end of video": checked on every probe (1.5 s), whether or not positions are remembered.
        if (sleep.value.atEndOfVideo) {
            engine.run(mediaTabId, com.streamshell.core.media.PlayerScripts.POSITION) { r ->
                val (t, d, playing) = com.streamshell.core.media.PlayerScripts.parsePositionAndId(r) ?: return@run
                if (!com.streamshell.core.media.PlayerScripts.belongsTo(playing, url)) return@run
                if (sleep.value.atEndOfVideo && d - t < 2.0) {
                    pause()
                    sleep.value = SleepTimer()
                    appLog("sleep timer: paused")
                    message.value = "Sleep timer: playback stopped"
                }
            }
        }
        // Remember the position every 5 s.
        if (st.resumePlayback && now - lastPositionAt >= 5000 && !isPrivate(mediaTabId)) {
            lastPositionAt = now
            val title = engine.pages.value[mediaTabId]?.title.orEmpty()
            engine.run(mediaTabId, com.streamshell.core.media.PlayerScripts.POSITION) { r ->
                val (t, d, playing) = com.streamshell.core.media.PlayerScripts.parsePositionAndId(r) ?: return@run
                // Never store the previous video's position under this video's address.
                if (!com.streamshell.core.media.PlayerScripts.belongsTo(playing, url)) return@run
                viewModelScope.launch { watchRepo.update { w -> w.copy(positions = com.streamshell.core.watch.WatchRules.record(w.positions, url, title, t, d, System.currentTimeMillis())) } }
            }
        }
    }

    /** The quality chosen for the current connection (mobile data = metered). */
    private fun preferredQuality(): String {
        val metered = runCatching { app.getSystemService(android.net.ConnectivityManager::class.java)?.isActiveNetworkMetered }.getOrNull() ?: false
        return if (metered) settings.value.qualityMobile else settings.value.qualityWifi
    }

    private fun applyPlayerSettings() {
        val st = settings.value
        if (com.streamshell.core.browser.YouTubeSite.isYouTube(UrlHosts.hostOf(mediaUrl())) && !loweredQuality) {
            val q = preferredQuality()
            if (q != "auto") {
                appLog("quality requested: $q")
                engine.run(mediaTabId, com.streamshell.core.media.PlayerScripts.quality(q))
            }
        }
        if (st.playbackSpeed != 100 || speedApplied != 100) engine.runEverywhere(com.streamshell.core.media.PlayerScripts.speed(st.playbackSpeed))
        speedApplied = st.playbackSpeed
        if (com.streamshell.core.browser.YouTubeSite.isYouTube(UrlHosts.hostOf(mediaUrl())) && (st.volumeBoost != 100 || st.normalizeVolume || audioApplied)) {
            audioApplied = true
            engine.run(mediaTabId, com.streamshell.core.media.PlayerScripts.audio(st.volumeBoost, st.normalizeVolume))
        }
    }
    private var speedApplied = 100
    private var audioApplied = false

    /** A new video started in [tab]: channel (speed per channel, watch time), captions; repeat starts off. */
    private fun onNewVideo(tab: Long, st: AppSettings) {
        // YouTube reuses its video element: a repeat set for the previous video would carry over to this one.
        val prev = player.value
        if (prev.loop || prev.repeatFrom != null) engine.run(tab, com.streamshell.core.media.PlayerScripts.repeat(false, null, null))
        val vid = com.streamshell.core.sponsor.SponsorBlockApi.videoIdOf(mediaUrl()).orEmpty()
        player.update { PlayerExtras(readyVideoId = vid) }
        val yt = com.streamshell.core.browser.YouTubeSite.isYouTube(UrlHosts.hostOf(mediaUrl()))
        if (!yt) return
        appLog("player ready: $vid (loading band off)")
        openedAt.remove(tab)?.let { at -> appLog("start-up: ${System.currentTimeMillis() - at} ms (tap to playing)") }
        if (st.captions != "default") {
            engine.run(tab, com.streamshell.core.media.PlayerScripts.captions(st.captions, st.captionsLanguage))
            appLog("captions ${st.captions}${st.captionsLanguage.takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty()}")
        }
        engine.run(tab, com.streamshell.core.media.PlayerScripts.CHANNEL) { r ->
            val ch = jsString(r).trim().take(100)
            if (mediaTabId != tab) return@run
            player.update { it.copy(channel = ch) }
            val sp = com.streamshell.core.watch.ChannelRules.speedOf(watchData.value.channelSpeeds, ch)
            when {
                sp != null -> {
                    engine.run(tab, com.streamshell.core.media.PlayerScripts.speed(sp))
                    channelSpeedTabs += tab
                    appLog("speed $sp % (remembered for this channel)")
                }

                tab in channelSpeedTabs -> {
                    channelSpeedTabs -= tab
                    engine.run(tab, com.streamshell.core.media.PlayerScripts.speed(settings.value.playbackSpeed))
                }
            }
        }
    }

    /** A JavaScript string result of evaluateJavascript ("\"text\"", JSON-encoded) as plain text. */
    private fun jsString(raw: String?): String = runCatching { org.json.JSONArray("[" + (raw ?: "\"\"") + "]").optString(0) }.getOrDefault("")

    // ------------------------------------------------------------ player extras (repeat, chapters, zoom, channel)

    fun setRepeatVideo(on: Boolean) {
        player.update { it.copy(loop = on) }
        applyRepeat()
        message.value = if (on) "Repeating this video" else "Repeat off"
    }

    /** Marks the start (first call) or end (second call) of the part to repeat; a third call clears it. */
    fun markRepeat() {
        queryPosition { t ->
            val p = player.value
            when {
                p.repeatFrom == null || p.repeatTo != null -> {
                    player.update { it.copy(repeatFrom = t, repeatTo = null) }
                    message.value = "Repeat from ${com.streamshell.core.watch.WatchRules.formatClock(t)} — tap again at the end"
                }

                t > (p.repeatFrom) + 1 -> {
                    player.update { it.copy(repeatTo = t) }
                    message.value = "Repeating ${com.streamshell.core.watch.WatchRules.formatClock(p.repeatFrom)}–${com.streamshell.core.watch.WatchRules.formatClock(t)}"
                }

                else -> message.value = "The end must be after the start"
            }
            applyRepeat()
        }
    }

    fun clearRepeatPart() {
        player.update { it.copy(repeatFrom = null, repeatTo = null) }
        applyRepeat()
    }

    private fun applyRepeat() {
        val p = player.value
        engine.run(mediaTabId, com.streamshell.core.media.PlayerScripts.repeat(p.loop, p.repeatFrom, p.repeatTo))
    }

    /** Jumps to the start of the next chapter (the chapters YouTube shows for this video). */
    fun nextChapter() {
        val chapters = engine.pages.value[mediaTabId]?.chapters.orEmpty()
        if (chapters.size < 2) return
        queryPosition { t ->
            val next = com.streamshell.core.browser.ChapterRules.next(chapters, t)
            if (next == null) {
                message.value = "This is the last chapter"
            } else {
                seekTo(next.start)
                message.value = next.title
            }
        }
    }

    fun setVideoZoom(fill: Boolean) {
        engine.run(mediaTabId, com.streamshell.core.media.PlayerScripts.zoom(fill))
        message.value = if (fill) "Zoomed to fill" else "Whole picture"
    }

    /** Remembers the current speed for this video's channel (or forgets it). */
    fun setChannelSpeed(remember: Boolean) {
        val ch = player.value.channel.takeIf { it.isNotBlank() } ?: return
        val sp = settings.value.playbackSpeed
        viewModelScope.launch { watchRepo.update { w -> w.copy(channelSpeeds = com.streamshell.core.watch.ChannelRules.setSpeed(w.channelSpeeds, ch, if (remember) sp else null)) } }
        if (remember) channelSpeedTabs += mediaTabId else channelSpeedTabs -= mediaTabId
        message.value = if (remember) "$ch: always ${sp / 100.0}x".replace(".0x", "x") else "$ch: normal speed"
    }

    /** Hides this video's channel in the feeds (Settings › YouTube › Blocked channels). */
    fun blockCurrentChannel() {
        val ch = player.value.channel.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch { settingsRepo.update { it.copy(blockedChannels = it.blockedChannels + ch) } }
        message.value = "$ch is hidden in your feeds"
    }

    // ------------------------------------------------------------ playlists (local)

    fun createPlaylist(name: String) {
        viewModelScope.launch { watchRepo.update { w -> w.copy(playlists = com.streamshell.core.watch.Playlists.create(w.playlists, name)) } }
    }

    fun renamePlaylist(from: String, to: String) {
        viewModelScope.launch { watchRepo.update { w -> w.copy(playlists = com.streamshell.core.watch.Playlists.rename(w.playlists, from, to)) } }
    }

    fun deletePlaylist(name: String) {
        viewModelScope.launch { watchRepo.update { w -> w.copy(playlists = com.streamshell.core.watch.Playlists.delete(w.playlists, name)) } }
    }

    /** Adds the current video to [name] (created when new). */
    fun addToPlaylist(name: String) {
        val s = state.value
        val url = s.currentUrl?.takeIf { s.isVideoPage } ?: return
        val clean = com.streamshell.core.watch.Playlists.clean(name) ?: return
        viewModelScope.launch {
            watchRepo.update { w -> w.copy(playlists = com.streamshell.core.watch.Playlists.add(w.playlists, clean, url, s.page.title, System.currentTimeMillis())) }
            message.value = "Added to $clean"
        }
    }

    fun removeFromPlaylist(name: String, index: Int) {
        viewModelScope.launch { watchRepo.update { w -> w.copy(playlists = com.streamshell.core.watch.Playlists.remove(w.playlists, name, index)) } }
    }

    fun movePlaylistItem(name: String, index: Int, delta: Int) {
        viewModelScope.launch { watchRepo.update { w -> w.copy(playlists = com.streamshell.core.watch.Playlists.move(w.playlists, name, index, delta)) } }
    }

    /** Plays the list from [from] on, one video after another (the queue). */
    fun playPlaylist(name: String, from: Int = 0) {
        val items = watchData.value.playlists.firstOrNull { it.name == name }?.items.orEmpty().map { QueueItem(it.url, it.title) }.take(QueueRules.MAX)
        if (items.isEmpty()) return
        val start = from.coerceIn(0, items.lastIndex)
        queue.value = PlayQueue(items, start)
        queueAdvanced = null
        message.value = "Playing $name"
        openVideo(items[start].url)
    }

    private fun flushWatchTime() {
        val ms = pendingWatchMs
        if (ms < 1000) return
        pendingWatchMs = 0
        val day = java.time.LocalDate.now().toString()
        val channel = player.value.channel
        viewModelScope.launch {
            watchRepo.update {
                it.copy(
                    stats = com.streamshell.core.watch.WatchRules.addWatchTime(it.stats, day, ms / 1000),
                    channelStats = com.streamshell.core.watch.ChannelRules.addTime(it.channelStats, channel, ms / 1000)
                )
            }
        }
    }

    private fun remind(text: String) {
        message.value = text
        if (!foreground) Reminders.show(app, text)
    }

    fun setPlaybackSpeed(percent: Int) {
        viewModelScope.launch { settingsRepo.update { it.copy(playbackSpeed = percent) } }
        engine.runEverywhere(com.streamshell.core.media.PlayerScripts.speed(percent))
        speedApplied = percent
        message.value = "Speed ${percent / 100.0}x".replace(".0x", "x")
    }

    /** The ± jump with the chosen length (Settings › Playback). */
    fun seekStep(forward: Boolean) = settings.value.seekSeconds.let { seekBy(if (forward) it else -it) }

    // ------------------------------------------------------------ queue (play one video after another)

    fun playAllWatchLater() {
        val items = watchData.value.later.map { QueueItem(it.url, it.title) }.take(QueueRules.MAX)
        if (items.isEmpty()) return
        queue.value = PlayQueue(items, 0)
        queueAdvanced = null
        message.value = "Playing ${items.size} videos one after another"
        openVideo(items[0].url)
    }

    fun addToQueue() {
        val url = state.value.currentUrl ?: return
        if (com.streamshell.core.sponsor.SponsorBlockApi.videoIdOf(url) == null) return
        val before = queue.value
        val after = QueueRules.add(before, QueueItem(url, state.value.page.title))
        queue.value = after
        message.value = if (after === before) "Already in the queue" else "Added to the queue (${after.items.size})"
    }

    fun playQueueFrom(index: Int) {
        val q = queue.value
        val item = q.items.getOrNull(index) ?: return
        queue.value = q.copy(pos = index)
        queueAdvanced = null
        openVideo(item.url)
    }

    fun removeFromQueue(index: Int) {
        queue.value = QueueRules.remove(queue.value, index)
    }
    fun clearQueue() {
        queue.value = PlayQueue()
        message.value = "Queue cleared"
    }

    private var queueAdvanced: String? = null

    /** When the queue's current video reaches its end, the next one opens (also before YouTube's own autoplay). */
    private fun checkQueue() {
        val q = queue.value
        val cur = q.current ?: return
        engine.run(mediaTabId, com.streamshell.core.media.PlayerScripts.POSITION) { r ->
            val (t, d, playing) = com.streamshell.core.media.PlayerScripts.parsePositionAndId(r) ?: return@run
            if (!com.streamshell.core.media.PlayerScripts.belongsTo(playing, cur.url) || d - t > 1.2) return@run
            val key = com.streamshell.core.watch.WatchRules.keyOf(cur.url)
            if (queueAdvanced == key || queue.value.current != cur) return@run
            queueAdvanced = key
            val (nq, next) = QueueRules.next(queue.value)
            queue.value = nq
            if (next != null) {
                appLog("queue: next video")
                message.value = "Next: ${next.title.ifBlank { "video" }}"
                openVideo(next.url)
            } else {
                message.value = "Queue finished"
            }
        }
    }

    // ------------------------------------------------------------ channel groups

    fun setChannelGroup(channelId: String, group: String?) {
        viewModelScope.launch { watchRepo.update { w -> w.copy(groups = com.streamshell.core.watch.ChannelGroups.assign(w.groups, channelId, group)) } }
    }

    fun startOver() {
        val url = mediaUrl() ?: return
        engine.run(mediaTabId, com.streamshell.core.media.PlayerScripts.seekTo(0.0))
        viewModelScope.launch { watchRepo.update { w -> w.copy(positions = w.positions.filterNot { it.key == com.streamshell.core.watch.WatchRules.keyOf(url) }) } }
    }

    private var sleepJob: kotlinx.coroutines.Job? = null
    private var sleepKey: String? = null

    /** [minutes] > 0: pause after that time; 0: at the end of the current video; null: off. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        sleepKey = null
        when {
            minutes == null -> {
                sleep.value = SleepTimer()
                message.value = "Sleep timer off"
            }

            minutes == 0 -> {
                sleep.value = SleepTimer(atEndOfVideo = true)
                sleepKey = mediaUrl()?.let { com.streamshell.core.watch.WatchRules.keyOf(it) }
                message.value = "Playback stops at the end of this video"
            }

            else -> {
                val end = System.currentTimeMillis() + minutes * 60_000L
                sleep.value = SleepTimer(endsAt = end, minutes = minutes)
                message.value = "Playback stops in $minutes minutes"
                sleepJob = viewModelScope.launch {
                    delay(minutes * 60_000L)
                    if (sleep.value.endsAt == end) {
                        pause()
                        sleep.value = SleepTimer()
                        appLog("sleep timer: paused")
                        message.value = "Sleep timer: playback stopped"
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------ watch later, continue watching

    fun toggleWatchLater() {
        val url = state.value.currentUrl ?: return
        val title = state.value.page.title
        val adding = !state.value.isWatchLater
        viewModelScope.launch { watchRepo.update { it.copy(later = com.streamshell.core.watch.WatchRules.toggleLater(it.later, url, title, System.currentTimeMillis())) } }
        message.value = if (adding) "Saved to Watch later" else "Removed from Watch later"
    }

    fun removeWatchLater(url: String) {
        viewModelScope.launch { watchRepo.update { w -> w.copy(later = w.later.filterNot { com.streamshell.core.watch.WatchRules.keyOf(it.url) == com.streamshell.core.watch.WatchRules.keyOf(url) }) } }
    }

    fun removePosition(key: String) {
        viewModelScope.launch { watchRepo.update { w -> w.copy(positions = w.positions.filterNot { it.key == key }) } }
    }

    /** Opens a remembered video: YouTube videos in the YouTube instance's player, others in the current tab. */
    fun openVideo(url: String) {
        nav.value = NavState()
        if (com.streamshell.core.browser.YouTubeSite.isYouTube(UrlHosts.hostOf(url)) && com.streamshell.core.sponsor.SponsorBlockApi.videoIdOf(url) != null) {
            openSiteApp(com.streamshell.core.browser.SiteApp.YOUTUBE)
            val inst = TabsReducer.instanceTab(tabs.value, com.streamshell.core.browser.SiteApp.YOUTUBE.name) ?: return
            onOpenVideo(inst.id, url)
        } else {
            open(url)
        }
    }

    /** Launcher shortcut "Continue watching": the most recent video, or the Watch later list. */
    fun continueLatest() {
        val last = state.value.continueWatching.firstOrNull()
        if (last != null) openVideo(last.url) else nav.value = NavState(Overlay.WatchLater)
    }

    /** Launcher shortcut "Search": the start page with its search field focused. */
    fun requestSearch() {
        nav.value = NavState()
        val plain = tabs.value.tabs.firstOrNull { it.instance == null && it.playerFor == null && it.id != miniTab.value }
        if (plain != null) {
            selectTab(plain.id)
            showStart()
        } else {
            newTab()
        }
        focusSearch.update { it + 1 }
    }

    // ------------------------------------------------------------ subscriptions (Google Takeout + public feeds)

    fun importSubscriptions(csv: String) {
        val channels = com.streamshell.core.watch.SubscriptionImport.parse(csv)
        if (channels.isEmpty()) {
            message.value = "No channels found. Choose subscriptions.csv (Google Takeout) or subscriptions.json (NewPipe)."
            return
        }
        viewModelScope.launch {
            watchRepo.update { w -> w.copy(channels = (channels + w.channels).distinctBy { it.id }.take(com.streamshell.core.watch.WatchRules.MAX_CHANNELS)) }
            message.value = "Imported ${channels.size} channels"
            refreshFeed()
        }
    }

    fun removeChannel(id: String) {
        viewModelScope.launch { watchRepo.update { w -> w.copy(channels = w.channels.filterNot { it.id == id }) } }
    }

    fun openChannel(c: com.streamshell.core.watch.Channel) {
        nav.value = NavState()
        openSiteApp(com.streamshell.core.browser.SiteApp.YOUTUBE)
        open(c.url)
    }

    /** Loads the newest videos of up to [FEED_CHANNELS] imported channels from YouTube's public feeds. */
    fun refreshFeed() {
        if (feed.value.loading) return
        val channels = watchData.value.channels.take(FEED_CHANNELS)
        if (channels.isEmpty()) return
        feed.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val sem = kotlinx.coroutines.sync.Semaphore(6)
            val results = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                channels.map { c ->
                    async { sem.withPermit { runCatching { com.streamshell.core.watch.ChannelFeed.parse(FeedFetcher.fetch(com.streamshell.core.watch.ChannelFeed.urlOf(c.id))).map { it.copy(channelId = c.id) } }.getOrNull() } }
                }.map { it.await() }
            }
            val ok = results.filterNotNull()
            feed.value = FeedState(
                videos = com.streamshell.core.watch.ChannelFeed.merge(ok),
                error = if (ok.isEmpty()) "Could not load the channels' feeds. Check the connection." else null,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    // ------------------------------------------------------------ backup

    suspend fun exportBackup(): String = com.streamshell.core.watch.Backup.encode(
        com.streamshell.core.watch.BackupContent(state.value.bookmarks, state.value.history, watchData.value, settings.value)
    )

    fun importBackup(text: String) {
        viewModelScope.launch {
            val c = runCatching { com.streamshell.core.watch.Backup.decode(text, settings.value) }.getOrElse {
                message.value = it.message ?: "This is not a StreamShell backup file."
                return@launch
            }
            library.replaceAll(c.bookmarks, c.history)
            watchRepo.update { c.watch }
            settingsRepo.update { c.settings }
            message.value = "Backup restored: ${c.bookmarks.size} bookmarks, ${c.watch.later.size} in Watch later, ${c.watch.channels.size} channels"
        }
    }

    // ------------------------------------------------------------ site instances

    /**
     * Opens a site app (YouTube, YouTube Music) as its persistent instance: if one exists it is shown exactly
     * as you left it (no reload); otherwise the current empty tab (or a new tab) becomes the instance.
     */
    fun openSiteApp(app: com.streamshell.core.browser.SiteApp) {
        val s = tabs.value
        TabsReducer.instanceTab(s, app.name)?.let {
            selectTab(it.id)
            return
        }
        val cur = s.active
        val target = if ((cur.showingStart || cur.url == null) && cur.instance == null && cur.playerFor == null) {
            cur.id
        } else {
            val after = TabsReducer.newTab(s)
            if (after === s) {
                message.value = "At most ${TabsReducer.MAX_TABS} tabs. Close one first."
                return
            }
            engine.pauseMedia(s.activeId)
            tabs.value = after
            after.activeId
        }
        tabs.update { TabsReducer.update(it, target) { t -> t.copy(instance = app.name) } }
        if (app == com.streamshell.core.browser.SiteApp.YOUTUBE) engine.setRouteVideos(target, true)
        open(app.destinations.first().url)
    }

    /** Start page tiles: site apps open as their instance, everything else in the current tab. */
    fun openFromStart(url: String) {
        val app = com.streamshell.core.browser.SiteApp.of(UrlHosts.hostOf(url))
        if (app != null && app.destinations.first().url == url) openSiteApp(app) else open(url)
    }

    /** Switch between instances (null = StreamShell start page) from the menu. */
    fun switchInstance(app: com.streamshell.core.browser.SiteApp?) {
        if (app != null) {
            openSiteApp(app)
            return
        }
        val plain = tabs.value.tabs.firstOrNull { it.instance == null && it.playerFor == null && it.id != miniTab.value }
        if (plain != null) {
            selectTab(plain.id)
            showStart()
        } else {
            newTab()
        }
    }

    /** Back from an instance's video: like the YouTube app, a playing video shrinks; otherwise the instance comes back. */
    fun leavePlayer() {
        val s = state.value
        val opener = s.active.playerFor?.takeIf { id -> tabs.value.tabs.any { it.id == id } } ?: return
        // "Leaving the player: Close video" — straight back to the feed, the video stops (no mini player to close).
        if (settings.value.leavePlayer == "close") {
            appLog("player closed (Back)")
            selectTab(opener)
            return
        }
        if (s.canMinimize && s.media == MediaState.PLAYING) minimize() else selectTab(opener)
    }

    // ------------------------------------------------------------ recommendations column (open foldables, tablets)

    /**
     * The screen asks for the recommendations column ([on]) whenever it is wide enough and a YouTube video page is in
     * front. The column is its own page (not a tab): the same video page showing only its list; it follows the video.
     */
    fun setRecommendationsColumn(on: Boolean) {
        wide = on
        val url = state.value.currentUrl
        val vid = com.streamshell.core.sponsor.SponsorBlockApi.videoIdOf(url)
        // Phones show the live chat inside the page (under the description); wide screens in their own window, opened
        // with the "Live chat" button. The video page itself keeps its recommendations (no second list next to it).
        engine.run(activeId, "window.__ssChatInline=${!on};")
        if (!on || vid == null || !com.streamshell.core.browser.YouTubeSite.isYouTube(UrlHosts.hostOf(url))) {
            closeChat()
            return
        }
        if (state.value.page.isLive && wideChatOpen) {
            val cid = chatPage.value ?: CHAT_PAGE_ID.also {
                engine.setChatPage(it, true)
                chatPage.value = it
            }
            if (chatVideo != vid) {
                chatVideo = vid
                engine.load(cid, com.streamshell.core.browser.YouTubeLiveChat.chatUrl(vid, popout = true))
            }
        } else {
            closeChat()
        }
    }

    @Volatile private var wide = false

    /** Wide screens: the live chat window is open (stays open for the next live stream until closed). */
    @Volatile private var wideChatOpen = false

    fun setWideChat(open: Boolean) {
        wideChatOpen = open
        setRecommendationsColumn(wide)
    }

    private fun closeChat() {
        val id = chatPage.value ?: return
        chatPage.value = null
        chatVideo = null
        engine.close(id)
        engine.setChatPage(id, false)
    }

    /** A video link tapped in the live chat window plays in the player. */
    private fun openFromHelperPage(url: String) {
        val owner = tabs.value.active
        val inst = owner.playerFor?.takeIf { id -> tabs.value.tabs.any { it.id == id } }
        if (inst != null) {
            onOpenVideo(inst, url)
            return
        }
        tabs.update { TabsReducer.update(it, owner.id) { t -> t.copy(url = url, showingStart = false) } }
        if (engine.hasView(owner.id)) engine.openVideoInPlace(owner.id, url) else engine.load(owner.id, url)
    }

    override fun onOpenVideo(tabId: Long, url: String) {
        if (tabId == chatPage.value) {
            openFromHelperPage(url)
            return
        }
        val s = tabs.value
        if (s.tabs.none { it.id == tabId }) return
        engine.pauseMedia(tabId)
        val existing = TabsReducer.playerTabOf(s, tabId)
        val pid = existing?.id ?: run {
            val priv = isPrivate(tabId)
            val (ns, id) = TabsReducer.newBackgroundTab(s) { it.copy(playerFor = tabId, showingStart = false, openedFromStart = false, private = priv) }
            if (id == null) {
                message.value = "At most ${TabsReducer.MAX_TABS} tabs. Close one first."
                return
            }
            if (priv) engine.markPrivate(id)
            tabs.value = ns
            id
        }
        // The mini player is cleared only after the player tab is selected (below): clearing it first made the feed tab
        // the playing tab for a moment, and its preview of the same video started loading (double streaming, 0.4.2 log).
        val wasMini = miniTab.value == pid
        // The same video is already in the player (a timestamp link): jump there, nothing is loaded again.
        val playingUrl = engine.pages.value[pid]?.url ?: existing?.url
        val sameVideo = existing != null && engine.hasView(pid) &&
            com.streamshell.core.sponsor.SponsorBlockApi.videoIdOf(playingUrl) == com.streamshell.core.sponsor.SponsorBlockApi.videoIdOf(url)
        if (sameVideo) {
            tabs.update { TabsReducer.select(it, pid) }
            if (wasMini) miniTab.value = null
            nav.value = NavState()
            val t = com.streamshell.core.browser.YouTubeTimestamps.seconds(runCatching { android.net.Uri.parse(url).getQueryParameter("t") }.getOrNull())
            engine.run(pid, if (t != null) com.streamshell.core.browser.YouTubeTimestamps.seekScript(t) else com.streamshell.core.media.MediaScripts.RESUME)
            return
        }
        tabs.update { TabsReducer.select(TabsReducer.update(it, pid) { t -> t.copy(url = url, showingStart = false) }, pid) }
        if (wasMini) miniTab.value = null
        nav.value = NavState()
        // The one player tab is reused: the next video opens in the same page (no new tab, no new WebView).
        // Only in a page that has finished loading: a video tapped while the prepared player was still loading YouTube's
        // home was switched in a page that was not ready and never played (log of 2026-11-01 16:11).
        openedAt[pid] = System.currentTimeMillis()
        appLog("video opened in tab $pid")
        val prepared = !warmUsed && pid == warmTab
        if (prepared) warmUsed = true
        when (PlayerOpen.mode(existing?.url != null && engine.hasView(pid), engine.pages.value[pid]?.loading == true)) {
            PlayerOpen.Mode.IN_PAGE -> {
                if (prepared) appLog("player prepared: used")
                engine.openVideoInPlace(pid, url)
            }

            PlayerOpen.Mode.FULL_LOAD_STILL_LOADING -> {
                appLog(if (prepared) "player prepared: still loading, full page load" else "open video: player page still loading, full page load")
                engine.load(pid, url)
            }

            PlayerOpen.Mode.FULL_LOAD -> engine.load(pid, url)
        }
        probeSoon()
    }

    // ------------------------------------------------------------ library

    fun toggleBookmark() {
        val s = state.value
        val url = s.currentUrl ?: return
        viewModelScope.launch {
            library.toggleBookmark(url, s.page.title)
            message.value = if (s.isBookmarked) "Bookmark removed." else "Bookmark added."
        }
    }

    fun removeBookmark(url: String) {
        viewModelScope.launch { library.removeBookmark(url) }
    }
    fun removeHistory(url: String) {
        viewModelScope.launch { library.removeHistory(url) }
    }
    fun clearHistory() {
        viewModelScope.launch {
            library.clearHistory()
            engine.clearPageHistory()
        }
    }

    // ------------------------------------------------------------ filter on this site

    fun toggleSiteExempt() {
        val host = state.value.currentHost?.let { DomainInput.normalize(it) } ?: return
        viewModelScope.launch {
            val exempt = host in settings.value.filterExemptSites
            settingsRepo.update { it.copy(filterExemptSites = if (exempt) it.filterExemptSites - host else it.filterExemptSites + host) }
            message.value = if (exempt) "Blocking is on again for $host." else "Nothing is blocked on $host any more."
            engine.reload(activeId)
        }
    }

    fun resolveBlockedNavigation(openAnyway: Boolean) {
        val b = blockedNav.value ?: return
        blockedNav.value = null
        if (!openAnyway) {
            // Counted only now: the user kept it blocked, so the page really did not load.
            privacy.recordBlocked(b.host, FilterDecision.Block(b.rule, b.source))
            return
        }
        if (engine.load(b.tabId, b.url, bypassFilter = true) == BrowserEngine.LoadResult.STARTED) {
            tabs.update { s -> TabsReducer.update(s, b.tabId) { it.copy(url = b.url, showingStart = false) } }
        }
    }

    fun clearBrowsingData(history: Boolean, cookies: Boolean, cache: Boolean) {
        viewModelScope.launch {
            if (history) {
                library.clearHistory()
                engine.clearPageHistory()
            }
            if (cookies) engine.clearCookiesAndSiteData()
            if (cache) engine.clearCache()
            message.value = "Browsing data cleared."
        }
    }

    fun messageShown() {
        message.value = null
    }
    fun say(text: String) {
        message.value = text
    }

    // ------------------------------------------------------------ EngineListener

    override fun onUrlChanged(tabId: Long, url: String) {
        com.streamshell.core.sponsor.SponsorBlockApi.videoIdOf(url)?.let { vid ->
            if (loggedVideo[tabId] != vid) {
                loggedVideo[tabId] = vid
                appLog("tab $tabId opened video $vid")
            }
        }
        val tab = tabs.value.tabs.firstOrNull { it.id == tabId }
        // Shorts as normal videos: a Shorts page opened in another tab (e.g. from the player's recommendations) becomes the
        // watch page. The YouTube instance itself is left alone — its taps on Shorts are routed by the page script, and its
        // Shorts tab must keep working (it moves from Short to Short by itself).
        if (settings.value.shortsAsVideos && tabId != chatPage.value && tab?.instance == null) {
            com.streamshell.core.browser.PageKind.shortsAsWatch(url)?.let { watch ->
                appLog("tab $tabId: Shorts opened as a normal video")
                // Replaces the Shorts entry in the page history: Back does not land on Shorts again (and loop).
                engine.replaceLocation(tabId, watch)
                return
            }
        }
        // Fallback when a tap was not caught in the page: the YouTube instance went to a video itself.
        if (tab?.instance == com.streamshell.core.browser.SiteApp.YOUTUBE.name && url.contains("/watch?") &&
            com.streamshell.core.sponsor.SponsorBlockApi.videoIdOf(url) != null
        ) {
            engine.pauseMedia(tabId)
            engine.goBack(tabId)
            onOpenVideo(tabId, url)
            return
        }
        tabs.update { s -> TabsReducer.update(s, tabId) { it.copy(url = url) } }
        lookUpSegments(tabId, url)
    }

    override fun onTitle(tabId: Long, url: String?, title: String) {
        tabs.update { s -> TabsReducer.update(s, tabId) { it.copy(title = title) } }
        if (url != null && settings.value.saveHistory && !isPrivate(tabId)) viewModelScope.launch { library.updateTitle(url, title) }
    }

    override fun onVisited(tabId: Long, url: String, title: String) {
        if (settings.value.saveHistory && !isPrivate(tabId)) viewModelScope.launch { library.recordVisit(url, title) }
    }

    override fun onNavigationBlocked(tabId: Long, url: String, host: String, decision: FilterDecision.Block) {
        blockedNav.value = BlockedNavigation(tabId, url, host, decision.rule, decision.source)
    }

    override fun onExternal(uri: String) {
        if (!external.open(uri)) message.value = "No app on this device can open that link."
    }

    override fun onDownload(url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {
        message.value = Downloads.enqueue(app, url, userAgent, contentDisposition, mimeType)?.let { "Downloading $it…" } ?: "This download could not be started."
    }

    // ------------------------------------------------------------ SponsorBlock

    private fun lookUpSegments(tabId: Long, url: String) = community.lookUpSegments(tabId, url)

    override fun onSponsorSkipped(tabId: Long, category: String, seconds: Int) {
        if (tabId != mediaTabId) return
        val label = com.streamshell.core.sponsor.SponsorCategory.of(category)?.label ?: return
        appLog("SponsorBlock skipped $category ($seconds s)")
        message.value = "Skipped: $label ($seconds s)"
    }

    override fun onDiagnostics(tabId: Long, line: String) {
        if (settings.value.diagnostics) diagnostics.add("tab $tabId · $line")
    }

    /** Last video per tab written to the diagnostics log (an address change within the same video is not a new line). */
    private val loggedVideo = HashMap<Long, String>()

    /** What the app itself did (lifecycle, fullscreen, mini player, quality, SponsorBlock …), only with diagnostics on. */
    private fun appLog(line: String) {
        if (settings.value.diagnostics) diagnostics.add("app · $line")
    }

    /** When a video was tapped, per player tab (start-up time = tap → player ready). */
    private val openedAt = java.util.concurrent.ConcurrentHashMap<Long, Long>()
    private var lastSettingsText: String? = null
    private val traceLimit = com.streamshell.core.diag.RateLimiter(TRACE_PER_SECOND)

    /** "Trace everything": one line from [source] (ui, settings, jank, memory …), at most 50 lines per second. */
    fun trace(source: String, line: String) {
        val s = settings.value
        if (s.diagnostics && s.traceEverything && traceLimit.allow()) diagnostics.add("$source · $line")
    }

    /** Header, summary and all lines — what "Save as file" writes and "Share" sends. */
    fun diagnosticsText(): String {
        val lines = diagnostics.snapshot()
        val crash = com.streamshell.core.diag.DiagnosticsHub.lastCrash?.let { "== previous crash ==\n$it\n==\n" }.orEmpty()
        return diagnosticsHeader() + "\n" + crash + com.streamshell.core.diag.DiagnosticsSummary.of(lines, diagnostics.dropped, traceLimit.refused) + "\n" + lines.joinToString("\n")
    }

    /** First lines of a shared log: app version and the settings that change playback. No addresses, no account data. */
    fun diagnosticsHeader(): String {
        val s = settings.value
        return listOf(
            "StreamShell ${com.streamshell.BuildConfig.VERSION_NAME} (${com.streamshell.BuildConfig.VERSION_CODE})" +
                " · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
            "settings: skipAds=${s.skipYouTubeAds} autoplay=${s.autoplay} sponsorBlock=${s.sponsorBlock}" +
                " quality wifi/mobile=${s.qualityWifi}/${s.qualityMobile} background=${s.backgroundPlayback}" +
                " audioOnly=${s.backgroundAudioOnly} speed=${s.playbackSpeed}% seek=${s.seekSeconds}s" +
                " resume=${s.resumePlayback} boost=${s.volumeBoost}% normalize=${s.normalizeVolume} compact=${s.youtubeCompact}",
            "trace everything: ${s.diagnostics && s.traceEverything} · lines: ${diagnostics.snapshot().size} (max ${DiagnosticsLog.MAX})",
            "----"
        ).joinToString("\n")
    }

    override fun onDislikesRequest(tabId: Long, videoId: String) = community.dislikes(tabId, videoId)

    override fun onDeArrowRequest(tabId: Long, videoIds: List<String>) = community.titles(tabId, videoIds)

    private var lastAdNotice = 0L

    override fun onAdSkipped(tabId: Long) {
        val now = System.currentTimeMillis()
        if (now - lastAdNotice < 10_000) return // one notice per ad break
        lastAdNotice = now
        message.value = "Ad skipped"
    }

    /** The phone's playing page for [com.streamshell.core.media.AudioCoordinator] (calls, navigation voices). */
    private val audioTarget = object : com.streamshell.core.media.AudioTarget {
        override val label = "phone"
        override fun probe(result: (Boolean) -> Unit) = engine.probeMedia(mediaTabId) { result(it == MediaState.PLAYING) }
        override fun pause() = engine.pauseMedia(mediaTabId)
        override fun play() = engine.playMedia(mediaTabId)
        override fun duck(on: Boolean) = engine.duckMedia(mediaTabId, on)
    }

    init {
        com.streamshell.core.media.AudioCoordinator.log = { line -> com.streamshell.core.diag.DiagnosticsHub.add("audio", line) }
        com.streamshell.core.media.AudioCoordinator.register(app, audioTarget)
        com.streamshell.core.diag.DiagnosticsHub.lastCrash?.let {
            message.value = if (settings.value.diagnostics) {
                "StreamShell closed after an error last time — the report is at the top of the diagnostics log"
            } else {
                "StreamShell closed after an error last time. Turn on Settings › YouTube › Diagnostics log to see and save the report"
            }
        }
    }

    override fun onCleared() {
        com.streamshell.core.media.AudioCoordinator.unregister(audioTarget)
        if (MediaBridge.controls === this) MediaBridge.controls = null
        PlaybackService.stop(app)
        engine.destroy()
    }
}

/** Helper used by the UI for the host part of a page, for labels. */
fun hostLabel(url: String?): String = UrlHosts.hostOf(url)?.removePrefix("www.") ?: ""

/** Wait after the feed has loaded before the player is prepared in the background (the feed's own loading comes first). */
private const val WARM_DELAY_MS = 2500L

/** Quick checks right after a video opens. */
private const val FAST_PROBES = 6
private const val TRACE_PER_SECOND = 50
private const val FAST_PROBE_MS = 350L
