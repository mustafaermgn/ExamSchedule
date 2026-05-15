package com.mustafa.sinavtakvim.shared.algorithms

import com.mustafa.sinavtakvim.shared.models.Assignment
import com.mustafa.sinavtakvim.shared.models.Course
import com.mustafa.sinavtakvim.shared.models.Exam
import com.mustafa.sinavtakvim.shared.models.Room
import com.mustafa.sinavtakvim.shared.models.User
import com.mustafa.sinavtakvim.shared.models.UserRole

internal object SchedulingEngine {
    private const val firstExamDay = 1_777_960_800_000L // 5 May 2026, 09:00 Europe/Istanbul
    private const val dayMillis = 86_400_000L

    fun solve(
        algorithmName: String,
        courses: List<Course>,
        rooms: List<Room>,
        proctors: List<User>,
        slotTimes: List<String>,
        examDays: List<Long>,
        selectRooms: (target: Int, rooms: List<Room>) -> List<Room>
    ): SolverResult {
        val normalizedSlotTimes = normalizeSlotTimes(slotTimes)
        val slotOffsets = normalizedSlotTimes.map { parseOffsetMs(it) }
        val slotsPerDay = slotOffsets.size
        val allowedExamDays = examDays.distinct().sorted()
        val maxSlot = if (allowedExamDays.isEmpty()) {
            (courses.size * 3).coerceAtLeast(16)
        } else {
            allowedExamDays.size * slotsPerDay
        }
        val cleanCourses = courses.filter { it.id.isNotBlank() && it.studentCount > 0 }
        val cleanRooms = rooms.filter { it.id.isNotBlank() && it.capacity > 0 }
        val cleanProctors = proctors.filter { it.role == UserRole.PROCTOR && it.uid.isNotBlank() }
        val sortedCourses = if (algorithmName.startsWith("Sezgisel")) {
            cleanCourses.sortedByDescending { it.studentCount }
        } else {
            cleanCourses.sortedWith(compareBy<Course> { it.semester }.thenByDescending { it.studentCount })
        }

        val slotSemesters = mutableMapOf<Int, MutableSet<Int>>()
        val slotRooms = mutableMapOf<Int, MutableSet<String>>()
        val slotProctors = mutableMapOf<Int, MutableSet<String>>()
        val exams = mutableListOf<Exam>()
        val violations = mutableListOf<String>()
        var totalCapacity = 0
        var unscheduled = 0

        sortedCourses.forEach { course ->
            val plan = findPlan(
                course = course,
                rooms = cleanRooms,
                proctors = cleanProctors,
                slotSemesters = slotSemesters,
                slotRooms = slotRooms,
                slotProctors = slotProctors,
                selectRooms = selectRooms,
                slotOffsets = slotOffsets,
                examDays = allowedExamDays,
                maxSlot = maxSlot
            )

            if (plan == null) {
                unscheduled += 1
                violations += "${course.code} için kapasite, dönem veya gözetmen kısıtlarını sağlayan oturum bulunamadı."
                return@forEach
            }

            slotSemesters.getOrPut(plan.slotIndex) { mutableSetOf() }.add(course.semester)
            slotRooms.getOrPut(plan.slotIndex) { mutableSetOf() }.addAll(plan.rooms.map { it.id })
            slotProctors.getOrPut(plan.slotIndex) { mutableSetOf() }.addAll(plan.proctors.map { it.uid })
            totalCapacity += plan.rooms.sumOf { it.capacity }

            exams += Exam(
                id = "${algorithmName.lowercase().replace(" ", "_")}_${course.id}_${plan.slotIndex}",
                courseId = course.id,
                date = slotStart(plan.slotIndex, slotOffsets, allowedExamDays),
                slotId = (plan.slotIndex % slotsPerDay) + 1,
                slotLabel = normalizedSlotTimes[plan.slotIndex % slotsPerDay],
                assignments = plan.rooms.zip(plan.proctors).map { (room, proctor) ->
                    Assignment(roomId = room.id, proctorId = proctor.uid)
                },
                algorithm = algorithmName,
                qualityScore = scoreCapacity(course.studentCount, plan.rooms.sumOf { it.capacity })
            )
        }

        val metrics = validate(cleanCourses, cleanRooms, cleanProctors, exams, unscheduled, totalCapacity)
        val penalty = metrics.unscheduledCourses +
            metrics.semesterConflicts +
            metrics.dailySemesterLimitWarnings +
            metrics.roomConflicts +
            metrics.capacityFailures +
            metrics.proctorConflicts +
            metrics.excuseConflicts +
            metrics.consecutiveViolations +
            metrics.proctorLoadImbalance
        val possibleChecks = (cleanCourses.size * 5).coerceAtLeast(1)
        val accuracy = ((possibleChecks - penalty).toDouble() / possibleChecks).coerceIn(0.0, 1.0)

        return SolverResult(
            exams = exams.sortedWith(compareBy<Exam> { it.date }.thenBy { it.slotId }),
            executionTimeMs = 0L,
            accuracy = accuracy,
            algorithmName = algorithmName,
            metrics = metrics,
            violations = violations
        )
    }

