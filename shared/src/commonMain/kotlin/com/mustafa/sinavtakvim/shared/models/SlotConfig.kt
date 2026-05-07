package com.mustafa.sinavtakvim.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class SlotConfig(
    val slotTimes: List<String> = listOf("09:00", "11:00", "14:00", "16:00")
)
