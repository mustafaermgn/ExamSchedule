package com.mustafa.sinavtakvim.shared.data.repository

import com.mustafa.sinavtakvim.shared.data.DemoData
import com.mustafa.sinavtakvim.shared.models.Course
import com.mustafa.sinavtakvim.shared.models.Exam
import com.mustafa.sinavtakvim.shared.models.LogEntry
import com.mustafa.sinavtakvim.shared.models.Room
import com.mustafa.sinavtakvim.shared.models.Student
import com.mustafa.sinavtakvim.shared.models.User
import com.mustafa.sinavtakvim.shared.models.UserRole
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ExamRepository {
    private val firestore get() = Firebase.firestore

    private val localCourses = DemoData.courses.toMutableList()
    private val localRooms = DemoData.rooms.toMutableList()
    private val localUsers = DemoData.users.toMutableList()
    private val localStudents = mutableListOf<Student>()
    private val localLogs = DemoData.logs.toMutableList()
    private var localExams = DemoData.exams().toMutableList()

    suspend fun getCourses(): List<Course> {
        val remote = try {
            firestore.collection("Courses_v2").get().documents.map { it.data<Course>() }
        } catch (_: Exception) { emptyList() }
        val combined = localCourses.toMutableList()
        remote.forEach { r ->
            val index = combined.indexOfFirst { it.id == r.id }
            if (index >= 0) combined[index] = r else combined += r
        }
        return combined
    }

    suspend fun getRooms(): List<Room> {
        val remote = try {
            firestore.collection("Rooms_v2").get().documents.map { it.data<Room>() }
        } catch (_: Exception) { emptyList() }
        val combined = localRooms.toMutableList()
        remote.forEach { r ->
            val index = combined.indexOfFirst { it.id == r.id }
            if (index >= 0) combined[index] = r else combined += r
        }
        return combined
    }

    suspend fun getUsers(): List<User> {
        val remote = try {
            firestore.collection("Users_v2").get().documents.map { it.data<User>() }
        } catch (_: Exception) { emptyList() }
        
        val combined = localUsers.toMutableList()
        remote.forEach { rUser ->
            val index = combined.indexOfFirst { it.uid == rUser.uid }
            if (index >= 0) combined[index] = rUser else combined += rUser
        }
        return combined
    }

    suspend fun getProctors(): List<User> = getUsers().filter { it.role == UserRole.PROCTOR }

    suspend fun getExams(): List<Exam> {
        val remote = try {
            firestore.collection("Exams_v2").get().documents.map { it.data<Exam>() }
        } catch (_: Exception) { emptyList() }
        val combined = localExams.toMutableList()
        remote.forEach { r ->
            val index = combined.indexOfFirst { it.id == r.id }
            if (index >= 0) combined[index] = r else combined += r
        }
        return combined
    }

    suspend fun getLogs(): List<LogEntry> = fromFirestore("Logs_v2") { localLogs }

    fun observeExams(): Flow<List<Exam>> = flow {
        emit(getExams())
    }

    suspend fun addCourse(course: Course) {
        upsertLocal(localCourses, course) { it.id }
        try {
            firestore.collection("Courses_v2").document(course.id).set(course)
        } catch (_: Exception) {
        }
    }

    suspend fun addRoom(room: Room) {
        upsertLocal(localRooms, room) { it.id }
        try {
            firestore.collection("Rooms_v2").document(room.id).set(room)
        } catch (_: Exception) {
        }
    }

    suspend fun addUser(user: User) {
        upsertLocal(localUsers, user) { it.uid }
        try {
            firestore.collection("Users_v2").document(user.uid).set(user)
        } catch (_: Exception) {
        }
    }

    suspend fun deleteUser(userId: String) {
        localUsers.removeAll { it.uid == userId }
        try {
            firestore.collection("Users_v2").document(userId).delete()
        } catch (_: Exception) {
        }
    }

    suspend fun submitExcuse(userId: String, excuse: com.mustafa.sinavtakvim.shared.models.DateRange) {
        val user = getUsers().find { it.uid == userId } ?: return
        val updated = user.copy(excuses = user.excuses + excuse)
        addUser(updated)
    }

    suspend fun updateExcuseStatus(userId: String, excuseStart: Long, isApproved: Boolean) {
        val user = getUsers().find { it.uid == userId } ?: return
        val updatedExcuses = user.excuses.map { 
            if (it.start == excuseStart) it.copy(isApproved = isApproved) else it 
        }
        addUser(user.copy(excuses = updatedExcuses))
    }

    suspend fun writeLog(log: LogEntry) {
        upsertLocal(localLogs, log) { it.id }
        try {
            firestore.collection("Logs_v2").document(log.id).set(log)
        } catch (_: Exception) {
        }
    }

    suspend fun saveExams(exams: List<Exam>) {
        localExams = exams.toMutableList()
        try {
            val existing = firestore.collection("Exams_v2").get()
            existing.documents.forEach { it.reference.delete() }

            val batch = firestore.batch()
            exams.forEach { exam ->
                batch.set(firestore.collection("Exams_v2").document(exam.id), exam)
            }
            batch.commit()
        } catch (_: Exception) {
        }
    }

    suspend fun seedDemoData() {
        DemoData.courses.forEach { addCourse(it) }
        DemoData.rooms.forEach { addRoom(it) }
        DemoData.users.forEach { addUser(it) }
        saveExams(DemoData.exams())
        DemoData.logs.forEach { writeLog(it) }
    }

    suspend fun clearDatabase() {
        // 1. Reset Local Cache to Demo Defaults
        localCourses.clear(); localCourses.addAll(DemoData.courses)
        localRooms.clear(); localRooms.addAll(DemoData.rooms)
        localUsers.clear(); localUsers.addAll(DemoData.users)
        localStudents.clear()
        localExams.clear(); localExams.addAll(DemoData.exams())
        localLogs.clear(); localLogs.addAll(DemoData.logs)
        
        // 2. Clear Firestore Collections
        try {
            val collections = listOf("Courses_v2", "Rooms_v2", "Users_v2", "Students_v2", "Exams_v2", "Logs_v2")
            for (collectionName in collections) {
                try {
                    val snapshot = firestore.collection(collectionName).get()
                    for (doc in snapshot.documents) {
                        doc.reference.delete()
                    }
                } catch (e: Exception) {
                    println("Firebase temizleme hatası ($collectionName): ${e.message}")
                }
            }
            
            // 3. Re-seed ONLY basic Demo Data (Admin, etc.) to Firestore so system remains usable
            DemoData.users.filter { it.role == com.mustafa.sinavtakvim.shared.models.UserRole.ADMIN }.forEach { addUser(it) }
            DemoData.rooms.forEach { addRoom(it) }
            DemoData.courses.forEach { addCourse(it) }
            
        } catch (e: Exception) {
            println("Genel veritabanı temizleme hatası: ${e.message}")
        }
    }

    suspend fun getStudents(): List<Student> {
        val remote = try {
            firestore.collection("Students_v2").get().documents.map { it.data<Student>() }
        } catch (_: Exception) {
            emptyList()
        }

        val combined = localStudents.toMutableList()
        remote.forEach { student ->
            val index = combined.indexOfFirst { it.id == student.id || it.studentNumber == student.studentNumber }
            if (index >= 0) combined[index] = student else combined += student
        }
        localStudents.clear()
        localStudents.addAll(combined)
        return combined
    }

    suspend fun addStudent(student: Student) {
        upsertLocal(localStudents, student) { it.id }
        try {
            firestore.collection("Students_v2").document(student.id).set(student)
        } catch (_: Exception) {
        }
    }

    suspend fun enrollStudentsInCourse(courseId: String, students: List<Student>) {
        getStudents()

        students.forEach { student ->
            val existing = localStudents.find { it.studentNumber == student.studentNumber }
            val updated = if (existing != null) {
                if (!existing.enrolledCourseIds.contains(courseId)) {
                    existing.copy(enrolledCourseIds = existing.enrolledCourseIds + courseId)
                } else existing
            } else {
                student.copy(id = student.studentNumber, enrolledCourseIds = listOf(courseId))
            }
            addStudent(updated)
        }
        
        val enrolledCount = localStudents.count { courseId in it.enrolledCourseIds }
        val course = getCourses().find { it.id == courseId }
        course?.let {
            addCourse(it.copy(studentCount = enrolledCount))
        }
    }

    private suspend inline fun <reified T : Any> fromFirestore(
        collectionName: String,
        fallback: () -> List<T>
    ): List<T> {
        return try {
            val remote = firestore.collection(collectionName).get().documents.map { it.data<T>() }
            // Merge remote with fallback to ensure demo data + dynamic data exists
            val local = fallback()
            if (remote.isEmpty()) return local
            
            // Simple merge: return remote but if remote doesn't have something, we'd need a key.
            // For now, let's just return remote if not empty, OR combine them if we can identify them.
            // Actually, for users, we definitely want both.
            remote
        } catch (_: Exception) {
            fallback()
        }
    }

    private fun <T> upsertLocal(list: MutableList<T>, item: T, idOf: (T) -> String) {
        val id = idOf(item)
        val index = list.indexOfFirst { idOf(it) == id }
        if (index >= 0) {
            list[index] = item
        } else {
            list += item
        }
    }
}
