package com.streamshell.feature.browser

import android.view.View
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.streamshell.core.adaptive.WidthClass
import com.streamshell.core.adaptive.WindowLayoutSpec
import com.streamshell.core.browser.AddressInput
import com.streamshell.core.browser.PageMessages
import com.streamshell.core.model.Overlay
import com.streamshell.core.navigation.NavAction
import com.streamshell.designsystem.Depth
import com.streamshell.designsystem.GlassIconButton
import com.streamshell.designsystem.GlassNavBar
import com.streamshell.designsystem.GlassNavItem
import com.streamshell.designsystem.GlowProgress
import com.streamshell.designsystem.Radius
import com.streamshell.designsystem.Spacing
import com.streamshell.designsystem.StreamColors
import com.streamshell.designsystem.StreamIcons
import com.streamshell.designsystem.StreamTheme
import com.streamshell.designsystem.ambientBackground
import com.streamshell.designsystem.glass
import com.streamshell.designsystem.pressScale
import kotlinx.coroutines.launch

/** Every user intent of the browser screen. Plain lambdas keep the UI testable without a WebView. */
data class BrowserActions(
    val onSubmitAddress: (String) -> Unit = {},
    val onOpen: (String) -> Unit = {},
    val onOpenInNewTab: (String) -> Unit = {},
    val onBack: () -> Unit = {},
    val onForward: () -> Unit = {},
    val onHome: () -> Unit = {},
    val onReload: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onNav: (NavAction) -> Unit = {},
    val onNewTab: () -> Unit = {},
    val onSelectTab: (Long) -> Unit = {},
    val onCloseTab: (Long) -> Unit = {},
    val onCloseAllTabs: () -> Unit = {},
    val onToggleBookmark: () -> Unit = {},
    val onRemoveBookmark: (String) -> Unit = {},
    val onRemoveHistory: (String) -> Unit = {},
    val onClearHistory: () -> Unit = {},
    val onToggleSiteExempt: () -> Unit = {},
    val onResolveBlocked: (Boolean) -> Unit = {},
    val onPip: () -> Unit = {},
    val onShare: (String) -> Unit = {},
    val onSiteDestination: (com.streamshell.core.browser.SiteDestination) -> Unit = {},
    val onSiteSearch: (String) -> Unit = {},
    /** Suggestions for the site search field (recent searches when empty). */
    val searchSuggestions: suspend (String) -> List<String> = { emptyList() },
    val onClearRecentSearches: () -> Unit = {},
    /** The user's arrangement of the site bar (items in order, see SiteBar). */
    val onSiteBarChange: (com.streamshell.core.browser.SiteApp, List<String>) -> Unit = { _, _ -> },
    val onMinimize: () -> Unit = {},
    /** Pull to refresh may start only when the page is scrolled to the very top. */
    val isPageAtTop: (Long) -> Boolean = { false },
    /** Start page tile: a site app opens as its persistent instance. */
    val onOpenSite: (String) -> Unit = {},
    /** Switch to a site instance (null = StreamShell start page). */
    val onSwitchInstance: (com.streamshell.core.browser.SiteApp?) -> Unit = {},
    val onRestoreMini: () -> Unit = {},
    // Watching
    val onOpenVideo: (String) -> Unit = {},
    val onToggleWatchLater: () -> Unit = {},
    val onRemoveWatchLater: (String) -> Unit = {},
    val onSetSpeed: (Int) -> Unit = {},
    val onSeek: (forward: Boolean) -> Unit = {},
    /** Next (true) / previous (false) track — YouTube Music (player bar, swipe over the player). */
    val onTrack: (next: Boolean) -> Unit = {},
    val onStartOver: () -> Unit = {},
    val onSleep: (minutes: Int?) -> Unit = {},
    val onImportSubscriptions: () -> Unit = {},
    val onRefreshFeed: () -> Unit = {},
    val onOpenChannel: (com.streamshell.core.watch.Channel) -> Unit = {},
    val onRemoveChannel: (String) -> Unit = {},
    /** Jump the playing video to a second (chapters). */
    val onSeekTo: (Int) -> Unit = {},
    /** Current position of the playing video in seconds (asynchronous). */
    val onQueryPosition: ((Double) -> Unit) -> Unit = {},
    /** A short tap in fullscreen at (x, y) px: the app checks whether it hit the chapter title. */
    val onFullscreenTap: (Float, Float) -> Unit = { _, _ -> },
    /** Wide screens, live stream: open / close the live chat window. */
    val onWideChat: (Boolean) -> Unit = {},
    /** The screen is wide enough for the recommendations column next to the player (true) or not (false). */
    val onRecommendationsColumn: (Boolean) -> Unit = {},
    val onCloseMini: () -> Unit = {},
    /** Leave fullscreen (swipe down in the middle, or the exit button). */
    val onExitFullscreen: () -> Unit = {},
    /** Pinch in fullscreen: true = fill the screen (crop), false = fit. */
    val onVideoZoom: (Boolean) -> Unit = {},
    val onToggleMiniPlay: () -> Unit = {},
    // Queue
    val onPlayAllLater: () -> Unit = {},
    val onAddToQueue: () -> Unit = {},
    val onPlayQueueFrom: (Int) -> Unit = {},
    val onRemoveFromQueue: (Int) -> Unit = {},
    val onClearQueue: () -> Unit = {},
    /** Put a channel into a group (null = no group). */
    val onSetChannelGroup: (channelId: String, group: String?) -> Unit = { _, _ -> },
    // Player extras
    val onSetRepeatVideo: (Boolean) -> Unit = {},
    /** Start / end / clear of the part to repeat (one button, three steps). */
    val onMarkRepeat: () -> Unit = {},
    val onClearRepeat: () -> Unit = {},
    val onNextChapter: () -> Unit = {},
    /** Remember the current speed for this channel (true) or forget it (false). */
    val onSetChannelSpeed: (Boolean) -> Unit = {},
    val onBlockChannel: () -> Unit = {},
    // Playlists
    val onCreatePlaylist: (String) -> Unit = {},
    val onRenamePlaylist: (from: String, to: String) -> Unit = { _, _ -> },
    val onDeletePlaylist: (String) -> Unit = {},
    val onAddToPlaylist: (String) -> Unit = {},
    val onRemoveFromPlaylist: (name: String, index: Int) -> Unit = { _, _ -> },
    val onMovePlaylistItem: (name: String, index: Int, delta: Int) -> Unit = { _, _, _ -> },
    val onPlayPlaylist: (name: String, from: Int) -> Unit = { _, _ -> },
    val onNewPrivateTab: () -> Unit = {}
)

/** Presentation flags the Activity owns (window/PiP state). */
data class BrowserChrome(
    val inPip: Boolean = false,
    val pipSupported: Boolean = false,
    /** Where the playing video sits in the page (view pixels), so PiP can show only the video. */
    val pipVideo: com.streamshell.core.media.VideoRect? = null
)

typealias PageSurface = @Composable (tabId: Long, modifier: Modifier) -> Unit

@Composable
fun BrowserScreen(
    state: BrowserUiState,
    layout: WindowLayoutSpec,
    actions: BrowserActions,
    page: PageSurface,
    fullscreen: View?,
    chrome: BrowserChrome,
    snackbar: SnackbarHostState,
    settingsContent: @Composable (onClose: () -> Unit) -> Unit,
    privacyContent: @Composable (onClose: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    diagnosticsContent: @Composable (onClose: () -> Unit) -> Unit = {}
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        val startGlow = state.showingStart && !chrome.inPip
        Box(Modifier.fillMaxSize().then(if (startGlow) Modifier.ambientBackground(StreamTheme.colors) else Modifier)) {
            // ONE composition tree for normal, fullscreen and PiP: only the bars around the page come and go.
            // The page's WebView is therefore never detached and re-attached while a video plays
            // (re-attaching it is what left the PiP window white).
            // "App in the app": on YouTube / YouTube Music the browser bars give way to the site's own sections.
            // The user can bring the browser bars back for this visit from the site menu.
            var browserBarsOnSite by rememberSaveable(state.siteApp) { mutableStateOf(false) }
            val siteApp = state.siteApp.takeIf { !browserBarsOnSite }
            val compact = layout.widthClass == WidthClass.Compact
            Column(
                if (chrome.inPip) {
                    Modifier.fillMaxSize().background(Color.Black).testTag("pip-content")
                } else {
                    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)).imePadding()
                        .testTag(
                            if (siteApp != null) {
                                "layout-site-app"
                            } else if (compact) {
                                "layout-compact"
                            } else {
                                "layout-wide"
                            }
                        )
                }
            ) {
                if (!chrome.inPip) {
                    if (siteApp == null) {
                        TopBar(state, actions, chrome, compact)
                    } else {
                        // The site starts right below the status bar; nothing of StreamShell sits above it.
                        Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(siteBarColors().background))
                    }
                    PageProgress(state)
                }
                var contentSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
                Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { if (!chrome.inPip) contentSize = it }) {
                    // Start page <-> page: a quick fade with a hint of scale (none with reduced motion).
                    val reducedStart = com.streamshell.designsystem.LocalReducedMotion.current
                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.showingStart,
                        enter = if (reducedStart) androidx.compose.animation.EnterTransition.None else androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) + androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(220), initialScale = 0.985f),
                        exit = if (reducedStart) androidx.compose.animation.ExitTransition.None else androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140))
                    ) { StartPage(state, actions, Modifier.fillMaxSize()) }
                    PageSurfaces(state, actions, page, chrome, contentSize)
                }
                if (!chrome.inPip) {
                    // The bar takes the page's tone (light YouTube → light glass on white), so page and app read as one.
                    if (siteApp != null) {
                        com.streamshell.designsystem.PageToned(state.page.background.takeUnless { state.showingStart }) {
                            // On an open YouTube video (not the mini player) the bar slides: sections and player actions in one row.
                            val playerBar = state.settings.playerActionRow && (state.onYouTube || state.onMusic) && state.isVideoPage && state.miniTabId != state.active.id
                            SiteAppBar(siteApp, siteApp.destinationOf(state.currentUrl), state, actions, chrome, playerBar) { browserBarsOnSite = true }
                        }
                    } else if (compact) {
                        BottomToolbar(state, actions, chrome)
                    }
                }
            }
            if (fullscreen != null) Immersive(fullscreen, state, actions)

            if (!chrome.inPip && fullscreen == null) {
                // Panels slide up and fade in; the last panel stays drawn while it slides out.
                var shown by remember { mutableStateOf(Overlay.None) }
                if (state.nav.overlay != Overlay.None) shown = state.nav.overlay
                val reduced = com.streamshell.designsystem.LocalReducedMotion.current
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.nav.overlay != Overlay.None,
                    enter = if (reduced) {
                        androidx.compose.animation.EnterTransition.None
                    } else {
                        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)) +
                            androidx.compose.animation.slideInVertically(androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 500f)) { it / 8 }
                    },
                    exit = if (reduced) {
                        androidx.compose.animation.ExitTransition.None
                    } else {
                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140)) +
                            androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(160)) { it / 10 }
                    }
                ) {
                    when (shown) {
                        Overlay.None -> Unit
                        Overlay.Tabs -> TabsPanel(state, actions)
                        Overlay.Bookmarks -> BookmarksPanel(state, actions)
                        Overlay.History -> HistoryPanel(state, actions)
                        Overlay.Settings -> OverlaySurface("overlay-settings") { settingsContent { actions.onNav(NavAction.CloseOverlay) } }
                        Overlay.Privacy -> OverlaySurface("overlay-privacy") { privacyContent { actions.onNav(NavAction.CloseOverlay) } }
                        Overlay.WatchLater -> WatchLaterPanel(state, actions)
                        Overlay.Subscriptions -> SubscriptionsPanel(state, actions)
                        Overlay.WatchTime -> WatchTimePanel(state, actions)
                        Overlay.Library -> LibraryPanel(state, actions)
                        Overlay.Diagnostics -> OverlaySurface("overlay-diagnostics") { diagnosticsContent { actions.onNav(NavAction.CloseOverlay) } }
                    }
                }
                state.blockedNavigation?.let { BlockedNavigationDialog(it, actions.onResolveBlocked) }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp).testTag("snackbar"))
        }
    }
}

@Composable
internal fun OverlaySurface(tag: String, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize().testTag(tag), color = Color.Transparent, contentColor = StreamTheme.colors.textPrimary) {
        Box(Modifier.fillMaxSize().ambientBackground(StreamTheme.colors).windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars), propagateMinConstraints = true) { content() }
    }
}

@Composable
private fun PageProgress(state: BrowserUiState) {
    Box(Modifier.fillMaxWidth().height(2.dp)) {
        val target = if (!state.showingStart && state.page.loading) state.page.progress.coerceIn(5, 100) / 100f else 1f
        val animated by androidx.compose.animation.core.animateFloatAsState(target, androidx.compose.animation.core.tween(220), label = "progress")
        if (!state.showingStart && state.page.loading && state.page.progress in 0..99) {
            GlowProgress(animated, Modifier.testTag("page-progress"))
        }
    }
}

