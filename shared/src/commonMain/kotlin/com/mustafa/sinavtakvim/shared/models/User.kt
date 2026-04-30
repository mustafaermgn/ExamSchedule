package com.mustafa.sinavtakvim.shared.models

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    ADMIN, PROCTOR
}

@Serializable
data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: UserRole = UserRole.PROCTOR,
    val deptId: String = "",
    val excuses: List<DateRange> = emptyList(),
    val password: String = "123456",
    val profileImageUrl: String = "",
    val phone: String = "",
    val preferences: Map<String, Boolean> = emptyMap()
)

@Serializable
data class DateRange(
    val start: Long = 0L,
    val end: Long = 0L,
    val isApproved: Boolean = false,
    val note: String = ""
)

@Serializable
data class LogEntry(
    val id: String = "",
    val action: String = "",
    val oldValue: String = "",
    val newValue: String = "",
    val editorId: String = "",
    val timestamp: Long = 0L
)
