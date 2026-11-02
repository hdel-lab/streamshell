package com.streamshell.feature.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streamshell.core.model.Overlay
import com.streamshell.core.navigation.NavAction
import com.streamshell.core.watch.Playlists
import com.streamshell.core.watch.WatchRules
import com.streamshell.designsystem.GlassChip
import com.streamshell.designsystem.GlassHeader
import com.streamshell.designsystem.GlassIconButton
import com.streamshell.designsystem.GlassPanel
import com.streamshell.designsystem.Radius
import com.streamshell.designsystem.SectionLabel
import com.streamshell.designsystem.Spacing
import com.streamshell.designsystem.StreamIcons
import com.streamshell.designsystem.StreamTheme

/*
 * 0.5.0: the StreamShell Library (bottom bar item), local playlists, the player action row on video pages and the
 * playlist picker. Everything here is local to the device.
 */

/** Add the current video to a playlist (an existing one, or a new one by name). */
@Composable
fun PlaylistPickerDialog(state: BrowserUiState, actions: BrowserActions, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("playlist-picker"),
        title = { Text("Add to playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val lists = state.watch.playlists
                if (lists.isNotEmpty()) {
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        lists.forEach { p ->
                            val inIt = Playlists.contains(lists, p.name, state.currentUrl)
                            GlassChip(p.name + if (inIt) " ✓" else "", inIt, {
                                actions.onAddToPlaylist(p.name)
                                onDismiss()
                            }, Modifier.testTag("playlist-pick-${p.name}"))
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(Playlists.MAX_NAME) },
                    singleLine = true,
                    placeholder = { Text("New playlist") },
                    modifier = Modifier.fillMaxWidth().testTag("playlist-new-name")
                )
            }
        },
        confirmButton = {
            com.streamshell.designsystem.QuietTextButton(onClick = {
                if (Playlists.clean(name) != null) actions.onAddToPlaylist(name)
                onDismiss()
            }, modifier = Modifier.testTag("playlist-create-add")) { Text(if (Playlists.clean(name) != null) "Create and add" else "Close") }
        }
    )
}