@Composable
private fun TopBar(state: BrowserUiState, actions: BrowserActions, chrome: BrowserChrome, compact: Boolean) {
    var shieldOpen by rememberSaveable { mutableStateOf(false) }
    // Native chrome sits on the app background, clearly apart from the page; only the address field is glass.
    // On the start page the bars let the ambient light through (no seam).
    Box(Modifier.background(if (state.showingStart) Color.Transparent else StreamTheme.colors.background)) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                .padding(horizontal = Spacing.sm + 4.dp, vertical = Spacing.sm).testTag("top-bar"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!compact) {
                NavButtons(state, actions)
                GlassIconButton(
                    if (state.page.loading) StreamIcons.Stop else StreamIcons.Reload,
                    if (state.page.loading) "Stop loading" else "Reload",
                    if (state.page.loading) actions.onStop else actions.onReload,
                    Modifier.testTag("reload"),
                    enabled = !state.showingStart,
                    glass = false
                )
                GlassIconButton(StreamIcons.HomePage, "Start page", actions.onHome, Modifier.testTag("home"), glass = false)
            }
            AddressField(state, actions, compact, Modifier.weight(1f))
            ShieldButton(state) { shieldOpen = true }
            if (!compact) {
                TabsButton(com.streamshell.core.browser.TabsReducer.visibleCount(state.tabs)) { actions.onNav(NavAction.Open(Overlay.Tabs)) }
                MenuButton(state, actions, chrome)
            }
        }
    }
    if (shieldOpen) ShieldDialog(state, actions) { shieldOpen = false }
}

@Composable
private fun NavButtons(state: BrowserUiState, actions: BrowserActions) {
    GlassIconButton(StreamIcons.Back, "Back", actions.onBack, Modifier.testTag("back"), enabled = state.canGoBack, glass = false, iconSize = 18.dp)
    GlassIconButton(StreamIcons.Forward, "Forward", actions.onForward, Modifier.testTag("forward"), enabled = state.canGoForward, glass = false, iconSize = 18.dp)
}

/** Phones: a floating glass toolbar with comfortable distance to the system bar. */
@Composable
private fun BottomToolbar(state: BrowserUiState, actions: BrowserActions, chrome: BrowserChrome) {
    Box(Modifier.fillMaxWidth().background(if (state.showingStart) Color.Transparent else StreamTheme.colors.background).windowInsetsPadding(WindowInsets.navigationBars), contentAlignment = Alignment.Center) {
        Row(
            Modifier.widthIn(max = Spacing.contentMaxWidth).fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm).height(56.dp)
                .glass(StreamTheme.colors, RoundedCornerShape(Radius.pill), strong = true, elevation = Depth.floating)
                .testTag("bottom-toolbar"),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavButtons(state, actions)
            GlassIconButton(StreamIcons.HomePage, "Start page", actions.onHome, Modifier.testTag("home"), glass = false, selected = state.showingStart)
            TabsButton(com.streamshell.core.browser.TabsReducer.visibleCount(state.tabs)) { actions.onNav(NavAction.Open(Overlay.Tabs)) }
            MenuButton(state, actions, chrome)
        }
    }
}

/** Tab count drawn inside a rounded square, like the tab switchers of modern browsers. */
@Composable
private fun TabsButton(count: Int, onClick: () -> Unit) {
    val c = StreamTheme.colors
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        Modifier.size(44.dp).pressScale(source).clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .testTag("tabs-button").semantics { contentDescription = "Tabs, $count open" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(22.dp).border(1.8.dp, c.textPrimary, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (count > 9) "9+" else "$count", style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.textPrimary)
        }
    }
}

@Composable
private fun ShieldButton(state: BrowserUiState, onClick: () -> Unit) {
    val on = state.settings.filteringEnabled && !state.siteExempt
    val n = if (state.showingStart) 0 else state.page.blocked
    val c = StreamTheme.colors
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        Modifier.size(44.dp).pressScale(source).clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .testTag("shield").semantics { contentDescription = if (!on) "Blocking is off here" else "Ads and trackers: $n blocked on this page" },
        contentAlignment = Alignment.Center
    ) {
        BadgedBox(badge = { if (on && n > 0) Badge(containerColor = c.accentFill, contentColor = c.onAccent) { Text(if (n > 99) "99+" else "$n") } }) {
            Icon(if (on) StreamIcons.PrivacyOn else StreamIcons.PrivacyOff, null, tint = if (on) c.accent else c.textSecondary, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun MenuButton(state: BrowserUiState, actions: BrowserActions, chrome: BrowserChrome) {
    var open by remember { mutableStateOf(false) }
    var playback by rememberSaveable { mutableStateOf(false) }
    if (playback) PlaybackDialog(state, actions) { playback = false }
    val c = StreamTheme.colors
    Box {
        GlassIconButton(StreamIcons.More, "Menu", { open = true }, Modifier.testTag("menu-button"), glass = false, selected = open)
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.testTag("menu"),
            shape = RoundedCornerShape(Radius.panel),
            containerColor = c.glassStrong,
            border = androidx.compose.foundation.BorderStroke(1.dp, c.glassEdge),
            shadowElevation = Depth.floating,
            tonalElevation = 0.dp
        ) {
            fun go(f: () -> Unit) {
                open = false
                f()
            }
            val onPage = !state.showingStart && state.currentUrl != null
            MenuRow("New tab", StreamIcons.NewTab, "menu-new-tab") { go(actions.onNewTab) }
            MenuRow(if (state.isBookmarked) "Remove bookmark" else "Add bookmark", if (state.isBookmarked) StreamIcons.BookmarkFilled else StreamIcons.Bookmark, "menu-bookmark", onPage) { go(actions.onToggleBookmark) }
            MenuDivider()
            com.streamshell.core.browser.SiteApp.entries.forEach { a ->
                MenuRow(a.title, siteAppIcon(a), "menu-instance-${a.name.lowercase()}") { go { actions.onSwitchInstance(a) } }
            }
            MenuDivider()
            if (state.media != com.streamshell.core.media.MediaState.NONE) MenuRow("Playback (speed, sleep timer)", StreamIcons.Play, "menu-playback") { go { playback = true } }
            MenuRow(if (state.isWatchLater) "Remove from Watch later" else "Save to Watch later", StreamIcons.WatchLater, "menu-watch-later-toggle", onPage && (state.isVideoPage || state.isWatchLater)) { go(actions.onToggleWatchLater) }
            if (onPage && state.isVideoPage) MenuRow("Add to queue", StreamIcons.Queue, "menu-add-queue") { go(actions.onAddToQueue) }
            MenuRow("Watch later", StreamIcons.WatchLater, "menu-watch-later") { go { actions.onNav(NavAction.Open(Overlay.WatchLater)) } }
            MenuRow("Subscriptions", StreamIcons.YtSubs, "menu-subscriptions") { go { actions.onNav(NavAction.Open(Overlay.Subscriptions)) } }
            MenuRow("Watch time", StreamIcons.WatchTime, "menu-watch-time") { go { actions.onNav(NavAction.Open(Overlay.WatchTime)) } }
            MenuDivider()
            MenuRow("Bookmarks", StreamIcons.Bookmarks, "menu-bookmarks") { go { actions.onNav(NavAction.Open(Overlay.Bookmarks)) } }
            MenuRow("History", StreamIcons.History, "menu-history") { go { actions.onNav(NavAction.Open(Overlay.History)) } }
            if (chrome.pipSupported) MenuRow("Picture-in-picture", StreamIcons.Pip, "menu-pip", onPage) { go(actions.onPip) }
            if (state.canMinimize) MenuRow("Minimize video", StreamIcons.ExpandDown, "menu-minimize") { go(actions.onMinimize) }
            MenuRow("Share page", StreamIcons.Share, "menu-share", onPage) { go { state.currentUrl?.let(actions.onShare) } }
            MenuDivider()
            if (state.settings.diagnostics) MenuRow("Diagnostics log", StreamIcons.Settings, "menu-diagnostics") { go { actions.onNav(NavAction.Open(Overlay.Diagnostics)) } }
            MenuRow("Settings", StreamIcons.Settings, "menu-settings") { go { actions.onNav(NavAction.Open(Overlay.Settings)) } }
        }
    }
}

@Composable
private fun MenuRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(icon, null, Modifier.size(20.dp)) },
        enabled = enabled,
        onClick = onClick,
        colors = androidx.compose.material3.MenuDefaults.itemColors(textColor = StreamTheme.colors.textPrimary, leadingIconColor = StreamTheme.colors.textSecondary),
        modifier = Modifier.padding(horizontal = 6.dp).clip(RoundedCornerShape(Radius.control)).testTag(tag)
    )
}

@Composable
private fun MenuDivider() {
    Box(Modifier.padding(horizontal = Spacing.md, vertical = 4.dp).fillMaxWidth().height(1.dp).background(StreamTheme.colors.glassEdge))
}

@Composable
private fun AddressField(state: BrowserUiState, actions: BrowserActions, compact: Boolean, modifier: Modifier) {
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val url = state.currentUrl
    val c = StreamTheme.colors
    // Filled when the field gets focus (address selected) and while it has none; what you type is never replaced by an
    // address change of the page (YouTube updates its address while a video plays).
    var value by remember { mutableStateOf(TextFieldValue(AddressInput.display(url))) }
    androidx.compose.runtime.LaunchedEffect(focused) { if (focused) value = TextFieldValue(url.orEmpty(), TextRange(0, url.orEmpty().length)) }
    androidx.compose.runtime.LaunchedEffect(url, focused) { if (!focused) value = TextFieldValue(AddressInput.display(url)) }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        singleLine = true,
        placeholder = {
            Text(
                if (state.onYouTube) {
                    "Search YouTube"
                } else if (compact) {
                    "Search or address"
                } else {
                    "Search or type a web address"
                },
                maxLines = 1
            )
        },
        leadingIcon = { Icon(if (url != null && !focused) StreamIcons.Secure else StreamIcons.Search, if (url != null && !focused) "Secure connection" else null, Modifier.size(18.dp), tint = if (focused) c.accent else c.textSecondary) },
        trailingIcon = if (compact && !focused && url != null) {
            {
                IconButton(onClick = if (state.page.loading) actions.onStop else actions.onReload, modifier = Modifier.testTag("reload")) {
                    Icon(if (state.page.loading) StreamIcons.Stop else StreamIcons.Reload, if (state.page.loading) "Stop loading" else "Reload", Modifier.size(20.dp))
                }
            }
        } else if (focused && value.text.isNotEmpty()) {
            { IconButton(onClick = { value = TextFieldValue("") }, modifier = Modifier.testTag("address-clear")) { Icon(StreamIcons.Close, "Clear", Modifier.size(20.dp)) } }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = {
            val text = value.text
            focus.clearFocus()
            actions.onSubmitAddress(text)
        }),
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = RoundedCornerShape(Radius.field),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = c.surfaceRaised,
            unfocusedContainerColor = c.glass,
            focusedBorderColor = c.accent,
            unfocusedBorderColor = c.glassEdge,
            cursorColor = c.accent,
            unfocusedTextColor = c.textPrimary,
            focusedTextColor = c.textPrimary,
            unfocusedPlaceholderColor = c.textSecondary,
            focusedPlaceholderColor = c.textSecondary
        ),
        modifier = modifier.height(50.dp).onFocusChanged { focused = it.isFocused }.testTag("address-bar")
    )
}

@Composable
private fun Immersive(view: View, state: BrowserUiState, actions: BrowserActions) {
    // Taps are only observed on the video view's own node (never by a layer above it): YouTube's controls — play,
    // timeline, exit fullscreen — get every tap. (A full-size observer layer on top took the taps away, 0.2.6–0.2.14.)
    var touch by remember { mutableStateOf(0) }
    // Volume / brightness swipes: observed on the video's own node too (a layer above would take YouTube's taps).
    var swipeHud by remember { mutableStateOf<Pair<Boolean, Float>?>(null) }
    var hudToken by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(hudToken) {
        if (swipeHud != null) {
            kotlinx.coroutines.delay(700)
            swipeHud = null
        }
    }
    val swipe = rememberSwipeControls(state.settings.swipeControls, onExit = actions.onExitFullscreen, onPinch = actions.onVideoZoom) { v, l ->
        swipeHud = v to l
        hudToken++
    }
    // Exit button: shows with YouTube's controls (any touch) and fades after 3 s.
    var exitVisible by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(touch) {
        if (touch > 0) {
            exitVisible = true
            kotlinx.coroutines.delay(3000)
            exitVisible = false
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("immersive")) {
        androidx.compose.runtime.key(view) {
            AndroidView(
                factory = { _ ->
                    (view.parent as? android.view.ViewGroup)?.removeView(view)
                    view
                },
                onRelease = { v -> (v.parent as? android.view.ViewGroup)?.removeView(v) },
                modifier = Modifier.fillMaxSize().then(swipe).pointerInput(state.page.chapters.size >= 2) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        touch++
                        // Checked at finger-down, before YouTube reacts to the tap (its controls fade, the button changes).
                        if (state.page.chapters.size >= 2) actions.onFullscreenTap(down.position.x, down.position.y)
                        waitForUpOrCancellation(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    }
                }.testTag("immersive-video")
            )
        }
        if (state.page.chapters.size >= 2) ChapterOverlay(state, actions, touch)
        swipeHud?.let { (isVolume, level) -> SwipeHud(isVolume, level) }
        androidx.compose.animation.AnimatedVisibility(
            exitVisible,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.displayCutout).padding(12.dp)
        ) {
            GlassIconButton(StreamIcons.ExpandDown, "Exit fullscreen", actions.onExitFullscreen, Modifier.testTag("fullscreen-exit"), size = 40.dp, iconSize = 20.dp, colors = StreamColors.OnMedia)
        }
    }
}

