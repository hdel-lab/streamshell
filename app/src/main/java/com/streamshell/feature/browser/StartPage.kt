@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.streamshell.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streamshell.core.model.Overlay
import com.streamshell.core.navigation.NavAction
import com.streamshell.designsystem.GlassButton
import com.streamshell.designsystem.GlassDivider
import com.streamshell.designsystem.GlassPanel
import com.streamshell.designsystem.QuietOutlinedButton
import com.streamshell.designsystem.QuietTextButton
import com.streamshell.designsystem.Radius
import com.streamshell.designsystem.SectionLabel
import com.streamshell.designsystem.Spacing
import com.streamshell.designsystem.StreamIcons
import com.streamshell.designsystem.StreamTheme
import com.streamshell.designsystem.ambientBackground
import com.streamshell.designsystem.glass
import com.streamshell.designsystem.pressScale

/** Video sites on the start page. Tiles are drawn locally (no favicon requests, so nothing is fetched just to show them). */
data class QuickSite(val name: String, val url: String, val tint: Long)

object QuickSites {
    val all = listOf(
        QuickSite("YouTube", "https://m.youtube.com/", 0xFFE5484D),
        QuickSite("YouTube Music", "https://music.youtube.com/", 0xFFD9363E),
        QuickSite("Vimeo", "https://vimeo.com/", 0xFF1AB7EA),
        QuickSite("Dailymotion", "https://www.dailymotion.com/", 0xFF3B6CF6),
        QuickSite("Twitch", "https://m.twitch.tv/", 0xFF9146FF),
        QuickSite("PeerTube", "https://joinpeertube.org/", 0xFFF1680D),
        QuickSite("Internet Archive", "https://archive.org/details/movies", 0xFF5A5A5A),
        QuickSite("ARTE", "https://www.arte.tv/", 0xFFFA481C)
    )

    const val GOOGLE_SIGN_IN = "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F"
}

@Composable
fun StartPage(state: BrowserUiState, actions: BrowserActions, modifier: Modifier = Modifier) {
    val c = StreamTheme.colors
    // The ambient light is drawn by the browser screen behind the bars and this page together.
    Box(modifier.testTag("start-page"), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 92.dp),
            modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("start-grid"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.lg, bottom = Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { Hero(actions, state.focusSearch) }
            item(span = { GridItemSpan(maxLineSpan) }) { LibraryShortcuts(state, actions) }
            item(span = { GridItemSpan(maxLineSpan) }) { ContinueWatchingRow(state, actions) }

            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Video sites", Modifier.padding(top = Spacing.lg, start = Spacing.xs, bottom = Spacing.xs)) }
            items(QuickSites.all, key = { it.url }) { site -> SiteTile(site) { actions.onOpenSite(site.url) } }

            if (state.bookmarks.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.lg)) {
                        SectionLabel("Bookmarks", Modifier.weight(1f).padding(start = Spacing.xs))
                        QuietTextButton(onClick = { actions.onNav(NavAction.Open(Overlay.Bookmarks)) }, modifier = Modifier.testTag("start-all-bookmarks")) { Text("All") }
                    }
                }
                items(state.bookmarks.take(8), key = { "b-" + it.url }) { b ->
                    SiteTile(QuickSite(b.title.ifBlank { hostLabel(b.url) }, b.url, 0xFF6C8CFF)) { actions.onOpen(b.url) }
                }
            }

            if (state.settings.saveHistory && state.history.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    GlassPanel(
                        Modifier.padding(top = Spacing.lg),
                        title = "Recent",
                        trailing = { QuietTextButton(onClick = { actions.onNav(NavAction.Open(Overlay.History)) }, modifier = Modifier.testTag("start-all-history")) { Text("History") } }
                    ) {
                        state.history.take(5).forEachIndexed { i, h ->
                            if (i > 0) GlassDivider()
                            Row(
                                Modifier.fillMaxWidth().clickable { actions.onOpen(h.url) }.padding(horizontal = Spacing.md, vertical = 12.dp).testTag("start-recent"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                            ) {
                                Icon(StreamIcons.History, null, Modifier.size(20.dp), tint = c.textSecondary)
                                Column(Modifier.weight(1f)) {
                                    Text(h.title.ifBlank { hostLabel(h.url) }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(hostLabel(h.url), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                GlassPanel(Modifier.padding(top = Spacing.lg).testTag("start-sign-in"), title = "Google account") {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            "Sign in once on Google's own page. StreamShell never sees your password; the session stays in this app on this device until you clear it in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary
                        )
                        GlassButton("Sign in to Google", onClick = { actions.onOpen(QuickSites.GOOGLE_SIGN_IN) }, icon = StreamIcons.SignIn, modifier = Modifier.testTag("start-sign-in-button"))
                    }
                }
            }
        }
    }
}

/** Wordmark, logo and the prominent search field. */
@Composable
private fun Hero(actions: BrowserActions, focusSearch: Int = 0) {
    val c = StreamTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = Spacing.md), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LogoMark(44.dp)
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    append("Stream")
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = if (c.isDark) Color(0xFFFF4D4D) else Color(0xFFD90A24)))
                    append("Shell")
                    pop()
                },
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = c.textPrimary,
                modifier = Modifier.semantics { heading() }
            )
        }
        Text("Your video browser", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        StartSearch(actions, Modifier.padding(top = Spacing.xs), focusSearch)
    }
}

