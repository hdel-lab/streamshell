@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.streamshell.feature.browser

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streamshell.core.navigation.NavAction
import com.streamshell.core.watch.WatchRules
import com.streamshell.designsystem.GlassButton
import com.streamshell.designsystem.GlassChip
import com.streamshell.designsystem.GlassHeader
import com.streamshell.designsystem.GlassIconButton
import com.streamshell.designsystem.GlassPanel
import com.streamshell.designsystem.Radius
import com.streamshell.designsystem.SectionLabel
import com.streamshell.designsystem.Spacing
import com.streamshell.designsystem.StreamIcons
import com.streamshell.designsystem.StreamTheme
import com.streamshell.designsystem.glass

/** In-memory cache of video pictures (i.ytimg.com only), shared by the lists. */
internal object ThumbCache {
    private val cache = object : android.util.LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    fun get(url: String): Bitmap? = cache.get(url)
    fun put(url: String, b: Bitmap) {
        cache.put(url, b)
    }
}

/** A video picture loaded in the background; a neutral placeholder while loading or for non-YouTube videos. */
@Composable
fun VideoThumb(url: String, modifier: Modifier = Modifier, progress: Float? = null, rounded: Boolean = true, fill: Boolean = false) {
    val thumb = WatchRules.thumbnailOf(url)
    var bmp by androidx.compose.runtime.remember(thumb) { mutableStateOf(thumb?.let { ThumbCache.get(it) }) }
    LaunchedEffect(thumb) {
        if (thumb != null && bmp == null) {
            bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { com.streamshell.core.media.Thumbnails.fetch(thumb) }.getOrNull()?.also { ThumbCache.put(thumb, it) }
            }
        }
    }
    val c = StreamTheme.colors
    Box(modifier.then(if (fill) Modifier else Modifier.aspectRatio(16f / 9f)).then(if (rounded) Modifier.clip(RoundedCornerShape(Radius.control)) else Modifier).background(c.surfaceRaised), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(StreamIcons.Play, null, Modifier.size(28.dp), tint = c.textSecondary)
        }
        if (progress != null && progress > 0f) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.35f))) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Color(0xFFFF0033)))
            }
        }
    }
}