/**
 * Fullscreen: a vertical swipe on the left half changes the screen brightness, on the right half the volume (like the
 * YouTube patches people use). Only a clear up/down swipe counts; taps, sideways drags and the top and bottom bands
 * (YouTube's buttons and timeline) stay YouTube's. The brightness is the app window's own and goes back to the system
 * setting when fullscreen ends.
 */
@Composable
private fun rememberSwipeControls(enabled: Boolean, onExit: () -> Unit, onPinch: (Boolean) -> Unit, onHud: (isVolume: Boolean, level: Float) -> Unit): Modifier {
    val exit by androidx.compose.runtime.rememberUpdatedState(onExit)
    val pinch by androidx.compose.runtime.rememberUpdatedState(onPinch)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val activity = remember(ctx) { generateSequence(ctx) { (it as? android.content.ContextWrapper)?.baseContext }.firstOrNull { it is android.app.Activity } as? android.app.Activity }
    val audio = remember(ctx) { ctx.getSystemService(android.media.AudioManager::class.java) }
    val hud by androidx.compose.runtime.rememberUpdatedState(onHud)
    androidx.compose.runtime.DisposableEffect(activity) {
        onDispose { activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } } }
    }
    return Modifier.pointerInput(activity, audio, enabled) {
        val slop = 24.dp.toPx()
        val exitDistance = 72.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            val h = size.height.toFloat()
            if (down.position.y < h * 0.15f || down.position.y > h * 0.75f) return@awaitEachGesture
            // Zones: left third brightness, right third volume, middle third swipe down = leave fullscreen (like the
            // YouTube app). Without swipe controls the whole width leaves fullscreen.
            val third = size.width / 3f
            val zone = when {
                !enabled -> 0
                down.position.x < third -> -1
                down.position.x > 2 * third -> 1
                else -> 0
            }
            val volume = zone == 1
            var dx = 0f
            var dy = 0f
            var claimed = false
            var start = 0f
            var pinchStart = 0f
            var pinched = false
            while (true) {
                val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                if (ev.changes.size > 1) {
                    // Pinch: observed only (never consumed): out = fill the screen, in = fit.
                    val a = ev.changes[0].position
                    val b = ev.changes[1].position
                    val dist = (a - b).getDistance()
                    if (pinchStart == 0f) pinchStart = dist
                    if (!pinched && pinchStart > 0f) {
                        if (dist / pinchStart > 1.25f) {
                            pinched = true
                            pinch(true)
                        }
                        if (dist / pinchStart < 0.8f) {
                            pinched = true
                            pinch(false)
                        }
                    }
                    if (ev.changes.none { it.pressed }) break
                    continue
                }
                if (pinchStart > 0f) break
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) break
                val d = ch.position - ch.previousPosition
                dx += d.x
                dy += d.y
                if (zone == 0) {
                    if (kotlin.math.abs(dx) > slop && kotlin.math.abs(dx) > kotlin.math.abs(dy)) break // sideways: YouTube's
                    if (dy < -slop) break // up: YouTube's
                    if (dy > exitDistance && dy > 2 * kotlin.math.abs(dx)) {
                        ch.consume()
                        exit()
                        break
                    }
                    continue
                }
                if (!claimed) {
                    if (kotlin.math.abs(dx) > slop && kotlin.math.abs(dx) > kotlin.math.abs(dy)) break // sideways: YouTube's
                    if (kotlin.math.abs(dy) > slop && kotlin.math.abs(dy) > 2 * kotlin.math.abs(dx)) {
                        claimed = true
                        start = if (volume) {
                            audio?.let { it.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / it.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1) } ?: 0f
                        } else {
                            activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f }
                                ?: runCatching { android.provider.Settings.System.getInt(ctx.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f)
                        }
                        dy = 0f
                    } else {
                        continue
                    }
                }
                ch.consume() // from here the swipe is ours: the video page gets a cancel
                val level = (start - dy / (h * 0.7f)).coerceIn(0f, 1f)
                if (volume) {
                    audio?.let { a ->
                        val max = a.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                        a.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (level * max).toInt(), 0)
                    }
                } else {
                    activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = level.coerceAtLeast(0.01f) } }
                }
                hud(volume, level)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.SwipeHud(isVolume: Boolean, level: Float) {
    run {
        Row(
            Modifier.align(Alignment.Center).glass(StreamColors.OnMedia, RoundedCornerShape(Radius.pill), strong = true).padding(horizontal = 16.dp, vertical = 10.dp).testTag("swipe-hud"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(if (isVolume) StreamIcons.Volume else StreamIcons.Brightness, if (isVolume) "Volume" else "Brightness", Modifier.size(20.dp), tint = Color.White)
            Box(Modifier.width(120.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.3f))) {
                Box(Modifier.fillMaxWidth(level).fillMaxHeight().background(Color.White))
            }
            Text("${(level * 100).toInt()} %", color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Fullscreen chapters, part of YouTube's own timeline: a tap on the chapter title next to the time (in YouTube's
 * controls) opens a slim strip of chapters right above the timeline. No extra button over the video. A chapter jumps
 * the video there; the strip hides after a few seconds without use.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ChapterOverlay(state: BrowserUiState, actions: BrowserActions, touch: Int) {
    val chapters = state.page.chapters
    var open by remember { mutableStateOf(false) }
    var used by remember { mutableStateOf(0) }
    var current by remember { mutableStateOf(-1) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var first by remember { mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(state.page.chaptersRequest) {
        if (!first) {
            open = true
            used++
        }
        first = false
    }
    androidx.compose.runtime.LaunchedEffect(touch) { if (open) used++ }
    androidx.compose.runtime.LaunchedEffect(used, open) {
        if (open) {
            kotlinx.coroutines.delay(6000)
            open = false
        }
    }
    androidx.compose.runtime.LaunchedEffect(open) {
        if (open) actions.onQueryPosition { pos -> current = com.streamshell.core.browser.Chapters.current(chapters, pos) }
    }
    androidx.compose.runtime.LaunchedEffect(open, current) { if (open && current >= 0) runCatching { listState.animateScrollToItem((current - 1).coerceAtLeast(0)) } }
    val c = StreamColors.OnMedia
    androidx.compose.animation.AnimatedVisibility(
        visible = open,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.displayCutout).padding(bottom = 112.dp)
    ) {
        val videoId = com.streamshell.core.sponsor.SponsorBlockApi.videoIdOf(state.currentUrl)
        val board = state.page.storyboard?.takeIf { it.videoId == videoId }
        Row(
            Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.5f), Color.Transparent))).padding(vertical = 6.dp).testTag("chapters-strip"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.lazy.LazyRow(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(chapters.size) { i ->
                    val ch = chapters[i]
                    val on = i == current
                    Column(
                        Modifier.width(152.dp).clip(RoundedCornerShape(Radius.control))
                            .clickable {
                                current = i
                                used++
                                actions.onSeekTo(ch.start)
                            }
                            .padding(4.dp).testTag("chapter-$i"),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF202020))
                                .then(if (on) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp)) else Modifier)
                        ) {
                            ChapterPicture(board, videoId, ch.start, ch.picture)
                            Text(
                                com.streamshell.core.watch.WatchRules.formatClock(ch.start.toDouble()),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            ch.title.ifBlank { "Chapter ${i + 1}" },
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium),
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
            GlassIconButton(StreamIcons.Close, "Close chapters", { open = false }, Modifier.padding(end = 16.dp).testTag("chapters-close"), size = 36.dp, iconSize = 16.dp, colors = c)
        }
    }
}

/**
 * The picture of a chapter: the frame from YouTube's timeline previews at that second (cut from its sprite sheet), or the
 * video's thumbnail when there are no previews (live streams, some short videos). Loaded once, cached in memory.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ChapterPicture(board: com.streamshell.core.browser.StoryboardInfo?, videoId: String?, at: Int, picture: String? = null) {
    // YouTube's own chapter picture first, then the timeline preview at that second, then the video's thumbnail.
    val frame = if (picture != null) null else board?.let { com.streamshell.core.browser.Storyboard.frame(it.spec, it.durationSec, at) }
    val key = picture ?: frame?.let { "${it.url}#${it.x},${it.y}" } ?: videoId?.let { "https://i.ytimg.com/vi/$it/mqdefault.jpg" }
    var bmp by remember(key) { mutableStateOf(key?.let { ThumbCache.get(it) }) }
    androidx.compose.runtime.LaunchedEffect(key) {
        if (key == null || bmp != null) return@LaunchedEffect
        bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                if (frame != null) {
                    val sheet = ThumbCache.get(frame.url) ?: com.streamshell.core.media.Thumbnails.fetch(frame.url, crop16x9 = false)?.also { ThumbCache.put(frame.url, it) }
                    sheet?.takeIf { frame.x + frame.w <= it.width && frame.y + frame.h <= it.height }?.let { android.graphics.Bitmap.createBitmap(it, frame.x, frame.y, frame.w, frame.h) }
                } else {
                    com.streamshell.core.media.Thumbnails.fetch(key)
                }
            }.getOrNull()?.also { ThumbCache.put(key, it) }
        }
    }
    bmp?.let { b: android.graphics.Bitmap -> androidx.compose.foundation.Image(b.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
}

/** Keeps the web view at its normal [size] and scales/moves it so [video] (or the whole page) fills the PiP window. */
private fun Modifier.pipCrop(size: androidx.compose.ui.unit.IntSize, video: com.streamshell.core.media.VideoRect?): Modifier = this.then(Modifier.clipToBounds()).layout { measurable, constraints ->
    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(size.width, size.height))
    val boxW = constraints.maxWidth
    val boxH = constraints.maxHeight
    val crop = com.streamshell.core.media.MediaPolicy.pipCrop(size.width, size.height, video, boxW, boxH)
    val whole = minOf(boxW.toFloat() / size.width, boxH.toFloat() / size.height)
    layout(boxW, boxH) {
        placeable.placeWithLayer(0, 0) {
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            if (crop != null) {
                scaleX = crop.scale
                scaleY = crop.scale
                translationX = crop.translateX
                translationY = crop.translateY
            } else {
                scaleX = whole
                scaleY = whole
                translationX = (boxW - size.width * whole) / 2f
                translationY = (boxH - size.height * whole) / 2f
            }
        }
    }
}

/** Neutral colours of the site bar, close to the sites' own light/dark chrome. */
@Composable
internal fun siteBarColors(): SiteBarColors = if (StreamTheme.colors.isDark) {
    SiteBarColors(Color(0xFF0F0F0F), Color(0xFFF1F1F1), Color(0xFFAAAAAA), Color(0xFF272727))
} else {
    SiteBarColors(Color(0xFFFFFFFF), Color(0xFF0F0F0F), Color(0xFF606060), Color(0xFFE5E5E5))
}

internal data class SiteBarColors(val background: Color, val selected: Color, val normal: Color, val divider: Color)

private fun siteIcon(key: String, on: Boolean) = when (key) {
    "home" -> if (on) StreamIcons.YtHomeOn else StreamIcons.YtHome
    "shorts" -> if (on) StreamIcons.YtShortsOn else StreamIcons.YtShorts
    "live" -> if (on) StreamIcons.YtLiveOn else StreamIcons.YtLive
    "subscriptions" -> if (on) StreamIcons.YtSubsOn else StreamIcons.YtSubs
    "you" -> if (on) StreamIcons.YtYouOn else StreamIcons.YtYou
    "explore" -> if (on) StreamIcons.ExploreOn else StreamIcons.Explore
    "library" -> if (on) StreamIcons.YtYouOn else StreamIcons.YtYou
    else -> StreamIcons.Globe
}

