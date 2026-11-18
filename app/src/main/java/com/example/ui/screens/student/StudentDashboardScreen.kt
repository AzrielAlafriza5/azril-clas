package com.example.ui.screens.student

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.components.AzrilCard
import com.example.ui.components.AzrilEmptyState
import com.example.ui.components.AzrilMintCard
import com.example.ui.components.AzrilSkeletonList
import com.example.ui.components.AzrilStatCard
import com.example.ui.components.AzrilStatusBadge
import com.example.ui.model.Assignment
import com.example.ui.model.AttendanceSessionState
import com.example.ui.model.AttendanceStatus
import com.example.ui.model.ScreenLoadState
import com.example.ui.navigation.StudentDestination
import com.example.ui.screens.ranking.StudentRankingDashboardWidget
import com.example.ui.state.AzrilUiState
import com.example.ui.theme.Amber50
import com.example.ui.theme.Amber700
import com.example.ui.theme.BorderMint
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSubtle
import com.example.ui.theme.InkText
import com.example.ui.theme.LightGreenSecondary
import com.example.ui.theme.LimeDeep
import com.example.ui.theme.LimeInk
import com.example.ui.theme.LimePrimary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Rose50
import com.example.ui.theme.Rose600
import com.example.ui.theme.SoftMintBg

@Composable
fun StudentDashboardScreen(
    uiState: AzrilUiState,
    isExpanded: Boolean,
    onNavigate: (StudentDestination) -> Unit
) {
    if (uiState.loadState is ScreenLoadState.Loading) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            AzrilSkeletonList(count = 4)
        }
        return
    }

    val user = uiState.currentUser ?: return
    val activeAssignments = uiState.assignments.filter { it.isActive }
    val mySubmissions = uiState.submissions.filter { it.studentId == user.id }
    val pendingAssignments = activeAssignments.filter { a -> mySubmissions.none { it.assignmentId == a.id } }
    val openSessions = uiState.attendanceSessions.filter { it.state == AttendanceSessionState.OPEN }
    val myAttendance = uiState.attendanceRecords.filter { it.studentId == user.id }
    val attendancePct = if (myAttendance.isEmpty()) 0 else {
        (myAttendance.count { it.status == AttendanceStatus.HADIR } * 100) / myAttendance.size
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Airy Minimalist Greeting & Student Identity Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Halo, selamat belajar 👋",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSubtle
                )
                Text(
                    text = user.fullName,
                    style = MaterialTheme.typography.headlineLarge,
                    color = InkText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AzrilStatusBadge(
                        text = "NIM ${user.nim ?: "-"}",
                        containerColor = DarkCardBg,
                        contentColor = LimePrimary
                    )
                    AzrilStatusBadge(
                        text = user.studyProgramName ?: "Program Studi",
                        containerColor = LightGreenSecondary,
                        contentColor = LimeInk
                    )
                    user.className?.takeIf { it.isNotBlank() }?.let { cls ->
                        AzrilStatusBadge(
                            text = cls,
                            containerColor = PureWhite,
                            contentColor = InkText
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(LimePrimary)
                    .clickable { onNavigate(StudentDestination.PROFILE) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.fullName.take(2).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = DarkCardBg
                )
            }
        }

        // 2. Signature Black Card Ranking Widget (#171A17 + Lime Green #B5D45D)
        StudentRankingDashboardWidget(
            uiState = uiState,
            onOpenRanking = { onNavigate(StudentDestination.RANKING) }
        )

        // Active Attendance Banner (Light Green highlight when a session is OPEN)
        if (openSessions.isNotEmpty()) {
            AzrilMintCard(contentPadding = PaddingValues(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        AzrilStatusBadge("ABSENSI DIBUKA", DarkCardBg, LimePrimary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = openSessions.first().meetingTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = InkText
                        )
                        Text(
                            text = "${openSessions.first().method.label} • ${openSessions.first().openTime} - ${openSessions.first().closeTime} WIB",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = { onNavigate(StudentDestination.ATTENDANCE) },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkCardBg, contentColor = LimePrimary)
                    ) {
                        Text("Absen")
                    }
                }
            }
        }

        // 3. Ringkasan Kehadiran & Tugas (Modern Rounded Stat Grid)
        val cols = if (isExpanded) 4 else 2
        val statCards = listOf(
            StudentKpiSpec("Kehadiran", "$attendancePct%", "${myAttendance.size} pertemuan", Icons.Rounded.FactCheck, mint = true),
            StudentKpiSpec("Tugas Aktif", "${activeAssignments.size}", "${mySubmissions.size} dikumpulkan", Icons.AutoMirrored.Rounded.Assignment),
            StudentKpiSpec("Belum Kumpul", "${pendingAssignments.size}", "Perlu diselesaikan", Icons.Rounded.WarningAmber, isAlert = pendingAssignments.isNotEmpty()),
            StudentKpiSpec("Sesi Aktif", "${openSessions.size}", "Absensi terbuka", Icons.Rounded.HourglassTop)
        )
        statCards.chunked(cols).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { kpi ->
                    AzrilStatCard(
                        label = kpi.label,
                        value = kpi.value,
                        helper = kpi.helper,
                        icon = kpi.icon,
                        accentColor = if (kpi.isAlert) Rose600 else LimeInk,
                        accentBg = if (kpi.isAlert) Rose50 else LightGreenSecondary,
                        mintVariant = kpi.mint,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 4. Deadline Tugas Terdekat & Jadwal Perkuliahan
        if (isExpanded) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StudentUpcomingDeadlinesCard(pendingAssignments, onNavigate, Modifier.weight(1f))
                StudentWeeklyScheduleCard(uiState, onNavigate, Modifier.weight(1f))
            }
        } else {
            StudentUpcomingDeadlinesCard(pendingAssignments, onNavigate, Modifier.fillMaxWidth())
            StudentWeeklyScheduleCard(uiState, onNavigate, Modifier.fillMaxWidth())
        }

        // 5. Aktivitas Terbaru (Real attendance, submissions, & point history)
        StudentRecentActivityCard(uiState, onNavigate)

        Spacer(modifier = Modifier.height(12.dp))
    }
}