/** Start page row: videos you stopped in the middle of. */
@Composable
fun ContinueWatchingRow(state: BrowserUiState, actions: BrowserActions) {
    val list = state.continueWatching
    if (list.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = Spacing.lg).testTag("continue-watching")) {
        SectionLabel("Continue watching", Modifier.padding(start = Spacing.xs, bottom = Spacing.sm))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), contentPadding = PaddingValues(horizontal = 2.dp)) {
            items(list, key = { it.key }) { p ->
                Column(
                    Modifier.width(220.dp).clickable { actions.onOpenVideo(p.url) }.testTag("continue-${p.key}"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VideoThumb(p.url, Modifier.fillMaxWidth(), progress = p.progress)
                    Text(p.title.ifBlank { hostLabel(p.url) }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${WatchRules.formatClock(p.position)} / ${WatchRules.formatClock(p.duration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = StreamTheme.colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoRow(url: String, title: String, subtitle: String, tag: String, onOpen: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = 6.dp).clip(RoundedCornerShape(Radius.field)).clickable(onClick = onOpen).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        VideoThumb(url, Modifier.width(150.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = StreamTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

@Composable
private fun Empty(tag: String, title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(Spacing.xl).testTag(tag), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        LogoMark(48.dp)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.sm))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = StreamTheme.colors.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        action?.invoke()
    }
}

@Composable
fun WatchLaterPanel(state: BrowserUiState, actions: BrowserActions) {
    OverlaySurface("watch-later-panel") {
        Column(Modifier.fillMaxSize()) {
            val list = state.watch.later
            GlassHeader("Watch later", { actions.onNav(NavAction.CloseOverlay) }, backDescription = "Back to the page") {
                if (list.isNotEmpty()) GlassIconButton(StreamIcons.Queue, "Play all, one after another", actions.onPlayAllLater, Modifier.testTag("watch-later-play-all"), glass = false)
            }
            val q = state.queue
            if (q.items.isNotEmpty()) QueueSection(q, actions)
            if (list.isEmpty()) {
                Empty("watch-later-empty", "Nothing saved yet", "On a video, open the menu and choose “Save to Watch later”. The list stays on this device.")
                return@Column
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.xl)) {
                    items(list, key = { it.url }) { item ->
                        val pos = state.watch.positions.firstOrNull { it.key == WatchRules.keyOf(item.url) }
                        VideoRow(
                            item.url,
                            item.title.ifBlank { hostLabel(item.url) },
                            pos?.let { "Stopped at ${WatchRules.formatClock(it.position)}" } ?: hostLabel(item.url),
                            "watch-later-row",
                            { actions.onOpenVideo(item.url) }
                        ) {
                            GlassIconButton(StreamIcons.Close, "Remove from Watch later", { actions.onRemoveWatchLater(item.url) }, Modifier.testTag("watch-later-remove"), size = 36.dp, iconSize = 16.dp, glass = false)
                        }
                    }
                }
            }
        }
    }
}

/** The queue: what plays one after another (tap to play from there). */
@Composable
private fun QueueSection(q: PlayQueue, actions: BrowserActions) {
    val c = StreamTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs).testTag("queue-section")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (q.active) "Queue · ${q.pos + 1} of ${q.items.size}" else "Queue · ${q.items.size}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            com.streamshell.designsystem.QuietTextButton(onClick = actions.onClearQueue, modifier = Modifier.testTag("queue-clear")) { Text("Clear") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(q.items.size) { i ->
                val it = q.items[i]
                Column(Modifier.width(160.dp).clip(RoundedCornerShape(Radius.field)).clickable { actions.onPlayQueueFrom(i) }.testTag("queue-$i"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box {
                        VideoThumb(it.url, Modifier.fillMaxWidth())
                        if (i == q.pos) {
                            Text(
                                "Playing",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFFF0033)).padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        GlassIconButton(StreamIcons.Close, "Remove from queue", { actions.onRemoveFromQueue(i) }, Modifier.align(Alignment.TopEnd).padding(2.dp).testTag("queue-remove-$i"), size = 28.dp, iconSize = 14.dp, colors = com.streamshell.designsystem.StreamColors.OnMedia)
                    }
                    Text(it.title.ifBlank { hostLabel(it.url) }, style = MaterialTheme.typography.labelMedium, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun SubscriptionsPanel(state: BrowserUiState, actions: BrowserActions) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var groupFor by remember { mutableStateOf<com.streamshell.core.watch.Channel?>(null) }
    val groups = state.watch.groups
    if (group != null && group !in groups) group = null
    val channels = state.watch.channels
    LaunchedEffect(channels.size) { if (channels.isNotEmpty() && state.feed.videos.isEmpty() && !state.feed.loading) actions.onRefreshFeed() }
    OverlaySurface("subscriptions-panel") {
        Column(Modifier.fillMaxSize()) {
            GlassHeader("Subscriptions", { actions.onNav(NavAction.CloseOverlay) }, backDescription = "Back to the page") {
                if (channels.isNotEmpty()) GlassIconButton(StreamIcons.Reload, "Refresh", actions.onRefreshFeed, Modifier.testTag("feed-refresh"), glass = false)
            }
            if (channels.isEmpty()) {
                Empty(
                    "subscriptions-empty",
                    "Bring your subscriptions",
                    "Export them with Google Takeout (takeout.google.com › YouTube and YouTube Music › subscriptions) and choose the file subscriptions.csv here — or the subscriptions.json exported by NewPipe. " +
                        "StreamShell then shows your channels and their newest videos from YouTube's public feeds — without signing in. Everything stays on this device."
                ) { GlassButton("Choose subscriptions file", actions.onImportSubscriptions, icon = StreamIcons.NewTab, modifier = Modifier.padding(top = Spacing.sm).testTag("subscriptions-import")) }
                return@Column
            }
            Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GlassChip("Latest", tab == 0, { tab = 0 }, Modifier.testTag("subs-latest"))
                GlassChip("Channels (${channels.size})", tab == 1, { tab = 1 }, Modifier.testTag("subs-channels"))
            }
            // Groups: "All" and the user's groups (made by putting a channel into one under Channels).
            if (groups.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), contentPadding = PaddingValues(horizontal = Spacing.md, vertical = 2.dp), modifier = Modifier.testTag("group-chips")) {
                    item { GlassChip("All", group == null, { group = null }, Modifier.testTag("group-all")) }
                    items(groups.keys.sorted()) { g -> GlassChip("$g (${groups[g]?.size ?: 0})", group == g, { group = g }, Modifier.testTag("group-$g"), icon = StreamIcons.Group) }
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                if (tab == 0) {
                    val f = state.feed
                    when {
                        f.loading && f.videos.isEmpty() -> Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            repeat(5) {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                    com.streamshell.designsystem.Skeleton(Modifier.width(150.dp).aspectRatio(16f / 9f))
                                    com.streamshell.designsystem.Skeleton(Modifier.weight(1f).height(18.dp))
                                }
                            }
                        }

                        f.videos.isEmpty() -> Empty("feed-empty", "No videos", f.error ?: "Tap Refresh to load the newest videos.")

                        else -> LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("feed-list"), contentPadding = PaddingValues(bottom = Spacing.xl)) {
                            items(com.streamshell.core.watch.ChannelGroups.filter(f.videos, groups, group), key = { it.videoId }) { v ->
                                VideoRow(v.url, v.title, "${v.channel} · ${ago(v.published)}", "feed-row", { actions.onOpenVideo(v.url) })
                            }
                        }
                    }
                } else {
                    LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.xl)) {
                        item {
                            GlassButton("Import again", actions.onImportSubscriptions, icon = StreamIcons.NewTab, modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm).testTag("subscriptions-reimport"))
                        }
                        items(channels.filter { ch -> group == null || ch.id in groups[group].orEmpty() }, key = { it.id }) { ch ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = 4.dp).glass(StreamTheme.colors, RoundedCornerShape(Radius.field))
                                    .clickable { actions.onOpenChannel(ch) }.padding(horizontal = Spacing.md, vertical = 12.dp).testTag("channel-row"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                            ) {
                                Box(Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).background(StreamTheme.colors.surfaceRaised), contentAlignment = Alignment.Center) {
                                    Text(ch.title.take(1).uppercase(), style = MaterialTheme.typography.titleSmall)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(ch.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    com.streamshell.core.watch.ChannelGroups.groupOf(groups, ch.id)?.let { g -> Text(g, style = MaterialTheme.typography.bodySmall, color = StreamTheme.colors.textSecondary, maxLines = 1) }
                                }
                                GlassIconButton(StreamIcons.Group, "Group for ${ch.title}", { groupFor = ch }, Modifier.testTag("channel-group"), size = 32.dp, iconSize = 16.dp, glass = false)
                                GlassIconButton(StreamIcons.Close, "Remove channel", { actions.onRemoveChannel(ch.id) }, size = 32.dp, iconSize = 16.dp, glass = false)
                            }
                        }
                    }
                }
            }
        }
    }
    groupFor?.let { ch ->
        GroupDialog(ch, groups, onPick = { g ->
            actions.onSetChannelGroup(ch.id, g)
            groupFor = null
        }, onDismiss = { groupFor = null })
    }
}

