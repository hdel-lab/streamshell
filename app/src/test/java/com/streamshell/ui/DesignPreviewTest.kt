package com.streamshell.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.streamshell.core.adaptive.WindowLayoutPolicy
import com.streamshell.core.browser.BrowserTab
import com.streamshell.core.browser.PageState
import com.streamshell.core.browser.TabsState
import com.streamshell.core.library.Bookmark
import com.streamshell.core.library.HistoryEntry
import com.streamshell.core.media.MediaState
import com.streamshell.core.model.AppSettings
import com.streamshell.core.model.Overlay
import com.streamshell.core.model.ThemeMode
import com.streamshell.core.navigation.NavState
import com.streamshell.designsystem.StreamShellTheme
import com.streamshell.feature.browser.BrowserActions
import com.streamshell.feature.browser.BrowserChrome
import com.streamshell.feature.browser.BrowserScreen
import com.streamshell.feature.browser.BrowserUiState
import com.streamshell.feature.settings.SettingsActions
import com.streamshell.feature.settings.SettingsScreen
import com.streamshell.feature.settings.SettingsUiState
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the main screens to PNG (app/build/design-preview) so the design can be looked at without a device.
 * It is a preview, not a pixel test: nothing is compared, it only must render.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = android.app.Application::class)
class DesignPreviewTest {
    @get:Rule val rule = createComposeRule()
    private val out = File("build/design-preview").apply { mkdirs() }

    private val library = BrowserUiState(
        bookmarks = listOf(Bookmark("https://vimeo.com/staffpicks", "Staff Picks", 0), Bookmark("https://www.arte.tv/de/", "ARTE", 0)),
        history = listOf(
            HistoryEntry("https://m.youtube.com/watch?v=dQw4w9WgXcQ", "Lofi beats to relax", 0),
            HistoryEntry("https://vimeo.com/123", "A short film", 0),
            HistoryEntry("https://www.dailymotion.com/", "Dailymotion", 0)
        ),
        watch = com.streamshell.core.watch.WatchData(
            positions = com.streamshell.core.watch.WatchRules.record(
                com.streamshell.core.watch.WatchRules.record(emptyList(), "https://vimeo.com/123", "A short film about light", 400.0, 900.0, 1),
                "https://www.arte.tv/de/videos/1",
                "Documentary: The Alps",
                1200.0,
                3000.0,
                2
            )
        )
    )

