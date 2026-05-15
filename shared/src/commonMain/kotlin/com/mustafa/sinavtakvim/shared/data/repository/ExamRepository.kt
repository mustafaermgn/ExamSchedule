package com.mustafa.sinavtakvim.shared.data.repository

import com.mustafa.sinavtakvim.shared.models.AdminDataBackup
import com.mustafa.sinavtakvim.shared.models.Course
import com.mustafa.sinavtakvim.shared.models.DateRange
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
        if (remote.isEmpty()) {
            localUsers.add(bootstrapAdminUser())
            persistBootstrapAdmin()
        } else {
            localUsers.addAll(remote)
        }
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
        runCatching {
            firestore.collection(COLLECTION_ADMIN_COMMANDS).document(DOC_SLOT_CONFIG).set(slotConfig)
        }
    }

    @Suppress("unused")
    fun observeExams(): Flow<List<Exam>> = flow { emit(getExams()) }

    suspend fun addCourse(course: Course) {
        upsertLocal(localCourses, course) { it.id }
        runCatching { firestore.collection(COLLECTION_COURSES).document(course.id).set(course) }
    }

    suspend fun addRoom(room: Room) {
        upsertLocal(localRooms, room) { it.id }
        runCatching { firestore.collection(COLLECTION_ROOMS).document(room.id).set(room) }
    }

    suspend fun addUser(user: User): Boolean {
        upsertLocal(localUsers, user) { it.uid }
        return try {
            firestore.collection(COLLECTION_USERS).document(user.uid).set(user)
            true
        } catch (e: Exception) {
            println("Add user error: ${e.message}")
            false
        }
    }

    suspend fun deleteUser(userId: String) {
        localUsers.removeAll { it.uid == userId }
        runCatching { firestore.collection(COLLECTION_USERS).document(userId).delete() }
    }

    suspend fun submitExcuse(userId: String, excuse: DateRange) {
        val user = getUsers().find { it.uid == userId } ?: return
        val updated = user.copy(excuses = user.excuses + excuse)
        addUser(updated)
    }

    suspend fun updateExcuseStatus(userId: String, excuseStart: Long, isApproved: Boolean) {
        val user = getUsers().find { it.uid == userId } ?: return
        val updatedExcuses = user.excuses.map {
            if (it.start == excuseStart) it.copy(isApproved = isApproved, isRejected = false) else it
        }
        addUser(user.copy(excuses = updatedExcuses))
    }

    suspend fun rejectExcuse(userId: String, excuseStart: Long) {
        val user = getUsers().find { it.uid == userId } ?: return
        val updatedExcuses = user.excuses.map {
            if (it.start == excuseStart) it.copy(isApproved = false, isRejected = true) else it
        }
        addUser(user.copy(excuses = updatedExcuses))
    }

    @Suppress("unused")
    suspend fun writeLog(log: LogEntry) {
        upsertLocal(localLogs, log) { it.id }
        runCatching { firestore.collection(COLLECTION_LOGS).document(log.id).set(log) }
    }

    suspend fun saveExams(exams: List<Exam>) {
        localExams = exams.toMutableList()

        runCatching {
            val existing = firestore.collection(COLLECTION_EXAMS).get()
            existing.documents.forEach { it.reference.delete() }

            val batch = firestore.batch()
            exams.forEach { exam ->
                batch.set(firestore.collection(COLLECTION_EXAMS).document(exam.id), exam)
            }
            batch.commit()
        }
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
            clearCollection(collectionName)
        }

        persistBootstrapAdmin()
    }

    suspend fun createAdminDataBackup(): AdminDataBackup {
        val allUsers = getUsers()
        return AdminDataBackup(
            courses = getCourses(),
            students = getStudents(),
            rooms = getRooms(),
            proctors = allUsers.filter { it.role == UserRole.PROCTOR }
        )
    }

    suspend fun restoreAdminDataBackup(backup: AdminDataBackup) {
        val admins = getUsers()
            .filter { it.role == UserRole.ADMIN }
            .ifEmpty { listOf(bootstrapAdminUser()) }
            .distinctBy { it.uid }

        val restoredCourses = backup.courses
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
        val restoredRooms = backup.rooms
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
        val restoredStudents = backup.students
            .map { student -> student.copy(id = student.id.ifBlank { student.studentNumber }) }
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
        val restoredProctors = backup.proctors
            .filter { it.uid.isNotBlank() }
            .map { it.copy(role = UserRole.PROCTOR) }
            .distinctBy { it.uid }

        clearCollection(COLLECTION_COURSES)
        clearCollection(COLLECTION_ROOMS)
        clearCollection(COLLECTION_STUDENTS)
        clearCollection(COLLECTION_EXAMS)
        clearCollection(COLLECTION_LOGS)
        clearProctorUsers()

        localCourses.clear()
        localRooms.clear()
        localStudents.clear()
        localExams.clear()
        localLogs.clear()

        localCourses.addAll(restoredCourses)
        localRooms.addAll(restoredRooms)
        localStudents.addAll(restoredStudents)

        localUsers.clear()
        localUsers.addAll(admins)
        localUsers.addAll(restoredProctors)

        persistBootstrapAdmin()
        restoredCourses.forEach { course ->
            runCatching { firestore.collection(COLLECTION_COURSES).document(course.id).set(course) }
        }
        restoredRooms.forEach { room ->
            runCatching { firestore.collection(COLLECTION_ROOMS).document(room.id).set(room) }
        }
        restoredStudents.forEach { student ->
            runCatching { firestore.collection(COLLECTION_STUDENTS).document(student.id).set(student) }
        }
        restoredProctors.forEach { proctor ->
            runCatching { firestore.collection(COLLECTION_USERS).document(proctor.uid).set(proctor) }
        }
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
        runCatching { firestore.collection(COLLECTION_STUDENTS).document(student.id).set(student) }
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

    private fun bootstrapAdminUser(): User {
        return User(
            uid = BOOTSTRAP_ADMIN_UID,
            name = "Sistem Yoneticisi",
            email = "admin@fakulte.edu.tr",
            role = UserRole.ADMIN,
            password = "123456",
            deptId = "BIL"
        )
    }

    private suspend fun persistBootstrapAdmin() {
        val admin = bootstrapAdminUser()
        upsertLocal(localUsers, admin) { it.uid }
        runCatching { firestore.collection(COLLECTION_USERS).document(admin.uid).set(admin) }
    }

    private suspend fun clearCollection(collectionName: String) {
        runCatching {
            val snapshot = firestore.collection(collectionName).get()
            snapshot.documents.forEach { it.reference.delete() }
        }
    }

    private suspend fun clearProctorUsers() {
        runCatching {
            val snapshot = firestore.collection(COLLECTION_USERS).get()
            snapshot.documents.forEach { document ->
                val user = document.data<User>()
                if (user.role == UserRole.PROCTOR) {
                    document.reference.delete()
                }
            }
        }
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
}
