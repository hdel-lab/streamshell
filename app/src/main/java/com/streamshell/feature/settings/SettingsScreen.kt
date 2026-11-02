package com.streamshell.feature.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.webkit.WebViewCompat
import com.streamshell.BuildConfig
import com.streamshell.core.model.AppSettings
import com.streamshell.core.model.SearchEngine
import com.streamshell.core.model.ThemeMode
import com.streamshell.designsystem.GlassChip
import com.streamshell.designsystem.GlassDivider
import com.streamshell.designsystem.GlassHeader
import com.streamshell.designsystem.GlassPanel
import com.streamshell.designsystem.QuietOutlinedButton
import com.streamshell.designsystem.QuietTextButton
import com.streamshell.designsystem.Spacing
import com.streamshell.designsystem.StreamIcons
import com.streamshell.designsystem.StreamTheme
import com.streamshell.designsystem.ambientBackground

data class SettingsActions(
    val onUpdate: ((AppSettings) -> AppSettings) -> Unit = {},
    val onOpenPrivacy: () -> Unit = {},
    val onResetSettings: () -> Unit = {},
    val onOpenExternal: (String) -> Unit = {},
    val onClearData: (history: Boolean, cookies: Boolean, cache: Boolean) -> Unit = { _, _, _ -> },
    val onBackup: () -> Unit = {},
    val onRestore: () -> Unit = {}
)

