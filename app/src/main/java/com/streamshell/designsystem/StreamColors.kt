package com.streamshell.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Semantic colour tokens. Screens ask for meaning ("accent", "glass", "textSecondary"), never for a hex value.
 *
 * Identity (2026-10-19, user's choice): YouTube's own contrast — black, white and YouTube blue. Blue is the only
 * accent (links, focus, switches, progress, badges); selection is shown YouTube-style by inverting black/white.
 *
 * - [accent] is the accent as TEXT / icon colour (meets 4.5:1 on every surface).
 * - [accentFill] is the accent as a FILL behind [onAccent] labels (buttons, switches); also 4.5:1.
 * - Glass: [glass] (translucent panel), [glassStrong] (bars over web content), [glassEdge] (fine contour),
 *   [glassSheen] (soft top reflection), [shadow].
 *
 * Every text/background pair is checked against WCAG AA in DesignTokensTest.
 */
@Immutable
data class StreamColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val outline: Color,
    val accent: Color,
    val accentFill: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val accent2: Color,
    val highlight: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val onDanger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val glass: Color,
    val glassStrong: Color,
    val glassEdge: Color,
    val glassSheen: Color,
    val shadow: Color,
    val scrim: Color,
    val isDark: Boolean
) {
    companion object {
        /** Primary look: YouTube's dark theme — near-black, white text, YouTube blue as the only accent. */
        val Dark = StreamColors(
            background = Color(0xFF0F0F0F),
            surface = Color(0xFF181818),
            surfaceRaised = Color(0xFF272727),
            textPrimary = Color(0xFFF1F1F1),
            textSecondary = Color(0xFFAAAAAA),
            divider = Color(0xFF2E2E2E),
            outline = Color(0xFF717171),
            accent = Color(0xFF3EA6FF),
            accentFill = Color(0xFF3EA6FF),
            onAccent = Color(0xFF0F0F0F),
            accentContainer = Color(0xFF263850),
            onAccentContainer = Color(0xFFD3E3FD),
            accent2 = Color(0xFF65B8FF),
            highlight = Color(0xFF3EA6FF),
            success = Color(0xFF2BA640),
            warning = Color(0xFFF9AB00),
            danger = Color(0xFFFF6B63),
            onDanger = Color(0xFF410002),
            dangerContainer = Color(0xFF4E1512),
            onDangerContainer = Color(0xFFFFDAD6),
            glass = Color(0xB3212121),
            glassStrong = Color(0xEB181818),
            glassEdge = Color(0x1FFFFFFF),
            glassSheen = Color(0x0FFFFFFF),
            shadow = Color(0xFF000000),
            scrim = Color(0xA6000000),
            isDark = true
        )

        /** YouTube's light theme — white, near-black text, YouTube blue. */
        val Light = StreamColors(
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFF9F9F9),
            surfaceRaised = Color(0xFFF2F2F2),
            textPrimary = Color(0xFF0F0F0F),
            textSecondary = Color(0xFF606060),
            divider = Color(0xFFE5E5E5),
            outline = Color(0xFF8A8A8A),
            accent = Color(0xFF065FD4),
            accentFill = Color(0xFF065FD4),
            onAccent = Color(0xFFFFFFFF),
            accentContainer = Color(0xFFDEF1FF),
            onAccentContainer = Color(0xFF0B3B8C),
            accent2 = Color(0xFF0B57D0),
            highlight = Color(0xFF065FD4),
            success = Color(0xFF1E7E34),
            warning = Color(0xFFB06000),
            danger = Color(0xFFCC0000),
            onDanger = Color(0xFFFFFFFF),
            dangerContainer = Color(0xFFFFE3E1),
            onDangerContainer = Color(0xFF5C0A07),
            glass = Color(0xD6FFFFFF),
            glassStrong = Color(0xF5FFFFFF),
            glassEdge = Color(0x1A000000),
            glassSheen = Color(0x66FFFFFF),
            shadow = Color(0xFF000000),
            scrim = Color(0x66000000),
            isDark = false
        )

        /** Controls that float over video (minimize, back/forward): always dark glass, whatever the theme. */
        val OnMedia = Dark
    }
}

/** Maps tokens onto Material 3 so stock components (dialogs, menus, switches, sheets) pick them up. */
internal fun StreamColors.toMaterial(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accentFill,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = onAccentContainer,
        secondary = accent2,
        onSecondary = background,
        secondaryContainer = surfaceRaised,
        onSecondaryContainer = textPrimary,
        tertiary = highlight,
        onTertiary = onAccent,
        background = background,
        onBackground = textPrimary,
        surface = background,
        onSurface = textPrimary,
        surfaceVariant = surfaceRaised,
        onSurfaceVariant = textSecondary,
        surfaceTint = Color.Transparent,
        surfaceBright = surfaceRaised,
        surfaceDim = background,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceRaised,
        surfaceContainerHighest = surfaceRaised,
        outline = outline,
        outlineVariant = divider,
        error = danger,
        onError = onDanger,
        errorContainer = dangerContainer,
        onErrorContainer = onDangerContainer,
        scrim = Color.Black
    )
}
