package com.mustafa.sinavtakvim.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class Student(
    val id: String = "",
    val name: String = "",
    val studentNumber: String = "",
    val department: String = "",
    val enrolledCourseIds: List<String> = emptyList()
)
