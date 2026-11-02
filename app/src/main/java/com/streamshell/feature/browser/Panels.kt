package com.streamshell.feature.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streamshell.core.model.Overlay
import com.streamshell.core.navigation.NavAction
import com.streamshell.core.privacy.FilterSources
import com.streamshell.designsystem.Depth
import com.streamshell.designsystem.GlassHeader
import com.streamshell.designsystem.GlassIconButton
import com.streamshell.designsystem.QuietOutlinedButton
import com.streamshell.designsystem.QuietTextButton
import com.streamshell.designsystem.Radius
import com.streamshell.designsystem.Spacing
import com.streamshell.designsystem.StreamIcons
import com.streamshell.designsystem.StreamTheme
import com.streamshell.designsystem.glass
import java.text.DateFormat
import java.util.Date

@Composable
private fun PanelHeader(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    GlassHeader(title, onBack, backDescription = "Back to the page") { actions() }
}

@Composable
private fun EmptyState(tag: String, title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(Spacing.xl).testTag(tag), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        LogoMark(48.dp)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.sm))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = StreamTheme.colors.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** List row as a slim glass card (bookmarks, history). */
private fun Modifier.glassRow(c: com.streamshell.designsystem.StreamColors) = this.padding(horizontal = Spacing.md, vertical = 4.dp).glass(c, RoundedCornerShape(Radius.field))

