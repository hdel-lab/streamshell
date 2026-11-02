package com.streamshell.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Liquid Glass components.
 *
 * Glass = controlled translucency + a fine light contour (brighter at the top, like light catching an edge)
 * + a soft top sheen + a very soft shadow. Real backdrop blur is not used: on Android it would either blur
 * the content itself or need a copy of the WebView every frame (expensive on battery and video). The
 * translucent fills are tuned so text stays readable over any page; bars over web content use the stronger
 * glass. Nothing here is decorative-only: every component wraps a real action or real content.
 */

/** Paints a glass layer in [shape]. Use through [GlassSurface] or directly on custom layouts. */
fun Modifier.glass(
    colors: StreamColors,
    shape: Shape,
    strong: Boolean = false,
    elevation: Dp = 0.dp,
    tint: Color = Color.Transparent
): Modifier = this
    .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape, clip = false, ambientColor = colors.shadow.copy(alpha = 0.35f), spotColor = colors.shadow.copy(alpha = 0.45f)) else Modifier)
    .clip(shape)
    .background(if (strong) colors.glassStrong else colors.glass)
    .then(if (tint != Color.Transparent) Modifier.background(tint) else Modifier)
    .background(Brush.verticalGradient(0f to colors.glassSheen, 0.45f to Color.Transparent))
    .border(1.dp, Brush.verticalGradient(listOf(colors.glassEdge, colors.glassEdge.copy(alpha = colors.glassEdge.alpha * 0.3f))), shape)

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.panel),
    strong: Boolean = false,
    elevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier.glass(StreamTheme.colors, shape, strong, elevation), content = content)
}

/** A titled group of rows (settings, start page sections). */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier) {
        if (title != null) {
            Row(Modifier.fillMaxWidth().padding(start = Spacing.xs, end = Spacing.xs, bottom = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(title, Modifier.weight(1f))
                trailing?.invoke()
            }
        }
        Column(Modifier.fillMaxWidth().glass(StreamTheme.colors, RoundedCornerShape(Radius.panel), elevation = Depth.panel).padding(vertical = Spacing.xs), content = content)
    }
}

/** Small uppercase label above a group. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp)),
        color = StreamTheme.colors.textSecondary,
        modifier = modifier
    )
}

/** Thin separator inside a glass panel. */
@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(horizontal = Spacing.md).height(1.dp).background(StreamTheme.colors.glassEdge))
}

/** Press feedback: a quick, subtle scale-down (none with reduced motion). */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource, pressed: Float = 0.94f): Modifier {
    val reduced = LocalReducedMotion.current
    val down by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (down && !reduced) pressed else 1f, spring(dampingRatio = 0.6f, stiffness = 900f), label = "press")
    return this.graphicsLayer {
        scaleX = s
        scaleY = s
    }
}

/**
 * Round glass icon button. [contentDescription] is required for anything without a visible label.
 * [selected] tints it with the accent (state is also given by the icon/semantics, not by colour alone).
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    glass: Boolean = true,
    colors: StreamColors = StreamTheme.colors,
    tint: Color? = null
) {
    val source = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) colors.accentFill.copy(alpha = 0.22f) else Color.Transparent, tween(Motion.SHORT_MS), label = "sel")
    val fg = tint ?: if (!enabled) {
        colors.textSecondary.copy(alpha = 0.38f)
    } else if (selected) {
        colors.accent
    } else {
        colors.textPrimary
    }
    Box(
        modifier
            .size(size)
            .pressScale(source)
            .then(if (glass) Modifier.glass(colors, CircleShape) else Modifier.clip(CircleShape))
            .background(bg, CircleShape)
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(bounded = true), enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(iconSize))
    }
}

/** Chip for selections (search engine, instance switcher, filters). */
@Composable
fun GlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    role: Role = Role.RadioButton
) {
    val c = StreamTheme.colors
    val source = remember { MutableInteractionSource() }
    // YouTube-style chips: the selected one is inverted (white on dark, black on light).
    val bg by animateColorAsState(if (selected) c.textPrimary else c.surfaceRaised, tween(Motion.SHORT_MS), label = "chip")
    val edge by animateColorAsState(if (selected) c.textPrimary else Color.Transparent, tween(Motion.SHORT_MS), label = "edge")
    Row(
        modifier
            .heightIn(min = 40.dp)
            .pressScale(source, 0.96f)
            .clip(RoundedCornerShape(Radius.control))
            .background(bg, RoundedCornerShape(Radius.control))
            .border(1.dp, edge, RoundedCornerShape(Radius.control))
            .selectable(selected = selected, interactionSource = source, indication = androidx.compose.material3.ripple(), role = role, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = if (selected) c.background else c.textPrimary)
        Text(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            color = if (selected) c.background else c.textPrimary
        )
    }
}