    /** Stand-in for a web page (real pages need a WebView). */
    @Composable
    private fun FakePage(m: Modifier, dark: Boolean) {
        Column(m.background(if (dark) Color(0xFF0F0F0F) else Color.White).padding(top = 0.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(230.dp).background(Color(0xFF3A4A5E)))
            repeat(4) {
                Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(18.dp).background(if (dark) Color(0xFF2A2A2A) else Color(0xFFE6E6E6)))
                Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth(0.6f).height(12.dp).background(if (dark) Color(0xFF222222) else Color(0xFFEEEEEE)))
            }
        }
    }

    @Composable
    private fun Browser(state: BrowserUiState, mode: ThemeMode, width: Int = 411) {
        root = androidx.compose.ui.platform.LocalView.current
        StreamShellTheme(mode, reducedMotion = true) {
            BrowserScreen(
                state = state, layout = WindowLayoutPolicy.compute(width, 891, null), actions = BrowserActions(),
                page = { _, m -> FakePage(m.testTag("fake-page"), mode == ThemeMode.DARK) }, fullscreen = null, chrome = BrowserChrome(pipSupported = true),
                snackbar = SnackbarHostState(),
                settingsContent = { close -> SettingsScreen(SettingsUiState(AppSettings(), "Protection on · 3 lists"), SettingsActions(), close) },
                privacyContent = {}
            )
        }
    }

    private var root: android.view.View? = null

    private fun save(name: String) {
        rule.waitForIdle()
        rule.runOnIdle {
            val v = root!!.rootView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(bmp))
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private val page = BrowserUiState(
        tabs = TabsState(listOf(BrowserTab(1, "https://vimeo.com/123", "A short film", showingStart = false), BrowserTab(2, null)), 1),
        page = PageState(url = "https://vimeo.com/123", title = "A short film", canGoBack = true, blocked = 12, loading = true, progress = 60)
    )
    private val youtube = BrowserUiState(
        tabs = TabsState(listOf(BrowserTab(1, "https://m.youtube.com/watch?v=dQw4w9WgXcQ", "Video", showingStart = false, instance = "YOUTUBE")), 1),
        page = PageState(url = "https://m.youtube.com/watch?v=dQw4w9WgXcQ", canGoBack = true),
        media = MediaState.PLAYING
    )

    @Test fun startDark() {
        rule.setContent { Browser(library, ThemeMode.DARK) }
        save("01-start-dark")
    }

    @Test fun startLight() {
        rule.setContent { Browser(library, ThemeMode.LIGHT) }
        save("02-start-light")
    }

    @Test fun pageDark() {
        rule.setContent { Browser(page, ThemeMode.DARK) }
        save("03-page-dark")
    }

    @Test fun menuDark() {
        rule.setContent { Browser(page, ThemeMode.DARK) }
        rule.onNodeWithTag("menu-button").performClick()
        save("04-menu-dark")
    }

    @Test fun youtubeDark() {
        rule.setContent { Browser(youtube.copy(page = youtube.page.copy(url = "https://m.youtube.com/feed/subscriptions"), tabs = TabsState(listOf(BrowserTab(1, "https://m.youtube.com/feed/subscriptions", "YouTube", showingStart = false)), 1), media = MediaState.NONE), ThemeMode.DARK) }
        save("05-youtube-dark")
    }

    @Test fun youtubeVideoLight() {
        rule.setContent { Browser(youtube, ThemeMode.LIGHT) }
        save("06-youtube-video-light")
    }

    /** App in dark mode on a light YouTube page: the site bar takes the page's (light) tone. */
    @Test fun youtubeLightPageInDarkApp() {
        rule.setContent { Browser(youtube.copy(page = youtube.page.copy(url = "https://m.youtube.com/feed/subscriptions", background = android.graphics.Color.WHITE), tabs = TabsState(listOf(BrowserTab(1, "https://m.youtube.com/feed/subscriptions", "YouTube", showingStart = false)), 1), media = MediaState.NONE), ThemeMode.DARK) }
        save("12-youtube-light-page-dark-app")
    }

    @Test fun customBar() {
        rule.setContent { Browser(youtube.copy(settings = AppSettings(siteBars = mapOf("YOUTUBE" to listOf("home", "shorts", "subscriptions", "search"))), page = youtube.page.copy(url = "https://m.youtube.com/", background = android.graphics.Color.WHITE), tabs = TabsState(listOf(BrowserTab(1, "https://m.youtube.com/", "YouTube", showingStart = false)), 1), media = MediaState.NONE), ThemeMode.DARK) }
        save("13-custom-bar")
    }

    @Config(qualifiers = "w891dp-h411dp-xhdpi")
    @Test
    fun fullscreenChapters() {
        val chapters = listOf(
            com.streamshell.core.browser.Chapter(0, "Intro"),
            com.streamshell.core.browser.Chapter(41, "Initial deployment"),
            com.streamshell.core.browser.Chapter(90, "Push to B and the long fight for the bridge"),
            com.streamshell.core.browser.Chapter(150, "Result"),
            com.streamshell.core.browser.Chapter(200, "Outro")
        )
        var req by androidx.compose.runtime.mutableStateOf(0)
        rule.setContent {
            root = androidx.compose.ui.platform.LocalView.current
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val video = androidx.compose.runtime.remember { android.view.View(ctx).apply { setBackgroundColor(0xFF2B3A2B.toInt()) } }
            StreamShellTheme(ThemeMode.DARK, reducedMotion = true) {
                BrowserScreen(
                    state = youtube.copy(page = youtube.page.copy(chapters = chapters, chaptersRequest = req)), layout = WindowLayoutPolicy.compute(891, 411, null), actions = BrowserActions(onQueryPosition = { it(50.0) }),
                    page = { _, m -> Box(m) }, fullscreen = video, chrome = BrowserChrome(pipSupported = true), snackbar = SnackbarHostState(),
                    settingsContent = {}, privacyContent = {}
                )
            }
        }
        req = 1
        save("14-fullscreen-chapters")
    }

    @Config(qualifiers = "w673dp-h841dp-xhdpi")
    @Test
    fun foldStackedLive() {
        rule.setContent {
            root = androidx.compose.ui.platform.LocalView.current
            StreamShellTheme(ThemeMode.DARK, reducedMotion = true) {
                BrowserScreen(
                    state = youtube.copy(page = youtube.page.copy(isLive = true, background = android.graphics.Color.WHITE), chatPageId = 98),
                    layout = WindowLayoutPolicy.compute(673, 841, null), actions = BrowserActions(),
                    page = { id, m ->
                        Box(
                            m.background(
                                when (id) {
                                    98L -> Color(0xFFE8F0E8)
                                    99L -> Color(0xFFF2F2F2)
                                    else -> Color.White
                                }
                            )
                        ) {
                            if (id == 1L) Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF3A4A5E)))
                            androidx.compose.material3.Text(
                                when (id) {
                                    98L -> "live chat page"
                                    99L -> "recommendations page"
                                    else -> ""
                                },
                                Modifier.align(androidx.compose.ui.Alignment.Center)
                            )
                        }
                    },
                    fullscreen = null, chrome = BrowserChrome(pipSupported = true), snackbar = SnackbarHostState(),
                    settingsContent = {}, privacyContent = {}
                )
            }
        }
        save("15-fold-stacked-live")
    }

    @Test fun tabsDark() {
        rule.setContent { Browser(page.copy(nav = NavState(Overlay.Tabs)), ThemeMode.DARK) }
        save("07-tabs-dark")
    }

    @Test fun settingsDark() {
        rule.setContent { Browser(page.copy(nav = NavState(Overlay.Settings)), ThemeMode.DARK) }
        save("08-settings-dark")
    }

    @Test fun youtubeMoreDark() {
        rule.setContent { Browser(youtube, ThemeMode.DARK) }
        save("08b-youtube-slide-bar-dark") // 0.5.1: the sliding player bar on an open video
        rule.onNodeWithTag("bar-item-more").performClick()
        save("09-youtube-more-dark")
    }

    @Test fun watchTimeDark() {
        rule.setContent { Browser(library.copy(nav = NavState(Overlay.WatchTime), watch = com.streamshell.core.watch.WatchData(stats = (0..6).associate { java.time.LocalDate.now().minusDays(it.toLong()).toString() to (600L + it * 900L) })), ThemeMode.DARK) }
        save("11-watch-time-dark")
    }

    @Test fun tabletStartDark() {
        rule.setContent { Browser(library, ThemeMode.DARK, width = 900) }
        save("10-wide-start-dark")
    }

    @Test fun appIcon() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val bmp = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val clip = android.graphics.Path().apply { addRoundRect(0f, 0f, 432f, 432f, 100f, 100f, android.graphics.Path.Direction.CW) }
        canvas.clipPath(clip)
        listOf(com.streamshell.R.drawable.ic_launcher_background_gradient, com.streamshell.R.drawable.ic_launcher_foreground).forEach { id ->
            val d = androidx.core.content.ContextCompat.getDrawable(ctx, id)!!
            d.setBounds(-72, -72, 504, 504) // adaptive icon: 108 units, the visible part is the middle 72
            d.draw(canvas)
        }
        File(out, "00-app-icon.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
