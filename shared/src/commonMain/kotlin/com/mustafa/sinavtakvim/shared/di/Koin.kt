package com.mustafa.sinavtakvim.shared.di

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

import com.mustafa.sinavtakvim.shared.data.repository.AuthRepository
import com.mustafa.sinavtakvim.shared.data.repository.ExamRepository

val firebaseModule = module {
    single { AuthRepository(runCatching { Firebase.auth }.getOrNull()) }
    single { ExamRepository() }
}

fun initKoin(appDeclaration: KoinAppDeclaration = {}) =
    startKoin {
        appDeclaration()
        modules(firebaseModule)
    }

// Helper for iOS
fun initKoin() = initKoin {}
