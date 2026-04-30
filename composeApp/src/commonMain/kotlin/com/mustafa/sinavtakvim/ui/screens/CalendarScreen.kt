package com.mustafa.sinavtakvim.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import cafe.adriel.voyager.core.screen.Screen
import com.mustafa.sinavtakvim.shared.data.repository.ExamRepository
import com.mustafa.sinavtakvim.shared.models.Course
import com.mustafa.sinavtakvim.shared.models.Exam
import com.mustafa.sinavtakvim.shared.models.Room
import com.mustafa.sinavtakvim.shared.models.User
import com.mustafa.sinavtakvim.shared.models.UserRole
import com.mustafa.sinavtakvim.shared.utils.addToCalendar
import com.mustafa.sinavtakvim.shared.utils.examDateLabel
import com.mustafa.sinavtakvim.shared.utils.slotLabel
import com.mustafa.sinavtakvim.ui.components.*
import org.koin.compose.koinInject

class CalendarScreen(
    private val role: UserRole = UserRole.ADMIN,
    private val proctorId: String? = null
) : Screen {
    @Composable
    override fun Content() {
        val repository = koinInject<ExamRepository>()
        var searchQuery by remember { mutableStateOf("") }
        var selectedSemester by remember { mutableIntStateOf(0) }
        var exams by remember { mutableStateOf<List<Exam>>(emptyList()) }
        var courses by remember { mutableStateOf<Map<String, Course>>(emptyMap()) }
        var rooms by remember { mutableStateOf<Map<String, Room>>(emptyMap()) }
        var users by remember { mutableStateOf<Map<String, User>>(emptyMap()) }
        val isProctor = role == UserRole.PROCTOR

        LaunchedEffect(role, proctorId) {
            courses = repository.getCourses().associateBy { it.id }
            rooms = repository.getRooms().associateBy { it.id }
            users = repository.getUsers().associateBy { it.uid }
            exams = repository.getExams()
                .filter { exam -> !isProctor || exam.assignments.any { it.proctorId == proctorId } }
                .sortedWith(compareBy<Exam> { it.date }.thenBy { it.slotId })
        }

        val semesters = listOf(0) + courses.values.map { it.semester }.distinct().sorted()
        val filteredExams = exams.filter { exam ->
            val course = courses[exam.courseId]
            val matchesSearch = searchQuery.isBlank() ||
                course?.name?.contains(searchQuery, ignoreCase = true) == true ||
                course?.code?.contains(searchQuery, ignoreCase = true) == true
            val matchesSemester = isProctor || selectedSemester == 0 || course?.semester == selectedSemester
            matchesSearch && matchesSemester
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val isDesktop = this.maxWidth > 800.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isDesktop) 32.dp else 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(modifier = Modifier.widthIn(max = 1300.dp)) {
                    PageHeader(
                        title = if (isProctor) "Görev Takvimim" else "Akademik Sınav Takvimi",
                        subtitle = if (isProctor) "Size atanmış sınav oturumları" else "Bölüm geneli sınav planı",
                        trailing = { StatusPill("${filteredExams.size} Oturum", CorporateColors.Primary) }
                    )

                    Spacer(Modifier.height(24.dp))

                    // Filters Card
                    CorporateCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text("Ders kodu veya adı ile ara") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Search, null, tint = CorporateColors.Muted) },
                                shape = MaterialTheme.shapes.medium
                            )
                            
                            if (isDesktop && !isProctor) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    semesters.forEach { semester ->
                                        SemesterButton(
                                            text = if (semester == 0) "Tümü" else "$semester. YY",
                                            selected = selectedSemester == semester,
                                            onClick = { selectedSemester = semester }
                                        )
                                    }
                                }
                            }
                        }
                        
                        if (!isDesktop && !isProctor) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                semesters.forEach { semester ->
                                    SemesterButton(
                                        text = if (semester == 0) "Tümü" else "$semester. Yarıyıl",
                                        selected = selectedSemester == semester,
                                        onClick = { selectedSemester = semester }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    if (isDesktop) {
                        // Desktop Grid View
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 380.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(filteredExams) { exam ->
                                ExamRow(
                                    exam = exam,
                                    course = courses[exam.courseId],
                                    rooms = rooms,
                                    users = users,
                                    isProctor = isProctor,
                                    proctorId = proctorId
                                )
                            }
                        }
                    } else {
                        // Mobile List View
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredExams) { exam ->
                                ExamRow(
                                    exam = exam,
                                    course = courses[exam.courseId],
                                    rooms = rooms,
                                    users = users,
                                    isProctor = isProctor,
                                    proctorId = proctorId
                                )
                            }
                        }
                    }

                    if (filteredExams.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text("Aranan kriterlerde sınav bulunamadı.", color = CorporateColors.Muted)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SemesterButton(text: String, selected: Boolean, onClick: () -> Unit) {
        if (selected) {
            Button(
                onClick = onClick,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(backgroundColor = CorporateColors.Primary),
                elevation = ButtonDefaults.elevation(0.dp)
            ) {
                Text(text, color = Color.White)
            }
        } else {
            OutlinedButton(
                onClick = onClick,
                shape = MaterialTheme.shapes.medium,
                border = ButtonDefaults.outlinedBorder.copy(width = 1.dp)
            ) {
                Text(text, color = CorporateColors.Muted)
            }
        }
    }

    @Composable
    private fun ExamRow(
        exam: Exam,
        course: Course?,
        rooms: Map<String, Room>,
        users: Map<String, User>,
        isProctor: Boolean,
        proctorId: String?
    ) {
        val visibleAssignments = if (isProctor) {
            exam.assignments.filter { it.proctorId == proctorId }
        } else {
            exam.assignments
        }
        val roomNames = visibleAssignments.mapNotNull { rooms[it.roomId]?.name }
        val proctorNames = visibleAssignments.mapNotNull { users[it.proctorId]?.name }

        CorporateCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(course?.code ?: exam.courseId, style = MaterialTheme.typography.h3, color = CorporateColors.Ink)
                        if (!isProctor) StatusPill("${course?.semester ?: "-"}. YY", CorporateColors.Steel)
                    }
                    Text(course?.name ?: "Ders Adı", style = MaterialTheme.typography.body1, color = CorporateColors.Ink)
                    
                    DividerLine()
                    
                    Row(Modifier.fillMaxWidth()) {
                        Cell("Tarih", examDateLabel(exam.date), Modifier.weight(1f))
                        Cell("Saat", slotLabel(exam.slotId), Modifier.weight(1f))
                    }
                    
                    Row(Modifier.fillMaxWidth()) {
                        Cell("Salon", roomNames.joinToString(", "), Modifier.weight(1f))
                        if (!isProctor) Cell("Gözetmen", proctorNames.joinToString(", "), Modifier.weight(1f))
                    }
                }
                
                IconButton(
                    onClick = {
                        addToCalendar(
                            title = "${course?.code ?: "Sınav"} - ${course?.name ?: ""}",
                            description = "Salon: ${roomNames.joinToString(", ")}",
                            startTime = exam.date
                        )
                    }
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = "Takvime ekle", tint = CorporateColors.Primary)
                }
            }
        }
    }

    @Composable
    private fun Cell(label: String, value: String, modifier: Modifier = Modifier) {
        Column(modifier.padding(vertical = 4.dp)) {
            Text(label, style = MaterialTheme.typography.caption, color = CorporateColors.Muted)
            Text(value.ifBlank { "-" }, style = MaterialTheme.typography.body2, color = CorporateColors.Ink, fontWeight = FontWeight.SemiBold)
        }
    }
}
