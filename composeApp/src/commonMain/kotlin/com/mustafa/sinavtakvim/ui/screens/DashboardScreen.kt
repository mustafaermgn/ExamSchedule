package com.mustafa.sinavtakvim.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.mustafa.sinavtakvim.shared.algorithms.SolverResult
import com.mustafa.sinavtakvim.shared.data.repository.ExamRepository
import com.mustafa.sinavtakvim.shared.models.Course
import com.mustafa.sinavtakvim.shared.models.Exam
import com.mustafa.sinavtakvim.shared.models.Room
import com.mustafa.sinavtakvim.shared.models.User
import com.mustafa.sinavtakvim.shared.utils.examDateLabel
import com.mustafa.sinavtakvim.shared.utils.slotLabel
import com.mustafa.sinavtakvim.ui.components.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class DashboardScreen : Screen {
    @Composable
    override fun Content() {
        val repository = koinInject<ExamRepository>()
        val scope = rememberCoroutineScope()
        var courses by remember { mutableStateOf<List<Course>>(emptyList()) }
        var rooms by remember { mutableStateOf<List<Room>>(emptyList()) }
        var proctors by remember { mutableStateOf<List<User>>(emptyList()) }
        var exams by remember { mutableStateOf<List<Exam>>(emptyList()) }
        var latestResult by remember { mutableStateOf<SolverResult?>(null) }

        suspend fun load() {
            courses = repository.getCourses()
            rooms = repository.getRooms()
            proctors = repository.getProctors()
            exams = repository.getExams()
        }

        LaunchedEffect(Unit) {
            load()
        }

        val courseMap = courses.associateBy { it.id }
        val roomMap = rooms.associateBy { it.id }
        val plannedStudents = exams.mapNotNull { courseMap[it.courseId]?.studentCount }.sum()
        val assignedCapacity = exams.flatMap { it.assignments }.sumOf { roomMap[it.roomId]?.capacity ?: 0 }
        val utilization = if (assignedCapacity == 0) 0f else plannedStudents.toFloat() / assignedCapacity
        val plannedRatio = if (courses.isEmpty()) 0f else exams.size.toFloat() / courses.size
        val cleanPlan = latestResult?.violations?.isEmpty() != false

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val isDesktop = maxWidth > 800.dp

            Scaffold(
                backgroundColor = Color.Transparent
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(if (isDesktop) 32.dp else 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(modifier = Modifier.widthIn(max = 1200.dp)) {
                        PageHeader(
                            title = "Operasyon Paneli",
                            subtitle = "Akademik dönem: 2026 Bahar",
                            trailing = {
                                StatusPill(
                                    text = if (cleanPlan) "Plan uygun" else "Uyarı var",
                                    color = if (cleanPlan) CorporateColors.Success else CorporateColors.Risk
                                )
                            }
                        )

                        Spacer(Modifier.height(24.dp))

                        if (isDesktop) {
                            DesktopDashboardContent(
                                courses = courses,
                                rooms = rooms,
                                exams = exams,
                                proctors = proctors,
                                utilization = utilization,
                                plannedRatio = plannedRatio,
                                plannedStudents = plannedStudents,
                                assignedCapacity = assignedCapacity,
                                latestResult = latestResult,
                                courseMap = courseMap,
                                roomMap = roomMap,
                                onRefresh = { scope.launch { load() } }
                            )
                        } else {
                            MobileDashboardContent(
                                courses = courses,
                                rooms = rooms,
                                exams = exams,
                                proctors = proctors,
                                utilization = utilization,
                                plannedRatio = plannedRatio,
                                plannedStudents = plannedStudents,
                                assignedCapacity = assignedCapacity,
                                latestResult = latestResult,
                                courseMap = courseMap,
                                roomMap = roomMap,
                                onRefresh = { scope.launch { load() } }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun DesktopDashboardContent(
        courses: List<Course>,
        rooms: List<Room>,
        exams: List<Exam>,
        proctors: List<User>,
        utilization: Float,
        plannedRatio: Float,
        plannedStudents: Int,
        assignedCapacity: Int,
        latestResult: SolverResult?,
        courseMap: Map<String, Course>,
        roomMap: Map<String, Room>,
        onRefresh: () -> Unit
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            // Metrics Row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetricCard("Ders", courses.size.toString(), "planlama havuzu", CorporateColors.Primary, Modifier.weight(1f))
                MetricCard("Salon", rooms.size.toString(), "aktif kapasite", CorporateColors.Steel, Modifier.weight(1f))
                MetricCard("Sınav", exams.size.toString(), "takvime alınan", CorporateColors.Amber, Modifier.weight(1f))
                MetricCard("Gözetmen", proctors.size.toString(), "uygun personel", CorporateColors.Risk, Modifier.weight(1f))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                // Left Column: Health and Actions
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    CorporateCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle("Kısıt Sağlığı", "Kapasite ve personel kontrolü", Modifier.weight(1f))
                            Text("${plannedStudents}/${assignedCapacity}", style = MaterialTheme.typography.body2, color = CorporateColors.Muted)
                        }
                        Spacer(Modifier.height(14.dp))
                        ProgressRow("Salon doluluk oranı", utilization, CorporateColors.Primary)
                        Spacer(Modifier.height(12.dp))
                        ProgressRow("Planlanan ders oranı", plannedRatio, CorporateColors.Steel)
                        
                        latestResult?.let {
                            Spacer(Modifier.height(12.dp))
                            DividerLine()
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                SmallStat("Motor", it.algorithmName)
                                SmallStat("Süre", "${it.executionTimeMs} ms")
                                SmallStat("Doğruluk", "%${(it.accuracy * 100).toInt()}")
                            }
                        }
                    }

                    Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth().height(48.dp), shape = MaterialTheme.shapes.medium, colors = ButtonDefaults.buttonColors(backgroundColor = Color.White)) {
                        Text("Veriyi Yenile", color = CorporateColors.Primary)
                    }
                }

                // Right Column: Upcoming Sessions
                Column(Modifier.weight(1f)) {
                    SectionTitle("Yaklaşan Oturumlar", "İlk dört sınav")
                    Spacer(Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        exams.take(4).forEach { exam ->
                            ExamSummaryRow(exam, courseMap, roomMap)
                        }
                        if (exams.isEmpty()) {
                            CorporateCard(Modifier.fillMaxWidth()) {
                                Text("Takvim henüz oluşturulmadı.", color = CorporateColors.Ink)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MobileDashboardContent(
        courses: List<Course>,
        rooms: List<Room>,
        exams: List<Exam>,
        proctors: List<User>,
        utilization: Float,
        plannedRatio: Float,
        plannedStudents: Int,
        assignedCapacity: Int,
        latestResult: SolverResult?,
        courseMap: Map<String, Course>,
        roomMap: Map<String, Room>,
        onRefresh: () -> Unit
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Ders", courses.size.toString(), "havuz", CorporateColors.Primary, Modifier.weight(1f))
                    MetricCard("Salon", rooms.size.toString(), "kapasite", CorporateColors.Steel, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Sınav", exams.size.toString(), "planlanan", CorporateColors.Amber, Modifier.weight(1f))
                    MetricCard("Gözetmen", proctors.size.toString(), "personel", CorporateColors.Risk, Modifier.weight(1f))
                }
            }
            item {
                CorporateCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Kısıt Sağlığı", "Genel durum", Modifier.weight(1f))
                        Text("${plannedStudents}/${assignedCapacity}", style = MaterialTheme.typography.body2, color = CorporateColors.Muted)
                    }
                    Spacer(Modifier.height(14.dp))
                    ProgressRow("Salon doluluk", utilization, CorporateColors.Primary)
                    Spacer(Modifier.height(12.dp))
                    ProgressRow("Planlanan ders", plannedRatio, CorporateColors.Steel)
                }
            }
            item {
                Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth().height(44.dp), colors = ButtonDefaults.buttonColors(backgroundColor = Color.White)) {
                    Text("Yenile", color = CorporateColors.Primary)
                }
            }
            item {
                SectionTitle("Yaklaşan Oturumlar", "İlk sınavlar")
            }
            items(exams.take(4)) { exam ->
                ExamSummaryRow(exam, courseMap, roomMap)
            }
        }
    }

    @Composable
    private fun SmallStat(label: String, value: String) {
        Column {
            Text(label, style = MaterialTheme.typography.body2, color = CorporateColors.Muted)
            Text(value, style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.SemiBold), color = CorporateColors.Ink)
        }
    }

    @Composable
    private fun ExamSummaryRow(
        exam: Exam,
        courseMap: Map<String, Course>,
        roomMap: Map<String, Room>
    ) {
        val course = courseMap[exam.courseId]
        val roomsText = exam.assignments.mapNotNull { roomMap[it.roomId]?.name }.joinToString(", ")
        CorporateCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(course?.code ?: exam.courseId, style = MaterialTheme.typography.h3, color = CorporateColors.Ink)
                    Text(course?.name ?: "Ders bilgisi", style = MaterialTheme.typography.body1, color = CorporateColors.Ink)
                    Text(roomsText, style = MaterialTheme.typography.body2, color = CorporateColors.Muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(examDateLabel(exam.date), style = MaterialTheme.typography.body2, color = CorporateColors.Muted)
                    Spacer(Modifier.height(4.dp))
                    Text(exam.slotLabel.ifBlank { slotLabel(exam.slotId) }, style = MaterialTheme.typography.h3, color = CorporateColors.Primary)
                }
            }
        }
    }
}
