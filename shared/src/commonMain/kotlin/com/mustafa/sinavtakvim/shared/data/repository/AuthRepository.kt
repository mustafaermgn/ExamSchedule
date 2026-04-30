package com.mustafa.sinavtakvim.shared.data.repository

import com.mustafa.sinavtakvim.shared.models.UserRole
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.firestore.firestore

class AuthRepository(private val firebaseAuth: FirebaseAuth?) {
    private var demoSignedIn = false
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
        val expectedDemoRole = demoRoleForEmail(selectedEmail)
        if (expectedDemoRole != null && expectedDemoRole != role) {
            return Result.failure(IllegalArgumentException("Bu e-posta seçilen kullanıcı rolü ile eşleşmiyor."))
        }

        selectedRole = role

        return try {
            val result = firebaseAuth?.signInWithEmailAndPassword(selectedEmail, password)
                ?: throw IllegalStateException("Firebase Auth bu platformda başlatılamadı.")
            syncRoleFromFirestore(selectedEmail)
            demoSignedIn = false
            Result.success(result.user)
        } catch (e: Exception) {
            // Check Firestore for dynamically created proctors
            try {
                val usersRef = firestore.collection("Users_v2")
                val users = usersRef.get().documents.map { it.data<com.mustafa.sinavtakvim.shared.models.User>() }
                val dynamicUser = users.find { it.email == selectedEmail && it.password == password }
                
                if (dynamicUser != null) {
                    if (dynamicUser.role != role) {
                        return Result.failure(IllegalArgumentException("Bu e-posta seçilen kullanıcı rolü ile eşleşmiyor."))
                    }
                    demoSignedIn = true
                    selectedRole = dynamicUser.role
                    selectedUserId = dynamicUser.uid
                    Result.success(null)
                } else if (canUseDemoSession(selectedEmail, password)) {
                    demoSignedIn = true
                    selectedRole = expectedDemoRole ?: role
                    selectedUserId = demoUserIdForEmail(selectedEmail) ?: if (selectedRole == UserRole.ADMIN) "u-admin" else "p1"
                    Result.success(null)
                } else {
                    Result.failure(e)
                }
            } catch (firestoreError: Exception) {
                if (canUseDemoSession(selectedEmail, password)) {
                    demoSignedIn = true
                    selectedRole = expectedDemoRole ?: role
                    selectedUserId = demoUserIdForEmail(selectedEmail) ?: if (selectedRole == UserRole.ADMIN) "u-admin" else "p1"
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
            demoSignedIn = false
            Result.success(result.user)
        } catch (e: Exception) {
            if (selectedEmail.endsWith("@fakulte.edu.tr")) {
                demoSignedIn = true
                Result.success(null)
            } else {
                Result.failure(e)
            }
        }
    }

    fun getCurrentUser(): FirebaseUser? = firebaseAuth?.currentUser

    fun isSignedIn(): Boolean = demoSignedIn || firebaseAuth?.currentUser != null

    fun currentRole(): UserRole = selectedRole

    fun currentEmail(): String = selectedEmail

    fun currentUserId(): String {
        val firebaseUid = firebaseAuth?.currentUser?.uid
        if (!firebaseUid.isNullOrBlank()) return firebaseUid
        
        if (selectedUserId.isNotBlank()) return selectedUserId

        return when {
            selectedEmail.equals("admin@fakulte.edu.tr", ignoreCase = true) -> "u-admin"
            selectedEmail.equals("mert.celik@fakulte.edu.tr", ignoreCase = true) -> "p1"
            selectedEmail.equals("irem.koc@fakulte.edu.tr", ignoreCase = true) -> "p2"
            selectedEmail.equals("selin.gunes@fakulte.edu.tr", ignoreCase = true) -> "p3"
            selectedRole == UserRole.PROCTOR -> "p1"
            else -> "u-admin"
        }
    }

    suspend fun logout() {
        demoSignedIn = false
        selectedEmail = ""
        firebaseAuth?.signOut()
    }

    private fun canUseDemoSession(email: String, password: String): Boolean {
        return email.endsWith("@fakulte.edu.tr") && password == "123456"
    }

    private suspend fun syncRoleFromFirestore(email: String) {
        try {
            val users = firestore.collection("Users_v2").get().documents.map { it.data<com.mustafa.sinavtakvim.shared.models.User>() }
            val user = users.firstOrNull { it.email.equals(email, ignoreCase = true) }
            if (user != null) {
                selectedRole = user.role
                selectedUserId = user.uid
            }
        } catch (_: Exception) {
        }
    }

    private fun demoRoleForEmail(email: String): UserRole? {
        return when {
            email.equals("admin@fakulte.edu.tr", ignoreCase = true) -> UserRole.ADMIN
            email.endsWith("@fakulte.edu.tr") -> UserRole.PROCTOR
            else -> null
        }
    }

    private fun demoUserIdForEmail(email: String): String? {
        return when {
            email.equals("admin@fakulte.edu.tr", ignoreCase = true) -> "u-admin"
            email.equals("mert.celik@fakulte.edu.tr", ignoreCase = true) -> "p1"
            email.equals("irem.koc@fakulte.edu.tr", ignoreCase = true) -> "p2"
            email.equals("selin.gunes@fakulte.edu.tr", ignoreCase = true) -> "p3"
            email.equals("ege.sen@fakulte.edu.tr", ignoreCase = true) -> "p4"
            email.equals("zeynep.korkmaz@fakulte.edu.tr", ignoreCase = true) -> "p5"
            email.equals("cem.uslu@fakulte.edu.tr", ignoreCase = true) -> "p6"
            else -> null
        }
    }
}
