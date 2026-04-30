package com.mustafa.sinavtakvim.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class Room(
    val id: String = "",
    val name: String = "",
    val capacity: Int = 0,
    val floor: Int = 0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val building: String = "Mühendislik Fakültesi",
    val facilities: List<String> = emptyList()
)
