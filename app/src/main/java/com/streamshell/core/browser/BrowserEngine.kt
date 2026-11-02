package com.streamshell.core.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.streamshell.core.media.MediaScripts
import com.streamshell.core.media.MediaState
import com.streamshell.core.privacy.FilterDecision
import com.streamshell.core.privacy.PrivacyCoordinator
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface PageError {
    data object Offline : PageError
    data class Network(val detail: String) : PageError
    data class Http(val status: Int) : PageError
    data object Ssl : PageError
    data object Crashed : PageError
}

object PageMessages {
    fun explain(e: PageError): String = when (e) {
        PageError.Offline -> "You're offline. Check your connection and try again."
        is PageError.Network -> "The page could not be loaded${e.detail.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()}."
        is PageError.Http -> "The site answered with HTTP ${e.status}."
        PageError.Ssl -> "The site's security certificate could not be verified, so the page was not loaded."
        PageError.Crashed -> "The page stopped working and was reset."
    }
}

/** Everything the toolbar needs to know about one tab's page. */
data class PageState(
    val url: String? = null,
    val title: String = "",
    val progress: Int = 0,
    val loading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val error: PageError? = null,
    /** Requests StreamShell refused on this page since it started loading. */
    val blocked: Int = 0,
    /** The page's own background colour (ARGB), read when it finished loading. */
    val background: Int? = null,
    /** YouTube's description/comments panel is open (pull to refresh and the minimize swipe stand back). */
    val panelOpen: Boolean = false,
    /** Chapters of the video on this page (from its description's timestamps). */
    val chapters: List<Chapter> = emptyList(),
    /** Bumped when the page asks to open the fullscreen chapter strip (tap on YouTube's chapter title). */
    val chaptersRequest: Int = 0,
    /** Timeline preview pattern of the video on the page (for chapter pictures in fullscreen). */
    val storyboard: StoryboardInfo? = null,
    /** The video on this page is a live stream (live chat available). */
    val isLive: Boolean = false
)

/** Events the browser state holder (view model) reacts to. All called on the main thread. */
interface EngineListener {
    fun onUrlChanged(tabId: Long, url: String)
    fun onTitle(tabId: Long, url: String?, title: String)
    fun onVisited(tabId: Long, url: String, title: String)
    fun onNavigationBlocked(tabId: Long, url: String, host: String, decision: FilterDecision.Block)
    fun onExternal(uri: String)
    fun onDownload(url: String, userAgent: String, contentDisposition: String?, mimeType: String?)

    /** The YouTube instance tab [tabId] asked to open a video ([url] is a checked https watch URL). */
    fun onOpenVideo(tabId: Long, url: String) {}

    /** SponsorBlock jumped over a segment ([category], length in seconds). Default: ignore. */
    fun onSponsorSkipped(tabId: Long, category: String, seconds: Int) {}

    /** The ad skipper finished (or jumped over) an ad. Default: ignore. */
    fun onAdSkipped(tabId: Long) {}

    /** The page asks for the dislike count of [videoId] (Return YouTube Dislike on). */
    fun onDislikesRequest(tabId: Long, videoId: String) {}

    /** The page asks for DeArrow titles of these videos (DeArrow on). */
    fun onDeArrowRequest(tabId: Long, videoIds: List<String>) {}

    /** One diagnostics line from the page (Diagnostics on). */
    fun onDiagnostics(tabId: Long, line: String) {}
}

/**
 * A WebView whose page can be told it is still visible while StreamShell is in the background
 * ([keepVisible]), so media keeps playing when background playback is on. Without it, Chromium reports
 * the page as hidden when the window goes away and many sites pause their media.
 */
@SuppressLint("ViewConstructor")
class MediaWebView(context: Context) : WebView(context) {
    @Volatile var keepVisible = false

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(if (keepVisible && visibility != View.VISIBLE) View.VISIBLE else visibility)
    }
}

/**
 * The browser engine: one Android System WebView per tab (created lazily, destroyed on close), shared
 * cookie jar, filter on sub-resources, top-level navigation policy, fullscreen video and media probing.
 * Must be used on the main thread only (WebView rule); [PrivacyCoordinator] is the only part touched
 * from WebView's network threads.
 */
