@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
package com.mustafa.sinavtakvim.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mustafa.sinavtakvim.ThemeState
import com.mustafa.sinavtakvim.shared.data.repository.ExamRepository
import com.mustafa.sinavtakvim.shared.models.User
import com.mustafa.sinavtakvim.shared.utils.toImageBitmap
import com.mustafa.sinavtakvim.ui.components.*
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
class ProfileScreen(
    private val userId: String,
    private val onUserUpdated: (User) -> Unit = {}
) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repository = koinInject<ExamRepository>()
        val scope = rememberCoroutineScope()
        
        var user by remember { mutableStateOf<User?>(null) }
        var name by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var profileImageUrl by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }
        val preferences = remember { mutableStateMapOf<String, Boolean>() }
        var isLoading by remember { mutableStateOf(true) }
        
        var selectedImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

        val launcher = rememberFilePickerLauncher(
            type = PickerType.Image,
            mode = PickerMode.Single,
            title = "Profil Fotoğrafı Seç",
        ) { platformFile: PlatformFile? ->
            scope.launch {
                platformFile?.let { file ->
                    try {
                        val bytes = file.readBytes()
                        selectedImageBitmap = bytes.toImageBitmap()
                        profileImageUrl = Base64.encode(bytes)
                    } catch (_: Exception) {}
                }
            }
        }

        LaunchedEffect(userId) {
            isLoading = true
            val allUsers = repository.getUsers()
            val loadedUser = allUsers.firstOrNull { it.uid == userId }
            user = loadedUser ?: User(uid = userId)
            isLoading = false
            
            user?.let { u ->
                name = u.name
                email = u.email
                phone = u.phone
                password = u.password
                profileImageUrl = u.profileImageUrl
                preferences.clear()
                preferences.putAll(u.preferences)
                
                if (profileImageUrl.isNotBlank()) {
                    try {
                        val bytes = Base64.decode(profileImageUrl)
                        selectedImageBitmap = bytes.toImageBitmap()
                    } catch (_: Exception) {}
                }
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CorporateColors.Primary)
            }
        } else {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CorporateColors.Background)
                    .verticalScroll(scrollState)
            ) {

                // Profile Content Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profile Picture overlapping the banner
                    ProfilePictureSection(selectedImageBitmap) { launcher.launch() }
                    
                    Spacer(Modifier.height(12.dp))
                    Text(name.ifBlank { "İsimsiz Kullanıcı" }, style = MaterialTheme.typography.h2, color = CorporateColors.Ink)
                    Text(email.ifBlank { "E-posta girilmemiş" }, style = MaterialTheme.typography.body2, color = CorporateColors.Muted)
                    
                    Spacer(Modifier.height(32.dp))

                    // Responsive Column
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val maxWidth = maxWidth
                        val isWide = maxWidth > 800.dp
                        
                        if (isWide) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                Column(Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                    ProfileInfoCard(name, { name = it }, email, { email = it }, phone, { phone = it }, password, { password = it })
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                    PreferencesCard(preferences, { key, val_ -> 
                                        preferences[key] = val_
                                        if (key == "darkTheme") ThemeState.darkThemeEnabled = val_
                                    })
                                    SaveActionCard(onSave = {
                                        user?.let { currentUser ->
                                            scope.launch {
                                                val updatedUser = currentUser.copy(
                                                    name = name.trim(),
                                                    email = email.trim(),
                                                    phone = phone.trim(),
                                                    password = password.trim(),
                                                    profileImageUrl = profileImageUrl.trim(),
                                                    preferences = preferences.toMap()
                                                )
                                                repository.addUser(updatedUser)
                                                user = updatedUser
                                                message = "Profil başarıyla güncellendi."
                                            }
                                        }
                                    }, message = message)
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                ProfileInfoCard(name, { name = it }, email, { email = it }, phone, { phone = it }, password, { password = it })
                                PreferencesCard(preferences, { key, val_ -> 
                                    preferences[key] = val_
                                    if (key == "darkTheme") ThemeState.darkThemeEnabled = val_
                                })
                                SaveActionCard(onSave = {
                                    user?.let { currentUser ->
                                        scope.launch {
                                            val updatedUser = currentUser.copy(
                                                name = name.trim(),
                                                email = email.trim(),
                                                phone = phone.trim(),
                                                password = password.trim(),
                                                profileImageUrl = profileImageUrl.trim(),
                                                preferences = preferences.toMap()
                                            )
                                            val success = repository.addUser(updatedUser)
                                            if (success) {
                                                user = updatedUser
                                                onUserUpdated(updatedUser)
                                                message = "Profil başarıyla güncellendi."
                                            } else {
                                                message = "Hata: Profil kaydedilemedi! (Fotoğraf boyutu çok büyük olabilir)"
                                            }
                                        }
                                    }
                                }, message = message)
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }

    @Composable
    private fun ProfilePictureSection(bitmap: ImageBitmap?, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .border(4.dp, Color.White, CircleShape)
                .background(Color.White)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.Person, null, modifier = Modifier.size(50.dp), tint = CorporateColors.Muted)
            }
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(CorporateColors.Steel)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(16.dp), tint = Color.White)
            }
        }
    }

    @Composable
    private fun ProfileInfoCard(
        name: String, onNameChange: (String) -> Unit,
        email: String, onEmailChange: (String) -> Unit,
        phone: String, onPhoneChange: (String) -> Unit,
        password: String, onPasswordChange: (String) -> Unit
    ) {
        CorporateCard(Modifier.fillMaxWidth()) {
            Text("Hesap Bilgileri", style = MaterialTheme.typography.h3, color = CorporateColors.Ink)
            Text("Kişisel bilgilerinizi buradan güncelleyebilirsiniz.", style = MaterialTheme.typography.body2, color = CorporateColors.Muted)
            
            Spacer(Modifier.height(24.dp))
            
            ProfileTextField("Ad Soyad", name, onNameChange, Icons.Default.Person)
            Spacer(Modifier.height(16.dp))
            ProfileTextField("E-posta", email, onEmailChange, Icons.Default.Email)
            Spacer(Modifier.height(16.dp))
            ProfileTextField("Telefon", phone, onPhoneChange, Icons.Default.Phone)
            Spacer(Modifier.height(16.dp))
            ProfileTextField("Yeni Şifre", password, onPasswordChange, Icons.Default.Lock, isPassword = true)
        }
    }

    @Composable
    private fun ProfileTextField(
        label: String,
        value: String,
        onValueChange: (String) -> Unit,
        icon: ImageVector,
        isPassword: Boolean = false
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.caption, color = CorporateColors.Muted, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                leadingIcon = { Icon(icon, null, tint = CorporateColors.Primary, modifier = Modifier.size(20.dp)) },
                visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = CorporateColors.Primary,
                    unfocusedBorderColor = CorporateColors.Border
                )
            )
        }
    }

    @Composable
    private fun PreferencesCard(
        preferences: Map<String, Boolean>,
        onToggle: (String, Boolean) -> Unit
    ) {
        CorporateCard(Modifier.fillMaxWidth()) {
            Text("Tercihler", style = MaterialTheme.typography.h3, color = CorporateColors.Ink)
            Spacer(Modifier.height(16.dp))
            
            PreferenceSwitch("Anlık Bildirimler", "Sınav güncellemeleri hakkında bilgi al", preferences["notifications"] ?: true) { onToggle("notifications", it) }
            DividerLine(Modifier.padding(vertical = 8.dp))
            PreferenceSwitch("Koyu Tema", "Uygulamayı karanlık modda kullan", preferences["darkTheme"] ?: false) { onToggle("darkTheme", it) }
            DividerLine(Modifier.padding(vertical = 8.dp))
            PreferenceSwitch("Veri Tasarrufu", "Daha az hücresel veri kullan", preferences["dataSaver"] ?: false) { onToggle("dataSaver", it) }
        }
    }

    @Composable
    private fun PreferenceSwitch(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.body1, fontWeight = FontWeight.Bold, color = CorporateColors.Ink)
                Text(subtitle, style = MaterialTheme.typography.caption, color = CorporateColors.Muted)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = CorporateColors.Primary))
        }
    }

    @Composable
    private fun SaveActionCard(onSave: () -> Unit, message: String) {
        CorporateCard(Modifier.fillMaxWidth()) {
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(backgroundColor = CorporateColors.Primary)
            ) {
                Icon(Icons.Default.Save, null, tint = Color.White)
                Spacer(Modifier.width(12.dp))
                Text("Değişiklikleri Kaydet", color = Color.White, fontWeight = FontWeight.Bold)
            }
            
            if (message.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(message, color = CorporateColors.Success, style = MaterialTheme.typography.body2)
                }
            }
        }
    }
}
