package com.stoni.androidstone.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = StonePrimary,
    onPrimary = StoneOnPrimary,
    primaryContainer = StonePrimaryContainer,
    secondary = StoneSecondary,
    background = StoneBackground,
    onBackground = StoneOnBackground,
    surface = StoneSurface,
    onSurface = StoneOnSurface,
    onSurfaceVariant = StoneOnSurfaceVariant
)

private val DarkColorScheme = darkColorScheme(
    primary = StonePrimaryDark,
    onPrimary = StoneOnPrimaryDark,
    primaryContainer = StonePrimaryContainerDark,
    secondary = StoneSecondaryDark,
    background = StoneBackgroundDark,
    onBackground = StoneOnBackgroundDark,
    surface = StoneSurfaceDark,
    onSurface = StoneOnSurfaceDark,
    onSurfaceVariant = StoneOnSurfaceVariantDark
)

@Composable
fun AndroidStoneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
