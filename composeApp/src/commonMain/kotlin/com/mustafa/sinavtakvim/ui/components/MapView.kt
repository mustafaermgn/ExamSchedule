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

@Composable
expect fun LocationPickerMap(
    modifier: Modifier,
    latitude: Double?,
    longitude: Double?,
    onLocationSelected: (latitude: Double, longitude: Double) -> Unit
)