/** Put a channel into a group: one of the existing groups, a new one, or none. */
@Composable
private fun GroupDialog(channel: com.streamshell.core.watch.Channel, groups: Map<String, List<String>>, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val current = com.streamshell.core.watch.ChannelGroups.groupOf(groups, channel.id)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("group-dialog"),
        title = { Text("Group for ${channel.title}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassChip("No group", current == null, { onPick(null) }, Modifier.testTag("group-none"))
                    groups.keys.sorted().forEach { g -> GlassChip(g, current == g, { onPick(g) }, Modifier.testTag("group-pick-$g")) }
                }
                androidx.compose.material3.OutlinedTextField(
                    name,
                    { name = it.take(com.streamshell.core.watch.ChannelGroups.MAX_NAME) },
                    singleLine = true,
                    placeholder = { Text("New group, e.g. Music") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (name.isNotBlank()) onPick(name) }),
                    modifier = Modifier.fillMaxWidth().testTag("group-new")
                )
            }
        },
        confirmButton = { com.streamshell.designsystem.QuietTextButton(onClick = { if (name.isNotBlank()) onPick(name) else onDismiss() }, modifier = Modifier.testTag("group-done")) { Text(if (name.isNotBlank()) "Create" else "Close") } }
    )
}

private fun ago(t: Long): String {
    if (t <= 0) return ""
    val m = (System.currentTimeMillis() - t) / 60_000
    return when {
        m < 60 -> "${m.coerceAtLeast(1)} min ago"
        m < 60 * 24 -> "${m / 60} h ago"
        m < 60 * 24 * 30 -> "${m / (60 * 24)} days ago"
        else -> "${m / (60 * 24 * 30)} months ago"
    }
}