    fun slotStart(slotIndex: Int, slotOffsets: List<Long>): Long {
        return slotStart(slotIndex, slotOffsets, emptyList())
    }

    private fun slotStart(slotIndex: Int, slotOffsets: List<Long>, examDays: List<Long>): Long {
        val day = slotIndex / slotOffsets.size
        val slot = slotIndex % slotOffsets.size
        val dayStart = examDays.getOrNull(day) ?: firstExamDay + day * dayMillis
        return dayStart + slotOffsets[slot]
    }

    private fun findPlan(
        course: Course,
        rooms: List<Room>,
        proctors: List<User>,
        slotSemesters: Map<Int, Set<Int>>,
        slotRooms: Map<Int, Set<String>>,
        slotProctors: Map<Int, Set<String>>,
        selectRooms: (target: Int, rooms: List<Room>) -> List<Room>,
        slotOffsets: List<Long>,
        examDays: List<Long>,
        maxSlot: Int
    ): SlotPlan? {
        for (slot in 0 until maxSlot) {
            if (slotSemesters[slot]?.contains(course.semester) == true) continue

            val freeRooms = rooms.filterNot { room -> slotRooms[slot]?.contains(room.id) == true }
            val selectedRooms = selectBestRooms(course.studentCount, freeRooms, selectRooms)
            if (selectedRooms.sumOf { it.capacity } < course.studentCount) continue

            val selectedProctors = mutableListOf<User>()
            val proctorLoad = slotProctors.values.flatten().groupingBy { it }.eachCount()
            for (room in selectedRooms) {
                val proctor = proctors
                    .asSequence()
                    .filter { candidate ->
                            candidate.uid !in selectedProctors.map { it.uid } &&
                                slotProctors[slot]?.contains(candidate.uid) != true &&
                                isAvailable(candidate, slot, slotProctors, slotOffsets, examDays)
                    }
                    .sortedWith(
                        compareBy<User>(
                            { proctorLoad[it.uid] ?: 0 },
                            { proctorPoolPriority(it, course.departmentId) },
                            { it.name }
                        )
                    )
                    .firstOrNull()
                if (proctor != null) selectedProctors += proctor
            }
            if (selectedProctors.size == selectedRooms.size) return SlotPlan(slot, selectedRooms, selectedProctors)
        }
        return null
    }

    private fun selectBestRooms(
        target: Int,
        freeRooms: List<Room>,
        selectRooms: (target: Int, rooms: List<Room>) -> List<Room>
    ): List<Room> {
        if (freeRooms.isEmpty()) return emptyList()
        val byFloor = freeRooms.groupBy { it.floor }
        val perFloor = byFloor.values
            .map { candidateFloorRooms -> selectRooms(target, candidateFloorRooms) }
            .filter { it.sumOf { room -> room.capacity } >= target }
            .minWithOrNull(compareBy<List<Room>>(
                { it.sumOf { room -> room.capacity } - target },
                { it.size }
            ))
        if (perFloor != null) return perFloor

        return selectRooms(target, freeRooms)
    }

    private fun isAvailable(
        proctor: User,
        slot: Int,
        slotProctors: Map<Int, Set<String>>,
        slotOffsets: List<Long>,
        examDays: List<Long>
    ): Boolean {
        val start = slotStart(slot, slotOffsets, examDays)
        val hasExcuse = proctor.excuses.any { start in it.start..it.end }
        if (hasExcuse) return false

        val previousThree = (1..3).all { offset ->
            val previousSlot = slot - offset
            previousSlot >= (slot / slotOffsets.size) * slotOffsets.size &&
                slotProctors[previousSlot]?.contains(proctor.uid) == true
        }
        return !previousThree
    }

    private fun scoreCapacity(studentCount: Int, capacity: Int): Double {
        if (studentCount <= 0 || capacity < studentCount) return 0.0
        val wasteRatio = (capacity - studentCount).toDouble() / capacity.coerceAtLeast(1)
        return (1.0 - wasteRatio).coerceIn(0.0, 1.0)
    }

