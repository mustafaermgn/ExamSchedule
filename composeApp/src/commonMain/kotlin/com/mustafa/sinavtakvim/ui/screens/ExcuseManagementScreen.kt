package com.mustafa.sinavtakvim.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.mustafa.sinavtakvim.shared.data.repository.ExamRepository
import com.mustafa.sinavtakvim.shared.models.User
import com.mustafa.sinavtakvim.shared.utils.examDateLabel
import com.mustafa.sinavtakvim.ui.components.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class ExcuseManagementScreen : Screen {
    @Composable
    override fun Content() {
        val repository = koinInject<ExamRepository>()
        val scope = rememberCoroutineScope()
        var users by remember { mutableStateOf<List<User>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }

        suspend fun load() {
            isLoading = true
            users = repository.getUsers()
            isLoading = false
        }

        LaunchedEffect(Unit) { load() }

        val allRequests = users.flatMap { u -> u.excuses.map { u to it } }
            .sortedByDescending { it.second.start }
        val pendingRequestCount = allRequests.count { !it.second.isApproved && !it.second.isRejected }

        ResponsiveBox(Modifier.fillMaxSize(), breakpoint = 800.dp) { isDesktop ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isDesktop) 32.dp else 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(modifier = Modifier.widthIn(max = 1200.dp)) {
                    PageHeader(
                        title = "İzin ve Mazeret Yönetimi",
                        subtitle = "Gözetmenlerden gelen talepleri inceleyin ve onaylayın",
                        trailing = { StatusPill("$pendingRequestCount Bekleyen", CorporateColors.Amber) }
                    )

                    Spacer(Modifier.height(24.dp))

                    if (isLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = CorporateColors.Primary)
                        }
                    } else if (allRequests.isEmpty()) {
                        CorporateCard(Modifier.fillMaxWidth()) {
                            Text("Henüz izin talebi bulunmuyor.", color = CorporateColors.Muted)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(allRequests) { (user, excuse) ->
                                ExcuseRequestCard(
                                    user = user,
                                    excuse = excuse,
                                    onApprove = {
                                        scope.launch {
                                            repository.updateExcuseStatus(user.uid, excuse.start, true)
                                            load()
                                        }
                                    },
                                    onReject = {
                                        scope.launch {
                                            repository.rejectExcuse(user.uid, excuse.start)
                                            load()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ExcuseRequestCard(
        user: User,
        excuse: com.mustafa.sinavtakvim.shared.models.DateRange,
        onApprove: () -> Unit,
        onReject: () -> Unit
    ) {
        val isPending = !excuse.isApproved && !excuse.isRejected
        val statusText = when {
            excuse.isApproved -> "OnaylandÄ±"
            excuse.isRejected -> "Reddedildi"
            else -> "Bekliyor"
        }
        val statusColor = when {
            excuse.isApproved -> CorporateColors.Success
            excuse.isRejected -> CorporateColors.Risk
            else -> CorporateColors.Amber
        }

        CorporateCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(user.name, style = MaterialTheme.typography.h3, color = CorporateColors.Ink)
                        Spacer(Modifier.width(8.dp))
                        if (excuse.isRejected) {
                            StatusPill("Reddedildi", CorporateColors.Risk)
                        } else
                        StatusPill(
                            if (excuse.isApproved) "Onaylandı" else "Bekliyor",
                            if (excuse.isApproved) CorporateColors.Success else CorporateColors.Amber
                        )
                    }
                    Text(user.email, style = MaterialTheme.typography.caption, color = CorporateColors.Muted)
                    Spacer(Modifier.height(12.dp))
                    DividerLine()
                    Spacer(Modifier.height(12.dp))
                    Text("Tarih: ${examDateLabel(excuse.start)}", fontWeight = FontWeight.Bold, color = CorporateColors.Ink)
                    Text("Gerekçe: ${excuse.note}", style = MaterialTheme.typography.body2, color = CorporateColors.Ink)
                }

                if (isPending) {
                    Row {
                        IconButton(onClick = onApprove) {
                            Icon(Icons.Default.Check, "Onayla", tint = CorporateColors.Success)
                        }
                        IconButton(onClick = onReject) {
                            Icon(Icons.Default.Close, "Reddet", tint = CorporateColors.Risk)
                        }
                    }
                }
            }
        }
    }
}
