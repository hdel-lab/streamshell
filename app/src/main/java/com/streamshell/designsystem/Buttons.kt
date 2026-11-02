package com.streamshell.designsystem

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Secondary actions are neutral (primary text colour). Only the single most important action on a
 * screen uses the accent fill (Button). This keeps the accent meaningful instead of everywhere.
 */
@Composable
fun QuietOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) = OutlinedButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.button),
    colors = ButtonDefaults.outlinedButtonColors(containerColor = StreamTheme.colors.glass, contentColor = MaterialTheme.colorScheme.onSurface),
    border = androidx.compose.foundation.BorderStroke(1.dp, StreamTheme.colors.glassEdge),
    content = content
)

@Composable
fun QuietTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) = TextButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.button),
    colors = ButtonDefaults.textButtonColors(contentColor = StreamTheme.colors.accent),
    content = content
)
