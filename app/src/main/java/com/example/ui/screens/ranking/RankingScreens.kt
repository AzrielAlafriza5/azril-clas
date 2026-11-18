package com.example.ui.screens.ranking

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.components.AzrilCard
import com.example.ui.components.AzrilDarkCard
import com.example.ui.components.AzrilEmptyState
import com.example.ui.components.AzrilMintCard
import com.example.ui.components.AzrilPageHeader
import com.example.ui.components.AzrilStatusBadge
import com.example.ui.model.PointSourceType
import com.example.ui.model.RankingPeriodFilter
import com.example.ui.model.RankingPointAuditLog
import com.example.ui.model.RankingPointTransaction
import com.example.ui.model.RankingScope
import com.example.ui.model.StudentRankingEntry
import com.example.ui.state.AzrilUiState
import com.example.ui.state.AzrilViewModel
import com.example.ui.theme.Amber50
import com.example.ui.theme.Amber700
import com.example.ui.theme.BorderMint
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.DarkCardElevated
import com.example.ui.theme.Emerald50
import com.example.ui.theme.Emerald600
import com.example.ui.theme.Emerald700
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSubtle
import com.example.ui.theme.InkText
import com.example.ui.theme.LightGreenSecondary
import com.example.ui.theme.LimeDeep
import com.example.ui.theme.LimeInk
import com.example.ui.theme.LimePrimary
import com.example.ui.theme.MintSurfaceSoft
import com.example.ui.theme.OnDarkMuted
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Rose600
import com.example.ui.theme.SoftMintBg