/** Icon of a site app (menu, instance switcher). */
internal fun siteAppIcon(a: com.streamshell.core.browser.SiteApp) = if (a == com.streamshell.core.browser.SiteApp.YOUTUBE_MUSIC) StreamIcons.YouTubeMusic else StreamIcons.YouTube

/** Minimum width (open foldable, tablet; either orientation) for the player + recommendations columns. */
private val SPLIT_MIN_WIDTH = 600.dp

/**
 * The site's main sections, a small search capsule and "More", as a floating bar. Shorts and Live are
 * under More. Tapping the search capsule turns the bar into a search field; Search on the keyboard runs it.
 */
@Composable
private fun SiteAppBar(
    app: com.streamshell.core.browser.SiteApp,
    current: com.streamshell.core.browser.SiteDestination?,
    state: BrowserUiState,
    actions: BrowserActions,
    chrome: BrowserChrome,
    playerBar: Boolean,
    onShowBrowserBars: () -> Unit
) {
    val c = siteBarColors()
    var menu by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    // The user's bar: chosen items in their order (search capsule included), "More" always last.
    val config = state.settings.siteBars[app.name]
    val keys = com.streamshell.core.browser.SiteBar.items(app, config)
    val items = keys.mapNotNull { k -> siteBarItem(app, k) } + GlassNavItem("more", "More", StreamIcons.More, StreamIcons.More, "site-more")
    val selected = when {
        menu -> "more"
        current != null && current.id !in keys -> "more"
        else -> current?.id
    }
    Box(Modifier.fillMaxWidth().background(c.background).windowInsetsPadding(WindowInsets.navigationBars), contentAlignment = Alignment.Center) {
        val barModifier = Modifier.widthIn(max = Spacing.contentMaxWidth).padding(horizontal = Spacing.sm + 4.dp, vertical = 6.dp)
        if (searching) {
            SiteSearchBar(app, barModifier, actions, onSearch = { q ->
                searching = false
                actions.onSiteSearch(q)
            }, onClose = { searching = false })
        } else if (playerBar) {
            PlayerSlideBar(app, state, actions, barModifier, onSearch = { searching = true }, onMore = { menu = true })
        } else {
            GlassNavBar(
                items = items,
                selectedKey = selected,
                onSelect = { item ->
                    when (item.key) {
                        "more" -> menu = true
                        com.streamshell.core.browser.SiteBar.SEARCH -> searching = true
                        com.streamshell.core.browser.SiteBar.LIBRARY -> actions.onNav(NavAction.Open(Overlay.Library))
                        else -> app.destinations.firstOrNull { it.id == item.key }?.let(actions.onSiteDestination)
                    }
                },
                modifier = barModifier.testTag("site-bar"),
                capsuleKey = com.streamshell.core.browser.SiteBar.SEARCH
            )
        }
    }
    if (menu) {
        SiteMenuSheet(app, state, actions, chrome, onDismiss = { menu = false }, onShowBrowserBars = {
            menu = false
            onShowBrowserBars()
        })
    }
}

/** One bar item: the search capsule or one of the site's sections. */
private fun siteBarItem(app: com.streamshell.core.browser.SiteApp, key: String): GlassNavItem? = if (key == com.streamshell.core.browser.SiteBar.SEARCH) {
    GlassNavItem(key, "Search", StreamIcons.Search, StreamIcons.Search, "site-search-capsule")
} else if (key == com.streamshell.core.browser.SiteBar.LIBRARY) {
    GlassNavItem(key, "Library", StreamIcons.Bookmarks, StreamIcons.Bookmarks, "site-library")
} else {
    app.destinations.firstOrNull { it.id == key }?.let { d -> GlassNavItem(d.id, d.label, siteIcon(d.icon, false), siteIcon(d.icon, true), "site-${d.id}") }
}

/**
 * Arrange the bottom bar: show/hide items and change their order. Changes apply at once; "More" always stays last and
 * holds the sections that are not on the bar.
 */
@Composable
private fun SiteBarEditor(app: com.streamshell.core.browser.SiteApp, config: List<String>?, actions: BrowserActions, onDismiss: () -> Unit) {
    val c = StreamTheme.colors
    val bar = com.streamshell.core.browser.SiteBar
    val shown = bar.items(app, config)
    val hidden = bar.all(app).filterNot { it in shown }
    fun change(next: List<String>) = actions.onSiteBarChange(app, next)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("site-bar-editor"),
        title = { Text("Bottom bar") },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Up to ${bar.MAX} items, in this order. \"More\" is always last and holds everything else.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(6.dp))
                val shownItems = shown.mapNotNull { k -> siteBarItem(app, k)?.let { k to it } }
                shownItems.forEachIndexed { i, (k, item) ->
                    androidx.compose.runtime.key(k) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("bar-item-$k"), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(checked = true, enabled = shown.size > 1, onCheckedChange = { change(bar.toggle(app, config, k)) }, modifier = Modifier.testTag("bar-toggle-$k"))
                            Icon(item.icon, null, Modifier.size(20.dp), tint = c.textPrimary)
                            Text(item.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
                            androidx.compose.material3.IconButton(onClick = { change(bar.move(app, config, k, -1)) }, enabled = i > 0, modifier = Modifier.testTag("bar-up-$k")) { Icon(StreamIcons.ExpandUp, "Move ${item.label} up (further left in the bar)") }
                            androidx.compose.material3.IconButton(onClick = { change(bar.move(app, config, k, 1)) }, enabled = i < shownItems.lastIndex, modifier = Modifier.testTag("bar-down-$k")) { Icon(StreamIcons.ExpandDown, "Move ${item.label} down (further right in the bar)") }
                        }
                    }
                }
                if (hidden.isNotEmpty()) {
                    Text("In More", style = MaterialTheme.typography.labelLarge, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
                    hidden.mapNotNull { k -> siteBarItem(app, k)?.let { k to it } }.forEach { (k, item) ->
                        androidx.compose.runtime.key(k) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("bar-item-$k"), verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.Checkbox(checked = false, enabled = shown.size < bar.MAX, onCheckedChange = { change(bar.toggle(app, config, k)) }, modifier = Modifier.testTag("bar-toggle-$k"))
                                Icon(item.icon, null, Modifier.size(20.dp), tint = c.textSecondary)
                                Text(item.label, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, modifier = Modifier.weight(1f).padding(start = 12.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.testTag("site-bar-done")) { Text("Done") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { change(bar.default(app)) }, modifier = Modifier.testTag("site-bar-reset")) { Text("Reset") } }
    )
}

/** The bar turned into a search field (keyboard opens at once). */
@Composable
private fun SiteSearchBar(app: com.streamshell.core.browser.SiteApp, modifier: Modifier, actions: BrowserActions, onSearch: (String) -> Unit, onClose: () -> Unit) {
    val c = StreamTheme.colors
    var q by rememberSaveable { mutableStateOf("") }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.activity.compose.BackHandler(onBack = onClose)
    // Suggestions right away (recent searches), then while typing (short pause so not every key asks).
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var clearedAt by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(q, clearedAt) {
        if (q.isNotBlank()) kotlinx.coroutines.delay(180)
        suggestions = runCatching { actions.searchSuggestions(q) }.getOrDefault(emptyList())
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (suggestions.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 320.dp).glass(c, RoundedCornerShape(Radius.panel), strong = true, elevation = Depth.floating)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical = 6.dp).testTag("site-search-suggestions")
            ) {
                if (q.isBlank()) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent searches", style = MaterialTheme.typography.labelLarge, color = c.textSecondary, modifier = Modifier.weight(1f))
                        Text(
                            "Clear",
                            style = MaterialTheme.typography.labelLarge,
                            color = c.textSecondary,
                            modifier = Modifier.clip(RoundedCornerShape(Radius.pill)).clickable {
                                actions.onClearRecentSearches()
                                suggestions = emptyList()
                                clearedAt++
                            }.padding(horizontal = 10.dp, vertical = 6.dp).testTag("site-search-clear-recent")
                        )
                    }
                }
                suggestions.forEachIndexed { i, s ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onSearch(s) }.padding(horizontal = 16.dp).testTag("site-search-suggestion-$i"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(if (q.isBlank()) StreamIcons.History else StreamIcons.Search, null, Modifier.size(18.dp), tint = c.textSecondary)
                        Text(s, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        // Put it into the field to go on typing.
                        Text(
                            "↖",
                            color = c.textSecondary,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.clip(androidx.compose.foundation.shape.CircleShape).clickable { q = "$s " }.padding(8.dp).semantics { contentDescription = "Edit $s" }
                        )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(com.streamshell.designsystem.NavBarHeight).glass(c, RoundedCornerShape(Radius.pill), strong = true, elevation = Depth.floating).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(Radius.pill)).background(c.surfaceRaised)
                    .border(1.dp, c.accent, RoundedCornerShape(Radius.pill)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(StreamIcons.Search, null, Modifier.size(20.dp), tint = c.textPrimary)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (q.isEmpty()) Text("Search ${app.title}", style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, maxLines = 1)
                    androidx.compose.foundation.text.BasicTextField(
                        value = q,
                        onValueChange = { q = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.textPrimary),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { if (q.isNotBlank()) onSearch(q.trim()) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("site-bar-search")
                    )
                }
                if (q.isNotEmpty()) {
                    Icon(StreamIcons.Close, "Clear", Modifier.size(18.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable { q = "" }, tint = c.textSecondary)
                }
            }
            GlassIconButton(StreamIcons.Close, "Close search", onClose, Modifier.testTag("site-search-close"), glass = false, size = 40.dp, iconSize = 20.dp)
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** Everything the hidden browser bars offered, reachable from the site bar. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SiteMenuSheet(
    app: com.streamshell.core.browser.SiteApp,
    state: BrowserUiState,
    actions: BrowserActions,
    chrome: BrowserChrome,
    onDismiss: () -> Unit,
    onShowBrowserBars: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var shield by rememberSaveable { mutableStateOf(false) }
    var playback by rememberSaveable { mutableStateOf(false) }
    if (playback) {
        PlaybackDialog(state, actions) {
            playback = false
            onDismiss()
        }
    }
    var editBar by rememberSaveable { mutableStateOf(false) }
    var addToList by rememberSaveable { mutableStateOf(false) }
    if (addToList) {
        PlaylistPickerDialog(state, actions) {
            addToList = false
            onDismiss()
        }
    }
    fun go(f: () -> Unit) {
        onDismiss()
        f()
    }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("site-menu"),
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
        containerColor = StreamTheme.colors.surface,
        scrimColor = StreamTheme.colors.scrim,
        tonalElevation = 0.dp
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = Spacing.md).padding(bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search ${app.title}") },
                leadingIcon = { Icon(StreamIcons.Search, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) go { actions.onSiteSearch(query) } }),
                shape = RoundedCornerShape(Radius.field),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = StreamTheme.colors.surfaceRaised,
                    unfocusedContainerColor = StreamTheme.colors.surfaceRaised,
                    focusedBorderColor = StreamTheme.colors.accent,
                    unfocusedBorderColor = StreamTheme.colors.glassEdge,
                    cursorColor = StreamTheme.colors.accent
                ),
                modifier = Modifier.fillMaxWidth().testTag("site-search")
            )
            val onPage = state.currentUrl != null
            if (onPage && state.isVideoPage) PlayerActionGrid(state, actions, onDismiss) { addToList = true }
            InstanceSwitcher(app, actions) { onDismiss() }
            com.streamshell.core.browser.SiteBar.hidden(app, state.settings.siteBars[app.name]).forEach { d ->
                SheetItem(siteIcon(d.icon, false), d.label, "site-${d.id}") { go { actions.onSiteDestination(d) } }
            }
            SheetItem(StreamIcons.HomePage, "StreamShell start page", "site-menu-start") { go { actions.onSwitchInstance(null) } }
            if (state.media != com.streamshell.core.media.MediaState.NONE) SheetItem(StreamIcons.Play, "Playback (speed, sleep timer)", "site-menu-playback") { playback = true }
            SheetItem(StreamIcons.WatchLater, if (state.isWatchLater) "Remove from Watch later" else "Save to Watch later", "site-menu-watch-later-toggle", onPage && (state.isVideoPage || state.isWatchLater)) { go(actions.onToggleWatchLater) }
            if (onPage && state.isVideoPage) SheetItem(StreamIcons.Queue, "Add to queue", "site-menu-add-queue") { go(actions.onAddToQueue) }
            if (onPage && state.isVideoPage) SheetItem(StreamIcons.Bookmarks, "Add to playlist", "site-menu-add-playlist") { addToList = true }
            if (onPage && state.isVideoPage && state.player.channel.isNotBlank()) {
                SheetItem(StreamIcons.PrivacyOn, "Hide ${state.player.channel} in feeds", "site-menu-block-channel") { go(actions.onBlockChannel) }
            }
            SheetItem(StreamIcons.Bookmarks, "Library", "site-menu-library") { go { actions.onNav(NavAction.Open(Overlay.Library)) } }
            SheetItem(StreamIcons.Secure, "New private tab", "site-menu-private-tab") { go(actions.onNewPrivateTab) }
            SheetItem(StreamIcons.WatchLater, "Watch later (${state.watch.later.size})", "site-menu-watch-later") { go { actions.onNav(NavAction.Open(Overlay.WatchLater)) } }
            SheetItem(StreamIcons.YtSubs, "Imported subscriptions", "site-menu-subscriptions") { go { actions.onNav(NavAction.Open(Overlay.Subscriptions)) } }
            SheetItem(StreamIcons.WatchTime, "Watch time", "site-menu-watch-time") { go { actions.onNav(NavAction.Open(Overlay.WatchTime)) } }
            SheetItem(StreamIcons.Tabs, "Tabs (${com.streamshell.core.browser.TabsReducer.visibleCount(state.tabs)})", "site-menu-tabs") { go { actions.onNav(NavAction.Open(Overlay.Tabs)) } }
            SheetItem(if (state.isBookmarked) StreamIcons.BookmarkFilled else StreamIcons.Bookmark, if (state.isBookmarked) "Remove bookmark" else "Add bookmark", "site-menu-bookmark", onPage) { go(actions.onToggleBookmark) }
            SheetItem(StreamIcons.PrivacyOn, if (state.settings.filteringEnabled && !state.siteExempt) "Ads & trackers: ${state.page.blocked} blocked" else "Ads & trackers: blocking off here", "site-menu-shield") { shield = true }
            if (chrome.pipSupported) SheetItem(StreamIcons.Pip, "Picture-in-picture", "site-menu-pip", onPage) { go(actions.onPip) }
            SheetItem(StreamIcons.Share, "Share page", "site-menu-share", onPage) { go { state.currentUrl?.let(actions.onShare) } }
            if (state.canMinimize) SheetItem(StreamIcons.ExpandDown, "Minimize video", "site-menu-minimize") { go(actions.onMinimize) }
            SheetItem(StreamIcons.Reload, "Reload", "site-menu-reload", onPage) { go(actions.onReload) }
            SheetItem(StreamIcons.Globe, "Show browser bars", "site-menu-browser-bars") { onShowBrowserBars() }
            SheetItem(StreamIcons.More, "Customize bottom bar", "site-menu-customize-bar") { editBar = true }
            if (state.settings.diagnostics) SheetItem(StreamIcons.Settings, "Diagnostics log", "site-menu-diagnostics") { go { actions.onNav(NavAction.Open(Overlay.Diagnostics)) } }
            SheetItem(StreamIcons.Settings, "Settings", "site-menu-settings") { go { actions.onNav(NavAction.Open(Overlay.Settings)) } }
        }
    }
    if (shield) ShieldDialog(state, actions) { shield = false }
    if (editBar) SiteBarEditor(app, state.settings.siteBars[app.name], actions) { editBar = false }
}

