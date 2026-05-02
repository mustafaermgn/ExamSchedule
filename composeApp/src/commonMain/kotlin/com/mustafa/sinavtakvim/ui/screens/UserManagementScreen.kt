package com.mustafa.sinavtakvim.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import com.mustafa.sinavtakvim.shared.data.repository.ExamRepository
import com.mustafa.sinavtakvim.shared.models.User
import com.mustafa.sinavtakvim.shared.models.UserRole
import com.mustafa.sinavtakvim.ui.components.CorporateCard
import com.mustafa.sinavtakvim.ui.components.CorporateColors
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class UserManagementScreen : Screen {
    @Composable
    override fun Content() {
        val repository = koinInject<ExamRepository>()
        val scope = rememberCoroutineScope()
        
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

        BoxWithConstraints(Modifier.fillMaxSize().background(CorporateColors.Background)) {
            val isDesktop = maxWidth > 800.dp
            
            Column(Modifier.fillMaxSize().padding(if (isDesktop) 32.dp else 16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Kullanıcı Yönetimi", style = MaterialTheme.typography.h4, color = CorporateColors.Primary, fontWeight = FontWeight.Bold)
                        Text("${users.size} Toplam Kullanıcı", style = MaterialTheme.typography.body2, color = CorporateColors.Muted)
                    }
                    
                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(backgroundColor = CorporateColors.Primary),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.Add, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Yeni Kullanıcı Ekle", color = Color.White)
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Filters
                CorporateCard {
                    if (isDesktop) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("İsim veya e-posta ile ara...") },
                                modifier = Modifier.weight(1f),
                                leadingIcon = { Icon(Icons.Default.Search, null, tint = CorporateColors.Muted) },
                                shape = MaterialTheme.shapes.medium
                            )

                            FilterChip("Hepsi", selectedRoleFilter == null) { selectedRoleFilter = null }
                            FilterChip("Yöneticiler", selectedRoleFilter == UserRole.ADMIN) { selectedRoleFilter = UserRole.ADMIN }
                            FilterChip("Gözetmenler", selectedRoleFilter == UserRole.PROCTOR) { selectedRoleFilter = UserRole.PROCTOR }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("İsim veya e-posta ile ara...") },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { Icon(Icons.Default.Search, null, tint = CorporateColors.Muted) },
                                shape = MaterialTheme.shapes.medium
                            )
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
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            shape = MaterialTheme.shapes.medium,
            color = if (selected) CorporateColors.Primary else CorporateColors.Surface,
            border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, CorporateColors.Muted.copy(alpha = 0.3f))
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = if (selected) Color.White else CorporateColors.Ink,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp
            )
        }
    }

    @Composable
    private fun UserListItem(user: User, onDelete: () -> Unit) {
        CorporateCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = (if (user.role == UserRole.ADMIN) CorporateColors.Primary else CorporateColors.Steel).copy(alpha = 0.1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(user.name.take(1).uppercase(), color = if (user.role == UserRole.ADMIN) CorporateColors.Primary else CorporateColors.Steel, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Spacer(Modifier.width(16.dp))
                    
                    Column {
                        Text(user.name, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
                        Text(user.email, style = MaterialTheme.typography.caption, color = CorporateColors.Muted)
                    }
                    
                    Spacer(Modifier.width(16.dp))
                    
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = (if (user.role == UserRole.ADMIN) CorporateColors.Primary else CorporateColors.Steel).copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = if (user.role == UserRole.ADMIN) "Yönetici" else "Gözetmen",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = if (user.role == UserRole.ADMIN) CorporateColors.Primary else CorporateColors.Steel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = CorporateColors.Risk)
                }
            }
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
            title = { Text("Yeni Kullanıcı Ekle", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Ad Soyad") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("E-posta") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Şifre") }, modifier = Modifier.fillMaxWidth())
                    
                    Text("Kullanıcı Rolü", style = MaterialTheme.typography.subtitle2)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { role = UserRole.PROCTOR }) {
                            RadioButton(selected = role == UserRole.PROCTOR, onClick = { role = UserRole.PROCTOR })
                            Text("Gözetmen")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { role = UserRole.ADMIN }) {
                            RadioButton(selected = role == UserRole.ADMIN, onClick = { role = UserRole.ADMIN })
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
                    enabled = name.isNotBlank() && email.isNotBlank()
                ) {
                    Text("Ekle")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("İptal") }
            }
        )
    }
}
