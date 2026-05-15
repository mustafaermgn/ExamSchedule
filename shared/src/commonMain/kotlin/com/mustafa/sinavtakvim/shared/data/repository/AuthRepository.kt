package com.mustafa.sinavtakvim.shared.data.repository

import com.mustafa.sinavtakvim.shared.models.User
import com.mustafa.sinavtakvim.shared.models.UserRole
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.firestore.firestore

class AuthRepository(private val firebaseAuth: FirebaseAuth?) {
    private var selectedRole = UserRole.ADMIN
    private var selectedEmail = ""
    private var selectedUserId = ""
    private val firestore get() = dev.gitlive.firebase.Firebase.firestore

    suspend fun login(
        email: String,
        password: String,
        role: UserRole = UserRole.ADMIN
    ): Result<FirebaseUser?> {
        if (email.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("E-posta ve şifre zorunludur."))
        }

        selectedEmail = email.trim()
        selectedRole = role

        return try {
            val result = firebaseAuth?.signInWithEmailAndPassword(selectedEmail, password)
                ?: throw IllegalStateException("Firebase Auth bu platformda başlatılamadı.")
            syncRoleFromFirestore(selectedEmail)
            Result.success(result.user)
        } catch (e: Exception) {
            // Fallback: local kullanıcı yönetimi ekranından eklenen kullanıcılar
            // Firebase Auth hesabı olmadan da uygulamaya girebilsin.
            try {
                val users = firestore.collection("Users").get().documents.map { it.data<User>() }
                val user = findLocalUser(users, selectedEmail, password, role)
                    ?: findLocalUser(listOf(bootstrapAdminUser()), selectedEmail, password, role)
                if (user != null) {
                    selectedRole = user.role
                    selectedUserId = user.uid
                    Result.success(null)
                } else {
                    Result.failure(e)
                }
            } catch (_: Exception) {
                val user = findLocalUser(listOf(bootstrapAdminUser()), selectedEmail, password, role)
                if (user != null) {
                    selectedRole = user.role
                    selectedUserId = user.uid
                    Result.success(null)
                } else {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun register(
        email: String,
        password: String,
        role: UserRole = UserRole.ADMIN
    ): Result<FirebaseUser?> {
        if (email.isBlank() || password.length < 6) {
            return Result.failure(IllegalArgumentException("Şifre en az 6 karakter olmalıdır."))
        }

        selectedRole = role
        selectedEmail = email.trim()

        return try {
            val result = firebaseAuth?.createUserWithEmailAndPassword(selectedEmail, password)
                ?: throw IllegalStateException("Firebase Auth bu platformda başlatılamadı.")
            Result.success(result.user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCurrentUser(): FirebaseUser? = firebaseAuth?.currentUser

    fun isSignedIn(): Boolean = firebaseAuth?.currentUser != null || selectedUserId.isNotBlank()

    fun currentRole(): UserRole = selectedRole

    fun currentEmail(): String = selectedEmail

    fun currentUserId(): String {
        val firebaseUid = firebaseAuth?.currentUser?.uid
        if (!firebaseUid.isNullOrBlank()) return firebaseUid
        return selectedUserId
    }

    suspend fun logout() {
        selectedEmail = ""
        selectedUserId = ""
        firebaseAuth?.signOut()
    }

    private suspend fun syncRoleFromFirestore(email: String) {
        try {
            val users = firestore.collection("Users").get().documents.map { it.data<User>() }
            val user = users.firstOrNull { it.email.equals(email, ignoreCase = true) }
            if (user != null) {
                selectedRole = user.role
                selectedUserId = user.uid
            }
        } catch (_: Exception) {
        }
    }

    private fun findLocalUser(users: List<User>, email: String, password: String, role: UserRole): User? {
        return users.firstOrNull { user ->
            user.email.equals(email, ignoreCase = true) &&
                (user.password == password || (user.password.isBlank() && password == DEMO_PASSWORD)) &&
                user.role == role
        }
    }

    private fun bootstrapAdminUser(): User {
        return User(
            uid = BOOTSTRAP_ADMIN_UID,
            name = "Sistem Yoneticisi",
            email = "admin@fakulte.edu.tr",
            role = UserRole.ADMIN,
            password = DEMO_PASSWORD,
            deptId = "BIL"
        )
    }

    private companion object {
        const val DEMO_PASSWORD = "123456"
        const val BOOTSTRAP_ADMIN_UID = "admin-root"
    }
}