@Composable
private fun SheetItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = StreamTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(Radius.control))
            .selectable(selected = false, enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .then(if (enabled) Modifier else Modifier.alpha(0.38f)).padding(horizontal = Spacing.sm).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Box(Modifier.size(36.dp).glass(c, RoundedCornerShape(Radius.control)), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp), tint = c.textPrimary) }
        Text(label, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
    }
}

/**
 * Watches touches on their way to the web view (Initial pass, nothing is consumed, so the page still gets
 * every touch) and recognises two clear one-finger swipes down:
 *  - starting on the video ([minimize] on): [onMinimize];
 *  - starting elsewhere while the page is scrolled to the very top ([refresh] on): pull to refresh. [onPull]
 *    reports the pull (0 = none, 1 = far enough); letting go past 1 calls [onRefresh].
 */
private fun Modifier.pageGestures(
    video: com.streamshell.core.media.VideoRect?,
    minimize: Boolean,
    refresh: Boolean,
    atTop: () -> Boolean,
    onMinimize: () -> Unit,
    onPull: (Float) -> Unit,
    onRefresh: () -> Unit
): Modifier = this.pointerInput(video, minimize, refresh) {
    val threshold = 96.dp.toPx()
    val pullDistance = 120.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
        val y0 = down.position.y
        val onVideo = if (video != null) y0 >= video.top && y0 <= video.top + video.height else y0 <= size.height * 0.4f
        val mode = when {
            minimize && onVideo -> 1
            refresh && atTop() -> 2
            else -> return@awaitEachGesture
        }
        var dx = 0f
        var dy = 0f
        try {
            while (true) {
                val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                if (ev.changes.size > 1) break // pinch or two fingers: not ours
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) {
                    if (mode == 2 && dy >= pullDistance && dy > 2 * kotlin.math.abs(dx)) onRefresh()
                    break
                }
                val d = ch.position - ch.previousPosition
                dx += d.x
                dy += d.y
                if (dy < -threshold / 2) break // swiping up: leave it to the page
                if (mode == 1 && dy > threshold && dy > 2 * kotlin.math.abs(dx)) {
                    onMinimize()
                    break
                }
                if (mode == 2) {
                    if (kotlin.math.abs(dx) > dy && kotlin.math.abs(dx) > threshold / 2) break // sideways: not a pull
                    onPull((dy / pullDistance).coerceIn(0f, 1.4f))
                }
            }
        } finally {
            if (mode == 2) onPull(0f)
        }
    }
}

/** YouTube's live chat in a rounded card with its own header (wide screens, live streams). */
@Composable
private fun LiveChatCard(pageId: Long, page: PageSurface, pageColor: Int?, modifier: Modifier, floating: Boolean = false, onClose: () -> Unit = {}) {
    // In the colours of the video page next to it (YouTube light or dark), not the app's.
    val bg = pageColor?.let { Color(it) } ?: siteBarColors().background
    val dark = bg.luminance() < 0.5f
    val fg = if (dark) Color(0xFFF1F1F1) else Color(0xFF0F0F0F)
    // A soft, barely visible contour (no hard lines), like the shaded recommendation tiles below it.
    val line = if (dark) Color(0x24FFFFFF) else Color(0x1A000000)
    Box(if (floating) modifier else modifier.background(bg).padding(8.dp)) {
        Column(
            (if (floating) Modifier.fillMaxSize().shadow(Depth.floating, RoundedCornerShape(Radius.panel)) else Modifier.fillMaxSize())
                .clip(RoundedCornerShape(Radius.panel)).background(bg).border(1.dp, line, RoundedCornerShape(Radius.panel)).testTag("live-chat-card")
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFFF0033)))
                Text("Live chat", style = MaterialTheme.typography.titleSmall, color = fg, modifier = Modifier.padding(start = 8.dp).weight(1f))
                Icon(StreamIcons.Close, "Close live chat", Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(onClick = onClose).padding(6.dp).testTag("live-chat-close"), tint = fg)
            }
            Box(Modifier.fillMaxWidth().weight(1f)) { page(pageId, Modifier.fillMaxSize()) }
        }
    }
}

/** Two quick taps on the same half of the video (observe only: the page still gets its taps). */
private fun Modifier.doubleTapSeek(video: com.streamshell.core.media.VideoRect?, onSeek: (forward: Boolean) -> Unit): Modifier = this.pointerInput(video) {
    var lastUp = 0L
    var lastX = 0f
    var lastY = 0f
    val slop = 60.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
        val v = video ?: return@awaitEachGesture
        val p = down.position
        if (p.x < v.left || p.x > v.left + v.width || p.y < v.top || p.y > v.top + v.height) return@awaitEachGesture
        val up = waitForUpOrCancellation(androidx.compose.ui.input.pointer.PointerEventPass.Initial) ?: return@awaitEachGesture
        if (up.uptimeMillis - down.uptimeMillis > 250 || (up.position - p).getDistance() > slop / 2) {
            lastUp = 0
            return@awaitEachGesture
        }
        if (lastUp > 0 && up.uptimeMillis - lastUp < 320 && kotlin.math.abs(p.x - lastX) < slop && kotlin.math.abs(p.y - lastY) < slop) {
            lastUp = 0
            onSeek(p.x > v.left + v.width / 2)
        } else {
            lastUp = up.uptimeMillis
            lastX = p.x
            lastY = p.y
        }
    }
}

/** "« 10 s" / "10 s »" bubble over the tapped half of the video. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.SeekFlash(forward: Boolean, token: Long, video: com.streamshell.core.media.VideoRect?, seconds: Int, onDone: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(token) {
        kotlinx.coroutines.delay(650)
        onDone()
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val v = video ?: return
    Box(
        Modifier.offset { androidx.compose.ui.unit.IntOffset((v.left + if (forward) v.width * 0.75f else v.width * 0.25f).toInt() - with(density) { 44.dp.roundToPx() }, (v.top + v.height / 2).toInt() - with(density) { 22.dp.roundToPx() }) }
            .size(88.dp, 44.dp).glass(StreamColors.OnMedia, RoundedCornerShape(Radius.pill), strong = true).testTag("seek-flash"),
        contentAlignment = Alignment.Center
    ) {
        Text(if (forward) "$seconds s »" else "« $seconds s", color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

/** YouTube-style pull indicator: a round chip that follows the finger and turns as you pull. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.PullIndicator(pull: Float, refreshing: Boolean) {
    val c = StreamTheme.colors
    val shown = pull > 0.02f || refreshing
    val anim by androidx.compose.animation.core.animateFloatAsState(if (refreshing) 1f else pull.coerceAtMost(1.2f), androidx.compose.animation.core.spring(stiffness = 900f), label = "pull")
    if (!shown && anim < 0.02f) return
    val spin = if (refreshing) {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "spin").animateFloat(
            0f,
            360f,
            androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.LinearEasing)),
            label = "spinAngle"
        ).value
    } else {
        anim * 300f
    }
    Box(
        Modifier.align(Alignment.TopCenter)
            .offset { androidx.compose.ui.unit.IntOffset(0, (anim * 72.dp.toPx()).toInt() - 44.dp.roundToPx()) }
            .size(40.dp)
            .graphicsLayer {
                alpha = anim.coerceIn(0f, 1f)
                scaleX = 0.6f + 0.4f * anim.coerceIn(0f, 1f)
                scaleY = scaleX
            }
            .glass(c, androidx.compose.foundation.shape.CircleShape, strong = true, elevation = Depth.floating)
            .testTag("pull-refresh"),
        contentAlignment = Alignment.Center
    ) {
        Icon(StreamIcons.Reload, "Refresh", Modifier.size(22.dp).graphicsLayer { rotationZ = spin }, tint = if (anim >= 1f || refreshing) c.accent else c.textPrimary)
    }
}

/**
 * All web pages on screen, in ONE keyed loop: the current page and (if any) the minimized video. Because each
 * tab keeps its key, minimizing or restoring only moves the same composition (and the same WebView) between
 * the full and the mini position; nothing is detached, re-attached or re-loaded. The move itself is a GPU
 * transform (scale/translate/clip in a graphics layer) driven by a spring, so it stays smooth while YouTube works.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.PageSurfaces(state: BrowserUiState, actions: BrowserActions, page: PageSurface, chrome: BrowserChrome, contentSize: androidx.compose.ui.unit.IntSize) {
    val activeId = state.active.id
    val mini = state.miniTabId
    // Open foldables and tablets: the video page uses the whole screen (with its own recommendations); for a live stream
    // the chat opens as a window below it (upright) or beside it (landscape). Pages keep their compositions.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val wideVideo = !chrome.inPip && mini == null && !state.showingStart && state.onYouTube &&
        com.streamshell.core.sponsor.SponsorBlockApi.videoIdOf(state.currentUrl) != null &&
        contentSize.width > 0 && with(density) { contentSize.width.toDp() } >= SPLIT_MIN_WIDTH
    // Reported once the size is known (the very first frame has no size yet).
    androidx.compose.runtime.LaunchedEffect(wideVideo, state.currentUrl, state.page.isLive, contentSize.width > 0) { if (contentSize.width > 0) actions.onRecommendationsColumn(wideVideo) }
    val ids = buildList {
        if (!state.showingStart && activeId != mini) add(activeId)
        if (mini != null) add(mini)
    }
    // The video page always has the whole screen. Live stream on a wide screen: the chat is a floating window at the
    // bottom right (sized to the player), opened with the "Live chat" button and scrollable on its own.
    val chat = state.chatPageId?.takeIf { wideVideo && state.page.isLive }
    for (id in ids) {
        androidx.compose.runtime.key(id) {
            TabSlot(id, isMini = id == mini, state, actions, page, chrome, contentSize)
        }
    }
    if (chat != null) {
        val pv = chrome.pipVideo
        val c = ChatWindow.compute(contentSize.width, contentSize.height, density.density, pv?.let { (it.top + it.height).toInt() })
        LiveChatCard(
            chat,
            page,
            state.page.background,
            Modifier.offset { androidx.compose.ui.unit.IntOffset(c.left, c.top) }.size(with(density) { c.width.toDp() }, with(density) { c.height.toDp() }),
            floating = true
        ) { actions.onWideChat(false) }
    }
    if (wideVideo && state.page.isLive && chat == null) {
        // Live stream, chat window closed: one button to open it.
        Row(
            Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 12.dp).glass(StreamColors.OnMedia, RoundedCornerShape(Radius.pill), strong = true, elevation = Depth.floating)
                .clip(RoundedCornerShape(Radius.pill)).clickable { actions.onWideChat(true) }.padding(horizontal = 16.dp, vertical = 10.dp).testTag("open-live-chat"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFFF0033)))
            Text("Live chat", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * The floating live chat window on a wide screen: bottom right, from just under the player to the bottom of the page
 * (as tall as there is room under the video — the player's real height comes from the page, YouTube makes it lower
 * than 16:9 on wide windows), half the width. At least 260 dp tall so a few messages can be read; if there is not that
 * much room under the player it overlaps the player's lower edge. Pure, unit-tested.
 */
