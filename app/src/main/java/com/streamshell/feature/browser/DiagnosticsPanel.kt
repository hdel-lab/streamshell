package com.streamshell.feature.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamshell.designsystem.GlassButton
import com.streamshell.designsystem.GlassHeader
import com.streamshell.designsystem.Spacing
import com.streamshell.designsystem.StreamIcons
import com.streamshell.designsystem.StreamTheme

/**
 * Diagnostics log (Settings › YouTube › Diagnostics log; the LOG button on every screen): what the YouTube player,
 * StreamShell's scripts and — with "Trace everything" — the whole app did, newest at the bottom. In memory only;
 * "Save as file" writes header, summary and every line to a file the user picks; "Share" hands the same text to an app.
 */
@Composable
fun DiagnosticsPanel(lines: List<String>, tracing: Boolean, onShare: () -> Unit, onSave: () -> Unit, onClear: () -> Unit, onClose: () -> Unit, crash: String? = null) {
    val list = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) list.scrollToItem(lines.lastIndex) }
    Column(Modifier.fillMaxSize().testTag("diagnostics-panel")) {
        GlassHeader("Diagnostics", onClose, backDescription = "Back")
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            GlassButton("Save as file", onSave, Modifier.weight(1f).testTag("diagnostics-save"), icon = StreamIcons.Download, enabled = lines.isNotEmpty())
            GlassButton("Share", onShare, Modifier.weight(1f).testTag("diagnostics-share"), icon = StreamIcons.Share, enabled = lines.isNotEmpty())
            GlassButton("Clear", onClear, Modifier.weight(1f).testTag("diagnostics-clear"), icon = StreamIcons.Close, enabled = lines.isNotEmpty())
        }
        Text(
            (if (tracing) "Tracing everything: touches, page taps and scrolls, page loads, scripts, buffering, frames, stutter, memory, settings. " else "Player log: ads, jumps, page loads, mini player, fullscreen. Switch on \"Trace everything\" in Settings for the whole app. ") +
                "${lines.size} lines. No addresses, titles, search or typed text. \"Save as file\" keeps every line with a summary at the top. Memory only, until the app closes.",
            style = MaterialTheme.typography.bodySmall,
            color = StreamTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
        )
        if (crash != null) {
            Text(
                "Previous crash (also at the top of a saved log):\n" + crash.lines().take(CRASH_PREVIEW_LINES).joinToString("\n"),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                color = StreamTheme.colors.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs).testTag("diagnostics-crash")
            )
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (lines.isEmpty()) {
                Text("No entries yet.", color = StreamTheme.colors.textSecondary, modifier = Modifier.padding(Spacing.lg).testTag("diagnostics-empty"))
            } else {
                LazyColumn(Modifier.widthIn(max = 900.dp).fillMaxSize().testTag("diagnostics-list"), state = list, contentPadding = PaddingValues(Spacing.md)) {
                    itemsIndexed(lines) { _, l ->
                        Text(l, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp, color = StreamTheme.colors.textPrimary, modifier = Modifier.padding(vertical = 1.dp))
                    }
                }
            }
        }
    }
}

private const val CRASH_PREVIEW_LINES = 14
