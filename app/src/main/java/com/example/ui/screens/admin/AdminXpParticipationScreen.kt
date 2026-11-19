package com.example.ui.screens.admin

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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.gamification.XpAndStreakEngine
import com.example.ui.components.AzrilCard
import com.example.ui.components.AzrilDarkCard
import com.example.ui.components.AzrilEmptyState
import com.example.ui.components.AzrilMintCard
import com.example.ui.components.AzrilPageHeader
import com.example.ui.components.AzrilStatusBadge
import com.example.ui.model.ParticipationXpCategory
import com.example.ui.model.PointSourceType
import com.example.ui.model.StudentRecord
import com.example.ui.screens.student.XpTransactionLedgerCard
import com.example.ui.state.AzrilUiState
import com.example.ui.state.AzrilViewModel
import com.example.ui.theme.Amber50
import com.example.ui.theme.Amber700
import com.example.ui.theme.BorderMint
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.DarkCardElevated
import com.example.ui.theme.Emerald50
import com.example.ui.theme.Emerald700
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSubtle
import com.example.ui.theme.InkText
import com.example.ui.theme.LightGreenSecondary
import com.example.ui.theme.LimeDeep
import com.example.ui.theme.LimeInk
import com.example.ui.theme.LimePrimary
import com.example.ui.theme.OnDarkMuted
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Rose50
import com.example.ui.theme.Rose600
import com.example.ui.theme.Rose700
import com.example.ui.theme.SoftMintBg
import com.example.ui.theme.SurfaceMuted