internal object ChatWindow {
    fun compute(w: Int, h: Int, density: Float, playerBottom: Int? = null): androidx.compose.ui.unit.IntRect {
        val dp = { v: Int -> (v * density).toInt() }
        val margin = dp(12)
        val bottomOfPlayer = playerBottom?.takeIf { it in dp(80)..(h - dp(40)) } ?: (w * 9 / 16)
        val width = (w * 0.5f).toInt().coerceIn(minOf(dp(300), w - 2 * margin), minOf(dp(480), w - 2 * margin))
        val minH = minOf(dp(260), h - 2 * margin)
        val top = minOf(bottomOfPlayer + margin, h - margin - minH)
        return androidx.compose.ui.unit.IntRect(w - margin - width, top, w - margin, h - margin)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.TabSlot(
    tabId: Long,
    isMini: Boolean,
    state: BrowserUiState,
    actions: BrowserActions,
    page: PageSurface,
    chrome: BrowserChrome,
    contentSize: androidx.compose.ui.unit.IntSize
) {
    val reduced = com.streamshell.designsystem.LocalReducedMotion.current
    val progress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isMini && !chrome.inPip) 1f else 0f,
        // Minimize: soft spring. Restore: a short, decelerating tween, so the page is full size again quickly
        // (the parts that were hidden in the mini player have to be painted again).
        animationSpec = if (reduced) {
            androidx.compose.animation.core.snap()
        } else if (isMini) {
            androidx.compose.animation.core.spring(dampingRatio = 0.86f, stiffness = 380f)
        } else {
            androidx.compose.animation.core.tween(240, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
        },
        label = "mini"
    )
    val density = androidx.compose.ui.platform.LocalDensity.current
    val w = contentSize.width
    val h = contentSize.height
    val margin = with(density) { 12.dp.toPx() }
    val miniW = (w * 0.55f).coerceIn(with(density) { 160.dp.toPx() }, with(density) { 340.dp.toPx() }).coerceAtMost(w - 2 * margin)
    val miniH = miniW * 9f / 16f
    val miniY = h - miniH - margin
    // The mini player can be dragged to the left or right side, or pushed over an edge to hide it there (a thin tab
    // stays at the edge to pull it back). Page picture and controls move together, so they never separate.
    var miniSide by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(MiniDock.RIGHT) }
    var miniHidden by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(isMini) { if (isMini) miniHidden = false }
    val restX = MiniDock.restX(miniSide, miniHidden, w.toFloat(), miniW, margin)
    // One animated position: a drag moves it directly under the finger, letting go springs it on from exactly there
    // (no jump back). Read only while drawing/placing, so a drag does not rebuild the screen on every move.
    val miniXAnim = remember { androidx.compose.animation.core.Animatable(restX) }
    val miniScope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(restX, dragging) {
        if (dragging) return@LaunchedEffect
        if (reduced || !isMini) {
            miniXAnim.snapTo(restX)
        } else {
            miniXAnim.animateTo(restX, androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 500f))
        }
    }
    // The media tab's video position (the mini player's video while one is open, else the current page's).
    val video = chrome.pipVideo
    val isMediaTab = isMini || state.miniTabId == null
    val targetAt = { x: Float -> miniTarget(w, h, if (isMediaTab) video else null, x, miniY, miniW, miniH) }
    val corner = with(density) { 12.dp.toPx() }
    val shadow = with(density) { 10.dp.toPx() }

    val isActive = tabId == state.active.id
    // With YouTube's description/comments panel open, swiping is for the panel: no pull to refresh, and minimize
    // only from the video itself.
    val panelOpen = isActive && state.page.panelOpen
    val swipe = isActive && !isMini && !chrome.inPip && state.settings.swipeToMinimize && (!panelOpen || video != null) && state.canMinimize && state.media != com.streamshell.core.media.MediaState.NONE
    // 0.6.4: not on YouTube Music's player page — a swipe down there is for the mini player (it reloaded the page and
    // YouTube Music asked "leave this page?").
    val pullRefresh = !isMini && !chrome.inPip && tabId == state.active.id && !state.showingStart && state.settings.pullToRefresh && !state.page.panelOpen &&
        !(state.onMusic && state.isVideoPage)
    var pull by remember { mutableStateOf(0f) }
    var refreshing by remember { mutableStateOf(false) }
    if (refreshing && !state.page.loading) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(400)
            refreshing = false
        }
    }
    val slotModifier = when {
        w <= 0 -> Modifier.fillMaxSize()

        chrome.inPip && isMediaTab -> Modifier.fillMaxSize().pipCrop(contentSize, video)

        else -> Modifier.fillMaxSize().layout { measurable, constraints ->
            val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.placeWithLayer(0, 0) {
                    val p = progress
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    if (p > 0f) {
                        val target = targetAt(miniXAnim.value)
                        val sc = 1f + (target.scale - 1f) * p
                        scaleX = sc
                        scaleY = sc
                        translationX = target.tx * p
                        translationY = target.ty * p
                        clip = true
                        shape = LerpRectShape(target.clip, p, corner / sc)
                        shadowElevation = shadow * p / sc
                    }
                }
            }
        }
    }
    // Double-tap the left/right half of the video: -10 s / +10 s (sites without their own double-tap).
    val doubleTap = isActive && !isMini && !chrome.inPip && state.settings.doubleTapSeek && state.siteApp == null && !state.onYouTube &&
        state.media != com.streamshell.core.media.MediaState.NONE && video != null
    // YouTube Music: swipe sideways over the player for the next / previous track (like Spotify).
    val trackSwipe = isActive && !isMini && !chrome.inPip && state.onMusic && state.isVideoPage
    var seekFlash by remember { mutableStateOf<Pair<Boolean, Long>?>(null) }
    Box(
        Modifier.fillMaxSize()
            .testTag(
                if (isMini) {
                    "mini-box"
                } else if (isActive) {
                    "page"
                } else {
                    "page-side"
                }
            ).then(
                if (swipe || pullRefresh) {
                    Modifier.pageGestures(
                        // YouTube Music plays songs in a hidden or tiny video element: its player is the top of the page.
                        if (state.onMusic) null else video?.takeIf { it.width > 1f && it.height > 1f },
                        minimize = swipe,
                        refresh = pullRefresh,
                        atTop = { actions.isPageAtTop(tabId) },
                        onMinimize = actions.onMinimize,
                        onPull = { pull = it },
                        onRefresh = {
                            refreshing = true
                            actions.onReload()
                        }
                    )
                } else {
                    Modifier
                }
            ).then(
                if (trackSwipe) {
                    Modifier.trackSwipe(video, actions.onTrack)
                } else {
                    Modifier
                }
            ).then(
                if (doubleTap) {
                    Modifier.doubleTapSeek(video) { fwd ->
                        seekFlash = fwd to System.nanoTime()
                        actions.onSeek(fwd)
                    }
                } else {
                    Modifier
                }
            )
    ) {
        // Backdrop for a loading video page (the web view is see-through until the page has loaded): black where the
        // player will be, the page's colour below — instead of a white or black screen.
        if (!isMini && isActive && com.streamshell.core.sponsor.SponsorBlockApi.videoIdOf(state.currentUrl) != null) {
            val bg = state.page.background?.let { Color(it) } ?: siteBarColors().background
            Column(Modifier.fillMaxSize().background(bg)) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black))
            }
        }
        page(tabId, slotModifier)
        // While a YouTube video page is still loading, YouTube draws its player under its (hidden) top bar, which
        // left a white band above the black player. Until the video plays, the app covers that strip in the
        // player's black; it fades away as soon as playback starts (at the latest after 8 s).
        if (!isMini && isActive && !chrome.inPip && state.onYouTube && com.streamshell.core.sponsor.SponsorBlockApi.videoIdOf(state.currentUrl) != null) {
            var loadingBand by remember(state.currentUrl) { mutableStateOf(true) }
            androidx.compose.runtime.LaunchedEffect(state.currentUrl) {
                kotlinx.coroutines.delay(LOADING_BAND_MAX_MS)
                loadingBand = false
            }
            // Gone when the video plays AND either its picture starts at the top edge (YouTube has moved the player up), or
            // the app has confirmed that THIS video plays in the player ("playing" alone can still be the previous video
            // for a moment). 0.5.5: live streams and videos that are not 16:9 never start at the top edge — the band then
            // stayed until its fallback (8 s, now 3 s).
            val atTop = video != null && video.top <= 4f
            val pageVideo = com.streamshell.core.sponsor.SponsorBlockApi.videoIdOf(state.currentUrl)
            val confirmed = state.player.readyVideoId.isNotEmpty() && state.player.readyVideoId == pageVideo
            androidx.compose.runtime.LaunchedEffect(state.currentUrl, state.media, atTop, confirmed) {
                if (state.media == com.streamshell.core.media.MediaState.PLAYING && (atTop || confirmed)) {
                    kotlinx.coroutines.delay(300)
                    loadingBand = false
                }
            }
            val bandAlpha by androidx.compose.animation.core.animateFloatAsState(if (loadingBand) 1f else 0f, androidx.compose.animation.core.tween(180), label = "band")
            if (bandAlpha > 0f) {
                // 0.6.4: one calm loading screen instead of a picture over a white page (screenshots of 2026-11-02): the
                // video's picture in the player's 16:9 place with a loading ring, and below it placeholders for title,
                // channel and the next videos in the page's own colour — all fading out together once the video plays.
                val bg = state.page.background?.let { Color(it) } ?: siteBarColors().background
                androidx.compose.foundation.layout.BoxWithConstraints(
                    Modifier.fillMaxSize().graphicsLayer { alpha = bandAlpha }.background(bg).testTag("youtube-loading-band")
                ) {
                    val playerHeight = maxWidth * 9f / 16f
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().height(playerHeight).background(Color.Black)) {
                            val url = state.currentUrl
                            if (url != null && com.streamshell.core.browser.PageKind.of(url) == "video") {
                                VideoThumb(url, Modifier.fillMaxSize().testTag("youtube-loading-thumb"), rounded = false, fill = true)
                            }
                            androidx.compose.material3.CircularProgressIndicator(
                                Modifier.align(Alignment.Center).size(36.dp),
                                color = Color.White,
                                strokeWidth = 3.dp,
                                trackColor = Color.White.copy(alpha = 0.25f)
                            )
                        }
                        LoadingPagePlaceholder()
                    }
                }
            }
        }
        if (pullRefresh) PullIndicator(pull, (refreshing && state.page.loading) || (refreshing && pull == 0f))
        seekFlash?.let { (fwd, t) -> SeekFlash(fwd, t, video, state.settings.seekSeconds) { seekFlash = null } }
        if (!isMini) {
            state.page.error?.takeIf { tabId == state.active.id }?.let { err ->
                Surface(Modifier.fillMaxSize().testTag("page-error"), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(StreamIcons.Offline, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(PageMessages.explain(err), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Spacing.sm))
                        Button(onClick = actions.onReload, modifier = Modifier.padding(top = Spacing.md).testTag("page-retry")) { Text("Try again") }
                    }
                }
            }
        }
    }
    if (isMini && !chrome.inPip && w > 0) {
        val dp = with(density) { androidx.compose.ui.unit.DpSize(miniW.toDp(), miniH.toDp()) }
        // Controls fade in at the end of the move; a tap on the video opens it again; a sideways drag moves it.
        if (!miniHidden || dragging) {
            Box(
                Modifier.offset { androidx.compose.ui.unit.IntOffset(miniXAnim.value.toInt(), miniY.toInt()) }.size(dp)
                    .graphicsLayer { alpha = ((progress - 0.6f) / 0.4f).coerceIn(0f, 1f) }
                    .pointerInput(w, miniW) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragging = true
                                miniScope.launch { miniXAnim.stop() }
                            },
                            onDragEnd = {
                                dragging = false
                                if (MiniDock.closes(miniXAnim.value, miniW, w.toFloat())) {
                                    // Swiped away (most of it beyond an edge): closed, as with X.
                                    actions.onCloseMini()
                                } else {
                                    val (side, hidden) = MiniDock.settle(miniXAnim.value, miniW, w.toFloat())
                                    miniSide = side
                                    miniHidden = hidden
                                }
                            },
                            onDragCancel = { dragging = false }
                        ) { change, dx ->
                            change.consume()
                            miniScope.launch { miniXAnim.snapTo(miniXAnim.value + dx) }
                        }
                    }
                    .clickable(onClickLabel = "Open video") { actions.onRestoreMini() }
                    .testTag("mini-player").semantics { contentDescription = "Mini player: ${state.miniTitle}" }
            ) {
                val playing = state.media == com.streamshell.core.media.MediaState.PLAYING
                GlassIconButton(StreamIcons.Close, "Close mini player", actions.onCloseMini, Modifier.align(Alignment.TopEnd).padding(4.dp).testTag("mini-close"), size = 32.dp, iconSize = 16.dp, colors = StreamColors.OnMedia)
                GlassIconButton(if (playing) StreamIcons.Pause else StreamIcons.PlayArrow, if (playing) "Pause" else "Play", actions.onToggleMiniPlay, Modifier.align(Alignment.BottomStart).padding(4.dp).testTag("mini-play"), size = 32.dp, iconSize = 18.dp, colors = StreamColors.OnMedia)
                // Progress of the video (updated every second while the mini player shows).
                if (state.player.duration > 0) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color(0x55FFFFFF)).testTag("mini-progress")) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(state.player.progress).background(StreamTheme.colors.accent))
                    }
                }
            }
        }
        if (miniHidden && !dragging) {
            // Hidden at an edge: a thin tab there (sound keeps playing); tap it or pull it inwards to bring the player back.
            val tabW = with(density) { 18.dp.toPx() }
            val tabH = with(density) { 64.dp.toPx() }
            val right = miniSide == MiniDock.RIGHT
            val shape = if (right) RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp) else RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
            Box(
                Modifier.offset { androidx.compose.ui.unit.IntOffset(if (right) (w - tabW).toInt() else 0, (miniY + miniH / 2 - tabH / 2).toInt()) }
                    .size(with(density) { tabW.toDp() }, with(density) { tabH.toDp() })
                    .shadow(Depth.floating, shape).clip(shape).background(Color(0xE6202020))
                    .pointerInput(right) {
                        detectHorizontalDragGestures { change, dx ->
                            change.consume()
                            if ((right && dx < -2f) || (!right && dx > 2f)) miniHidden = false
                        }
                    }
                    .clickable(onClickLabel = "Show mini player") { miniHidden = false }
                    .testTag("mini-tab").semantics { contentDescription = "Show mini player" },
                contentAlignment = Alignment.Center
            ) {
                Icon(if (right) StreamIcons.Back else StreamIcons.Forward, null, Modifier.size(14.dp), tint = Color.White)
            }
        }
    }
}

