package com.mustafa.sinavtakvim.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import cafe.adriel.voyager.core.screen.Screen
import com.mustafa.sinavtakvim.shared.algorithms.DPSolver
import com.mustafa.sinavtakvim.shared.algorithms.GreedySolver
import com.mustafa.sinavtakvim.shared.algorithms.SolverResult
import com.mustafa.sinavtakvim.shared.data.repository.ExamRepository
import com.mustafa.sinavtakvim.shared.models.*
import com.mustafa.sinavtakvim.shared.utils.*
import com.mustafa.sinavtakvim.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

class PlanningScreen : Screen {
    @Composable
    override fun Content() {
        val repository = koinInject<ExamRepository>()
        val scope = rememberCoroutineScope()

        var courses by remember { mutableStateOf<List<Course>>(emptyList()) }
        var rooms by remember { mutableStateOf<List<Room>>(emptyList()) }
        var proctors by remember { mutableStateOf<List<User>>(emptyList()) }
        var savedExams by remember { mutableStateOf<List<Exam>>(emptyList()) }

        var results by remember { mutableStateOf<List<SolverResult>>(emptyList()) }
        var selectedResultIndex by remember { mutableIntStateOf(0) }
        var busy by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf("") }
        var currentStep by remember { mutableStateOf(1) } // 1: Config, 2: Analyze, 3: Export

        // Config states
        var examType by remember { mutableStateOf("VIZE") }
        var academicTerm by remember { mutableStateOf("2026 Bahar") }
        var slotTimesText by remember { mutableStateOf("09:00,11:00,14:00,16:00") }

        suspend fun load() {
            courses = repository.getCourses().filter { it.studentCount > 0 }
            rooms = repository.getRooms()
            proctors = repository.getProctors()
            savedExams = repository.getExams()
            slotTimesText = repository.getSlotConfig().slotTimes.joinToString(",")
        }

