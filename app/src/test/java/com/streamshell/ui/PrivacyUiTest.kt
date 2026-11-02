package com.streamshell.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.streamshell.core.model.AppSettings
import com.streamshell.core.model.ThemeMode
import com.streamshell.core.privacy.DomainFilterEngine
import com.streamshell.core.privacy.EngineState
import com.streamshell.core.privacy.FilterSources
import com.streamshell.core.privacy.ListEntry
import com.streamshell.core.privacy.ListStatus
import com.streamshell.core.privacy.Subscription
import com.streamshell.designsystem.StreamShellTheme
import com.streamshell.feature.privacy.PrivacyActions
import com.streamshell.feature.privacy.PrivacyScreen
import com.streamshell.feature.privacy.PrivacyUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h4000dp", application = android.app.Application::class)
class PrivacyUiTest {
    @get:Rule val rule = createComposeRule()

    private val sub = Subscription("easyprivacy", "EasyPrivacy", "https://easylist.to/easylist/easyprivacy.txt", "GPLv3", "https://easylist.to/", enabled = true, recommended = true)

    private fun show(state: PrivacyUiState, actions: PrivacyActions = PrivacyActions()) = rule.setContent { StreamShellTheme(ThemeMode.LIGHT) { PrivacyScreen(state, actions, onClose = {}) } }

    @Test fun showsStatsAndLimitationsHonestly() {
        show(PrivacyUiState(engine = EngineState.Ready(DomainFilterEngine.EMPTY, 12, listOf("builtin")), blocked = 3, observed = 40))
        rule.onNodeWithTag("privacy-screen").assertIsDisplayed()
        rule.onNodeWithTag("stat-blocked").assertTextContains("3", substring = true)
        rule.onNodeWithTag("stat-observed").assertTextContains("40", substring = true)
        rule.onNodeWithTag("privacy-limits").assertIsDisplayed()
    }

    @Test fun listRowShowsVisibleErrorAndToggleDispatches() {
        val toggles = ArrayList<Pair<String, Boolean>>()
        val entry = ListEntry(sub, ListStatus(lastAttemptMs = 5, error = "List contains no supported rules"), cached = false)
        show(PrivacyUiState(entries = listOf(entry)), PrivacyActions(onSetListEnabled = { id, on -> toggles += id to on }))
        rule.onNodeWithTag("list-error-easyprivacy", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("list-switch-easyprivacy").performClick()
        assertEquals(listOf("easyprivacy" to false), toggles)
    }

    @Test fun updateNowAndClearStatsAreWired() {
        var update = 0
        var clear = 0
        show(PrivacyUiState(entries = listOf(ListEntry(sub, ListStatus(), false))), PrivacyActions(onUpdateNow = { update++ }, onClearStats = { clear++ }))
        rule.onNodeWithTag("privacy-update-now").performClick()
        rule.onNodeWithTag("privacy-clear-stats").performClick()
        assertEquals(1, update)
        assertEquals(1, clear)
    }

    @Test fun resetNeedsConfirmation() {
        var reset = 0
        show(PrivacyUiState(), PrivacyActions(onResetDefaults = { reset++ }))
        rule.onNodeWithTag("privacy-reset").performClick()
        assertEquals(0, reset)
        rule.onNodeWithTag("reset-confirm").performClick()
        assertEquals(1, reset)
    }

    @Test fun untrustedHostRequiresExplicitDecision() {
        val added = ArrayList<Boolean>()
        show(
            PrivacyUiState(pendingTrust = com.streamshell.feature.privacy.PendingList("Mine", "https://lists.example.org/a.txt", "lists.example.org"), settings = AppSettings()),
            PrivacyActions(onAddList = { _, _, trust -> added += trust })
        )
        rule.onNodeWithTag("trust-confirm").assertIsDisplayed()
        rule.onNodeWithTag("trust-confirm").performClick()
        assertEquals(listOf(true), added)
    }
}