class BrowserEngine(
    private val appContext: Context,
    private val privacy: PrivacyCoordinator,
    private val network: NetworkMonitor
) {
    private class Tab(val id: Long, var view: MediaWebView, var wrapper: MutableContextWrapper) {
        @Volatile var pageUrl: String? = null
        val blocked = AtomicInteger(0)

        /** Trace everything: requests of the page since it started loading, refused video streams, failed requests. */
        val requests = AtomicInteger(0)
        val streamsRefused = AtomicInteger(0)
        val failed = AtomicInteger(0)

        @Volatile var startedAt = 0L

        /** Hosts the user chose to open despite a block; applies to navigation only. */
        val navigationBypass = HashSet<String>()
    }

    private val main = Handler(Looper.getMainLooper())
    private val tabs = ConcurrentHashMap<Long, Tab>()

    /** Diagnostics › Trace everything: page loads with times, request counts, errors (lines through the listener). */
    @Volatile var trace = false

    private fun traceLine(id: Long, line: String) {
        if (trace) listener?.onDiagnostics(id, line)
    }
    var listener: EngineListener? = null

    private val _pages = MutableStateFlow<Map<Long, PageState>>(emptyMap())
    val pages: StateFlow<Map<Long, PageState>> = _pages.asStateFlow()

    private val _fullscreenView = MutableStateFlow<View?>(null)
    val fullscreenView: StateFlow<View?> = _fullscreenView.asStateFlow()
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    /** Bumped when a tab's WebView instance is replaced (renderer crash) so the UI re-binds it. */
    private val _versions = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val versions: StateFlow<Map<Long, Int>> = _versions.asStateFlow()

    private val _media = MutableStateFlow(MediaState.NONE)
    val media: StateFlow<MediaState> = _media.asStateFlow()

    /** Where the (largest playing) video sits in the active page, for a video-only PiP window. */
    private val _video = MutableStateFlow<com.streamshell.core.media.VideoRect?>(null)
    val video: StateFlow<com.streamshell.core.media.VideoRect?> = _video.asStateFlow()

    private var destroyed = false

    /** Compact YouTube user style on/off (see [YouTubeSite]). */
    @Volatile private var youtubeCompact = true
    private val docStartHandlers = HashMap<Long, androidx.webkit.ScriptHandler>()

    /** SponsorBlock segments currently installed per tab (re-installed after a full page load). */
    private val sponsorScripts = HashMap<Long, String>()

    fun applySponsorSegments(id: Long, videoId: String, segments: List<com.streamshell.core.sponsor.SkipSegment>) {
        val t = tabs[id] ?: return
        val js = com.streamshell.core.sponsor.SponsorScripts.install(videoId, segments)
        sponsorScripts[id] = js
        t.view.evaluateJavascript(js, null)
    }

    fun clearSponsorSegments() {
        sponsorScripts.clear()
    }

    /** "Skip YouTube ads" on/off (see [YouTubeAds]). */
    @Volatile private var skipAds = true
    private val adHandlers = HashMap<Long, androidx.webkit.ScriptHandler>()

    fun setSkipYouTubeAds(on: Boolean) {
        if (on == skipAds) return
        skipAds = on
        tabs.values.forEach { t ->
            applyAdScript(t.id, t.view)
            if (YouTubeSite.isYouTube(UrlHosts.hostOf(t.pageUrl))) {
                if (on) t.view.evaluateJavascript(YouTubeAds.INSTALL, null) else t.view.reload()
            }
        }
    }

    /** "Autoplay videos" on YouTube (see [YouTubeAutoplay]). */
    @Volatile private var autoplay = true
    private val autoplayHandlers = HashMap<Long, androidx.webkit.ScriptHandler>()

    fun setYouTubeAutoplay(on: Boolean) {
        if (on == autoplay) return
        autoplay = on
        tabs.values.forEach { t ->
            applyAutoplayScript(t.id, t.view)
            if (YouTubeSite.isYouTube(UrlHosts.hostOf(t.pageUrl))) {
                if (on) t.view.evaluateJavascript(YouTubeAutoplay.INSTALL, null) else t.view.reload()
            }
        }
    }

    private fun applyAutoplayScript(id: Long, view: WebView) {
        autoplayHandlers.remove(id)?.let { runCatching { it.remove() } }
        if (!autoplay || id in helperPages) return
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                autoplayHandlers[id] = androidx.webkit.WebViewCompat.addDocumentStartJavaScript(view, YouTubeAutoplay.INSTALL, YouTubeSite.origins)
            }
        }
    }

    /** Instance tabs route video taps to their player tab (see [YouTubeRouter]). */
    private val routeTabs = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    private val routeHandlers = HashMap<Long, androidx.webkit.ScriptHandler>()

    /** Shorts links open as normal videos (router + [com.streamshell.core.browser.PageKind]). */
    @Volatile var shortsAsVideos = false
        private set

    /** Feed tabs load video streams for YouTube's muted previews only when this is on. */
    @Volatile var feedPreviews = false

    private fun routeScript(): String = YouTubeRouter.FLAG + (if (shortsAsVideos) "window.__ssShortsAsVideos=true;" else "window.__ssShortsAsVideos=false;") + YouTubeRouter.INSTALL

    fun setShortsAsVideos(on: Boolean) {
        if (on == shortsAsVideos) return
        shortsAsVideos = on
        routeTabs.forEach { id ->
            val t = tabs[id] ?: return@forEach
            applyRouteScripts(id, t.view)
            if (YouTubeSite.isYouTube(UrlHosts.hostOf(t.pageUrl))) t.view.evaluateJavascript("window.__ssShortsAsVideos=$on;", null)
        }
    }

    /** Player tabs prepared in advance: like feed tabs, they load no video stream until they show a video page. */
    private val warmTabs = ConcurrentHashMap.newKeySet<Long>()

    fun markWarm(id: Long) {
        warmTabs += id
    }

    /** Why the next full page load of a tab starts (diagnostics only). */
    private val loadReason = ConcurrentHashMap<Long, String>()

    fun setRouteVideos(id: Long, on: Boolean) {
        if (on == (id in routeTabs)) return
        if (on) routeTabs += id else routeTabs -= id
        val t = tabs[id] ?: return
        applyRouteScripts(id, t.view)
        if (YouTubeSite.isYouTube(UrlHosts.hostOf(t.pageUrl))) {
            t.view.evaluateJavascript(if (on) routeScript() else "window.__streamshellRouteVideos=false;", null)
        }
    }

    private fun applyRouteScripts(id: Long, view: WebView) {
        routeHandlers.remove(id)?.let { runCatching { it.remove() } }
        if (id !in routeTabs) return
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                routeHandlers[id] = androidx.webkit.WebViewCompat.addDocumentStartJavaScript(view, routeScript(), YouTubeSite.origins)
            }
        }
    }

    private fun applyAdScript(id: Long, view: WebView) {
        adHandlers.remove(id)?.let { runCatching { it.remove() } }
        if (!skipAds || id in helperPages) return
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                adHandlers[id] = androidx.webkit.WebViewCompat.addDocumentStartJavaScript(view, YouTubeAds.INSTALL, YouTubeSite.origins + MusicSite.origins)
            }
        }
    }

    /** Feed filter, dislikes, DeArrow and timeline marks (see [YouTubeExtras]): the current switch state as a script. */
    @Volatile private var extrasConfig = YouTubeExtras.config("", dislikes = false, deArrow = false, markers = false)
    private val extrasHandlers = HashMap<Long, androidx.webkit.ScriptHandler>()

    fun setExtras(config: String) {
        if (config == extrasConfig) return
        extrasConfig = config
        tabs.values.forEach { t ->
            applyExtrasScript(t.id, t.view)
            // Applies at once (no reload): the page script reads the new switches.
            if (YouTubeSite.isYouTube(UrlHosts.hostOf(t.pageUrl)) && t.id !in helperPages) t.view.evaluateJavascript(config + YouTubeExtras.INSTALL, null)
        }
    }

    private fun applyExtrasScript(id: Long, view: WebView) {
        extrasHandlers.remove(id)?.let { runCatching { it.remove() } }
        if (id in helperPages) return
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                extrasHandlers[id] = androidx.webkit.WebViewCompat.addDocumentStartJavaScript(view, extrasConfig + YouTubeExtras.INSTALL, YouTubeSite.origins)
            }
        }
    }

    fun setYouTubeCompact(on: Boolean) {
        if (on == youtubeCompact) return
        youtubeCompact = on
        tabs.values.forEach { t ->
            applyDocStartStyle(t.id, t.view)
            // The fixer changes inline styles, so switching off needs a fresh page; switching on applies at once.
            if (YouTubeSite.isYouTube(UrlHosts.hostOf(t.pageUrl))) {
                if (on) t.view.evaluateJavascript(YouTubeSite.INSTALL, null) else t.view.reload()
            }
        }
    }

    /** Installs the style before YouTube's own scripts run (no flash of the header), where WebView supports it. */
    private fun applyDocStartStyle(id: Long, view: WebView) {
        // Removing can throw on an old WebView or a destroyed view (renderer crash): never let that close the app.
        docStartHandlers.remove(id)?.let { runCatching { it.remove() } }
        musicStyleHandlers.remove(id)?.let { runCatching { it.remove() } }
        if (!youtubeCompact) return
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                docStartHandlers[id] = androidx.webkit.WebViewCompat.addDocumentStartJavaScript(view, YouTubeSite.INSTALL, YouTubeSite.origins)
                musicStyleHandlers[id] = androidx.webkit.WebViewCompat.addDocumentStartJavaScript(view, MusicSite.INSTALL, MusicSite.origins)
            }
        }
    }

    /** Pages that get the ad skipper: YouTube and (0.6.4) YouTube Music, whose player is YouTube's. */
    private fun adSite(url: String?): Boolean = UrlHosts.hostOf(url).let { h -> YouTubeSite.isYouTube(h) || MusicSite.isMusic(h) }

    /** YouTube Music's style (its bottom tab bar hidden in app mode), see [MusicSite]. */
    private val musicStyleHandlers = HashMap<Long, androidx.webkit.ScriptHandler>()

    // ---------------------------------------------------------------- media session (car buttons, audio yield)

    /** Next / previous track through the page's own media session (see [MediaSessionScripts]). */
    fun mediaAction(id: Long, action: String, result: (String) -> Unit = {}) = run(id, MediaSessionScripts.action(action)) { r -> result(r?.trim('"').orEmpty()) }

    /** Title and artist of what plays in [id] (null when the page does not say). */
    fun nowPlaying(id: Long, result: (Pair<String, String>?) -> Unit) = run(id, MediaSessionScripts.NOW_PLAYING) { r -> result(MediaSessionScripts.parseNowPlaying(r)) }

    /** Lower (true) or restore (false) the volume of [id]'s media (another app's navigation voice). */
    fun duckMedia(id: Long, on: Boolean) = run(id, MediaScripts.duck(on))

    private fun page(id: Long, f: (PageState) -> PageState) = _pages.update { m -> m + (id to f(m[id] ?: PageState())) }

    private fun tab(id: Long): Tab {
        tabs[id]?.let { return it }
        val wrapper = MutableContextWrapper(appContext)
        val t = Tab(id, createWebView(id, wrapper), wrapper)
        tabs[id] = t
        page(id) { it }
        return t
    }

    fun hasView(id: Long) = tabs.containsKey(id)

    /** The tab's WebView, re-parented into the Activity's window. */
    fun attach(id: Long, activityContext: Context): View {
        val t = tab(id)
        t.wrapper.baseContext = activityContext
        // The visible page rasterises a little beyond the screen (smoother scrolling and moving); others save memory.
        runCatching { t.view.settings.offscreenPreRaster = true }
        // A YouTube instance's feed keeps its priority while the player covers it (Android was free to end it then, and
        // the feed was loaded again when it came back).
        runCatching { t.view.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, id !in routeTabs) }
        (t.view.parent as? ViewGroup)?.removeView(t.view)
        return t.view
    }

    fun detach(id: Long) {
        val t = tabs[id] ?: return
        runCatching { t.view.settings.offscreenPreRaster = false }
        (t.view.parent as? ViewGroup)?.removeView(t.view)
        t.wrapper.baseContext = appContext
    }

    enum class LoadResult { STARTED, BLOCKED, EXTERNAL, REFUSED }

    /** Loads [url] after the navigation policy and the filter (unless the user chose to open it anyway). */
    fun load(id: Long, url: String, bypassFilter: Boolean = false): LoadResult {
        if (destroyed) return LoadResult.REFUSED
        return when (val d = NavigationPolicy.decide(url, hasGesture = true)) {
            is NavigationDecision.InApp -> {
                val host = UrlHosts.hostOf(d.url)
                val existing = tabs[id]
                if (!bypassFilter && host != null && existing?.navigationBypass?.contains(host) != true) {
                    val f = privacy.decideNavigation(d.url)
                    if (f is FilterDecision.Block) {
                        listener?.onNavigationBlocked(id, d.url, host, f)
                        return LoadResult.BLOCKED
                    }
                }
                val t = tab(id)
                if (bypassFilter && host != null) t.navigationBypass += host
                page(id) { it.copy(error = null, loading = true) }
                loadReason[id] = "app"
                t.view.loadUrl(d.url)
                LoadResult.STARTED
            }

            is NavigationDecision.External -> {
                listener?.onExternal(d.uri)
                LoadResult.EXTERNAL
            }

            NavigationDecision.Refuse -> LoadResult.REFUSED
        }
    }

    fun reload(id: Long) {
        val t = tabs[id] ?: return
        page(id) { it.copy(error = null, loading = true) }
        loadReason[id] = "reload"
        val current = t.pageUrl
        if (current != null && current.startsWith("https://") && t.view.url.isNullOrBlank()) t.view.loadUrl(current) else t.view.reload()
    }

    fun stopLoading(id: Long) {
        tabs[id]?.view?.stopLoading()
    }
    private val lastTap = ConcurrentHashMap<Long, Long>()

    /**
     * Presses YouTube's visible Skip button with a real touch (a scripted click can be ignored). Only for a tab with
     * ad skipping on, at most once per second, and only inside the page.
     */
    private fun tapSkip(id: Long, m: PageMessage.TapSkip) {
        val (x, y, iw, ih) = m
        val view = tabs[id]?.view ?: return
        if (!skipAds || view.width == 0) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - (lastTap[id] ?: 0L) < 1000) return
        lastTap[id] = now
        val sx = x * view.width.toFloat() / iw
        val sy = y * view.height.toFloat() / ih
        fun ev(action: Int, t: Long) = android.view.MotionEvent.obtain(now, t, action, sx, sy, 0).apply { source = android.view.InputDevice.SOURCE_TOUCHSCREEN }
        val down = ev(android.view.MotionEvent.ACTION_DOWN, now)
        view.dispatchTouchEvent(down)
        down.recycle()
        view.postDelayed({
            val up = ev(android.view.MotionEvent.ACTION_UP, android.os.SystemClock.uptimeMillis())
            view.dispatchTouchEvent(up)
            up.recycle()
        }, 60)
    }

    /**
     * Opens a YouTube video in an already loaded YouTube tab without a full page load: YouTube's page navigates
     * itself (history + popstate, as with its own back button). If the player does not show that video within 2.5 s,
     * the URL is loaded normally in the same tab.
     */
    fun openVideoInPlace(id: Long, url: String) {
        val view = tabs[id]?.view
        val target = com.streamshell.core.sponsor.SponsorBlockApi.videoIdOf(url)
        val current = view?.url
        if (view == null || target == null || current == null || !YouTubeSite.isYouTube(UrlHosts.hostOf(current)) || UrlHosts.hostOf(current) != UrlHosts.hostOf(url)) {
            val why = when {
                view == null -> "no page"
                target == null -> "no video id"
                else -> "other site"
            }
            listener?.onDiagnostics(id, "open video: full page load ($why)")
            load(id, url)
            return
        }
        val q = org.json.JSONObject.quote(url)
        view.evaluateJavascript(
            "(function(u){try{var t=new URL(u);history.pushState({},'',t.pathname+t.search);" +
                "window.dispatchEvent(new PopStateEvent('popstate',{state:{}}));return 'ok';}catch(e){return 'no';}})($q)"
        ) { r ->
            if (r?.contains("ok") != true) {
                listener?.onDiagnostics(id, "open video: in-page switch refused, full page load")
                load(id, url)
                return@evaluateJavascript
            }
            view.postDelayed({
                if (tabs[id]?.view !== view) return@postDelayed
                view.evaluateJavascript(PlayerOpen.CHECK) { raw ->
                    val c = PlayerOpen.parse(raw)
                    val state = PlayerOpen.stateName(c.state)
                    when {
                        c.videoId != target -> {
                            listener?.onDiagnostics(id, "open video: player still on '" + c.videoId + "' after 2.5 s, full page load")
                            load(id, url)
                        }

                        !PlayerOpen.switched(c, target, autoplay) -> {
                            listener?.onDiagnostics(id, "open video: new video in page but player $state after 2.5 s, full page load")
                            load(id, url)
                        }

                        else -> listener?.onDiagnostics(id, "open video: switched in page (player $state)")
                    }
                }
            }, 2500)
        }
    }

    /** Loads a YouTube [url] in place of the current page history entry (no Back to the old page). */
    fun replaceLocation(id: Long, url: String) {
        val t = tabs[id] ?: return
        if (!url.startsWith("https://") || !YouTubeSite.isYouTube(UrlHosts.hostOf(url))) return
        loadReason[id] = "app"
        t.view.evaluateJavascript("location.replace(" + org.json.JSONObject.quote(url) + ")", null)
    }

    fun goBack(id: Long) {
        loadReason[id] = "back"
        tabs[id]?.view?.takeIf { it.canGoBack() }?.goBack()
    }
    fun goForward(id: Long) {
        tabs[id]?.view?.takeIf { it.canGoForward() }?.goForward()
    }

    fun close(id: Long) {
        val t = tabs.remove(id) ?: return
        docStartHandlers.remove(id)?.let { h -> runCatching { h.remove() } }
        musicStyleHandlers.remove(id)?.let { h -> runCatching { h.remove() } }
        sponsorScripts.remove(id)
        adHandlers.remove(id)?.let { h -> runCatching { h.remove() } }
        autoplayHandlers.remove(id)?.let { h -> runCatching { h.remove() } }
        routeHandlers.remove(id)?.let { h -> runCatching { h.remove() } }
        extrasHandlers.remove(id)?.let { h -> runCatching { h.remove() } }
        routeTabs.remove(id)
        helperPages.remove(id)
        silentTabs.remove(id)
        val wasPrivate = privateTabs.remove(id)
        warmTabs.remove(id)
        if (fullscreenOwner == id) exitFullscreen()
        (t.view.parent as? ViewGroup)?.removeView(t.view)
        t.view.stopLoading()
        t.view.loadUrl("about:blank")
        t.view.destroy()
        _pages.update { it - id }
        _versions.update { it - id }
        // The last private tab closed: its cookies and storage go (best effort — Android may keep them until all its
        // views are gone; then they go at the next close or start).
        if (wasPrivate && privateTabs.isEmpty()) main.post { deletePrivateProfile() }
    }

    fun deletePrivateProfile() {
        if (privateTabs.isNotEmpty()) return
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) androidx.webkit.ProfileStore.getInstance().deleteProfile(PRIVATE_PROFILE)
        }
    }

    /** On-screen bounds of the video (fullscreen view) or the tab's page, for a smooth PiP transition. */
    fun visibleRect(id: Long): android.graphics.Rect? {
        val fs = _fullscreenView.value
        val v: View = fs ?: tabs[id]?.view ?: return null
        val r = android.graphics.Rect()
        if (!v.isAttachedToWindow || !v.getGlobalVisibleRect(r)) return null
        // Not fullscreen: animate from the video itself, not the whole page.
        val video = _video.value
        if (fs == null && video != null) {
            val loc = IntArray(2).also { v.getLocationInWindow(it) }
            val vr = android.graphics.Rect(
                loc[0] + video.left.toInt(),
                loc[1] + video.top.toInt(),
                loc[0] + (video.left + video.width).toInt(),
                loc[1] + (video.top + video.height).toInt()
            )
            if (vr.intersect(r)) return vr
        }
        return r
    }

    fun exitFullscreen() {
        if (_fullscreenView.value != null) tabs[fullscreenOwner]?.view?.webChromeClient?.onHideCustomView()
    }

    // ---------------------------------------------------------------- media (generic HTML5, see MediaScripts)

    fun probeMedia(id: Long, onResult: (MediaState) -> Unit = {}) {
        val t = tabs[id]
        if (t == null || t.pageUrl == null) {
            _media.value = MediaState.NONE
            _video.value = null
            onResult(MediaState.NONE)
            return
        }
        t.view.evaluateJavascript(MediaScripts.PROBE) { r ->
            val p = MediaScripts.parseProbe(r)
            _media.value = p.state
            _video.value = p.video
            onResult(p.state)
        }
    }

    fun playMedia(id: Long) {
        tabs[id]?.view?.evaluateJavascript(MediaScripts.PLAY, null)
    }
    fun run(id: Long, script: String, result: ((String?) -> Unit)? = null) {
        val v = tabs[id]?.view
        if (v == null) {
            result?.invoke(null)
            return
        }
        v.evaluateJavascript(script) { r -> result?.invoke(r) }
    }

    /**
     * Helper pages: the live chat window on wide screens. Same YouTube origin, but never streams video, gets no autoplay,
     * ad or extras scripts, takes the colour of the video page next to it, and taps on videos are sent to the player.
     * Must be set before the page's view is created.
     */
    private val helperPages = ConcurrentHashMap.newKeySet<Long>()

    fun setChatPage(id: Long, on: Boolean) {
        if (on) {
            helperPages.add(id)
            setRouteVideos(id, true)
            tabs[id]?.view?.setBackgroundColor(lastPageColor)
        } else {
            helperPages.remove(id)
        }
    }

    /** Runs [script] in every open page (settings that apply to all videos, e.g. playback speed). */
    fun runEverywhere(script: String) {
        tabs.values.forEach { it.view.evaluateJavascript(script, null) }
    }

    fun seekMedia(id: Long, seconds: Int) {
        tabs[id]?.view?.evaluateJavascript(MediaScripts.seekBy(seconds.coerceIn(-600, 600)), null)
    }
    fun pauseMedia(id: Long) {
        tabs[id]?.view?.evaluateJavascript(MediaScripts.PAUSE, null)
    }
    fun pauseAllMedia() {
        tabs.values.forEach { it.view.evaluateJavascript(MediaScripts.PAUSE, null) }
    }

    /** True when the page is scrolled to the very top (pull to refresh may start). */
    fun isAtTop(id: Long): Boolean = tabs[id]?.view?.let { it.scrollY <= 0 && !it.canScrollVertically(-1) } ?: false

    /** Tabs that may not play media now (see [MediaScripts.silent]); re-applied after each page load. */
    private val silentTabs = ConcurrentHashMap.newKeySet<Long>()

    @Volatile private var mediaTabNow: Long? = null

    /** No media tab set (e.g. the car screen's own engine): nothing is silenced. */
    private fun isSilent(id: Long) = mediaTabNow.let { it != null && id != it && id !in helperPages }

    /**
     * Only [mediaTab] (the mini player's video, else the current page) may play: two pages streaming at once — e.g. the
     * YouTube feed's muted previews while the mini player plays — competed for the connection (seen in a user's log).
     */
    fun setMediaTab(mediaTab: Long) {
        mediaTabNow = mediaTab
        tabs.values.forEach { t ->
            val silent = isSilent(t.id)
            val was = t.id in silentTabs
            if (silent) silentTabs += t.id else silentTabs -= t.id
            if (silent != was) t.view.evaluateJavascript(MediaScripts.silent(silent), null)
        }
    }

    /** Only the tab that plays in the background is reported as visible; others pause normally. */
    fun setKeepVisible(id: Long?) {
        tabs.values.forEach { it.view.keepVisible = it.id == id }
    }

    /** Leaving the app without background playback: stop media and pause web views (timers stay global). */
    fun pauseViews() {
        tabs.values.forEach { it.view.onPause() }
    }
    fun resumeViews() {
        tabs.values.forEach { it.view.onResume() }
    }

    // ---------------------------------------------------------------- sessions

    /** Writes cookies to disk now (so a sign-in survives an app kill). */
    fun flushCookies() = CookieManager.getInstance().flush()

    fun clearCookiesAndSiteData() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
    }

    @Volatile private var thirdPartyCookies = true

    fun setThirdPartyCookies(on: Boolean) {
        if (on == thirdPartyCookies) return
        thirdPartyCookies = on
        tabs.values.forEach { runCatching { CookieManager.getInstance().setAcceptThirdPartyCookies(it.view, on) } }
    }

    fun clearCache() {
        tabs.values.forEach { it.view.clearCache(true) }
    }
    fun clearPageHistory() {
        tabs.values.forEach { it.view.clearHistory() }
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        tabs.keys.toList().forEach { close(it) }
    }

    // ---------------------------------------------------------------- WebView setup

    private var fullscreenOwner: Long? = null

    /** A short tap in fullscreen: if it hit the chapter title in YouTube's controls, the chapter strip opens. */
    fun fullscreenTap(xPx: Float, yPx: Float, titles: List<String>) {
        val id = fullscreenOwner ?: return
        if (titles.size < 2) return
        tabs[id]?.view?.evaluateJavascript(YouTubeTimestamps.chapterTitleAt(xPx, yPx, titles)) { r ->
            if (r?.trim('"') == "yes") page(id) { it.copy(chaptersRequest = it.chaptersRequest + 1) }
        }
    }

    /**
     * Entering or leaving fullscreen can leave the video paused (the player is moved between views). If it was
     * playing, it is started again shortly after the switch — a video you paused stays paused.
     */
    private fun keepPlayingThroughFullscreenChange(id: Long) {
        if (_media.value != MediaState.PLAYING) return
        val view = tabs[id]?.view ?: return
        listOf(500L, 1300L).forEach { delay -> view.postDelayed({ if (tabs[id]?.view === view) view.evaluateJavascript(MediaScripts.RESUME, null) }, delay) }
    }

    @Volatile private var pageBackground: Int = android.graphics.Color.WHITE

    @Volatile private var lastPageColor: Int = android.graphics.Color.WHITE

    /**
     * Colour drawn where the page has not been painted yet (e.g. the parts of a video page that were hidden in the
     * mini player and are painted again on the way back). Matches the theme sites use (YouTube dark/light), so this
     * shows as the page's own background instead of a black band.
     */
    fun setPageBackground(color: Int) {
        if (color == pageBackground) return
        pageBackground = color
        tabs.values.forEach { it.view.setBackgroundColor(color) }
    }

    /** Tabs that use the private cookie/storage profile (registered before their page is created). */
    private val privateTabs = ConcurrentHashMap.newKeySet<Long>()

    /** Whether private tabs get their own cookies and storage (WebView multi-profile); otherwise only nothing is remembered. */
    val privateProfileSupported: Boolean by lazy { runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }.getOrDefault(false) }

    /** Marks [id] private; call before the tab's page is first created. */
    fun markPrivate(id: Long) {
        privateTabs += id
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(id: Long, wrapper: MutableContextWrapper): MediaWebView {
        val wv = MediaWebView(wrapper)
        // Private tab: its own profile (cookies, storage, cache) — must be set before anything else touches the view.
        if (id in privateTabs && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching { androidx.webkit.WebViewCompat.setProfile(wv, PRIVATE_PROFILE) }
        }
        wv.setBackgroundColor(if (id in helperPages) lastPageColor else pageBackground)
        // The live chat window is small: slightly smaller text shows more messages.
        if (id in helperPages) wv.settings.textZoom = 90
        wv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            run {
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
            }
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // target=_blank and window.open load in the same tab; pop-up windows are never created.
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            // Needed so play() from the notification / PiP buttons works; sites may also autoplay.
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            // userAgentString intentionally untouched (no disguise as another browser).
        }
        // Optional WebView features: an old or mismatched WebView APK must never prevent a tab from opening.
        runCatching { if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) WebSettingsCompat.setSafeBrowsingEnabled(wv.settings, true) }
        runCatching { if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, true) }
        CookieManager.getInstance().setAcceptCookie(true)
        // Google sign-in and embedded players span several domains; without third-party cookies they log you out.
        // Switchable (Settings › Browsing); on by default because of the sign-in.
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, thirdPartyCookies)
        applyDocStartStyle(id, wv)
        if (id !in helperPages) {
            runCatching {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    androidx.webkit.WebViewCompat.addDocumentStartJavaScript(wv, MediaSessionScripts.CAPTURE, MediaSessionScripts.origins)
                }
            }
        }
        applyAdScript(id, wv)
        applyAutoplayScript(id, wv)
        applyRouteScripts(id, wv)
        applyExtrasScript(id, wv)
        if (id !in helperPages) {
            runCatching {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    androidx.webkit.WebViewCompat.addDocumentStartJavaScript(wv, YouTubeLiveChat.INSTALL, YouTubeSite.origins)
                }
            }
        }
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                androidx.webkit.WebViewCompat.addDocumentStartJavaScript(wv, YouTubeTimestamps.INSTALL, YouTubeSite.origins)
            }
        }
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                androidx.webkit.WebViewCompat.addWebMessageListener(wv, YouTubeRouter.CHANNEL, YouTubeSite.origins) { _, message, _, isMainFrame, _ ->
                    val url = message.data ?: return@addWebMessageListener
                    // Accept only a plain https YouTube watch URL; anything else is ignored.
                    if (isMainFrame && id in routeTabs && url.length < 2048 && url.startsWith("https://") && YouTubeSite.isYouTube(UrlHosts.hostOf(url)) &&
                        com.streamshell.core.sponsor.SponsorBlockApi.videoIdOf(url) != null
                    ) {
                        listener?.onOpenVideo(id, url)
                    }
                }
            }
        }
        // Skip reports from the SponsorBlock timer; only YouTube's own top frame can send them.
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                androidx.webkit.WebViewCompat.addWebMessageListener(wv, com.streamshell.core.sponsor.SponsorScripts.CHANNEL, YouTubeSite.origins) { _, message, _, isMainFrame, _ ->
                    // Only YouTube's own top frame; the text is checked and typed by PageMessage.parse (unknown → ignored).
                    if (!isMainFrame) return@addWebMessageListener
                    when (val m = PageMessage.parse(message.data)) {
                        PageMessage.AdSkipped -> listener?.onAdSkipped(id)
                        is PageMessage.TapSkip -> tapSkip(id, m)
                        is PageMessage.Live -> page(id) { it.copy(isLive = m.live) }
                        is PageMessage.Board -> page(id) { it.copy(storyboard = m.info) }
                        PageMessage.OpenChapters -> page(id) { it.copy(chaptersRequest = it.chaptersRequest + 1) }
                        is PageMessage.ChapterList -> page(id) { it.copy(chapters = m.chapters) }
                        is PageMessage.DislikesRequest -> listener?.onDislikesRequest(id, m.videoId)
                        is PageMessage.DeArrowRequest -> listener?.onDeArrowRequest(id, m.videoIds)
                        is PageMessage.Diagnostics -> listener?.onDiagnostics(id, m.line)
                        is PageMessage.Panel -> page(id) { it.copy(panelOpen = m.open) }
                        is PageMessage.SponsorSkipped -> listener?.onSponsorSkipped(id, m.category, m.seconds)
                        null -> Unit
                    }
                }
            }
        }
        wv.webViewClient = Client(id)
        wv.webChromeClient = Chrome(id)
        wv.setDownloadListener { url, ua, cd, mime, _ -> if (url.startsWith("https://")) listener?.onDownload(url, ua, cd, mime) }
        wv.isFocusable = true
        wv.isFocusableInTouchMode = true
        return wv
    }

    private fun recreateAfterCrash(id: Long) {
        val t = tabs[id] ?: return
        val old = t.view
        (old.parent as? ViewGroup)?.removeView(old)
        old.destroy()
        if (fullscreenOwner == id) {
            _fullscreenView.value = null
            fullscreenCallback = null
            fullscreenOwner = null
        }
        t.wrapper = MutableContextWrapper(appContext)
        t.view = createWebView(id, t.wrapper)
        _versions.update { it + (id to ((it[id] ?: 0) + 1)) }
        page(id) { it.copy(loading = false, canGoBack = false, canGoForward = false, error = PageError.Crashed) }
    }

    private val blockedResponse get() = WebResourceResponse("text/plain", "utf-8", 403, "Blocked by StreamShell", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    private inner class Client(private val id: Long) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            val url = request.url.toString()
            return when (val d = NavigationPolicy.decide(url, request.hasGesture())) {
                is NavigationDecision.InApp -> {
                    val host = UrlHosts.hostOf(d.url)
                    val t = tabs[id]
                    if (host != null && t != null && host !in t.navigationBypass) {
                        val f = privacy.decideNavigation(d.url)
                        if (f is FilterDecision.Block) {
                            listener?.onNavigationBlocked(id, d.url, host, f)
                            return true
                        }
                    }
                    if (d.url != url) {
                        view.loadUrl(d.url)
                        true
                    } else {
                        false
                    }
                }

                is NavigationDecision.External -> {
                    if (d.uri.startsWith("intent:", ignoreCase = true)) {
                        // Prefer the site's https fallback inside the browser when there is one.
                        NavigationPolicy.intentFallback(d.uri)?.let {
                            view.loadUrl(it)
                            return true
                        }
                    }
                    listener?.onExternal(d.uri)
                    true
                }

                NavigationDecision.Refuse -> true
            }
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            listener?.onDiagnostics(id, "page load (" + (loadReason.remove(id) ?: "by the page") + ", " + PageKind.of(url) + ")")
            val t = tabs[id]
            t?.pageUrl = url
            t?.blocked?.set(0)
            t?.requests?.set(0)
            t?.streamsRefused?.set(0)
            t?.failed?.set(0)
            t?.startedAt = android.os.SystemClock.elapsedRealtime()
            page(id) { it.copy(url = url, loading = true, progress = 0, error = null, blocked = 0, panelOpen = false, chapters = emptyList(), storyboard = null, isLive = false, canGoBack = view.canGoBack(), canGoForward = view.canGoForward()) }
            // While a video page loads, let the app draw behind it (black where the player will be, the page colour below)
            // instead of one plain colour; the page's own colour comes back when it has loaded.
            // Helper pages (recommendations, chat) keep the page colour: nothing is drawn behind them.
            if (id !in helperPages && com.streamshell.core.sponsor.SponsorBlockApi.videoIdOf(url) != null) view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            if (url != null && url.startsWith("https://")) listener?.onUrlChanged(id, url)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            tabs[id]?.pageUrl = url
            page(id) { it.copy(url = url ?: it.url, canGoBack = view.canGoBack(), canGoForward = view.canGoForward()) }
            if (url != null && url.startsWith("https://")) {
                listener?.onUrlChanged(id, url)
                if (!isReload) listener?.onVisited(id, url, view.title.orEmpty())
            }
        }

        override fun onPageCommitVisible(view: WebView, url: String?) {
            tabs[id]?.let { t -> traceLine(id, "page visible (" + PageKind.of(url) + ") after " + (android.os.SystemClock.elapsedRealtime() - t.startedAt) + " ms") }
            if (skipAds && id !in helperPages && adSite(url)) view.evaluateJavascript(YouTubeAds.INSTALL, null)
            if (autoplay && id !in helperPages && YouTubeSite.isYouTube(UrlHosts.hostOf(url))) view.evaluateJavascript(YouTubeAutoplay.INSTALL, null)
            if (YouTubeSite.isYouTube(UrlHosts.hostOf(url))) view.evaluateJavascript(YouTubeTimestamps.INSTALL, null)
            if (id !in helperPages && YouTubeSite.isYouTube(UrlHosts.hostOf(url))) view.evaluateJavascript(YouTubeLiveChat.INSTALL, null)
            if (id in routeTabs && YouTubeSite.isYouTube(UrlHosts.hostOf(url))) view.evaluateJavascript(routeScript(), null)
            if (id !in helperPages && YouTubeSite.isYouTube(UrlHosts.hostOf(url))) view.evaluateJavascript(extrasConfig + YouTubeExtras.INSTALL, null)
            // Fallback for WebViews without document-start scripts (and harmless when the style is already there).
            if (youtubeCompact && YouTubeSite.isYouTube(UrlHosts.hostOf(url))) view.evaluateJavascript(YouTubeSite.INSTALL, null)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            tabs[id]?.let { t ->
                traceLine(
                    id,
                    "page finished (" + PageKind.of(url) + ") after " + (android.os.SystemClock.elapsedRealtime() - t.startedAt) + " ms · requests " + t.requests.get() +
                        ", blocked " + t.blocked.get() + ", video streams refused " + t.streamsRefused.get() + ", failed " + t.failed.get()
                )
            }
            // A page loaded in a tab that may not play now (a new page in the feed tab while the mini player plays).
            if (isSilent(id)) {
                silentTabs += id
                view.evaluateJavascript(MediaScripts.silent(true), null)
            }
            if (youtubeCompact && YouTubeSite.isYouTube(UrlHosts.hostOf(url))) view.evaluateJavascript(YouTubeSite.INSTALL, null)
            if (youtubeCompact && MusicSite.isMusic(UrlHosts.hostOf(url))) view.evaluateJavascript(MusicSite.INSTALL, null)
            if (YouTubeSite.isYouTube(UrlHosts.hostOf(url))) sponsorScripts[id]?.let { view.evaluateJavascript(it, null) }
            // Helper pages (live chat window) never get the ad skipper or autoplay — also not here at the end of loading.
            if (skipAds && id !in helperPages && adSite(url)) view.evaluateJavascript(YouTubeAds.INSTALL, null)
            if (autoplay && id !in helperPages && YouTubeSite.isYouTube(UrlHosts.hostOf(url))) view.evaluateJavascript(YouTubeAutoplay.INSTALL, null)
            if (id in routeTabs && YouTubeSite.isYouTube(UrlHosts.hostOf(url))) view.evaluateJavascript(routeScript(), null)
            page(id) { it.copy(url = url ?: it.url, loading = false, progress = 100, canGoBack = view.canGoBack(), canGoForward = view.canGoForward()) }
            // Use the page's own background where it is not painted yet (no black band when the mini player opens up).
            view.evaluateJavascript(PAGE_BACKGROUND) { r ->
                // Helper pages (recommendations, chat) take the colour of the video page they sit next to.
                val c = if (id in helperPages) PageColors.parse(r) ?: lastPageColor else PageColors.parse(r)?.also { lastPageColor = it } ?: pageBackground
                view.setBackgroundColor(c)
                page(id) { it.copy(background = c) }
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            tabs[id]?.failed?.incrementAndGet()
            if (request.isForMainFrame) traceLine(id, "page error (" + PageKind.of(request.url.toString()) + "): code " + error.errorCode)
            if (!request.isForMainFrame) return
            page(id) {
                it.copy(loading = false, error = if (!network.isOnline.value) PageError.Offline else PageError.Network(error.description?.toString().orEmpty()))
            }
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            // Only real server errors of the page itself; blocked sub-resources are never reported as page errors.
            if (request.isForMainFrame && errorResponse.statusCode >= 400 && errorResponse.reasonPhrase != "Blocked by StreamShell") {
                traceLine(id, "page HTTP error " + errorResponse.statusCode + " (" + PageKind.of(request.url.toString()) + ")")
                page(id) { it.copy(loading = false, error = PageError.Http(errorResponse.statusCode)) }
            }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel() // never proceed past certificate errors
            page(id) { it.copy(loading = false, error = PageError.Ssl) }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            listener?.onDiagnostics(id, "page process ended (" + (if (detail.didCrash()) "crash" else "ended by Android, e.g. memory") + "), page re-created")
            main.post { recreateAfterCrash(id) }
            return true
        }

        /** Runs on a WebView network thread. Sub-resources only; never touches the WebView. */
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val t = tabs[id] ?: return null
            t.requests.incrementAndGet()
            val url = request.url.toString()
            // The recommendations page next to the player never streams video (its player is not shown).
            if (id in helperPages && request.url.host?.endsWith("googlevideo.com") == true) {
                t.streamsRefused.incrementAndGet()
                return blockedResponse
            }
            // Feed previews off: an instance's feed page loads no video streams (its videos open in the player tab).
            val noPreviews = (!feedPreviews && id in routeTabs) || id in warmTabs
            if (noPreviews && request.url.host?.endsWith("googlevideo.com") == true && !PageKind.isVideoPage(t.pageUrl)) {
                t.streamsRefused.incrementAndGet()
                return blockedResponse
            }
            val d = privacy.decideRequest(url, t.pageUrl, request.isForMainFrame)
            if (d is FilterDecision.Block) {
                val host = request.url.host.orEmpty()
                privacy.recordBlocked(host, d)
                val n = t.blocked.incrementAndGet()
                main.post { page(id) { it.copy(blocked = n) } }
                return blockedResponse
            }
            privacy.recordAllowed(url)
            return null
        }
    }

    private inner class Chrome(private val id: Long) : WebChromeClient() {
        /**
         * YouTube Music asks "leave this page?" while a song plays whenever the app navigates (Home, Explore, Back, a
         * reload): those navigations are the user's own choice in StreamShell's bar, so the page is left without the
         * question. Other sites keep their question.
         */
        override fun onJsBeforeUnload(view: WebView, url: String?, message: String?, result: android.webkit.JsResult): Boolean {
            if (!adSite(url)) return false
            listener?.onDiagnostics(id, "page asked \"leave this page?\" (" + PageKind.of(url) + "): left")
            result.confirm()
            return true
        }

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            page(id) { it.copy(progress = newProgress, loading = newProgress < 100) }
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            val t = title.orEmpty()
            page(id) { it.copy(title = t) }
            listener?.onTitle(id, view.url, t)
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            keepPlayingThroughFullscreenChange(id)
            // The page's scripts may not see a fullscreen element in every WebView version: tell them.
            tabs[id]?.view?.evaluateJavascript("window.__ssFullscreen=true;", null)
            fullscreenCallback?.onCustomViewHidden()
            fullscreenCallback = callback
            fullscreenOwner = id
            _fullscreenView.value = view
        }

        override fun onHideCustomView() {
            fullscreenOwner?.let {
                keepPlayingThroughFullscreenChange(it)
                tabs[it]?.view?.evaluateJavascript("window.__ssFullscreen=false;", null)
            }
            val cb = fullscreenCallback
            fullscreenCallback = null
            fullscreenOwner = null
            _fullscreenView.value = null
            cb?.onCustomViewHidden()
        }

        // Camera, microphone, MIDI and location are never granted: StreamShell declares none of those permissions.
        override fun onPermissionRequest(request: PermissionRequest) = request.deny()
        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) = callback.invoke(origin, false, false)
    }
}

