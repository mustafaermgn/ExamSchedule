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
    Box(modifier = Modifier.fillMaxSize().background(CorporateColors.Background)) {
        content()
    }
}