@Composable
private fun LibraryEntry(icon: ImageVector, title: String, subtitle: String?, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.field)).clickable(onClick = onClick).padding(horizontal = Spacing.md, vertical = 12.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = StreamTheme.colors.textPrimary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = StreamTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The StreamShell Library: Continue watching, Watch later and the queue, playlists, imported subscriptions, watch time,
 * history and a private tab — one place for what StreamShell keeps on this device.
 */
@Composable
fun LibraryPanel(state: BrowserUiState, actions: BrowserActions) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val list = open?.let { n -> state.watch.playlists.firstOrNull { it.name == n } }
    if (open != null && list == null) open = null
    OverlaySurface("library-panel") {
        if (list != null) {
            PlaylistDetail(list, actions) { open = null }
            return@OverlaySurface
        }
        Column(Modifier.fillMaxSize()) {
            GlassHeader("Library", { actions.onNav(NavAction.CloseOverlay) }, backDescription = "Back to the page")
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(Modifier.widthIn(max = Spacing.contentMaxWidth).fillMaxSize(), contentPadding = PaddingValues(Spacing.md)) {
                    item { ContinueWatchingRow(state, actions) }
                    item {
                        GlassPanel(Modifier.padding(top = Spacing.md)) {
                            LibraryEntry(StreamIcons.WatchLater, "Watch later", "${state.watch.later.size} videos" + if (state.queue.items.isNotEmpty()) " · queue ${state.queue.items.size}" else "", "library-watch-later") {
                                actions.onNav(NavAction.Open(Overlay.WatchLater))
                            }
                            LibraryEntry(StreamIcons.YtSubs, "Subscriptions", "${state.watch.channels.size} channels (imported)", "library-subscriptions") { actions.onNav(NavAction.Open(Overlay.Subscriptions)) }
                            LibraryEntry(StreamIcons.WatchTime, "Watch time", "Today ${WatchRules.formatDuration(state.watch.stats[java.time.LocalDate.now().toString()] ?: 0L)}", "library-watch-time") {
                                actions.onNav(NavAction.Open(Overlay.WatchTime))
                            }
                            LibraryEntry(StreamIcons.History, "History", null, "library-history") { actions.onNav(NavAction.Open(Overlay.History)) }
                            LibraryEntry(StreamIcons.Secure, "New private tab", "Nothing is remembered", "library-private-tab") {
                                actions.onNav(NavAction.CloseOverlay)
                                actions.onNewPrivateTab()
                            }
                        }
                    }
                    item { PlaylistsSection(state, actions) { open = it } }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsSection(state: BrowserUiState, actions: BrowserActions, onOpen: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(top = Spacing.lg).testTag("library-playlists")) {
        SectionLabel("Playlists", Modifier.padding(start = Spacing.xs, bottom = Spacing.sm))
        GlassPanel {
            state.watch.playlists.forEach { p ->
                LibraryEntry(StreamIcons.Queue, p.name, "${p.items.size} videos", "playlist-${p.name}") { onOpen(p.name) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(Playlists.MAX_NAME) },
                    singleLine = true,
                    placeholder = { Text("New playlist") },
                    modifier = Modifier.weight(1f).testTag("library-new-playlist")
                )
                GlassIconButton(StreamIcons.NewTab, "Create playlist", {
                    actions.onCreatePlaylist(name)
                    name = ""
                }, Modifier.testTag("library-create-playlist"), enabled = Playlists.clean(name) != null)
            }
            if (state.watch.playlists.isEmpty()) {
                Text(
                    "On a video, tap “Playlist” in the row above the bottom bar to add it to a list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StreamTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
                )
            }
        }
    }
}

@Composable
private fun PlaylistDetail(p: com.streamshell.core.watch.Playlist, actions: BrowserActions, onBack: () -> Unit) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().testTag("playlist-detail")) {
        GlassHeader(p.name, onBack, backDescription = "Back to the library") {
            if (p.items.isNotEmpty()) GlassIconButton(StreamIcons.PlayArrow, "Play all", { actions.onPlayPlaylist(p.name, 0) }, Modifier.testTag("playlist-play-all"), glass = false)
            GlassIconButton(StreamIcons.Settings, "Rename", { renaming = true }, Modifier.testTag("playlist-rename"), glass = false)
            GlassIconButton(StreamIcons.ClearData, "Delete playlist", { confirmDelete = true }, Modifier.testTag("playlist-delete"), glass = false)
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.xl)) {
                itemsIndexed(p.items, key = { i, entry -> "$i|${entry.url}" }) { i, item ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = 6.dp).clip(RoundedCornerShape(Radius.field))
                            .clickable { actions.onPlayPlaylist(p.name, i) }.testTag("playlist-item"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        VideoThumb(item.url, Modifier.width(132.dp))
                        Text(item.title.ifBlank { hostLabel(item.url) }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Column {
                            GlassIconButton(StreamIcons.ExpandUp, "Move up", { actions.onMovePlaylistItem(p.name, i, -1) }, Modifier.testTag("playlist-up"), size = 32.dp, iconSize = 16.dp, glass = false, enabled = i > 0)
                            GlassIconButton(StreamIcons.ExpandDown, "Move down", { actions.onMovePlaylistItem(p.name, i, 1) }, Modifier.testTag("playlist-down"), size = 32.dp, iconSize = 16.dp, glass = false, enabled = i < p.items.lastIndex)
                        }
                        GlassIconButton(StreamIcons.Close, "Remove", { actions.onRemoveFromPlaylist(p.name, i) }, Modifier.testTag("playlist-remove"), size = 32.dp, iconSize = 16.dp, glass = false)
                    }
                }
                if (p.items.isEmpty()) {
                    item {
                        Text(
                            "Empty. On a video, tap “Playlist” to add it here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StreamTheme.colors.textSecondary,
                            modifier = Modifier.padding(Spacing.lg)
                        )
                    }
                }
            }
        }
    }
    if (renaming) {
        var to by rememberSaveable { mutableStateOf(p.name) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename playlist") },
            text = { OutlinedTextField(value = to, onValueChange = { to = it.take(Playlists.MAX_NAME) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("playlist-rename-field")) },
            confirmButton = {
                com.streamshell.designsystem.QuietTextButton(onClick = {
                    actions.onRenamePlaylist(p.name, to)
                    renaming = false
                    onBack()
                }) { Text("Rename") }
            }
        )
    }
    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${p.name}”?") },
            text = { Text("The list and its ${p.items.size} entries are removed (the videos themselves stay on YouTube).") },
            confirmButton = {
                com.streamshell.designsystem.QuietTextButton(onClick = {
                    actions.onDeletePlaylist(p.name)
                    confirmDelete = false
                    onBack()
                }, modifier = Modifier.testTag("playlist-delete-confirm")) { Text("Delete") }
            },
            dismissButton = { com.streamshell.designsystem.QuietTextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