private const val PAGE_BACKGROUND = "(function(){var b=document.body,h=document.documentElement;var c=b?getComputedStyle(b).backgroundColor:'';" +
    "if(!c||c==='rgba(0, 0, 0, 0)'||c==='transparent')c=getComputedStyle(h).backgroundColor;" +
    // Both see-through: the page shows the browser canvas (white), or YouTube's dark theme. Before, this fell back to the
    // app's own (dark) colour, which painted black corners and strips around YouTube's white sheets.
    "if(!c||c==='rgba(0, 0, 0, 0)'||c==='transparent')c=h.hasAttribute('dark')?'rgb(15, 15, 15)':'rgb(255, 255, 255)';return c;})()"

/** Parses "rgb(r, g, b)" / opaque "rgba(r, g, b, 1)" from a page into an Android colour; null for anything else. */
internal object PageColors {
    private val rgb = Regex("""rgba?\(\s*(\d{1,3}),\s*(\d{1,3}),\s*(\d{1,3})(?:,\s*([0-9.]+))?\s*\)""")
    fun parse(raw: String?): Int? {
        val s = raw?.trim()?.trim('"') ?: return null
        val m = rgb.find(s) ?: return null
        val a = m.groupValues[4].takeIf { it.isNotEmpty() }?.toFloatOrNull() ?: 1f
        if (a < 0.99f) return null
        val (r, g, b) = m.groupValues.drop(1).take(3).map { it.toInt().coerceIn(0, 255) }
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}

/** WebView profile of private tabs. */
const val PRIVATE_PROFILE = "streamshell-private"
