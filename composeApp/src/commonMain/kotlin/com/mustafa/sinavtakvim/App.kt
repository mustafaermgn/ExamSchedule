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
import com.mustafa.sinavtakvim.ui.components.BackgroundGradient
import com.mustafa.sinavtakvim.ui.components.CorporateColors
import com.mustafa.sinavtakvim.ui.screens.*
import com.mustafa.sinavtakvim.ui.components.ModernHeader
import com.mustafa.sinavtakvim.ui.components.ResponsiveBox
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
        
        var selectedTab by remember(role) { mutableIntStateOf(0) }
        var user by remember { mutableStateOf<User?>(null) }
        val examRepository = koinInject<ExamRepository>()

        LaunchedEffect(userId) {
            user = examRepository.getUsers().find { it.uid == userId }
        }
        
        val desktopItems = if (role == UserRole.ADMIN) {
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
        val mobileItems = if (role == UserRole.ADMIN) {
            listOf(
                NavItem("Panel", Icons.Default.Home),
                NavItem("Veri", Icons.Default.Add),
                NavItem("Plan", Icons.Default.Settings),
                NavItem("Diğer", Icons.Default.MoreHoriz)
            )
        } else {
            listOf(
                NavItem("Görev", Icons.Default.Home),
                NavItem("Takvim", Icons.Default.DateRange),
                NavItem("Salon", Icons.Default.LocationOn)
            )
        }

        ResponsiveBox(Modifier.fillMaxSize(), breakpoint = 800.dp) { isDesktop ->
            if (isDesktop) {
                // Desktop Layout: Sidebar + Content
                Row(Modifier.fillMaxSize()) {
                    // Navigation Rail (Sidebar)
                    NavigationRail(
                        backgroundColor = CorporateColors.Surface,
                        contentColor = CorporateColors.Primary,
                        elevation = 8.dp
                    ) {
                        desktopItems.forEachIndexed { index, item ->
                            NavigationRailItem(
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, fontSize = 10.sp) },
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                selectedContentColor = CorporateColors.Primary,
                                unselectedContentColor = CorporateColors.Muted
                            )
                        }
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
                val mobileSelectedTab = selectedTab.coerceIn(0, mobileItems.lastIndex)
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
                            mobileItems.forEachIndexed { index, item ->
                                BottomNavigationItem(
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label, fontSize = 10.sp, maxLines = 1) },
                                    selected = mobileSelectedTab == index,
                                    onClick = { selectedTab = index },
                                    selectedContentColor = CorporateColors.Primary,
                                    unselectedContentColor = CorporateColors.Muted,
                                    alwaysShowLabel = false
                                )
                            }
                        }
                    }
                ) { padding ->
                    BackgroundGradient {
                        Box(Modifier.padding(padding)) {
                            MobileContent(
                                role = role,
                                selectedTab = mobileSelectedTab,
                                userId = userId,
                                onOpenCalendar = { navigator.push(CalendarScreen(role = role, proctorId = if (role == UserRole.PROCTOR) userId else null)) },
                                onOpenMap = { navigator.push(MapScreen(role = role, proctorId = if (role == UserRole.PROCTOR) userId else null)) },
                                onOpenExcuses = { navigator.push(ExcuseManagementScreen()) },
                                onOpenProfile = { navigator.push(ProfileScreen(userId)) },
                                onLogout = {
                                    scope.launch {
                                        authRepository.logout()
                                        navigator.replaceAll(LoginScreen())
                                    }
                                }
                            )
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

    @Composable
    private fun MobileContent(
        role: UserRole,
        selectedTab: Int,
        userId: String,
        onOpenCalendar: () -> Unit,
        onOpenMap: () -> Unit,
        onOpenExcuses: () -> Unit,
        onOpenProfile: () -> Unit,
        onLogout: () -> Unit
    ) {
        if (role == UserRole.ADMIN) {
            when (selectedTab) {
                0 -> DashboardScreen().Content()
                1 -> AdminDataScreen().Content()
                2 -> PlanningScreen().Content()
                3 -> MobileMoreScreen(
                    onOpenCalendar = onOpenCalendar,
                    onOpenMap = onOpenMap,
                    onOpenExcuses = onOpenExcuses,
                    onOpenProfile = onOpenProfile,
                    onLogout = onLogout
                )
            }
        } else {
            MainContent(role, selectedTab, userId)
        }
    }

    @Composable
    private fun MobileMoreScreen(
        onOpenCalendar: () -> Unit,
        onOpenMap: () -> Unit,
        onOpenExcuses: () -> Unit,
        onOpenProfile: () -> Unit,
        onLogout: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Diğer", style = MaterialTheme.typography.h6, color = CorporateColors.Ink, fontWeight = FontWeight.Bold)
            MobileActionCard("Sınav Takvimi", "Programı detaylı görüntüle", onOpenCalendar)
            MobileActionCard("Salon Haritası", "Salon konumları ve atamalar", onOpenMap)
            MobileActionCard("İzin Talepleri", "Eski onay ekranı", onOpenExcuses)
            MobileActionCard("Profil ve Ayarlar", "Koyu tema ve bildirimler", onOpenProfile)
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = CorporateColors.Risk)
                Spacer(Modifier.width(8.dp))
                Text("Çıkış Yap", color = CorporateColors.Risk)
            }
        }
    }

    @Composable
    private fun MobileActionCard(title: String, subtitle: String, onClick: () -> Unit) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = CorporateColors.Surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(title, color = CorporateColors.Ink, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = CorporateColors.Muted, style = MaterialTheme.typography.body2)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Aç", color = CorporateColors.Primary)
                }
            }
        }
    }
}

private data class NavItem(
    val label: String,
    val icon: ImageVector
)