        LaunchedEffect(Unit) { load() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CorporateColors.Background)
        ) {
            // Step Indicator
            Surface(elevation = 2.dp, color = CorporateColors.Surface) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    StepIcon(1, "Yapılandırma", Icons.Default.Settings, currentStep >= 1) { currentStep = 1 }
                    StepIcon(2, "Analiz", Icons.Default.Analytics, currentStep >= 2) { if (currentStep >= 2) currentStep = 2 }
                    StepIcon(3, "Sonuç", Icons.Default.CheckCircle, currentStep >= 3) { if (currentStep >= 3) currentStep = 3 }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Column(Modifier.widthIn(max = 800.dp)) {
                        when (currentStep) {
                            1 -> ConfigurationSection(
                                examType, { examType = it },
                                academicTerm, { academicTerm = it },
                                slotTimesText, { slotTimesText = it },
                                onNext = {
                                    scope.launch {
                                        busy = true
                                        val slotTimes = slotTimesText.split(",").map { it.trim() }
                                        repository.saveSlotConfig(SlotConfig(slotTimes = slotTimes))
                                        
                                        // Run auto-analysis
                                        results = listOf(
                                            DPSolver().solve(courses, rooms, proctors, slotTimes),
                                            GreedySolver().solve(courses, rooms, proctors, slotTimes)
                                        )
                                        busy = false
                                        currentStep = 2
                                    }
                                },
                                busy = busy
                            )
                            2 -> AnalysisSection(
                                results = results,
                                selectedIndex = selectedResultIndex,
                                onSelect = { selectedResultIndex = it },
                                onApply = {
                                    scope.launch {
                                        val chosen = results[selectedResultIndex]
                                        repository.saveExams(chosen.exams)
                                        message = "Program başarıyla uygulandı."
                                        currentStep = 3
                                    }
                                }
                            )
                            3 -> ExportSection(
                                onExportPdf = {
                                    scope.launch {
                                        val exams = repository.getExams()
                                        val courseMap = repository.getCourses().associateBy { it.id }
                                        val roomMap = repository.getRooms().associateBy { it.id }
                                        val userMap = repository.getUsers().associateBy { it.uid }
                                        val bytes = buildSchedulePdf(exams, courseMap, roomMap, userMap)
                                        saveReportFile("program.pdf", bytes)
                                        message = "PDF indirildi."
                                    }
                                },
                                onExportExcel = {
                                    scope.launch {
                                        val exams = repository.getExams()
                                        val courseMap = repository.getCourses().associateBy { it.id }
                                        val roomMap = repository.getRooms().associateBy { it.id }
                                        val userMap = repository.getUsers().associateBy { it.uid }
                                        val bytes = buildScheduleExcelXml(exams, courseMap, roomMap, userMap)
                                        saveReportFile("program.xml", bytes)
                                        message = "Excel indirildi."
                                    }
                                },
                                onRestart = { currentStep = 1 }
                            )
                        }
                    }
                }

                if (message.isNotBlank()) {
                    item {
                        StatusPill(text = message, color = CorporateColors.Success)
                    }
                }
            }
        }
    }

    @Composable
    private fun StepIcon(num: Int, label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
        Column(
            modifier = Modifier.clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (active) CorporateColors.Primary else CorporateColors.Muted.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (active) Color.White else CorporateColors.Muted, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (active) CorporateColors.Primary else CorporateColors.Muted)
        }
    }

    @Composable
    private fun ConfigurationSection(
        examType: String, onTypeChange: (String) -> Unit,
        term: String, onTermChange: (String) -> Unit,
        slots: String, onSlotsChange: (String) -> Unit,
        onNext: () -> Unit,
        busy: Boolean
    ) {
        CorporateCard {
            Text("1. Planlama Yapılandırması", style = MaterialTheme.typography.h3, color = CorporateColors.Ink)
            Spacer(Modifier.height(8.dp))
            Text("Sınav türünü ve oturum saatlerini belirleyin.", style = MaterialTheme.typography.body2, color = CorporateColors.Muted)
            
            Spacer(Modifier.height(24.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    modifier = Modifier.weight(1f).clickable { onTypeChange("VIZE") },
                    shape = RoundedCornerShape(8.dp),
                    color = if (examType == "VIZE") CorporateColors.PrimarySoft else CorporateColors.Background,
                    border = BorderStroke(1.dp, if (examType == "VIZE") CorporateColors.Primary else CorporateColors.Border)
                ) {
                    Box(Modifier.padding(12.dp), contentAlignment = Alignment.Center) { Text("Vize Sınavı") }
                }
                Surface(
                    modifier = Modifier.weight(1f).clickable { onTypeChange("FINAL") },
                    shape = RoundedCornerShape(8.dp),
                    color = if (examType == "FINAL") CorporateColors.PrimarySoft else CorporateColors.Background,
                    border = BorderStroke(1.dp, if (examType == "FINAL") CorporateColors.Primary else CorporateColors.Border)
                ) {
                    Box(Modifier.padding(12.dp), contentAlignment = Alignment.Center) { Text("Final Sınavı") }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(term, onTermChange, label = { Text("Akademik Dönem") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(slots, onSlotsChange, label = { Text("Oturum Saatleri (09:00, 11:00...)") }, modifier = Modifier.fillMaxWidth())
            
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(CorporateColors.Primary)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White)
                else Text("Algoritmaları Çalıştır", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }

    @Composable
    private fun AnalysisSection(
        results: List<SolverResult>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit,
        onApply: () -> Unit
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("2. Algoritma Karşılaştırması", style = MaterialTheme.typography.h2, color = CorporateColors.Ink)
            
            results.forEachIndexed { index, result ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(index) },
                    shape = RoundedCornerShape(12.dp),
                    color = CorporateColors.Surface,
                    border = BorderStroke(2.dp, if (selectedIndex == index) CorporateColors.Primary else Color.Transparent),
                    elevation = 2.dp
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selectedIndex == index, onClick = { onSelect(index) })
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(result.algorithmName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("${result.metrics.scheduledCourses} ders planlandı", style = MaterialTheme.typography.caption)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("%${(result.accuracy * 100).toInt()} Başarı", color = CorporateColors.Success, fontWeight = FontWeight.Bold)
                            Text("${result.executionTimeMs} ms", style = MaterialTheme.typography.caption)
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onApply,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(CorporateColors.Primary)
            ) {
                Text("Seçili Programı Onayla ve Uygula", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }

    @Composable
    private fun ExportSection(onExportPdf: () -> Unit, onExportExcel: () -> Unit, onRestart: () -> Unit) {
        CorporateCard {
            Text("3. İşlem Tamamlandı", style = MaterialTheme.typography.h3, color = CorporateColors.Ink)
            Spacer(Modifier.height(16.dp))
            Text("Sınav programı başarıyla oluşturuldu. Artık resmi raporları alabilirsiniz.", color = CorporateColors.Muted)
            
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onExportPdf, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Default.PictureAsPdf, null)
                    Spacer(Modifier.width(8.dp))
                    Text("PDF İndir")
                }
                OutlinedButton(onExportExcel, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Default.TableChart, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Excel İndir")
                }
            }
            
            Spacer(Modifier.height(24.dp))
            TextButton(onRestart, modifier = Modifier.fillMaxWidth()) {
                Text("Yeni Planlama Başlat", color = CorporateColors.Primary)
            }
        }
    }
}
