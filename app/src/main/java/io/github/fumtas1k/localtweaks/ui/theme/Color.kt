package io.github.fumtas1k.localtweaks.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

// Android 16 (this app's only target) always supports dynamic color, but
// dynamicLightColorScheme / dynamicDarkColorScheme can still fail to resolve
// in environments such as Compose Preview. These static schemes back
// LocalTweaksTheme whenever that happens.
internal val LightFallbackColorScheme: ColorScheme = lightColorScheme()
internal val DarkFallbackColorScheme: ColorScheme = darkColorScheme()
