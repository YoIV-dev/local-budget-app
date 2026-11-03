package com.localbudget.app.ui.theme

import androidx.compose.ui.graphics.Color

val PrimaryGreen = Color(0xFF0F9D58)
val PrimaryGreenDark = Color(0xFF0B8043)
val SecondaryTeal = Color(0xFF009688)
val BackgroundLight = Color(0xFFF8F9FA)
val SurfaceLight = Color(0xFFFFFFFF)
val TextPrimaryLight = Color(0xFF202124)
val TextSecondaryLight = Color(0xFF5F6368)
val CardBorderLight = Color(0xFFE0E0E0)

val BackgroundDark = Color(0xFF121212)
val SurfaceDark = Color(0xFF1E1E1E)
val TextPrimaryDark = Color(0xFFE8EAED)
val TextSecondaryDark = Color(0xFF9AA0A6)
val CardBorderDark = Color(0xFF2D2D2D)

// Fallback category color
fun parseColorHex(hex: String, fallback: Color = PrimaryGreen): Color {
    return try {
        val cleanHex = if (hex.startsWith("#")) hex.substring(1) else hex
        val colorInt = cleanHex.toLong(16)
        if (cleanHex.length == 6) {
            Color(0xFF000000 or colorInt)
        } else if (cleanHex.length == 8) {
            Color(colorInt)
        } else {
            fallback
        }
    } catch (_: Exception) {
        fallback
    }
}
