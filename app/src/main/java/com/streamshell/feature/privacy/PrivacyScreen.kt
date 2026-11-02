package com.streamshell.feature.privacy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.streamshell.core.model.AppSettings
import com.streamshell.core.privacy.EngineState
import com.streamshell.core.privacy.FilterSources
import com.streamshell.core.privacy.ListEntry
import com.streamshell.designsystem.QuietOutlinedButton
import com.streamshell.designsystem.QuietTextButton
import com.streamshell.designsystem.Spacing
import com.streamshell.designsystem.StreamIcons
import com.streamshell.designsystem.StreamTheme
import com.streamshell.designsystem.glass
import java.text.DateFormat
import java.util.Date

data class PrivacyActions(
    val onUpdate: ((AppSettings) -> AppSettings) -> Unit = {},
    val onUpdateNow: () -> Unit = {},
    val onSetListEnabled: (String, Boolean) -> Unit = { _, _ -> },
    val onRemoveList: (String) -> Unit = {},
    val onAddList: (name: String, url: String, trust: Boolean) -> Unit = { _, _, _ -> },
    val onCancelTrust: () -> Unit = {},
    val onAddBlocked: (String) -> Unit = {},
    val onAddAllowed: (String) -> Unit = {},
    val onRemoveBlocked: (String) -> Unit = {},
    val onRemoveAllowed: (String) -> Unit = {},
    val onAddExempt: (String) -> Unit = {},
    val onRemoveExempt: (String) -> Unit = {},
    val onClearStats: () -> Unit = {},
    val onResetDefaults: () -> Unit = {},
    val explain: (String) -> String = { "" },
    val onOpenExternal: (String) -> Unit = {}
)

private val INTERVALS = listOf(0 to "Off", 12 to "12 hours", 24 to "Daily", 72 to "3 days", 168 to "Weekly")

@Composable
fun PrivacyScreen(state: PrivacyUiState, actions: PrivacyActions, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val s = state.settings

    Surface(modifier.fillMaxSize().testTag("privacy-screen"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            com.streamshell.designsystem.GlassHeader("AdBlock & Privacy", onClose, backTag = "privacy-back")
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(Modifier.widthIn(max = Spacing.contentMaxWidth).fillMaxSize()) {
                    // Master switch + honest status
                    item {
                        SwitchRow("Protection", "Checks links and watches the player's requests with the rules below.", s.filteringEnabled, "privacy-master") { v ->
                            actions.onUpdate { it.copy(filteringEnabled = v) }
                        }
                    }
                    item { ListItem(headlineContent = { Text(engineText(state.engine, s.filteringEnabled), modifier = Modifier.testTag("privacy-engine-status")) }) }
                    item { LimitsCard() }

                    // Statistics
                    item { Header("Statistics (this session)") }
                    item {
                        Column(Modifier.padding(horizontal = Spacing.md).testTag("privacy-stats"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Links blocked by StreamShell: ${state.blocked}", modifier = Modifier.testTag("stat-blocked"))
                            Text("Player requests observed (never blocked): ${state.observed}", modifier = Modifier.testTag("stat-observed"))
                            Text("Lists enabled: ${state.enabledLists}", modifier = Modifier.testTag("stat-lists"))
                            Text("Last successful list update: ${state.lastUpdateMs?.let { formatTime(it) } ?: "never"}", modifier = Modifier.testTag("stat-updated"))
                            Text(
                                "These count only what StreamShell itself saw or refused. They are not an estimate of your device's traffic.",
                                style = MaterialTheme.typography.bodySmall,
                                color = StreamTheme.colors.textSecondary
                            )
                        }
                    }
                    item {
                        SwitchRow("Keep host-level details", "Remember which hosts were refused/observed (hosts only, in memory). Off keeps only counters.", s.detailedLogging, "privacy-logging") { v ->
                            actions.onUpdate { it.copy(detailedLogging = v) }
                        }
                    }
                    if (state.recent.isNotEmpty()) {
                        item { SubHeader("Recently blocked links") }
                        items(state.recent.take(10), key = { "r-${it.timestampMs}-${it.host}" }) { r ->
                            ListItem(headlineContent = { Text(r.host) }, supportingContent = { Text("${r.rule} · ${sourceLabel(r.source, state.entries)}") })
                        }
                    }
                    if (state.observedHosts.isNotEmpty()) {
                        item { SubHeader("Hosts the player contacted (observed only)") }
                        items(state.observedHosts.entries.sortedByDescending { it.value }.take(10), key = { "o-${it.key}" }) { e ->
                            ListItem(headlineContent = { Text(e.key) }, trailingContent = { Text("${e.value}×") })
                        }
                    }
                    item {
                        QuietTextButton(onClick = actions.onClearStats, modifier = Modifier.padding(horizontal = Spacing.sm).testTag("privacy-clear-stats")) { Text("Clear statistics") }
                    }

                    // Lists
                    item { Header("Filter lists") }
                    items(state.entries, key = { "l-${it.subscription.id}" }) { e -> ListRow(e, actions) }
                    item {
                        Column(Modifier.padding(horizontal = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Button(onClick = actions.onUpdateNow, enabled = !state.updating && state.enabledLists > 0, modifier = Modifier.testTag("privacy-update-now")) {
                                Text(if (state.updating) "Updating…" else "Update all lists now")
                            }
                            state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("privacy-message")) }
                            Text("Automatic updates", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.testTag("interval-group")) {
                                INTERVALS.forEach { (h, label) ->
                                    FilterChip(
                                        selected = s.updateIntervalHours == h,
                                        onClick = { actions.onUpdate { it.copy(updateIntervalHours = h) } },
                                        label = { Text(label, maxLines = 1) },
                                        modifier = Modifier.testTag("interval-$h")
                                    )
                                }
                            }
                            Text(
                                "Runs in the background when a network is available. A failed update keeps the previous lists.",
                                style = MaterialTheme.typography.bodySmall,
                                color = StreamTheme.colors.textSecondary
                            )
                        }
                    }
                    item { AddListForm(actions) }

                    // Custom rules
                    item { Header("Your rules") }
                    item { DomainRules("Always block", "custom-block", s.customBlockedDomains, actions.onAddBlocked, actions.onRemoveBlocked) }
                    item { DomainRules("Always allow", "custom-allow", s.customAllowedDomains, actions.onAddAllowed, actions.onRemoveAllowed) }
                    item { DomainRules("Sites without blocking", "exempt-site", s.filterExemptSites, actions.onAddExempt, actions.onRemoveExempt) }
                    item { TestDomain(actions.explain) }

                    item { Header("Reset") }
                    item {
                        QuietOutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.padding(horizontal = Spacing.md).testTag("privacy-reset")) {
                            Text("Reset to recommended defaults")
                        }
                    }
                    item { Box(Modifier.padding(bottom = Spacing.xl)) }
                }
            }
        }
    }

    state.pendingTrust?.let { p ->
        AlertDialog(
            onDismissRequest = actions.onCancelTrust,
            title = { Text("Trust ${p.host}?") },
            text = { Text("StreamShell will download filter rules from ${p.host} over https. The list is only read as data (domain rules) and never executed. Only add sources you trust.") },
            confirmButton = { Button(onClick = { actions.onAddList(p.name, p.url, true) }, modifier = Modifier.testTag("trust-confirm")) { Text("Trust and add") } },
            dismissButton = { QuietTextButton(onClick = actions.onCancelTrust, modifier = Modifier.testTag("trust-cancel")) { Text("Cancel") } }
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset to recommended defaults?") },
            text = { Text("Lists return to the recommended set (your own lists are removed), your block/allow rules are cleared, protection is switched on and updates run daily.") },
            confirmButton = {
                Button(onClick = {
                    confirmReset = false
                    actions.onResetDefaults()
                }, modifier = Modifier.testTag("reset-confirm")) { Text("Reset") }
            },
            dismissButton = { QuietTextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
        )
    }
}