@Composable
private fun limeFilterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = DarkCardBg,
    selectedLabelColor = LimePrimary,
    containerColor = SoftMintBg,
    labelColor = InkText
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminRankingScreen(
    uiState: AzrilUiState,
    viewModel: AzrilViewModel,
    isExpanded: Boolean
) {
    var selectedPeriod by rememberSaveable { mutableStateOf(RankingPeriodFilter.SEMESTER) }
    var customStartDate by rememberSaveable { mutableStateOf("01 Nov 2026") }
    var customEndDate by rememberSaveable { mutableStateOf("30 Nov 2026") }
    var selectedProdiId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedClassId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSemester by rememberSaveable { mutableStateOf<Int?>(null) }
    var inspectingEntry by remember { mutableStateOf<StudentRankingEntry?>(null) }
    var adjustingEntry by remember { mutableStateOf<StudentRankingEntry?>(null) }

    val rankings = remember(uiState, selectedPeriod, selectedProdiId, selectedClassId, selectedSemester) {
        AzrilViewModel.computeRankings(
            state = uiState,
            periodFilter = selectedPeriod,
            studyProgramId = selectedProdiId,
            classId = selectedClassId,
            semester = selectedSemester
        )
    }

    val availableSemesters = remember(uiState.students) {
        uiState.students.map { it.semester }.distinct().sorted()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AzrilPageHeader(
            title = "Student Ranking & Leaderboard",
            subtitle = "Peringkat otomatis dari kehadiran dan ketepatan pengumpulan tugas dengan tie-breaker & audit log."
        )

        PointRulesReferenceCard()

        // Filter Panel
        AzrilCard {
            Text("Filter Periode & Akademik", style = MaterialTheme.typography.titleSmall, color = InkText)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RankingPeriodFilter.entries.forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period },
                        label = { Text(period.label) },
                        shape = RoundedCornerShape(999.dp),
                        colors = limeFilterChipColors(),
                        modifier = Modifier.testTag("ranking_period_${period.name.lowercase()}")
                    )
                }
            }
            if (selectedPeriod == RankingPeriodFilter.CUSTOM) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customStartDate,
                        onValueChange = { customStartDate = it },
                        label = { Text("Tanggal Mulai") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customEndDate,
                        onValueChange = { customEndDate = it },
                        label = { Text("Tanggal Akhir") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (uiState.studyPrograms.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text("Program Studi", style = MaterialTheme.typography.labelMedium, color = InkMuted)
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedProdiId == null,
                        onClick = { selectedProdiId = null; selectedClassId = null },
                        label = { Text("Semua Prodi") },
                        shape = RoundedCornerShape(999.dp),
                        colors = limeFilterChipColors()
                    )
                    uiState.studyPrograms.forEach { prodi ->
                        FilterChip(
                            selected = selectedProdiId == prodi.id,
                            onClick = { selectedProdiId = prodi.id; selectedClassId = null },
                            label = { Text(prodi.code) },
                            shape = RoundedCornerShape(999.dp),
                            colors = limeFilterChipColors()
                        )
                    }
                }
            }
            val visibleClasses = uiState.classes.filter { selectedProdiId == null || it.studyProgramId == selectedProdiId }
            if (visibleClasses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Kelas", style = MaterialTheme.typography.labelMedium, color = InkMuted)
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedClassId == null,
                        onClick = { selectedClassId = null },
                        label = { Text("Semua Kelas") },
                        shape = RoundedCornerShape(999.dp),
                        colors = limeFilterChipColors()
                    )
                    visibleClasses.forEach { cls ->
                        FilterChip(
                            selected = selectedClassId == cls.id,
                            onClick = { selectedClassId = cls.id },
                            label = { Text(cls.name) },
                            shape = RoundedCornerShape(999.dp),
                            colors = limeFilterChipColors()
                        )
                    }
                }
            }
            if (availableSemesters.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Semester", style = MaterialTheme.typography.labelMedium, color = InkMuted)
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedSemester == null,
                        onClick = { selectedSemester = null },
                        label = { Text("Semua") },
                        shape = RoundedCornerShape(999.dp),
                        colors = limeFilterChipColors()
                    )
                    availableSemesters.forEach { sem ->
                        FilterChip(
                            selected = selectedSemester == sem,
                            onClick = { selectedSemester = sem },
                            label = { Text("Sem $sem") },
                            shape = RoundedCornerShape(999.dp),
                            colors = limeFilterChipColors()
                        )
                    }
                }
            }
        }

        if (rankings.isEmpty()) {
            AzrilEmptyState(
                title = "Belum Ada Data Peringkat Mahasiswa",
                description = "Peringkat dihitung otomatis dari data aktual absensi dan pengumpulan tugas (tanpa data dummy). Daftarkan mahasiswa, buka sesi absensi, atau buat tugas untuk melihat leaderboard aktif.",
                icon = Icons.Rounded.EmojiEvents,
                showIllustration = true
            )
        } else {
            RankingPodiumSection(
                entries = rankings.take(3),
                onSelectEntry = { inspectingEntry = it }
            )

            Text("Peringkat Seluruh Mahasiswa (${rankings.size})", style = MaterialTheme.typography.titleMedium, color = InkText)
            rankings.forEach { entry ->
                AdminRankingStudentCard(
                    entry = entry,
                    onInspect = { inspectingEntry = entry },
                    onAdjust = { adjustingEntry = entry }
                )
            }
        }

        // Global Audit History Card
        AzrilCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(LightGreenSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.History, contentDescription = null, tint = DarkCardBg, modifier = Modifier.size(18.dp))
                }
                Text("Riwayat Audit Perubahan Poin", style = MaterialTheme.typography.titleMedium, color = InkText)
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (uiState.rankingAuditLogs.isEmpty()) {
                Text(
                    "Belum ada perubahan poin manual atau koreksi absensi. Setiap penyesuaian oleh Admin tercatat dengan identitas Admin, alasan, poin sebelum, poin sesudah, dan waktu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle
                )
            } else {
                uiState.rankingAuditLogs.take(8).forEach { log ->
                    HorizontalDivider(color = BorderMint.copy(alpha = 0.7f), modifier = Modifier.padding(vertical = 8.dp))
                    AuditLogRow(log)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    inspectingEntry?.let { entry ->
        val studentTxs = uiState.rankingPoints.filter { it.studentId == entry.studentId }
        val studentLogs = uiState.rankingAuditLogs.filter { it.studentId == entry.studentId }
        RankingDetailDialog(
            entry = entry,
            transactions = studentTxs,
            auditLogs = studentLogs,
            isAdmin = true,
            onRequestAdjust = {
                inspectingEntry = null
                adjustingEntry = entry
            },
            onDismiss = { inspectingEntry = null }
        )
    }

    adjustingEntry?.let { entry ->
        ManualPointAdjustmentDialog(
            entry = entry,
            onDismiss = { adjustingEntry = null },
            onSave = { delta, reason ->
                val err = viewModel.adjustStudentPoints(entry.studentId, entry.studentName, delta, reason)
                if (err == null) adjustingEntry = null
                err
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentRankingScreen(
    uiState: AzrilUiState,
    isExpanded: Boolean
) {
    val user = uiState.currentUser ?: return
    val allowGlobal = uiState.settings.allowGlobalStudentRanking
    var selectedScope by rememberSaveable { mutableStateOf(RankingScope.CLASS) }
    var selectedPeriod by rememberSaveable { mutableStateOf(RankingPeriodFilter.SEMESTER) }

    val effectiveScope = if (!allowGlobal && selectedScope == RankingScope.ALL) RankingScope.STUDY_PROGRAM else selectedScope
    val myStudentRecord = uiState.students.find { it.id == user.id }

    val rankings = remember(uiState, effectiveScope, selectedPeriod, myStudentRecord) {
        when (effectiveScope) {
            RankingScope.CLASS -> AzrilViewModel.computeRankings(
                state = uiState,
                periodFilter = selectedPeriod,
                classId = myStudentRecord?.classId
            )
            RankingScope.STUDY_PROGRAM -> AzrilViewModel.computeRankings(
                state = uiState,
                periodFilter = selectedPeriod,
                studyProgramId = myStudentRecord?.studyProgramId
            )
            RankingScope.ALL -> AzrilViewModel.computeRankings(
                state = uiState,
                periodFilter = selectedPeriod
            )
        }
    }

    val myEntry = rankings.find { it.studentId == user.id }
    val myTransactions = uiState.rankingPoints.filter { it.studentId == user.id }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AzrilPageHeader(
            title = "Ranking & Leaderboard",
            subtitle = "Pantau posisi peringkat Anda dari kehadiran dan ketepatan waktu pengumpulan tugas."
        )

        // Personal Dark Ranking Hero Card (#171A17 + Lime Green #B5D45D)
        AzrilDarkCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AzrilStatusBadge(
                    text = effectiveScope.label,
                    containerColor = LimePrimary,
                    contentColor = DarkCardBg
                )
                AzrilStatusBadge(
                    text = selectedPeriod.label,
                    containerColor = DarkCardElevated,
                    contentColor = PureWhite
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text("Posisi Anda Saat Ini", style = MaterialTheme.typography.labelMedium, color = OnDarkMuted)
                    Text(
                        text = myEntry?.let { "#${it.rank}" } ?: "Belum Ada",
                        style = MaterialTheme.typography.displaySmall,
                        color = PureWhite
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total Poin", style = MaterialTheme.typography.labelMedium, color = OnDarkMuted)
                    Text(
                        text = "${myEntry?.totalPoints ?: 0} pts",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = LimePrimary
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = DarkCardElevated)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hadir: ${myEntry?.hadirCount ?: 0} • Tepat Waktu: ${myEntry?.onTimeAssignmentsCount ?: 0}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkMuted
                )
                Text(
                    text = when {
                        myEntry == null -> "Aktifkan poin via absensi/tugas"
                        myEntry.rank == 1 -> "Posisi Puncak!"
                        myEntry.pointGapToAbove != null -> "Selisih ${myEntry.pointGapToAbove} pts ke #${myEntry.rankAbove}"
                        else -> "-"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = LimePrimary
                )
            }
        }

        // Scope & Period Controls
        AzrilCard {
            Text("Cakupan & Periode", style = MaterialTheme.typography.titleSmall, color = InkText)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = effectiveScope == RankingScope.CLASS, onClick = { selectedScope = RankingScope.CLASS }, label = { Text("Di Kelas") }, shape = RoundedCornerShape(999.dp), colors = limeFilterChipColors())
                FilterChip(selected = effectiveScope == RankingScope.STUDY_PROGRAM, onClick = { selectedScope = RankingScope.STUDY_PROGRAM }, label = { Text("Di Prodi") }, shape = RoundedCornerShape(999.dp), colors = limeFilterChipColors())
                if (allowGlobal) {
                    FilterChip(selected = effectiveScope == RankingScope.ALL, onClick = { selectedScope = RankingScope.ALL }, label = { Text("Seluruh Mahasiswa") }, shape = RoundedCornerShape(999.dp), colors = limeFilterChipColors())
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(RankingPeriodFilter.WEEKLY, RankingPeriodFilter.MONTHLY, RankingPeriodFilter.SEMESTER).forEach { p ->
                    FilterChip(selected = selectedPeriod == p, onClick = { selectedPeriod = p }, label = { Text(p.label) }, shape = RoundedCornerShape(999.dp), colors = limeFilterChipColors())
                }
            }
        }

        if (rankings.isEmpty()) {
            AzrilEmptyState(
                title = "Leaderboard Masih Kosong",
                description = "Catat kehadiran (+10 poin) atau kumpulkan tugas tepat waktu (+10 poin) agar peringkat Anda muncul secara otomatis.",
                icon = Icons.Rounded.EmojiEvents,
                showIllustration = true
            )
        } else {
            RankingPodiumSection(entries = rankings.take(3), onSelectEntry = {})

            Text("Tabel Peringkat (${effectiveScope.label})", style = MaterialTheme.typography.titleMedium, color = InkText)
            rankings.forEach { entry ->
                val isMe = entry.studentId == user.id
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = if (isMe) LightGreenSecondary else PureWhite,
                    border = BorderStroke(if (isMe) 1.5.dp else 1.dp, if (isMe) DarkCardBg else BorderMint.copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            RankBadgeCircle(entry.rank)
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(entry.studentName, style = MaterialTheme.typography.titleSmall, color = InkText)
                                    if (isMe) {
                                        AzrilStatusBadge("Anda", DarkCardBg, LimePrimary)
                                    }
                                }
                                Text(
                                    "${entry.className} • Hadir ${entry.hadirCount}x • Tepat Waktu ${entry.onTimeAssignmentsCount}x",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkMuted
                                )
                            }
                        }
                        AzrilStatusBadge(
                            text = "${entry.totalPoints} pts",
                            containerColor = if (isMe) DarkCardBg else SoftMintBg,
                            contentColor = if (isMe) LimePrimary else LimeInk
                        )
                    }
                }
            }
        }

        // Student Read-Only Point History
        AzrilCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Riwayat Perolehan Poin Saya", style = MaterialTheme.typography.titleMedium, color = InkText)
                AzrilStatusBadge("Hanya Baca", SoftMintBg, InkMuted)
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (myTransactions.isEmpty()) {
                Text(
                    "Belum ada transaksi poin. Kehadiran dan pengumpulan tugas akan otomatis menambahkan poin di sini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle
                )
            } else {
                myTransactions.forEach { tx ->
                    HorizontalDivider(color = BorderMint.copy(alpha = 0.65f), modifier = Modifier.padding(vertical = 8.dp))
                    PointTransactionRow(tx)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun AdminRankingDashboardWidget(
    uiState: AzrilUiState,
    onOpenRanking: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rankings = remember(uiState) { AzrilViewModel.computeRankings(uiState) }
    val totalPointsRecorded = uiState.rankingPoints.sumOf { it.points }

    AzrilDarkCard(modifier = modifier, contentPadding = PaddingValues(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(LimePrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = DarkCardBg, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text("Student Ranking", style = MaterialTheme.typography.titleMedium, color = PureWhite)
                    Text("${uiState.rankingPoints.size} aktivitas poin • Total $totalPointsRecorded pts", style = MaterialTheme.typography.bodySmall, color = OnDarkMuted)
                }
            }
            Surface(
                onClick = onOpenRanking,
                color = LimePrimary,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.testTag("admin_dashboard_open_ranking")
            ) {
                Text(
                    text = "Leaderboard",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = DarkCardBg,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        if (rankings.isEmpty()) {
            Surface(
                color = DarkCardElevated,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Belum ada mahasiswa pada papan peringkat. Poin dihitung otomatis dari absensi (+10 Hadir, +5 Izin/Sakit) & tugas (+10 Tepat Waktu, +5 Terlambat).",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkMuted,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            rankings.take(3).forEachIndexed { idx, entry ->
                if (idx > 0) HorizontalDivider(color = DarkCardElevated, modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RankBadgeCircle(entry.rank)
                        Column {
                            Text(entry.studentName, style = MaterialTheme.typography.titleSmall, color = PureWhite)
                            Text("${entry.className} • Hadir ${entry.hadirCount}x • Tepat ${entry.onTimeAssignmentsCount}x", style = MaterialTheme.typography.bodySmall, color = OnDarkMuted)
                        }
                    }
                    AzrilStatusBadge("${entry.totalPoints} pts", LimePrimary, DarkCardBg)
                }
            }
        }
    }
}

@Composable
fun StudentRankingDashboardWidget(
    uiState: AzrilUiState,
    onOpenRanking: () -> Unit,
    modifier: Modifier = Modifier
) {
    val user = uiState.currentUser ?: return
    val rankings = remember(uiState) { AzrilViewModel.computeRankings(uiState) }
    val myEntry = rankings.find { it.studentId == user.id }
    val activityCount = uiState.rankingPoints.count { it.studentId == user.id && it.points > 0 }

    // Signature Black Card (#171A17) with Lime Green (#B5D45D) Accent
    AzrilDarkCard(modifier = modifier, contentPadding = PaddingValues(22.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(LimePrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = DarkCardBg, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text("Peringkat Saya", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                    Text("Skor Kedisiplinan Akademik", style = MaterialTheme.typography.labelSmall, color = OnDarkMuted)
                }
            }
            Surface(
                onClick = onOpenRanking,
                color = LimePrimary,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.testTag("student_dashboard_open_ranking")
            ) {
                Text(
                    text = "Detail",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = DarkCardBg,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DarkRankingMetricBox(
                label = "Posisi",
                value = myEntry?.let { "#${it.rank}" } ?: "-",
                highlightValue = true,
                modifier = Modifier.weight(1f)
            )
            DarkRankingMetricBox(
                label = "Total Poin",
                value = "${myEntry?.totalPoints ?: 0} pts",
                highlightValue = true,
                modifier = Modifier.weight(1.2f)
            )
            DarkRankingMetricBox(
                label = "Aktivitas",
                value = "$activityCount",
                highlightValue = false,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Surface(
            color = DarkCardElevated,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.ArrowUpward, contentDescription = null, tint = LimePrimary, modifier = Modifier.size(16.dp))
                Text(
                    text = when {
                        myEntry == null -> "Hadir kuliah (+10) & kumpulkan tugas tepat waktu (+10) untuk mulai naik peringkat."
                        myEntry.rank == 1 -> "Anda berada di peringkat #1 saat ini! Pertahankan konsistensi Anda."
                        myEntry.pointGapToAbove != null -> "Selisih ${myEntry.pointGapToAbove} poin dengan peringkat #${myEntry.rankAbove} di atas Anda."
                        else -> "Terus tingkatkan kehadiran dan ketepatan waktu tugas Anda."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkMuted
                )
            }
        }
    }
}

@Composable
private fun DarkRankingMetricBox(
    label: String,
    value: String,
    highlightValue: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = DarkCardElevated,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (highlightValue) LimePrimary.copy(alpha = 0.25f) else Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = OnDarkMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = if (highlightValue) LimePrimary else PureWhite,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PointRulesReferenceCard() {
    AzrilMintCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Security, contentDescription = null, tint = DarkCardBg, modifier = Modifier.size(18.dp))
            Text("Aturan Poin Otomatis & Anti-Duplikasi", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = InkText)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Absensi: Hadir +10 • Izin +5 • Sakit +5 • Alfa 0  |  Tugas: Tepat Waktu +10 • Terlambat +5 • Tidak Mengumpulkan 0",
            style = MaterialTheme.typography.bodySmall,
            color = InkText
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            "Tie-breaker jika poin sama: (1) Tugas tepat waktu terbanyak, (2) Hadir terbanyak, (3) Alfa paling sedikit.",
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted
        )
    }
}

@Composable
private fun RankingPodiumSection(
    entries: List<StudentRankingEntry>,
    onSelectEntry: (StudentRankingEntry) -> Unit
) {
    val first = entries.getOrNull(0)
    val second = entries.getOrNull(1)
    val third = entries.getOrNull(2)

    AzrilCard(contentPadding = PaddingValues(20.dp)) {
        Text("Podium Top 3 Mahasiswa", style = MaterialTheme.typography.titleMedium, color = InkText)
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            PodiumColumn(
                placeLabel = "Juara 2",
                entry = second,
                isFirst = false,
                pedestalBg = LightGreenSecondary,
                pedestalHeight = 76.dp,
                onClick = { second?.let(onSelectEntry) },
                modifier = Modifier.weight(1f)
            )
            PodiumColumn(
                placeLabel = "Juara 1",
                entry = first,
                isFirst = true,
                pedestalBg = DarkCardBg,
                pedestalHeight = 104.dp,
                onClick = { first?.let(onSelectEntry) },
                modifier = Modifier.weight(1.15f)
            )
            PodiumColumn(
                placeLabel = "Juara 3",
                entry = third,
                isFirst = false,
                pedestalBg = SoftMintBg,
                pedestalHeight = 62.dp,
                onClick = { third?.let(onSelectEntry) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PodiumColumn(
    placeLabel: String,
    entry: StudentRankingEntry?,
    isFirst: Boolean,
    pedestalBg: Color,
    pedestalHeight: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = entry != null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isFirst) LimePrimary else LightGreenSecondary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isFirst) Icons.Rounded.EmojiEvents else Icons.Rounded.MilitaryTech,
                contentDescription = placeLabel,
                tint = DarkCardBg,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = entry?.studentName ?: "Belum terisi",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = if (entry != null) InkText else InkSubtle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = entry?.className ?: "-",
            style = MaterialTheme.typography.labelSmall,
            color = InkSubtle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().height(pedestalHeight),
            color = pedestalBg,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
            border = if (!isFirst) BorderStroke(1.dp, BorderMint) else null
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = placeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFirst) OnDarkMuted else InkMuted
                )
                Text(
                    text = entry?.let { "${it.totalPoints} pts" } ?: "0 pts",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isFirst) LimePrimary else InkText
                )
            }
        }
    }
}

@Composable
private fun AdminRankingStudentCard(
    entry: StudentRankingEntry,
    onInspect: () -> Unit,
    onAdjust: () -> Unit
) {
    AzrilCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                RankBadgeCircle(entry.rank)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(entry.studentName, style = MaterialTheme.typography.titleMedium, color = InkText)
                    Text("NIM ${entry.nim} • ${entry.studyProgramName} • ${entry.className}", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
                }
            }
            AzrilStatusBadge("${entry.totalPoints} pts", DarkCardBg, LimePrimary)
        }
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = BorderMint.copy(alpha = 0.65f))
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Absensi — Hadir: ${entry.hadirCount} • Izin: ${entry.izinCount} • Sakit: ${entry.sakitCount} • Alfa: ${entry.alfaCount}",
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Tugas — Tepat Waktu: ${entry.onTimeAssignmentsCount} • Terlambat: ${entry.lateAssignmentsCount} • Belum Kumpul: ${entry.missingAssignmentsCount}",
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onInspect,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, BorderMint)
            ) {
                Text("Detail", color = InkText)
            }
            Button(
                onClick = onAdjust,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LimePrimary, contentColor = InkText)
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sesuaikan Poin")
            }
        }
    }
}

