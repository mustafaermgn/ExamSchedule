package com.mustafa.sinavtakvim.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.mustafa.sinavtakvim.shared.algorithms.DPSolver
import com.mustafa.sinavtakvim.shared.algorithms.GreedySolver
import com.mustafa.sinavtakvim.shared.algorithms.SolverResult
import com.mustafa.sinavtakvim.shared.data.repository.ExamRepository
import com.mustafa.sinavtakvim.shared.models.Course
import com.mustafa.sinavtakvim.shared.models.Exam
import com.mustafa.sinavtakvim.shared.models.Room
import com.mustafa.sinavtakvim.shared.models.SlotConfig
import com.mustafa.sinavtakvim.shared.models.User
import com.mustafa.sinavtakvim.shared.utils.buildScheduleExcelXml
import com.mustafa.sinavtakvim.shared.utils.buildSchedulePdf
import com.mustafa.sinavtakvim.shared.utils.examDateLabel
import com.mustafa.sinavtakvim.shared.utils.saveReportFile
import com.mustafa.sinavtakvim.shared.utils.slotLabel
import com.mustafa.sinavtakvim.ui.components.CorporateCard
import com.mustafa.sinavtakvim.ui.components.CorporateColors
import com.mustafa.sinavtakvim.ui.components.DividerLine
import com.mustafa.sinavtakvim.ui.components.MetricCard
import com.mustafa.sinavtakvim.ui.components.PageHeader
import com.mustafa.sinavtakvim.ui.components.SectionTitle
import com.mustafa.sinavtakvim.ui.components.StatusPill
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
        var users by remember { mutableStateOf<List<User>>(emptyList()) }
        var savedExams by remember { mutableStateOf<List<Exam>>(emptyList()) }

        var selected by remember { mutableIntStateOf(0) }
        var results by remember { mutableStateOf<List<SolverResult>>(emptyList()) }
        var busy by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf("") }
        var examType by remember { mutableStateOf("VIZE") }
        var academicTerm by remember { mutableStateOf("2026 Bahar") }
        var slotTimesText by remember { mutableStateOf("09:00,11:00,14:00,16:00") }
        var mobileStep by remember { mutableStateOf("KURULUM") }

        suspend fun load() {
            courses = repository.getCourses().sortedBy { it.code }
            rooms = repository.getRooms().sortedByDescending { it.capacity }
            proctors = repository.getProctors().sortedBy { it.name }
            users = repository.getUsers()
            savedExams = repository.getExams()
            slotTimesText = repository.getSlotConfig().slotTimes.joinToString(",")
        }

        fun SolverResult.withMetadata(): SolverResult {
            return copy(
                exams = exams.map { exam ->
                    exam.copy(
                        id = "${examType.lowercase()}_${exam.id}",
                        examType = examType,
                        academicTerm = academicTerm.trim().ifBlank { "2026 Bahar" }
                    )
                }
            )
        }

        fun SolverResult.withRuleWarnings(): SolverResult {
            val dynamicWarnings = buildList {
                if (metrics.dailySemesterLimitWarnings > 0) {
                    add("Aynı gün 2 sınav limitini aşan ${metrics.dailySemesterLimitWarnings} dönem kaydı var.")
                }
                if (metrics.proctorLoadImbalance > 2) {
                    add("Gözetmen görev dağılımı dengesiz (yük farkı: ${metrics.proctorLoadImbalance}).")
                }
            }
            return copy(violations = violations + dynamicWarnings)
        }

        suspend fun runAnalysis() {
            busy = true
            message = ""
            val activeCourses = repository.getCourses().filter { it.studentCount > 0 }
            val activeRooms = repository.getRooms()
            val activeProctors = repository.getProctors()
            val slotTimes = parseSlotTimes(slotTimesText)

            if (activeCourses.isEmpty()) {
                results = emptyList()
                message = "Planlama için önce derslere öğrenci listesi yükleyin."
            } else if (activeRooms.isEmpty() || activeProctors.isEmpty()) {
                results = emptyList()
                message = "Planlama için salon ve gözetmen havuzu tamamlanmalıdır."
            } else {
                results = listOf(
                    DPSolver().solve(activeCourses, activeRooms, activeProctors, slotTimes).withMetadata().withRuleWarnings(),
                    GreedySolver().solve(activeCourses, activeRooms, activeProctors, slotTimes).withMetadata().withRuleWarnings()
                )
                selected = 0
                message = "Analiz tamamlandı. Uygulanacak sonucu seçebilirsiniz."
            }
            busy = false
            load()
        }

        suspend fun saveSlots() {
            val parsed = parseSlotTimes(slotTimesText)
            repository.saveSlotConfig(SlotConfig(slotTimes = parsed))
            slotTimesText = parsed.joinToString(",")
            message = "Oturum saatleri kaydedildi."
        }

        suspend fun applySelectedPlan() {
            val chosen = results.getOrNull(selected)?.withMetadata()
            if (chosen == null) {
                message = "Önce analiz çalıştırıp bir sonuç seçin."
                return
            }
            repository.saveExams(chosen.exams)
            savedExams = chosen.exams
            message = "${chosen.algorithmName} sonucu sınav takvimine uygulandı."
        }

        suspend fun exportSchedule(isPdf: Boolean) {
            try {
                val exams = savedExams.ifEmpty { repository.getExams() }.ifEmpty { results.getOrNull(selected)?.withMetadata()?.exams.orEmpty() }
                if (exams.isEmpty()) {
                    message = "Dışa aktarmak için önce bir sınav programı oluşturun."
                    return
                }
                val courseMap = repository.getCourses().associateBy { it.id }
                val roomMap = repository.getRooms().associateBy { it.id }
                val userMap = repository.getUsers().associateBy { it.uid }
                val extension = if (isPdf) "pdf" else "xml"
                val fileName = "sinav_programi_${examType.lowercase()}_${academicTerm.fileSafe()}.$extension"
                val bytes = withContext(Dispatchers.Default) {
                    if (isPdf) {
                        buildSchedulePdf(exams, courseMap, roomMap, userMap)
                    } else {
                        buildScheduleExcelXml(exams, courseMap, roomMap, userMap)
                    }
                }
                val path = saveReportFile(fileName, bytes)
                message = "Program dışa aktarıldı: $path"
            } catch (t: Throwable) {
                message = "Dışa aktarma başarısız: ${t.message ?: "bilinmeyen hata"}"
            }
        }

        LaunchedEffect(Unit) { load() }

        val activeCourses = courses.filter { it.studentCount > 0 }
        val totalStudents = activeCourses.sumOf { it.studentCount }
        val totalCapacity = rooms.sumOf { it.capacity }
        val selectedResult = results.getOrNull(selected)?.withMetadata()
        val previewExams = savedExams.ifEmpty { selectedResult?.exams.orEmpty() }
        val courseMap = courses.associateBy { it.id }
        val roomMap = rooms.associateBy { it.id }
        val userMap = users.associateBy { it.uid }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val isDesktop = maxWidth > 900.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isDesktop) 32.dp else 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(modifier = Modifier.widthIn(max = 1300.dp)) {
                    PageHeader(
                        title = "Sınav Planlama",
                        subtitle = "DP ve sezgisel algoritmaları karşılaştırın, sonucu seçip resmi programı üretin.",
                        trailing = {
                            StatusPill(
                                text = if (savedExams.isNotEmpty()) "Takvim yayına hazır" else "Analiz bekliyor",
                                color = if (savedExams.isNotEmpty()) CorporateColors.Success else CorporateColors.Amber
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
                                    MetricCard("Planlanacak ders", activeCourses.size.toString(), "$totalStudents öğrenci", CorporateColors.Primary, Modifier.weight(1f))
                                    MetricCard("Salon kapasitesi", totalCapacity.toString(), "${rooms.size} salon", CorporateColors.Steel, Modifier.weight(1f))
                                    MetricCard("Gözetmen", proctors.size.toString(), "uygun havuz", CorporateColors.Amber, Modifier.weight(1f))
                                    MetricCard("Oturum", previewExams.size.toString(), "seçili program", CorporateColors.Risk, Modifier.weight(1f))
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        MetricCard("Ders", activeCourses.size.toString(), "$totalStudents öğrenci", CorporateColors.Primary, Modifier.weight(1f))
                                        MetricCard("Kapasite", totalCapacity.toString(), "${rooms.size} salon", CorporateColors.Steel, Modifier.weight(1f))
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        MetricCard("Gözetmen", proctors.size.toString(), "havuz", CorporateColors.Amber, Modifier.weight(1f))
                                        MetricCard("Oturum", previewExams.size.toString(), "program", CorporateColors.Risk, Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        item {
                            PlanningControlCard(
                                examType = examType,
                                onExamTypeChange = { examType = it },
                                academicTerm = academicTerm,
                                onAcademicTermChange = { academicTerm = it },
                                slotTimesText = slotTimesText,
                                onSlotTimesChange = { slotTimesText = it },
                                busy = busy,
                                hasResults = results.isNotEmpty(),
                                hasSchedule = previewExams.isNotEmpty(),
                                compact = !isDesktop,
                                onRun = { scope.launch { runAnalysis() } },
                                onSaveSlots = { scope.launch { saveSlots() } },
                                onApply = { scope.launch { applySelectedPlan() } },
                                onExportExcel = { scope.launch { exportSchedule(isPdf = false) } },
                                onExportPdf = { scope.launch { exportSchedule(isPdf = true) } }
                            )
                        }
                        if (!isDesktop) {
                            item {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SegmentedChoice("Kurulum", mobileStep == "KURULUM", Modifier.widthIn(min = 110.dp)) { mobileStep = "KURULUM" }
                                    SegmentedChoice("Sonuçlar", mobileStep == "SONUÇ", Modifier.widthIn(min = 110.dp)) { mobileStep = "SONUÇ" }
                                    SegmentedChoice("Önizleme", mobileStep == "ÖNİZLEME", Modifier.widthIn(min = 110.dp)) { mobileStep = "ÖNİZLEME" }
                                }
                            }
                        }

                        if (results.isNotEmpty() && (isDesktop || mobileStep == "SONUÇ")) {
                            item {
                                if (isDesktop) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                        ComparisonChart(results, Modifier.weight(1f))
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                            results.forEachIndexed { index, result ->
                                                ResultCard(
                                                    result = result,
                                                    selected = selected == index,
                                                    onSelect = { selected = index }
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        ComparisonChart(results, Modifier.fillMaxWidth())
                                        results.forEachIndexed { index, result ->
                                            ResultCard(
                                                result = result,
                                                selected = selected == index,
                                                onSelect = { selected = index }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (isDesktop || mobileStep == "ÖNİZLEME") item {
                            SchedulePreview(
                                exams = previewExams,
                                courses = courseMap,
                                rooms = roomMap,
                                users = userMap
                            )
                        }

                        if (message.isNotBlank()) {
                            item {
                                StatusPill(
                                    text = message,
                                    color = if (message.contains("önce", ignoreCase = true) || message.contains("tamamlanmalıdır", ignoreCase = true)) CorporateColors.Risk else CorporateColors.Success
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PlanningControlCard(
        examType: String,
        onExamTypeChange: (String) -> Unit,
        academicTerm: String,
        onAcademicTermChange: (String) -> Unit,
        slotTimesText: String,
        onSlotTimesChange: (String) -> Unit,
        busy: Boolean,
        hasResults: Boolean,
        hasSchedule: Boolean,
        compact: Boolean,
        onRun: () -> Unit,
        onSaveSlots: () -> Unit,
        onApply: () -> Unit,
        onExportExcel: () -> Unit,
        onExportPdf: () -> Unit
    ) {
        CorporateCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Planlama kontrol merkezi", "Sınav türünü seçin, algoritmaları çalıştırın ve resmi programı üretin.", Modifier.weight(1f))
                Icon(Icons.Default.Assessment, contentDescription = null, tint = CorporateColors.Primary)
            }
            Spacer(Modifier.height(18.dp))

            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SegmentedChoice("Vize", examType == "VIZE", Modifier.weight(1f)) { onExamTypeChange("VIZE") }
                        SegmentedChoice("Final", examType == "FINAL", Modifier.weight(1f)) { onExamTypeChange("FINAL") }
                    }
                    OutlinedTextField(
                        value = academicTerm,
                        onValueChange = onAcademicTermChange,
                        label = { Text("Akademik dönem") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = slotTimesText,
                        onValueChange = onSlotTimesChange,
                        label = { Text("Oturumlar (09:00,11:00,14:00)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedButton(
                        onClick = onSaveSlots,
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) { Text("Oturumları kaydet") }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SegmentedChoice("Vize", examType == "VIZE", Modifier.weight(1f)) { onExamTypeChange("VIZE") }
                    SegmentedChoice("Final", examType == "FINAL", Modifier.weight(1f)) { onExamTypeChange("FINAL") }
                    OutlinedTextField(
                        value = academicTerm,
                        onValueChange = onAcademicTermChange,
                        label = { Text("Akademik dönem") },
                        modifier = Modifier.weight(1.4f),
                        singleLine = true
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = slotTimesText,
                        onValueChange = onSlotTimesChange,
                        label = { Text("Oturum saatleri (virgülle)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedButton(onClick = onSaveSlots, modifier = Modifier.height(56.dp)) {
                        Text("Kaydet")
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onRun,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = CorporateColors.Primary)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.width(22.dp).height(22.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Analizi çalıştır", color = Color.White)
                        }
                    }

                    OutlinedButton(
                        onClick = onApply,
                        enabled = hasResults && !busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CorporateColors.Primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Seçili planı uygula", color = CorporateColors.Primary)
                    }

                    OutlinedButton(
                        onClick = onExportExcel,
                        enabled = hasSchedule,
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null, tint = CorporateColors.Primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Excel indir", color = CorporateColors.Primary)
                    }
                    OutlinedButton(
                        onClick = onExportPdf,
                        enabled = hasSchedule,
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = CorporateColors.Primary)
                        Spacer(Modifier.width(8.dp))
                        Text("PDF indir", color = CorporateColors.Primary)
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onRun,
                        enabled = !busy,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = CorporateColors.Primary)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.width(22.dp).height(22.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Analizi çalıştır", color = Color.White)
                        }
                    }

                    OutlinedButton(
                        onClick = onApply,
                        enabled = hasResults && !busy,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CorporateColors.Primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Seçili planı uygula", color = CorporateColors.Primary)
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onExportExcel,
                        enabled = hasSchedule,
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null, tint = CorporateColors.Primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Excel indir", color = CorporateColors.Primary)
                    }
                    OutlinedButton(
                        onClick = onExportPdf,
                        enabled = hasSchedule,
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = CorporateColors.Primary)
                        Spacer(Modifier.width(8.dp))
                        Text("PDF indir", color = CorporateColors.Primary)
                    }
                }
            }
        }
    }

    @Composable
    private fun SegmentedChoice(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
        Surface(
            modifier = modifier.height(56.dp).clickable(onClick = onClick),
            shape = MaterialTheme.shapes.medium,
            color = if (selected) CorporateColors.Primary else CorporateColors.Surface,
            border = BorderStroke(1.dp, if (selected) CorporateColors.Primary else CorporateColors.Border)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text, color = if (selected) Color.White else CorporateColors.Ink, fontWeight = FontWeight.Bold)
            }
        }
    }

    @Composable
    private fun ComparisonChart(results: List<SolverResult>, modifier: Modifier = Modifier) {
        val maxTime = results.maxOf { it.executionTimeMs.coerceAtLeast(1L) }.toFloat()
        CorporateCard(modifier.fillMaxWidth()) {
            SectionTitle("Performans karşılaştırması", "Çalışma süresi, doğruluk ve kapasite kullanımı")
            Spacer(Modifier.height(20.dp))
            results.forEachIndexed { index, result ->
                if (index > 0) {
                    Spacer(Modifier.height(16.dp))
                    DividerLine()
                    Spacer(Modifier.height(16.dp))
                }
                Text(result.algorithmName, style = MaterialTheme.typography.h3, color = CorporateColors.Ink)
                Spacer(Modifier.height(10.dp))
                ChartBar(
                    label = "Çalışma süresi (${result.executionTimeMs} ms)",
                    fraction = result.executionTimeMs.coerceAtLeast(1L).toFloat() / maxTime,
                    color = if (result.algorithmName.contains("DP")) CorporateColors.Primary else CorporateColors.Amber
                )
                Spacer(Modifier.height(12.dp))
                ChartBar(
                    label = "Doğruluk oranı (%${(result.accuracy * 100).toInt()})",
                    fraction = result.accuracy.toFloat(),
                    color = CorporateColors.Steel
                )
            }
        }
    }

    @Composable
    private fun ResultCard(result: SolverResult, selected: Boolean, onSelect: () -> Unit) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
            shape = MaterialTheme.shapes.medium,
            color = CorporateColors.Surface,
            border = BorderStroke(1.dp, if (selected) CorporateColors.Primary else CorporateColors.Border)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(result.algorithmName, style = MaterialTheme.typography.h3, color = CorporateColors.Ink)
                        Text("${result.metrics.scheduledCourses} ders planlandı, ${result.metrics.unscheduledCourses} ders beklemede", style = MaterialTheme.typography.body2, color = CorporateColors.Muted)
                    }
                    StatusPill(
                        text = if (selected) "Seçili" else if (result.violations.isEmpty()) "Uygun" else "${result.violations.size} uyarı",
                        color = if (selected || result.violations.isEmpty()) CorporateColors.Success else CorporateColors.Risk
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricText("Süre", "${result.executionTimeMs} ms")
                    MetricText("Doğruluk", "%${(result.accuracy * 100).toInt()}")
                    MetricText("Kayıp kapasite", result.metrics.capacityWaste.toString())
                }
                if (result.violations.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    DividerLine()
                    Spacer(Modifier.height(10.dp))
                    result.violations.take(3).forEach { warning ->
                        Text("• $warning", style = MaterialTheme.typography.body2, color = CorporateColors.Risk)
                    }
                }
            }
        }
    }

    @Composable
    private fun SchedulePreview(
        exams: List<Exam>,
        courses: Map<String, Course>,
        rooms: Map<String, Room>,
        users: Map<String, User>
    ) {
        CorporateCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Program önizlemesi", "Kaydedilen ya da seçili algoritma sonucu", Modifier.weight(1f))
                Icon(Icons.Default.Description, contentDescription = null, tint = CorporateColors.Primary)
            }
            Spacer(Modifier.height(14.dp))
            if (exams.isEmpty()) {
                Text("Henüz sınav programı oluşturulmadı.", color = CorporateColors.Muted)
            } else {
                exams.take(10).forEachIndexed { index, exam ->
                    if (index > 0) {
                        Spacer(Modifier.height(8.dp))
                        DividerLine()
                        Spacer(Modifier.height(8.dp))
                    }
                    val course = courses[exam.courseId]
                    val roomNames = exam.assignments.mapNotNull { rooms[it.roomId]?.name }.joinToString(", ")
                    val proctorNames = exam.assignments.mapNotNull { users[it.proctorId]?.name }.joinToString(", ")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${course?.code ?: exam.courseId} · ${course?.name.orEmpty()}", color = CorporateColors.Ink, fontWeight = FontWeight.Bold)
                            Text("Salon: ${roomNames.ifBlank { "-" }} · Gözetmen: ${proctorNames.ifBlank { "-" }}", style = MaterialTheme.typography.caption, color = CorporateColors.Muted)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(examDateLabel(exam.date), style = MaterialTheme.typography.caption, color = CorporateColors.Muted)
                            Text(exam.slotLabel.ifBlank { slotLabel(exam.slotId) }, color = CorporateColors.Primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MetricText(label: String, value: String) {
        Column {
            Text(label, style = MaterialTheme.typography.body2, color = CorporateColors.Muted)
            Text(value, style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Bold), color = CorporateColors.Ink)
        }
    }

    @Composable
    private fun ChartBar(label: String, fraction: Float, color: Color) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.body2, color = CorporateColors.Muted)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(12.dp).background(Color(0xFFE7ECEF), MaterialTheme.shapes.small)) {
                Box(Modifier.fillMaxWidth(fraction.coerceIn(0.04f, 1f)).height(12.dp).background(color, MaterialTheme.shapes.small))
            }
        }
    }

    private fun String.fileSafe(): String {
        return lowercase()
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
            .trim('_')
            .ifBlank { "donem" }
    }

    private fun parseSlotTimes(raw: String): List<String> {
        val regex = Regex("""^([01]\d|2[0-3]):([0-5]\d)$""")
        return raw.split(",")
            .map { it.trim() }
            .filter { it.matches(regex) }
            .distinct()
            .sorted()
            .ifEmpty { listOf("09:00", "11:00", "14:00", "16:00") }
    }
}