private fun formatTime(ms: Long) = DateFormat.getDateTimeInstance().format(Date(ms))

private fun engineText(e: EngineState, enabled: Boolean): String = when {
    !enabled -> "Protection is off."
    e is EngineState.Initializing -> "Filter engine is starting."
    e is EngineState.Failed -> "Filter engine failed: ${e.reason}. Nothing is being checked."
    e is EngineState.Ready -> "Filter engine ready: ${e.ruleCount} rules from ${e.sourceIds.size} list(s)."
    else -> ""
}

private fun sourceLabel(id: String?, entries: List<ListEntry>): String = when (id) {
    null -> "unknown list"
    "custom" -> "your rule"
    FilterSources.BUILTIN_ID -> "built-in list"
    else -> entries.firstOrNull { it.subscription.id == id }?.subscription?.name ?: id
}

@Composable
private fun LimitsCard() {
    Surface(
        color = androidx.compose.ui.graphics.Color.Transparent,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .glass(StreamTheme.colors, androidx.compose.foundation.shape.RoundedCornerShape(com.streamshell.designsystem.Radius.panel)).testTag("privacy-limits")
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("What this does, and what it cannot do", style = MaterialTheme.typography.titleSmall)
            Text(
                "• Requests that pages load inside StreamShell (scripts, images, frames, XHR, media) are checked and blocked when a rule matches. A top-level page that matches is not opened; you can open it anyway.\n" +
                    "• Sign-in, video, thumbnail and subtitle servers (Google, YouTube, Vimeo, Twitch, Dailymotion CDNs) are never blocked by lists, so those sites keep working. Ads served from those same servers, such as YouTube video ads, are therefore not blocked.\n" +
                    "• Only plain domain rules from EasyList/EasyPrivacy are used; cosmetic and path rules are skipped (counted per list).\n" +
                    "• You can switch blocking off per site (shield in the address bar) and add your own block/allow rules.\n" +
                    "• Other apps and the rest of the device are not filtered; this is not a VPN.",
                style = MaterialTheme.typography.bodySmall,
                color = StreamTheme.colors.textSecondary
            )
        }
    }
}

