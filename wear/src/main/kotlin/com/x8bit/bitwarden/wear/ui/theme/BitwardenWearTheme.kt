package com.x8bit.bitwarden.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/**
 * Theme wrapper around the Wear OS Material 3 theme.
 *
 * Color values intentionally mirror the phone app's Bitwarden brand palette
 * (see `:ui` module BitwardenTheme) for visual consistency across devices.
 * Wear OS apps are primarily used in dark mode, matching the default
 * [darkWearColorScheme].
 */
@Composable
fun BitwardenWearTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkWearColorScheme else lightWearColorScheme,
        content = content,
    )
}

/**
 * Dark color scheme derived from the Bitwarden brand palette.
 */
private val darkWearColorScheme = ColorScheme(
    primary = BitwardenWearPrimitives.blue400,
    primaryDim = BitwardenWearPrimitives.blue500,
    primaryContainer = BitwardenWearPrimitives.blue700,
    onPrimary = BitwardenWearPrimitives.gray1100,
    onPrimaryContainer = BitwardenWearPrimitives.gray200,
    secondary = BitwardenWearPrimitives.gray500,
    secondaryDim = BitwardenWearPrimitives.gray900,
    secondaryContainer = BitwardenWearPrimitives.gray1000,
    onSecondary = BitwardenWearPrimitives.gray1100,
    onSecondaryContainer = BitwardenWearPrimitives.gray200,
    tertiary = BitwardenWearPrimitives.green200,
    tertiaryDim = BitwardenWearPrimitives.yellow200,
    tertiaryContainer = BitwardenWearPrimitives.gray1000,
    onTertiary = BitwardenWearPrimitives.gray1400,
    onTertiaryContainer = BitwardenWearPrimitives.gray200,
    surfaceContainerLow = BitwardenWearPrimitives.gray1200,
    surfaceContainer = BitwardenWearPrimitives.gray1100,
    surfaceContainerHigh = BitwardenWearPrimitives.gray1000,
    onSurface = BitwardenWearPrimitives.gray200,
    onSurfaceVariant = BitwardenWearPrimitives.gray500,
    outline = BitwardenWearPrimitives.gray900,
    outlineVariant = BitwardenWearPrimitives.gray1000,
    background = BitwardenWearPrimitives.gray1200,
    onBackground = BitwardenWearPrimitives.gray200,
    error = BitwardenWearPrimitives.red200,
    errorDim = BitwardenWearPrimitives.red200,
    errorContainer = BitwardenWearPrimitives.gray1000,
    onError = BitwardenWearPrimitives.gray1400,
    onErrorContainer = BitwardenWearPrimitives.gray200,
)

/**
 * Light color scheme derived from the Bitwarden brand palette.
 */
private val lightWearColorScheme = ColorScheme(
    primary = BitwardenWearPrimitives.blue500,
    primaryDim = BitwardenWearPrimitives.blue400,
    primaryContainer = BitwardenWearPrimitives.blue700,
    onPrimary = BitwardenWearPrimitives.gray100,
    onPrimaryContainer = BitwardenWearPrimitives.gray200,
    secondary = BitwardenWearPrimitives.gray500,
    secondaryDim = BitwardenWearPrimitives.gray900,
    secondaryContainer = BitwardenWearPrimitives.gray300,
    onSecondary = BitwardenWearPrimitives.gray100,
    onSecondaryContainer = BitwardenWearPrimitives.gray1400,
    tertiary = BitwardenWearPrimitives.green200,
    tertiaryDim = BitwardenWearPrimitives.yellow200,
    tertiaryContainer = BitwardenWearPrimitives.gray300,
    onTertiary = BitwardenWearPrimitives.gray1400,
    onTertiaryContainer = BitwardenWearPrimitives.gray1400,
    surfaceContainerLow = BitwardenWearPrimitives.gray200,
    surfaceContainer = BitwardenWearPrimitives.gray100,
    surfaceContainerHigh = BitwardenWearPrimitives.gray300,
    onSurface = BitwardenWearPrimitives.gray1400,
    onSurfaceVariant = BitwardenWearPrimitives.gray900,
    outline = BitwardenWearPrimitives.gray500,
    outlineVariant = BitwardenWearPrimitives.gray300,
    background = BitwardenWearPrimitives.gray200,
    onBackground = BitwardenWearPrimitives.gray1400,
    error = BitwardenWearPrimitives.red200,
    errorDim = BitwardenWearPrimitives.red200,
    errorContainer = BitwardenWearPrimitives.gray300,
    onError = BitwardenWearPrimitives.gray100,
    onErrorContainer = BitwardenWearPrimitives.gray1400,
)