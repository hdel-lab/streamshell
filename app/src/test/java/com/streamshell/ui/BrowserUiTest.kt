package com.streamshell.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.streamshell.core.adaptive.WindowLayoutPolicy
import com.streamshell.core.browser.BrowserTab
import com.streamshell.core.browser.PageError
import com.streamshell.core.browser.PageState
import com.streamshell.core.browser.TabsState
import com.streamshell.core.library.Bookmark
import com.streamshell.core.library.HistoryEntry
import com.streamshell.core.model.AppSettings
import com.streamshell.core.model.Overlay
import com.streamshell.core.model.ThemeMode
import com.streamshell.core.navigation.NavAction
import com.streamshell.core.navigation.NavState
import com.streamshell.designsystem.StreamShellTheme
import com.streamshell.feature.browser.BlockedNavigation
import com.streamshell.feature.browser.BrowserActions
import com.streamshell.feature.browser.BrowserChrome
import com.streamshell.feature.browser.BrowserScreen
import com.streamshell.feature.browser.BrowserUiState
import com.streamshell.feature.browser.QuickSites
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val PAGE_TAB = TabsState(listOf(BrowserTab(1, "https://vimeo.com/123", "A film", showingStart = false, openedFromStart = true)), 1)
private val ON_PAGE = BrowserUiState(tabs = PAGE_TAB, page = PageState(url = "https://vimeo.com/123", title = "A film", canGoBack = true, blocked = 7))

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp", application = android.app.Application::class)
class BrowserUiTest {
    @get:Rule val rule = createComposeRule()
    private val log = ArrayList<String>()

    private val actions = BrowserActions(
        onSubmitAddress = { log += "submit:$it" }, onOpen = { log += "open:$it" }, onBack = { log += "back" }, onForward = { log += "forward" },
        onHome = { log += "home" }, onOpenSite = { log += "site:$it" }, onSwitchInstance = { log += "instance:${it?.name ?: "start"}" }, onReload = { log += "reload" }, onStop = { log += "stop" }, onNav = { log += "nav:$it" },
        onNewTab = { log += "newtab" }, onSelectTab = { log += "select:$it" }, onCloseTab = { log += "close:$it" }, onCloseAllTabs = { log += "closeall" },
        onToggleBookmark = { log += "bookmark" }, onRemoveBookmark = { log += "rmbookmark:$it" }, onRemoveHistory = { log += "rmhistory:$it" },
        onClearHistory = { log += "clearhistory" }, onToggleSiteExempt = { log += "exempt" }, onResolveBlocked = { log += "blocked:$it" },
        onPip = { log += "pip" }, onShare = { log += "share:$it" },
        onMinimize = { log += "minimize" }, onRestoreMini = { log += "minirestore" }, onCloseMini = { log += "miniclose" }, onToggleMiniPlay = { log += "miniplay" }
    )

    @Composable
    private fun Host(state: BrowserUiState, width: Int = 411, chrome: BrowserChrome = BrowserChrome(pipSupported = true)) {
        StreamShellTheme(ThemeMode.LIGHT) {
            BrowserScreen(
                state = state, layout = WindowLayoutPolicy.compute(width, 891, null), actions = actions,
                page = { id, m -> Box(m.testTag("web-$id")) }, fullscreen = null, chrome = chrome,
                snackbar = SnackbarHostState(), settingsContent = { Box(Modifier.testTag("settings-stub")) }, privacyContent = { Box(Modifier.testTag("privacy-stub")) }
            )
        }
    }

    @Test fun startPageWithOneToolbarAndNoDuplicateNavigation() {
        rule.setContent { Host(BrowserUiState()) }
        rule.onNodeWithTag("start-page").assertIsDisplayed()
        rule.onNodeWithTag("layout-compact").assertIsDisplayed()
        rule.onNodeWithTag("bottom-toolbar").assertIsDisplayed()
        assertEquals(1, rule.onAllNodesWithTag("bottom-toolbar").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithTag("web-1").fetchSemanticsNodes().size) // no web view behind the start page
        rule.onNodeWithTag("back").assertIsNotEnabled()
        rule.onNodeWithTag("forward").assertIsNotEnabled()
    }

    @Test fun quickSiteOpens() {
        rule.setContent { Host(BrowserUiState()) }
        rule.onNodeWithTag("site-YouTube").performClick()
        assertEquals(listOf("site:https://m.youtube.com/"), log)
    }

    @Test fun signInOpensGooglesOwnPage() {
        rule.setContent { Host(BrowserUiState()) }
        rule.onNodeWithTag("start-grid").performScrollToNode(androidx.compose.ui.test.hasTestTag("start-sign-in-button"))
        rule.onNodeWithTag("start-sign-in-button").performClick()
        assertEquals(listOf("open:${QuickSites.GOOGLE_SIGN_IN}"), log)
        assertTrue(QuickSites.GOOGLE_SIGN_IN.startsWith("https://accounts.google.com/"))
    }

    @Test fun addressBarSubmits() {
        rule.setContent { Host(BrowserUiState()) }
        rule.onNodeWithTag("address-bar").performTextReplacement("cat videos")
        rule.onNodeWithTag("address-bar").performImeAction()
        assertEquals(listOf("submit:cat videos"), log)
    }

    @Test fun pageShowsWebViewAndToolbarWorks() {
        rule.setContent { Host(ON_PAGE) }
        rule.onNodeWithTag("web-1").assertIsDisplayed()
        rule.onNodeWithTag("address-bar").assertTextContains("vimeo.com/123", substring = true)
        rule.onNodeWithTag("back").assertIsEnabled().performClick()
        rule.onNodeWithTag("home").performClick()
        rule.onNodeWithTag("reload").performClick()
        rule.onNodeWithTag("tabs-button").performClick()
        assertEquals(listOf("back", "home", "reload", "nav:${NavAction.Open(Overlay.Tabs)}"), log)
    }

    @Test fun shieldShowsRealCountAndSiteToggle() {
        rule.setContent { Host(ON_PAGE) }
        rule.onNodeWithTag("shield").performClick()
        rule.onNodeWithTag("shield-summary").assertTextContains("7 request(s) blocked", substring = true)
        rule.onNodeWithTag("shield-site-toggle").performClick()
        rule.onNodeWithTag("shield-settings").performClick()
        assertEquals(listOf("exempt", "nav:${NavAction.Open(Overlay.Privacy)}"), log)
    }

    @Test fun shieldWhenProtectionOff() {
        rule.setContent { Host(ON_PAGE.copy(settings = AppSettings(filteringEnabled = false))) }
        rule.onNodeWithTag("shield").performClick()
        rule.onNodeWithTag("shield-summary").assertTextContains("switched off", substring = true)
        assertEquals(0, rule.onAllNodesWithTag("shield-site-toggle").fetchSemanticsNodes().size)
    }

