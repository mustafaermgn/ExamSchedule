package com.mustafa.sinavtakvim.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.mustafa.sinavtakvim.shared.data.repository.ExamRepository
import com.mustafa.sinavtakvim.shared.models.Course
import com.mustafa.sinavtakvim.shared.models.Exam
import com.mustafa.sinavtakvim.shared.models.Room
import com.mustafa.sinavtakvim.shared.models.UserRole
import com.mustafa.sinavtakvim.shared.utils.examDateLabel
import com.mustafa.sinavtakvim.shared.utils.slotLabel
import com.mustafa.sinavtakvim.ui.components.*
import org.koin.compose.koinInject

class MapScreen(
    private val role: UserRole = UserRole.ADMIN,
    private val proctorId: String? = null
) : Screen {
    @Composable
    override fun Content() {
        val repository = koinInject<ExamRepository>()
        var rooms by remember { mutableStateOf<List<Room>>(emptyList()) }
        var exams by remember { mutableStateOf<List<Exam>>(emptyList()) }
        var courses by remember { mutableStateOf<Map<String, Course>>(emptyMap()) }
        var selectedRoomId by remember { mutableStateOf<String?>(null) }
        val isProctor = role == UserRole.PROCTOR

        LaunchedEffect(role, proctorId) {
            val allRooms = repository.getRooms()
            val allExams = repository.getExams()
            courses = repository.getCourses().associateBy { it.id }
            exams = allExams.filter { exam -> !isProctor || exam.assignments.any { it.proctorId == proctorId } }
            val visibleRoomIds = exams.flatMap { exam ->
                exam.assignments
                    .filter { !isProctor || it.proctorId == proctorId }
                    .map { it.roomId }
            }.toSet()
            rooms = if (isProctor) allRooms.filter { it.id in visibleRoomIds } else allRooms
            selectedRoomId = rooms.firstOrNull()?.id
        }

        val selectedRoom = rooms.firstOrNull { it.id == selectedRoomId } ?: rooms.firstOrNull()
        val roomExams = exams.filter { exam -> exam.assignments.any { it.roomId == selectedRoom?.id } }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val isDesktop = this.maxWidth > 900.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isDesktop) 32.dp else 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(modifier = Modifier.widthIn(max = 1400.dp)) {
                    PageHeader(
                        title = if (isProctor) "Görev Salonlarım" else "Kampüs Yerleşim Planı",
                        subtitle = if (isProctor) "Sınav görevlerinizin bulunduğu salonlar" else "Derslik konumları ve doluluk analizi",
                        trailing = { StatusPill("${rooms.size} Salon Kayıtlı", CorporateColors.Primary) }
                    )

                    Spacer(Modifier.height(24.dp))

                    if (selectedRoom != null) {
                        if (isDesktop) {
                            DesktopMapLayout(
                                rooms = rooms,
                                selectedRoom = selectedRoom,
                                roomExams = roomExams,
                                courses = courses,
                                onRoomSelect = { selectedRoomId = it.id },
                                isProctor = isProctor
                            )
                        } else {
                            MobileMapLayout(
                                rooms = rooms,
                                selectedRoom = selectedRoom,
                                roomExams = roomExams,
                                courses = courses,
                                onRoomSelect = { selectedRoomId = it.id },
                                isProctor = isProctor
                            )
                        }
                    } else {
                        CorporateCard(Modifier.fillMaxWidth()) {
                            Text(
                                if (isProctor) "Şu an için atanmış bir salonunuz bulunmuyor." else "Sistemde kayıtlı salon bulunamadı.",
                                color = CorporateColors.Muted
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun DesktopMapLayout(
        rooms: List<Room>,
        selectedRoom: Room,
        roomExams: List<Exam>,
        courses: Map<String, Course>,
        onRoomSelect: (Room) -> Unit,
        isProctor: Boolean
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // Sidebar Room List
            Column(Modifier.width(300.dp)) {
                SectionTitle("Salonlar", "Tüm binalar")
                Spacer(Modifier.height(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(rooms) { room ->
                        RoomChip(room, selected = room.id == selectedRoom.id, onClick = { onRoomSelect(room) }, isDesktop = true)
                    }
                }
            }

            // Right side Content
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                // Map
                CorporateCard(Modifier.fillMaxWidth().height(450.dp)) {
                    MapView(
                        modifier = Modifier.fillMaxSize(),
                        lat = selectedRoom.latitude,
                        lng = selectedRoom.longitude,
                        title = selectedRoom.name
                    )
                }

                // Details Row
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    CorporateCard(Modifier.weight(1f)) {
                        SectionTitle(selectedRoom.name, "${selectedRoom.building} · ${selectedRoom.floor}. Kat")
                        Spacer(Modifier.height(16.dp))
                        DividerLine()
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            MetricText("Kapasite", "${selectedRoom.capacity} Kişi")
                            MetricText("Olanaklar", selectedRoom.facilities.firstOrNull() ?: "Standart Sınav Düzeni")
                        }
                    }

                    CorporateCard(Modifier.weight(1f)) {
                        SectionTitle("Kullanım Programı", "Yaklaşan oturumlar")
                        Spacer(Modifier.height(12.dp))
                        if (roomExams.isEmpty()) {
                            Text("Boşta", color = CorporateColors.Success)
                        } else {
                            roomExams.take(3).forEach { exam ->
                                val course = courses[exam.courseId]
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(course?.code ?: "Ders", fontWeight = FontWeight.Bold)
                                    Text("${examDateLabel(exam.date)} · ${slotLabel(exam.slotId)}", style = MaterialTheme.typography.caption)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MobileMapLayout(
        rooms: List<Room>,
        selectedRoom: Room,
        roomExams: List<Exam>,
        courses: Map<String, Course>,
        onRoomSelect: (Room) -> Unit,
        isProctor: Boolean
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CorporateCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(selectedRoom.name, style = MaterialTheme.typography.h2)
                        Text("${selectedRoom.building} · ${selectedRoom.floor}. Kat", style = MaterialTheme.typography.caption)
                    }
                    StatusPill("${selectedRoom.capacity} Kap.", CorporateColors.Primary)
                }
            }

            CorporateCard(Modifier.fillMaxWidth().height(250.dp)) {
                MapView(Modifier.fillMaxSize(), selectedRoom.latitude, selectedRoom.longitude, selectedRoom.name)
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(rooms) { room ->
                    RoomChip(room, selected = room.id == selectedRoom.id, onClick = { onRoomSelect(room) }, isDesktop = false)
                }
            }

            CorporateCard(Modifier.fillMaxWidth()) {
                SectionTitle("Oturumlar", "Yaklaşan")
                roomExams.take(2).forEach { exam ->
                    val course = courses[exam.courseId]
                    DividerLine(Modifier.padding(vertical = 8.dp))
                    Text("${course?.code} - ${slotLabel(exam.slotId)}", style = MaterialTheme.typography.body2)
                }
            }
        }
    }

    @Composable
    private fun MetricText(label: String, value: String) {
        Column {
            Text(label, style = MaterialTheme.typography.caption, color = CorporateColors.Muted)
            Text(value, style = MaterialTheme.typography.body2, fontWeight = FontWeight.Bold)
        }
    }

    @Composable
    private fun RoomChip(room: Room, selected: Boolean, onClick: () -> Unit, isDesktop: Boolean) {
        val background = if (selected) CorporateColors.Primary else CorporateColors.Surface
        val titleColor = if (selected) Color.White else CorporateColors.Ink
        val captionColor = if (selected) Color.White.copy(alpha = 0.76f) else CorporateColors.Muted
        
        androidx.compose.material.Card(
            modifier = Modifier
                .then(if (isDesktop) Modifier.fillMaxWidth() else Modifier.width(160.dp))
                .height(72.dp)
                .clickable(onClick = onClick),
            elevation = 0.dp,
            shape = MaterialTheme.shapes.medium,
            backgroundColor = background
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.Center) {
                Text(room.name, style = MaterialTheme.typography.body1, fontWeight = FontWeight.Bold, color = titleColor)
                Text("${room.capacity} Kişi", style = MaterialTheme.typography.caption, color = captionColor)
            }
        }
    }
}