/** Watch later · Subscriptions · Watch time, as small glass buttons under the search. */
@Composable
private fun LibraryShortcuts(state: BrowserUiState, actions: BrowserActions) {
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(top = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        GlassButton("Watch later" + if (state.watch.later.isNotEmpty()) " (${state.watch.later.size})" else "", { actions.onNav(NavAction.Open(Overlay.WatchLater)) }, icon = StreamIcons.WatchLater, modifier = Modifier.testTag("start-watch-later"))
        GlassButton("Subscriptions", { actions.onNav(NavAction.Open(Overlay.Subscriptions)) }, icon = StreamIcons.YtSubs, modifier = Modifier.testTag("start-subscriptions"))
        GlassButton("Watch time", { actions.onNav(NavAction.Open(Overlay.WatchTime)) }, icon = StreamIcons.WatchTime, modifier = Modifier.testTag("start-watch-time"))
        GlassButton("Library", { actions.onNav(NavAction.Open(Overlay.Library)) }, icon = StreamIcons.Bookmarks, modifier = Modifier.testTag("start-library"))
        GlassButton("Private tab", actions.onNewPrivateTab, icon = StreamIcons.Secure, modifier = Modifier.testTag("start-private-tab"))
    }
}

/**
 * StreamShell logo — the same mark as the app icon: a red rounded square (bright red to deep crimson) with a
 * white play triangle inside an open "shell" arc. Fixed brand colours (not themed), no blue.
 */
@Composable
fun LogoMark(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.linearGradient(listOf(Color(0xFFFF3D3D), Color(0xFFE8112D), Color(0xFF8E0019))))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(size * 0.3f))
            .drawBehind {
                // Icon geometry is drawn in the adaptive icon's 108-unit space; the visible square is 18..90.
                val k = this.size.width / 72f
                fun p(v: Float) = (v - 18f) * k
                val r = 22f * k
                drawArc(
                    Color.White,
                    startAngle = -20f,
                    sweepAngle = 280f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(p(54f) - r, p(54f) - r),
                    size = androidx.compose.ui.geometry.Size(2 * r, 2 * r),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.5f * k, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
                drawCircle(Color.White, radius = 3.4f * k, center = androidx.compose.ui.geometry.Offset(p(65f), p(35f)))
                val tri = androidx.compose.ui.graphics.Path().apply {
                    moveTo(p(48.5f), p(47.2f))
                    cubicTo(p(48.5f), p(45.4f), p(50.4f), p(44.3f), p(52f), p(45.2f))
                    lineTo(p(63.6f), p(52.1f))
                    cubicTo(p(65.2f), p(53f), p(65.2f), p(55.2f), p(63.6f), p(56.1f))
                    lineTo(p(52f), p(63f))
                    cubicTo(p(50.4f), p(63.9f), p(48.5f), p(62.8f), p(48.5f), p(61f))
                    close()
                }
                drawPath(tri, Color.White)
            }
    )
}

@Composable
private fun StartSearch(actions: BrowserActions, modifier: Modifier, focusSearch: Int = 0) {
    val c = StreamTheme.colors
    var text by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val requester = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(focusSearch) { if (focusSearch > 0) runCatching { requester.requestFocus() } }
    OutlinedTextField(
        value = text, onValueChange = { text = it }, singleLine = true,
        placeholder = { Text("Search videos or enter an address", maxLines = 1) },
        leadingIcon = { Icon(StreamIcons.Search, null, tint = c.accent) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = {
            if (text.isNotBlank()) {
                focus.clearFocus()
                actions.onSubmitAddress(text)
            }
        }),
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(Radius.field),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = c.surfaceRaised, unfocusedContainerColor = c.glass,
            focusedBorderColor = c.accent, unfocusedBorderColor = c.glassEdge, cursorColor = c.accent,
            focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
            focusedPlaceholderColor = c.textSecondary, unfocusedPlaceholderColor = c.textSecondary
        ),
        modifier = modifier.widthIn(max = Spacing.contentMaxWidth).fillMaxWidth().height(56.dp).focusRequester(requester).testTag("start-search")
    )
}

/** Quick access tile: glass tile with the site's monogram in its colour. Drawn locally, nothing is fetched. */
@Composable
private fun SiteTile(site: QuickSite, onClick: () -> Unit) {
    val c = StreamTheme.colors
    val source = remember { MutableInteractionSource() }
    val tint = Color(site.tint)
    Column(
        Modifier.pressScale(source, 0.95f).glass(c, RoundedCornerShape(Radius.panel))
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), role = Role.Button, onClickLabel = "Open ${site.name}", onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 6.dp).testTag("site-${site.name}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(Radius.field))
                .background(Brush.linearGradient(listOf(tint, androidx.compose.ui.graphics.lerp(tint, Color.Black, 0.25f))))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(Radius.field)),
            contentAlignment = Alignment.Center
        ) {
            Text(site.name.take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        }
        Text(site.name, style = MaterialTheme.typography.labelMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
