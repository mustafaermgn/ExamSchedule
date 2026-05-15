package com.mustafa.sinavtakvim.shared.algorithms

import com.mustafa.sinavtakvim.shared.models.Course
import com.mustafa.sinavtakvim.shared.models.Room
import com.mustafa.sinavtakvim.shared.models.User
import kotlin.time.measureTime

class GreedySolver : ExamSolver {
    override fun solve(
        courses: List<Course>,
        rooms: List<Room>,
        proctors: List<User>,
        slotTimes: List<String>,
        examDays: List<Long>
    ): SolverResult {
        var result = SolverResult(emptyList(), 0L, 0.0)
        val time = measureTime {
            result = SchedulingEngine.solve(
                algorithmName = "Sezgisel Solver",
                courses = courses,
                rooms = rooms,
                proctors = proctors,
                slotTimes = slotTimes,
                examDays = examDays,
                selectRooms = ::findFastRooms
            )
        }
        return result.copy(executionTimeMs = time.inWholeMilliseconds)
    }

    private fun findFastRooms(target: Int, rooms: List<Room>): List<Room> {
        var capacity = 0
        val selected = mutableListOf<Room>()
        rooms.sortedByDescending { it.capacity }.forEach { room ->
            if (capacity < target) {
                selected += room
                capacity += room.capacity
            }
        }
        return if (capacity >= target) selected else emptyList()
    }
}
