@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
package com.mustafa.sinavtakvim.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mustafa.sinavtakvim.shared.data.repository.ExamRepository
import com.mustafa.sinavtakvim.shared.models.User
import com.mustafa.sinavtakvim.shared.utils.toImageBitmap
import com.mustafa.sinavtakvim.ui.components.CorporateCard
import com.mustafa.sinavtakvim.ui.components.CorporateColors
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
class ProfileScreen(private val userId: String) : Screen {
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
            user = loadedUser ?: User(uid = userId, name = "", email = "", role = com.mustafa.sinavtakvim.shared.models.UserRole.PROCTOR)
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

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Profil Ayarları", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = Color.White)
                        }
                    },
                    backgroundColor = CorporateColors.Primary,
                    elevation = 0.dp
                )
            }
        ) { paddingValues ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(CorporateColors.Background)
            ) {
                val isDesktop = this.maxWidth > 800.dp
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(if (isDesktop) 40.dp else 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = CorporateColors.Primary)
                        }
                    } else {
                        // Responsive container
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 1000.dp)
                            .fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = Color.White,
                        elevation = if (isDesktop) 4.dp else 0.dp
                    ) {
                        if (isDesktop) {
                            DesktopProfileLayout(
                                name = name,
                                onNameChange = { name = it },
                                email = email,
                                onEmailChange = { email = it },
                                phone = phone,
                                onPhoneChange = { phone = it },
                                password = password,
                                onPasswordChange = { password = it },
                                preferences = preferences,
                                selectedImageBitmap = selectedImageBitmap,
                                onPickImage = { launcher.launch() },
                                onSave = {
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
                                            message = "Profil bilgileriniz başarıyla güncellendi."
                                        }
                                    }
                                },
                                message = message
                            )
                        } else {
                            MobileProfileLayout(
                                name = name,
                                onNameChange = { name = it },
                                email = email,
                                onEmailChange = { email = it },
                                phone = phone,
                                onPhoneChange = { phone = it },
                                password = password,
                                onPasswordChange = { password = it },
                                preferences = preferences,
                                selectedImageBitmap = selectedImageBitmap,
                                onPickImage = { launcher.launch() },
                                onSave = {
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
                                            message = "Profil bilgileriniz başarıyla güncellendi."
                                        }
                                    }
                                },
                                message = message
                            )
                        }
                    }
                }
            }
        }
    }
}

    @Composable
    private fun DesktopProfileLayout(
        name: String, onNameChange: (String) -> Unit,
        email: String, onEmailChange: (String) -> Unit,
        phone: String, onPhoneChange: (String) -> Unit,
        password: String, onPasswordChange: (String) -> Unit,
        preferences: SnapshotStateMap<String, Boolean>,
        selectedImageBitmap: ImageBitmap?,
        onPickImage: () -> Unit,
        onSave: () -> Unit,
        message: String
    ) {
        Row(
            modifier = Modifier.padding(32.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(40.dp)
        ) {
            // Left Side: Profile Picture
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(200.dp)) {
                ProfilePictureSection(selectedImageBitmap, onPickImage)
                Spacer(Modifier.height(24.dp))
                Text(name.ifBlank { "İsimsiz Kullanıcı" }, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold, color = CorporateColors.Ink)
                Text(email.ifBlank { "E-posta tanımlanmamış" }, style = MaterialTheme.typography.body2, color = CorporateColors.Muted)
                Spacer(Modifier.height(24.dp))
                Text("Profil Fotoğrafı", style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Medium)
                Text("Değiştirmek için tıklayın", style = MaterialTheme.typography.caption, color = CorporateColors.Muted)
            }

            // Right Side: Form Fields
            Column(modifier = Modifier.weight(1f)) {
                Text("Kişisel Bilgiler", style = MaterialTheme.typography.h5, color = CorporateColors.Primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Adınız, e-posta adresiniz ve şifreniz", style = MaterialTheme.typography.body2, color = CorporateColors.Muted)
                Spacer(Modifier.height(24.dp))
                
                ProfileForm(
                    name, onNameChange,
                    email, onEmailChange,
                    phone, onPhoneChange,
                    password, onPasswordChange,
                    onSave, message
                )

                Spacer(Modifier.height(40.dp))
                DividerLine()
                Spacer(Modifier.height(32.dp))

                Text("Uygulama Tercihleri", style = MaterialTheme.typography.h5, color = CorporateColors.Primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))

                PreferenceItem("Bildirimler", "Sınav değişikliklerini anında bildir", preferences["notifications"] ?: true) { preferences["notifications"] = it }
                PreferenceItem("Koyu Tema", "Göz yorgunluğunu azaltmak için karanlık mod", preferences["darkTheme"] ?: false) { preferences["darkTheme"] = it }
                PreferenceItem("İki Faktörlü Doğrulama", "Hesap güvenliğini artırın", preferences["2fa"] ?: false) { preferences["2fa"] = it }
            }
        }
    }

    @Composable
    private fun PreferenceItem(title: String, subtitle: String, value: Boolean, onValueChange: (Boolean) -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = CorporateColors.Ink)
                Text(subtitle, style = MaterialTheme.typography.caption, color = CorporateColors.Muted)
            }
            Switch(
                checked = value,
                onCheckedChange = onValueChange,
                colors = SwitchDefaults.colors(checkedThumbColor = CorporateColors.Primary)
            )
        }
    }

    @Composable
    private fun DividerLine() {
        Box(Modifier.fillMaxWidth().height(1.dp).background(CorporateColors.Muted.copy(alpha = 0.2f)))
    }

    @Composable
    private fun MobileProfileLayout(
        name: String, onNameChange: (String) -> Unit,
        email: String, onEmailChange: (String) -> Unit,
        phone: String, onPhoneChange: (String) -> Unit,
        password: String, onPasswordChange: (String) -> Unit,
        preferences: SnapshotStateMap<String, Boolean>,
        selectedImageBitmap: ImageBitmap?,
        onPickImage: () -> Unit,
        onSave: () -> Unit,
        message: String
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProfilePictureSection(selectedImageBitmap, onPickImage)
            Spacer(Modifier.height(16.dp))
            Text(name.ifBlank { "İsimsiz Kullanıcı" }, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold, color = CorporateColors.Ink)
            Text(email.ifBlank { "E-posta tanımlanmamış" }, style = MaterialTheme.typography.body2, color = CorporateColors.Muted)
            Spacer(Modifier.height(24.dp))
            
            Text("Hesap Bilgileri", style = MaterialTheme.typography.h6, color = CorporateColors.Primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            ProfileForm(
                name, onNameChange,
                email, onEmailChange,
                phone, onPhoneChange,
                password, onPasswordChange,
                onSave, message
            )

            Spacer(Modifier.height(32.dp))
            DividerLine()
            Spacer(Modifier.height(24.dp))

            Text("Tercihler", style = MaterialTheme.typography.h6, color = CorporateColors.Primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            PreferenceItem("Bildirimler", "Anlık uyarılar", preferences["notifications"] ?: true) { preferences["notifications"] = it }
            PreferenceItem("Koyu Tema", "Karanlık mod", preferences["darkTheme"] ?: false) { preferences["darkTheme"] = it }
            PreferenceItem("Güvenlik", "İki faktörlü giriş", preferences["2fa"] ?: false) { preferences["2fa"] = it }
        }
    }

    @Composable
    private fun ProfilePictureSection(bitmap: ImageBitmap?, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(CorporateColors.Muted.copy(alpha = 0.1f))
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
                Icon(Icons.Default.Person, null, modifier = Modifier.size(60.dp), tint = CorporateColors.Muted)
            }
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CorporateColors.Primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(20.dp), tint = Color.White)
            }
        }
    }

    @Composable
    private fun ProfileForm(
        name: String, onNameChange: (String) -> Unit,
        email: String, onEmailChange: (String) -> Unit,
        phone: String, onPhoneChange: (String) -> Unit,
        password: String, onPasswordChange: (String) -> Unit,
        onSave: () -> Unit,
        message: String
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Ad Soyad") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text("E-posta Adresi") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )
            OutlinedTextField(
                value = phone,
                onValueChange = onPhoneChange,
                label = { Text("Telefon Numarası") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Yeni Şifre") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )
            
            Spacer(Modifier.height(16.dp))
            
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = CorporateColors.Primary),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Değişiklikleri Kaydet", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            
            if (message.isNotEmpty()) {
                Text(
                    text = message,
                    color = CorporateColors.Success,
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
                )
            }
        }
    }
}