/**
 * Where the mini player rests: at the left or right edge, or hidden just beyond one edge. Pure, unit-tested.
 * Letting go after a sideways drag: more than a third of it beyond an edge hides it there; otherwise it goes to the
 * nearer side.
 */
internal object MiniDock {
    const val LEFT = -1
    const val RIGHT = 1

    fun restX(side: Int, hidden: Boolean, w: Float, miniW: Float, margin: Float): Float = when {
        hidden && side == LEFT -> -miniW - 2f
        hidden -> w + 2f
        side == LEFT -> margin
        else -> w - miniW - margin
    }

    /** Let go with more than two thirds beyond an edge: the mini player is swiped away (closed). */
    fun closes(x: Float, miniW: Float, w: Float): Boolean = -x > miniW * 2f / 3f || x + miniW - w > miniW * 2f / 3f

    /** (side, hidden) after letting go with the player's left edge at [x]. */
    fun settle(x: Float, miniW: Float, w: Float): Pair<Int, Boolean> {
        val outLeft = -x
        val outRight = x + miniW - w
        return when {
            outLeft > miniW / 3f -> LEFT to true
            outRight > miniW / 3f -> RIGHT to true
            x + miniW / 2f < w / 2f -> LEFT to false
            else -> RIGHT to false
        }
    }
}

/** Where the page has to go so that its video ends up exactly in the mini player rectangle. */
internal data class MiniTarget(val scale: Float, val tx: Float, val ty: Float, val clip: androidx.compose.ui.geometry.Rect)

internal fun miniTarget(w: Int, h: Int, video: com.streamshell.core.media.VideoRect?, x: Float, y: Float, mw: Float, mh: Float): MiniTarget {
    val full = androidx.compose.ui.geometry.Rect(0f, 0f, w.toFloat(), h.toFloat())
    val crop = com.streamshell.core.media.MediaPolicy.pipCrop(w, h, video, mw.toInt().coerceAtLeast(1), mh.toInt().coerceAtLeast(1))
    if (crop == null || video == null) {
        val s = minOf(mw / w.coerceAtLeast(1), mh / h.coerceAtLeast(1))
        // No known video: shrink the top 16:9 band of the page (where players usually are).
        val band = androidx.compose.ui.geometry.Rect(0f, 0f, w.toFloat(), minOf(h.toFloat(), w * 9f / 16f))
        val sb = minOf(mw / band.width, mh / band.height)
        return if (band.height > 0) {
            MiniTarget(sb, x + (mw - band.width * sb) / 2f, y + (mh - band.height * sb) / 2f, band)
        } else {
            MiniTarget(s, x, y, full)
        }
    }
    val l = video.left.coerceIn(0f, w.toFloat())
    val t = video.top.coerceIn(0f, h.toFloat())
    val r = minOf(video.left + video.width, w.toFloat())
    val b = minOf(video.top + video.height, h.toFloat())
    return MiniTarget(crop.scale, x + crop.translateX, y + crop.translateY, androidx.compose.ui.geometry.Rect(l, t, r, b))
}

/** Clip outline that moves from the whole page (p = 0) to [target] (p = 1), in the layer's own coordinates. */
private class LerpRectShape(val target: androidx.compose.ui.geometry.Rect, val p: Float, val radius: Float) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
        fun lerp(a: Float, b: Float) = a + (b - a) * p
        val r = androidx.compose.ui.geometry.Rect(lerp(0f, target.left), lerp(0f, target.top), lerp(size.width, target.right), lerp(size.height, target.bottom))
        return androidx.compose.ui.graphics.Outline.Rounded(androidx.compose.ui.geometry.RoundRect(r, androidx.compose.ui.geometry.CornerRadius(radius * p)))
    }
}

/** Chips to jump between the persistent site instances; the current one is selected. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun InstanceSwitcher(current: com.streamshell.core.browser.SiteApp, actions: BrowserActions, onDone: () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("instance-switcher")
    ) {
        com.streamshell.core.browser.SiteApp.entries.forEach { a ->
            com.streamshell.designsystem.GlassChip(
                a.title,
                selected = a == current,
                onClick = {
                    onDone()
                    if (a != current) actions.onSwitchInstance(a)
                },
                icon = siteAppIcon(a),
                modifier = Modifier.testTag("instance-${a.name.lowercase()}")
            )
        }
    }
}

/** Items of the sliding player bar, in order (pure, unit-tested). Sections first, then the player actions. */
internal object PlayerBar {
    const val SEARCH = "search"
    const val HOME = "home"
    const val SUBSCRIPTIONS = "subscriptions"
    const val LIBRARY = "library"
    const val LATER = "later"
    const val QUEUE = "queue"
    const val PLAYLIST = "playlist"
    const val SPEED = "speed"
    const val REPEAT = "repeat"
    const val CHAPTER = "chapter"
    const val MORE = "more"

    // YouTube Music (0.6.3)
    const val EXPLORE = "explore"
    const val PREVIOUS = "previous"
    const val NEXT = "next"
    const val MUSIC_START = NEXT

    /** Sections (left of the thin line); everything after them is a player action. */
    val SECTIONS = setOf(SEARCH, HOME, SUBSCRIPTIONS, LIBRARY, EXPLORE)

    /** YouTube Music's bar: its sections (search, Home, Explore, Library), then previous / next track, Watch later, speed, repeat. */
    fun musicKeys(): List<String> = listOf(SEARCH, HOME, EXPLORE, LIBRARY, PREVIOUS, NEXT, LATER, SPEED, REPEAT, MORE)

    /** The first player action of [keys] (a thin line before it). */
    fun firstAction(keys: List<String>): String? = keys.firstOrNull { it !in SECTIONS }

    /** A horizontal swipe on the music player: true = next, false = previous, null = not a track swipe (pure). */
    fun trackSwipe(dxDp: Float, dyDp: Float): Boolean? {
        val horizontal = kotlin.math.abs(dxDp) >= TRACK_SWIPE_DP && kotlin.math.abs(dxDp) > 2 * kotlin.math.abs(dyDp)
        return if (horizontal) dxDp < 0 else null
    }

    const val TRACK_SWIPE_DP = 80f

    /** The first player action (a thin line before it separates it from the sections). */
    const val FIRST_ACTION = LATER

    /** Where the bar starts when a video opens. */
    const val START = SPEED

    /** Items across the bar at a time (as many as the normal bar shows). */
    const val VISIBLE = 5

    fun keys(hasChapters: Boolean): List<String> = listOf(SEARCH, HOME, SUBSCRIPTIONS, LIBRARY, LATER, QUEUE, PLAYLIST, SPEED, REPEAT) + (if (hasChapters) listOf(CHAPTER) else emptyList()) + MORE

    /** How far (dp) a finger must pull on past an end before the bar goes back to its start. */
    const val PUSH_DP = 28

    /** Bounce of the spring back (below 1 = it swings past a little before it settles). */
    const val SPRING_DAMPING = 0.42f

    /** The most the row moves when pulled past an end (it gets harder and harder to pull). */
    const val MAX_STRETCH_DP = 56

    /** First visible item of the home position: [START] in the middle of [VISIBLE] items, as near as the ends allow. */
    fun homeFirst(keys: List<String>, startKey: String = START): Int {
        val start = keys.indexOf(startKey).coerceAtLeast(0)
        return (start - VISIBLE / 2).coerceIn(0, (keys.size - VISIBLE).coerceAtLeast(0))
    }

    /** Rubber: how far the row moves for [pulled] px past an end — almost 1:1 at first, never more than [max]. */
    fun rubber(pulled: Float, max: Float): Float = if (pulled <= 0f || max <= 0f) 0f else max * pulled / (pulled + max)

    /**
     * The distance pulled on past an end so far: grows while the finger moves towards an end the bar has reached
     * (right at the start, left at the end); any other move starts it over.
     */
    fun push(sofar: Float, dx: Float, atStart: Boolean, atEnd: Boolean): Float {
        val pastStart = dx > 0f && atStart
        val pastEnd = dx < 0f && atEnd
        return when {
            pastStart || pastEnd -> sofar + kotlin.math.abs(dx)
            dx == 0f -> sofar
            else -> 0f
        }
    }

    /** A haptic tick: while sliding, when another item than before reaches the middle (not on the first layout). */
    fun ticks(last: Int, now: Int, sliding: Boolean): Boolean = sliding && last >= 0 && now >= 0 && now != last

    /** Index of the item whose centre is nearest [center] (items as start/size pairs in px), or -1. */
    fun nearest(items: List<Pair<Int, Int>>, center: Int): Int = items.withIndex().minByOrNull { (_, it) -> kotlin.math.abs(it.first + it.second / 2 - center) }?.index ?: -1
}

/**
 * The bottom bar while a YouTube video is open — the same glass bar as everywhere (capsule, neutral highlight behind the
 * active icon, YouTube's icons and labels), but its row slides sideways: search and the sections on one side, the player
 * actions on the other. Five items show at a time; the one in the middle sits in the bar's highlight and is lifted, as the
 * selected section is in the normal bar. A light tick (haptic) when an item reaches the middle, a click on a tap — both
 * follow the system's touch-feedback setting. Starts on Speed; search and the sections are one swipe to the right.
 */