@Composable
fun SettingsScreen(state: SettingsUiState, actions: SettingsActions, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val s = state.settings

    // Settings search: a group shows when its title or one of its words contains every typed word.
    fun shows(words: String) = SettingsSearch.matches(query, words)

    val c = StreamTheme.colors
    Box(modifier.fillMaxSize().ambientBackground(c).testTag("settings-screen")) {
        Column(Modifier.fillMaxSize()) {
            GlassHeader("Settings", onClose, backTag = "settings-back")
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    Modifier.widthIn(max = Spacing.contentMaxWidth).fillMaxSize().testTag("settings-list"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it.take(60) },
                            singleLine = true,
                            placeholder = { Text("Search settings") },
                            leadingIcon = { Icon(StreamIcons.Search, null) },
                            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(StreamIcons.Close, "Clear search") } },
                            modifier = Modifier.fillMaxWidth().testTag("settings-search")
                        )
                    }
                    if (shows(SettingsSearch.APPEARANCE)) {
                        item {
                            GlassPanel(title = "Appearance") {
                                AppearanceSelector(s.themeMode) { mode -> actions.onUpdate { it.copy(themeMode = mode) } }
                                GlassDivider()
                                SwitchRow("AMOLED black", "Dark theme with pure black backgrounds — saves battery on OLED screens.", s.amoledBlack, tag = "amoled") { v ->
                                    actions.onUpdate { it.copy(amoledBlack = v) }
                                }
                                GlassDivider()
                                AccentRow(s.accent) { k -> actions.onUpdate { it.copy(accent = k) } }
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                    GlassDivider()
                                    SwitchRow("Match system colors", "Backgrounds follow your wallpaper palette. The StreamShell accent stays.", s.dynamicColor, tag = "dynamic-color") { v ->
                                        actions.onUpdate { it.copy(dynamicColor = v) }
                                    }
                                }
                            }
                        }
                    }
                    if (shows(SettingsSearch.BROWSING)) {
                        item {
                            GlassPanel(title = "Browsing") {
                                EngineSelector(s.searchEngine) { e -> actions.onUpdate { it.copy(searchEngine = e) } }
                                GlassDivider()
                                SwitchRow("Save history", "Visited pages are kept on this device only (up to 500).", s.saveHistory, tag = "save-history") { v ->
                                    actions.onUpdate { it.copy(saveHistory = v) }
                                }
                                GlassDivider()
                                SwitchRow("Third-party cookies", "Needed for Google sign-in (it spans several Google domains) and some embedded players. Off blocks cookies of other sites inside a page — less tracking across sites, but Google may sign you out.", s.thirdPartyCookies, tag = "third-party-cookies") { v ->
                                    actions.onUpdate { it.copy(thirdPartyCookies = v) }
                                }
                                GlassDivider()
                                SwitchRow("Search suggestions", "While you type in the YouTube search, the text is sent to suggestqueries.google.com for suggestions. Off: only your own recent searches (on this device).", s.searchSuggestions, tag = "search-suggestions") { v ->
                                    actions.onUpdate { it.copy(searchSuggestions = v) }
                                }
                                GlassDivider()
                                SwitchRow("Clean shared links", "Share page removes tracking parts from links (on YouTube everything but the video, start time and playlist; elsewhere utm_…, fbclid and the like).", s.cleanShareLinks, tag = "clean-share") { v ->
                                    actions.onUpdate { it.copy(cleanShareLinks = v) }
                                }
                                GlassDivider()
                                ActionRow("Clear browsing data", "History, cookies and sign-ins, cached files", "clear-data") { confirmClear = true }
                            }
                        }
                    }
                    if (shows(SettingsSearch.START)) {
                        item {
                            GlassPanel(title = "Start page") {
                                StartWithSelector(s.defaultInstance) { v -> actions.onUpdate { it.copy(defaultInstance = v) } }
                            }
                        }
                    }
                    if (shows(SettingsSearch.PLAYBACK)) {
                        item {
                            GlassPanel(title = "Playback") {
                                SwitchRow(
                                    "Background playback",
                                    "Audio and video keep playing when you leave StreamShell or lock the screen, with controls in the notification. " +
                                        "Some sites pause on their own; YouTube's terms reserve background play for YouTube Premium.",
                                    s.backgroundPlayback,
                                    tag = "background-playback"
                                ) { v -> actions.onUpdate { it.copy(backgroundPlayback = v) } }
                                GlassDivider()
                                SwitchRow(
                                    "Automatic picture-in-picture",
                                    "Leaving the app while a video plays shrinks it into a floating window. Also available from the menu.",
                                    s.autoPip,
                                    tag = "auto-pip"
                                ) { v -> actions.onUpdate { it.copy(autoPip = v) } }
                                GlassDivider()
                                SwitchRow("Continue where you stopped", "Remembers the position of videos longer than a minute and continues there (\"Continue watching\" on the start page). Kept on this device.", s.resumePlayback, tag = "resume-playback") { v ->
                                    actions.onUpdate { it.copy(resumePlayback = v) }
                                }
                                GlassDivider()
                                ChipRow("Jump length (notification, headset, playback menu)", "seek-length", com.streamshell.core.settings.SettingsSanitizer.SEEKS.map { it to "$it s" }, s.seekSeconds) { v -> actions.onUpdate { it.copy(seekSeconds = v) } }
                                SwitchRow("Double-tap to jump", "Double-tap the left or right half of a video, by the jump length above. YouTube has its own (10 s).", s.doubleTapSeek, tag = "double-tap") { v ->
                                    actions.onUpdate { it.copy(doubleTapSeek = v) }
                                }
                                GlassDivider()
                                QualityRow("Video quality on Wi-Fi (YouTube)", "quality-wifi", s.qualityWifi) { q -> actions.onUpdate { it.copy(qualityWifi = q) } }
                                QualityRow("Video quality on mobile data (YouTube)", "quality-mobile", s.qualityMobile) { q -> actions.onUpdate { it.copy(qualityMobile = q) } }
                                Text("Asked of YouTube's player when a video starts. YouTube may choose lower when the connection is slow, or ignore it.", style = MaterialTheme.typography.bodySmall, color = StreamTheme.colors.textSecondary, modifier = Modifier.padding(horizontal = Spacing.md))
                                GlassDivider()
                                SwitchRow("Swipe for brightness and volume", "In fullscreen: swipe up or down on the left third for brightness, on the right third for volume. A swipe down in the middle always leaves fullscreen.", s.swipeControls, tag = "swipe-controls") { v ->
                                    actions.onUpdate { it.copy(swipeControls = v) }
                                }
                                GlassDivider()
                                SwitchRow("Audio only in the background", "With background playback, YouTube is asked for its lowest video quality while StreamShell is not on screen; the sound stays. Saves data and battery. YouTube may ignore it.", s.backgroundAudioOnly, tag = "audio-only") { v ->
                                    actions.onUpdate { it.copy(backgroundAudioOnly = v) }
                                }
                                GlassDivider()
                                ChipRow("Volume boost (YouTube)", "volume-boost", com.streamshell.core.settings.SettingsSanitizer.BOOSTS.map { it to if (it == 100) "Off" else "$it %" }, s.volumeBoost) { v -> actions.onUpdate { it.copy(volumeBoost = v) } }
                                SwitchRow("Even out loudness (YouTube)", "Quiet parts louder, loud parts softer (compression). Applies from the next video.", s.normalizeVolume, tag = "normalize") { v ->
                                    actions.onUpdate { it.copy(normalizeVolume = v) }
                                }
                                GlassDivider()
                                LeavePlayerRow(s.leavePlayer) { v -> actions.onUpdate { it.copy(leavePlayer = v) } }
                                GlassDivider()
                                SwitchRow("Sliding player bar", "On an open YouTube video the bottom bar slides sideways: search and sections on one side, Watch later, queue, playlist, speed, repeat and next chapter on the other. Off: the normal bar.", s.playerActionRow, tag = "player-action-row-switch") { v ->
                                    actions.onUpdate { it.copy(playerActionRow = v) }
                                }
                                GlassDivider()
                                SwitchRow("Android Auto: controls on the left", "On the car screen the control rail (search, home, back, scroll) sits at the left edge; off: the right edge. The video's play bar is at the bottom. Applies the next time StreamShell opens in the car.", s.carRailLeft, tag = "car-rail-left") { v ->
                                    actions.onUpdate { it.copy(carRailLeft = v) }
                                }
                                ChipRow("Android Auto: opens with", "car-source", listOf("youtube" to "YouTube", "music" to "YouTube Music"), s.carSource) { v -> actions.onUpdate { it.copy(carSource = v) } }
                                GlassDivider()
                                SwitchRow("Make room for calls and navigation voices", "Playing videos pause during phone and internet calls and continue afterwards; while a navigation, radar or assistant app speaks they play quieter, and continue if that app stopped them. Phone and car screen.", s.yieldAudio, tag = "yield-audio") { v ->
                                    actions.onUpdate { it.copy(yieldAudio = v) }
                                }
                                GlassDivider()
                                CaptionsRow(s.captions, s.captionsLanguage) { mode, lang -> actions.onUpdate { it.copy(captions = mode, captionsLanguage = lang) } }
                                GlassDivider()
                                ChipRow("Break reminder", "break-reminder", com.streamshell.core.settings.SettingsSanitizer.REMINDERS.map { it to if (it == 0) "Off" else "$it min" }, s.breakReminderMinutes) { v -> actions.onUpdate { it.copy(breakReminderMinutes = v) } }
                            }
                        }
                    }
                    if (shows(SettingsSearch.FEED)) {
                        item {
                            GlassPanel(title = "Feed") {
                                SwitchRow(
                                    "Feed previews",
                                    "Muted previews of videos in YouTube's feed. Off: the feed loads no video at all (only pictures) — less data and battery, and a scroll can never open a video by itself.",
                                    s.feedPreviews,
                                    tag = "feed-previews"
                                ) { v -> actions.onUpdate { it.copy(feedPreviews = v) } }
                                GlassDivider()
                                SwitchRow("Shorts as normal videos", "Shorts open in the normal player (with resume, SponsorBlock and the playback menu) instead of YouTube's Shorts player.", s.shortsAsVideos, tag = "shorts-as-videos") { v ->
                                    actions.onUpdate { it.copy(shortsAsVideos = v) }
                                }
                                GlassDivider()
                                SwitchRow("Hide watched videos", "Videos in your history are hidden in feeds, search and recommendations.", s.hideWatched, tag = "hide-watched") { v ->
                                    actions.onUpdate { it.copy(hideWatched = v) }
                                }
                                GlassDivider()
                                FeedPartsRow(s.hiddenFeedParts) { parts -> actions.onUpdate { it.copy(hiddenFeedParts = parts) } }
                                GlassDivider()
                                WordListRow("Blocked channels", "Their videos are hidden in feeds. On a video: More › Hide … in feeds.", "blocked-channels", s.blockedChannels) { v -> actions.onUpdate { it.copy(blockedChannels = v) } }
                                GlassDivider()
                                WordListRow("Blocked words", "Videos whose title contains one of these words are hidden.", "blocked-words", s.blockedKeywords) { v -> actions.onUpdate { it.copy(blockedKeywords = v) } }
                            }
                        }
                    }
                    if (shows(SettingsSearch.YOUTUBE)) {
                        item {
                            GlassPanel(title = "YouTube") {
                                SwitchRow(
                                    "YouTube app mode",
                                    "YouTube and YouTube Music open like apps: no browser bars, the site's sections at the bottom, search and browser functions under More. On YouTube its own top and bottom bars are hidden so videos start at the player. Turn off if a page looks wrong.",
                                    s.youtubeCompact,
                                    tag = "youtube-compact"
                                ) { v -> actions.onUpdate { it.copy(youtubeCompact = v) } }
                                GlassDivider()
                                SwitchRow(
                                    "Autoplay videos",
                                    "A YouTube video starts by itself when you open it, and continues right after an ad. If you pause it, it stays paused.",
                                    s.autoplay,
                                    tag = "autoplay"
                                ) { v -> actions.onUpdate { it.copy(autoplay = v) } }
                                GlassDivider()
                                SwitchRow(
                                    "Swipe down to minimize",
                                    "Swipe down on a playing YouTube video to shrink it into the mini player and keep browsing.",
                                    s.swipeToMinimize,
                                    tag = "swipe-minimize"
                                ) { v -> actions.onUpdate { it.copy(swipeToMinimize = v) } }
                                GlassDivider()
                                SwitchRow(
                                    "Pull down to refresh",
                                    "At the top of a page, pull down (below the video) and let go to reload it.",
                                    s.pullToRefresh,
                                    tag = "pull-refresh"
                                ) { v -> actions.onUpdate { it.copy(pullToRefresh = v) } }
                                GlassDivider()
                                SwitchRow(
                                    "Skip YouTube ads",
                                    "Re-loads the video past ads through YouTube's own player, otherwise mutes the ad, jumps to its end and presses Skip; hides ad cards in the feed. Works on what the page shows, so it can stop working when YouTube changes its player. Against YouTube's terms; YouTube Premium removes ads officially.",
                                    s.skipYouTubeAds,
                                    tag = "skip-ads"
                                ) { v -> actions.onUpdate { it.copy(skipYouTubeAds = v) } }
                                GlassDivider()
                                SwitchRow(
                                    "SponsorBlock",
                                    "Jumps over parts of YouTube videos that the SponsorBlock community marked (sponsor reads, self-promotion, reminders). It skips parts of the video itself, not YouTube's ads. Only a 4-character hash prefix of the video ID is sent to sponsor.ajay.app.",
                                    s.sponsorBlock,
                                    tag = "sponsorblock"
                                ) { v -> actions.onUpdate { it.copy(sponsorBlock = v) } }
                                if (s.sponsorBlock) SponsorCategories(s.sponsorCategories) { cats -> actions.onUpdate { it.copy(sponsorCategories = cats) } }
                                if (s.sponsorBlock) {
                                    SwitchRow("Show segments on the timeline", "Marks the skipped parts in their SponsorBlock colours on YouTube's timeline.", s.sponsorMarkers, tag = "sponsor-markers") { v ->
                                        actions.onUpdate { it.copy(sponsorMarkers = v) }
                                    }
                                }
                                GlassDivider()
                                SwitchRow(
                                    "Show dislikes",
                                    "Shows the dislike count next to YouTube's dislike button (Return YouTube Dislike: an estimate from archived data and its users). When on, the ID of each video you open and your IP address go to returnyoutubedislikeapi.com (a third party). Off by default.",
                                    s.returnDislikes,
                                    tag = "return-dislikes"
                                ) { v -> actions.onUpdate { it.copy(returnDislikes = v) } }
                                GlassDivider()
                                SwitchRow(
                                    "DeArrow: better titles",
                                    "Replaces clickbait titles with titles written by the DeArrow community (the original stays as tooltip). Only a 4-character hash prefix of each video ID is sent to sponsor.ajay.app. Data: DeArrow contributors, CC BY-NC-SA 4.0.",
                                    s.deArrow,
                                    tag = "dearrow"
                                ) { v -> actions.onUpdate { it.copy(deArrow = v) } }
                                GlassDivider()
                                SwitchRow(
                                    "Diagnostics log",
                                    "For finding faults: logs what YouTube's player and StreamShell's scripts do (ads, jumps, page loads, mini player, fullscreen). Open the log with the log button or under More › Diagnostics log; save it as a file or share it. Kept in memory only, cleared when switched off.",
                                    s.diagnostics,
                                    tag = "diagnostics"
                                ) { v -> actions.onUpdate { it.copy(diagnostics = v) } }
                                if (s.diagnostics) {
                                    SwitchRow(
                                        "Trace everything (whole app)",
                                        "Also every touch and swipe on StreamShell's screens (which element), every tap and scroll on pages (kind of element only), page loads with times and request counts, script errors, buffering, dropped frames and quality every 5 s, screen stutter, memory and every settings change. No addresses, titles, search text or typed text. More lines, a little more battery.",
                                        s.traceEverything,
                                        tag = "trace-everything"
                                    ) { v -> actions.onUpdate { it.copy(traceEverything = v) } }
                                    SwitchRow(
                                        "Log button on every screen",
                                        "A small LOG button at the right edge (drag it up or down) opens the log from any tab.",
                                        s.logButton,
                                        tag = "log-button-switch"
                                    ) { v -> actions.onUpdate { it.copy(logButton = v) } }
                                }
                            }
                        }
                    }
                    if (shows(SettingsSearch.PRIVACY)) {
                        item {
                            GlassPanel(title = "AdBlock & Privacy") {
                                ActionRow("AdBlock & Privacy", state.privacySummary, "settings-open-privacy", actions.onOpenPrivacy)
                            }
                        }
                    }
                    if (shows(SettingsSearch.BACKUP)) {
                        item {
                            GlassPanel(title = "Backup") {
                                ActionRow("Back up to a file", "Bookmarks, history, Watch later, positions, watch time, channels and settings as one JSON file. No passwords or sign-ins. The file is NOT encrypted: anyone who gets it can read your history — keep it private.", "backup-export", actions.onBackup)
                                GlassDivider()
                                ActionRow("Restore from a file", "Replaces those lists and settings with the backup's.", "backup-import", actions.onRestore)
                            }
                        }
                    }
                    if (shows(SettingsSearch.ABOUT)) {
                        item {
                            GlassPanel(title = "About") {
                                AboutBlock(LocalContext.current)
                                GlassDivider()
                                ActionRow("Reset settings", "All settings on this page back to their defaults. AdBlock & Privacy settings, bookmarks, history, Watch later and sign-ins are kept.", "reset-settings") { confirmReset = true }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ClearDataDialog(onDismiss = { confirmClear = false }) { h, c, k ->
            confirmClear = false
            actions.onClearData(h, c, k)
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset settings?") },
            text = { Text("Appearance, browsing, start page, playback, YouTube settings and the bottom bar return to their defaults. AdBlock & Privacy settings, bookmarks, history and sign-ins are kept.") },
            confirmButton = {
                Button(onClick = {
                    confirmReset = false
                    actions.onResetSettings()
                }, modifier = Modifier.testTag("confirm-action")) { Text("Confirm") }
            },
            dismissButton = { QuietTextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
        )
    }
}

/** Three-way System / Light / Dark selector. State is conveyed by icon, label weight and fill, not colour alone. */
@Composable
private fun AppearanceSelector(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        Triple(ThemeMode.SYSTEM, "System", StreamIcons.ThemeSystem),
        Triple(ThemeMode.LIGHT, "Light", StreamIcons.ThemeLight),
        Triple(ThemeMode.DARK, "Dark", StreamIcons.ThemeDark)
    )
    Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Row(
            Modifier.fillMaxWidth().selectableGroup().testTag("appearance-selector"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            options.forEach { (mode, label, icon) ->
                val selected = current == mode
                val shape = androidx.compose.foundation.shape.RoundedCornerShape(com.streamshell.designsystem.Radius.field)
                val c = StreamTheme.colors
                Surface(
                    shape = shape,
                    color = if (selected) c.accentFill.copy(alpha = if (c.isDark) 0.26f else 0.12f) else c.glass,
                    contentColor = if (selected) c.textPrimary else c.textSecondary,
                    border = androidx.compose.foundation.BorderStroke(
                        if (selected) 1.5.dp else 1.dp,
                        if (selected) c.accent else c.glassEdge
                    ),
                    modifier = Modifier.weight(1f).heightIn(min = Spacing.minTouch)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(mode) })
                        .testTag("theme-${mode.name.lowercase()}")
                ) {
                    Column(
                        Modifier.padding(vertical = Spacing.sm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Icon(icon, null, Modifier.size(22.dp))
                        Text(
                            label,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (selected) {
                                    androidx.compose.ui.text.font.FontWeight.Bold
                                } else {
                                    androidx.compose.ui.text.font.FontWeight.Medium
                                }
                            )
                        )
                    }
                }
            }
        }
        Text(
            when (current) {
                ThemeMode.SYSTEM -> "Follows the Android light/dark setting."
                ThemeMode.LIGHT -> "Always light, whatever Android is set to."
                ThemeMode.DARK -> "Always dark, whatever Android is set to."
            },
            style = MaterialTheme.typography.bodySmall,
            color = StreamTheme.colors.textSecondary,
            modifier = Modifier.padding(top = Spacing.sm)
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, tag: String? = null, onChange: (Boolean) -> Unit) {
    val c = StreamTheme.colors
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = subtitle?.let { { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedTrackColor = c.accentFill,
                    checkedThumbColor = c.onAccent,
                    checkedBorderColor = c.accentFill,
                    uncheckedTrackColor = c.surfaceRaised,
                    uncheckedThumbColor = c.textSecondary,
                    uncheckedBorderColor = c.outline
                )
            )
        },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(role = Role.Switch) { onChange(!checked) }.let { if (tag != null) it.testTag(tag) else it }
    )
}

