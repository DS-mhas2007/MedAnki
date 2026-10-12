package com.example.ui.theme

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
    primary = MedDarkPrimary,
    onPrimary = MedDarkOnPrimary,
    primaryContainer = MedDarkPrimaryContainer,
    onPrimaryContainer = MedDarkOnPrimaryContainer,
    secondary = MedDarkSecondary,
    onSecondary = MedDarkOnSecondary,
    secondaryContainer = MedDarkSecondaryContainer,
    onSecondaryContainer = MedDarkOnSecondaryContainer,
    tertiary = MedDarkTertiary,
    background = MedDarkBackground,
    surface = MedDarkSurface,
    surfaceVariant = MedDarkSurfaceVariant,
    onSurface = MedDarkOnSurface,
    onSurfaceVariant = MedDarkOnSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = MedLightPrimary,
    onPrimary = MedLightOnPrimary,
    primaryContainer = MedLightPrimaryContainer,
    onPrimaryContainer = MedLightOnPrimaryContainer,
    secondary = MedLightSecondary,
    onSecondary = MedLightOnSecondary,
    secondaryContainer = MedLightSecondaryContainer,
    onSecondaryContainer = MedLightOnSecondaryContainer,
    tertiary = MedLightTertiary,
    background = MedLightBackground,
    surface = MedLightSurface,
    surfaceVariant = MedLightSurfaceVariant,
    onSurface = MedLightOnSurface,
    onSurfaceVariant = MedLightOnSurfaceVariant
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our medical color scheme for consistency
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
