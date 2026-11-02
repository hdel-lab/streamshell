package com.streamshell.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import com.streamshell.core.adaptive.WindowLayoutPolicy
import com.streamshell.core.browser.BrowserTab
import com.streamshell.core.browser.PageState
import com.streamshell.core.browser.TabsState
import com.streamshell.core.model.ThemeMode
import com.streamshell.designsystem.StreamShellTheme
import com.streamshell.feature.browser.BrowserActions
import com.streamshell.feature.browser.BrowserChrome
import com.streamshell.feature.browser.BrowserScreen
import com.streamshell.feature.browser.BrowserUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 0.6.2: switching from the YouTube instance (video in the mini player) to YouTube Music must not crash the screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp", application = android.app.Application::class)
class MusicSwitchUiTest {
    @get:Rule val rule = createComposeRule()

    private val yt = BrowserTab(3, "https://m.youtube.com/", "YouTube", showingStart = false, instance = "YOUTUBE")
    private val player = BrowserTab(6, "https://m.youtube.com/watch?v=FOj5mu3hL64", "Video", showingStart = false, playerFor = 3)
    private val music = BrowserTab(7, "https://music.youtube.com/", "YouTube Music", showingStart = false, instance = "YOUTUBE_MUSIC")

    private fun states(): List<BrowserUiState> {
        val onPlayer = BrowserUiState(tabs = TabsState(listOf(yt, player), 6), page = PageState(url = player.url))
        val miniOnFeed = BrowserUiState(tabs = TabsState(listOf(yt, player), 3), page = PageState(url = yt.url), miniTabId = 6, miniTitle = "Video")
        val musicLoading = BrowserUiState(tabs = TabsState(listOf(yt, player, music), 7), page = PageState(url = null, loading = true), miniTabId = 6)
        val musicHome = BrowserUiState(tabs = TabsState(listOf(yt, player, music), 7), page = PageState(url = music.url), miniTabId = 6)
        val musicWatch = BrowserUiState(tabs = TabsState(listOf(yt, player, music.copy(url = "https://music.youtube.com/watch?v=abcdefghijk")), 7), page = PageState(url = "https://music.youtube.com/watch?v=abcdefghijk"))
        val back = BrowserUiState(tabs = TabsState(listOf(yt, player, music), 3), page = PageState(url = yt.url))
        return listOf(onPlayer, miniOnFeed, musicLoading, musicHome, musicWatch, back)
    }

    @Test fun switchingToMusicAndBackRendersEveryStep() {
        var s by mutableStateOf(states().first())
        rule.setContent {
            StreamShellTheme(ThemeMode.DARK) {
                BrowserScreen(
                    state = s, layout = WindowLayoutPolicy.compute(411, 891, null), actions = BrowserActions(),
                    page = { id, m -> Box(m.testTag("web-$id")) }, fullscreen = null, chrome = BrowserChrome(pipSupported = true),
                    snackbar = SnackbarHostState(), settingsContent = { }, privacyContent = { }
                )
            }
        }
        states().forEach { st ->
            s = st
            rule.waitForIdle()
        }
    }
}
