package com.mustafa.sinavtakvim.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mustafa.sinavtakvim.shared.data.repository.ExamRepository
import com.mustafa.sinavtakvim.shared.models.*
import com.mustafa.sinavtakvim.shared.utils.*
import com.mustafa.sinavtakvim.ui.components.*
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

class AdminDataScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repository = koinInject<ExamRepository>()
        val scope = rememberCoroutineScope()

        var courses by remember { mutableStateOf<List<Course>>(emptyList()) }
        var rooms by remember { mutableStateOf<List<Room>>(emptyList()) }
        var users by remember { mutableStateOf<List<User>>(emptyList()) }
        var message by remember { mutableStateOf("") }
        var activeTab by remember { mutableIntStateOf(0) }
        var busy by remember { mutableStateOf(false) }

        // Form states
        var courseCode by remember { mutableStateOf("") }
        var courseName by remember { mutableStateOf("") }
        var semester by remember { mutableStateOf("") }
        var departmentId by remember { mutableStateOf("BIL") }
        var instructorName by remember { mutableStateOf("") }

        var roomName by remember { mutableStateOf("") }
        var roomCapacity by remember { mutableStateOf("") }
        var roomFloor by remember { mutableStateOf("") }
        var roomBuilding by remember { mutableStateOf("Mühendislik Fakültesi") }
        var roomLatitude by remember { mutableStateOf("") }
        var roomLongitude by remember { mutableStateOf("") }

        var selectedCourseId by remember { mutableStateOf("") }
        var editingCourseId by remember { mutableStateOf<String?>(null) }
        var editingRoomId by remember { mutableStateOf<String?>(null) }

        suspend fun load(forceRefresh: Boolean = false) {
            courses = repository.getCourses(forceRefresh).sortedBy { it.code }
            rooms = repository.getRooms(forceRefresh).sortedBy { it.name }
            users = repository.getUsers(forceRefresh).sortedBy { it.name }
            if (selectedCourseId.isBlank()) selectedCourseId = courses.firstOrNull()?.id.orEmpty()
        }

        LaunchedEffect(Unit) { load() }

        val importLauncher = rememberFilePickerLauncher(
            type = PickerType.File(extensions = listOf("xlsx", "csv", "tsv")),
            mode = PickerMode.Single,
            title = "Öğrenci listesi seç"
        ) { file: PlatformFile? ->
            if (file != null) {
                scope.launch {
                    busy = true
                    try {
                        val courseId = selectedCourseId
                        if (courseId.isBlank()) {
                            message = "Önce öğrenci listesi yüklenecek dersi seçin."
                            return@launch
                        }
                        val result = withContext(Dispatchers.Default) {
                            parseStudentSpreadsheet(file.readBytes(), file.name)
                        }
                        if (result.students.isEmpty()) {
                            message = "Dosya boş veya format hatalı. (Öğrenci No ve Ad Soyad sütunlarını kontrol edin)"
                        } else {
                            repository.enrollStudentsInCourse(courseId, result.students)
                            message = "${result.students.size} kayıt güncellendi/eklendi."
                            load(true)
                        }
                    } catch (e: Exception) {
                        message = "Hata: ${e.message}"
                    } finally {
                        busy = false
                    }
                }
            }
        }

        val backupSaver = rememberFileSaverLauncher { file ->
            if (file != null) message = "Yedek kaydedildi."
        }

        val backupLoader = rememberFilePickerLauncher(
            type = PickerType.File(extensions = listOf("json")),
            mode = PickerMode.Single
        ) { file ->
            if (file != null) {
                scope.launch {
                    busy = true
                    try {
                        val backup = decodeAdminDataBackup(file.readBytes())
                        repository.restoreAdminDataBackup(backup)
                        message = "Yedek başarıyla yüklendi."
                        load()
                    } catch (e: Exception) {
                        message = "Geri yükleme hatası: ${e.message}"
                    } finally {
                        busy = false
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CorporateColors.Background)
        ) {
            // Tab Header
            Surface(elevation = 4.dp, color = CorporateColors.Surface) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TabItem("Dersler", Icons.Default.Book, activeTab == 0) { activeTab = 0 }
                        TabItem("Salonlar", Icons.Default.MeetingRoom, activeTab == 1) { activeTab = 1 }
                        TabItem("Gözetmenler", Icons.Default.People, activeTab == 2) { activeTab = 2 }
                        TabItem("Yedekleme", Icons.Default.Backup, activeTab == 3) { activeTab = 3 }
                    }

                    IconButton(onClick = {
                        scope.launch {
                            busy = true
                            load(true)
                            busy = false
                            message = "Veriler güncellendi."
                        }
                    }) {
                        Icon(Icons.Default.Refresh, "Yenile", tint = CorporateColors.Primary)
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                when (activeTab) {
                    0 -> CoursesTab(
                        courses = courses,
                        selectedCourseId = selectedCourseId,
                        onSelectCourse = { selectedCourseId = it },
                        onUploadStudents = { importLauncher.launch() },
                        busy = busy,
                        courseCode = courseCode,
                        onCodeChange = { courseCode = it },
                        courseName = courseName,
                        onNameChange = { courseName = it },
                        semester = semester,
                        onSemChange = { semester = it },
                        department = departmentId,
                        onDeptChange = { departmentId = it },
                        instructor = instructorName,
                        onInsChange = { instructorName = it },
                        isEditing = editingCourseId != null,
                        onEditCourse = { course ->
                            editingCourseId = course.id
                            courseCode = course.code
                            courseName = course.name
                            semester = course.semester.toString()
                            departmentId = course.departmentId
                            instructorName = course.instructorName
                        },
                        onDeleteCourse = { id ->
                            scope.launch {
                                repository.deleteCourse(id)
                                load(true)
                                message = "Ders silindi."
                            }
                        },
                        onCancelEdit = {
                            editingCourseId = null
                            courseCode = ""; courseName = ""; semester = ""; instructorName = ""
                        },
                        onSaveCourse = {
                            scope.launch {
                                val course = Course(
                                    id = editingCourseId ?: courseCode.uppercase(),
                                    code = courseCode.uppercase(),
                                    name = courseName,
                                    semester = semester.toIntOrNull() ?: 1,
                                    departmentId = departmentId,
                                    instructorName = instructorName
                                )
                                repository.addCourse(course)
                                load(true)
                                message = if (editingCourseId != null) "Ders güncellendi." else "Ders eklendi."
                                editingCourseId = null
                                courseCode = ""; courseName = ""; semester = ""; instructorName = ""
                            }
                        }
                    )
                    1 -> RoomsTab(
                        rooms = rooms,
                        roomName = roomName,
                        onNameChange = { roomName = it },
                        capacity = roomCapacity,
                        onCapChange = { roomCapacity = it },
                        floor = roomFloor,
                        onFloorChange = { roomFloor = it },
                        building = roomBuilding,
                        onBuildingChange = { roomBuilding = it },
                        latitude = roomLatitude,
                        onLatitudeChange = { roomLatitude = it },
                        longitude = roomLongitude,
                        onLongitudeChange = { roomLongitude = it },
                        isEditing = editingRoomId != null,
                        onEditRoom = { room ->
                            editingRoomId = room.id
                            roomName = room.name
                            roomCapacity = room.capacity.toString()
                            roomFloor = room.floor.toString()
                            roomBuilding = room.building
                            roomLatitude = room.latitude.takeIf { it != 0.0 }?.toString().orEmpty()
                            roomLongitude = room.longitude.takeIf { it != 0.0 }?.toString().orEmpty()
                        },
                        onDeleteRoom = { id ->
                            scope.launch {
                                repository.deleteRoom(id)
                                load(true)
                                message = "Salon silindi."
                            }
                        },
                        onCancelEdit = {
                            editingRoomId = null
                            roomName = ""; roomCapacity = ""; roomFloor = ""
                            roomBuilding = "Mühendislik Fakültesi"; roomLatitude = ""; roomLongitude = ""
                        },
                        onSaveRoom = {
                            scope.launch {
                                val room = Room(
                                    id = editingRoomId ?: roomName,
                                    name = roomName,
                                    capacity = roomCapacity.toIntOrNull() ?: 30,
                                    floor = roomFloor.toIntOrNull() ?: 0,
                                    latitude = roomLatitude.replace(',', '.').toDoubleOrNull() ?: 0.0,
                                    longitude = roomLongitude.replace(',', '.').toDoubleOrNull() ?: 0.0,
                                    building = roomBuilding.ifBlank { "Mühendislik Fakültesi" }
                                )
                                repository.addRoom(room)
                                load(true)
                                message = if (editingRoomId != null) "Salon güncellendi." else "Salon eklendi."
                                editingRoomId = null
                                roomName = ""; roomCapacity = ""; roomFloor = ""
                                roomBuilding = "Mühendislik Fakültesi"; roomLatitude = ""; roomLongitude = ""
                            }
                        }
                    )
                    2 -> ProctorsTab(
                        proctors = users.filter { it.role == UserRole.PROCTOR },
                        onManage = { navigator.push(UserManagementScreen()) }
                    )
                    3 -> BackupTab(
                        onBackup = {
                            scope.launch {
                                val backup = repository.createAdminDataBackup()
                                backupSaver.launch(encodeAdminDataBackup(backup), "yedek", "json")
                            }
                        },
                        onRestore = { backupLoader.launch() },
                        onClear = {
                            scope.launch {
                                repository.clearDatabase()
                                load()
                                message = "Veritabanı sıfırlandı."
                            }
                        }
                    )
                }

                if (message.isNotBlank()) {
                    StatusPill(
                        text = message,
                        color = CorporateColors.Primary,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                    )
                    LaunchedEffect(message) {
                        kotlinx.coroutines.delay(3000)
                        message = ""
                    }
                }
            }
        }
    }

    @Composable
    private fun TabItem(title: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon, null,
                tint = if (selected) CorporateColors.Primary else CorporateColors.Muted,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) CorporateColors.Primary else CorporateColors.Muted
            )
            if (selected) {
                Spacer(Modifier.height(4.dp))
                Box(Modifier.width(24.dp).height(2.dp).background(CorporateColors.Primary))
            }
        }
    }

    @Composable
    private fun CoursesTab(
        courses: List<Course>,
        selectedCourseId: String,
        onSelectCourse: (String) -> Unit,
        onUploadStudents: () -> Unit,
        busy: Boolean,
        courseCode: String, onCodeChange: (String) -> Unit,
        courseName: String, onNameChange: (String) -> Unit,
        semester: String, onSemChange: (String) -> Unit,
        department: String, onDeptChange: (String) -> Unit,
        instructor: String, onInsChange: (String) -> Unit,
        isEditing: Boolean,
        onEditCourse: (Course) -> Unit,
        onDeleteCourse: (String) -> Unit,
        onCancelEdit: () -> Unit,
        onSaveCourse: () -> Unit
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                CorporateCard {
                    Text(if (isEditing) "Dersi Düzenle" else "Yeni Ders Ekle", style = MaterialTheme.typography.h3, color = CorporateColors.Ink)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(courseCode, onCodeChange, label = { Text("Kod") }, modifier = Modifier.weight(1f), enabled = !isEditing)
                        OutlinedTextField(semester, onSemChange, label = { Text("Dönem") }, modifier = Modifier.width(80.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(department, onDeptChange, label = { Text("Bölüm") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(courseName, onNameChange, label = { Text("Ders Adı") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(instructor, onInsChange, label = { Text("Sorumlu") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isEditing) {
                            OutlinedButton(onCancelEdit, modifier = Modifier.weight(1f)) {
                                Text("Vazgeç")
                            }
                        }
                        Button(onSaveCourse, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(CorporateColors.Primary)) {
                            Text(if (isEditing) "Güncelle" else "Dersi Kaydet", color = Color.White)
                        }
                    }
                }
            }
            item {
                CorporateCard {
                    SectionTitle("Ders Havuzu")
                    Spacer(Modifier.height(16.dp))
                    if (courses.isEmpty()) {
                        Text("Henüz ders tanımlanmamış.", color = CorporateColors.Muted, modifier = Modifier.padding(vertical = 16.dp))
                    }
                }
            }
            items(courses) { course ->
                CorporateCard(Modifier.padding(horizontal = 4.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selectedCourseId == course.id, onClick = { onSelectCourse(course.id) })
                        Column(Modifier.weight(1f).clickable { onSelectCourse(course.id) }) {
                            Text(course.code, fontWeight = FontWeight.Bold, color = CorporateColors.Ink)
                            Text(course.name, style = MaterialTheme.typography.caption, color = CorporateColors.Muted)
                        }

                        Row {
                            IconButton(onClick = { onEditCourse(course) }) {
                                Icon(Icons.Default.Edit, "Düzenle", tint = CorporateColors.Primary, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { onDeleteCourse(course.id) }) {
                                Icon(Icons.Default.Delete, "Sil", tint = CorporateColors.Risk, modifier = Modifier.size(20.dp))
                            }
                        }

                        StatusPill(
                            text = if (course.studentCount > 0) "${course.studentCount} Öğr." else "Liste Yok",
                            color = if (course.studentCount > 0) CorporateColors.Success else CorporateColors.Amber
                        )
                    }
                }
            }
            item {
                CorporateCard {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onUploadStudents,
                        enabled = selectedCourseId.isNotBlank() && !busy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(CorporateColors.Steel)
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                        else {
                            Icon(Icons.Default.UploadFile, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Seçili Derse Excel Yükle", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun RoomsTab(
        rooms: List<Room>,
        roomName: String, onNameChange: (String) -> Unit,
        capacity: String, onCapChange: (String) -> Unit,
        floor: String, onFloorChange: (String) -> Unit,
        building: String, onBuildingChange: (String) -> Unit,
        latitude: String, onLatitudeChange: (String) -> Unit,
        longitude: String, onLongitudeChange: (String) -> Unit,
        isEditing: Boolean,
        onEditRoom: (Room) -> Unit,
        onDeleteRoom: (String) -> Unit,
        onCancelEdit: () -> Unit,
        onSaveRoom: () -> Unit
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                CorporateCard {
                    Text(if (isEditing) "Salonu Düzenle" else "Yeni Salon Ekle", style = MaterialTheme.typography.h3, color = CorporateColors.Ink)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(roomName, onNameChange, label = { Text("Salon Adı") }, modifier = Modifier.fillMaxWidth(), enabled = !isEditing)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(capacity, onCapChange, label = { Text("Kapasite") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(floor, onFloorChange, label = { Text("Kat") }, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(building, onBuildingChange, label = { Text("Bina / Konum") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(latitude, onLatitudeChange, label = { Text("Enlem") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(longitude, onLongitudeChange, label = { Text("Boylam") }, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isEditing) {
                            OutlinedButton(onCancelEdit, modifier = Modifier.weight(1f)) {
                                Text("Vazgeç")
                            }
                        }
                        Button(onSaveRoom, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(CorporateColors.Primary)) {
                            Text(if (isEditing) "Güncelle" else "Salonu Kaydet", color = Color.White)
                        }
                    }
                }
            }
            items(rooms) { room ->
                CorporateCard(Modifier.padding(horizontal = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(room.name, fontWeight = FontWeight.Bold, color = CorporateColors.Ink)
                            Text("${room.building} · ${room.floor}. Kat · ${room.capacity} Kişilik", style = MaterialTheme.typography.caption, color = CorporateColors.Muted)
                        }

                        Row {
                            IconButton(onClick = { onEditRoom(room) }) {
                                Icon(Icons.Default.Edit, "Düzenle", tint = CorporateColors.Primary, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { onDeleteRoom(room.id) }) {
                                Icon(Icons.Default.Delete, "Sil", tint = CorporateColors.Risk, modifier = Modifier.size(20.dp))
                            }
                        }

                        StatusPill(text = "Salon", color = CorporateColors.Steel)
                    }
                }
            }
        }
    }

    @Composable
    private fun ProctorsTab(proctors: List<User>, onManage: () -> Unit) {
        val navigator = LocalNavigator.currentOrThrow
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CorporateCard {
                    Column {
                        SectionTitle("Gözetmen Havuzu")
                        Text("${proctors.size} kayıtlı personel", style = MaterialTheme.typography.body2, color = CorporateColors.Muted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { navigator.push(ExcuseManagementScreen()) },
                            colors = ButtonDefaults.buttonColors(CorporateColors.PrimarySoft),
                            elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PendingActions, null, tint = CorporateColors.Primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("İzin Talepleri", color = CorporateColors.Primary, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onManage,
                            colors = ButtonDefaults.buttonColors(CorporateColors.PrimarySoft),
                            elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.ManageAccounts, null, tint = CorporateColors.Primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Yönet", color = CorporateColors.Primary, fontWeight = FontWeight.Bold)
                        }
                    }

                Spacer(Modifier.height(16.dp))

                if (proctors.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Kayıtlı gözetmen bulunamadı.", color = CorporateColors.Muted)
                    }
                } else {
                    proctors.take(10).forEach { proctor ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp).background(CorporateColors.Steel.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(proctor.name.take(1).uppercase(), color = CorporateColors.Steel, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(proctor.name, fontWeight = FontWeight.Bold, color = CorporateColors.Ink)
                                Text(proctor.email, style = MaterialTheme.typography.caption, color = CorporateColors.Muted)
                            }
                        }
                        DividerLine()
                    }
                    if (proctors.size > 10) {
                        TextButton(onClick = onManage, modifier = Modifier.fillMaxWidth()) {
                            Text("Tümünü Gör (${proctors.size})", color = CorporateColors.Primary)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun BackupTab(onBackup: () -> Unit, onRestore: () -> Unit, onClear: () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CorporateCard {
                Text("Sistem Bakımı", style = MaterialTheme.typography.h3, color = CorporateColors.Ink)
                Spacer(Modifier.height(24.dp))
                BackupAction("Verileri Yedekle", "Tüm sistemi JSON olarak dışa aktar", Icons.Default.Download, CorporateColors.Primary, onBackup)
                DividerLine(Modifier.padding(vertical = 12.dp))
                BackupAction("Yedekten Geri Yükle", "Eski bir yedek dosyasını içeri al", Icons.Default.Upload, CorporateColors.Steel, onRestore)
                DividerLine(Modifier.padding(vertical = 12.dp))
                BackupAction("Sistemi Sıfırla", "Tüm verileri kalıcı olarak siler!", Icons.Default.DeleteForever, CorporateColors.Risk, onClear)
            }
        }
    }

    @Composable
    private fun BackupAction(title: String, desc: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(40.dp).background(color.copy(alpha = 0.1f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.caption, color = CorporateColors.Muted)
            }
        }
    }
}