@Composable
private fun ListRow(e: ListEntry, actions: PrivacyActions) {
    val sub = e.subscription
    val st = e.status
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("list-${sub.id}")) {
        ListItem(
            headlineContent = { Text(sub.name) },
            supportingContent = {
                Column {
                    val facts = buildList {
                        st.ruleCount?.let { add("$it rules") }
                        st.skippedCount?.takeIf { it > 0 }?.let { add("$it unsupported lines skipped") }
                        add(st.lastSuccessMs?.let { "updated ${formatTime(it)}" } ?: "not downloaded yet")
                    }
                    Text(facts.joinToString(" · "), modifier = Modifier.testTag("list-status-${sub.id}"))
                    st.error?.let {
                        Text("Last update failed: $it" + if (e.cached) " (previous copy still active)" else "", color = StreamTheme.colors.danger, modifier = Modifier.testTag("list-error-${sub.id}"))
                    }
                    val credit = listOfNotNull(sub.license?.let { "License: $it" }, sub.homepage?.let { "Source: ${it.removePrefix("https://")}" }).joinToString(" · ")
                    if (credit.isNotEmpty()) {
                        Text(
                            credit,
                            style = MaterialTheme.typography.bodySmall,
                            color = StreamTheme.colors.textSecondary,
                            modifier = Modifier.clickable(enabled = sub.homepage != null) { sub.homepage?.let(actions.onOpenExternal) }
                        )
                    }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!sub.recommended) {
                        IconButton(onClick = { actions.onRemoveList(sub.id) }, modifier = Modifier.testTag("list-remove-${sub.id}")) {
                            Icon(StreamIcons.ClearData, contentDescription = "Remove ${sub.name}")
                        }
                    }
                    Switch(checked = sub.enabled, onCheckedChange = { actions.onSetListEnabled(sub.id, it) }, modifier = Modifier.testTag("list-switch-${sub.id}"))
                }
            }
        )
    }
}

@Composable
private fun AddListForm(actions: PrivacyActions) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Add a list", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("add-list-name"))
        OutlinedTextField(
            url,
            { url = it },
            label = { Text("https:// address of the list") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().testTag("add-list-url")
        )
        QuietOutlinedButton(
            onClick = {
                actions.onAddList(name, url, false)
            },
            enabled = name.isNotBlank() && url.isNotBlank(),
            modifier = Modifier.testTag("add-list-button")
        ) { Text("Add list") }
        Text("Supported: domain rules (||example.com^) and hosts-file lines.", style = MaterialTheme.typography.bodySmall, color = StreamTheme.colors.textSecondary)
    }
}

@Composable
private fun DomainRules(title: String, tag: String, domains: Set<String>, onAdd: (String) -> Unit, onRemove: (String) -> Unit) {
    var text by rememberSaveable(tag) { mutableStateOf("") }
    Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                text,
                { text = it },
                placeholder = { Text("example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.weight(1f).testTag("$tag-input")
            )
            QuietOutlinedButton(onClick = {
                onAdd(text)
                text = ""
            }, enabled = text.isNotBlank(), modifier = Modifier.testTag("$tag-add")) { Text("Add") }
        }
        if (domains.isEmpty()) Text("None", style = MaterialTheme.typography.bodySmall, color = StreamTheme.colors.textSecondary)
        domains.sorted().forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("$tag-row-$d")) {
                Text(d, modifier = Modifier.weight(1f))
                IconButton(onClick = { onRemove(d) }, modifier = Modifier.testTag("$tag-remove-$d")) { Icon(StreamIcons.Close, contentDescription = "Remove $d") }
            }
        }
    }
}

@Composable
private fun TestDomain(explain: (String) -> String) {
    var text by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Test a domain", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                text,
                {
                    text = it
                    result = null
                },
                placeholder = { Text("tracker.example") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.weight(1f).testTag("test-domain-input")
            )
            QuietOutlinedButton(onClick = { result = explain(text) }, enabled = text.isNotBlank(), modifier = Modifier.testTag("test-domain-button")) { Text("Check") }
        }
        result?.let { Text(it, modifier = Modifier.testTag("test-domain-result")) }
    }
}

@Composable
private fun Header(text: String) {
    com.streamshell.designsystem.SectionLabel(text, Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.lg, bottom = Spacing.xs))
}

@Composable
private fun SubHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = StreamTheme.colors.textSecondary, modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs))
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedTrackColor = StreamTheme.colors.accentFill,
                    checkedThumbColor = StreamTheme.colors.onAccent,
                    checkedBorderColor = StreamTheme.colors.accentFill,
                    uncheckedTrackColor = StreamTheme.colors.surfaceRaised,
                    uncheckedThumbColor = StreamTheme.colors.textSecondary,
                    uncheckedBorderColor = StreamTheme.colors.outline
                )
            )
        },
        modifier = Modifier.clickable(role = Role.Switch) { onChange(!checked) }.testTag(tag)
    )
}