@Composable
private fun ActionRow(title: String, subtitle: String?, tag: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = subtitle?.let { { Text(it, style = MaterialTheme.typography.bodySmall, color = StreamTheme.colors.textSecondary) } },
        trailingContent = { Icon(StreamIcons.Forward, null, Modifier.size(16.dp), tint = StreamTheme.colors.textSecondary) },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).testTag(tag)
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun EngineSelector(current: SearchEngine, onSelect: (SearchEngine) -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Text("Search engine", style = MaterialTheme.typography.labelLarge)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(vertical = Spacing.sm).selectableGroup().testTag("engine-selector")
        ) {
            SearchEngine.entries.forEach { e ->
                GlassChip(e.label, selected = e == current, onClick = { onSelect(e) }, modifier = Modifier.testTag("engine-${e.name.lowercase()}"))
            }
        }
        Text("Used for text in the address bar that is not a web address.", style = MaterialTheme.typography.bodySmall, color = StreamTheme.colors.textSecondary)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SponsorCategories(current: Set<String>, onChange: (Set<String>) -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Text("Skip", style = MaterialTheme.typography.labelLarge)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(vertical = Spacing.sm).testTag("sponsor-categories")) {
            com.streamshell.core.sponsor.SponsorCategory.entries.forEach { c ->
                val on = c.key in current
                GlassChip(c.label, selected = on, onClick = { onChange(if (on) current - c.key else current + c.key) }, role = Role.Checkbox, modifier = Modifier.testTag("sponsor-${c.key}"))
            }
        }
        Text(
            "Segment data: SponsorBlock contributors (sponsor.ajay.app), CC BY-NC-SA 4.0.",
            style = MaterialTheme.typography.bodySmall,
            color = StreamTheme.colors.textSecondary,
            modifier = Modifier.padding(top = Spacing.xs)
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun StartWithSelector(current: String, onSelect: (String) -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Text("When StreamShell opens", style = MaterialTheme.typography.labelLarge)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.testTag("start-with")) {
            (listOf("" to "StreamShell start page") + com.streamshell.core.browser.SiteApp.entries.map { it.name to it.title }).forEach { (key, label) ->
                GlassChip(label, selected = current == key, onClick = { onSelect(key) }, modifier = Modifier.testTag("start-with-${key.ifEmpty { "start" }.lowercase()}"))
            }
        }
        Text(
            "Opening the app again after leaving it starts here (not at the last video). YouTube and YouTube Music open as one persistent instance each; switch between them under More or in the menu.",
            style = MaterialTheme.typography.bodySmall,
            color = StreamTheme.colors.textSecondary,
            modifier = Modifier.padding(top = Spacing.xs)
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun QualityRow(title: String, tag: String, current: String, onSelect: (String) -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(top = Spacing.sm).selectableGroup().testTag(tag)
        ) {
            com.streamshell.core.model.VideoQuality.OPTIONS.forEach { (q, label) -> GlassChip(label, q == current, { onSelect(q) }, Modifier.testTag("$tag-$q")) }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FeedPartsRow(current: Set<String>, onChange: (Set<String>) -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Text("Hide in feeds", style = MaterialTheme.typography.bodyLarge)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(vertical = Spacing.sm).testTag("feed-parts")) {
            com.streamshell.core.browser.FeedFilter.PARTS.forEach { (key, label) ->
                val on = key in current
                GlassChip(label, on, { onChange(if (on) current - key else current + key) }, role = Role.Checkbox, modifier = Modifier.testTag("feed-hide-$key"))
            }
        }
        Text("Hidden at once, no reload. The Shorts tab itself stays (remove it from the bottom bar if you like).", style = MaterialTheme.typography.bodySmall, color = StreamTheme.colors.textSecondary)
    }
}

/** Words for the settings search, per group (pure, unit-tested). */
object SettingsSearch {
    const val APPEARANCE = "appearance theme dark light system amoled black oled accent colour color wallpaper"
    const val BROWSING = "browsing search engine history cookies third-party suggestions share links clear data"
    const val START = "start page open instance youtube music"
    const val PLAYBACK = "playback background picture-in-picture pip resume continue jump seek double-tap quality wifi mobile swipe brightness volume audio boost loudness " +
        "break reminder leave player mini close action row captions subtitles language android auto car rail left right music source calls navigation radar voice duck audio"
    const val FEED = "feed previews shorts watched hide blocked channels words keywords posts playables filter"
    const val YOUTUBE = "youtube app mode autoplay minimize refresh ads sponsorblock segments timeline dislikes dearrow titles diagnostics log trace everything admin logging button"
    const val PRIVACY = "adblock privacy trackers filter lists"
    const val BACKUP = "backup restore file export import json"
    const val ABOUT = "about version reset licenses"

    /** Every typed word (without case) occurs in [words]; an empty query matches everything. */
    fun matches(query: String, words: String): Boolean {
        val q = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (q.isEmpty()) return true
        val w = words.lowercase()
        return q.all { w.contains(it) }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AccentRow(current: String, onSelect: (String) -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Text("Accent colour", style = MaterialTheme.typography.bodyLarge)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(top = Spacing.sm).selectableGroup().testTag("accent-row")
        ) {
            com.streamshell.core.model.AccentColors.OPTIONS.forEach { (k, argb) ->
                val sel = k == current
                Box(
                    Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(androidx.compose.ui.graphics.Color(argb))
                        .border(if (sel) 3.dp else 0.dp, StreamTheme.colors.textPrimary, androidx.compose.foundation.shape.CircleShape)
                        .selectable(selected = sel, role = Role.RadioButton, onClick = { onSelect(k) })
                        .testTag("accent-$k")
                        .semantics { contentDescription = "Accent $k" }
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LeavePlayerRow(current: String, onSelect: (String) -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Text("Back from the video player", style = MaterialTheme.typography.bodyLarge)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm).selectableGroup().testTag("leave-player")) {
            GlassChip("Mini player", current == "mini", { onSelect("mini") }, Modifier.testTag("leave-player-mini"))
            GlassChip("Close video", current == "close", { onSelect("close") }, Modifier.testTag("leave-player-close"))
        }
        Text("“Close video” stops the video and goes back to the feed (no mini player to close).", style = MaterialTheme.typography.bodySmall, color = StreamTheme.colors.textSecondary)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CaptionsRow(mode: String, language: String, onChange: (String, String) -> Unit) {
    var lang by rememberSaveable(language) { mutableStateOf(language) }
    Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Text("Captions (YouTube)", style = MaterialTheme.typography.bodyLarge)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm).selectableGroup().testTag("captions")) {
            GlassChip("YouTube decides", mode == "default", { onChange("default", lang) }, Modifier.testTag("captions-default"))
            GlassChip("Always on", mode == "on", { onChange("on", lang) }, Modifier.testTag("captions-on"))
            GlassChip("Always off", mode == "off", { onChange("off", lang) }, Modifier.testTag("captions-off"))
        }
        if (mode == "on") {
            OutlinedTextField(
                value = lang,
                onValueChange = { lang = it.take(12) },
                singleLine = true,
                label = { Text("Language code (e.g. en, de, bs)") },
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onChange(mode, lang) }),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm).testTag("captions-language")
            )
        }
        Text("Uses YouTube's own captions (when the video has them); applies from the next video. Not every video has every language.", style = MaterialTheme.typography.bodySmall, color = StreamTheme.colors.textSecondary)
    }
}