/** The one most important action on a screen: violet fill with a soft blue sheen. */
@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val c = StreamTheme.colors
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(Radius.button)
    Row(
        modifier
            .heightIn(min = 48.dp)
            .pressScale(source, 0.97f)
            .clip(shape)
            .background(if (enabled) Brush.horizontalGradient(listOf(c.accentFill, lerpColor(c.accentFill, c.highlight, 0.35f))) else Brush.linearGradient(listOf(c.surfaceRaised, c.surfaceRaised)))
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.16f else 0f), shape)
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        val fg = if (enabled) c.onAccent else c.textSecondary
        if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = fg)
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

/** Secondary action: glass button with a neutral label. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val c = StreamTheme.colors
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(Radius.button)
    Row(
        modifier
            .heightIn(min = 44.dp)
            .pressScale(source, 0.97f)
            .glass(c, shape)
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        val fg = if (enabled) c.textPrimary else c.textSecondary.copy(alpha = 0.5f)
        if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = fg)
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

/** Height of the floating navigation (compact: content gets the room, touch targets stay ≥ 48 dp). */
val NavBarHeight = 56.dp

data class GlassNavItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val tag: String
)

/**
 * Floating glass navigation: items share the width and one indicator capsule glides to the active item.
 * The active item is marked by the capsule, a filled icon and a bold label (not by colour alone).
 * The item with [capsuleKey] (search) is drawn as its own rounded field wherever it stands in the order; it is wider
 * (icon + label) when there is room, otherwise only its icon, so every section label fits.
 */
