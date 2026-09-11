package com.x8bit.bitwarden.wear.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Bitwarden brand palette used by the Wear OS variant.
 *
 * Values mirror the phone app's `PrimitiveColors` (see `:ui` module
 * `com.bitwarden.ui.platform.theme.color`) so brand colors stay consistent
 * across devices. Wear apps are typically consumed in dark mode, so the
 * [darkWearColorScheme] is the primary scheme.
 */
internal object BitwardenWearPrimitives {
    val gray100: Color = Color(color = 0xFFFFFFFF)
    val gray200: Color = Color(color = 0xFFF3F6F9)
    val gray300: Color = Color(color = 0xFFE6E9EF)
    val gray500: Color = Color(color = 0xFF96A3BB)
    val gray900: Color = Color(color = 0xFF657185)
    val gray1000: Color = Color(color = 0xFF303946)
    val gray1100: Color = Color(color = 0xFF202733)
    val gray1200: Color = Color(color = 0xFF121A27)
    val gray1400: Color = Color(color = 0xFF000000)

    val blue400: Color = Color(color = 0xFF65ABFF)
    val blue500: Color = Color(color = 0xFF175DDC)
    val blue700: Color = Color(color = 0xFF020F66)

    val green200: Color = Color(color = 0xFF6BF178)
    val red200: Color = Color(color = 0xFFFF4E63)
    val yellow200: Color = Color(color = 0xFFFFBF00)
}