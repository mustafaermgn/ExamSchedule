package com.mustafa.sinavtakvim.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mustafa.sinavtakvim.shared.data.repository.ExamRepository
import com.mustafa.sinavtakvim.shared.models.Course
import com.mustafa.sinavtakvim.shared.models.Room
import com.mustafa.sinavtakvim.shared.models.Student
import com.mustafa.sinavtakvim.shared.models.User
import com.mustafa.sinavtakvim.shared.models.UserRole
import com.mustafa.sinavtakvim.shared.utils.parseStudentSpreadsheet
import com.mustafa.sinavtakvim.ui.components.CorporateCard
import com.mustafa.sinavtakvim.ui.components.CorporateColors
import com.mustafa.sinavtakvim.ui.components.DividerLine
import com.mustafa.sinavtakvim.ui.components.MetricCard
import com.mustafa.sinavtakvim.ui.components.PageHeader
import com.mustafa.sinavtakvim.ui.components.SectionTitle
import com.mustafa.sinavtakvim.ui.components.StatusPill
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.coroutines.launch
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
        var students by remember { mutableStateOf<List<Student>>(emptyList()) }
        var message by remember { mutableStateOf("") }
        var importBusy by remember { mutableStateOf(false) }

        var courseCode by remember { mutableStateOf("") }
        var courseName by remember { mutableStateOf("") }
        var semester by remember { mutableStateOf("") }
        var instructorName by remember { mutableStateOf("") }

        var roomName by remember { mutableStateOf("") }
        var roomCapacity by remember { mutableStateOf("") }
        var roomFloor by remember { mutableStateOf("") }
        var roomBuilding by remember { mutableStateOf("Mühendislik Fakültesi") }
        var roomLat by remember { mutableStateOf("41.0082") }
        var roomLng by remember { mutableStateOf("28.9784") }

        var selectedCourseId by remember { mutableStateOf("") }

        suspend fun load() {
            courses = repository.getCourses().sortedBy { it.code }
            rooms = repository.getRooms().sortedBy { it.name }
            users = repository.getUsers().sortedBy { it.name }
            students = repository.getStudents()
            if (selectedCourseId.isBlank()) selectedCourseId = courses.firstOrNull()?.id.orEmpty()
        }

        val importLauncher = rememberFilePickerLauncher(
            type = PickerType.File(extensions = listOf("xlsx", "csv", "tsv")),
            mode = PickerMode.Single,
            title = "Öğrenci listesi seç"
        ) { file: PlatformFile? ->
            if (file != null) {
                val courseId = selectedCourseId
                scope.launch {
                    if (courseId.isBlank()) {
                        message = "Önce öğrenci listesinin bağlı olduğu dersi seçin."
                        return@launch
                    }

                    importBusy = true
                    message = ""
                    try {
                        val result = parseStudentSpreadsheet(file.readBytes(), file.name)
                        if (result.students.isEmpty()) {
                            message = "Dosyada içe aktarılabilecek öğrenci satırı bulunamadı."
                        } else {
                            repository.enrollStudentsInCourse(courseId, result.students)
                            val warningText = result.warnings.take(2).joinToString(" ")
                            message = buildString {
                                append("${result.students.size} öğrenci ${courses.firstOrNull { it.id == courseId }?.code.orEmpty()} dersine yüklendi.")
                                if (warningText.isNotBlank()) append(" Uyarı: $warningText")
                            }
                            load()
                        }
                    } catch (error: Exception) {
                        message = "Dosya okunamadı: ${error.message ?: "bilinmeyen hata"}"
                    } finally {
                        importBusy = false
                    }
                }
            }
        }

        LaunchedEffect(Unit) { load() }

        val proctors = users.filter { it.role == UserRole.PROCTOR }
        val totalCapacity = rooms.sumOf { it.capacity }
        val loadedCourseCount = courses.count { it.studentCount > 0 }
        val studentCountByCourse = courses.associate { course ->
            course.id to students.count { course.id in it.enrolledCourseIds }.coerceAtLeast(course.studentCount)
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val isDesktop = maxWidth > 900.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isDesktop) 32.dp else 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(modifier = Modifier.widthIn(max = 1380.dp)) {
                    PageHeader(
                        title = "Veri Hazırlığı",
                        subtitle = "Ders havuzu, öğrenci listeleri, salonlar ve gözetmen kayıtları",
                        trailing = {
                            StatusPill(
                                text = if (loadedCourseCount == courses.size && courses.isNotEmpty()) "Planlamaya hazır" else "Veri hazırlanıyor",
                                color = if (loadedCourseCount == courses.size && courses.isNotEmpty()) CorporateColors.Success else CorporateColors.Amber
                            )
                        }
                    )

                    Spacer(Modifier.height(24.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            if (isDesktop) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    MetricCard("Ders", courses.size.toString(), "$loadedCourseCount listesi yüklü", CorporateColors.Primary, Modifier.weight(1f))
                                    MetricCard("Öğrenci", students.size.toString(), "tekil kayıt", CorporateColors.Steel, Modifier.weight(1f))
                                    MetricCard("Salon", totalCapacity.toString(), "toplam kapasite", CorporateColors.Amber, Modifier.weight(1f))
                                    MetricCard("Gözetmen", proctors.size.toString(), "aktif personel", CorporateColors.Risk, Modifier.weight(1f))
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        MetricCard("Ders", courses.size.toString(), "$loadedCourseCount yüklü", CorporateColors.Primary, Modifier.weight(1f))
                                        MetricCard("Öğrenci", students.size.toString(), "tekil", CorporateColors.Steel, Modifier.weight(1f))
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        MetricCard("Salon", totalCapacity.toString(), "kapasite", CorporateColors.Amber, Modifier.weight(1f))
                                        MetricCard("Gözetmen", proctors.size.toString(), "personel", CorporateColors.Risk, Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        item {
                            WorkflowCard()
                        }

                        item {
                            if (isDesktop) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        CourseAddForm(
                                            code = courseCode,
                                            onCodeChange = { courseCode = it },
                                            name = courseName,
                                            onNameChange = { courseName = it },
                                            sem = semester,
                                            onSemChange = { semester = it },
                                            instructor = instructorName,
                                            onInstructorChange = { instructorName = it },
                                            onSave = {
                                                val sem = semester.toIntOrNull()
                                                if (courseCode.isBlank() || courseName.isBlank() || sem == null) {
                                                    message = "Ders kodu, ders adı ve yarıyıl zorunludur."
                                                } else {
                                                    scope.launch {
                                                        val course = Course(
                                                            id = stableId("course", courseCode),
                                                            code = courseCode.trim().uppercase(),
                                                            name = courseName.trim(),
                                                            studentCount = 0,
                                                            semester = sem,
                                                            departmentId = "BIL",
                                                            instructorName = instructorName.trim()
                                                        )
                                                        repository.addCourse(course)
                                                        courseCode = ""
                                                        courseName = ""
                                                        semester = ""
                                                        instructorName = ""
                                                        selectedCourseId = course.id
                                                        message = "${course.code} ders havuzuna eklendi. Şimdi öğrenci listesini yükleyebilirsiniz."
                                                        load()
                                                    }
                                                }
                                            }
                                        )

                                        StudentUploadPanel(
                                            courses = courses,
                                            selectedCourseId = selectedCourseId,
                                            onSelectCourse = { selectedCourseId = it },
                                            counts = studentCountByCourse,
                                            busy = importBusy,
                                            onPickFile = { importLauncher.launch() }
                                        )
                                    }

                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        RoomAddForm(
                                            name = roomName,
                                            onNameChange = { roomName = it },
                                            capacity = roomCapacity,
                                            onCapChange = { roomCapacity = it },
                                            floor = roomFloor,
                                            onFloorChange = { roomFloor = it },
                                            building = roomBuilding,
                                            onBuildingChange = { roomBuilding = it },
                                            lat = roomLat,
                                            onLatChange = { roomLat = it },
                                            lng = roomLng,
                                            onLngChange = { roomLng = it },
                                            onSave = {
                                                val cap = roomCapacity.toIntOrNull()
                                                if (roomName.isBlank() || cap == null) {
                                                    message = "Salon adı ve kapasite zorunludur."
                                                } else {
                                                    scope.launch {
                                                        val room = Room(
                                                            id = stableId("room", roomName),
                                                            name = roomName.trim(),
                                                            capacity = cap,
                                                            floor = roomFloor.toIntOrNull() ?: 0,
                                                            latitude = roomLat.toDoubleOrNull() ?: 41.0082,
                                                            longitude = roomLng.toDoubleOrNull() ?: 28.9784,
                                                            building = roomBuilding.trim().ifBlank { "Fakülte Binası" },
                                                            facilities = listOf("Standart sınav düzeni")
                                                        )
                                                        repository.addRoom(room)
                                                        roomName = ""
                                                        roomCapacity = ""
                                                        roomFloor = ""
                                                        message = "${room.name} salonu kaydedildi."
                                                        load()
                                                    }
                                                }
                                            }
                                        )

                                        AdminRelationPanel(
                                            proctors = proctors,
                                            onManageUsers = { navigator.push(UserManagementScreen()) }
                                        )
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    CourseAddForm(
                                        code = courseCode,
                                        onCodeChange = { courseCode = it },
                                        name = courseName,
                                        onNameChange = { courseName = it },
                                        sem = semester,
                                        onSemChange = { semester = it },
                                        instructor = instructorName,
                                        onInstructorChange = { instructorName = it },
                                        onSave = {
                                            val sem = semester.toIntOrNull()
                                            if (courseCode.isBlank() || courseName.isBlank() || sem == null) {
                                                message = "Ders kodu, ders adı ve yarıyıl zorunludur."
                                            } else {
                                                scope.launch {
                                                    val course = Course(
                                                        id = stableId("course", courseCode),
                                                        code = courseCode.trim().uppercase(),
                                                        name = courseName.trim(),
                                                        semester = sem,
                                                        departmentId = "BIL",
                                                        instructorName = instructorName.trim()
                                                    )
                                                    repository.addCourse(course)
                                                    selectedCourseId = course.id
                                                    courseCode = ""
                                                    courseName = ""
                                                    semester = ""
                                                    instructorName = ""
                                                    message = "${course.code} ders havuzuna eklendi."
                                                    load()
                                                }
                                            }
                                        }
                                    )

                                    StudentUploadPanel(
                                        courses = courses,
                                        selectedCourseId = selectedCourseId,
                                        onSelectCourse = { selectedCourseId = it },
                                        counts = studentCountByCourse,
                                        busy = importBusy,
                                        onPickFile = { importLauncher.launch() }
                                    )

                                    RoomAddForm(
                                        name = roomName,
                                        onNameChange = { roomName = it },
                                        capacity = roomCapacity,
                                        onCapChange = { roomCapacity = it },
                                        floor = roomFloor,
                                        onFloorChange = { roomFloor = it },
                                        building = roomBuilding,
                                        onBuildingChange = { roomBuilding = it },
                                        lat = roomLat,
                                        onLatChange = { roomLat = it },
                                        lng = roomLng,
                                        onLngChange = { roomLng = it },
                                        onSave = {
                                            val cap = roomCapacity.toIntOrNull()
                                            if (roomName.isBlank() || cap == null) {
                                                message = "Salon adı ve kapasite zorunludur."
                                            } else {
                                                scope.launch {
                                                    repository.addRoom(
                                                        Room(
                                                            id = stableId("room", roomName),
                                                            name = roomName.trim(),
                                                            capacity = cap,
                                                            floor = roomFloor.toIntOrNull() ?: 0,
                                                            latitude = roomLat.toDoubleOrNull() ?: 41.0082,
                                                            longitude = roomLng.toDoubleOrNull() ?: 28.9784,
                                                            building = roomBuilding.trim().ifBlank { "Fakülte Binası" },
                                                            facilities = listOf("Standart sınav düzeni")
                                                        )
                                                    )
                                                    roomName = ""
                                                    roomCapacity = ""
                                                    roomFloor = ""
                                                    message = "Salon kaydedildi."
                                                    load()
                                                }
                                            }
                                        }
                                    )

                                    AdminRelationPanel(
                                        proctors = proctors,
                                        onManageUsers = { navigator.push(UserManagementScreen()) }
                                    )
                                }
                            }
                        }

                        item {
                            CourseInventoryPanel(courses, studentCountByCourse)
                        }

                        if (message.isNotBlank()) {
                            item {
                                StatusPill(
                                    text = message,
                                    color = if (message.contains("hata", ignoreCase = true) || message.contains("zorunlu", ignoreCase = true)) CorporateColors.Risk else CorporateColors.Success
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun WorkflowCard() {
        CorporateCard(Modifier.fillMaxWidth()) {
            SectionTitle("Operasyon akışı", "Admin ekranında veri hazırlanır; gözetmenler yalnızca kendi görevlerini görür.")
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WorkflowStep("1", "Dersleri aç", "Ders kodu ve yarıyıl bilgisi", Modifier.weight(1f))
                WorkflowStep("2", "Excel yükle", "Öğrenci sayısı otomatik alınır", Modifier.weight(1f))
                WorkflowStep("3", "Salon ve görevli", "Kapasite ve gözetmen havuzu", Modifier.weight(1f))
                WorkflowStep("4", "Planla", "DP veya sezgisel sonucu seç", Modifier.weight(1f))
            }
        }
    }

    @Composable
    private fun WorkflowStep(number: String, title: String, caption: String, modifier: Modifier = Modifier) {
        Surface(
            modifier = modifier.heightIn(min = 96.dp),
            shape = MaterialTheme.shapes.medium,
            color = CorporateColors.PrimarySoft,
            border = BorderStroke(1.dp, CorporateColors.Border)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(number, style = MaterialTheme.typography.h3, color = CorporateColors.Primary)
                Text(title, fontWeight = FontWeight.Bold, color = CorporateColors.Ink)
                Text(caption, style = MaterialTheme.typography.caption, color = CorporateColors.Muted)
            }
        }
    }

    @Composable
    private fun CourseAddForm(
        code: String,
        onCodeChange: (String) -> Unit,
        name: String,
        onNameChange: (String) -> Unit,
        sem: String,
        onSemChange: (String) -> Unit,
        instructor: String,
        onInstructorChange: (String) -> Unit,
        onSave: () -> Unit
    ) {
        CorporateCard(Modifier.fillMaxWidth()) {
            SectionTitle("Ders kaydı", "Öğrenci listesi yüklenmeden önce ders kabuğunu oluşturun.")
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { onCodeChange(it.uppercase()) },
                    label = { Text("Ders kodu") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = sem,
                    onValueChange = { onSemChange(it.filter(Char::isDigit)) },
                    label = { Text("Yarıyıl") },
                    modifier = Modifier.width(120.dp),
                    singleLine = true
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Ders adı") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = instructor,
                onValueChange = onInstructorChange,
                label = { Text("Ders sorumlusu") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = CorporateColors.Primary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Dersi kaydet", color = Color.White)
            }
        }
    }

    @Composable
    private fun StudentUploadPanel(
        courses: List<Course>,
        selectedCourseId: String,
        onSelectCourse: (String) -> Unit,
        counts: Map<String, Int>,
        busy: Boolean,
        onPickFile: () -> Unit
    ) {
        CorporateCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Öğrenci listesi", "Excel, CSV veya TSV dosyası ile yükleme", Modifier.weight(1f))
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = CorporateColors.Primary)
            }
            Spacer(Modifier.height(14.dp))

            if (courses.isEmpty()) {
                EmptyState(Icons.Default.School, "Önce ders ekleyin.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(courses) { course ->
                        CourseSelectorRow(
                            course = course,
                            count = counts[course.id] ?: course.studentCount,
                            selected = selectedCourseId == course.id,
                            onClick = { onSelectCourse(course.id) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onPickFile,
                enabled = selectedCourseId.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = CorporateColors.Steel)
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Excel öğrenci listesini yükle", color = Color.White)
                }
            }
        }
    }

    @Composable
    private fun CourseSelectorRow(course: Course, count: Int, selected: Boolean, onClick: () -> Unit) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            shape = MaterialTheme.shapes.medium,
            color = if (selected) CorporateColors.PrimarySoft else CorporateColors.Surface,
            border = BorderStroke(1.dp, if (selected) CorporateColors.Primary else CorporateColors.Border)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    RadioButton(selected = selected, onClick = onClick)
                    Column {
                        Text(course.code, fontWeight = FontWeight.Bold, color = CorporateColors.Ink)
                        Text(course.name, style = MaterialTheme.typography.caption, color = CorporateColors.Muted)
                    }
                }
                StatusPill(
                    text = if (count > 0) "$count öğrenci" else "Liste bekliyor",
                    color = if (count > 0) CorporateColors.Success else CorporateColors.Amber
                )
            }
        }
    }

    @Composable
    private fun RoomAddForm(
        name: String,
        onNameChange: (String) -> Unit,
        capacity: String,
        onCapChange: (String) -> Unit,
        floor: String,
        onFloorChange: (String) -> Unit,
        building: String,
        onBuildingChange: (String) -> Unit,
        lat: String,
        onLatChange: (String) -> Unit,
        lng: String,
        onLngChange: (String) -> Unit,
        onSave: () -> Unit
    ) {
        CorporateCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Salon kaydı", "Kapasite ve kampüs konumu", Modifier.weight(1f))
                Icon(Icons.Default.Home, contentDescription = null, tint = CorporateColors.Primary)
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Salon adı") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = capacity,
                    onValueChange = { onCapChange(it.filter(Char::isDigit)) },
                    label = { Text("Kapasite") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = floor,
                    onValueChange = { onFloorChange(it.filter { char -> char.isDigit() || char == '-' }) },
                    label = { Text("Kat") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = building,
                onValueChange = onBuildingChange,
                label = { Text("Bina") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = lat,
                    onValueChange = onLatChange,
                    label = { Text("Enlem") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = lng,
                    onValueChange = onLngChange,
                    label = { Text("Boylam") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = CorporateColors.Primary)
            ) {
                Text("Salonu kaydet", color = Color.White)
            }
        }
    }

    @Composable
    private fun AdminRelationPanel(proctors: List<User>, onManageUsers: () -> Unit) {
        CorporateCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Admin - gözetmen ilişkisi", "Gözetmenler algoritma ve veri ekranlarını görmez.", Modifier.weight(1f))
                Icon(Icons.Default.People, contentDescription = null, tint = CorporateColors.Primary)
            }
            Spacer(Modifier.height(14.dp))
            DividerLine()
            Spacer(Modifier.height(14.dp))
            if (proctors.isEmpty()) {
                EmptyState(Icons.Default.Warning, "Planlama için en az bir gözetmen ekleyin.")
            } else {
                proctors.take(4).forEach { proctor ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(proctor.name, color = CorporateColors.Ink, fontWeight = FontWeight.SemiBold)
                        Text(proctor.email, style = MaterialTheme.typography.caption, color = CorporateColors.Muted)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = onManageUsers,
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Icon(Icons.Default.People, contentDescription = null, tint = CorporateColors.Primary)
                Spacer(Modifier.width(8.dp))
                Text("Kullanıcıları yönet", color = CorporateColors.Primary)
            }
        }
    }

    @Composable
    private fun CourseInventoryPanel(courses: List<Course>, counts: Map<String, Int>) {
        CorporateCard(Modifier.fillMaxWidth()) {
            SectionTitle("Ders yükleme durumu", "Algoritma yalnızca öğrenci listesi yüklenen dersleri planlar.")
            Spacer(Modifier.height(12.dp))
            if (courses.isEmpty()) {
                EmptyState(Icons.Default.School, "Henüz ders bulunmuyor.")
            } else {
                courses.forEachIndexed { index, course ->
                    if (index > 0) {
                        Spacer(Modifier.height(8.dp))
                        DividerLine()
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${course.code} · ${course.name}", fontWeight = FontWeight.Bold, color = CorporateColors.Ink)
                            Text("${course.semester}. yarıyıl · ${course.instructorName.ifBlank { "Sorumlu girilmedi" }}", style = MaterialTheme.typography.caption, color = CorporateColors.Muted)
                        }
                        val count = counts[course.id] ?: course.studentCount
                        StatusPill(
                            text = if (count > 0) "$count öğrenci" else "Eksik",
                            color = if (count > 0) CorporateColors.Success else CorporateColors.Amber
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = CorporateColors.Muted)
            Spacer(Modifier.width(8.dp))
            Text(text, color = CorporateColors.Muted)
        }
    }

    private fun stableId(prefix: String, value: String): String {
        val safe = value.trim().lowercase().filter { it.isLetterOrDigit() }
        return "${prefix}_${safe.ifBlank { "kayit" }}"
    }
}