@Composable
fun WatchTimePanel(state: BrowserUiState, actions: BrowserActions) {
    val today = java.time.LocalDate.now()
    val days = WatchRules.lastDays(state.watch.stats, today, 7)
    val max = (days.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(60L)
    OverlaySurface("watch-time-panel") {
        Column(Modifier.fillMaxSize()) {
            GlassHeader("Watch time", { actions.onNav(NavAction.CloseOverlay) }, backDescription = "Back to the page")
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = Spacing.contentMaxWidth).fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    GlassPanel(title = "Today") {
                        Text(WatchRules.formatDuration(days.last().second), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(Spacing.md).testTag("watch-today"))
                    }
                    GlassPanel(title = "Last 7 days · ${WatchRules.formatDuration(days.sumOf { it.second })}") {
                        Row(Modifier.fillMaxWidth().height(160.dp).padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Bottom) {
                            days.forEach { (d, sec) ->
                                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                    val frac = (sec.toFloat() / max).coerceIn(0f, 1f)
                                    Box(
                                        Modifier.fillMaxWidth(0.7f).fillMaxHeight(0.82f * frac + 0.01f).clip(RoundedCornerShape(6.dp))
                                            .background(if (d == today) StreamTheme.colors.textPrimary else StreamTheme.colors.textSecondary.copy(alpha = 0.55f))
                                    )
                                    Text(d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, androidx.compose.ui.platform.LocalLocale.current.platformLocale), style = MaterialTheme.typography.labelSmall, color = StreamTheme.colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                    val month = WatchRules.lastDays(state.watch.stats, today, 30).sumOf { it.second }
                    GlassPanel(title = "Last 30 days") {
                        Text(WatchRules.formatDuration(month), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(Spacing.md).testTag("watch-month"))
                    }
                    val top = com.streamshell.core.watch.ChannelRules.top(state.watch.channelStats, 5)
                    if (top.isNotEmpty()) {
                        GlassPanel(title = "Most watched channels") {
                            top.forEach { (name, sec) ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = 8.dp).testTag("watch-channel"), verticalAlignment = Alignment.CenterVertically) {
                                    Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(WatchRules.formatDuration(sec), style = MaterialTheme.typography.bodyMedium, color = StreamTheme.colors.textSecondary)
                                }
                            }
                        }
                    }
                    Text(
                        "Counted while a video plays in StreamShell, kept on this device for 30 days. A break reminder can be set in Settings › Playback.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StreamTheme.colors.textSecondary
                    )
                }
            }
        }
    }
}

