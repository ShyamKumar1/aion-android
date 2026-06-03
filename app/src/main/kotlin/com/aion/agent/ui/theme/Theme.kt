package com.aion.agent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = AionPrimary,
    onPrimary = AionBg,
    primaryContainer = AionPrimary,
    onPrimaryContainer = AionBg,
    secondary = AionSecondary,
    onSecondary = AionBg,
    tertiary = AionAccent,
    onTertiary = AionBg,
    background = AionBg,
    onBackground = AionOnSurface,
    surface = AionSurface,
    onSurface = AionOnSurface,
    surfaceVariant = AionSurfaceVariant,
    onSurfaceVariant = AionOnSurfaceVariant,
    outline = AionOutline,
    error = AionError,
    onError = AionBg,
)

private val LightColors = lightColorScheme(
    primary = AionPrimary,
    onPrimary = AionBgLight,
    secondary = AionSecondary,
    onSecondary = AionBgLight,
    tertiary = AionAccent,
    onTertiary = AionBgLight,
    background = AionBgLight,
    onBackground = AionOnSurfaceLight,
    surface = AionSurfaceLight,
    onSurface = AionOnSurfaceLight,
    surfaceVariant = AionSurfaceVariantLight,
    onSurfaceVariant = AionOnSurfaceVariantLight,
    outline = AionOutline,
    error = AionError,
    onError = AionBgLight,
)

/**
 * AION theme entry point. Dark is the default — AION is a focused, low-light app.
 *
 * @param darkTheme override the system theme. Defaults to [isSystemInDarkTheme].
 * @param content the Compose tree to render inside this theme.
 */
@Composable
fun AionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = AionTypography,
        content = content,
    )
}
