package com.mustafa.sinavtakvim.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@Composable
actual fun MapView(
    modifier: Modifier,
    lat: Double,
    lng: Double,
    title: String
) {
    val location = LatLng(lat, lng)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(location, 15f)
    }

    LaunchedEffect(lat, lng) {
        cameraPositionState.position = CameraPosition.fromLatLngZoom(location, 15f)
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState
    ) {
        Marker(
            state = MarkerState(position = location),
            title = title
        )
    }
}

@Composable
actual fun LocationPickerMap(
    modifier: Modifier,
    latitude: Double?,
    longitude: Double?,
    onLocationSelected: (latitude: Double, longitude: Double) -> Unit
) {
    val selectedLocation = remember(latitude, longitude) {
        if (latitude != null && longitude != null) LatLng(latitude, longitude) else null
    }
    val defaultLocation = LatLng(DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(selectedLocation ?: defaultLocation, if (selectedLocation == null) 12f else 16f)
    }

    LaunchedEffect(latitude, longitude) {
        if (selectedLocation != null) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(selectedLocation, 16f)
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        onMapClick = { location ->
            onLocationSelected(location.latitude, location.longitude)
        }
    ) {
        if (selectedLocation != null) {
            Marker(
                state = MarkerState(position = selectedLocation),
                title = "Seçilen salon konumu"
            )
        }
    }
}

private const val DEFAULT_LATITUDE = 38.611942
private const val DEFAULT_LONGITUDE = 27.378973
