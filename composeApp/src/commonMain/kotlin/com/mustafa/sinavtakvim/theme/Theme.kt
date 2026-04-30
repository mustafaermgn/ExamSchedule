package com.mustafa.sinavtakvim.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorPalette = darkColors(
    primary = Color(0xFF17443D),
    primaryVariant = Color(0xFF0F302B),
    secondary = Color(0xFFB88343),
    background = Color(0xFF101417),
    surface = Color(0xFF191F23),
    onPrimary = Color.White,
    onSecondary = Color(0xFF1C1206),
    onBackground = Color(0xFFE8ECE7),
    onSurface = Color(0xFFE8ECE7),
)

private val LightColorPalette = lightColors(
    primary = Color(0xFF17443D),
    primaryVariant = Color(0xFF0F302B),
    secondary = Color(0xFFB88343),
    background = Color(0xFFF2F4F6),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color(0xFF1F1609),
    onBackground = Color(0xFF172026),
    onSurface = Color(0xFF172026),
)

@Composable
fun AppTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    MaterialTheme(
        colors = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