    @Test fun menuActionsOnAPage() {
        rule.setContent { Host(ON_PAGE) }
        for (tag in listOf("menu-bookmark", "menu-pip", "menu-share", "menu-new-tab", "menu-history", "menu-bookmarks", "menu-settings")) {
            rule.onNodeWithTag("menu-button").performClick()
            rule.onNodeWithTag(tag).performClick()
        }
        assertEquals(
            listOf(
                "bookmark",
                "pip",
                "share:https://vimeo.com/123",
                "newtab",
                "nav:${NavAction.Open(Overlay.History)}",
                "nav:${NavAction.Open(Overlay.Bookmarks)}",
                "nav:${NavAction.Open(Overlay.Settings)}"
            ),
            log
        )
    }

    @Test fun pageActionsAreDisabledOnTheStartPageAndPipHiddenWhenUnsupported() {
        rule.setContent { Host(BrowserUiState(), chrome = BrowserChrome(pipSupported = false)) }
        rule.onNodeWithTag("menu-button").performClick()
        rule.onNodeWithTag("menu-bookmark").assertIsNotEnabled()
        rule.onNodeWithTag("menu-share").assertIsNotEnabled()
        assertEquals(0, rule.onAllNodesWithTag("menu-pip").fetchSemanticsNodes().size)
    }

    @Test fun errorStateOffersRetry() {
        rule.setContent { Host(ON_PAGE.copy(page = ON_PAGE.page.copy(error = PageError.Offline))) }
        rule.onNodeWithTag("page-error").assertIsDisplayed()
        rule.onNodeWithTag("page-retry").performClick()
        assertEquals(listOf("reload"), log)
    }