private data class StudentKpiSpec(
    val label: String,
    val value: String,
    val helper: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val mint: Boolean = false,
    val isAlert: Boolean = false
)

@Composable
private fun StudentUpcomingDeadlinesCard(
    pendingAssignments: List<Assignment>,
    onNavigate: (StudentDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    AzrilCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(LightGreenSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, tint = DarkCardBg, modifier = Modifier.size(18.dp))
                }
                Text("Deadline Tugas Terdekat", style = MaterialTheme.typography.titleMedium, color = InkText)
            }
            Text(
                text = "Semua",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = LimeDeep,
                modifier = Modifier.clickable { onNavigate(StudentDestination.ASSIGNMENTS) }
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        if (pendingAssignments.isEmpty()) {
            Surface(
                color = SoftMintBg,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Belum ada deadline tugas aktif yang belum dikumpulkan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            pendingAssignments.take(3).forEachIndexed { idx, task ->
                if (idx > 0) HorizontalDivider(color = BorderMint.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(StudentDestination.ASSIGNMENTS) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(task.title, style = MaterialTheme.typography.titleSmall, color = InkText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${task.className} • Tenggat: ${task.deadline}", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    AzrilStatusBadge(task.submissionMethod.label, Amber50, Amber700)
                }
            }
        }
    }
}

@Composable
private fun StudentWeeklyScheduleCard(
    uiState: AzrilUiState,
    onNavigate: (StudentDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    AzrilCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(LightGreenSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = DarkCardBg, modifier = Modifier.size(18.dp))
                }
                Text("Jadwal Perkuliahan", style = MaterialTheme.typography.titleMedium, color = InkText)
            }
            Text(
                text = "Lihat Semua",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = LimeDeep,
                modifier = Modifier.clickable { onNavigate(StudentDestination.MY_SCHEDULE) }
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        if (uiState.schedules.isEmpty()) {
            AzrilEmptyState(
                title = "Belum Ada Jadwal Minggu Ini",
                description = "Jadwal perkuliahan daring dan luring untuk kelas Anda akan tampil di sini.",
                icon = Icons.Rounded.CalendarMonth
            )
        } else {
            uiState.schedules.take(3).forEachIndexed { idx, s ->
                if (idx > 0) HorizontalDivider(color = BorderMint.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(s.courseTitle, style = MaterialTheme.typography.titleSmall, color = InkText)
                        Text("${s.dateLabel} • ${s.startTime} - ${s.endTime} WIB", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
                    }
                    AzrilStatusBadge(
                        text = s.method.label,
                        containerColor = if (s.method.name == "DARING") DarkCardBg else LightGreenSecondary,
                        contentColor = if (s.method.name == "DARING") LimePrimary else LimeInk
                    )
                }
            }
        }
    }
}

@Composable
private fun StudentRecentActivityCard(
    uiState: AzrilUiState,
    onNavigate: (StudentDestination) -> Unit
) {
    val user = uiState.currentUser ?: return
    val myPoints = uiState.rankingPoints.filter { it.studentId == user.id }.take(4)
    val mySubmissions = uiState.submissions.filter { it.studentId == user.id }.take(3)

    AzrilCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(DarkCardBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.History, contentDescription = null, tint = LimePrimary, modifier = Modifier.size(18.dp))
                }
                Text("Aktivitas Terbaru", style = MaterialTheme.typography.titleMedium, color = InkText)
            }
            Text(
                text = "Nilai & Feedback",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = LimeDeep,
                modifier = Modifier.clickable { onNavigate(StudentDestination.GRADES_FEEDBACK) }
            )
        }
        Spacer(modifier = Modifier.height(14.dp))

        if (myPoints.isEmpty() && mySubmissions.isEmpty()) {
            Surface(
                color = SoftMintBg,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderMint),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Rounded.EventAvailable, contentDescription = null, tint = LimeDeep)
                    Text(
                        text = "Belum ada aktivitas akademik tercatat. Mulai isi absensi atau kumpulkan tugas untuk melihat aktivitas dan perolehan poin Anda di sini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted
                    )
                }
            }
        } else {
            myPoints.forEachIndexed { idx, tx ->
                if (idx > 0) HorizontalDivider(color = BorderMint.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(tx.description, style = MaterialTheme.typography.titleSmall, color = InkText)
                        Text("${tx.sourceType.label} • ${tx.updatedAt}", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
                    }
                    AzrilStatusBadge(
                        text = if (tx.points >= 0) "+${tx.points} pts" else "${tx.points} pts",
                        containerColor = LightGreenSecondary,
                        contentColor = LimeInk
                    )
                }
            }
        }
    }
}
