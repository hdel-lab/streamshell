package com.streamshell.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.streamshell.core.model.AppSettings
import com.streamshell.core.model.SearchEngine
import com.streamshell.core.model.ThemeMode
import com.streamshell.designsystem.StreamShellTheme
import com.streamshell.feature.settings.SettingsActions
import com.streamshell.feature.settings.SettingsScreen
import com.streamshell.feature.settings.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h9000dp", application = android.app.Application::class)
class SettingsUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun togglesAndEngineUpdateSettings() {
        var s = AppSettings()
        rule.setContent {
            StreamShellTheme(ThemeMode.LIGHT) {
                SettingsScreen(SettingsUiState(s), SettingsActions(onUpdate = { f -> s = f(s) }), onClose = {})
            }
        }
        rule.onNodeWithTag("background-playback").performClick()
        rule.onNodeWithTag("auto-pip").performClick()
        rule.onNodeWithTag("save-history").performClick()
        rule.onNodeWithTag("engine-duckduckgo").performClick()
        rule.onNodeWithTag("sponsor-intro").performClick()
        rule.onNodeWithTag("skip-ads").performClick()
        rule.onNodeWithTag("autoplay").performClick()
        rule.onNodeWithTag("swipe-minimize").performClick()
        assertEquals(false, s.backgroundPlayback)
        assertEquals(false, s.autoPip)
        assertEquals(false, s.saveHistory)
        assertEquals(SearchEngine.DUCKDUCKGO, s.searchEngine)
        assertTrue("intro" in s.sponsorCategories)
        assertEquals(false, s.skipYouTubeAds)
        assertEquals(false, s.autoplay)
        assertEquals(false, s.swipeToMinimize)
    }

    @Test fun newOptionsUpdateSettings() {
        var s = AppSettings()
        rule.setContent {
            StreamShellTheme(ThemeMode.DARK) {
                SettingsScreen(SettingsUiState(s), SettingsActions(onUpdate = { f -> s = f(s) }), onClose = {})
            }
        }
        rule.onNodeWithTag("amoled").performClick()
        rule.onNodeWithTag("accent-green").performClick()
        rule.onNodeWithTag("feed-previews").performClick()
        rule.onNodeWithTag("shorts-as-videos").performClick()
        rule.onNodeWithTag("hide-watched").performClick()
        rule.onNodeWithTag("leave-player-close").performClick()
        rule.onNodeWithTag("captions-off").performClick()
        rule.onNodeWithTag("player-action-row-switch").performClick()
        rule.onNodeWithTag("blocked-words-add").performTextInput("trailer")
        rule.onNodeWithTag("blocked-words-add-button").performClick()
        assertEquals(true, s.amoledBlack)
        assertEquals("green", s.accent)
        assertEquals(true, s.feedPreviews)
        assertEquals(true, s.shortsAsVideos)
        assertEquals(true, s.hideWatched)
        assertEquals("close", s.leavePlayer)
        assertEquals("off", s.captions)
        assertEquals(false, s.playerActionRow)
        assertEquals(setOf("trailer"), s.blockedKeywords)
    }

    @Test fun searchShowsOnlyMatchingGroups() {
        rule.setContent {
            StreamShellTheme(ThemeMode.DARK) { SettingsScreen(SettingsUiState(AppSettings()), SettingsActions(), onClose = {}) }
        }
        rule.onNodeWithTag("settings-search").performTextInput("amoled")
        rule.onNodeWithTag("amoled").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithTag("background-playback").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithTag("skip-ads").fetchSemanticsNodes().size)
    }

    @Test fun clearDataNeedsConfirmationWithChoices() {
        var cleared: Triple<Boolean, Boolean, Boolean>? = null
        rule.setContent {
            StreamShellTheme(ThemeMode.LIGHT) {
                SettingsScreen(SettingsUiState(AppSettings()), SettingsActions(onClearData = { h, c, k -> cleared = Triple(h, c, k) }), onClose = {})
            }
        }
        rule.onNodeWithTag("clear-data").performClick()
        rule.onNodeWithTag("clear-data-dialog").assertIsDisplayed()
        rule.onNodeWithTag("clear-cookies").performClick()
        rule.onNodeWithTag("clear-data-confirm").performClick()
        assertEquals(Triple(true, true, true), cleared)
    }
}