    @Test fun wideLayoutPutsEverythingInOneTopBar() {
        rule.setContent { Host(ON_PAGE, width = 900) }
        rule.onNodeWithTag("layout-wide").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithTag("bottom-toolbar").fetchSemanticsNodes().size)
        rule.onNodeWithTag("forward").assertIsDisplayed()
        rule.onNodeWithTag("tabs-button").assertIsDisplayed()
    }

    @Test fun pipShowsOnlyThePage() {
        rule.setContent { Host(ON_PAGE, chrome = BrowserChrome(inPip = true, pipSupported = true)) }
        rule.onNodeWithTag("pip-content", useUnmergedTree = true).assertExists()
        assertEquals(0, rule.onAllNodesWithTag("top-bar").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithTag("bottom-toolbar").fetchSemanticsNodes().size)
    }

    @Test fun pipKeepsTheSamePageAndShowsNothingElse() {
        var chrome by mutableStateOf(BrowserChrome(pipSupported = true))
        rule.setContent { Host(ON_PAGE, chrome = chrome) }
        rule.onNodeWithTag("web-1").assertIsDisplayed()
        chrome = chrome.copy(inPip = true, pipVideo = com.streamshell.core.media.VideoRect(0f, 0f, 400f, 225f))
        rule.waitForIdle()
        assertEquals(1, rule.onAllNodesWithTag("web-1").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithTag("bottom-toolbar").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithTag("address-bar").fetchSemanticsNodes().size)
    }

    private fun ytState(url: String, settings: AppSettings = AppSettings()) = BrowserUiState(
        tabs = TabsState(listOf(BrowserTab(1, url, "YouTube", showingStart = false)), 1),
        page = PageState(url = url, blocked = 3),
        settings = settings
    )

    @Test fun youTubeOpensAsAnAppWithoutBrowserBars() {
        rule.setContent { Host(ytState("https://m.youtube.com/feed/subscriptions")) }
        rule.onNodeWithTag("layout-site-app").assertIsDisplayed()
        rule.onNodeWithTag("site-bar").assertIsDisplayed()
        rule.onNodeWithTag("site-subscriptions").assertIsSelected()
        assertEquals(0, rule.onAllNodesWithTag("address-bar").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithTag("bottom-toolbar").fetchSemanticsNodes().size)
        rule.onNodeWithTag("web-1").assertIsDisplayed()
    }

    @Test fun siteBarDestinationsAndMoreMenu() {
        val opened = ArrayList<String>()
        rule.setContent {
            StreamShellTheme(ThemeMode.LIGHT) {
                BrowserScreen(
                    state = ytState("https://m.youtube.com/"), layout = WindowLayoutPolicy.compute(411, 891, null),
                    actions = actions.copy(onSiteDestination = { opened += it.id }, onSiteSearch = { opened += "search:$it" }),
                    page = { id, m -> Box(m.testTag("web-$id")) }, fullscreen = null, chrome = BrowserChrome(pipSupported = true),
                    snackbar = SnackbarHostState(), settingsContent = {}, privacyContent = {}
                )
            }
        }
        // Shorts and Live are under More; the bar holds Home, Subscriptions, You, search and More.
        assertEquals(0, rule.onAllNodesWithTag("site-live").fetchSemanticsNodes().size)
        rule.onNodeWithTag("site-more").performClick()
        rule.onNodeWithTag("site-live").performClick()
        rule.onNodeWithTag("site-more").performClick()
        rule.onNodeWithTag("site-shorts").performClick()
        rule.onNodeWithTag("site-more").performClick()
        rule.onNodeWithTag("site-search").performTextReplacement("lofi")
        rule.onNodeWithTag("site-search").performImeAction()
        rule.onNodeWithTag("site-more").performClick()
        rule.onNodeWithTag("site-menu-start").performClick()
        assertEquals(listOf("live", "shorts", "search:lofi"), opened)
        assertEquals(listOf("instance:start"), log)
    }

    @Test fun searchCapsuleOpensASearchFieldInTheBar() {
        val searched = ArrayList<String>()
        rule.setContent {
            StreamShellTheme(ThemeMode.DARK) {
                BrowserScreen(
                    state = ytState("https://m.youtube.com/"), layout = WindowLayoutPolicy.compute(411, 891, null),
                    actions = actions.copy(onSiteSearch = { searched += it }),
                    page = { id, m -> Box(m.testTag("web-$id")) }, fullscreen = null, chrome = BrowserChrome(pipSupported = true),
                    snackbar = SnackbarHostState(), settingsContent = {}, privacyContent = {}
                )
            }
        }
        rule.onNodeWithTag("site-home").assertIsDisplayed()
        rule.onNodeWithTag("site-subscriptions").assertIsDisplayed()
        rule.onNodeWithTag("site-search-capsule").performClick()
        rule.onNodeWithTag("site-bar-search").performTextReplacement("lofi hip hop")
        rule.onNodeWithTag("site-bar-search").performImeAction()
        assertEquals(listOf("lofi hip hop"), searched)
        rule.onNodeWithTag("site-bar").assertIsDisplayed() // back to the bar after searching
    }

    @Test fun searchShowsRecentSearchesAtOnceAndSuggestionsWhileTyping() {
        val searched = ArrayList<String>()
        rule.setContent {
            StreamShellTheme(ThemeMode.DARK) {
                BrowserScreen(
                    state = ytState("https://m.youtube.com/"), layout = WindowLayoutPolicy.compute(411, 891, null),
                    actions = actions.copy(
                        onSiteSearch = { searched += it },
                        searchSuggestions = { q -> if (q.isBlank()) listOf("lofi hip hop") else listOf("$q trailer", "$q 2026") }
                    ),
                    page = { id, m -> Box(m.testTag("web-$id")) }, fullscreen = null, chrome = BrowserChrome(pipSupported = true),
                    snackbar = SnackbarHostState(), settingsContent = {}, privacyContent = {}
                )
            }
        }
        rule.onNodeWithTag("site-search-capsule").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Recent searches").assertIsDisplayed() // before typing anything
        rule.onNodeWithText("lofi hip hop").assertIsDisplayed()
        rule.onNodeWithTag("site-bar-search").performTextReplacement("gta")
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        rule.onNodeWithText("gta trailer").assertIsDisplayed()
        rule.onNodeWithTag("site-search-suggestion-0").performClick()
        assertEquals(listOf("gta trailer"), searched)
    }

    @Test fun bottomBarFollowsTheUsersArrangement() {
        // Shorts moved onto the bar, search capsule at the end, You hidden (→ More).
        val custom = AppSettings(siteBars = mapOf("YOUTUBE" to listOf("home", "shorts", "subscriptions", "search")))
        rule.setContent { Host(ytState("https://m.youtube.com/", custom)) }
        rule.onNodeWithTag("site-shorts").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithTag("site-you").fetchSemanticsNodes().size)
        val x = { tag: String -> rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.left }
        assertTrue(x("site-home") < x("site-shorts") && x("site-shorts") < x("site-subscriptions") && x("site-subscriptions") < x("site-search-capsule"))
        assertTrue(x("site-search-capsule") < x("site-more"))
    }

    @Test fun barEditorTogglesAndMovesItems() {
        val changes = ArrayList<List<String>>()
        rule.setContent {
            StreamShellTheme(ThemeMode.DARK) {
                BrowserScreen(
                    state = ytState("https://m.youtube.com/"), layout = WindowLayoutPolicy.compute(411, 891, null),
                    actions = actions.copy(onSiteBarChange = { _, keys -> changes += keys }),
                    page = { id, m -> Box(m.testTag("web-$id")) }, fullscreen = null, chrome = BrowserChrome(pipSupported = true),
                    snackbar = SnackbarHostState(), settingsContent = {}, privacyContent = {}
                )
            }
        }
        rule.onNodeWithTag("site-more").performClick()
        rule.onNodeWithTag("site-menu-customize-bar").performScrollTo().performClick()
        rule.onNodeWithTag("site-bar-editor").assertIsDisplayed()
        rule.onNodeWithTag("bar-toggle-ss-library").performClick()
        assertEquals(listOf("search", "home", "subscriptions", "you"), changes.last())
        rule.onNodeWithTag("bar-down-search").performClick()
        assertEquals(listOf("home", "search", "subscriptions", "you", "ss-library"), changes.last())
        rule.onNodeWithTag("bar-toggle-you").performClick()
        assertEquals(listOf("search", "home", "subscriptions", "ss-library"), changes.last())
    }

    @Test fun openVideoGetsTheSlidingPlayerBarStartingOnSpeed() {
        val log = ArrayList<String>()
        rule.setContent {
            StreamShellTheme(ThemeMode.DARK) {
                BrowserScreen(
                    state = ytState("https://m.youtube.com/watch?v=dQw4w9WgXcQ"), layout = WindowLayoutPolicy.compute(411, 891, null),
                    actions = actions.copy(onAddToQueue = { log += "queue" }, onSetRepeatVideo = { log += "repeat $it" }),
                    page = { id, m -> Box(m.testTag("web-$id")) }, fullscreen = null, chrome = BrowserChrome(),
                    snackbar = SnackbarHostState(), settingsContent = {}, privacyContent = {}
                )
            }
        }
        rule.onNodeWithTag("player-slide-bar").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithTag("site-search-capsule").fetchSemanticsNodes().size) // the normal bar is replaced
        // Five across, Speed in the middle: Queue · Playlist · Speed · Repeat · More; the sections are a swipe away.
        listOf("queue", "playlist", "speed", "repeat", "more").forEach { rule.onNodeWithTag("bar-item-$it").assertIsDisplayed() }
        assertEquals(0, rule.onAllNodesWithTag("bar-item-search").fetchSemanticsNodes().size)
        rule.onNodeWithTag("bar-item-queue").performClick()
        rule.onNodeWithTag("bar-item-repeat").performClick()
        assertEquals(listOf("queue", "repeat true"), log)
        rule.onNodeWithTag("bar-item-more").performClick()
        rule.onNodeWithTag("player-action-grid").assertIsDisplayed()
    }

    @Test fun slidingBarOffKeepsTheNormalBarOnVideos() {
        rule.setContent { Host(ytState("https://m.youtube.com/watch?v=dQw4w9WgXcQ", AppSettings(playerActionRow = false))) }
        assertEquals(0, rule.onAllNodesWithTag("player-slide-bar").fetchSemanticsNodes().size)
        rule.onNodeWithTag("site-bar").assertIsDisplayed()
    }

    @Test fun feedKeepsTheNormalBar() {
        rule.setContent { Host(ytState("https://m.youtube.com/")) }
        assertEquals(0, rule.onAllNodesWithTag("player-slide-bar").fetchSemanticsNodes().size)
    }

    @Test fun moreMenuSwitchesInstances() {
        rule.setContent { Host(ytState("https://m.youtube.com/")) }
        rule.onNodeWithTag("site-more").performClick()
        rule.onNodeWithTag("instance-switcher").assertIsDisplayed()
        rule.onNodeWithTag("instance-youtube").performClick() // already here: nothing reloads
        rule.onNodeWithTag("site-more").performClick()
        rule.onNodeWithTag("instance-youtube_music").performClick()
        assertEquals(listOf("instance:YOUTUBE_MUSIC"), log)
    }

    @Test fun moreMenuCanBringTheBrowserBarsBack() {
        rule.setContent { Host(ytState("https://m.youtube.com/")) }
        rule.onNodeWithTag("site-more").performClick()
        rule.onNodeWithTag("site-menu-browser-bars").performScrollTo().performClick()
        rule.onNodeWithTag("address-bar").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithTag("site-bar").fetchSemanticsNodes().size)
    }

    @Test fun youTubeMusicGetsItsOwnSections() {
        rule.setContent { Host(ytState("https://music.youtube.com/explore")) }
        rule.onNodeWithTag("site-explore").assertIsSelected()
        rule.onNodeWithTag("site-library").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithTag("site-shorts").fetchSemanticsNodes().size)
    }

    @Test fun otherSitesUseTheNormalBrowserBars() {
        rule.setContent { Host(ON_PAGE) }
        assertEquals(0, rule.onAllNodesWithTag("site-bar").fetchSemanticsNodes().size)
        rule.onNodeWithTag("address-bar").assertIsDisplayed()
    }

    @Test fun appModeOffShowsBrowserBarsOnYouTube() {
        rule.setContent { Host(ytState("https://m.youtube.com/", AppSettings(youtubeCompact = false))) }
        assertEquals(0, rule.onAllNodesWithTag("site-bar").fetchSemanticsNodes().size)
        rule.onNodeWithTag("address-bar").assertIsDisplayed()
    }

    @Test fun startPageOffersLibraryAndContinueWatching() {
        val w = com.streamshell.core.watch.WatchData(positions = com.streamshell.core.watch.WatchRules.record(emptyList(), "https://vimeo.com/1", "Film", 100.0, 600.0, 1))
        val opened = ArrayList<String>()
        rule.setContent {
            StreamShellTheme(ThemeMode.DARK) {
                BrowserScreen(
                    state = BrowserUiState(watch = w), layout = WindowLayoutPolicy.compute(411, 891, null), actions = actions.copy(onOpenVideo = { opened += it }),
                    page = { id, m -> Box(m.testTag("web-$id")) }, fullscreen = null, chrome = BrowserChrome(),
                    snackbar = SnackbarHostState(), settingsContent = {}, privacyContent = {}
                )
            }
        }
        rule.onNodeWithTag("start-watch-later").performClick()
        rule.onNodeWithTag("continue-https://vimeo.com/1").performClick()
        assertEquals(listOf("https://vimeo.com/1"), opened)
        assertTrue(log.any { it.contains("WatchLater") })
    }

    @Test fun playbackDialogSetsSpeedSleepAndSeeks() {
        val calls = ArrayList<String>()
        val st = ON_PAGE.copy(media = com.streamshell.core.media.MediaState.PLAYING)
        rule.setContent {
            StreamShellTheme(ThemeMode.DARK) {
                BrowserScreen(
                    state = st, layout = WindowLayoutPolicy.compute(411, 891, null),
                    actions = actions.copy(onSetSpeed = { calls += "speed:$it" }, onSleep = { calls += "sleep:$it" }, onSeek = { calls += "seek:$it" }),
                    page = { id, m -> Box(m.testTag("web-$id")) }, fullscreen = null, chrome = BrowserChrome(),
                    snackbar = SnackbarHostState(), settingsContent = {}, privacyContent = {}
                )
            }
        }
        rule.onNodeWithTag("menu-button").performClick()
        rule.onNodeWithTag("menu-playback").performClick()
        rule.onNodeWithTag("speed-150").performClick()
        rule.onNodeWithTag("sleep-30").performClick()
        rule.onNodeWithTag("playback-fwd10").performClick()
        assertEquals(listOf("speed:150", "sleep:30", "seek:true"), calls)
    }

    @Test fun subscriptionsPanelAsksForTheTakeoutFile() {
        var asked = 0
        rule.setContent {
            StreamShellTheme(ThemeMode.DARK) {
                BrowserScreen(
                    state = BrowserUiState(nav = NavState(Overlay.Subscriptions)), layout = WindowLayoutPolicy.compute(411, 891, null),
                    actions = actions.copy(onImportSubscriptions = { asked++ }),
                    page = { id, m -> Box(m.testTag("web-$id")) }, fullscreen = null, chrome = BrowserChrome(),
                    snackbar = SnackbarHostState(), settingsContent = {}, privacyContent = {}
                )
            }
        }
        rule.onNodeWithTag("subscriptions-import").performClick()
        assertEquals(1, asked)
    }

    @Config(qualifiers = "w673dp-h841dp")
    @Test
    fun openFoldableNormalVideoUsesTheWholeScreen() {
        val asked = ArrayList<Boolean>()
        val url = "https://m.youtube.com/watch?v=dQw4w9WgXcQ"
        rule.setContent {
            StreamShellTheme(ThemeMode.DARK) {
                BrowserScreen(
                    state = BrowserUiState(tabs = TabsState(listOf(BrowserTab(1, url, "Video", showingStart = false)), 1), page = PageState(url = url)),
                    layout = WindowLayoutPolicy.compute(673, 841, null), actions = actions.copy(onRecommendationsColumn = { asked += it }),
                    page = { id, m -> Box(m.testTag("web-$id")) }, fullscreen = null, chrome = BrowserChrome(),
                    snackbar = SnackbarHostState(), settingsContent = {}, privacyContent = {}
                )
            }
        }
        rule.onNodeWithTag("web-1").assertIsDisplayed()
        assertEquals(listOf(true), asked)
        // No extra area, no second list, no chat button for a normal video.
        assertEquals(0, rule.onAllNodesWithTag("split-divider").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithTag("open-live-chat").fetchSemanticsNodes().size)
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val video = rule.onNodeWithTag("web-1").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(video.width - root.width) < 2f)
    }

    @Config(qualifiers = "w673dp-h841dp")
    @Test
    fun openFoldableLiveStreamOffersTheChatWindow() {
        val opened = ArrayList<Boolean>()
        val url = "https://m.youtube.com/watch?v=dQw4w9WgXcQ"
        var chatId by androidx.compose.runtime.mutableStateOf<Long?>(null)
        rule.setContent {
            StreamShellTheme(ThemeMode.DARK) {
                BrowserScreen(
                    state = BrowserUiState(tabs = TabsState(listOf(BrowserTab(1, url, "Live", showingStart = false)), 1), page = PageState(url = url, isLive = true), chatPageId = chatId),
                    layout = WindowLayoutPolicy.compute(673, 841, null), actions = actions.copy(onWideChat = { opened += it }),
                    page = { id, m -> Box(m.testTag("web-$id")) }, fullscreen = null, chrome = BrowserChrome(),
                    snackbar = SnackbarHostState(), settingsContent = {}, privacyContent = {}
                )
            }
        }
        rule.onNodeWithTag("open-live-chat").performClick()
        assertEquals(listOf(true), opened)
        chatId = 98L // the app opened the chat page
        rule.waitForIdle()
        rule.onNodeWithTag("live-chat-card").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithTag("open-live-chat").fetchSemanticsNodes().size)
        val video = rule.onNodeWithTag("web-1").fetchSemanticsNode().boundsInRoot
        val chat = rule.onNodeWithTag("web-98").fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(video.width - root.width) < 2f) // the video page keeps the whole width
        assertTrue(chat.left > root.width * 0.4f && chat.right < root.right && chat.bottom < root.bottom) // floating chat window bottom right, half the width
        rule.onNodeWithTag("live-chat-close").performClick()
        assertEquals(listOf(true, false), opened)
    }

    @Test fun phoneWidthHasNoRecommendationsColumn() {
        val asked = ArrayList<Boolean>()
        val url = "https://m.youtube.com/watch?v=dQw4w9WgXcQ"
        rule.setContent {
            StreamShellTheme(ThemeMode.DARK) {
                BrowserScreen(
                    state = BrowserUiState(tabs = TabsState(listOf(BrowserTab(1, url, "Video", showingStart = false)), 1), page = PageState(url = url)),
                    layout = WindowLayoutPolicy.compute(411, 891, null), actions = actions.copy(onRecommendationsColumn = { asked += it }),
                    page = { id, m -> Box(m.testTag("web-$id")) }, fullscreen = null, chrome = BrowserChrome(),
                    snackbar = SnackbarHostState(), settingsContent = {}, privacyContent = {}
                )
            }
        }
        assertEquals(0, rule.onAllNodesWithTag("web-99").fetchSemanticsNodes().size)
        assertEquals(listOf(false), asked)
    }

    @Test fun doubleTapOnAVideoJumpsOnOtherSites() {
        val seeks = ArrayList<Boolean>()
        val st = ON_PAGE.copy(media = com.streamshell.core.media.MediaState.PLAYING)
        rule.setContent {
            StreamShellTheme(ThemeMode.DARK) {
                BrowserScreen(
                    state = st, layout = WindowLayoutPolicy.compute(411, 891, null), actions = actions.copy(onSeek = { seeks += it }),
                    page = { id, m -> Box(m.testTag("web-$id")) }, fullscreen = null,
                    chrome = BrowserChrome(pipVideo = com.streamshell.core.media.VideoRect(0f, 0f, 400f, 225f)),
                    snackbar = SnackbarHostState(), settingsContent = {}, privacyContent = {}
                )
            }
        }
        rule.onNodeWithTag("page").performTouchInput { doubleClick(androidx.compose.ui.geometry.Offset(350f, 100f)) }
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithTag("page").performTouchInput { doubleClick(androidx.compose.ui.geometry.Offset(50f, 100f)) }
        assertEquals(listOf(true, false), seeks)
    }

    @Test fun loadingYouTubeVideoGetsABlackBandUntilItPlays() {
        val st = ytState("https://m.youtube.com/watch?v=dQw4w9WgXcQ")
        var media by androidx.compose.runtime.mutableStateOf(com.streamshell.core.media.MediaState.NONE)
        rule.setContent { Host(st.copy(media = media), chrome = BrowserChrome(pipSupported = true, pipVideo = com.streamshell.core.media.VideoRect(0f, 0f, 411f, 231f))) }
        rule.onNodeWithTag("youtube-loading-band").assertExists()
        media = com.streamshell.core.media.MediaState.PLAYING
        rule.waitForIdle() // recompose with "playing" first
        rule.mainClock.advanceTimeBy(1000) // then 300 ms + the fade (well before the 3 s fallback)
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithTag("youtube-loading-band").fetchSemanticsNodes().size)
    }

    @Test fun loadingBandGoesWhenTheVideoIsConfirmedEvenIfItsPictureIsNotAtTheTop() {
        // 0.5.5: a live stream / non-16:9 video whose picture starts lower in the player kept the band for 8 s.
        val st = ytState("https://m.youtube.com/watch?v=dQw4w9WgXcQ").copy(media = com.streamshell.core.media.MediaState.PLAYING)
        var player by androidx.compose.runtime.mutableStateOf(com.streamshell.feature.browser.PlayerExtras())
        rule.setContent { Host(st.copy(player = player), chrome = BrowserChrome(pipSupported = true, pipVideo = com.streamshell.core.media.VideoRect(0f, 60f, 411f, 231f))) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithTag("youtube-loading-band").assertExists() // playing, but not this video confirmed and not at the top
        player = com.streamshell.feature.browser.PlayerExtras(readyVideoId = "dQw4w9WgXcQ")
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithTag("youtube-loading-band").fetchSemanticsNodes().size)
    }

    @Test fun loadingVideoShowsItsPictureInsteadOfBlack() {
        rule.setContent { Host(ytState("https://m.youtube.com/watch?v=dQw4w9WgXcQ")) }
        rule.onNodeWithTag("youtube-loading-thumb").assertExists()
    }

    @Test fun loadingBandFallbackIsThreeSeconds() {
        val st = ytState("https://m.youtube.com/watch?v=dQw4w9WgXcQ")
        rule.setContent { Host(st) }
        rule.mainClock.advanceTimeBy(2000)
        rule.onNodeWithTag("youtube-loading-band").assertExists()
        rule.mainClock.advanceTimeBy(1600)
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithTag("youtube-loading-band").fetchSemanticsNodes().size)
    }

    @Test fun videoPageHasNoFloatingButtonsGesturesDoTheWork() {
        val st = ytState("https://m.youtube.com/watch?v=dQw4w9WgXcQ").copy(media = com.streamshell.core.media.MediaState.PLAYING)
        rule.setContent { Host(st.copy(page = st.page.copy(canGoBack = true))) }
        assertEquals(0, rule.onAllNodesWithTag("minimize").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithTag("floating-nav").fetchSemanticsNodes().size)
    }

    @Composable
    private fun PullHost(state: BrowserUiState, atTop: Boolean) {
        StreamShellTheme(ThemeMode.DARK) {
            BrowserScreen(
                state = state, layout = WindowLayoutPolicy.compute(411, 891, null), actions = actions.copy(isPageAtTop = { atTop }),
                page = { id, m -> Box(m.testTag("web-$id")) }, fullscreen = null,
                chrome = BrowserChrome(pipVideo = com.streamshell.core.media.VideoRect(0f, 0f, 1000f, 300f)),
                snackbar = SnackbarHostState(), settingsContent = {}, privacyContent = {}
            )
        }
    }

    @Test fun pullDownAtTheTopRefreshesBelowTheVideo() {
        val st = ytState("https://m.youtube.com/watch?v=dQw4w9WgXcQ").copy(media = com.streamshell.core.media.MediaState.PLAYING)
        rule.setContent { PullHost(st, atTop = true) }
        rule.onNodeWithTag("page").performTouchInput { this.swipe(androidx.compose.ui.geometry.Offset(centerX, 700f), androidx.compose.ui.geometry.Offset(centerX, 1500f), 300) }
        assertEquals(listOf("reload"), log)
    }

    @Test fun pullOnTheVideoStillMinimizesAndShortOrScrolledPullsDoNothing() {
        val st = ytState("https://m.youtube.com/watch?v=dQw4w9WgXcQ").copy(media = com.streamshell.core.media.MediaState.PLAYING)
        rule.setContent { PullHost(st, atTop = true) }
        rule.onNodeWithTag("page").performTouchInput { this.swipe(androidx.compose.ui.geometry.Offset(centerX, 40f), androidx.compose.ui.geometry.Offset(centerX, 600f), 200) }
        rule.onNodeWithTag("page").performTouchInput { this.swipe(androidx.compose.ui.geometry.Offset(centerX, 700f), androidx.compose.ui.geometry.Offset(centerX, 760f), 200) }
        assertEquals(listOf("minimize"), log)
    }

    @Test fun noPullRefreshWhenThePageIsScrolled() {
        rule.setContent { PullHost(ON_PAGE, atTop = false) }
        rule.onNodeWithTag("page").performTouchInput { this.swipe(androidx.compose.ui.geometry.Offset(centerX, 300f), androidx.compose.ui.geometry.Offset(centerX, 1300f), 300) }
        assertEquals(emptyList<String>(), log)
    }

    @Test fun swipeDownOnTheVideoMinimizes() {
        val st = ytState("https://m.youtube.com/watch?v=dQw4w9WgXcQ").copy(media = com.streamshell.core.media.MediaState.PLAYING)
        rule.setContent { Host(st, chrome = BrowserChrome(pipVideo = com.streamshell.core.media.VideoRect(0f, 0f, 1000f, 500f))) }
        rule.onNodeWithTag("page").performTouchInput { this.swipe(androidx.compose.ui.geometry.Offset(centerX, 40f), androidx.compose.ui.geometry.Offset(centerX, 600f), 200) }
        assertEquals(listOf("minimize"), log)
    }

    @Test fun swipesBelowTheVideoOrSidewaysDoNotMinimize() {
        val st = ytState("https://m.youtube.com/watch?v=dQw4w9WgXcQ").copy(media = com.streamshell.core.media.MediaState.PLAYING)
        rule.setContent { Host(st, chrome = BrowserChrome(pipVideo = com.streamshell.core.media.VideoRect(0f, 0f, 1000f, 300f))) }
        rule.onNodeWithTag("page").performTouchInput { this.swipe(androidx.compose.ui.geometry.Offset(centerX, 700f), androidx.compose.ui.geometry.Offset(centerX, 1400f), 200) }
        rule.onNodeWithTag("page").performTouchInput { this.swipe(androidx.compose.ui.geometry.Offset(50f, 100f), androidx.compose.ui.geometry.Offset(900f, 300f), 200) }
        assertEquals(emptyList<String>(), log)
    }

    @Test fun swipeToMinimizeCanBeSwitchedOff() {
        val st = ytState("https://m.youtube.com/watch?v=dQw4w9WgXcQ", AppSettings(swipeToMinimize = false)).copy(media = com.streamshell.core.media.MediaState.PLAYING)
        rule.setContent { Host(st, chrome = BrowserChrome(pipVideo = com.streamshell.core.media.VideoRect(0f, 0f, 1000f, 500f))) }
        rule.onNodeWithTag("page").performTouchInput { this.swipe(androidx.compose.ui.geometry.Offset(centerX, 40f), androidx.compose.ui.geometry.Offset(centerX, 600f), 200) }
        assertEquals(emptyList<String>(), log)
    }

    @Test fun noMinimizeWithoutVideoOrOutsideWatchPages() {
        rule.setContent { Host(ytState("https://m.youtube.com/").copy(media = com.streamshell.core.media.MediaState.PLAYING)) }
        assertEquals(0, rule.onAllNodesWithTag("minimize").fetchSemanticsNodes().size)
    }

    @Test fun miniPlayerMovesSidewaysHidesAtTheEdgeAndComesBack() {
        val tabs = TabsState(
            listOf(BrowserTab(1, "https://m.youtube.com/watch?v=dQw4w9WgXcQ", "Video", showingStart = false), BrowserTab(2, "https://m.youtube.com/", "YouTube", showingStart = false)),
            2
        )
        rule.setContent { Host(BrowserUiState(tabs = tabs, page = PageState(url = "https://m.youtube.com/"), miniTabId = 1, media = com.streamshell.core.media.MediaState.PLAYING)) }
        rule.mainClock.autoAdvance = true
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val start = rule.onNodeWithTag("mini-player").fetchSemanticsNode().boundsInRoot
        val edge = 20f * rule.density.density
        assertTrue(root.right - start.right < edge) // starts at the right edge
        // Drag to the left half: it rests on the left side.
        rule.onNodeWithTag("mini-player").performTouchInput { swipe(center, center.copy(x = center.x - root.width * 0.6f), 300) }
        rule.waitForIdle()
        val left = rule.onNodeWithTag("mini-player").fetchSemanticsNode().boundsInRoot
        assertTrue(left.left < edge) // rests at the left edge
        // Push it over the left edge: hidden, a thin tab stays.
        rule.onNodeWithTag("mini-player").performTouchInput { swipe(center, center.copy(x = center.x - width * 0.5f), 300) }
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithTag("mini-player").fetchSemanticsNodes().size)
        val tab = rule.onNodeWithTag("mini-tab").fetchSemanticsNode().boundsInRoot
        assertTrue(tab.left < 1f && tab.width < 30f * rule.density.density)
        // Tap the tab: back.
        rule.onNodeWithTag("mini-tab").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("mini-player").assertExists()
        assertEquals(0, rule.onAllNodesWithTag("mini-tab").fetchSemanticsNodes().size)
        assertEquals(emptyList<String>(), log.filter { it == "minirestore" }) // dragging never opened the video
    }

    @Test fun miniPlayerShowsTheVideoTabAndItsControls() {
        val tabs = TabsState(
            listOf(BrowserTab(1, "https://m.youtube.com/watch?v=dQw4w9WgXcQ", "Video", showingStart = false), BrowserTab(2, "https://m.youtube.com/", "YouTube", showingStart = false)),
            2
        )
        rule.setContent { Host(BrowserUiState(tabs = tabs, page = PageState(url = "https://m.youtube.com/"), miniTabId = 1, media = com.streamshell.core.media.MediaState.PLAYING)) }
        rule.onNodeWithTag("web-2").assertIsDisplayed() // the page you browse
        rule.onNodeWithTag("web-1", useUnmergedTree = true).assertExists() // the minimized video
        rule.onNodeWithTag("mini-play").performClick()
        rule.onNodeWithTag("mini-close").performClick()
        rule.onNodeWithTag("mini-player").performClick()
        assertEquals(listOf("miniplay", "miniclose", "minirestore"), log)
        assertEquals(0, rule.onAllNodesWithTag("minimize").fetchSemanticsNodes().size) // one mini player at a time
    }

    @Test fun minimizingAndRestoringKeepsTheSamePageInstance() {
        val created = HashMap<Long, Int>()
        val video = BrowserTab(1, "https://m.youtube.com/watch?v=dQw4w9WgXcQ", "Video", showingStart = false)
        var state by mutableStateOf(BrowserUiState(tabs = TabsState(listOf(video), 1), page = PageState(url = video.url), media = com.streamshell.core.media.MediaState.PLAYING))
        rule.setContent {
            StreamShellTheme(ThemeMode.LIGHT) {
                BrowserScreen(
                    state = state, layout = WindowLayoutPolicy.compute(411, 891, null), actions = actions,
                    page = { id, m ->
                        androidx.compose.runtime.remember {
                            created[id] = (created[id] ?: 0) + 1
                            id
                        }
                        Box(m.testTag("web-$id"))
                    },
                    fullscreen = null, chrome = BrowserChrome(), snackbar = SnackbarHostState(), settingsContent = {}, privacyContent = {}
                )
            }
        }
        // minimize: the video tab becomes the mini player, a new tab is shown
        state = state.copy(tabs = TabsState(listOf(video, BrowserTab(2, "https://m.youtube.com/", "YouTube", showingStart = false)), 2), miniTabId = 1)
        rule.waitForIdle()
        rule.onNodeWithTag("mini-player").assertExists()
        // restore
        state = state.copy(tabs = state.tabs.copy(activeId = 1), miniTabId = null)
        rule.waitForIdle()
        assertEquals(1, created[1]) // never re-created: the WebView is not detached or re-loaded
    }

    @Test fun miniTargetPutsTheVideoIntoTheMiniRectangle() {
        val t = com.streamshell.feature.browser.miniTarget(1080, 2000, com.streamshell.core.media.VideoRect(0f, 100f, 1080f, 608f), 500f, 1600f, 540f, 304f)
        // the video's top-left lands on the mini rectangle (within a pixel), and it fits its width
        assertEquals(500f, 0f * t.scale + t.tx, 1.5f)
        assertEquals(1600f, 100f * t.scale + t.ty, 2f)
        assertEquals(540f, 1080f * t.scale, 1.5f)
        assertEquals(100f, t.clip.top, 0.01f)
    }

    @Test fun tabsPanel() {
        val tabs = TabsState(listOf(BrowserTab(1), BrowserTab(2, "https://vimeo.com/1", "Film", showingStart = false)), 2)
        rule.setContent { Host(BrowserUiState(tabs = tabs, nav = NavState(Overlay.Tabs))) }
        rule.onNodeWithTag("tabs-panel").assertIsDisplayed()
        rule.onNodeWithText("Start page").assertIsDisplayed()
        rule.onNodeWithText("Film").assertIsDisplayed()
        rule.onNodeWithTag("tab-1").performClick()
        rule.onNodeWithTag("tab-close-2").performClick()
        rule.onNodeWithTag("tabs-new").performClick()
        rule.onNodeWithTag("tabs-close-all").performClick()
        rule.onNodeWithTag("tabs-close-all-confirm").performClick()
        assertEquals(listOf("select:1", "close:2", "newtab", "closeall"), log)
    }

    @Test fun bookmarksPanelEmptyAndFilled() {
        var state by mutableStateOf(BrowserUiState(nav = NavState(Overlay.Bookmarks)))
        rule.setContent { Host(state) }
        rule.onNodeWithTag("bookmarks-empty").assertIsDisplayed()
        state = state.copy(bookmarks = listOf(Bookmark("https://vimeo.com/1", "Film", 1)))
        rule.onNodeWithTag("bookmark-row").performClick()
        rule.onNodeWithTag("bookmark-remove").performClick()
        assertEquals(listOf("open:https://vimeo.com/1", "rmbookmark:https://vimeo.com/1"), log)
    }

    @Test fun historyPanelSearchRemoveAndClear() {
        val h = listOf(HistoryEntry("https://vimeo.com/1", "Cats", 2), HistoryEntry("https://m.youtube.com/", "YouTube", 1))
        rule.setContent { Host(BrowserUiState(history = h, nav = NavState(Overlay.History))) }
        rule.onNodeWithTag("history-search").performTextReplacement("cats")
        assertEquals(1, rule.onAllNodesWithTag("history-row").fetchSemanticsNodes().size)
        rule.onNodeWithTag("history-remove").performClick()
        rule.onNodeWithTag("history-clear").performClick()
        rule.onNodeWithTag("history-clear-confirm").performClick()
        assertEquals(listOf("rmhistory:https://vimeo.com/1", "clearhistory"), log)
    }

    @Test fun historyOffIsExplained() {
        rule.setContent { Host(BrowserUiState(settings = AppSettings(saveHistory = false), nav = NavState(Overlay.History))) }
        rule.onNodeWithTag("history-off").assertIsDisplayed()
    }

    @Test fun blockedPageAsksTheUser() {
        val b = BlockedNavigation(1, "https://ads.test/x", "ads.test", "||ads.test^", "builtin")
        rule.setContent { Host(BrowserUiState(blockedNavigation = b)) }
        rule.onNodeWithTag("blocked-dialog").assertIsDisplayed()
        rule.onNodeWithText("the built-in list", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("blocked-open").performClick()
        assertEquals(listOf("blocked:true"), log)
    }

    @Test fun settingsAndPrivacyAreFullPanels() {
        rule.setContent { Host(BrowserUiState(nav = NavState(Overlay.Settings))) }
        rule.onNodeWithTag("overlay-settings").assertIsDisplayed()
        rule.onNodeWithTag("settings-stub").assertIsDisplayed()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp", application = android.app.Application::class)
class FullscreenUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun fullscreenVideoCoversTheScreenButKeepsThePageAttached() {
        rule.setContent {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val video = androidx.compose.runtime.remember { android.view.View(ctx) }
            StreamShellTheme(ThemeMode.LIGHT) {
                BrowserScreen(
                    state = ON_PAGE, layout = WindowLayoutPolicy.compute(411, 891, null), actions = BrowserActions(),
                    page = { id, m -> Box(m.testTag("web-$id")) }, fullscreen = video, chrome = BrowserChrome(),
                    snackbar = SnackbarHostState(), settingsContent = {}, privacyContent = {}
                )
            }
        }
        rule.onNodeWithTag("immersive").assertIsDisplayed()
        assertEquals(1, rule.onAllNodesWithTag("web-1").fetchSemanticsNodes().size)
    }

    @Test fun shortFullscreenTapIsCheckedForTheChapterTitle() {
        val taps = ArrayList<Pair<Float, Float>>()
        val chapters = listOf(com.streamshell.core.browser.Chapter(0, "Intro"), com.streamshell.core.browser.Chapter(41, "Initial deployment"))
        rule.setContent {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val video = androidx.compose.runtime.remember { android.view.View(ctx) }
            StreamShellTheme(ThemeMode.DARK) {
                BrowserScreen(
                    state = ON_PAGE.copy(page = ON_PAGE.page.copy(chapters = chapters)), layout = WindowLayoutPolicy.compute(891, 411, null),
                    actions = BrowserActions(onFullscreenTap = { x, y -> taps += x to y }), page = { id, m -> Box(m.testTag("web-$id")) }, fullscreen = video, chrome = BrowserChrome(),
                    snackbar = SnackbarHostState(), settingsContent = {}, privacyContent = {}
                )
            }
        }
        rule.onNodeWithTag("immersive-video").performClick()
        assertEquals(1, taps.size)
    }

    @Test fun fullscreenTapsReachTheVideoAndNoChapterButtonCoversIt() {
        var touches = 0
        val chapters = listOf(com.streamshell.core.browser.Chapter(0, "Intro"), com.streamshell.core.browser.Chapter(20, "Night attack"))
        rule.setContent {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val video = androidx.compose.runtime.remember {
                android.view.View(ctx).apply {
                    setOnTouchListener { _, e ->
                        if (e.actionMasked == android.view.MotionEvent.ACTION_DOWN) touches++
                        true
                    }
                }
            }
            StreamShellTheme(ThemeMode.DARK) {
                BrowserScreen(
                    state = ON_PAGE.copy(page = ON_PAGE.page.copy(chapters = chapters)), layout = WindowLayoutPolicy.compute(891, 411, null),
                    actions = BrowserActions(), page = { id, m -> Box(m.testTag("web-$id")) }, fullscreen = video, chrome = BrowserChrome(),
                    snackbar = SnackbarHostState(), settingsContent = {}, privacyContent = {}
                )
            }
        }
        rule.onNodeWithTag("immersive-video").performClick()
        assertEquals(1, touches) // YouTube's own controls get the tap
        assertEquals(0, rule.onAllNodesWithTag("chapters-button").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithTag("chapters-strip").fetchSemanticsNodes().size)
    }

    @Test fun fullscreenChapterStripJumpsWithoutLeaving() {
        val seeks = ArrayList<Int>()
        val chapters = listOf(com.streamshell.core.browser.Chapter(0, "Intro"), com.streamshell.core.browser.Chapter(20, "Night attack"), com.streamshell.core.browser.Chapter(45, "Hits"))
        var request by androidx.compose.runtime.mutableStateOf(0)
        rule.setContent {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val video = androidx.compose.runtime.remember { android.view.View(ctx) }
            StreamShellTheme(ThemeMode.DARK) {
                BrowserScreen(
                    state = ON_PAGE.copy(page = ON_PAGE.page.copy(chapters = chapters, chaptersRequest = request)), layout = WindowLayoutPolicy.compute(891, 411, null),
                    actions = BrowserActions(onSeekTo = { seeks += it }, onQueryPosition = { cb -> cb(25.0) }),
                    page = { id, m -> Box(m.testTag("web-$id")) }, fullscreen = video, chrome = BrowserChrome(),
                    snackbar = SnackbarHostState(), settingsContent = {}, privacyContent = {}
                )
            }
        }
        request = 1 // the page reports a tap on the chapter title in YouTube's timeline
        rule.waitForIdle()
        rule.onNodeWithTag("chapters-strip").assertIsDisplayed()
        rule.onNodeWithTag("chapter-1").performClick()
        assertEquals(listOf(20), seeks)
        rule.onNodeWithTag("immersive").assertIsDisplayed() // still fullscreen
    }

    @Test fun fullscreenSwipeOnTheRightChangesVolumeTapsStillReachTheVideo() {
        var downs = 0
        var cancels = 0
        rule.setContent {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val video = androidx.compose.runtime.remember {
                android.view.View(ctx).apply {
                    setOnTouchListener { _, e ->
                        if (e.actionMasked == android.view.MotionEvent.ACTION_DOWN) downs++
                        if (e.actionMasked == android.view.MotionEvent.ACTION_CANCEL) cancels++
                        true
                    }
                }
            }
            StreamShellTheme(ThemeMode.DARK) {
                BrowserScreen(
                    state = ON_PAGE, layout = WindowLayoutPolicy.compute(891, 411, null),
                    actions = BrowserActions(), page = { id, m -> Box(m.testTag("web-$id")) }, fullscreen = video, chrome = BrowserChrome(),
                    snackbar = SnackbarHostState(), settingsContent = {}, privacyContent = {}
                )
            }
        }
        rule.onNodeWithTag("immersive-video").performClick()
        assertEquals(1, downs) // a tap is YouTube's
        rule.onNodeWithTag("immersive-video").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width * 0.8f, height * 0.6f), androidx.compose.ui.geometry.Offset(width * 0.8f, height * 0.25f), 300)
        }
        rule.onNodeWithTag("swipe-hud").assertIsDisplayed()
        assertEquals(1, cancels) // the page gets a cancel once the swipe is recognised
    }

    @Test fun watchLaterPlaysAllAndShowsTheQueue() {
        val later = listOf(com.streamshell.core.watch.WatchLaterItem("https://m.youtube.com/watch?v=aaaaaaaaaaa", "First", 1))
        val queue = com.streamshell.feature.browser.PlayQueue(listOf(com.streamshell.feature.browser.QueueItem("https://m.youtube.com/watch?v=bbbbbbbbbbb", "Queued")), 0)
        val calls = ArrayList<String>()
        rule.setContent {
            StreamShellTheme(ThemeMode.LIGHT) {
                BrowserScreen(
                    state = BrowserUiState(nav = com.streamshell.core.navigation.NavState(com.streamshell.core.model.Overlay.WatchLater), watch = com.streamshell.core.watch.WatchData(later = later), queue = queue),
                    layout = WindowLayoutPolicy.compute(411, 891, null),
                    actions = BrowserActions(onPlayAllLater = { calls += "all" }, onPlayQueueFrom = { calls += "from:$it" }, onClearQueue = { calls += "clear" }),
                    page = { id, m -> Box(m.testTag("web-$id")) }, fullscreen = null, chrome = BrowserChrome(),
                    snackbar = SnackbarHostState(), settingsContent = {}, privacyContent = {}
                )
            }
        }
        rule.onNodeWithTag("queue-section").assertIsDisplayed()
        rule.onNodeWithTag("watch-later-play-all").performClick()
        rule.onNodeWithTag("queue-0").performClick()
        rule.onNodeWithTag("queue-clear").performClick()
        assertEquals(listOf("all", "from:0", "clear"), calls)
    }
}