@Composable
private fun PlayerSlideBar(
    app: com.streamshell.core.browser.SiteApp,
    state: BrowserUiState,
    actions: BrowserActions,
    modifier: Modifier,
    onSearch: () -> Unit,
    onMore: () -> Unit
) {
    var playback by rememberSaveable { mutableStateOf(false) }
    var playlist by rememberSaveable { mutableStateOf(false) }
    val music = app == com.streamshell.core.browser.SiteApp.YOUTUBE_MUSIC
    val keys = if (music) PlayerBar.musicKeys() else PlayerBar.keys(state.page.chapters.size >= 2)
    val firstAction = PlayerBar.firstAction(keys)
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    val view = androidx.compose.ui.platform.LocalView.current
    val colors = StreamTheme.colors
    val reduced = com.streamshell.designsystem.LocalReducedMotion.current
    // Opening a video: the home position — Speed in the middle, the bar full (as near the middle as the ends allow).
    val home = PlayerBar.homeFirst(keys, if (music) PlayerBar.MUSIC_START else PlayerBar.START)
    androidx.compose.runtime.LaunchedEffect(state.currentUrl) { list.scrollToItem(home) }
    val centered by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf {
            val info = list.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            val visible = info.visibleItemsInfo
            visible.getOrNull(PlayerBar.nearest(visible.map { it.offset to it.size }, mid))?.index ?: -1
        }
    }
    // Tick when another item reaches the middle while sliding (not on the first layout).
    var lastTick by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    androidx.compose.runtime.LaunchedEffect(centered) {
        if (PlayerBar.ticks(lastTick, centered, list.isScrollInProgress)) view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        lastTick = centered
    }
    val shape = RoundedCornerShape(Radius.pill)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Spring at the ends: how far the row is pulled past an end (px, + = right), shown as a shift of the row.
    var stretch by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier.fillMaxWidth().height(com.streamshell.designsystem.NavBarHeight)
            .glass(colors, shape, strong = true, elevation = Depth.floating)
            .clip(shape)
            // Spring at the ends: pulling on past the first or last item moves the row less and less (like rubber); let go and
            // it springs back. Pulled far enough (a stronger buzz), the whole bar then goes back to its home position.
            // Only observed — the row itself still scrolls and snaps as usual.
            .pointerInput(keys.size) {
                val threshold = PlayerBar.PUSH_DP.dp.toPx()
                val maxStretch = PlayerBar.MAX_STRETCH_DP.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    var push = 0f
                    var dir = 0f
                    var pushed = false
                    while (true) {
                        val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        val dx = ch.position.x - ch.previousPosition.x
                        val atStart = !list.canScrollBackward
                        val atEnd = !list.canScrollForward
                        push = PlayerBar.push(push, dx, atStart, atEnd)
                        if (push > 0f && dx != 0f) dir = if (dx > 0f) 1f else -1f
                        stretch = dir * PlayerBar.rubber(push, maxStretch)
                        if (!pushed && push > threshold) {
                            pushed = true
                            view.performHapticFeedback(
                                if (android.os.Build.VERSION.SDK_INT >= 34) android.view.HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else android.view.HapticFeedbackConstants.LONG_PRESS
                            )
                        }
                    }
                    if (stretch != 0f || pushed) {
                        val from = stretch
                        scope.launch {
                            androidx.compose.animation.core.animate(
                                from,
                                0f,
                                animationSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = PlayerBar.SPRING_DAMPING,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                )
                            ) { v, _ -> stretch = v }
                        }
                        if (pushed) scope.launch { list.animateScrollToItem(home) }
                    }
                }
            }
            .testTag("player-slide-bar")
    ) {
        // Five items across, like the normal bar, always edge to edge (no empty space at the ends).
        val itemWidth = maxWidth / PlayerBar.VISIBLE
        // The bar's highlight, fixed in the middle (the row slides under it).
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = 6.dp).width(minOf(itemWidth - 8.dp, 60.dp)).height(28.dp)
                .clip(shape).background(colors.textPrimary.copy(alpha = if (colors.isDark) 0.14f else 0.08f))
        )
        androidx.compose.foundation.lazy.LazyRow(
            state = list,
            flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(list, androidx.compose.foundation.gestures.snapping.SnapPosition.Center),
            // No Android stretch: the bar's own spring (above) is felt at the ends instead.
            overscrollEffect = null,
            modifier = Modifier.fillMaxSize().graphicsLayer { translationX = stretch }
        ) {
            itemsIndexed(keys, key = { _, k -> k }) { i, k ->
                val (icon, label, on) = playerBarItem(k, state)
                val isCenter = i == centered
                val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val tap = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                    runPlayerBarItem(k, app, state, actions) { local ->
                        when (local) {
                            PlayerBar.SEARCH -> onSearch()
                            PlayerBar.MORE -> onMore()
                            PlayerBar.PLAYLIST -> playlist = true
                            PlayerBar.SPEED -> playback = true
                        }
                    }
                }
                // Search is an item like the others here (icon and label): a capsule in one slot was squeezed and, at the
                // left end, cut by the bar's rounded edge.
                val strong = isCenter || on
                val fg by androidx.compose.animation.animateColorAsState(if (strong) colors.textPrimary else colors.textSecondary, label = "bar-fg")
                val lift by androidx.compose.animation.core.animateFloatAsState(
                    if (isCenter && !reduced) 1.08f else 1f,
                    androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 600f),
                    label = "bar-lift"
                )
                Column(
                    Modifier.width(itemWidth).fillMaxHeight().pressScale(source, 0.92f)
                        .clickable(interactionSource = source, indication = null, onClickLabel = label, onClick = tap)
                        .then(
                            if (k == firstAction) {
                                Modifier.drawBehind { drawLine(colors.glassEdge, androidx.compose.ui.geometry.Offset(0f, size.height * 0.3f), androidx.compose.ui.geometry.Offset(0f, size.height * 0.7f), 1.dp.toPx()) }
                            } else {
                                Modifier
                            }
                        )
                        .testTag("bar-item-$k"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            null,
                            tint = fg,
                            modifier = Modifier.size(21.dp).graphicsLayer {
                                scaleX = lift
                                scaleY = lift
                            }
                        )
                    }
                    Text(
                        label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = fg,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium)
                    )
                }
            }
        }
    }
    if (playback) PlaybackDialog(state, actions) { playback = false }
    if (playlist) PlaylistPickerDialog(state, actions) { playlist = false }
}

/** What a tap on an item of the sliding player bar does; items that open something of the bar itself go to [local]. */
private fun runPlayerBarItem(k: String, app: com.streamshell.core.browser.SiteApp, state: BrowserUiState, actions: BrowserActions, local: (String) -> Unit) {
    when (k) {
        PlayerBar.SEARCH, PlayerBar.MORE, PlayerBar.PLAYLIST, PlayerBar.SPEED -> local(k)

        PlayerBar.HOME, PlayerBar.SUBSCRIPTIONS, PlayerBar.EXPLORE -> app.destinations.firstOrNull { it.id == k }?.let(actions.onSiteDestination)

        // YouTube Music has its own Library section; on YouTube it is StreamShell's Library.
        PlayerBar.LIBRARY -> if (app == com.streamshell.core.browser.SiteApp.YOUTUBE_MUSIC) {
            app.destinations.firstOrNull { it.id == k }?.let(actions.onSiteDestination)
        } else {
            actions.onNav(NavAction.Open(Overlay.Library))
        }

        PlayerBar.PREVIOUS -> actions.onTrack(false)

        PlayerBar.NEXT -> actions.onTrack(true)

        PlayerBar.LATER -> actions.onToggleWatchLater()

        PlayerBar.QUEUE -> actions.onAddToQueue()

        PlayerBar.REPEAT -> actions.onSetRepeatVideo(!state.player.loop)

        PlayerBar.CHAPTER -> actions.onNextChapter()
    }
}

/** Icon, label and "on" state of one item of the sliding player bar. */
@Composable
private fun playerBarItem(k: String, state: BrowserUiState): Triple<androidx.compose.ui.graphics.vector.ImageVector, String, Boolean> = when (k) {
    PlayerBar.SEARCH -> Triple(StreamIcons.Search, "Search", false)
    PlayerBar.HOME -> Triple(siteIcon("home", false), "Home", false)
    PlayerBar.SUBSCRIPTIONS -> Triple(siteIcon("subscriptions", false), "Subscriptions", false)
    PlayerBar.LIBRARY -> if (state.onMusic) Triple(siteIcon("library", false), "Library", false) else Triple(StreamIcons.Bookmarks, "Library", false)
    PlayerBar.LATER -> Triple(StreamIcons.WatchLater, if (state.isWatchLater) "Saved" else "Later", state.isWatchLater)
    PlayerBar.QUEUE -> Triple(StreamIcons.Queue, "Queue", false)
    PlayerBar.PLAYLIST -> Triple(StreamIcons.Bookmarks, "Playlist", false)
    PlayerBar.SPEED -> Triple(StreamIcons.Speed, (state.settings.playbackSpeed / 100.0).toString().removeSuffix(".0") + "x", state.settings.playbackSpeed != 100)
    PlayerBar.REPEAT -> Triple(StreamIcons.Reload, "Repeat", state.player.loop || state.player.repeatTo != null)
    PlayerBar.CHAPTER -> Triple(StreamIcons.Chapters, "Chapter", false)
    PlayerBar.EXPLORE -> Triple(siteIcon("explore", false), "Explore", false)
    PlayerBar.PREVIOUS -> Triple(StreamIcons.Previous, "Previous", false)
    PlayerBar.NEXT -> Triple(StreamIcons.Next, "Next", false)
    else -> Triple(StreamIcons.More, "More", false)
}

/** "More" on a video: the player actions as a 3-column grid at the top of the menu. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PlayerActionGrid(state: BrowserUiState, actions: BrowserActions, onDismiss: () -> Unit, onPlaylist: () -> Unit) {
    var playback by rememberSaveable { mutableStateOf(false) }
    if (playback) {
        PlaybackDialog(state, actions) {
            playback = false
            onDismiss()
        }
    }
    val tiles = buildList {
        add(Triple(StreamIcons.WatchLater, if (state.isWatchLater) "Saved" else "Later", actions.onToggleWatchLater))
        add(Triple(StreamIcons.Queue, "Queue", actions.onAddToQueue))
        add(Triple(StreamIcons.Bookmarks, "Playlist", onPlaylist))
        add(Triple(StreamIcons.Speed, "Speed", { playback = true }))
        add(Triple(StreamIcons.Reload, if (state.player.loop) "Repeat on" else "Repeat", { actions.onSetRepeatVideo(!state.player.loop) }))
        if (state.page.chapters.size >= 2) add(Triple(StreamIcons.Chapters, "Chapter", actions.onNextChapter))
    }
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().testTag("player-action-grid"),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3
    ) {
        val view = androidx.compose.ui.platform.LocalView.current
        tiles.forEach { (icon, label, run) ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(Radius.field)).background(StreamTheme.colors.surfaceRaised)
                    .clickable {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                        run()
                    }
                    .padding(vertical = 12.dp).testTag("grid-$label"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, null, Modifier.size(22.dp), tint = StreamTheme.colors.textPrimary)
                Text(label, style = MaterialTheme.typography.labelMedium, color = StreamTheme.colors.textSecondary, maxLines = 1)
            }
        }
    }
}

/** Longest the black loading band stays over a YouTube video page (fallback; it normally goes when the video plays). */
private const val LOADING_BAND_MAX_MS = 3000L

/**
 * Observes a sideways swipe that starts over the music player (its video/picture, else the top square of the page) and
 * asks for the next (swipe left) or previous (swipe right) track. Nothing is consumed: the page still gets the touch.
 */
private fun Modifier.trackSwipe(video: com.streamshell.core.media.VideoRect?, onTrack: (Boolean) -> Unit): Modifier = pointerInput(video) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
        val p = down.position
        val inPlayer = if (video != null && video.width > 0f) {
            p.x >= video.left && p.x <= video.left + video.width && p.y >= video.top && p.y <= video.top + video.height
        } else {
            p.y <= size.width
        }
        if (!inPlayer) return@awaitEachGesture
        var last = p
        while (true) {
            val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
            last = ch.position
            if (!ch.pressed) break
        }
        PlayerBar.trackSwipe((last.x - p.x) / density, (last.y - p.y) / density)?.let(onTrack)
    }
}

/** Placeholders under the loading player: title, channel row, action chips and two next videos (static with reduced motion). */
@Composable
private fun LoadingPagePlaceholder() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp).testTag("youtube-loading-placeholder"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        com.streamshell.designsystem.Skeleton(Modifier.fillMaxWidth(0.92f).height(18.dp))
        com.streamshell.designsystem.Skeleton(Modifier.fillMaxWidth(0.6f).height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.streamshell.designsystem.Skeleton(Modifier.size(36.dp), androidx.compose.foundation.shape.CircleShape)
            com.streamshell.designsystem.Skeleton(Modifier.width(140.dp).height(14.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(LOADING_CHIPS) { com.streamshell.designsystem.Skeleton(Modifier.width(76.dp).height(32.dp), RoundedCornerShape(Radius.pill)) }
        }
        repeat(LOADING_CARDS) {
            Spacer(Modifier.height(4.dp))
            com.streamshell.designsystem.Skeleton(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            com.streamshell.designsystem.Skeleton(Modifier.fillMaxWidth(0.8f).height(14.dp))
        }
    }
}

private const val LOADING_CHIPS = 3
private const val LOADING_CARDS = 2
