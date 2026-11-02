package com.streamshell.designsystem

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.compositionLocalOf

/**
 * Restrained motion. All durations are short; every spec collapses to `snap()` when the user
 * disabled animations (Settings > Accessibility > Remove animations => animator scale 0).
 * Motion is purely visual: nothing here delays navigation, loads, PiP or the WebView.
 */
object Motion {
    private val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val QUICK_MS = 120
    const val SHORT_MS = 180
    const val THEME_MS = 260

    fun <T> spec(reduced: Boolean, ms: Int = SHORT_MS): FiniteAnimationSpec<T> = if (reduced) snap() else tween(ms, easing = Standard)

    /** True when the system animator scale is 0 (reduced-motion / remove-animations). */
    fun isReduced(context: Context): Boolean = try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (_: Exception) {
        false
    }
}

val LocalReducedMotion = compositionLocalOf { false }
