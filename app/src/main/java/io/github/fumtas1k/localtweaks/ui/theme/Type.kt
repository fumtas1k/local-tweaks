package io.github.fumtas1k.localtweaks.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

// Japanese text needs more line height than the Material 3 baseline to stay
// readable, so every style is widened by the same ratio. Everything else is
// left at the Material 3 default.
private const val LINE_HEIGHT_SCALE = 1.4f

private fun TextStyle.widenedForJapanese(): TextStyle = copy(lineHeight = lineHeight * LINE_HEIGHT_SCALE)

private val baselineTypography = Typography()

internal val LocalTweaksTypography: Typography = baselineTypography.copy(
    displayLarge = baselineTypography.displayLarge.widenedForJapanese(),
    displayMedium = baselineTypography.displayMedium.widenedForJapanese(),
    displaySmall = baselineTypography.displaySmall.widenedForJapanese(),
    headlineLarge = baselineTypography.headlineLarge.widenedForJapanese(),
    headlineMedium = baselineTypography.headlineMedium.widenedForJapanese(),
    headlineSmall = baselineTypography.headlineSmall.widenedForJapanese(),
    titleLarge = baselineTypography.titleLarge.widenedForJapanese(),
    titleMedium = baselineTypography.titleMedium.widenedForJapanese(),
    titleSmall = baselineTypography.titleSmall.widenedForJapanese(),
    bodyLarge = baselineTypography.bodyLarge.widenedForJapanese(),
    bodyMedium = baselineTypography.bodyMedium.widenedForJapanese(),
    bodySmall = baselineTypography.bodySmall.widenedForJapanese(),
    labelLarge = baselineTypography.labelLarge.widenedForJapanese(),
    labelMedium = baselineTypography.labelMedium.widenedForJapanese(),
    labelSmall = baselineTypography.labelSmall.widenedForJapanese(),
)
