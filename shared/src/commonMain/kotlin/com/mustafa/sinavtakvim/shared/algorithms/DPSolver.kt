package com.mustafa.sinavtakvim.shared.algorithms

import com.mustafa.sinavtakvim.shared.models.Course
import com.mustafa.sinavtakvim.shared.models.Room
import com.mustafa.sinavtakvim.shared.models.User
import kotlin.time.measureTime

class DPSolver : ExamSolver {
    override fun solve(
        courses: List<Course>,
        rooms: List<Room>,
        proctors: List<User>
    ): SolverResult {
        var result = SolverResult(emptyList(), 0L, 0.0)
        val time = measureTime {
            result = SchedulingEngine.solve(
                algorithmName = "DP Solver",
                courses = courses,
                rooms = rooms,
                proctors = proctors,
                selectRooms = ::findOptimalRooms
            )
        }
        return result.copy(executionTimeMs = time.inWholeMilliseconds)
    }

    private fun findOptimalRooms(target: Int, rooms: List<Room>): List<Room> {
        if (target <= 0 || rooms.isEmpty()) return emptyList()

        val totalCapacity = rooms.sumOf { it.capacity }
        if (totalCapacity < target) return emptyList()

        val reachable = BooleanArray(totalCapacity + 1)
        val previous = IntArray(totalCapacity + 1) { -1 }
        val chosenRoomIndex = IntArray(totalCapacity + 1) { -1 }
        reachable[0] = true

        rooms.forEachIndexed { index, room ->
            for (capacity in totalCapacity - room.capacity downTo 0) {
                val next = capacity + room.capacity
                if (reachable[capacity] && !reachable[next]) {
                    reachable[next] = true
                    previous[next] = capacity
                    chosenRoomIndex[next] = index
                }
            }
        }

        val bestCapacity = (target..totalCapacity)
            .filter { reachable[it] }
            .minWithOrNull(compareBy<Int> { it - target }.thenBy { roomCountFor(it, previous) })
            ?: return emptyList()

        val selected = mutableListOf<Room>()
        var cursor = bestCapacity
        while (cursor > 0) {
            val index = chosenRoomIndex[cursor]
            if (index < 0) break
            selected += rooms[index]
            cursor = previous[cursor]
        }
        return selected.sortedByDescending { it.capacity }
    }

    private fun roomCountFor(capacity: Int, previous: IntArray): Int {
        var count = 0
        var cursor = capacity
        while (cursor > 0 && previous[cursor] >= 0) {
            count += 1
            cursor = previous[cursor]
        }
        return count
    }
}
