package com.example.ui.screens.admin

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.components.AzrilCard
import com.example.ui.components.AzrilDarkCard
import com.example.ui.components.AzrilEmptyState
import com.example.ui.components.AzrilStatusBadge
import com.example.ui.model.Assignment
import com.example.ui.model.AssignmentChecklistRecord
import com.example.ui.model.StudentRecord
import com.example.ui.model.SubmissionRecord
import com.example.ui.model.TaskCompletionStatus
import com.example.ui.model.ToastType
import com.example.ui.model.resolveTaskCompletionStatus
import com.example.ui.state.AzrilUiState
import com.example.ui.state.AzrilViewModel
import com.example.ui.theme.Amber50
import com.example.ui.theme.Amber700
import com.example.ui.theme.BorderLight
import com.example.ui.theme.BorderMint
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.DarkCardElevated
import com.example.ui.theme.Emerald50
import com.example.ui.theme.Emerald700
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkText
import com.example.ui.theme.LightGreenSecondary
import com.example.ui.theme.LimeInk
import com.example.ui.theme.LimePrimary
import com.example.ui.theme.Navy900
import com.example.ui.theme.OnDarkMuted
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Rose50
import com.example.ui.theme.Rose600
import com.example.ui.theme.Rose700
import com.example.ui.theme.RoyalBlue50
import com.example.ui.theme.RoyalBlue700
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.SoftMintBg
import com.example.ui.theme.SurfaceMuted

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminAssignmentDetailScreen(
    assignment: Assignment,
    uiState: AzrilUiState,
    viewModel: AzrilViewModel,
    isExpanded: Boolean,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var searchQuery by rememberSaveable(assignment.id) { mutableStateOf("") }
    var statusFilter by rememberSaveable(assignment.id) { mutableStateOf("ALL") }
    var showGuideExpanded by rememberSaveable(assignment.id) { mutableStateOf(false) }
    var inspectingStudent by remember { mutableStateOf<StudentRecord?>(null) }

    val targetStudents = remember(assignment, uiState.students, uiState.classes) {
        AzrilViewModel.getTargetStudentsForAssignment(assignment, uiState.students, uiState.classes)
    }
    val submissionsByStudent = remember(assignment.id, uiState.submissions) {
        uiState.submissions
            .filter { it.assignmentId == assignment.id }
            .associateBy { it.studentId }
    }
    val checklistsByStudent = remember(assignment.id, uiState.assignmentChecklists) {
        uiState.assignmentChecklists
            .filter { it.assignmentId == assignment.id }
            .associateBy { it.studentId }
    }

    val totalTargets = targetStudents.size
    val completedCount = targetStudents.count { stu ->
        resolveTaskCompletionStatus(submissionsByStudent[stu.id], checklistsByStudent[stu.id]) == TaskCompletionStatus.COMPLETED
    }
    val awaitingCount = targetStudents.count { stu ->
        resolveTaskCompletionStatus(submissionsByStudent[stu.id], checklistsByStudent[stu.id]) == TaskCompletionStatus.AWAITING_VERIFICATION
    }
    val notSubmittedCount = targetStudents.count { stu ->
        resolveTaskCompletionStatus(submissionsByStudent[stu.id], checklistsByStudent[stu.id]) == TaskCompletionStatus.NOT_SUBMITTED
    }
    val submittedCount = targetStudents.count { stu -> submissionsByStudent.containsKey(stu.id) }
    val incompleteCount = (totalTargets - completedCount).coerceAtLeast(0)
    val completionRatio = if (totalTargets == 0) 0f else completedCount.toFloat() / totalTargets.toFloat()
    val completionPercent = (completionRatio * 100f).toInt()
    val isDeadlinePassed = assignment.isDeadlinePassed(viewModel.currentEffectiveNowMillis())

    val filteredStudents = remember(targetStudents, searchQuery, statusFilter, submissionsByStudent, checklistsByStudent) {
        val q = searchQuery.trim().lowercase()
        targetStudents.filter { stu ->
            val matchesQuery = q.isEmpty() ||
                stu.fullName.lowercase().contains(q) ||
                stu.nim.lowercase().contains(q) ||
                stu.studyProgramName.lowercase().contains(q) ||
                (stu.className?.lowercase()?.contains(q) == true)
            val status = resolveTaskCompletionStatus(submissionsByStudent[stu.id], checklistsByStudent[stu.id])
            val matchesStatus = statusFilter == "ALL" || status.name == statusFilter
            matchesQuery && matchesStatus
        }
    }

    fun openExternalResource(label: String, rawUrl: String?) {
        val cleanUrl = rawUrl?.trim().orEmpty()
        if (cleanUrl.startsWith("https://") || cleanUrl.startsWith("http://")) {
            runCatching { uriHandler.openUri(cleanUrl) }
                .onFailure { viewModel.showToast("Tidak dapat membuka tautan $label.", ToastType.WARNING) }
        } else if (cleanUrl.startsWith("content://")) {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(cleanUrl)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            }.onFailure { viewModel.showToast("Berkas '$label' tersimpan di lampiran.", ToastType.INFO) }
        } else {
            viewModel.showToast("Lampiran '$label' tidak memiliki tautan URL aktif.", ToastType.INFO)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Navigation Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, BorderMint),
                modifier = Modifier.testTag("admin_assignment_detail_back")
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Daftar Tugas", fontWeight = FontWeight.SemiBold, color = InkText)
            }
            OutlinedButton(
                onClick = { viewModel.syncAssignmentsAndChecklists(showToastOnComplete = true) },
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, BorderMint),
                modifier = Modifier.testTag("admin_assignment_detail_sync")
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Sinkronisasi", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sinkronkan", color = InkText)
            }
        }

        // Dark Hero Summary Card with Progress Bar
        AzrilDarkCard {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AzrilStatusBadge(
                    text = if (assignment.isActive) "Tugas Aktif" else "Nonaktif",
                    containerColor = if (assignment.isActive) LimePrimary else DarkCardElevated,
                    contentColor = if (assignment.isActive) DarkCardBg else OnDarkMuted
                )
                AzrilStatusBadge(
                    text = assignment.targetSummaryLabel(),
                    containerColor = DarkCardElevated,
                    contentColor = LimePrimary
                )
                AzrilStatusBadge(
                    text = if (isDeadlinePassed) "Deadline Berakhir" else "Masih Dibuka",
                    containerColor = if (isDeadlinePassed) Rose600 else Emerald700,
                    contentColor = PureWhite
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = assignment.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = PureWhite
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = LimePrimary, modifier = Modifier.size(16.dp))
                Text(
                    text = "Deadline: ${assignment.deadline}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkMuted
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Progres Verifikasi Penyelesaian",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnDarkMuted
                )
                Text(
                    text = "$completedCount / $totalTargets Selesai ($completionPercent%)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = LimePrimary,
                    modifier = Modifier.testTag("admin_assignment_progress_text")
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { completionRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .testTag("admin_assignment_progress_bar"),
                color = LimePrimary,
                trackColor = DarkCardElevated,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AzrilStatusBadge("Selesai: $completedCount", Emerald50, Emerald700)
                AzrilStatusBadge("Belum Selesai: $incompleteCount", DarkCardElevated, PureWhite)
                AzrilStatusBadge("Menunggu Verifikasi: $awaitingCount", Amber50, Amber700)
                AzrilStatusBadge("Mengumpulkan: $submittedCount/$totalTargets", LightGreenSecondary, LimeInk)
            }
        }

        // Collapsible Guide & 2 File Links Card
        val hasFiles = !assignment.file1Name.isNullOrBlank() || !assignment.file1Url.isNullOrBlank() ||
            !assignment.file2Name.isNullOrBlank() || !assignment.file2Url.isNullOrBlank()
        AzrilCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showGuideExpanded = !showGuideExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Panduan & Lampiran Tugas", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = InkText)
                    Text(
                        text = if (showGuideExpanded) "Ketuk untuk menyembunyikan detail panduan" else "Ketuk untuk membaca panduan lengkap & 2 tautan file",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted
                    )
                }
                Text(
                    text = if (showGuideExpanded) "Tutup" else "Lihat",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = LimeInk
                )
            }

            if (showGuideExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = BorderLight)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = assignment.description.ifBlank { assignment.instructions.ifBlank { "Tidak ada deskripsi tambahan." } },
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted
                )
                if (hasFiles) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!assignment.file1Name.isNullOrBlank() || !assignment.file1Url.isNullOrBlank()) {
                            val lbl1 = assignment.file1Name ?: "File Tugas 1"
                            OutlinedButton(
                                onClick = { openExternalResource(lbl1, assignment.file1Url) },
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("File 1: $lbl1", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (!assignment.file2Name.isNullOrBlank() || !assignment.file2Url.isNullOrBlank()) {
                            val lbl2 = assignment.file2Name ?: "File Tugas 2"
                            OutlinedButton(
                                onClick = { openExternalResource(lbl2, assignment.file2Url) },
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("File 2: $lbl2", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        // Search & Filter Card
        AzrilCard {
            Text(
                text = "Daftar Mahasiswa Target ($totalTargets Mahasiswa)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = InkText
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Centang checkbox di samping nama mahasiswa untuk menandai tugas Selesai. Perubahan langsung disimpan per individu tanpa memengaruhi mahasiswa lain.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Cari nama atau NIM mahasiswa...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Hapus pencarian")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_assignment_student_search")
            )
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = statusFilter == "ALL",
                    onClick = { statusFilter = "ALL" },
                    label = { Text("Semua ($totalTargets)") },
                    shape = RoundedCornerShape(999.dp)
                )
                FilterChip(
                    selected = statusFilter == TaskCompletionStatus.COMPLETED.name,
                    onClick = { statusFilter = TaskCompletionStatus.COMPLETED.name },
                    label = { Text("Selesai ($completedCount)") },
                    shape = RoundedCornerShape(999.dp)
                )
                FilterChip(
                    selected = statusFilter == TaskCompletionStatus.AWAITING_VERIFICATION.name,
                    onClick = { statusFilter = TaskCompletionStatus.AWAITING_VERIFICATION.name },
                    label = { Text("Menunggu Verifikasi ($awaitingCount)") },
                    shape = RoundedCornerShape(999.dp)
                )
                FilterChip(
                    selected = statusFilter == TaskCompletionStatus.NOT_SUBMITTED.name,
                    onClick = { statusFilter = TaskCompletionStatus.NOT_SUBMITTED.name },
                    label = { Text("Belum Mengumpulkan ($notSubmittedCount)") },
                    shape = RoundedCornerShape(999.dp)
                )
            }
        }

        // Student Target Roster Rows
        if (targetStudents.isEmpty()) {
            AzrilEmptyState(
                title = "Belum Ada Mahasiswa pada Target Tugas Ini",
                description = "Tambahkan atau impor mahasiswa ke ${assignment.targetSummaryLabel()} agar daftar monitoring dan checklist tampil di sini.",
                icon = Icons.Rounded.Groups
            )
        } else if (filteredStudents.isEmpty()) {
            AzrilEmptyState(
                title = "Mahasiswa Tidak Ditemukan",
                description = "Tidak ada mahasiswa yang cocok dengan pencarian '$searchQuery' atau filter status yang dipilih.",
                icon = Icons.Rounded.Search
            )
        } else {
            filteredStudents.forEach { student ->
                val submission = submissionsByStudent[student.id]
                val checklist = checklistsByStudent[student.id]
                val completionStatus = resolveTaskCompletionStatus(submission, checklist)
                val isChecked = checklist?.isCompleted == true

                AzrilCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Admin Verification Checkbox (48dp touch target)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    viewModel.toggleAdminAssignmentChecklist(
                                        assignmentId = assignment.id,
                                        studentId = student.id,
                                        isCompleted = checked
                                    )
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = DarkCardBg,
                                    checkmarkColor = LimePrimary,
                                    uncheckedColor = Slate500
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("admin_checklist_${assignment.id}_${student.id}")
                            )
                            Text(
                                text = if (isChecked) "Selesai" else "Belum",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isChecked) Emerald700 else Slate500,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Student Info & Status
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = student.fullName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = InkText
                            )
                            Text(
                                text = buildString {
                                    append("NIM ${student.nim} • ${student.studyProgramName}")
                                    if (!student.className.isNullOrBlank()) {
                                        append(" • ${student.className}")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate600
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // 1. Submission status badge
                                AzrilStatusBadge(
                                    text = if (submission != null) "Sudah Mengumpulkan" else "Belum Mengumpulkan",
                                    containerColor = if (submission != null) RoyalBlue50 else SurfaceMuted,
                                    contentColor = if (submission != null) RoyalBlue700 else Slate600
                                )
                                // 2. Verification / completion status badge
                                val (verBg, verFg) = when (completionStatus) {
                                    TaskCompletionStatus.COMPLETED -> Emerald50 to Emerald700
                                    TaskCompletionStatus.AWAITING_VERIFICATION -> Amber50 to Amber700
                                    TaskCompletionStatus.NOT_SUBMITTED -> Rose50 to Rose700
                                }
                                AzrilStatusBadge(
                                    text = "Status: ${completionStatus.label}",
                                    containerColor = verBg,
                                    contentColor = verFg
                                )
                            }

                            if (submission != null) {
                                Text(
                                    text = "Dikumpulkan: ${submission.firstSubmittedAt}" +
                                        if (submission.revisionCount > 0) " • Diperbarui: ${submission.lastUpdatedAt} (${submission.revisionCount}x edit)" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkMuted
                                )
                            }
                            if (isChecked && !checklist?.verifiedAt.isNullOrBlank()) {
                                Text(
                                    text = "Diverifikasi Admin (${checklist?.verifiedByName ?: "Admin"}) • ${checklist?.verifiedAt}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Emerald700
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { inspectingStudent = student },
                                    shape = RoundedCornerShape(999.dp),
                                    border = BorderStroke(1.dp, BorderMint),
                                    modifier = Modifier.testTag("admin_inspect_submission_${student.id}")
                                ) {
                                    Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (submission != null) "Periksa Jawaban" else "Lihat Detail",
                                        color = InkText
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    inspectingStudent?.let { stu ->
        val sub = submissionsByStudent[stu.id]
        val chk = checklistsByStudent[stu.id]
        AdminSubmissionReviewDialog(
            assignment = assignment,
            student = stu,
            submission = sub,
            checklist = chk,
            onOpenLink = { label, url -> openExternalResource(label, url) },
            onToggleChecklist = { completed ->
                viewModel.toggleAdminAssignmentChecklist(
                    assignmentId = assignment.id,
                    studentId = stu.id,
                    isCompleted = completed
                )
            },
            onDismiss = { inspectingStudent = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminSubmissionReviewDialog(
    assignment: Assignment,
    student: StudentRecord,
    submission: SubmissionRecord?,
    checklist: AssignmentChecklistRecord?,
    onOpenLink: (String, String?) -> Unit,
    onToggleChecklist: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val isCompleted = checklist?.isCompleted == true
    val status = resolveTaskCompletionStatus(submission, checklist)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Pemeriksaan Jawaban Mahasiswa", style = MaterialTheme.typography.titleLarge, color = Navy900)
                Text(assignment.title, style = MaterialTheme.typography.labelMedium, color = InkMuted)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Student Identity Box
                Surface(
                    color = SoftMintBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BorderMint),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(student.fullName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = InkText)
                        Text(
                            text = "NIM: ${student.nim} • Prodi: ${student.studyProgramName}" +
                                (student.className?.let { " • Kelas: $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate600
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AzrilStatusBadge(
                                text = if (submission != null) "Sudah Mengumpulkan" else "Belum Mengumpulkan",
                                containerColor = if (submission != null) RoyalBlue50 else SurfaceMuted,
                                contentColor = if (submission != null) RoyalBlue700 else Slate600
                            )
                            AzrilStatusBadge(
                                text = "Verifikasi: ${status.label}",
                                containerColor = when (status) {
                                    TaskCompletionStatus.COMPLETED -> Emerald50
                                    TaskCompletionStatus.AWAITING_VERIFICATION -> Amber50
                                    TaskCompletionStatus.NOT_SUBMITTED -> Rose50
                                },
                                contentColor = when (status) {
                                    TaskCompletionStatus.COMPLETED -> Emerald700
                                    TaskCompletionStatus.AWAITING_VERIFICATION -> Amber700
                                    TaskCompletionStatus.NOT_SUBMITTED -> Rose700
                                }
                            )
                        }
                    }
                }

                if (submission == null) {
                    Surface(
                        color = SurfaceMuted,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.HourglassTop, contentDescription = null, tint = Slate500, modifier = Modifier.size(28.dp))
                            Text("Mahasiswa belum mengumpulkan jawaban.", style = MaterialTheme.typography.titleSmall, color = InkText)
                            Text(
                                "Anda tetap dapat menandai checklist selesai secara manual apabila mahasiswa mengumpulkan secara luring.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted
                            )
                        }
                    }
                } else {
                    // Timestamps Box
                    Surface(
                        color = PureWhite,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Riwayat Waktu Pengumpulan (WIB)", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = InkText)
                            Text("Pengumpulan pertama: ${submission.firstSubmittedAt}", style = MaterialTheme.typography.bodySmall, color = Slate600)
                            Text("Pembaruan terakhir: ${submission.lastUpdatedAt}", style = MaterialTheme.typography.bodySmall, color = Slate600)
                            Text("Jumlah pembaruan: ${submission.revisionCount}x edit", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                        }
                    }

                    // Written Answer Box
                    if (!submission.answerText.isNullOrBlank()) {
                        Surface(
                            color = SoftMintBg,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, BorderMint),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Jawaban / Catatan Mahasiswa", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = InkText)
                                Text(submission.answerText, style = MaterialTheme.typography.bodyMedium, color = InkText)
                            }
                        }
                    }

                    // Uploaded File Row
                    if (!submission.uploadedFileName.isNullOrBlank()) {
                        Surface(
                            color = PureWhite,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, BorderMint),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onOpenLink(submission.uploadedFileName, submission.uploadedFileUrl) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(LightGreenSecondary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.AttachFile, contentDescription = null, tint = DarkCardBg, modifier = Modifier.size(18.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("File Jawaban", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                                    Text(submission.uploadedFileName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = InkText)
                                    submission.uploadedFileSizeLabel?.let {
                                        Text(it, style = MaterialTheme.typography.labelSmall, color = Slate500)
                                    }
                                }
                                Icon(Icons.Rounded.OpenInNew, contentDescription = "Buka file", tint = DarkCardBg, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // Submission URL Row
                    if (!submission.driveLink.isNullOrBlank()) {
                        Surface(
                            color = PureWhite,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, BorderMint),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onOpenLink("Tautan Pengumpulan", submission.driveLink) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(RoyalBlue50),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Link, contentDescription = null, tint = RoyalBlue700, modifier = Modifier.size(18.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Tautan Pengumpulan (URL)", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                                    Text(
                                        text = submission.driveLink,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = RoyalBlue700,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(Icons.Rounded.OpenInNew, contentDescription = "Buka tautan", tint = RoyalBlue700, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Verification Status Box
                if (isCompleted) {
                    Surface(
                        color = Emerald50,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Emerald700, modifier = Modifier.size(20.dp))
                            Text(
                                text = "Terverifikasi Selesai" +
                                    (checklist?.verifiedAt?.let { " pada $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Emerald700
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onToggleChecklist(!isCompleted) },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCompleted) Rose600 else DarkCardBg,
                    contentColor = if (isCompleted) PureWhite else LimePrimary
                ),
                modifier = Modifier.testTag("admin_dialog_toggle_checklist")
            ) {
                Text(if (isCompleted) "Batalkan Checklist" else "Tandai Selesai")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}
