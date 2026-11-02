package com.streamshell.designsystem

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.streamshell.core.model.ThemeMode

object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val panelWidth = 360.dp

    /** Comfortable reading/content width on the inner display; text never stretches past this. */
    val contentMaxWidth = 560.dp
    val minTouch = 48.dp
}

/**
 * Radius scale, used by purpose (not one radius everywhere):
 * small controls 12, input fields 16, buttons 18, panels 24, sheets/dialogs 28, floating bars fully round.
 */
object Radius {
    val control = 12.dp
    val field = 16.dp
    val button = 18.dp
    val panel = 24.dp
    val sheet = 28.dp
    val pill = 100.dp
}

/** Soft shadow depths for glass layers (kept low: depth comes mostly from contour and sheen). */
object Depth {
    val panel = 2.dp
    val floating = 12.dp
}

private val StreamShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.control),
    small = RoundedCornerShape(Radius.control),
    medium = RoundedCornerShape(Radius.field),
    large = RoundedCornerShape(Radius.panel),
    extraLarge = RoundedCornerShape(Radius.sheet)
)

val LocalStreamColors = compositionLocalOf { StreamColors.Light }

/** Access to semantic tokens: `StreamTheme.colors.accent`. */
object StreamTheme {
    val colors: StreamColors
        @Composable @ReadOnlyComposable
        get() = LocalStreamColors.current
}

/** Pure decision, unit-tested: which palette does a mode resolve to? */
fun resolveDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun StreamShellTheme(
    mode: ThemeMode,
    reducedMotion: Boolean = Motion.isReduced(LocalContext.current),
    dynamicColor: Boolean = false,
    /** Dark theme only: pure black backgrounds (OLED). */
    amoledBlack: Boolean = false,
    /** Accent colour (ARGB) instead of StreamShell's own; null = the default accent. */
    accent: Long? = null,
    content: @Composable () -> Unit
) {
    val dark = resolveDark(mode, isSystemInDarkTheme())
    val context = LocalContext.current
    val base = if (dark) StreamColors.Dark else StreamColors.Light
    // Optional: neutrals follow the wallpaper palette (Android 12+). The accent stays StreamShell's.
    val target = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val d = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        base.copy(
            background = d.surface,
            surface = d.surfaceContainerLow,
            surfaceRaised = d.surfaceContainerHigh,
            textPrimary = d.onSurface,
            textSecondary = d.onSurfaceVariant,
            divider = d.outlineVariant,
            outline = d.outline,
            glass = d.surfaceContainer.copy(alpha = base.glass.alpha),
            glassStrong = d.surfaceContainerLow.copy(alpha = base.glassStrong.alpha)
        )
    } else {
        base
    }.let { themed(it, dark, amoledBlack, accent) }
    // Colours cross-fade; the tree is NOT rebuilt (WebViews, scroll and nav state are untouched).
    val tokens = target.animated(reducedMotion)
    val scheme = remember(tokens) { tokens.toMaterial() }
    CompositionLocalProvider(
        LocalStreamColors provides tokens,
        LocalReducedMotion provides reducedMotion
    ) {
        MaterialTheme(colorScheme = scheme, typography = StreamTypography, shapes = StreamShapes, content = content)
    }
}

private val AmoledSurface = Color(0xFF0B0B0B)
private val AmoledRaised = Color(0xFF161616)

/** AMOLED black and the chosen accent on top of a palette (pure, unit-tested). */
fun themed(c: StreamColors, dark: Boolean, amoledBlack: Boolean, accent: Long?): StreamColors {
    val black = if (dark && amoledBlack) c.copy(background = Color.Black, surface = AmoledSurface, surfaceRaised = AmoledRaised) else c
    return if (accent == null) black else Color(accent).let { a -> black.copy(accent = a, accentFill = a) }
}

@Composable
private fun Color.anim(reduced: Boolean): Color = animateColorAsState(this, Motion.spec(reduced, Motion.THEME_MS), label = "theme").value

@Composable
private fun StreamColors.animated(reduced: Boolean): StreamColors = copy(
    background = background.anim(reduced),
    surface = surface.anim(reduced),
    surfaceRaised = surfaceRaised.anim(reduced),
    textPrimary = textPrimary.anim(reduced),
    textSecondary = textSecondary.anim(reduced),
    divider = divider.anim(reduced),
    outline = outline.anim(reduced),
    accent = accent.anim(reduced),
    accentFill = accentFill.anim(reduced),
    glass = glass.anim(reduced),
    glassStrong = glassStrong.anim(reduced),
    glassEdge = glassEdge.anim(reduced),
    onAccent = onAccent.anim(reduced),
    accentContainer = accentContainer.anim(reduced),
    onAccentContainer = onAccentContainer.anim(reduced),
    danger = danger.anim(reduced),
    onDanger = onDanger.anim(reduced),
    dangerContainer = dangerContainer.anim(reduced),
    onDangerContainer = onDangerContainer.anim(reduced)
)

/**
 * Native chrome that sits on a web page (the site bar, the chat card) takes the page's tone: light glass on a light
 * page, dark glass on a dark one — so the app and the website read as one surface instead of a white page with a
 * black bar glued under it. Colours only; the composition tree is the same.
 */
@Composable
fun PageToned(pageColor: Int?, content: @Composable () -> Unit) {
    val base = LocalStreamColors.current
    val dark = pageColor?.let { androidx.compose.ui.graphics.Color(it).luminance() < 0.5f } ?: base.isDark
    // Always the same tree (switching tone must not reset the bar's state, e.g. an open menu).
    val tokens = if (dark == base.isDark) {
        base
    } else if (dark) {
        StreamColors.Dark
    } else {
        StreamColors.Light
    }
    val scheme = remember(tokens) { tokens.toMaterial() }
    CompositionLocalProvider(LocalStreamColors provides tokens) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
    }
}
