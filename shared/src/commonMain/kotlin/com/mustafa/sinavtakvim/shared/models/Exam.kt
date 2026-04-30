package com.mustafa.sinavtakvim.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class Exam(
    val id: String = "",
    val courseId: String = "",
    val date: Long = 0L,
    val slotId: Int = 1,
    val assignments: List<Assignment> = emptyList(),
    val algorithm: String = "",
    val qualityScore: Double = 0.0,
    val examType: String = "VIZE",
    val academicTerm: String = "2026 Bahar"
)

@Serializable
data class Assignment(
    val roomId: String = "",
    val proctorId: String = ""
)
