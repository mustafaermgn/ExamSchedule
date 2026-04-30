package com.mustafa.sinavtakvim.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.mustafa.sinavtakvim.shared.data.repository.ExamRepository
import com.mustafa.sinavtakvim.shared.models.Course
import com.mustafa.sinavtakvim.shared.models.DateRange
import com.mustafa.sinavtakvim.shared.models.Exam
import com.mustafa.sinavtakvim.shared.models.Room
import com.mustafa.sinavtakvim.shared.models.User
import com.mustafa.sinavtakvim.shared.utils.addToCalendar
import com.mustafa.sinavtakvim.shared.utils.examDateLabel
import com.mustafa.sinavtakvim.shared.utils.slotLabel
import com.mustafa.sinavtakvim.ui.components.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class ProctorHomeScreen(private val proctorId: String) : Screen {
    @Composable
    override fun Content() {
        val repository = koinInject<ExamRepository>()
        val scope = rememberCoroutineScope()
        var exams by remember { mutableStateOf<List<Exam>>(emptyList()) }
        var courses by remember { mutableStateOf<Map<String, Course>>(emptyMap()) }
        var rooms by remember { mutableStateOf<Map<String, Room>>(emptyMap()) }
        var user by remember { mutableStateOf<User?>(null) }

        // Excuse form state
        var excuseNote by remember { mutableStateOf("") }
        var excuseDate by remember { mutableStateOf("") } // Simple string for now
        var showExcuseSuccess by remember { mutableStateOf(false) }

        suspend fun loadData() {
            val allUsers = repository.getUsers()
            user = allUsers.firstOrNull { it.uid == proctorId }
            courses = repository.getCourses().associateBy { it.id }
            rooms = repository.getRooms().associateBy { it.id }
            exams = repository.getExams()
                .filter { exam -> exam.assignments.any { it.proctorId == proctorId } }
                .sortedWith(compareBy<Exam> { it.date }.thenBy { it.slotId })
        }

        LaunchedEffect(proctorId) {
            loadData()
        }

        val nextExam = exams.firstOrNull()
        val assignedRooms = exams.flatMap { exam ->
            exam.assignments.filter { it.proctorId == proctorId }.mapNotNull { rooms[it.roomId] }
        }.distinctBy { it.id }

        // BoxWithConstraints'i kaldırıp direkt Column ile devam ediyoruz
        // veya constraints'i kullanarak responsive tasarım yapıyoruz
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 1200.dp)) {
                PageHeader(
                    title = "Gözetmen Paneli",
                    subtitle = "Hoş geldiniz, ${user?.name ?: "Gözetmen"}",
                    trailing = { StatusPill("${exams.size} Atanmış Görev", CorporateColors.Primary) }
                )

                Spacer(Modifier.height(24.dp))

                // Responsive layout için BoxWithConstraints kullanıyoruz ama constraints'i kullanarak
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val isDesktop = maxWidth > 800.dp

                    if (isDesktop) {
                        DesktopProctorLayout(
                            exams = exams,
                            nextExam = nextExam,
                            assignedRooms = assignedRooms,
                            courses = courses,
                            rooms = rooms,
                            proctorId = proctorId,
                            excuseNote = excuseNote,
                            onExcuseNoteChange = { excuseNote = it },
                            onExcuseSubmit = {
                                scope.launch {
                                    repository.submitExcuse(proctorId, DateRange(start = System.currentTimeMillis(), note = excuseNote))
                                    excuseNote = ""
                                    showExcuseSuccess = true
                                    loadData()
                                }
                            },
                            showExcuseSuccess = showExcuseSuccess
                        )
                    } else {
                        MobileProctorLayout(
                            exams = exams,
                            nextExam = nextExam,
                            assignedRooms = assignedRooms,
                            courses = courses,
                            rooms = rooms,
                            proctorId = proctorId,
                            excuseNote = excuseNote,
                            onExcuseNoteChange = { excuseNote = it },
                            onExcuseSubmit = {
                                scope.launch {
                                    repository.submitExcuse(proctorId, DateRange(start = System.currentTimeMillis(), note = excuseNote))
                                    excuseNote = ""
                                    showExcuseSuccess = true
                                    loadData()
                                }
                            },
                            showExcuseSuccess = showExcuseSuccess
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun DesktopProctorLayout(
        exams: List<Exam>,
        nextExam: Exam?,
        assignedRooms: List<Room>,
        courses: Map<String, Course>,
        rooms: Map<String, Room>,
        proctorId: String,
        excuseNote: String,
        onExcuseNoteChange: (String) -> Unit,
        onExcuseSubmit: () -> Unit,
        showExcuseSuccess: Boolean
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            // Metrics Row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetricCard("Toplam Görev", exams.size.toString(), "bu dönem", CorporateColors.Primary, Modifier.weight(1f))
                MetricCard("Farklı Salon", assignedRooms.size.toString(), "görev yerleri", CorporateColors.Steel, Modifier.weight(1f))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                // Left Column: Next Task & Excuse Form
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    SectionTitle("Sıradaki Görev", "En yakın sınav oturumunuz")
                    CorporateCard(Modifier.fillMaxWidth()) {
                        if (nextExam == null) {
                            Text("Bekleyen göreviniz bulunmuyor.", color = CorporateColors.Muted)
                        } else {
                            val course = courses[nextExam.courseId]
                            val roomNames = nextExam.assignments
                                .filter { it.proctorId == proctorId }
                                .mapNotNull { rooms[it.roomId]?.name }

                            Text(course?.code ?: nextExam.courseId, style = MaterialTheme.typography.h1, color = CorporateColors.Ink)
                            Text(course?.name ?: "", style = MaterialTheme.typography.h3, color = CorporateColors.Ink)

                            Spacer(Modifier.height(20.dp))
                            DividerLine()
                            Spacer(Modifier.height(20.dp))

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                InfoCell("Tarih", examDateLabel(nextExam.date))
                                InfoCell("Saat", slotLabel(nextExam.slotId))
                                InfoCell("Salon", roomNames.joinToString(", "))
                            }
                        }
                    }

                    SectionTitle("Mazeret Bildir", "Göreve katılamama durumu")
                    CorporateCard(Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = excuseNote,
                            onValueChange = onExcuseNoteChange,
                            label = { Text("Mazeret açıklaması") },
                            modifier = Modifier.fillMaxWidth().height(100.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onExcuseSubmit,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = CorporateColors.Risk)
                        ) {
                            Text("Mazeret Talebi Gönder", color = Color.White)
                        }
                        if (showExcuseSuccess) {
                            Spacer(Modifier.height(8.dp))
                            StatusPill("Talebiniz yöneticiye iletildi.", CorporateColors.Success)
                        }
                    }
                }

                // Right Column: All Tasks
                Column(Modifier.weight(1f)) {
                    SectionTitle("Görev Listesi", "Tüm dönem programı")
                    Spacer(Modifier.height(16.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.heightIn(max = 600.dp)) {
                        items(exams) { exam ->
                            ProctorExamRow(exam, courses[exam.courseId], rooms, proctorId)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MobileProctorLayout(
        exams: List<Exam>,
        nextExam: Exam?,
        assignedRooms: List<Room>,
        courses: Map<String, Course>,
        rooms: Map<String, Room>,
        proctorId: String,
        excuseNote: String,
        onExcuseNoteChange: (String) -> Unit,
        onExcuseSubmit: () -> Unit,
        showExcuseSuccess: Boolean
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Görev", exams.size.toString(), "adet", CorporateColors.Primary, Modifier.weight(1f))
                    MetricCard("Salon", assignedRooms.size.toString(), "lokasyon", CorporateColors.Steel, Modifier.weight(1f))
                }
            }
            item {
                SectionTitle("Sıradaki Görev", "En yakın")
                Spacer(Modifier.height(8.dp))
                CorporateCard(Modifier.fillMaxWidth()) {
                    if (nextExam == null) {
                        Text("Görev yok", color = CorporateColors.Muted)
                    } else {
                        val course = courses[nextExam.courseId]
                        Text(course?.code ?: "", style = MaterialTheme.typography.h2)
                        Text("${examDateLabel(nextExam.date)} · ${slotLabel(nextExam.slotId)}", style = MaterialTheme.typography.body2)
                    }
                }
            }
            item {
                SectionTitle("Mazeret Bildir", "Talepler")
                CorporateCard(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = excuseNote,
                        onValueChange = onExcuseNoteChange,
                        label = { Text("Açıklama") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onExcuseSubmit,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(backgroundColor = CorporateColors.Risk)
                    ) {
                        Text("Gönder", color = Color.White)
                    }
                    if (showExcuseSuccess) {
                        Spacer(Modifier.height(8.dp))
                        StatusPill("Talebiniz iletildi.", CorporateColors.Success)
                    }
                }
            }
            item {
                SectionTitle("Tüm Görevler", "Liste")
            }
            items(exams) { exam ->
                ProctorExamRow(exam, courses[exam.courseId], rooms, proctorId)
            }
        }
    }

    @Composable
    private fun ProctorExamRow(
        exam: Exam,
        course: Course?,
        rooms: Map<String, Room>,
        proctorId: String
    ) {
        val roomNames = exam.assignments
            .filter { it.proctorId == proctorId }
            .mapNotNull { rooms[it.roomId]?.name }

        CorporateCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = course?.code ?: exam.courseId,
                        fontWeight = FontWeight.Bold,
                        color = CorporateColors.Ink
                    )
                    Text(
                        text = "${examDateLabel(exam.date)} · ${slotLabel(exam.slotId)}",
                        style = MaterialTheme.typography.caption
                    )
                    Text(
                        text = "Salon: ${roomNames.joinToString(", ")}",
                        style = MaterialTheme.typography.caption,
                        color = CorporateColors.Primary
                    )
                }
                IconButton(onClick = {
                    addToCalendar("${course?.code} Sınavı", "Gözetmenlik görevi", exam.date)
                }) {
                    Icon(Icons.Default.DateRange, contentDescription = "Takvime Ekle", tint = CorporateColors.Primary)
                }
            }
        }
    }

    @Composable
    private fun InfoCell(label: String, value: String) {
        Column {
            Text(label, style = MaterialTheme.typography.caption, color = CorporateColors.Muted)
            Text(value, style = MaterialTheme.typography.body1, fontWeight = FontWeight.Bold)
        }
    }
}