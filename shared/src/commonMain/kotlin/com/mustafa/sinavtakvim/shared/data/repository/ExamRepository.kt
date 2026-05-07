package com.mustafa.sinavtakvim.shared.data.repository

import com.mustafa.sinavtakvim.shared.models.Course
import com.mustafa.sinavtakvim.shared.models.Exam
import com.mustafa.sinavtakvim.shared.models.LogEntry
import com.mustafa.sinavtakvim.shared.models.Room
import com.mustafa.sinavtakvim.shared.models.SlotConfig
import com.mustafa.sinavtakvim.shared.models.Student
import com.mustafa.sinavtakvim.shared.models.User
import com.mustafa.sinavtakvim.shared.models.UserRole
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ExamRepository {
    private val firestore get() = Firebase.firestore

    private val localCourses = mutableListOf<Course>()
    private val localRooms = mutableListOf<Room>()
    private val localUsers = mutableListOf<User>()
    private val localStudents = mutableListOf<Student>()
    private val localLogs = mutableListOf<LogEntry>()
    private var localExams = mutableListOf<Exam>()
    private var localSlotConfig: SlotConfig? = null

    suspend fun getCourses(): List<Course> {
        if (localCourses.isNotEmpty()) return localCourses.toList()
        val remote = readCollection<Course>(COLLECTION_COURSES)
        localCourses.clear()
        localCourses.addAll(remote)
        return localCourses.toList()
    }

    suspend fun getRooms(): List<Room> {
        if (localRooms.isNotEmpty()) return localRooms.toList()
        val remote = readCollection<Room>(COLLECTION_ROOMS)
        localRooms.clear()
        localRooms.addAll(remote)
        return localRooms.toList()
    }

    suspend fun getUsers(): List<User> {
        if (localUsers.isNotEmpty()) return localUsers.toList()
        val remote = readCollection<User>(COLLECTION_USERS)
        localUsers.clear()
        localUsers.addAll(remote)
        return localUsers.toList()
    }

    suspend fun getProctors(): List<User> = getUsers().filter { it.role == UserRole.PROCTOR }

    suspend fun getExams(): List<Exam> {
        if (localExams.isNotEmpty()) return localExams.toList()
        val remote = readCollection<Exam>(COLLECTION_EXAMS)
        localExams = remote.toMutableList()
        return localExams.toList()
    }

    @Suppress("unused")
    suspend fun getLogs(): List<LogEntry> {
        if (localLogs.isNotEmpty()) return localLogs.toList()
        val remote = readCollection<LogEntry>(COLLECTION_LOGS)
        localLogs.clear()
        localLogs.addAll(remote)
        return localLogs.toList()
    }

    suspend fun getSlotConfig(): SlotConfig {
        localSlotConfig?.let { return it }
        val remote = try {
            firestore.collection(COLLECTION_ADMIN_COMMANDS).document(DOC_SLOT_CONFIG).get().data<SlotConfig>()
        } catch (_: Exception) {
            null
        }
        val config = remote ?: SlotConfig()
        localSlotConfig = config
        return config
    }

    suspend fun saveSlotConfig(slotConfig: SlotConfig) {
        localSlotConfig = slotConfig
        firestore.collection(COLLECTION_ADMIN_COMMANDS).document(DOC_SLOT_CONFIG).set(slotConfig)
    }

    @Suppress("unused")
    fun observeExams(): Flow<List<Exam>> = flow { emit(getExams()) }

    suspend fun addCourse(course: Course) {
        upsertLocal(localCourses, course) { it.id }
        firestore.collection(COLLECTION_COURSES).document(course.id).set(course)
    }

    suspend fun addRoom(room: Room) {
        upsertLocal(localRooms, room) { it.id }
        firestore.collection(COLLECTION_ROOMS).document(room.id).set(room)
    }

    suspend fun addUser(user: User) {
        upsertLocal(localUsers, user) { it.uid }
        firestore.collection(COLLECTION_USERS).document(user.uid).set(user)
    }

    suspend fun deleteUser(userId: String) {
        localUsers.removeAll { it.uid == userId }
        firestore.collection(COLLECTION_USERS).document(userId).delete()
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

    @Suppress("unused")
    suspend fun writeLog(log: LogEntry) {
        upsertLocal(localLogs, log) { it.id }
        firestore.collection(COLLECTION_LOGS).document(log.id).set(log)
    }

    suspend fun saveExams(exams: List<Exam>) {
        localExams = exams.toMutableList()

        val existing = firestore.collection(COLLECTION_EXAMS).get()
        existing.documents.forEach { it.reference.delete() }

        val batch = firestore.batch()
        exams.forEach { exam ->
            batch.set(firestore.collection(COLLECTION_EXAMS).document(exam.id), exam)
        }
        batch.commit()
    }

    suspend fun clearDatabase() {
        localCourses.clear()
        localRooms.clear()
        localUsers.clear()
        localStudents.clear()
        localExams.clear()
        localLogs.clear()
        localSlotConfig = null

        val collections = listOf(
            COLLECTION_COURSES,
            COLLECTION_ROOMS,
            COLLECTION_USERS,
            COLLECTION_STUDENTS,
            COLLECTION_EXAMS,
            COLLECTION_LOGS,
            COLLECTION_ADMIN_COMMANDS,
            "Courses_v2",
            "Rooms_v2",
            "Users_v2",
            "Students_v2",
            "Exams_v2",
            "Logs_v2"
        )

        collections.forEach { collectionName ->
            val snapshot = firestore.collection(collectionName).get()
            snapshot.documents.forEach { it.reference.delete() }
        }

        bootstrapAdmin()
    }

    suspend fun getStudents(): List<Student> {
        if (localStudents.isNotEmpty()) return localStudents.toList()
        val remote = readCollection<Student>(COLLECTION_STUDENTS)
        localStudents.clear()
        localStudents.addAll(remote)
        return localStudents.toList()
    }

    suspend fun addStudent(student: Student) {
        upsertLocal(localStudents, student) { it.id }
        firestore.collection(COLLECTION_STUDENTS).document(student.id).set(student)
    }

    suspend fun enrollStudentsInCourse(courseId: String, students: List<Student>) {
        getStudents()

        students.forEach { student ->
            val existing = localStudents.find { it.studentNumber == student.studentNumber }
            val updated = if (existing != null) {
                if (!existing.enrolledCourseIds.contains(courseId)) {
                    existing.copy(enrolledCourseIds = existing.enrolledCourseIds + courseId)
                } else {
                    existing
                }
            } else {
                student.copy(id = student.studentNumber, enrolledCourseIds = listOf(courseId))
            }
            addStudent(updated)
        }

        val enrolledCount = localStudents.count { courseId in it.enrolledCourseIds }
        val course = getCourses().find { it.id == courseId }
        if (course != null) {
            addCourse(course.copy(studentCount = enrolledCount))
        }
    }

    private suspend inline fun <reified T : Any> readCollection(collectionName: String): List<T> {
        return try {
            firestore.collection(collectionName).get().documents.map { it.data<T>() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun <T> upsertLocal(list: MutableList<T>, item: T, idOf: (T) -> String) {
        val id = idOf(item)
        val index = list.indexOfFirst { idOf(it) == id }
        if (index >= 0) list[index] = item else list += item
    }

    private companion object {
        const val COLLECTION_USERS = "Users"
        const val COLLECTION_COURSES = "Courses"
        const val COLLECTION_ROOMS = "Rooms"
        const val COLLECTION_EXAMS = "Exams"
        const val COLLECTION_STUDENTS = "Students"
        const val COLLECTION_LOGS = "Logs"
        const val COLLECTION_ADMIN_COMMANDS = "AdminCommands"
        const val DOC_SLOT_CONFIG = "slot_config"
        const val BOOTSTRAP_ADMIN_UID = "admin-root"
    }

    private suspend fun bootstrapAdmin() {
        val admin = User(
            uid = BOOTSTRAP_ADMIN_UID,
            name = "Sistem Yöneticisi",
            email = "admin@fakulte.edu.tr",
            role = UserRole.ADMIN,
            password = "123456",
            deptId = "BIL"
        )
        addUser(admin)
    }

}
