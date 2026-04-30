@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
package com.mustafa.sinavtakvim

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.ImageBitmap
import com.mustafa.sinavtakvim.shared.utils.toImageBitmap
import kotlin.io.encoding.Base64
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
import com.mustafa.sinavtakvim.ui.components.BackgroundGradient
import com.mustafa.sinavtakvim.ui.components.CorporateColors
import com.mustafa.sinavtakvim.ui.screens.*
import com.mustafa.sinavtakvim.ui.components.ModernHeader
import org.koin.compose.koinInject

@OptIn(ExperimentalEncodingApi::class)
@Composable
fun App() {
    val authRepository = koinInject<AuthRepository>()
    val examRepository = koinInject<ExamRepository>()
    val userId = authRepository.currentUserId()
    var user by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(userId) {
        user = examRepository.getUsers().find { it.uid == userId }
    }

    AppTheme(darkTheme = user?.preferences?.get("darkTheme") ?: false) {
        val startScreen: Screen = if (authRepository.isSignedIn()) {
            MainScreen(authRepository.currentRole(), userId)
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
        
        var selectedTab by remember(role) { mutableIntStateOf(0) }
        var user by remember { mutableStateOf<User?>(null) }
        val examRepository = koinInject<ExamRepository>()

        LaunchedEffect(userId) {
            user = examRepository.getUsers().find { it.uid == userId }
        }
        
        val items = if (role == UserRole.ADMIN) {
            listOf(
                NavItem("Panel", Icons.Default.Home),
                NavItem("Veri", Icons.Default.Add),
                NavItem("Planlama", Icons.Default.Settings),
                NavItem("Takvim", Icons.Default.DateRange),
                NavItem("Harita", Icons.Default.LocationOn),
                NavItem("İzinler", Icons.Default.Info)
            )
        } else {
            listOf(
                NavItem("Görevlerim", Icons.Default.Home),
                NavItem("Takvim", Icons.Default.DateRange),
                NavItem("Salonlar", Icons.Default.LocationOn)
            )
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val isDesktop = this.maxWidth > 800.dp
            
            if (isDesktop) {
                // Desktop Layout: Sidebar + Content
                Row(Modifier.fillMaxSize()) {
                    // Navigation Rail (Sidebar)
                    NavigationRail(
                        backgroundColor = CorporateColors.Surface,
                        contentColor = CorporateColors.Primary,
                        elevation = 8.dp,
                        header = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 16.dp)) {
                                Surface(
                                    modifier = Modifier.size(54.dp),
                                    shape = CircleShape,
                                    color = CorporateColors.Primary.copy(alpha = 0.1f),
                                    elevation = 2.dp
                                ) {
                                    val bitmap = remember(user?.profileImageUrl) {
                                        try {
                                            if (!user?.profileImageUrl.isNullOrBlank()) {
                                                Base64.decode(user!!.profileImageUrl).toImageBitmap()
                                            } else null
                                        } catch (_: Exception) { null }
                                    }

                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                (user?.name?.take(1) ?: "S").uppercase(),
                                                color = CorporateColors.Primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    user?.name ?: (if (role == UserRole.ADMIN) "Admin" else "Gözetmen"),
                                    style = MaterialTheme.typography.caption,
                                    color = CorporateColors.Muted
                                )
                            }
                        }
                    ) {
                        items.forEachIndexed { index, item ->
                            NavigationRailItem(
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, fontSize = 10.sp) },
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                selectedContentColor = CorporateColors.Primary,
                                unselectedContentColor = CorporateColors.Muted
                            )
                        }
                        
                        Spacer(Modifier.weight(1f))
                        
                        NavigationRailItem(
                            icon = { Icon(Icons.Default.Person, contentDescription = "Profil") },
                            label = { Text("Profil") },
                            selected = false,
                            onClick = { navigator.push(ProfileScreen(userId)) }
                        )
                        NavigationRailItem(
                            icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Çıkış") },
                            label = { Text("Çıkış") },
                            selected = false,
                            onClick = {
                                scope.launch {
                                    authRepository.logout()
                                    navigator.replaceAll(LoginScreen())
                                }
                            }
                        )
                    }
                    
                    // Main Content Area
                    Column(Modifier.fillMaxSize()) {
                        ModernHeader(
                            title = if (role == UserRole.ADMIN) "Fakülte Sınav Koordinasyonu" else "Gözetmen Görev Paneli",
                            subtitle = "Akademik Planlama Paneli v1.0",
                            role = if (role == UserRole.ADMIN) "Yönetici" else "Gözetmen",
                            onSettings = { navigator.push(ProfileScreen(userId)) },
                            onLogout = {
                                scope.launch {
                                    authRepository.logout()
                                    navigator.replaceAll(LoginScreen())
                                }
                            }
                        )
                        
                        BackgroundGradient {
                            Box(Modifier.fillMaxSize().padding(if (isDesktop) 24.dp else 12.dp)) {
                                Surface(
                                    modifier = Modifier.fillMaxSize().align(Alignment.Center),
                                    shape = MaterialTheme.shapes.medium,
                                    color = Color.Transparent
                                ) {
                                    MainContent(role, selectedTab, userId)
                                }
                            }
                        }
                    }
                }
            } else {
                // Mobile Layout: Bottom Nav + Content
                Scaffold(
                    topBar = {
                        ModernHeader(
                            title = if (role == UserRole.ADMIN) "Sınav Sistemi" else "Gözetmen Paneli",
                            subtitle = "Mobil Erişim",
                            role = if (role == UserRole.ADMIN) "Yönetici" else "Gözetmen",
                            profileImageUrl = user?.profileImageUrl,
                            onSettings = { navigator.push(ProfileScreen(userId)) },
                            onLogout = {
                                scope.launch {
                                    authRepository.logout()
                                    navigator.replaceAll(LoginScreen())
                                }
                            }
                        )
                    },
                    bottomBar = {
                        BottomNavigation(
                            backgroundColor = CorporateColors.Surface,
                            contentColor = CorporateColors.Primary,
                            elevation = 8.dp
                        ) {
                            items.forEachIndexed { index, item ->
                                BottomNavigationItem(
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label, fontSize = 10.sp) },
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    selectedContentColor = CorporateColors.Primary,
                                    unselectedContentColor = CorporateColors.Muted
                                )
                            }
                        }
                    }
                ) { padding ->
                    BackgroundGradient {
                        Box(Modifier.padding(padding)) {
                            MainContent(role, selectedTab, userId)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MainContent(role: UserRole, selectedTab: Int, userId: String) {
        if (role == UserRole.ADMIN) {
            when (selectedTab) {
                0 -> DashboardScreen().Content()
                1 -> AdminDataScreen().Content()
                2 -> PlanningScreen().Content()
                3 -> CalendarScreen(role = role).Content()
                4 -> MapScreen(role = role).Content()
                5 -> ExcuseManagementScreen().Content() // New screen
            }
        } else {
            when (selectedTab) {
                0 -> ProctorHomeScreen(userId).Content()
                1 -> CalendarScreen(role = role, proctorId = userId).Content()
                2 -> MapScreen(role = role, proctorId = userId).Content()
            }
        }
    }
}

private data class NavItem(
    val label: String,
    val icon: ImageVector
)