@Composable
private fun RankBadgeCircle(rank: Int) {
    val (bg, fg) = when (rank) {
        1 -> LimePrimary to DarkCardBg
        2 -> DarkCardBg to LimePrimary
        3 -> LightGreenSecondary to LimeInk
        else -> SoftMintBg to InkText
    }
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "#$rank",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = fg
        )
    }
}

@Composable
private fun PointTransactionRow(tx: RankingPointTransaction) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            AzrilStatusBadge(
                text = tx.sourceType.label,
                containerColor = when (tx.sourceType) {
                    PointSourceType.ATTENDANCE -> MintSurfaceSoft
                    PointSourceType.ASSIGNMENT -> Emerald50
                    PointSourceType.MANUAL_ADJUSTMENT -> Amber50
                },
                contentColor = when (tx.sourceType) {
                    PointSourceType.ATTENDANCE -> LimeInk
                    PointSourceType.ASSIGNMENT -> Emerald700
                    PointSourceType.MANUAL_ADJUSTMENT -> Amber700
                }
            )
            Text(tx.description, style = MaterialTheme.typography.bodySmall, color = InkText)
            Text("Diperbarui: ${tx.updatedAt}", style = MaterialTheme.typography.labelSmall, color = InkSubtle)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (tx.points >= 0) "+${tx.points} pts" else "${tx.points} pts",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = if (tx.points > 0) Emerald600 else if (tx.points < 0) Rose600 else InkSubtle
        )
    }
}

