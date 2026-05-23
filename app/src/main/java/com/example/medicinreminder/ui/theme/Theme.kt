package com.example.medicinreminder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CareTealBrightDark,
    onPrimary = CareBackgroundDark,
    primaryContainer = CareTeal40,
    onPrimaryContainer = CareMintBrightDark,
    secondary = CareBlueBrightDark,
    onSecondary = CareBackgroundDark,
    secondaryContainer = CareBlue40,
    onSecondaryContainer = CareBlue80,
    tertiary = CareMintBrightDark,
    onTertiary = CareBackgroundDark,
    tertiaryContainer = CareMint40,
    onTertiaryContainer = CareMint80,
    background = CareBackgroundDark,
    onBackground = CareOnSurfaceDark,
    surface = CareSurfaceDark,
    onSurface = CareOnSurfaceDark,
    surfaceVariant = CareSurfaceVariantDark,
    onSurfaceVariant = CareOnSurfaceDark,
    outline = CareTealBrightDark,
    inversePrimary = CareTeal80,
    inverseSurface = CareSurfaceLight,
    inverseOnSurface = CareOnSurfaceLight
)

private val LightColorScheme = lightColorScheme(
    primary = CareTeal40,
    onPrimary = CareBackgroundLight,
    primaryContainer = CareTeal80,
    onPrimaryContainer = CareBackgroundDark,
    secondary = CareBlue40,
    onSecondary = CareBackgroundLight,
    secondaryContainer = CareBlue80,
    onSecondaryContainer = CareBackgroundDark,
    tertiary = CareMint40,
    onTertiary = CareBackgroundLight,
    tertiaryContainer = CareMint80,
    onTertiaryContainer = CareBackgroundDark,
    background = CareBackgroundLight,
    onBackground = CareOnSurfaceLight,
    surface = CareSurfaceLight,
    onSurface = CareOnSurfaceLight,
    surfaceVariant = CareSurfaceVariantLight,
    onSurfaceVariant = CareOnSurfaceLight,
    outline = CareTeal40
)

@Composable
fun MedicinReminderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}