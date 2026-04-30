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
    private val slotOffsets = listOf(0L, 2 * 60 * 60 * 1000L, 5 * 60 * 60 * 1000L, 7 * 60 * 60 * 1000L)

    fun solve(
        algorithmName: String,
        courses: List<Course>,
        rooms: List<Room>,
        proctors: List<User>,
        selectRooms: (target: Int, rooms: List<Room>) -> List<Room>
    ): SolverResult {
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
                maxSlot = (cleanCourses.size * 3).coerceAtLeast(16)
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
                date = slotStart(plan.slotIndex),
                slotId = (plan.slotIndex % slotOffsets.size) + 1,
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
            metrics.capacityFailures +
            metrics.proctorConflicts +
            metrics.excuseConflicts +
            metrics.consecutiveViolations
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

    fun slotStart(slotIndex: Int): Long {
        val day = slotIndex / slotOffsets.size
        val slot = slotIndex % slotOffsets.size
        return firstExamDay + day * dayMillis + slotOffsets[slot]
    }

    fun slotLabel(slotId: Int): String = when (slotId) {
        1 -> "09:00"
        2 -> "11:00"
        3 -> "14:00"
        4 -> "16:00"
        else -> "Oturum $slotId"
    }

    private fun findPlan(
        course: Course,
        rooms: List<Room>,
        proctors: List<User>,
        slotSemesters: Map<Int, Set<Int>>,
        slotRooms: Map<Int, Set<String>>,
        slotProctors: Map<Int, Set<String>>,
        selectRooms: (target: Int, rooms: List<Room>) -> List<Room>,
        maxSlot: Int
    ): SlotPlan? {
        for (slot in 0 until maxSlot) {
            if (slotSemesters[slot]?.contains(course.semester) == true) continue

            val freeRooms = rooms.filterNot { room -> slotRooms[slot]?.contains(room.id) == true }
            val selectedRooms = selectRooms(course.studentCount, freeRooms)
            if (selectedRooms.sumOf { it.capacity } < course.studentCount) continue

            val selectedProctors = mutableListOf<User>()
            for (room in selectedRooms) {
                val proctor = proctors.firstOrNull { candidate ->
                    candidate.uid !in selectedProctors.map { it.uid } &&
                        slotProctors[slot]?.contains(candidate.uid) != true &&
                        isAvailable(candidate, slot, slotProctors)
                }
                if (proctor != null) selectedProctors += proctor
            }
            if (selectedProctors.size == selectedRooms.size) return SlotPlan(slot, selectedRooms, selectedProctors)
        }
        return null
    }

    private fun isAvailable(proctor: User, slot: Int, slotProctors: Map<Int, Set<String>>): Boolean {
        val start = slotStart(slot)
        val hasExcuse = proctor.excuses.any { start in it.start..it.end }
        if (hasExcuse) return false

        val previousThree = (1..3).all { offset ->
            slotProctors[slot - offset]?.contains(proctor.uid) == true
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
        var capacityFailures = 0
        var proctorConflicts = 0
        var excuseConflicts = 0
        var consecutiveViolations = 0

        exams.groupBy { it.date to it.slotId }.values.forEach { sameSlot ->
            val semesters = sameSlot.mapNotNull { coursesById[it.courseId]?.semester }
            semesterConflicts += semesters.size - semesters.toSet().size

            val roomsInSlot = sameSlot.flatMap { it.assignments.map { assignment -> assignment.roomId } }
            proctorConflicts += roomsInSlot.size - roomsInSlot.toSet().size

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

        proctors.forEach { proctor ->
            val proctorSlots = exams
                .filter { exam -> exam.assignments.any { it.proctorId == proctor.uid } }
                .map { slotIndexFromExam(it) }
                .sorted()
            proctorSlots.windowed(4).forEach { window ->
                if (window.last() - window.first() == 3) consecutiveViolations += 1
            }
        }

        return SolverMetrics(
            scheduledCourses = exams.size,
            unscheduledCourses = unscheduled,
            totalStudents = courses.sumOf { it.studentCount },
            assignedCapacity = totalCapacity,
            capacityWaste = (totalCapacity - courses.filter { course -> exams.any { it.courseId == course.id } }.sumOf { it.studentCount }).coerceAtLeast(0),
            semesterConflicts = semesterConflicts,
            capacityFailures = capacityFailures,
            proctorConflicts = proctorConflicts,
            excuseConflicts = excuseConflicts,
            consecutiveViolations = consecutiveViolations
        )
    }

    private fun slotIndexFromExam(exam: Exam): Int {
        val day = ((exam.date - firstExamDay) / dayMillis).toInt().coerceAtLeast(0)
        return day * slotOffsets.size + (exam.slotId - 1).coerceAtLeast(0)
    }

    private data class SlotPlan(
        val slotIndex: Int,
        val rooms: List<Room>,
        val proctors: List<User>
    )
}
