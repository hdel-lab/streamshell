package com.streamshell.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp
import com.streamshell.R

/**
 * Nunito (SIL OFL 1.1, see docs/FONT-LICENSE-Nunito-OFL.txt): rounded terminals with open, readable
 * letterforms. Four static weights (Regular/Medium/SemiBold/Bold) were instanced from the upstream
 * variable font so they render identically on every Android version, with no dependence on variable-font
 * support. If a font cannot be loaded Compose falls back to the platform sans-serif.
 */
val StreamFontFamily: FontFamily = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_medium, FontWeight.Medium),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold)
)

/**
 * Role-based scale. Sizes are in sp so Android font scaling works. Line heights keep generous
 * leading; nothing here is condensed. Bold is reserved for screen titles.
 *
 *  screen title    -> headlineSmall   (Bold)
 *  section heading -> titleMedium     (SemiBold)
 *  video title     -> titleSmall      (SemiBold)  – use for any content title
 *  navigation      -> labelSmall      (Medium)
 *  button          -> labelLarge      (SemiBold)
 *  metadata        -> bodySmall       (Regular, secondary colour)
 *  search field    -> bodyLarge
 *  description     -> bodyMedium
 *  dialog/error    -> bodyMedium / titleMedium
 */
internal val StreamTypography: Typography = run {
    fun s(size: Int, line: Int, w: FontWeight, spacing: Double = 0.0) = TextStyle(
        fontFamily = StreamFontFamily,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = w,
        letterSpacing = spacing.sp,
        lineBreak = LineBreak.Paragraph
    )
    Typography(
        displaySmall = s(32, 40, FontWeight.Bold),
        headlineMedium = s(26, 34, FontWeight.Bold),
        headlineSmall = s(22, 30, FontWeight.Bold),
        titleLarge = s(20, 28, FontWeight.SemiBold),
        titleMedium = s(16, 24, FontWeight.SemiBold, 0.1),
        titleSmall = s(15, 22, FontWeight.SemiBold, 0.1),
        bodyLarge = s(16, 24, FontWeight.Normal, 0.15),
        bodyMedium = s(14, 21, FontWeight.Normal, 0.15),
        bodySmall = s(12, 17, FontWeight.Normal, 0.2),
        labelLarge = s(14, 20, FontWeight.SemiBold, 0.1),
        labelMedium = s(12, 16, FontWeight.Medium, 0.3),
        labelSmall = s(11, 14, FontWeight.Medium, 0.3)
    )
}