@Composable
fun TabsPanel(state: BrowserUiState, actions: BrowserActions) {
    var confirmClose by rememberSaveable { mutableStateOf(false) }
    OverlaySurface("tabs-panel") {
        Column(Modifier.fillMaxSize()) {
            PanelHeader("Tabs (${com.streamshell.core.browser.TabsReducer.visibleCount(state.tabs)})", { actions.onNav(NavAction.CloseOverlay) }) {
                GlassIconButton(StreamIcons.NewTab, "New tab", actions.onNewTab, Modifier.testTag("tabs-new"), selected = true)
                QuietTextButton(onClick = { confirmClose = true }, modifier = Modifier.testTag("tabs-close-all")) { Text("Close all") }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.tabs.tabs, key = { it.id }) { t ->
                    val active = t.id == state.tabs.activeId
                    val start = t.showingStart || t.url == null
                    val c = StreamTheme.colors
                    Surface(
                        color = if (active) c.accentFill.copy(alpha = if (c.isDark) 0.16f else 0.08f) else Color.Transparent,
                        shape = RoundedCornerShape(Radius.panel),
                        border = BorderStroke(if (active) 1.5.dp else 1.dp, if (active) c.accent else c.glassEdge),
                        modifier = Modifier.heightIn(min = 104.dp).glass(c, RoundedCornerShape(Radius.panel), elevation = Depth.panel)
                            .clickable(role = Role.Tab, onClickLabel = "Show tab") { actions.onSelectTab(t.id) }.testTag("tab-${t.id}")
                    ) {
                        Row(Modifier.padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f).padding(top = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text(if (start) "Start page" else t.title.ifBlank { hostLabel(t.url) }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    when {
                                        t.instance != null -> (com.streamshell.core.browser.SiteApp.entries.firstOrNull { it.name == t.instance }?.title ?: "") + " · instance"
                                        t.playerFor != null -> "Video player"
                                        start -> "StreamShell"
                                        else -> hostLabel(t.url)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StreamTheme.colors.textSecondary,
                                    maxLines = 1
                                )
                            }
                            GlassIconButton(StreamIcons.Close, "Close tab", { actions.onCloseTab(t.id) }, Modifier.padding(end = Spacing.xs).testTag("tab-close-${t.id}"), size = 36.dp, iconSize = 16.dp, glass = false)
                        }
                    }
                }
            }
        }
    }
    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Close all tabs?") },
            text = { Text("All open tabs are closed. Bookmarks, history and sign-ins are kept.") },
            confirmButton = {
                Button(onClick = {
                    confirmClose = false
                    actions.onCloseAllTabs()
                }, modifier = Modifier.testTag("tabs-close-all-confirm")) { Text("Close all") }
            },
            dismissButton = { QuietTextButton(onClick = { confirmClose = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun BookmarksPanel(state: BrowserUiState, actions: BrowserActions) {
    OverlaySurface("bookmarks-panel") {
        Column(Modifier.fillMaxSize()) {
            PanelHeader("Bookmarks", { actions.onNav(NavAction.CloseOverlay) })
            if (state.bookmarks.isEmpty()) {
                EmptyState("bookmarks-empty", "No bookmarks yet", "Open a page and choose “Add bookmark” in the menu.")
                return@Column
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(Modifier.widthIn(max = Spacing.contentMaxWidth).fillMaxSize()) {
                    items(state.bookmarks, key = { it.url }) { b ->
                        ListItem(
                            headlineContent = { Text(b.title.ifBlank { hostLabel(b.url) }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(hostLabel(b.url), maxLines = 1) },
                            leadingContent = { Icon(StreamIcons.BookmarkFilled, null, tint = StreamTheme.colors.accent) },
                            trailingContent = { IconButton(onClick = { actions.onRemoveBookmark(b.url) }, modifier = Modifier.testTag("bookmark-remove")) { Icon(StreamIcons.ClearData, "Remove bookmark") } },
                            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.glassRow(StreamTheme.colors).clickable { actions.onOpen(b.url) }.testTag("bookmark-row")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryPanel(state: BrowserUiState, actions: BrowserActions) {
    var query by rememberSaveable { mutableStateOf("") }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val list = com.streamshell.core.library.HistoryRules.search(state.history, query)
    OverlaySurface("history-panel") {
        Column(Modifier.fillMaxSize()) {
            PanelHeader("History", { actions.onNav(NavAction.CloseOverlay) }) {
                QuietTextButton(onClick = { confirmClear = true }, enabled = state.history.isNotEmpty(), modifier = Modifier.testTag("history-clear")) { Text("Clear") }
            }
            if (!state.settings.saveHistory) {
                Text(
                    "History is off: new visits are not saved. Turn it on in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StreamTheme.colors.textSecondary,
                    modifier = Modifier.padding(Spacing.md).testTag("history-off")
                )
            }
            OutlinedTextField(
                query,
                { query = it },
                singleLine = true,
                placeholder = { Text("Search history") },
                leadingIcon = { Icon(StreamIcons.Search, null, tint = StreamTheme.colors.accent) },
                shape = RoundedCornerShape(Radius.field),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = StreamTheme.colors.surfaceRaised,
                    unfocusedContainerColor = StreamTheme.colors.glass,
                    focusedBorderColor = StreamTheme.colors.accent,
                    unfocusedBorderColor = StreamTheme.colors.glassEdge,
                    cursorColor = StreamTheme.colors.accent
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm).testTag("history-search")
            )
            if (list.isEmpty()) {
                EmptyState("history-empty", if (query.isBlank()) "No history" else "Nothing found", if (query.isBlank()) "Pages you visit appear here. They stay on this device." else "No visited page matches “$query”.")
                return@Column
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(Modifier.widthIn(max = Spacing.contentMaxWidth).fillMaxSize()) {
                    items(list, key = { it.url + it.visitedAt }) { h ->
                        ListItem(
                            headlineContent = { Text(h.title.ifBlank { hostLabel(h.url) }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text("${hostLabel(h.url)} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(h.visitedAt))}", maxLines = 1) },
                            trailingContent = { IconButton(onClick = { actions.onRemoveHistory(h.url) }, modifier = Modifier.testTag("history-remove")) { Icon(StreamIcons.Close, "Remove from history") } },
                            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.glassRow(StreamTheme.colors).clickable { actions.onOpen(h.url) }.testTag("history-row")
                        )
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("All visited pages are removed from StreamShell. Bookmarks and sign-ins are kept.") },
            confirmButton = {
                Button(onClick = {
                    confirmClear = false
                    actions.onClearHistory()
                }, modifier = Modifier.testTag("history-clear-confirm")) { Text("Clear") }
            },
            dismissButton = { QuietTextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun ShieldDialog(state: BrowserUiState, actions: BrowserActions, onDismiss: () -> Unit) {
    val host = state.currentHost
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("shield-dialog"),
        icon = { Icon(StreamIcons.PrivacyOn, null, tint = StreamTheme.colors.accent) },
        title = { Text("Ads & trackers") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    when {
                        !state.settings.filteringEnabled -> "Protection is switched off in AdBlock & Privacy."
                        state.showingStart || host == null -> "Open a page to see what is blocked on it."
                        state.siteExempt -> "Nothing is blocked on $host."
                        else -> "${state.page.blocked} request(s) blocked on this page."
                    },
                    modifier = Modifier.testTag("shield-summary")
                )
                if (state.settings.filteringEnabled && host != null && !state.showingStart) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable(role = Role.Switch) { actions.onToggleSiteExempt() }.testTag("shield-site-toggle")
                    ) {
                        Text("Block on ${host.removePrefix("www.")}", modifier = Modifier.weight(1f))
                        Switch(checked = !state.siteExempt, onCheckedChange = null)
                    }
                }
                Text(
                    "Counts only requests StreamShell itself refused. Video ads served from the same servers as the video cannot be separated by domain and are not blocked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StreamTheme.colors.textSecondary
                )
            }
        },
        confirmButton = { QuietTextButton(onClick = onDismiss, modifier = Modifier.testTag("shield-close")) { Text("Close") } },
        dismissButton = {
            QuietOutlinedButton(onClick = {
                onDismiss()
                actions.onNav(NavAction.Open(Overlay.Privacy))
            }, modifier = Modifier.testTag("shield-settings")) { Text("Filter settings") }
        }
    )
}

@Composable
fun BlockedNavigationDialog(b: BlockedNavigation, onResolve: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onResolve(false) },
        modifier = Modifier.testTag("blocked-dialog"),
        icon = { Icon(StreamIcons.Warning, null) },
        title = { Text("Page blocked") },
        text = {
            Text(
                "StreamShell did not open ${b.host}: it matches ${b.rule} from " +
                    when (b.source) {
                        null -> "a filter list"
                        "custom" -> "your own block rules"
                        FilterSources.BUILTIN_ID -> "the built-in list"
                        else -> FilterSources.recommended.firstOrNull { it.id == b.source }?.name ?: "a filter list you added"
                    } + "."
            )
        },
        confirmButton = { Button(onClick = { onResolve(false) }, modifier = Modifier.testTag("blocked-keep")) { Text("Keep blocked") } },
        dismissButton = { QuietTextButton(onClick = { onResolve(true) }, modifier = Modifier.testTag("blocked-open")) { Text("Open anyway") } }
    )
}
