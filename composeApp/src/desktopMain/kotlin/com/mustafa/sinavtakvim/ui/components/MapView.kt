package com.mustafa.sinavtakvim.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
actual fun MapView(
    modifier: Modifier,
    lat: Double,
    lng: Double,
    title: String
) {
    Box(
        modifier = modifier.background(Color(0xFFE1E7E2)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.h3, color = MaterialTheme.colors.primary)
            Text("$lat, $lng", style = MaterialTheme.typography.body2, color = Color(0xFF65706A))
        }
    }
}
