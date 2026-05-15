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
import com.mustafa.sinavtakvim.shared.utils.timeLabel
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
        var excuseDate by remember { mutableStateOf("") }
        var excuseStartTime by remember { mutableStateOf("09:00") }
        var excuseEndTime by remember { mutableStateOf("17:00") }
        var excuseMessage by remember { mutableStateOf("") }
        var excuseError by remember { mutableStateOf(false) }

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

        fun submitExcuseRequest() {
            val start = parseExcuseDateTimeMillis(excuseDate, excuseStartTime)
            val end = parseExcuseDateTimeMillis(excuseDate, excuseEndTime)
            when {
                start == null || end == null -> {
                    excuseMessage = "Tarih/saat formatı: 2026-05-18, 09:00"
                    excuseError = true
                }
                end <= start -> {
                    excuseMessage = "Bitiş saati başlangıçtan sonra olmalıdır."
                    excuseError = true
                }
                excuseNote.isBlank() -> {
                    excuseMessage = "Mazeret açıklaması zorunludur."
                    excuseError = true
                }
                else -> {
                    scope.launch {
                        repository.submitExcuse(
                            proctorId,
                            DateRange(
                                start = start,
                                end = end,
                                note = excuseNote.trim()
                            )
                        )
                        excuseNote = ""
                        excuseDate = ""
                        excuseStartTime = "09:00"
                        excuseEndTime = "17:00"
                        excuseMessage = "Talebiniz yöneticiye iletildi."
                        excuseError = false
                        loadData()
                    }
                }
            }
        }

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

                ResponsiveBox(
                    modifier = Modifier.fillMaxWidth(),
                    breakpoint = 800.dp
                ) { isDesktop ->
                    if (isDesktop) {
                        DesktopProctorLayout(
                            exams = exams,
                            nextExam = nextExam,
                            assignedRooms = assignedRooms,
                            courses = courses,
                            rooms = rooms,
                            proctorId = proctorId,
                            excuses = user?.excuses.orEmpty(),
                            excuseDate = excuseDate,
                            onExcuseDateChange = { excuseDate = it },
                            excuseStartTime = excuseStartTime,
                            onExcuseStartTimeChange = { excuseStartTime = it },
                            excuseEndTime = excuseEndTime,
                            onExcuseEndTimeChange = { excuseEndTime = it },
                            excuseNote = excuseNote,
                            onExcuseNoteChange = { excuseNote = it },
                            onExcuseSubmit = { submitExcuseRequest() },
                            excuseMessage = excuseMessage,
                            excuseError = excuseError
                        )
                    } else {
                        MobileProctorLayout(
                            exams = exams,
                            nextExam = nextExam,
                            assignedRooms = assignedRooms,
                            courses = courses,
                            rooms = rooms,
                            proctorId = proctorId,
                            excuses = user?.excuses.orEmpty(),
                            excuseDate = excuseDate,
                            onExcuseDateChange = { excuseDate = it },
                            excuseStartTime = excuseStartTime,
                            onExcuseStartTimeChange = { excuseStartTime = it },
                            excuseEndTime = excuseEndTime,
                            onExcuseEndTimeChange = { excuseEndTime = it },
                            excuseNote = excuseNote,
                            onExcuseNoteChange = { excuseNote = it },
                            onExcuseSubmit = { submitExcuseRequest() },
                            excuseMessage = excuseMessage,
                            excuseError = excuseError
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
        excuses: List<DateRange>,
        excuseDate: String,
        onExcuseDateChange: (String) -> Unit,
        excuseStartTime: String,
        onExcuseStartTimeChange: (String) -> Unit,
        excuseEndTime: String,
        onExcuseEndTimeChange: (String) -> Unit,
        excuseNote: String,
        onExcuseNoteChange: (String) -> Unit,
        onExcuseSubmit: () -> Unit,
        excuseMessage: String,
        excuseError: Boolean
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
                            value = excuseDate,
                            onValueChange = onExcuseDateChange,
                            label = { Text("Tarih (2026-05-18)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = excuseStartTime,
                                onValueChange = onExcuseStartTimeChange,
                                label = { Text("Başlangıç") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = excuseEndTime,
                                onValueChange = onExcuseEndTimeChange,
                                label = { Text("Bitiş") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                        Spacer(Modifier.height(10.dp))
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
                        if (excuseMessage.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            if (excuseError) {
                                StatusPill(excuseMessage, CorporateColors.Risk)
                            } else
                            StatusPill("Talebiniz yöneticiye iletildi.", CorporateColors.Success)
                        }
                        ExcuseStatusList(excuses)
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
        excuses: List<DateRange>,
        excuseDate: String,
        onExcuseDateChange: (String) -> Unit,
        excuseStartTime: String,
        onExcuseStartTimeChange: (String) -> Unit,
        excuseEndTime: String,
        onExcuseEndTimeChange: (String) -> Unit,
        excuseNote: String,
        onExcuseNoteChange: (String) -> Unit,
        onExcuseSubmit: () -> Unit,
        excuseMessage: String,
        excuseError: Boolean
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
                        value = excuseDate,
                        onValueChange = onExcuseDateChange,
                        label = { Text("Tarih (2026-05-18)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = excuseStartTime,
                            onValueChange = onExcuseStartTimeChange,
                            label = { Text("Başlangıç") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = excuseEndTime,
                            onValueChange = onExcuseEndTimeChange,
                            label = { Text("Bitiş") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Spacer(Modifier.height(8.dp))
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
                    if (excuseMessage.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        StatusPill(excuseMessage, if (excuseError) CorporateColors.Risk else CorporateColors.Success)
                    }
                    ExcuseStatusList(excuses)
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
                        text = "${examDateLabel(exam.date)} · ${exam.slotLabel.ifBlank { slotLabel(exam.slotId) }}",
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
    private fun ExcuseStatusList(excuses: List<DateRange>) {
        if (excuses.isEmpty()) return

        Spacer(Modifier.height(12.dp))
        DividerLine()
        Spacer(Modifier.height(10.dp))
        SectionTitle("Mazeret Taleplerim", "Yonetici onay durumlari")
        Spacer(Modifier.height(8.dp))
        excuses.sortedByDescending { it.start }.take(4).forEach { excuse ->
            val text = when {
                excuse.isApproved -> "Onaylandi"
                excuse.isRejected -> "Reddedildi"
                else -> "Bekliyor"
            }
            val color = when {
                excuse.isApproved -> CorporateColors.Success
                excuse.isRejected -> CorporateColors.Risk
                else -> CorporateColors.Amber
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(examDateLabel(excuse.start), fontWeight = FontWeight.SemiBold, color = CorporateColors.Ink)
                    Text("${timeLabel(excuse.start)} - ${timeLabel(excuse.end)}", style = MaterialTheme.typography.caption, color = CorporateColors.Primary)
                    Text(excuse.note.ifBlank { "Aciklama yok" }, style = MaterialTheme.typography.caption, color = CorporateColors.Muted)
                }
                StatusPill(text, color)
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

    private fun parseExcuseDateTimeMillis(rawDate: String, rawTime: String): Long? {
        val date = parseDate(rawDate) ?: return null
        val minutes = parseTimeMinutes(rawTime) ?: return null
        return date.toEpochDay() * DAY_MILLIS - TURKEY_OFFSET_MILLIS + minutes * 60_000L
    }

    private fun parseDate(raw: String): SimpleDate? {
        val value = raw.trim()
        if (value.isBlank()) return null

        val parts = when {
            value.contains("-") && value.substringBefore("-").length == 4 -> value.split("-").mapNotNull { it.toIntOrNull() }.let { parsed ->
                if (parsed.size == 3) listOf(parsed[2], parsed[1], parsed[0]) else emptyList()
            }
            value.contains("-") -> value.split("-").mapNotNull { it.toIntOrNull() }
            value.contains(".") -> value.split(".").mapNotNull { it.toIntOrNull() }
            value.contains("/") -> value.split("/").mapNotNull { it.toIntOrNull() }
            else -> emptyList()
        }
        if (parts.size != 3) return null

        val day = parts[0]
        val month = parts[1]
        val year = parts[2]
        if (month !in 1..12 || day !in 1..daysInMonth(year, month)) return null

        return SimpleDate(year, month, day)
    }

    private fun parseTimeMinutes(raw: String): Int? {
        val parts = raw.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    private fun SimpleDate.toEpochDay(): Long {
        var days = 0L
        for (year in 1970 until this.year) days += if (isLeapYear(year)) 366 else 365
        for (month in 1 until this.month) days += daysInMonth(this.year, month)
        return days + this.day - 1
    }

    private fun daysInMonth(year: Int, month: Int): Int {
        return when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeapYear(year)) 29 else 28
            else -> 0
        }
    }

    private fun isLeapYear(year: Int): Boolean {
        return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    }

    private data class SimpleDate(val year: Int, val month: Int, val day: Int)

    private companion object {
        const val DAY_MILLIS = 86_400_000L
        const val TURKEY_OFFSET_MILLIS = 3 * 60 * 60 * 1000L
    }
}
