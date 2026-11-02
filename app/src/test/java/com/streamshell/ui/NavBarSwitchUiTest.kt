package com.streamshell.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.streamshell.core.model.ThemeMode
import com.streamshell.designsystem.GlassNavBar
import com.streamshell.designsystem.GlassNavItem
import com.streamshell.designsystem.StreamIcons
import com.streamshell.designsystem.StreamShellTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 0.6.3 regression: the bottom bar's items change between YouTube and YouTube Music (other count, search capsule in
 * another place). The crash report of 2026-11-01 23:41 showed state of one item read by another
 * (ClassCastException Rgb → MutableInteractionSource in GlassNavBar). Each item now has its own keyed group.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp", application = android.app.Application::class)
class NavBarSwitchUiTest {
    @get:Rule val rule = createComposeRule()

    private fun item(k: String) = GlassNavItem(k, k, StreamIcons.Globe, StreamIcons.Globe, "nav-$k")
    private val youtube = listOf("search", "home", "subscriptions", "you", "ss-library", "more").map(::item)
    private val music = listOf("home", "search", "explore", "library", "more").map(::item)
    private val musicNoSearch = listOf("home", "explore", "library", "more").map(::item)

    @Test fun barSurvivesChangingItems() {
        var items by mutableStateOf(youtube)
        var selected by mutableStateOf<String?>("home")
        rule.setContent { StreamShellTheme(ThemeMode.DARK) { GlassNavBar(items, selected, {}, capsuleKey = "search") } }
        listOf(music to "home", musicNoSearch to "explore", youtube to "subscriptions", music to null, youtube to "home", musicNoSearch to "library").forEach { (list, sel) ->
            items = list
            selected = sel
            rule.waitForIdle()
        }
        rule.onNodeWithTag("nav-library").assertIsDisplayed()
    }
}
