package com.mustafa.sinavtakvim.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class SlotConfig(
    val slotTimes: List<String> = listOf("09:00", "11:00", "14:00", "16:00"),
    val examStartDate: String = "2026-05-05",
    val examEndDate: String = "2026-05-12",
    val examWeekdays: List<Int> = listOf(1, 2, 3, 4, 5)
)
