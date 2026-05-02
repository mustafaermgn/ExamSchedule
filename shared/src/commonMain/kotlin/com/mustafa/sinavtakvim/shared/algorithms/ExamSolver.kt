package com.mustafa.sinavtakvim.shared.algorithms

import com.mustafa.sinavtakvim.shared.models.Course
import com.mustafa.sinavtakvim.shared.models.Room
import com.mustafa.sinavtakvim.shared.models.User
import com.mustafa.sinavtakvim.shared.models.Exam

interface ExamSolver {
    fun solve(
        courses: List<Course>,
        rooms: List<Room>,
        proctors: List<User>,
        slotTimes: List<String> = listOf("09:00", "11:00", "14:00", "16:00")
    ): SolverResult
}

data class SolverResult(
    val exams: List<Exam>,
    val executionTimeMs: Long,
    val accuracy: Double,
    val algorithmName: String = "",
    val metrics: SolverMetrics = SolverMetrics(),
    val violations: List<String> = emptyList()
)

data class SolverMetrics(
    val scheduledCourses: Int = 0,
    val unscheduledCourses: Int = 0,
    val totalStudents: Int = 0,
    val assignedCapacity: Int = 0,
    val capacityWaste: Int = 0,
    val semesterConflicts: Int = 0,
    val dailySemesterLimitWarnings: Int = 0,
    val capacityFailures: Int = 0,
    val proctorConflicts: Int = 0,
    val excuseConflicts: Int = 0,
    val consecutiveViolations: Int = 0,
    val proctorLoadImbalance: Int = 0
)
