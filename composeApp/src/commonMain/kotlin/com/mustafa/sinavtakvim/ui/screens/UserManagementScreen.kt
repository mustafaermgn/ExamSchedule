package com.mustafa.sinavtakvim.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mustafa.sinavtakvim.shared.data.repository.ExamRepository
import com.mustafa.sinavtakvim.shared.models.User
import com.mustafa.sinavtakvim.shared.models.UserRole
import com.mustafa.sinavtakvim.ui.components.CorporateCard
import com.mustafa.sinavtakvim.ui.components.CorporateColors
import com.mustafa.sinavtakvim.ui.components.ResponsiveBox
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class UserManagementScreen : Screen {
    @Composable
    override fun Content() {
        val repository = koinInject<ExamRepository>()
        val scope = rememberCoroutineScope()
        val navigator = LocalNavigator.currentOrThrow
        
        var users by remember { mutableStateOf<List<User>>(emptyList()) }
        var searchQuery by remember { mutableStateOf("") }
        var selectedRoleFilter by remember { mutableStateOf<UserRole?>(null) }
        var showAddDialog by remember { mutableStateOf(false) }
        
        LaunchedEffect(Unit) {
            users = repository.getUsers()
        }

        val filteredUsers = users.filter { user ->
            (searchQuery.isEmpty() || user.name.contains(searchQuery, ignoreCase = true) || user.email.contains(searchQuery, ignoreCase = true)) &&
            (selectedRoleFilter == null || user.role == selectedRoleFilter)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Kullanıcı Yönetimi", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = Color.White)
                        }
                    },
                    backgroundColor = CorporateColors.Primary,
                    elevation = 0.dp
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    backgroundColor = CorporateColors.Primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, "Ekle")
                }
            }
        ) { padding ->
            ResponsiveBox(
                modifier = Modifier.fillMaxSize().padding(padding).background(CorporateColors.Background),
                breakpoint = 800.dp
            ) { isDesktop ->
                Column(Modifier.fillMaxSize().padding(if (isDesktop) 32.dp else 16.dp)) {
                    // Summary and Filters
                    CorporateCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Sistem Kullanıcıları", style = MaterialTheme.typography.h3, color = CorporateColors.Ink)
                                    Text("${users.size} kayıtlı kullanıcı bulundu", style = MaterialTheme.typography.body2, color = CorporateColors.Muted)
                                }
                                
                                if (isDesktop) {
                                    OutlinedButton(
                                        onClick = { showAddDialog = true },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CorporateColors.Primary)
                                    ) {
                                        Icon(Icons.Default.GroupAdd, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Yeni Kullanıcı")
                                    }
                                }
                            }

                            Divider(color = CorporateColors.Border.copy(alpha = 0.5f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("İsim veya e-posta ile ara...") },
                                    modifier = Modifier.weight(1f),
                                    leadingIcon = { Icon(Icons.Default.Search, null, tint = CorporateColors.Muted) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = TextFieldDefaults.outlinedTextFieldColors(
                                        focusedBorderColor = CorporateColors.Primary,
                                        unfocusedBorderColor = CorporateColors.Border
                                    )
                                )

                                if (isDesktop) {
                                    FilterChip("Hepsi", selectedRoleFilter == null) { selectedRoleFilter = null }
                                    FilterChip("Yöneticiler", selectedRoleFilter == UserRole.ADMIN) { selectedRoleFilter = UserRole.ADMIN }
                                    FilterChip("Gözetmenler", selectedRoleFilter == UserRole.PROCTOR) { selectedRoleFilter = UserRole.PROCTOR }
                                }
                            }

                            if (!isDesktop) {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    FilterChip("Hepsi", selectedRoleFilter == null) { selectedRoleFilter = null }
                                    FilterChip("Yöneticiler", selectedRoleFilter == UserRole.ADMIN) { selectedRoleFilter = UserRole.ADMIN }
                                    FilterChip("Gözetmenler", selectedRoleFilter == UserRole.PROCTOR) { selectedRoleFilter = UserRole.PROCTOR }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // User List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(filteredUsers) { user ->
                            UserListItem(
                                user = user,
                                onDelete = {
                                    scope.launch {
                                        repository.deleteUser(user.uid)
                                        users = repository.getUsers()
                                    }
                                }
                            )
                        }
                    }
                }
            }
            
            if (showAddDialog) {
                AddUserDialog(
                    onDismiss = { showAddDialog = false },
                    onConfirm = { newUser ->
                        scope.launch {
                            repository.addUser(newUser)
                            users = repository.getUsers()
                            showAddDialog = false
                        }
                    }
                )
            }
        }
    }

    @Composable
    private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
        Surface(
            modifier = Modifier.clickable { onClick() },
            shape = RoundedCornerShape(12.dp),
            color = if (selected) CorporateColors.Primary else CorporateColors.Surface,
            border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, CorporateColors.Border)
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = if (selected) Color.White else CorporateColors.Ink,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp
            )
        }
    }

    @Composable
    private fun UserListItem(user: User, onDelete: () -> Unit) {
        CorporateCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = (if (user.role == UserRole.ADMIN) CorporateColors.Primary else CorporateColors.Steel).copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = user.name.take(1).uppercase(),
                            style = MaterialTheme.typography.h3,
                            color = if (user.role == UserRole.ADMIN) CorporateColors.Primary else CorporateColors.Steel
                        )
                    }
                }
                
                Spacer(Modifier.width(16.dp))
                
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(user.name, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold, color = CorporateColors.Ink)
                        Spacer(Modifier.width(8.dp))
                        Badge(
                            text = if (user.role == UserRole.ADMIN) "Yönetici" else "Gözetmen",
                            color = if (user.role == UserRole.ADMIN) CorporateColors.Primary else CorporateColors.Steel
                        )
                    }
                    Text(user.email, style = MaterialTheme.typography.body2, color = CorporateColors.Muted)
                }
                
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, "Sil", tint = CorporateColors.Risk.copy(alpha = 0.7f))
                }
            }
        }
    }

    @Composable
    private fun Badge(text: String, color: Color) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = color.copy(alpha = 0.1f)
        ) {
            Text(
                text = text.uppercase(),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }
    }

    @Composable
    private fun AddUserDialog(onDismiss: () -> Unit, onConfirm: (User) -> Unit) {
        var name by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var role by remember { mutableStateOf(UserRole.PROCTOR) }
        var password by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(16.dp),
            title = { 
                Text("Yeni Kullanıcı Ekle", style = MaterialTheme.typography.h2, color = CorporateColors.Ink) 
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = name, 
                        onValueChange = { name = it }, 
                        label = { Text("Ad Soyad") }, 
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = email, 
                        onValueChange = { email = it }, 
                        label = { Text("E-posta") }, 
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = password, 
                        onValueChange = { password = it }, 
                        label = { Text("Şifre") }, 
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    Text("Kullanıcı Rolü", style = MaterialTheme.typography.subtitle2, color = CorporateColors.Ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { role = UserRole.PROCTOR }) {
                            RadioButton(selected = role == UserRole.PROCTOR, onClick = { role = UserRole.PROCTOR }, colors = RadioButtonDefaults.colors(selectedColor = CorporateColors.Primary))
                            Text("Gözetmen")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { role = UserRole.ADMIN }) {
                            RadioButton(selected = role == UserRole.ADMIN, onClick = { role = UserRole.ADMIN }, colors = RadioButtonDefaults.colors(selectedColor = CorporateColors.Primary))
                            Text("Yönetici")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newUser = User(
                            uid = "u-" + (0..9999).random(),
                            name = name,
                            email = email,
                            role = role,
                            password = password
                        )
                        onConfirm(newUser)
                    },
                    enabled = name.isNotBlank() && email.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = CorporateColors.Primary)
                ) {
                    Text("Kullanıcıyı Kaydet", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { 
                    Text("İptal", color = CorporateColors.Muted) 
                }
            }
        )
    }
}
