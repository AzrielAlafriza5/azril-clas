package com.example.ui.screens.admin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Class
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.AzrilCard
import com.example.ui.components.AzrilDarkCard
import com.example.ui.components.AzrilEmptyState
import com.example.ui.components.AzrilPageHeader
import com.example.ui.components.AzrilSkeletonList
import com.example.ui.components.AzrilStatCard
import com.example.ui.components.AzrilStatusBadge
import com.example.ui.model.AttendanceStatus
import com.example.ui.model.ScreenLoadState
import com.example.ui.model.SubmissionStatus
import com.example.ui.navigation.AdminDestination
import com.example.ui.screens.ranking.AdminRankingDashboardWidget
import com.example.ui.state.AzrilUiState
import com.example.ui.theme.Amber50
import com.example.ui.theme.Amber700
import com.example.ui.theme.BorderMint
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.DarkCardElevated
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSubtle
import com.example.ui.theme.InkText
import com.example.ui.theme.LightGreenSecondary
import com.example.ui.theme.LimeDeep
import com.example.ui.theme.LimeInk
import com.example.ui.theme.LimePrimary
import com.example.ui.theme.OnDarkMuted
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftMintBg

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminDashboardScreen(
    uiState: AzrilUiState,
    isExpanded: Boolean,
    onNavigate: (AdminDestination) -> Unit
) {
    if (uiState.loadState is ScreenLoadState.Loading) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            AzrilSkeletonList(count = 4)
        }
        return
    }

    val uncheckedCount = uiState.submissions.count {
        it.status == SubmissionStatus.AWAITING_ADMIN || it.status == SubmissionStatus.SUBMITTED
    }
    val attendanceRate = if (uiState.attendanceRecords.isEmpty()) 0 else {
        (uiState.attendanceRecords.count { it.status == AttendanceStatus.HADIR } * 100) / uiState.attendanceRecords.size
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        AzrilPageHeader(
            title = "Dashboard Akademik",
            subtitle = "Pantau mahasiswa, program studi, absensi, tugas, AI review, dan ranking dalam satu tampilan minimalis."
        )

        // Editorial Dark Overview Card (#171A17 + Lime Green #B5D45D) with Quick Actions
        AzrilDarkCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AzrilStatusBadge("RINGKASAN REAL-TIME", LimePrimary, DarkCardBg)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${uiState.students.size} Mahasiswa • ${uiState.studyPrograms.size} Program Studi",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = PureWhite
                    )
                    Text(
                        text = "Kehadiran $attendanceRate% • $uncheckedCount pengumpulan menunggu penilaian",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnDarkMuted
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val actions = listOf(
                    Triple("Data Mahasiswa & Import", Icons.Rounded.PersonAdd, AdminDestination.STUDENTS),
                    Triple("Beri XP Keaktifan", Icons.Rounded.Bolt, AdminDestination.XP_KEAKTIFAN),
                    Triple("Buka Absensi", Icons.Rounded.FactCheck, AdminDestination.ATTENDANCE),
                    Triple("Buat Tugas", Icons.Rounded.Add, AdminDestination.ASSIGNMENTS),
                    Triple("Buat Jadwal", Icons.Rounded.CalendarMonth, AdminDestination.SCHEDULES),
                    Triple("Tambah Kelas", Icons.Rounded.Class, AdminDestination.CLASSES)
                )
                actions.forEachIndexed { index, (title, icon, dest) ->
                    val isPrimaryAction = index == 0
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { onNavigate(dest) }
                            .testTag("quick_action_${dest.route}"),
                        color = if (isPrimaryAction) LimePrimary else DarkCardElevated,
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                icon,
                                contentDescription = title,
                                tint = if (isPrimaryAction) DarkCardBg else LimePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isPrimaryAction) DarkCardBg else PureWhite
                            )
                        }
                    }
                }
            }
        }

        // KPI Stat Cards (Harmonized Mix of Light Green #DCECB8 and Pure White #FFFFFF Cards)
        val statItems = listOf(
            StatSpec("Total Mahasiswa", "${uiState.students.size}", "Terdaftar aktif", Icons.Rounded.Groups, mint = true),
            StatSpec("Program Studi", "${uiState.studyPrograms.size}", "HES • PJKR • FARMASI", Icons.Rounded.School),
            StatSpec("Total Kelas", "${uiState.classes.size}", "Semester berjalan", Icons.Rounded.Class),
            StatSpec("Total Tugas", "${uiState.assignments.size}", "Penugasan dibuat", Icons.AutoMirrored.Rounded.Assignment, mint = true),
            StatSpec("Pengumpulan", "${uiState.submissions.size}", "Berkas masuk", Icons.Rounded.UploadFile),
            StatSpec("Belum Diperiksa", "$uncheckedCount", "Menunggu penilaian", Icons.Rounded.HourglassTop, isWarning = uncheckedCount > 0),
            StatSpec("Kehadiran", "$attendanceRate%", "${uiState.attendanceRecords.size} catatan absen", Icons.Rounded.FactCheck, mint = true)
        )
        val columns = if (isExpanded) 4 else 2
        statItems.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { item ->
                    AzrilStatCard(
                        label = item.label,
                        value = item.value,
                        helper = item.helper,
                        icon = item.icon,
                        accentColor = if (item.isWarning) Amber700 else LimeInk,
                        accentBg = if (item.isWarning) Amber50 else LightGreenSecondary,
                        mintVariant = item.mint,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Student Ranking Overview Widget
        AdminRankingDashboardWidget(
            uiState = uiState,
            onOpenRanking = { onNavigate(AdminDestination.RANKING) }
        )

        // Analytics Section (4 Charts styled strictly in Lime Green / Dark Card / Olive palette)
        val chartPairs = listOf(
            ChartSpec(
                title = "Statistik Pengumpulan Tugas",
                subtitle = "Distribusi status pengumpulan",
                values = listOf(
                    "Masuk" to uiState.submissions.count { it.status == SubmissionStatus.AWAITING_ADMIN || it.status == SubmissionStatus.SUBMITTED },
                    "Dinilai" to uiState.submissions.count { it.status == SubmissionStatus.GRADED },
                    "Revisi" to uiState.submissions.count { it.status == SubmissionStatus.NEEDS_REVISION },
                    "Telat" to uiState.submissions.count { it.status == SubmissionStatus.LATE }
                ),
                barColor = LimePrimary
            ),
            ChartSpec(
                title = "Persentase Kehadiran",
                subtitle = "Rekap status kehadiran kuliah",
                values = listOf(
                    "Hadir" to uiState.attendanceRecords.count { it.status == AttendanceStatus.HADIR },
                    "Izin" to uiState.attendanceRecords.count { it.status == AttendanceStatus.IZIN },
                    "Sakit" to uiState.attendanceRecords.count { it.status == AttendanceStatus.SAKIT },
                    "Alfa" to uiState.attendanceRecords.count { it.status == AttendanceStatus.ALFA }
                ),
                barColor = DarkCardBg
            ),
            ChartSpec(
                title = "Aktivitas Akademik",
                subtitle = "Sebaran prodi, kelas, & jadwal",
                values = listOf(
                    "Prodi" to uiState.studyPrograms.size,
                    "Kelas" to uiState.classes.size,
                    "Jadwal" to uiState.schedules.size,
                    "Absen" to uiState.attendanceSessions.size
                ),
                barColor = LimeDeep
            ),
            ChartSpec(
                title = "Rekap Penilaian & AI",
                subtitle = "Progres evaluasi tugas mahasiswa",
                values = listOf(
                    "Aktif" to uiState.assignments.count { it.isActive },
                    "Cek AI" to uiState.submissions.count { it.aiReview != null },
                    "Dinilai" to uiState.submissions.count { it.finalScore != null },
                    "Revisi" to uiState.submissions.count { it.status == SubmissionStatus.NEEDS_REVISION }
                ),
                barColor = LimePrimary
            )
        )

        val chartCols = if (isExpanded) 2 else 1
        chartPairs.chunked(chartCols).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                pair.forEach { chart ->
                    MiniBarChartCard(chart = chart, modifier = Modifier.weight(1f))
                }
                repeat(chartCols - pair.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Operational Panels: Upcoming Schedules & Latest Submissions / AI Reviews
        val panelCols = if (isExpanded) 2 else 1
        if (panelCols == 2) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                UpcomingSchedulePanel(uiState, onNavigate, Modifier.weight(1f))
                RecentSubmissionsAndAiPanel(uiState, onNavigate, Modifier.weight(1f))
            }
        } else {
            UpcomingSchedulePanel(uiState, onNavigate, Modifier.fillMaxWidth())
            RecentSubmissionsAndAiPanel(uiState, onNavigate, Modifier.fillMaxWidth())
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

private data class StatSpec(
    val label: String,
    val value: String,
    val helper: String,
    val icon: ImageVector,
    val mint: Boolean = false,
    val isWarning: Boolean = false
)

private data class ChartSpec(
    val title: String,
    val subtitle: String,
    val values: List<Pair<String, Int>>,
    val barColor: Color
)

@Composable
private fun MiniBarChartCard(
    chart: ChartSpec,
    modifier: Modifier = Modifier
) {
    val maxVal = (chart.values.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val isEmpty = chart.values.all { it.second == 0 }

    AzrilCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(chart.title, style = MaterialTheme.typography.titleMedium, color = InkText)
                Text(chart.subtitle, style = MaterialTheme.typography.bodySmall, color = InkSubtle)
            }
            if (isEmpty) {
                AzrilStatusBadge("Belum ada data", SoftMintBg, InkMuted)
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        ) {
            val barCount = chart.values.size
            val spacing = 22.dp.toPx()
            val barWidth = ((size.width - spacing * (barCount + 1)) / barCount).coerceAtLeast(16f)
            val baselineY = size.height - 6.dp.toPx()

            drawLine(
                color = BorderMint,
                start = Offset(0f, baselineY),
                end = Offset(size.width, baselineY),
                strokeWidth = 2f
            )

            chart.values.forEachIndexed { idx, (_, value) ->
                val left = spacing + idx * (barWidth + spacing)
                // Soft background pill track
                drawRoundRect(
                    color = SoftMintBg,
                    topLeft = Offset(left, 6.dp.toPx()),
                    size = Size(barWidth, baselineY - 6.dp.toPx()),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                )
                val ratio = if (isEmpty) 0.14f else (value.toFloat() / maxVal.toFloat()).coerceIn(0.12f, 1f)
                val barHeight = (baselineY - 8.dp.toPx()) * ratio
                drawRoundRect(
                    color = if (isEmpty) LightGreenSecondary else chart.barColor,
                    topLeft = Offset(left, baselineY - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            chart.values.forEach { (label, count) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$count", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = InkText)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = InkSubtle)
                }
            }
        }
    }
}

@Composable
private fun UpcomingSchedulePanel(
    uiState: AzrilUiState,
    onNavigate: (AdminDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    AzrilCard(modifier = modifier, contentPadding = PaddingValues(20.dp)) {
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
                Text("Jadwal Terdekat", style = MaterialTheme.typography.titleMedium, color = InkText)
            }
            Text(
                text = "Kelola",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = LimeDeep,
                modifier = Modifier.clickable { onNavigate(AdminDestination.SCHEDULES) }
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        if (uiState.schedules.isEmpty()) {
            AzrilEmptyState(
                title = "Belum Ada Jadwal Perkuliahan",
                description = "Tambahkan jadwal daring atau luring untuk mulai memantau pertemuan mingguan.",
                icon = Icons.Rounded.CalendarMonth,
                showIllustration = true,
                actionLabel = "Buat Jadwal",
                onAction = { onNavigate(AdminDestination.SCHEDULES) }
            )
        } else {
            uiState.schedules.take(4).forEachIndexed { idx, schedule ->
                if (idx > 0) HorizontalDivider(color = BorderMint.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(schedule.courseTitle, style = MaterialTheme.typography.titleSmall, color = InkText)
                        Text(
                            "${schedule.className} • ${schedule.dateLabel} (${schedule.startTime}-${schedule.endTime})",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSubtle
                        )
                    }
                    AzrilStatusBadge(
                        text = schedule.method.label,
                        containerColor = if (schedule.method.name == "DARING") DarkCardBg else LightGreenSecondary,
                        contentColor = if (schedule.method.name == "DARING") LimePrimary else LimeInk
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentSubmissionsAndAiPanel(
    uiState: AzrilUiState,
    onNavigate: (AdminDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    AzrilCard(modifier = modifier, contentPadding = PaddingValues(20.dp)) {
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
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = LimePrimary, modifier = Modifier.size(18.dp))
                }
                Text("Aktivitas & AI Review", style = MaterialTheme.typography.titleMedium, color = InkText)
            }
            Text(
                text = "AI Assistant",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = LimeDeep,
                modifier = Modifier.clickable { onNavigate(AdminDestination.AI_ASSISTANT) }
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        if (uiState.submissions.isEmpty()) {
            AzrilEmptyState(
                title = "Belum Ada Pengumpulan Tugas",
                description = "Pengumpulan tugas mahasiswa dan ringkasan rekomendasi Gemini AI akan tampil di sini.",
                icon = Icons.Rounded.AutoAwesome,
                actionLabel = "Buka Penugasan",
                onAction = { onNavigate(AdminDestination.ASSIGNMENTS) }
            )
        } else {
            uiState.submissions.take(4).forEachIndexed { idx, sub ->
                if (idx > 0) HorizontalDivider(color = BorderMint.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(sub.assignmentTitle, style = MaterialTheme.typography.titleSmall, color = InkText)
                        Text("${sub.studentName} • ${sub.submittedAt}", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    AzrilStatusBadge(sub.status.label, LightGreenSecondary, LimeInk)
                }
            }
        }
    }
}
