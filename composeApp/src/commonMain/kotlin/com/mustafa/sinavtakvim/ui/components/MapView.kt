package com.mustafa.sinavtakvim.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun MapView(
    modifier: Modifier,
    lat: Double,
    lng: Double,
    title: String
)