/**
 * Admin XP Keaktifan Mahasiswa Screen:
 * Exact 8-step flow:
 * 1. Choose Class
 * 2. Choose Student
 * 3. Choose ONLY one of 2 categories (Biasa +10 XP or Kritis +20 XP)
 * 4. System auto-determines XP
 * 5. Optional notes / reason
 * 6. Tap "Tambahkan XP"
 * 7. Confirmation Dialog
 * 8. Confirm -> Student XP updates immediately with Admin ID & timestamp
 *
 * Also displays Individual Semester Izin Quota (X/2) & lets Admin inspect any student's XP history.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminXpParticipationScreen(
    uiState: AzrilUiState,
    viewModel: AzrilViewModel,
    isExpanded: Boolean
) {
    var selectedClassId by rememberSaveable { mutableStateOf<String?>(uiState.classes.firstOrNull()?.id) }
    var selectedStudentId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCategory by rememberSaveable { mutableStateOf(ParticipationXpCategory.REGULAR) }
    var reasonNote by rememberSaveable { mutableStateOf("") }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }
    var showConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var inspectingStudent by remember { mutableStateOf<StudentRecord?>(null) }

    val studentsInClass = remember(uiState.students, selectedClassId) {
        if (selectedClassId == null) uiState.students
        else uiState.students.filter { it.classId == selectedClassId }
    }
    val selectedStudent = remember(studentsInClass, selectedStudentId) {
        studentsInClass.find { it.id == selectedStudentId } ?: studentsInClass.firstOrNull()
    }
    val selectedClassName = remember(uiState.classes, selectedClassId) {
        uiState.classes.find { it.id == selectedClassId }?.name ?: "Semua Kelas"
    }
    val recentParticipationTxs = remember(uiState.rankingPoints) {
        uiState.rankingPoints.filter { it.sourceType == PointSourceType.PARTICIPATION }.take(10)
    }

    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = DarkCardBg,
        selectedLabelColor = LimePrimary,
        containerColor = SoftMintBg,
        labelColor = InkText
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        AzrilPageHeader(
            title = "XP Keaktifan Mahasiswa",
            subtitle = "Berikan XP keaktifan diskusi secara terstruktur (Biasa +10 XP atau Kritis +20 XP) dan pantau kuota izin semester mahasiswa."
        )

        // XP Rules Reference Banner
        AzrilMintCard(contentPadding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = DarkCardBg, modifier = Modifier.size(18.dp))
                Text("Skema XP Azril Clas (Semester ${uiState.settings.activeSemesterKey})", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = InkText)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Absensi: Hadir +20 XP • Izin ke-1 & ke-2 +10 XP • Izin ke-3+ & Alfa 0 XP\n" +
                    "Tugas: Sebelum Deadline +35 XP • Tepat Waktu +30 XP • Terlambat +10 XP\n" +
                    "Keaktifan: Biasa +10 XP • Kritis +20 XP  |  Streak: 3h +10 • 7h +30 • 14h +50 • 30h +100 XP",
                style = MaterialTheme.typography.bodySmall,
                color = InkText
            )
        }

        // Main Awarding Form Card (8-step flow)
        AzrilCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Form Pemberian XP Keaktifan", style = MaterialTheme.typography.titleMedium, color = InkText)
                AzrilStatusBadge(
                    text = "+${selectedCategory.xpAmount} XP Otomatis",
                    containerColor = DarkCardBg,
                    contentColor = LimePrimary
                )
            }
            Spacer(modifier = Modifier.height(14.dp))

            // Step 1: Select Class
            Text("1. Pilih Kelas", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = InkText)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = selectedClassId == null,
                    onClick = {
                        selectedClassId = null
                        selectedStudentId = null
                        formError = null
                    },
                    label = { Text("Semua Kelas (${uiState.students.size})") },
                    shape = RoundedCornerShape(999.dp),
                    colors = chipColors
                )
                uiState.classes.forEach { cls ->
                    FilterChip(
                        selected = selectedClassId == cls.id,
                        onClick = {
                            selectedClassId = cls.id
                            selectedStudentId = null
                            formError = null
                        },
                        label = { Text(cls.name) },
                        shape = RoundedCornerShape(999.dp),
                        colors = chipColors,
                        modifier = Modifier.testTag("xp_class_chip_${cls.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Step 2: Select Student
            Text("2. Pilih Mahasiswa", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = InkText)
            Spacer(modifier = Modifier.height(6.dp))
            if (studentsInClass.isEmpty()) {
                Surface(
                    color = SoftMintBg,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Belum ada mahasiswa terdaftar pada pilihan kelas ini. Tambahkan mahasiswa terlebih dahulu di menu Mahasiswa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    studentsInClass.forEach { stu ->
                        val isSelected = selectedStudent?.id == stu.id
                        val currentXp = XpAndStreakEngine.computeTotalXp(stu.id, uiState.rankingPoints)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedStudentId = stu.id
                                formError = null
                            },
                            label = { Text("${stu.fullName} (${stu.studyProgramName}) • $currentXp XP") },
                            shape = RoundedCornerShape(999.dp),
                            colors = chipColors,
                            modifier = Modifier.testTag("xp_student_chip_${stu.id}")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step 3 & 4: Choose Strictly 1 of 2 Categories (Auto XP)
            Text("3. Pilih Kategori Keaktifan (Jumlah XP Otomatis)", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = InkText)
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ParticipationXpCategory.entries.forEach { cat ->
                    val isSelected = selectedCategory == cat
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                selectedCategory = cat
                                formError = null
                            }
                            .testTag("xp_category_card_${cat.name.lowercase()}"),
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) DarkCardBg else SoftMintBg,
                        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) LimePrimary else BorderMint)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (isSelected) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = LimePrimary, modifier = Modifier.size(18.dp))
                                    }
                                    Text(
                                        text = cat.label,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) PureWhite else InkText
                                    )
                                }
                                Text(
                                    text = cat.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) OnDarkMuted else InkMuted
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            AzrilStatusBadge(
                                text = "+${cat.xpAmount} XP",
                                containerColor = if (isSelected) LimePrimary else LightGreenSecondary,
                                contentColor = DarkCardBg
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Step 5: Optional Reason / Note
            OutlinedTextField(
                value = reasonNote,
                onValueChange = { reasonNote = it; formError = null },
                label = { Text("4. Catatan / Alasan Pemberian XP (Opsional)") },
                placeholder = { Text("Contoh: Mengajukan analisis kritis terkait akad muamalah pada sesi diskusi") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("xp_participation_note_input")
            )

            formError?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(err, style = MaterialTheme.typography.bodySmall, color = Rose600)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step 6: Button "Tambahkan XP" -> opens Confirmation Dialog (Step 7)
            Button(
                onClick = {
                    if (selectedStudent == null) {
                        formError = "Pilih mahasiswa terlebih dahulu sebelum menambahkan XP."
                    } else {
                        showConfirmDialog = true
                    }
                },
                enabled = selectedStudent != null,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkCardBg, contentColor = LimePrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("admin_add_participation_xp_button")
            ) {
                Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tambahkan XP (+${selectedCategory.xpAmount} XP)", fontWeight = FontWeight.Bold)
            }
        }

        // Student Izin Quota & XP History Inspector Card
        AzrilCard {
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
                        Icon(Icons.Rounded.FactCheck, contentDescription = null, tint = DarkCardBg, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text("Kuota Izin & Riwayat XP Mahasiswa", style = MaterialTheme.typography.titleMedium, color = InkText)
                        Text("Semester ${uiState.settings.activeSemesterKey} • Maks. ${uiState.settings.maxPaidIzinPerSemester} izin ber-XP", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (uiState.students.isEmpty()) {
                Text("Belum ada mahasiswa terdaftar.", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
            } else {
                uiState.students.forEachIndexed { idx, stu ->
                    if (idx > 0) HorizontalDivider(color = BorderMint.copy(alpha = 0.65f), modifier = Modifier.padding(vertical = 8.dp))
                    val quota = viewModel.getStudentIzinQuota(stu.id)
                    val totalXp = XpAndStreakEngine.computeTotalXp(stu.id, uiState.rankingPoints)
                    val streakDays = XpAndStreakEngine.effectiveCurrentStreak(uiState.studentStreaks[stu.id])
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${stu.fullName} — ${stu.studyProgramName}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = InkText)
                            Text(
                                "Total: $totalXp XP • Streak: ${streakDays}h • Izin: ${quota.usedIzinCount}x (Sisa kuota XP: ${quota.remainingPaidIzin}/${quota.maxPaidIzin})",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { inspectingStudent = stu },
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Riwayat", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // Streak Reminder Evaluation Panel for Admin
        AzrilDarkCard(contentPadding = PaddingValues(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(LimePrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = DarkCardBg, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text("Pengingat Login Streak (WIB)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                        Text(
                            "Jadwal 08.00, 12.00, 17.00 WIB • ${uiState.streakNotificationLogs.size} pengingat terkirim (anti-duplikasi).",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnDarkMuted
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    onClick = {
                        viewModel.evaluateStreakRemindersForInactiveStudents()
                        viewModel.showToast("Pengecekan pengingat streak WIB selesai diproses tanpa duplikasi.", com.example.ui.model.ToastType.INFO)
                    },
                    color = LimePrimary,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.testTag("admin_check_streak_reminders_button")
                ) {
                    Text(
                        text = "Cek Sekarang",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = DarkCardBg,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Recent Manual Participation XP Awards
        AzrilCard {
            Text("Riwayat Pemberian XP Keaktifan Terbaru", style = MaterialTheme.typography.titleMedium, color = InkText)
            Spacer(modifier = Modifier.height(10.dp))
            if (recentParticipationTxs.isEmpty()) {
                AzrilEmptyState(
                    title = "Belum Ada XP Keaktifan Diberikan",
                    description = "Setiap pemberian XP keaktifan (Biasa +10 XP atau Kritis +20 XP) tercatat beserta identitas Admin dan waktu transaksi.",
                    icon = Icons.Rounded.Bolt
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    recentParticipationTxs.forEach { tx ->
                        val studentName = uiState.students.find { it.id == tx.studentId }?.fullName ?: "Mahasiswa"
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SoftMintBg,
                            border = BorderStroke(1.dp, BorderMint),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(studentName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = InkText)
                                    Text("${tx.activityTitle} • ${tx.description}", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                                    Text("Admin: ${tx.adminName ?: "-"} • ${tx.createdAt}", style = MaterialTheme.typography.labelSmall, color = InkSubtle)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                AzrilStatusBadge("+${tx.points} XP", DarkCardBg, LimePrimary)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    // Step 7: Confirmation Dialog before recording Participation XP
    if (showConfirmDialog && selectedStudent != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            containerColor = PureWhite,
            title = {
                Text("Konfirmasi Pemberian XP Keaktifan", style = MaterialTheme.typography.titleLarge, color = InkText)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pastikan detail pemberian XP keaktifan berikut sudah sesuai:", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                    Surface(color = SoftMintBg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Mahasiswa: ${selectedStudent.fullName} (${selectedStudent.nim})", style = MaterialTheme.typography.titleSmall, color = InkText)
                            Text("Kelas / Prodi: ${selectedStudent.className ?: selectedClassName} • ${selectedStudent.studyProgramName}", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                            Text("Kategori: ${selectedCategory.label}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = InkText)
                            Text("Jumlah XP: +${selectedCategory.xpAmount} XP", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Emerald700)
                            Text("Catatan: ${reasonNote.trim().ifBlank { "(Tanpa catatan tambahan)" }}", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val err = viewModel.awardParticipationXp(
                            studentId = selectedStudent.id,
                            category = selectedCategory,
                            reasonNote = reasonNote
                        )
                        if (err != null) {
                            formError = err
                        } else {
                            reasonNote = ""
                            formError = null
                        }
                        showConfirmDialog = false
                    },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkCardBg, contentColor = LimePrimary),
                    modifier = Modifier.testTag("confirm_award_participation_xp_button")
                ) {
                    Text("Konfirmasi & Tambahkan XP", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Batal") }
            }
        )
    }

    // Admin Inspection Dialog of a specific student's full XP History
    inspectingStudent?.let { stu ->
        val txs = viewModel.getAuthorizedXpHistory(stu.id)
        val totalXp = XpAndStreakEngine.computeTotalXp(stu.id, uiState.rankingPoints)
        val monthlyXp = XpAndStreakEngine.computeMonthlyXp(stu.id, uiState.rankingPoints)
        AlertDialog(
            onDismissRequest = { inspectingStudent = null },
            containerColor = PureWhite,
            title = {
                Column {
                    Text("Riwayat XP: ${stu.fullName}", style = MaterialTheme.typography.titleLarge, color = InkText)
                    Text("NIM ${stu.nim} • ${stu.studyProgramName} • Total $totalXp XP (Bulan ini +$monthlyXp XP)", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (txs.isEmpty()) {
                        Text("Belum ada transaksi XP untuk mahasiswa ini.", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
                    } else {
                        txs.take(25).forEach { tx ->
                            XpTransactionLedgerCard(tx = tx)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { inspectingStudent = null },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkCardBg, contentColor = LimePrimary)
                ) { Text("Tutup") }
            }
        )
    }
}
