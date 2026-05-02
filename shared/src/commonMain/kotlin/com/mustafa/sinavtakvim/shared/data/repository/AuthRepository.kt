package com.mustafa.sinavtakvim.shared.data.repository

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
                val users = firestore.collection("Users").get().documents.map { it.data<com.mustafa.sinavtakvim.shared.models.User>() }
                val user = users.firstOrNull {
                    it.email.equals(selectedEmail, ignoreCase = true) &&
                        it.password == password &&
                        it.role == role
                }
                if (user != null) {
                    selectedRole = user.role
                    selectedUserId = user.uid
                    Result.success(null)
                } else {
                    Result.failure(e)
                }
            } catch (_: Exception) {
                Result.failure(e)
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

    fun isSignedIn(): Boolean = firebaseAuth?.currentUser != null

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
            val users = firestore.collection("Users").get().documents.map { it.data<com.mustafa.sinavtakvim.shared.models.User>() }
            val user = users.firstOrNull { it.email.equals(email, ignoreCase = true) }
            if (user != null) {
                selectedRole = user.role
                selectedUserId = user.uid
            }
        } catch (_: Exception) {
        }
    }
}