@Composable
private fun AuditLogRow(log: RankingPointAuditLog) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(log.studentName, style = MaterialTheme.typography.titleSmall, color = InkText)
            AzrilStatusBadge("${log.pointsBefore} -> ${log.pointsAfter} pts", Amber50, Amber700)
        }
        Text("Admin: ${log.adminName} • ${log.changedAt}", style = MaterialTheme.typography.labelSmall, color = InkSubtle)
        Text("Alasan: ${log.reason}", style = MaterialTheme.typography.bodySmall, color = InkMuted)
    }
}

@Composable
private fun RankingDetailDialog(
    entry: StudentRankingEntry,
    transactions: List<RankingPointTransaction>,
    auditLogs: List<RankingPointAuditLog>,
    isAdmin: Boolean,
    onRequestAdjust: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        containerColor = PureWhite,
        title = { Text("Detail Peringkat #${entry.rank}", style = MaterialTheme.typography.titleLarge, color = InkText) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("${entry.studentName} (NIM ${entry.nim})", style = MaterialTheme.typography.titleSmall, color = InkText)
                Text("${entry.studyProgramName} • ${entry.className} • Total ${entry.totalPoints} poin", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                HorizontalDivider(color = BorderMint)
                Text("Rincian Transaksi Poin (${transactions.size})", style = MaterialTheme.typography.labelLarge, color = InkText)
                if (transactions.isEmpty()) {
                    Text("Belum ada transaksi poin tercatat.", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
                } else {
                    transactions.forEach { tx ->
                        PointTransactionRow(tx)
                        HorizontalDivider(color = BorderMint.copy(alpha = 0.6f))
                    }
                }
                if (auditLogs.isNotEmpty()) {
                    Text("Riwayat Perubahan Poin (${auditLogs.size})", style = MaterialTheme.typography.labelLarge, color = InkText)
                    auditLogs.forEach { log ->
                        AuditLogRow(log)
                        HorizontalDivider(color = BorderMint.copy(alpha = 0.6f))
                    }
                }
            }
        },
        confirmButton = {
            if (isAdmin) {
                Button(
                    onClick = onRequestAdjust,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LimePrimary, contentColor = InkText)
                ) { Text("Sesuaikan Poin") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup", color = InkMuted) } }
    )
}

@Composable
private fun ManualPointAdjustmentDialog(
    entry: StudentRankingEntry,
    onDismiss: () -> Unit,
    onSave: (Int, String) -> String?
) {
    var deltaText by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        containerColor = PureWhite,
        title = { Text("Penyesuaian Poin (Audit)", style = MaterialTheme.typography.titleLarge, color = InkText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Mahasiswa: ${entry.studentName} (Saat ini: ${entry.totalPoints} pts)", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                OutlinedTextField(
                    value = deltaText,
                    onValueChange = { deltaText = it; error = null },
                    label = { Text("Perubahan Poin (contoh: +5 atau -5)") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it; error = null },
                    label = { Text("Alasan Wajib (Audit Log)") },
                    minLines = 2,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Rose600) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val delta = deltaText.trim().replace("+", "").toIntOrNull()
                    error = if (delta == null || delta == 0) {
                        "Masukkan angka perubahan poin yang valid (bukan 0)."
                    } else {
                        onSave(delta, reason)
                    }
                },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LimePrimary, contentColor = InkText)
            ) { Text("Simpan & Catat Audit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal", color = InkMuted) } }
    )
}