/** Speed, ± jump, start over and the sleep timer for the playing video. */
@Composable
fun PlaybackDialog(state: BrowserUiState, actions: BrowserActions, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("playback-dialog"),
        title = { Text("Playback") },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    GlassButton("−${state.settings.seekSeconds} s", { actions.onSeek(false) }, Modifier.weight(1f).testTag("playback-back10"))
                    GlassButton("+${state.settings.seekSeconds} s", { actions.onSeek(true) }, Modifier.weight(1f).testTag("playback-fwd10"))
                }
                GlassButton("Start over", {
                    actions.onStartOver()
                    onDismiss()
                }, Modifier.fillMaxWidth().testTag("playback-start-over"), icon = StreamIcons.Reload)
                SectionLabel("Speed")
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.streamshell.core.settings.SettingsSanitizer.SPEEDS.forEach { p ->
                        GlassChip((p / 100.0).toString().removeSuffix(".0") + "x", state.settings.playbackSpeed == p, { actions.onSetSpeed(p) }, Modifier.testTag("speed-$p"))
                    }
                }
                val channel = state.player.channel
                if (channel.isNotBlank()) {
                    val remembered = com.streamshell.core.watch.ChannelRules.speedOf(state.watch.channelSpeeds, channel)
                    GlassChip(
                        if (remembered != null) "Always ${(remembered / 100.0).toString().removeSuffix(".0")}x for $channel" else "Remember this speed for $channel",
                        remembered != null,
                        { actions.onSetChannelSpeed(remembered == null) },
                        Modifier.testTag("speed-channel"),
                        role = androidx.compose.ui.semantics.Role.Checkbox
                    )
                }
                SectionLabel("Repeat")
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassChip("Whole video", state.player.loop, { actions.onSetRepeatVideo(!state.player.loop) }, Modifier.testTag("repeat-video"), role = androidx.compose.ui.semantics.Role.Checkbox)
                    val p = state.player
                    GlassChip(
                        when {
                            p.repeatFrom == null -> "Part: set start"
                            p.repeatTo == null -> "Part: set end (from ${WatchRules.formatClock(p.repeatFrom)})"
                            else -> "Part ${WatchRules.formatClock(p.repeatFrom)}–${WatchRules.formatClock(p.repeatTo)}: new start"
                        },
                        p.repeatTo != null,
                        actions.onMarkRepeat,
                        Modifier.testTag("repeat-part"),
                        role = androidx.compose.ui.semantics.Role.Button
                    )
                    if (p.repeatFrom != null) GlassChip("Clear part", false, actions.onClearRepeat, Modifier.testTag("repeat-clear"), role = androidx.compose.ui.semantics.Role.Button)
                }
                if (state.page.chapters.size >= 2) {
                    GlassButton("Next chapter", actions.onNextChapter, Modifier.fillMaxWidth().testTag("playback-next-chapter"), icon = StreamIcons.Chapters)
                }
                SectionLabel(
                    when {
                        state.sleep.atEndOfVideo -> "Sleep timer · end of video"
                        state.sleep.endsAt != null -> "Sleep timer · stops at " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(state.sleep.endsAt))
                        else -> "Sleep timer"
                    }
                )
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassChip("Off", !state.sleep.active, { actions.onSleep(null) }, Modifier.testTag("sleep-off"))
                    listOf(15, 30, 60).forEach { m -> GlassChip("$m min", state.sleep.minutes == m, { actions.onSleep(m) }, Modifier.testTag("sleep-$m")) }
                    GlassChip("End of video", state.sleep.atEndOfVideo, { actions.onSleep(0) }, Modifier.testTag("sleep-end"))
                }
            }
        },
        confirmButton = { com.streamshell.designsystem.QuietTextButton(onClick = onDismiss, modifier = Modifier.testTag("playback-close")) { Text("Done") } }
    )
}