    private fun validate(
        courses: List<Course>,
        rooms: List<Room>,
        proctors: List<User>,
        exams: List<Exam>,
        unscheduled: Int,
        totalCapacity: Int
    ): SolverMetrics {
        val coursesById = courses.associateBy { it.id }
        val roomsById = rooms.associateBy { it.id }
        val proctorsById = proctors.associateBy { it.uid }
        var semesterConflicts = 0
        var dailySemesterLimitWarnings = 0
        var roomConflicts = 0
        var capacityFailures = 0
        var proctorConflicts = 0
        var excuseConflicts = 0
        var consecutiveViolations = 0

        exams.groupBy { it.date to it.slotId }.values.forEach { sameSlot ->
            val semesters = sameSlot.mapNotNull { coursesById[it.courseId]?.semester }
            semesterConflicts += semesters.size - semesters.toSet().size

            val roomsInSlot = sameSlot.flatMap { it.assignments.map { assignment -> assignment.roomId } }
            roomConflicts += roomsInSlot.size - roomsInSlot.toSet().size

            val proctorsInSlot = sameSlot.flatMap { it.assignments.map { assignment -> assignment.proctorId } }
            proctorConflicts += proctorsInSlot.size - proctorsInSlot.toSet().size
        }

        exams.forEach { exam ->
            val course = coursesById[exam.courseId]
            val capacity = exam.assignments.sumOf { roomsById[it.roomId]?.capacity ?: 0 }
            if (course != null && capacity < course.studentCount) capacityFailures += 1

            exam.assignments.forEach { assignment ->
                val proctor = proctorsById[assignment.proctorId]
                if (proctor != null && proctor.excuses.any { exam.date in it.start..it.end }) {
                    excuseConflicts += 1
                }
            }
        }

        val byDayAndSemester = exams.groupBy { exam ->
            val semester = coursesById[exam.courseId]?.semester ?: -1
            localEpochDay(exam.date) to semester
        }
        dailySemesterLimitWarnings = byDayAndSemester.values.count { it.size > 2 }

        proctors.forEach { proctor ->
            val proctorSlotsByDay = exams
                .filter { exam -> exam.assignments.any { it.proctorId == proctor.uid } }
                .groupBy { localEpochDay(it.date) }
                .mapValues { (_, dayExams) -> dayExams.map { (it.slotId - 1).coerceAtLeast(0) }.sorted() }
            proctorSlotsByDay.values.forEach { proctorSlots ->
                proctorSlots.windowed(4).forEach { window ->
                    if (window.last() - window.first() == 3) consecutiveViolations += 1
                }
            }
        }

        val loadCounts = proctors.associate { user ->
            user.uid to exams.count { exam -> exam.assignments.any { it.proctorId == user.uid } }
        }.values
        val proctorLoadImbalance = if (loadCounts.isEmpty()) 0 else (loadCounts.maxOrNull() ?: 0) - (loadCounts.minOrNull() ?: 0)

        return SolverMetrics(
            scheduledCourses = exams.size,
            unscheduledCourses = unscheduled,
            totalStudents = courses.sumOf { it.studentCount },
            assignedCapacity = totalCapacity,
            capacityWaste = (totalCapacity - courses.filter { course -> exams.any { it.courseId == course.id } }.sumOf { it.studentCount }).coerceAtLeast(0),
            semesterConflicts = semesterConflicts,
            dailySemesterLimitWarnings = dailySemesterLimitWarnings,
            roomConflicts = roomConflicts,
            capacityFailures = capacityFailures,
            proctorConflicts = proctorConflicts,
            excuseConflicts = excuseConflicts,
            consecutiveViolations = consecutiveViolations,
            proctorLoadImbalance = proctorLoadImbalance
        )
    }

    private fun normalizeSlotTimes(slotTimes: List<String>): List<String> {
        val valid = slotTimes
            .map { it.trim() }
            .filter { it.matches(Regex("""^([01]\d|2[0-3]):([0-5]\d)$""")) }
            .distinct()
            .sortedBy { parseOffsetMs(it) }
        return if (valid.isEmpty()) listOf("09:00", "11:00", "14:00", "16:00") else valid
    }

    private fun parseOffsetMs(time: String): Long {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return ((hour * 60L + minute) - (9 * 60L)).coerceAtLeast(0L) * 60_000L
    }

    private fun proctorPoolPriority(proctor: User, courseDepartmentId: String): Int {
        val dept = proctor.deptId.trim()
        return when {
            dept.equals(courseDepartmentId, ignoreCase = true) -> 0
            dept.isBlank() || dept.contains("ortak", ignoreCase = true) || dept.contains("havuz", ignoreCase = true) -> 1
            else -> 2
        }
    }

    private fun localEpochDay(timestamp: Long): Long {
        return (timestamp + turkeyOffsetMillis) / dayMillis
    }

    private data class SlotPlan(
        val slotIndex: Int,
        val rooms: List<Room>,
        val proctors: List<User>
    )

    private const val turkeyOffsetMillis = 3 * 60 * 60 * 1000L
}
