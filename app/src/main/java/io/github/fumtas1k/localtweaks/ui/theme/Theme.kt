package io.github.fumtas1k.localtweaks.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun LocalTweaksTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme: ColorScheme = remember(darkTheme, context) {
        runCatching {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }.getOrElse {
            if (darkTheme) DarkFallbackColorScheme else LightFallbackColorScheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = LocalTweaksTypography,
        content = content,
    )
}
