package com.example.focus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val GrayscaleDarkColorScheme = darkColorScheme(
    primary = GrayPrimaryDark,
    onPrimary = GrayOnPrimaryDark,
    primaryContainer = GrayPrimaryContainerDark,
    onPrimaryContainer = GrayOnPrimaryContainerDark,
    secondary = GraySecondaryDark,
    onSecondary = GrayOnSecondaryDark,
    secondaryContainer = GraySecondaryContainerDark,
    onSecondaryContainer = GrayOnSecondaryContainerDark,
    tertiary = GrayTertiaryDark,
    onTertiary = GrayOnTertiaryDark,
    tertiaryContainer = GrayTertiaryContainerDark,
    onTertiaryContainer = GrayOnTertiaryContainerDark,
    background = GrayBackgroundDark,
    surface = GraySurfaceDark,
    surfaceVariant = GraySurfaceVariantDark,
    onSurface = GrayOnSurfaceDark,
    onSurfaceVariant = GrayOnSurfaceVariantDark,
    outline = GrayOutlineDark
)

private val GrayscaleLightColorScheme = lightColorScheme(
    primary = GrayPrimaryLight,
    onPrimary = GrayOnPrimaryLight,
    primaryContainer = GrayPrimaryContainerLight,
    onPrimaryContainer = GrayOnPrimaryContainerLight,
    secondary = GraySecondaryLight,
    onSecondary = GrayOnSecondaryLight,
    secondaryContainer = GraySecondaryContainerLight,
    onSecondaryContainer = GrayOnSecondaryContainerLight,
    tertiary = GrayTertiaryLight,
    onTertiary = GrayOnTertiaryLight,
    tertiaryContainer = GrayTertiaryContainerLight,
    onTertiaryContainer = GrayOnTertiaryContainerLight,
    background = GrayBackgroundLight,
    surface = GraySurfaceLight,
    surfaceVariant = GraySurfaceVariantLight,
    onSurface = GrayOnSurfaceLight,
    onSurfaceVariant = GrayOnSurfaceVariantLight,
    outline = GrayOutlineLight
)

@Composable
fun FocusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Pure Grayscale by default
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) GrayscaleDarkColorScheme else GrayscaleLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
