@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
package com.mustafa.sinavtakvim

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.io.encoding.ExperimentalEncodingApi
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.transitions.SlideTransition
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.mustafa.sinavtakvim.shared.models.User
import com.mustafa.sinavtakvim.shared.models.UserRole
import com.mustafa.sinavtakvim.shared.data.repository.AuthRepository
import com.mustafa.sinavtakvim.shared.data.repository.ExamRepository
import com.mustafa.sinavtakvim.theme.AppTheme
import com.mustafa.sinavtakvim.ui.components.*
import com.mustafa.sinavtakvim.ui.screens.*
import org.koin.compose.koinInject

object ThemeState {
    var darkThemeEnabled by mutableStateOf(false)
}

@OptIn(ExperimentalEncodingApi::class)
@Composable
fun App() {
    val authRepository = koinInject<AuthRepository>()
    val examRepository = koinInject<ExamRepository>()
    val userId = authRepository.currentUserId()
    var user by remember { mutableStateOf<User?>(null) }
    LaunchedEffect(userId) {
        user = examRepository.getUsers().find { it.uid == userId }
        ThemeState.darkThemeEnabled = user?.preferences?.get("darkTheme") ?: false
    }

    AppTheme(darkTheme = ThemeState.darkThemeEnabled) {
        val startScreen: Screen = if (authRepository.isSignedIn()) {
            MainScreen(
                role = authRepository.currentRole(),
                userId = userId
            )
        } else {
            LoginScreen()
        }

        Navigator(startScreen) { navigator ->
            SlideTransition(navigator)
        }
    }
}

class MainScreen(
    private val role: UserRole = UserRole.ADMIN,
    private val userId: String = "u-admin"
) : Screen {
    @Composable
    override fun Content() {
        val authRepository = koinInject<AuthRepository>()
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val scaffoldState = rememberScaffoldState()
        
        var currentScreen by remember { mutableStateOf(NavScreen.DASHBOARD) }
        var user by remember { mutableStateOf<User?>(null) }
        val examRepository = koinInject<ExamRepository>()

        LaunchedEffect(userId) {
            user = examRepository.getUsers().find { it.uid == userId }
        }

        Scaffold(
            scaffoldState = scaffoldState,
            drawerBackgroundColor = CorporateColors.Background,
            drawerElevation = 0.dp,
            drawerContent = {
                AppDrawer(
                    currentScreen = currentScreen,
                    onNavigate = { currentScreen = it },
                    onLogout = {
                        scope.launch {
                            authRepository.logout()
                            navigator.replaceAll(LoginScreen())
                        }
                    },
                    onClose = {
                        scope.launch { scaffoldState.drawerState.close() }
                    }
                )
            },
            bottomBar = {
                AppBottomBar(
                    currentScreen = currentScreen,
                    onNavigate = { currentScreen = it }
                )
            },
            topBar = {
                ModernHeader(
                    title = when (currentScreen) {
                        NavScreen.DASHBOARD -> if (role == UserRole.ADMIN) "Fakülte Koordinasyonu" else "Gözetmen Paneli"
                        NavScreen.CALENDAR -> "Sınav Takvimi"
                        NavScreen.MAP -> "Salon Haritası"
                        NavScreen.DATA -> "Veri Yönetimi"
                        NavScreen.PLANNING -> "Sınav Planlama"
                        NavScreen.EXCUSES -> "İzin Talepleri"
                        NavScreen.PROFILE -> "Profil Ayarları"
                    },
                    subtitle = "Akademik Planlama Paneli v1.1",
                    role = if (role == UserRole.ADMIN) "Yönetici" else "Gözetmen",
                    profileImageUrl = user?.profileImageUrl,
                    onSettings = { currentScreen = NavScreen.PROFILE },
                    onLogout = {
                        scope.launch {
                            authRepository.logout()
                            navigator.replaceAll(LoginScreen())
                        }
                    },
                    onMenuClick = {
                        scope.launch {
                            scaffoldState.drawerState.open()
                        }
                    }
                )
            }
        )
 { padding ->
            BackgroundGradient {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = if (role == UserRole.ADMIN) 16.dp else 8.dp)
                ) {
                    when (currentScreen) {
                        NavScreen.DASHBOARD -> {
                            if (role == UserRole.ADMIN) DashboardScreen().Content()
                            else ProctorHomeScreen(userId).Content()
                        }
                        NavScreen.CALENDAR -> CalendarScreen(role = role, proctorId = if (role == UserRole.PROCTOR) userId else null).Content()
                        NavScreen.MAP -> MapScreen(role = role, proctorId = if (role == UserRole.PROCTOR) userId else null).Content()
                        NavScreen.DATA -> AdminDataScreen().Content()
                        NavScreen.PLANNING -> PlanningScreen().Content()
                        NavScreen.EXCUSES -> ExcuseManagementScreen().Content()
                        NavScreen.PROFILE -> ProfileScreen(userId, onUserUpdated = { updated -> user = updated }).Content()
                    }
                }
            }
        }
    }
}
