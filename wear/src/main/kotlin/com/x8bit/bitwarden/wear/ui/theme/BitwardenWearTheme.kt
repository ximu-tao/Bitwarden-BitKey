package com.x8bit.bitwarden.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme

/**
 * Theme wrapper around the Wear OS Material 3 theme.
 *
 * Color values intentionally mirror the phone app's Bitwarden brand palette
 * (see `:ui` module BitwardenTheme) for visual consistency across devices.
 */
@Composable
fun BitwardenWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}