@Composable
fun GlassNavBar(
    items: List<GlassNavItem>,
    selectedKey: String?,
    onSelect: (GlassNavItem) -> Unit,
    modifier: Modifier = Modifier,
    colors: StreamColors = StreamTheme.colors,
    leading: (@Composable RowScope.() -> Unit)? = null,
    capsuleKey: String? = null
) {
    val reduced = LocalReducedMotion.current
    Row(
        modifier
            .fillMaxWidth()
            .height(NavBarHeight)
            .glass(colors, RoundedCornerShape(Radius.pill), strong = true, elevation = Depth.floating),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke(this)
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 4.dp)) {
            val n = items.size.coerceAtLeast(1)
            val hasCapsule = items.any { it.key == capsuleKey }
            val wideCapsule = hasCapsule && maxWidth / (n - 1 + 1.6f) >= 76.dp
            val weights = items.map { if (it.key == capsuleKey && wideCapsule) 1.6f else 1f }
            val unit = maxWidth / weights.sum().coerceAtLeast(1f)
            val starts = weights.runningFold(0f) { a, w -> a + w }
            val index = items.indexOfFirst { it.key == selectedKey && it.key != capsuleKey }
            val w = unit * (weights.getOrNull(index) ?: 1f)
            val pillW = minOf(w - 8.dp, 60.dp)
            val x by animateDpAsState(unit * starts[index.coerceAtLeast(0)] + (w - pillW) / 2, if (reduced) tween(0) else spring(dampingRatio = 0.78f, stiffness = 520f), label = "indicator")
            val alpha by animateFloatAsState(if (index >= 0) 1f else 0f, tween(Motion.SHORT_MS), label = "indicatorAlpha")
            // Indicator pill behind the active icon: a neutral YouTube-style highlight (white/black tint).
            Box(
                Modifier
                    .offset(x = x, y = 6.dp)
                    .width(pillW)
                    .height(28.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(colors.textPrimary.copy(alpha = if (colors.isDark) 0.14f else 0.08f))
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                // Each item in its own keyed group, no early return: a bar whose items change (YouTube ⇄ YouTube Music,
                // search capsule moved) recomposed state into the wrong slots — ClassCastException Rgb → InteractionSource
                // in the crash report of 2026-11-01 23:41.
                items.forEachIndexed { i, item ->
                    key(item.key, i) {
                        NavBarItem(item, item.key == capsuleKey, item.key == selectedKey, weights[i], wideCapsule, reduced, colors, onSelect)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavBarItem(
    item: GlassNavItem,
    capsule: Boolean,
    on: Boolean,
    weight: Float,
    wideCapsule: Boolean,
    reduced: Boolean,
    colors: StreamColors,
    onSelect: (GlassNavItem) -> Unit
) {
    val source = remember { MutableInteractionSource() }
    if (capsule) {
        Box(Modifier.weight(weight).fillMaxHeight().padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
            Row(
                Modifier.fillMaxWidth().height(38.dp).pressScale(source, 0.96f)
                    .clip(RoundedCornerShape(Radius.pill)).background(colors.surfaceRaised)
                    .border(1.dp, colors.glassEdge, RoundedCornerShape(Radius.pill))
                    .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = { onSelect(item) })
                    .padding(horizontal = 10.dp)
                    .testTag(item.tag).semantics { contentDescription = item.label },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
            ) {
                Icon(item.icon, null, Modifier.size(20.dp), tint = colors.textPrimary)
                if (wideCapsule) Text(item.label, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary, maxLines = 1)
            }
        }
    } else {
        val fg by animateColorAsState(if (on) colors.textPrimary else colors.textSecondary, tween(Motion.SHORT_MS), label = "fg")
        Column(
            Modifier.weight(weight).fillMaxHeight().pressScale(source, 0.92f)
                .selectable(selected = on, interactionSource = source, indication = null, role = Role.Tab, onClick = { onSelect(item) })
                .testTag(item.tag),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) {
                // A small lift of the active icon (with the gliding capsule behind it).
                val lift by animateFloatAsState(if (on && !reduced) 1.08f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "lift")
                Icon(
                    if (on) item.selectedIcon else item.icon,
                    null,
                    tint = if (on) colors.textPrimary else fg,
                    modifier = Modifier.size(21.dp).graphicsLayer {
                        scaleX = lift
                        scaleY = lift
                    }
                )
            }
            Text(
                item.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = fg,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
            )
        }
    }
}

/** Page loading line in YouTube blue. */
@Composable
fun GlowProgress(progress: Float, modifier: Modifier = Modifier) {
    val c = StreamTheme.colors
    Box(modifier.fillMaxWidth().height(2.dp)) {
        Box(
            Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(c.accentFill, c.highlight, c.accent2)), RoundedCornerShape(1.dp))
        )
    }
}

/** Loading placeholder with a slow sheen (static with reduced motion). */
@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(Radius.control)) {
    val c = StreamTheme.colors
    val reduced = LocalReducedMotion.current
    val base = c.surfaceRaised
    val shine = if (c.isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.7f)
    val t = if (reduced) {
        0f
    } else {
        rememberInfiniteTransition(label = "skeleton").animateFloat(
            0f,
            1f,
            infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
            label = "sheen"
        ).value
    }
    Box(
        modifier.clip(shape).background(base).drawBehind {
            val x = size.width * (t * 2f - 0.5f)
            drawRect(Brush.linearGradient(listOf(Color.Transparent, shine, Color.Transparent), start = Offset(x - size.width * 0.3f, 0f), end = Offset(x + size.width * 0.3f, size.height)))
        }
    )
}

/** Quiet ambient light behind content screens (start page, settings): one very soft neutral glow from the top. */
fun Modifier.ambientBackground(colors: StreamColors): Modifier = this
    .background(colors.background)
    .drawBehind {
        val a = if (colors.isDark) 0.10f else 0.05f
        // Neutral light (white on dark, black on light): depth without colour.
        drawRect(Brush.radialGradient(listOf(colors.textPrimary.copy(alpha = a * 0.6f), Color.Transparent), center = Offset(size.width * 0.5f, 0f), radius = size.maxDimension * 0.6f))
    }

/** Screen header for full-screen panels: glass back button, title, optional actions. */
@Composable
fun GlassHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backTag: String = "panel-back",
    backDescription: String = "Back",
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        GlassIconButton(StreamIcons.Back, backDescription, onBack, Modifier.testTag(backTag), iconSize = 18.dp)
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(start = Spacing.xs), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

internal fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)
