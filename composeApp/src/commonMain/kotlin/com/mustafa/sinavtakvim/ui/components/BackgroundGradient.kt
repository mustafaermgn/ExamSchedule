package com.mustafa.sinavtakvim.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun BackgroundGradient(content: @Composable () -> Unit) {
    val gradientBrush = Brush.verticalGradient(
        colors = if (MaterialTheme.colors.isLight) {
            listOf(Color(0xFFE8ECEF), Color(0xFFF2F4F6))
        } else {
            listOf(Color(0xFF0F151A), Color(0xFF161E24))
        }
    )
    Box(modifier = Modifier.fillMaxSize().background(gradientBrush)) {
        content()
    }
}
