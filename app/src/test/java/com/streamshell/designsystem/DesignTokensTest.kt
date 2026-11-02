package com.streamshell.designsystem

import androidx.compose.animation.core.SnapSpec
import androidx.compose.ui.graphics.Color
import com.streamshell.core.model.ThemeMode
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignTokensTest {
    private fun lin(c: Float) = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun lum(c: Color) = 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    private fun ratio(a: Color, b: Color): Double {
        val (x, y) = lum(a).let { la -> lum(b).let { lb -> if (la >= lb) la to lb else lb to la } }
        return (x + 0.05) / (y + 0.05)
    }

    private val palettes = mapOf("light" to StreamColors.Light, "dark" to StreamColors.Dark)

    @Test fun bodyTextMeetsWcagAaOnEverySurface() {
        palettes.forEach { (n, c) ->
            listOf(c.background, c.surface, c.surfaceRaised).forEach { bg ->
                assertTrue("$n primary on $bg", ratio(c.textPrimary, bg) >= 4.5)
                assertTrue("$n secondary on $bg", ratio(c.textSecondary, bg) >= 4.5)
            }
        }
    }

    @Test fun accentIsReadableAsTextAndAsFill() {
        palettes.forEach { (n, c) ->
            listOf(c.background, c.surface, c.surfaceRaised).forEach { bg -> assertTrue("$n accent text on $bg", ratio(c.accent, bg) >= 4.5) }
            assertTrue("$n label on accent fill", ratio(c.onAccent, c.accentFill) >= 4.5)
            assertTrue("$n cyan accent on background", ratio(c.accent2, c.background) >= 4.5)
            assertTrue("$n label on accent container", ratio(c.onAccentContainer, c.accentContainer) >= 4.5)
            assertTrue("$n label on danger container", ratio(c.onDangerContainer, c.dangerContainer) >= 4.5)
            assertTrue("$n danger text on background", ratio(c.danger, c.background) >= 4.5)
        }
    }

    @Test fun controlOutlinesMeetNonTextContrast() {
        palettes.forEach { (n, c) -> assertTrue("$n outline", ratio(c.outline, c.background) >= 3.0) }
    }

    @Test fun lightAndDarkShareOneAccentIdentity() {
        // One accent family (YouTube blue) in both themes; neutrals are true greys (black/white contrast).
        palettes.values.forEach { c ->
            listOf(c.accent, c.accentFill, c.accent2).forEach { a -> assertTrue(a.blue > a.green && a.green > a.red) }
            listOf(c.background, c.surface, c.surfaceRaised, c.textPrimary, c.textSecondary).forEach { n -> assertTrue(n.red == n.green && n.green == n.blue) }
        }
    }

    @Test fun glassStaysReadable() {
        // Strong glass (bars over web pages) must be nearly opaque; ordinary glass clearly translucent.
        palettes.forEach { (n, c) ->
            assertTrue("$n strong glass", c.glassStrong.alpha >= 0.9f)
            assertTrue("$n glass translucent", c.glass.alpha in 0.6f..0.85f)
        }
        assertEquals(StreamColors.Dark.background, androidx.compose.ui.graphics.Color(0xFF0F0F0F))
        assertEquals(StreamColors.Light.background, androidx.compose.ui.graphics.Color(0xFFFFFFFF))
    }

    @Test fun darkAndLightAreActuallyDifferentSurfaces() {
        assertTrue(lum(StreamColors.Dark.background) < 0.05)
        assertTrue(lum(StreamColors.Light.background) > 0.9)
        assertTrue(StreamColors.Dark.isDark && !StreamColors.Light.isDark)
    }

    @Test fun modeResolution() {
        assertTrue(resolveDark(ThemeMode.SYSTEM, true))
        assertFalse(resolveDark(ThemeMode.SYSTEM, false))
        // Manual modes override the system setting.
        assertFalse(resolveDark(ThemeMode.LIGHT, true))
        assertTrue(resolveDark(ThemeMode.DARK, false))
    }

    @Test fun reducedMotionCollapsesToInstantChange() {
        assertTrue(Motion.spec<Float>(reduced = true) is SnapSpec)
        assertFalse(Motion.spec<Float>(reduced = false) is SnapSpec)
        assertTrue(Motion.THEME_MS <= 300 && Motion.QUICK_MS <= Motion.SHORT_MS)
    }

    @Test fun typographyIsRoleBasedAndScalable() {
        val t = StreamTypography
        // sp units (font scaling), medium-to-semibold for titles, regular for descriptions, bold only for screen headings.
        assertEquals(androidx.compose.ui.text.font.FontWeight.SemiBold, t.titleSmall.fontWeight)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Normal, t.bodyMedium.fontWeight)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, t.headlineSmall.fontWeight)
        assertTrue(t.bodySmall.fontSize.value < t.bodyMedium.fontSize.value)
        assertTrue(t.bodyMedium.lineHeight.value / t.bodyMedium.fontSize.value >= 1.4f)
        assertEquals(androidx.compose.ui.unit.TextUnitType.Sp, t.bodyLarge.fontSize.type)
    }
}