/** A list of words (channels, keywords): chips with remove, a field to add. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WordListRow(title: String, help: String, tag: String, words: Set<String>, onChange: (Set<String>) -> Unit) {
    var add by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm).testTag(tag)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(help, style = MaterialTheme.typography.bodySmall, color = StreamTheme.colors.textSecondary)
        if (words.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                words.sortedBy { it.lowercase() }.forEach { w -> GlassChip("$w  ✕", true, { onChange(words - w) }, Modifier.testTag("$tag-$w"), role = Role.Button) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = add,
                onValueChange = { add = it.take(60) },
                singleLine = true,
                placeholder = { Text("Add") },
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                    if (add.isNotBlank()) onChange(words + add.trim())
                    add = ""
                }),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                modifier = Modifier.weight(1f).testTag("$tag-add")
            )
            IconButton(onClick = {
                if (add.isNotBlank()) onChange(words + add.trim())
                add = ""
            }, enabled = add.isNotBlank(), modifier = Modifier.testTag("$tag-add-button")) { Icon(StreamIcons.NewTab, "Add") }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(title: String, tag: String, options: List<Pair<T, String>>, current: T, onSelect: (T) -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(top = Spacing.sm).selectableGroup().testTag(tag)
        ) {
            options.forEach { (v, label) -> GlassChip(label, selected = v == current, onClick = { onSelect(v) }, modifier = Modifier.testTag("$tag-$v")) }
        }
    }
}

@Composable
private fun ClearDataDialog(onDismiss: () -> Unit, onConfirm: (Boolean, Boolean, Boolean) -> Unit) {
    var history by rememberSaveable { mutableStateOf(true) }
    var cookies by rememberSaveable { mutableStateOf(false) }
    var cache by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("clear-data-dialog"),
        title = { Text("Clear browsing data") },
        text = {
            Column {
                CheckRow("History", history, "clear-history") { history = it }
                CheckRow("Cookies and site data (signs you out everywhere in StreamShell)", cookies, "clear-cookies") { cookies = it }
                CheckRow("Cached images and files", cache, "clear-cache") { cache = it }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(history, cookies, cache) }, enabled = history || cookies || cache, modifier = Modifier.testTag("clear-data-confirm")) { Text("Clear") }
        },
        dismissButton = { QuietTextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onChange(!checked) }.padding(vertical = Spacing.xs).testTag(tag)
    ) {
        androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = null)
        Text(label, modifier = Modifier.padding(start = Spacing.sm))
    }
}

@Composable
private fun AboutBlock(context: Context) {
    val webView = remember {
        WebViewCompat.getCurrentWebViewPackage(context)?.let { "${it.packageName} ${it.versionName}" } ?: "unknown"
    }
    Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("StreamShell ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
        Text("Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        Text("Browser engine: Android System WebView $webView")
        Text("No analytics, no telemetry, no backend. Bookmarks, history, sign-ins and settings stay on this device.")
        Text("Permissions: internet and network state; foreground service for media playback (background audio); notifications (Android 13+, asked once, only for the playback controls); Android Auto: car screen and car speed (the picture shows only while parked).")
    }
}
