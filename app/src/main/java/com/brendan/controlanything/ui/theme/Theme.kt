package com.brendan.controlanything.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Tangerine,
    onPrimary = Navy,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = Navy,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = LightSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = Navy,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = Navy,
    onBackground = FloralWhite,
    surface = Navy,
    onSurface = FloralWhite,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = StormyTeal,
    surfaceTint = Tangerine,
    inverseSurface = FloralWhite,
    inverseOnSurface = Navy,
    inversePrimary = Tangerine
)

private val LightColorScheme = lightColorScheme(
    primary = PrussianBlue,
    onPrimary = FloralWhite,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = Tangerine,
    onSecondary = FloralWhite,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = Navy,
    tertiary = StormyTeal,
    onTertiary = FloralWhite,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = Navy,
    background = FloralWhite,
    onBackground = Navy,
    surface = FloralWhite,
    onSurface = Navy,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    surfaceTint = Tangerine,
    inverseSurface = Navy,
    inverseOnSurface = FloralWhite,
    inversePrimary = LightInversePrimary
)

@Composable
fun ControlAnythingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+, but disabled by default so the
    // app's brand palette is used consistently instead of the wallpaper-derived one.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
