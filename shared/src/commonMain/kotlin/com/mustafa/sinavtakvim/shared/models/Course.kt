package com.mustafa.sinavtakvim.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class Course(
    val id: String = "",
    val code: String = "",
    val name: String = "",
    val studentCount: Int = 0,
    val semester: Int = 1,
    val departmentId: String = "BIL",
    val instructorName: String = ""
)
