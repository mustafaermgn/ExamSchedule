package com.mustafa.sinavtakvim.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mustafa.sinavtakvim.MainScreen
import com.mustafa.sinavtakvim.NotificationRegistrationCredentials
import com.mustafa.sinavtakvim.registerDeviceForNotifications
import com.mustafa.sinavtakvim.shared.data.repository.AuthRepository
import com.mustafa.sinavtakvim.shared.data.repository.ExamRepository
import com.mustafa.sinavtakvim.shared.models.UserRole
import com.mustafa.sinavtakvim.ui.components.CorporateColors
import com.mustafa.sinavtakvim.ui.components.DividerLine
import com.mustafa.sinavtakvim.ui.components.ResponsiveBox
import com.mustafa.sinavtakvim.ui.components.StatusPill
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject

class LoginScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val authRepository = koinInject<AuthRepository>()
        val examRepository = koinInject<ExamRepository>()
        val scope = rememberCoroutineScope()

        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var role by remember { mutableStateOf(UserRole.ADMIN) }
        var isLoading by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf<String?>(null) }

        ResponsiveBox(Modifier.fillMaxSize(), breakpoint = 900.dp) { isDesktop ->
            if (isDesktop) {
                // Desktop Split Layout
                Row(Modifier.fillMaxSize()) {
                    // Left side: Brand/Hero
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .background(
                                Brush.verticalGradient(
                                    listOf(CorporateColors.Primary, CorporateColors.Steel)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(48.dp)) {
                            Box(
                                modifier = Modifier.size(80.dp).background(Color.White.copy(alpha = 0.2f), MaterialTheme.shapes.large),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("ST", color = Color.White, style = MaterialTheme.typography.h1)
                            }
                            Spacer(Modifier.height(24.dp))
                            Text("Sınav Takvim Sistemi", color = Color.White, style = MaterialTheme.typography.h1)
                            Text(
                                "Fakülte Akademik Planlama ve Koordinasyon Platformu",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.h3,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                            Spacer(Modifier.height(48.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                FeatureBadge("Hızlı Planlama")
                                FeatureBadge("Mobil Uyumlu")
                                FeatureBadge("Güvenli Veri")
                            }
                        }
                    }

                    // Right side: Login Form
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight().background(CorporateColors.Background),
                        contentAlignment = Alignment.Center
                    ) {
                        LoginForm(
                            email = email,
                            onEmailChange = { email = it },
                            password = password,
                            onPasswordChange = { password = it },
                            role = role,
                            onRoleChange = { 
                                role = it
                            },
                            isLoading = isLoading,
                            message = message,
                            onLogin = {
                                isLoading = true
                                message = null
                                scope.launch {
                                    val result = authRepository.login(email, password, role)
                                    isLoading = false
                                    if (result.isSuccess) {
                                        val userId = authRepository.currentUserId()
                                        registerDeviceForNotifications(
                                            userId = userId,
                                            examRepository = examRepository,
                                            credentials = NotificationRegistrationCredentials(email, password, authRepository.currentRole())
                                        )
                                        navigator.replaceAll(MainScreen(authRepository.currentRole(), userId))
                                    } else {
                                        message = result.exceptionOrNull()?.message ?: "Giriş başarısız."
                                    }
                                }
                            }
                        )
                    }
                }
            } else {
                // Mobile Layout
                Box(
                    modifier = Modifier.fillMaxSize().background(CorporateColors.Background),
                    contentAlignment = Alignment.Center
                ) {
                    LoginForm(
                        email = email,
                        onEmailChange = { email = it },
                        password = password,
                        onPasswordChange = { password = it },
                        role = role,
                        onRoleChange = { 
                            role = it
                        },
                        isLoading = isLoading,
                        message = message,
                        onLogin = {
                            isLoading = true
                            message = null
                            scope.launch {
                                val result = authRepository.login(email, password, role)
                                isLoading = false
                                if (result.isSuccess) {
                                    val userId = authRepository.currentUserId()
                                    registerDeviceForNotifications(
                                        userId = userId,
                                        examRepository = examRepository,
                                        credentials = NotificationRegistrationCredentials(email, password, authRepository.currentRole())
                                    )
                                    navigator.replaceAll(MainScreen(authRepository.currentRole(), userId))
                                } else {
                                    message = result.exceptionOrNull()?.message ?: "Giriş başarısız."
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun FeatureBadge(text: String) {
        Surface(
            color = Color.White.copy(alpha = 0.1f),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(text, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp)
        }
    }

    @Composable
    private fun LoginForm(
        email: String,
        onEmailChange: (String) -> Unit,
        password: String,
        onPasswordChange: (String) -> Unit,
        role: UserRole,
        onRoleChange: (UserRole) -> Unit,
        isLoading: Boolean,
        message: String?,
        onLogin: () -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 450.dp).padding(24.dp),
            shape = MaterialTheme.shapes.large,
            elevation = 8.dp,
            backgroundColor = CorporateColors.Surface
        ) {
            Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Oturum Açın", style = MaterialTheme.typography.h2, color = CorporateColors.Ink)
                Text("Devam etmek için kurumsal bilgilerinizi girin.", style = MaterialTheme.typography.body2, color = CorporateColors.Muted)

                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RoleButton("Yönetici", role == UserRole.ADMIN, Modifier.weight(1f)) { onRoleChange(UserRole.ADMIN) }
                    RoleButton("Gözetmen", role == UserRole.PROCTOR, Modifier.weight(1f)) { onRoleChange(UserRole.PROCTOR) }
                }

                DividerLine()

                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = { Text("E-posta") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Şifre") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

                message?.let {
                    Text(it, style = MaterialTheme.typography.body2, color = CorporateColors.Risk)
                }

                Button(
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !isLoading,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(backgroundColor = CorporateColors.Primary)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Giriş Yap", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "© 2026 Fakülte Sınav Yönetim Sistemi",
                    style = MaterialTheme.typography.caption,
                    color = CorporateColors.Muted,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }

    @Composable
    private fun RoleButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
        if (selected) {
            Button(
                onClick = onClick,
                modifier = modifier.height(44.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(backgroundColor = CorporateColors.PrimarySoft),
                elevation = ButtonDefaults.elevation(0.dp)
            ) {
                Text(text, color = CorporateColors.Primary, fontWeight = FontWeight.Bold)
            }
        } else {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier.height(44.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(text, color = CorporateColors.Muted)
            }
        }
    }
}
