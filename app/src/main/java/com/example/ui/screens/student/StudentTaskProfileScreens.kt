package com.example.ui.screens.student

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.ui.components.AzrilPageHeader
import com.example.ui.components.AzrilStatusBadge
import com.example.ui.model.Assignment
import com.example.ui.model.AssignmentChecklistRecord
import com.example.ui.model.StudentBadgeStatus
import com.example.ui.model.SubmissionMethod
import com.example.ui.model.SubmissionRecord
import com.example.ui.model.TaskCompletionStatus
import com.example.ui.model.ToastType
import com.example.ui.model.resolveTaskCompletionStatus
import com.example.ui.state.AzrilUiState
import com.example.ui.state.AzrilViewModel
import kotlinx.coroutines.delay
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
fun StudentAssignmentsScreen(
    uiState: AzrilUiState,
    viewModel: AzrilViewModel,
    isExpanded: Boolean
) {
    val user = uiState.currentUser ?: return
    val myStudentRecord = uiState.students.find { it.id == user.id }
    val targetedAssignments = remember(uiState.assignments, user, myStudentRecord, uiState.classes) {
        uiState.assignments.filter {
            AzrilViewModel.isAssignmentForStudent(it, user, myStudentRecord, uiState.classes)
        }
    }

    // Automatic initial sync + periodic polling while screen is open so Admin checklist updates appear automatically
    LaunchedEffect(user.id) {
        viewModel.syncAssignmentsAndChecklists(showToastOnComplete = false)
        while (uiState.isSupabaseConfigured) {
            delay(15_000L)
            viewModel.syncAssignmentsAndChecklists(showToastOnComplete = false)
        }
    }

    val mySubmissionsByAssignment = remember(uiState.submissions, user.id) {
        uiState.submissions
            .filter { it.studentId == user.id }
            .associateBy { it.assignmentId }
    }
    val myChecklistsByAssignment = remember(uiState.assignmentChecklists, user.id) {
        uiState.assignmentChecklists
            .filter { it.studentId == user.id }
            .associateBy { it.assignmentId }
    }

    val totalTargeted = targetedAssignments.size
    val completedCount = targetedAssignments.count { a ->
        resolveTaskCompletionStatus(mySubmissionsByAssignment[a.id], myChecklistsByAssignment[a.id]) == TaskCompletionStatus.COMPLETED
    }
    val awaitingCount = targetedAssignments.count { a ->
        resolveTaskCompletionStatus(mySubmissionsByAssignment[a.id], myChecklistsByAssignment[a.id]) == TaskCompletionStatus.AWAITING_VERIFICATION
    }
    val notSubmittedCount = targetedAssignments.count { a ->
        resolveTaskCompletionStatus(mySubmissionsByAssignment[a.id], myChecklistsByAssignment[a.id]) == TaskCompletionStatus.NOT_SUBMITTED
    }
    val progressRatio = if (totalTargeted == 0) 0f else completedCount.toFloat() / totalTargeted.toFloat()
    val progressPercent = (progressRatio * 100f).toInt()
    val effectiveNow = viewModel.currentEffectiveNowMillis()

    var statusFilter by rememberSaveable { mutableStateOf("ALL") }
    var submittingTarget by remember { mutableStateOf<Assignment?>(null) }
    var viewingSubmissionTarget by remember { mutableStateOf<Assignment?>(null) }

    val filteredAssignments = remember(targetedAssignments, statusFilter, mySubmissionsByAssignment, myChecklistsByAssignment) {
        targetedAssignments.filter { a ->
            val st = resolveTaskCompletionStatus(mySubmissionsByAssignment[a.id], myChecklistsByAssignment[a.id])
            statusFilter == "ALL" || st.name == statusFilter
        }
    }

    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    fun openAssignmentFile(label: String, url: String?) {
        val cleanUrl = url?.trim().orEmpty()
        if (cleanUrl.startsWith("https://") || cleanUrl.startsWith("http://")) {
            runCatching { uriHandler.openUri(cleanUrl) }
                .onFailure {
                    viewModel.showToast("Tidak dapat membuka link $label: $cleanUrl", ToastType.WARNING)
                }
        } else if (cleanUrl.startsWith("content://")) {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(cleanUrl)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            }.onFailure {
                viewModel.showToast("File '$label' siap diunduh dari lampiran penugasan.", ToastType.INFO)
            }
        } else {
            viewModel.showToast("Lampiran '$label' tersedia (hubungkan URL/Storage untuk unduhan langsung).", ToastType.INFO)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AzrilPageHeader(
            title = "Tugas Saya",
            subtitle = "Lihat daftar tugas untuk kelas atau prodi Anda, kumpulkan atau perbarui jawaban sebelum deadline (WIB), dan pantau status verifikasi admin.",
            action = {
                OutlinedButton(
                    onClick = { viewModel.syncAssignmentsAndChecklists(showToastOnComplete = true) },
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, BorderMint),
                    modifier = Modifier.testTag("student_sync_assignments_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Perbarui status tugas", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Perbarui", color = InkText)
                }
            }
        )

        // Personal Task Progress Hero Card
        AzrilDarkCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AzrilStatusBadge("PROGRES TUGAS PRIBADI", LimePrimary, DarkCardBg)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (totalTargeted == 0) {
                            "Belum ada tugas aktif"
                        } else {
                            "$completedCount dari $totalTargeted tugas telah selesai"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = PureWhite,
                        modifier = Modifier.testTag("student_task_progress_summary")
                    )
                    Text(
                        text = "Status Selesai ditentukan berdasarkan verifikasi checklist dari Admin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnDarkMuted
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(LimePrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$progressPercent%",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = DarkCardBg
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { progressRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .testTag("student_task_progress_bar"),
                color = LimePrimary,
                trackColor = DarkCardElevated,
                strokeCap = StrokeCap.Round
            )
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AzrilStatusBadge("Selesai: $completedCount", Emerald50, Emerald700)
                AzrilStatusBadge("Menunggu Verifikasi: $awaitingCount", Amber50, Amber700)
                AzrilStatusBadge("Belum Mengumpulkan: $notSubmittedCount", DarkCardElevated, PureWhite)
            }
        }

        if (targetedAssignments.isEmpty()) {
            AzrilEmptyState(
                title = "Belum Ada Tugas untuk Kelas / Prodi Anda",
                description = "Hanya penugasan yang ditujukan kepada kelas (${myStudentRecord?.className ?: user.className ?: "-"}) atau prodi (${myStudentRecord?.studyProgramName ?: user.studyProgramName ?: "-"}) Anda yang muncul di sini.",
                icon = Icons.AutoMirrored.Rounded.Assignment,
                showIllustration = true
            )
        } else {
            // Status Filter Row
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = statusFilter == "ALL",
                    onClick = { statusFilter = "ALL" },
                    label = { Text("Semua ($totalTargeted)") },
                    shape = RoundedCornerShape(999.dp)
                )
                FilterChip(
                    selected = statusFilter == TaskCompletionStatus.NOT_SUBMITTED.name,
                    onClick = { statusFilter = TaskCompletionStatus.NOT_SUBMITTED.name },
                    label = { Text("Belum Mengumpulkan ($notSubmittedCount)") },
                    shape = RoundedCornerShape(999.dp)
                )
                FilterChip(
                    selected = statusFilter == TaskCompletionStatus.AWAITING_VERIFICATION.name,
                    onClick = { statusFilter = TaskCompletionStatus.AWAITING_VERIFICATION.name },
                    label = { Text("Menunggu Verifikasi ($awaitingCount)") },
                    shape = RoundedCornerShape(999.dp)
                )
                FilterChip(
                    selected = statusFilter == TaskCompletionStatus.COMPLETED.name,
                    onClick = { statusFilter = TaskCompletionStatus.COMPLETED.name },
                    label = { Text("Selesai ($completedCount)") },
                    shape = RoundedCornerShape(999.dp)
                )
            }

            if (filteredAssignments.isEmpty()) {
                AzrilEmptyState(
                    title = "Tidak Ada Tugas pada Filter Ini",
                    description = "Pilih filter lain untuk melihat seluruh daftar tugas Anda.",
                    icon = Icons.AutoMirrored.Rounded.Assignment
                )
            } else {
                filteredAssignments.forEach { assignment ->
                    val mySub = mySubmissionsByAssignment[assignment.id]
                    val myChecklist = myChecklistsByAssignment[assignment.id]
                    val completionStatus = resolveTaskCompletionStatus(mySub, myChecklist)
                    val isVerifiedComplete = myChecklist?.isCompleted == true
                    val isDeadlinePassed = assignment.isDeadlinePassed(effectiveNow)

                    AzrilCard(
                        modifier = Modifier.testTag("student_assignment_card_${assignment.id}")
                    ) {
                        // Top Status Badges
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val (statusBg, statusFg) = when (completionStatus) {
                                TaskCompletionStatus.COMPLETED -> Emerald50 to Emerald700
                                TaskCompletionStatus.AWAITING_VERIFICATION -> Amber50 to Amber700
                                TaskCompletionStatus.NOT_SUBMITTED -> Rose50 to Rose700
                            }
                            AzrilStatusBadge(
                                text = completionStatus.label,
                                containerColor = statusBg,
                                contentColor = statusFg
                            )
                            AzrilStatusBadge(
                                text = if (isDeadlinePassed) "Deadline Berakhir • Dikunci" else "Dibuka • Bisa Diedit",
                                containerColor = if (isDeadlinePassed) SurfaceMuted else LightGreenSecondary,
                                contentColor = if (isDeadlinePassed) Rose700 else LimeInk
                            )
                            AzrilStatusBadge(assignment.targetSummaryLabel(), SoftMintBg, InkText)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = assignment.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = InkText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = if (isDeadlinePassed) Rose700 else Amber700,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isDeadlinePassed) {
                                    "Deadline: ${assignment.deadline} (Sudah Berakhir)"
                                } else {
                                    "Deadline: ${assignment.deadline}"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isDeadlinePassed) Rose700 else Amber700
                            )
                        }

                        // Full unlimited description / guide
                        val fullGuide = assignment.description.ifBlank { assignment.instructions }
                        if (fullGuide.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = SoftMintBg,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, BorderMint),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Deskripsi & Panduan Tugas",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = InkText
                                    )
                                    Text(
                                        text = fullGuide,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = InkMuted
                                    )
                                }
                            }
                        }

                        // Two Downloadable / Openable Assignment Files
                        val hasFile1 = !assignment.file1Name.isNullOrBlank() || !assignment.file1Url.isNullOrBlank()
                        val hasFile2 = !assignment.file2Name.isNullOrBlank() || !assignment.file2Url.isNullOrBlank()
                        if (hasFile1 || hasFile2) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Tautan / Berkas Tugas (Klik untuk Buka)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = InkText
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (hasFile1) {
                                    val f1Label = assignment.file1Name ?: "File Tugas 1"
                                    AssignmentFileDownloadCard(
                                        badgeLabel = "File 1",
                                        fileName = f1Label,
                                        fileUrl = assignment.file1Url,
                                        onClick = { openAssignmentFile(f1Label, assignment.file1Url) },
                                        modifier = Modifier.testTag("download_file1_${assignment.id}")
                                    )
                                }
                                if (hasFile2) {
                                    val f2Label = assignment.file2Name ?: "File Tugas 2"
                                    AssignmentFileDownloadCard(
                                        badgeLabel = "File 2",
                                        fileName = f2Label,
                                        fileUrl = assignment.file2Url,
                                        onClick = { openAssignmentFile(f2Label, assignment.file2Url) },
                                        modifier = Modifier.testTag("download_file2_${assignment.id}")
                                    )
                                }
                            }
                        }

                        // READ-ONLY Admin Verification Checklist Indicator (strictly non-interactive!)
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = if (isVerifiedComplete) Emerald50 else SurfaceMuted,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, if (isVerifiedComplete) Emerald700.copy(alpha = 0.35f) else BorderLight),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("student_readonly_checklist_${assignment.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = isVerifiedComplete,
                                    onCheckedChange = null, // Read-only indicator — students CANNOT toggle!
                                    enabled = false,
                                    colors = CheckboxDefaults.colors(
                                        disabledCheckedColor = Emerald700,
                                        disabledUncheckedColor = Slate500,
                                        checkmarkColor = PureWhite
                                    )
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = when (completionStatus) {
                                            TaskCompletionStatus.COMPLETED -> "Checklist Verifikasi Admin: Selesai"
                                            TaskCompletionStatus.AWAITING_VERIFICATION -> "Checklist Verifikasi Admin: Menunggu Verifikasi"
                                            TaskCompletionStatus.NOT_SUBMITTED -> "Checklist Verifikasi Admin: Belum Selesai"
                                        },
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = if (isVerifiedComplete) Emerald700 else InkText
                                    )
                                    Text(
                                        text = if (isVerifiedComplete && !myChecklist?.verifiedAt.isNullOrBlank()) {
                                            "Diverifikasi oleh Admin pada ${myChecklist?.verifiedAt} (Hanya Baca)"
                                        } else {
                                            "Indikator hanya baca — penyelesaian diverifikasi langsung oleh Admin."
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isVerifiedComplete) Emerald700 else InkMuted
                                    )
                                }
                                Icon(
                                    imageVector = if (isVerifiedComplete) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                                    contentDescription = "Status checklist hanya baca",
                                    tint = if (isVerifiedComplete) Emerald700 else Slate500,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Student's Own Submission Summary Box (if already submitted)
                        if (mySub != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = PureWhite,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, BorderMint),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Ringkasan Pengumpulan Anda",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = InkText
                                        )
                                        AzrilStatusBadge(
                                            text = if (mySub.revisionCount > 0) "Diperbarui ${mySub.revisionCount}x" else "Terkirim",
                                            containerColor = RoyalBlue50,
                                            contentColor = RoyalBlue700
                                        )
                                    }
                                    Text("Pengumpulan pertama: ${mySub.firstSubmittedAt}", style = MaterialTheme.typography.labelSmall, color = Slate600)
                                    Text("Pembaruan terakhir: ${mySub.lastUpdatedAt}", style = MaterialTheme.typography.labelSmall, color = Slate600)
                                    mySub.uploadedFileName?.let {
                                        Text("File: $it (${mySub.uploadedFileSizeLabel ?: "-"})", style = MaterialTheme.typography.bodySmall, color = InkText)
                                    }
                                    mySub.driveLink?.let {
                                        Text("Tautan: $it", style = MaterialTheme.typography.bodySmall, color = RoyalBlue700, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    mySub.answerText?.let {
                                        Text("Jawaban: $it", style = MaterialTheme.typography.bodySmall, color = InkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Format diizinkan: ${assignment.allowedFormats.joinToString(", ")} (maks ${assignment.maxFileSizeMb} MB) • Metode: ${assignment.submissionMethod.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate500
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        if (isDeadlinePassed) {
                            // After Deadline: Edit & Re-upload STRICTLY DISABLED; Student can still view saved submission!
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    shape = RoundedCornerShape(999.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("student_deadline_locked_button_${assignment.id}")
                                ) {
                                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Deadline Berakhir — Pengumpulan & Edit Ditutup")
                                }
                                if (mySub != null) {
                                    OutlinedButton(
                                        onClick = { viewingSubmissionTarget = assignment },
                                        shape = RoundedCornerShape(999.dp),
                                        border = BorderStroke(1.dp, BorderMint),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("student_view_locked_submission_${assignment.id}")
                                    ) {
                                        Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Lihat Jawaban Tersimpan", color = InkText)
                                    }
                                }
                            }
                        } else {
                            // Before Deadline: Submit OR Edit / Replace File / Re-upload allowed
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { submittingTarget = assignment },
                                    shape = RoundedCornerShape(999.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (mySub != null) DarkCardBg else LimePrimary,
                                        contentColor = if (mySub != null) LimePrimary else InkText
                                    ),
                                    modifier = Modifier.testTag("student_submit_or_edit_button_${assignment.id}")
                                ) {
                                    Icon(
                                        imageVector = if (mySub != null) Icons.Rounded.Edit else Icons.Rounded.UploadFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (mySub != null) "Edit / Unggah Ulang Jawaban" else "Kumpulkan Jawaban",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (mySub != null) {
                                    OutlinedButton(
                                        onClick = { viewingSubmissionTarget = assignment },
                                        shape = RoundedCornerShape(999.dp),
                                        border = BorderStroke(1.dp, BorderMint)
                                    ) {
                                        Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Lihat Jawaban", color = InkText)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    submittingTarget?.let { assignment ->
        val existingSub = mySubmissionsByAssignment[assignment.id]
        SubmitAssignmentDialog(
            assignment = assignment,
            existingSubmission = existingSub,
            onDismiss = { submittingTarget = null },
            onSubmit = { answerText, submissionUrl, fileName, fileUrl, fileSize, fileBytes, mimeType ->
                val err = viewModel.submitAssignment(
                    assignmentId = assignment.id,
                    driveLink = submissionUrl,
                    uploadedFileName = fileName,
                    uploadedFileSizeLabel = fileSize,
                    answerText = answerText,
                    uploadedFileUrl = fileUrl,
                    uploadedFileBytes = fileBytes,
                    uploadedFileMimeType = mimeType
                )
                if (err == null) submittingTarget = null
                err
            }
        )
    }

    viewingSubmissionTarget?.let { assignment ->
        val existingSub = mySubmissionsByAssignment[assignment.id]
        val existingChecklist = myChecklistsByAssignment[assignment.id]
        if (existingSub != null) {
            StudentSubmissionReadOnlyDialog(
                assignment = assignment,
                submission = existingSub,
                checklist = existingChecklist,
                onOpenLink = { label, url -> openAssignmentFile(label, url) },
                onDismiss = { viewingSubmissionTarget = null }
            )
        } else {
            viewingSubmissionTarget = null
        }
    }
}

@Composable
private fun AssignmentFileDownloadCard(
    badgeLabel: String,
    fileName: String,
    fileUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = PureWhite,
        border = BorderStroke(1.dp, BorderMint)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(LightGreenSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Description, contentDescription = null, tint = DarkCardBg, modifier = Modifier.size(18.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$badgeLabel • $fileName",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = InkText
                    )
                    if (!fileUrl.isNullOrBlank()) {
                        Text(
                            text = fileUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Rounded.Download, contentDescription = "Buka atau unduh $fileName", tint = DarkCardBg)
        }
    }
}

@Composable
private fun SubmitAssignmentDialog(
    assignment: Assignment,
    existingSubmission: SubmissionRecord?,
    onDismiss: () -> Unit,
    onSubmit: (
        answerText: String?,
        submissionUrl: String?,
        fileName: String?,
        fileUrl: String?,
        fileSize: String?,
        fileBytes: ByteArray?,
        mimeType: String?
    ) -> String?
) {
    val context = LocalContext.current
    val isEditing = existingSubmission != null
    var answerText by rememberSaveable(assignment.id) { mutableStateOf(existingSubmission?.answerText.orEmpty()) }
    var submissionUrl by rememberSaveable(assignment.id) { mutableStateOf(existingSubmission?.driveLink.orEmpty()) }
    var pickedFileName by rememberSaveable(assignment.id) { mutableStateOf(existingSubmission?.uploadedFileName) }
    var pickedFileUrl by rememberSaveable(assignment.id) { mutableStateOf(existingSubmission?.uploadedFileUrl) }
    var pickedFileSize by rememberSaveable(assignment.id) { mutableStateOf(existingSubmission?.uploadedFileSizeLabel) }
    var pickedFileBytes by remember(assignment.id) { mutableStateOf<ByteArray?>(null) }
    var pickedMimeType by rememberSaveable(assignment.id) { mutableStateOf<String?>(null) }
    var error by rememberSaveable(assignment.id) { mutableStateOf<String?>(null) }

    // Zero-permission Android Storage Access Framework Document Picker
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var name = "berkas_tugas"
            var sizeLabel = "Ukuran terverifikasi"
            var oversized = false
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                    if (sizeIdx >= 0) {
                        val bytes = cursor.getLong(sizeIdx)
                        val mb = bytes / (1024.0 * 1024.0)
                        sizeLabel = String.format("%.2f MB", mb)
                        if (mb > assignment.maxFileSizeMb) {
                            error = "Ukuran file ($sizeLabel) melebihi batas maksimal ${assignment.maxFileSizeMb} MB."
                            oversized = true
                        }
                    }
                }
            }
            if (!oversized) {
                val loadedBytes = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val maxBytes = assignment.maxFileSizeMb.coerceIn(1, 50) * 1024 * 1024
                        val raw = stream.readBytes()
                        if (raw.size <= maxBytes) raw else null
                    }
                }.getOrNull()
                pickedFileName = name
                pickedFileUrl = uri.toString()
                pickedFileSize = sizeLabel
                pickedFileBytes = loadedBytes
                pickedMimeType = context.contentResolver.getType(uri)
                error = null
            }
        }
    }

    val allowUrl = assignment.submissionMethod != SubmissionMethod.DIRECT_UPLOAD
    val allowUpload = assignment.submissionMethod != SubmissionMethod.GOOGLE_DRIVE

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (isEditing) "Edit / Unggah Ulang Jawaban" else "Kumpulkan Jawaban Tugas",
                    style = MaterialTheme.typography.titleLarge,
                    color = Navy900
                )
                Text("Batas akhir: ${assignment.deadline}", style = MaterialTheme.typography.labelSmall, color = Amber700)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(assignment.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Navy900)
                if (existingSubmission != null) {
                    Surface(
                        color = SoftMintBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderMint),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Pengumpulan pertama: ${existingSubmission.firstSubmittedAt}", style = MaterialTheme.typography.labelSmall, color = Slate600)
                            Text("Pembaruan terakhir: ${existingSubmission.lastUpdatedAt}", style = MaterialTheme.typography.labelSmall, color = Slate600)
                            Text("Pembaruan akan memperbarui data jawaban Anda (tanpa duplikasi).", style = MaterialTheme.typography.labelSmall, color = LimeInk)
                        }
                    }
                }

                // Written Answer / Notes
                OutlinedTextField(
                    value = answerText,
                    onValueChange = { answerText = it; error = null },
                    label = { Text("Jawaban / Catatan Pengumpulan") },
                    placeholder = { Text("Tuliskan jawaban atau catatan singkat pengerjaan Anda...") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("student_submission_answer_input")
                )

                // URL Link Input
                if (allowUrl) {
                    OutlinedTextField(
                        value = submissionUrl,
                        onValueChange = { submissionUrl = it; error = null },
                        label = { Text("Tautan Pengumpulan (URL / Google Drive / Docs)") },
                        placeholder = { Text("https://drive.google.com/...") },
                        leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_submission_url_input")
                    )
                }

                // File Picker / Replace File
                if (allowUpload) {
                    OutlinedButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_submission_pick_file_button")
                    ) {
                        Icon(Icons.Rounded.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (pickedFileName != null) "Ganti File: $pickedFileName" else "Pilih File Jawaban dari Perangkat",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    pickedFileName?.let { fName ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "File aktif: $fName (${pickedFileSize ?: "-"})",
                                style = MaterialTheme.typography.labelSmall,
                                color = Emerald700,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    pickedFileName = null
                                    pickedFileUrl = null
                                    pickedFileSize = null
                                    pickedFileBytes = null
                                    pickedMimeType = null
                                }
                            ) {
                                Text("Hapus", color = Rose600)
                            }
                        }
                    }
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Rose600) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    error = onSubmit(
                        answerText.ifBlank { null },
                        submissionUrl.ifBlank { null },
                        pickedFileName,
                        pickedFileUrl,
                        pickedFileSize,
                        pickedFileBytes,
                        pickedMimeType
                    )
                },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkCardBg, contentColor = LimePrimary),
                modifier = Modifier.testTag("student_submission_confirm_button")
            ) {
                Text(if (isEditing) "Simpan Pembaruan" else "Kirim Jawaban")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun StudentSubmissionReadOnlyDialog(
    assignment: Assignment,
    submission: SubmissionRecord,
    checklist: AssignmentChecklistRecord?,
    onOpenLink: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val completionStatus = resolveTaskCompletionStatus(submission, checklist)
    val isVerified = checklist?.isCompleted == true

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Jawaban Tersimpan Saya", style = MaterialTheme.typography.titleLarge, color = Navy900)
                Text(assignment.title, style = MaterialTheme.typography.labelMedium, color = InkMuted)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AzrilStatusBadge(
                    text = "Status: ${completionStatus.label}",
                    containerColor = if (isVerified) Emerald50 else Amber50,
                    contentColor = if (isVerified) Emerald700 else Amber700
                )
                Surface(
                    color = SoftMintBg,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BorderMint),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Pengumpulan pertama: ${submission.firstSubmittedAt}", style = MaterialTheme.typography.bodySmall, color = Slate600)
                        Text("Pembaruan terakhir: ${submission.lastUpdatedAt}", style = MaterialTheme.typography.bodySmall, color = Slate600)
                        Text("Jumlah revisi: ${submission.revisionCount}x", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                    }
                }
                if (!submission.answerText.isNullOrBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Jawaban Teks", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = InkText)
                        Text(submission.answerText, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                    }
                }
                if (!submission.uploadedFileName.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = { onOpenLink(submission.uploadedFileName, submission.uploadedFileUrl) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${submission.uploadedFileName} (${submission.uploadedFileSizeLabel ?: "-"})", modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
                if (!submission.driveLink.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = { onOpenLink("Tautan Jawaban", submission.driveLink) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(submission.driveLink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

@Composable
fun StudentSubmissionsScreen(
    uiState: AzrilUiState,
    isExpanded: Boolean
) {
    val user = uiState.currentUser ?: return
    val mySubs = uiState.submissions.filter { it.studentId == user.id }
    val myChecklists = uiState.assignmentChecklists
        .filter { it.studentId == user.id }
        .associateBy { it.assignmentId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AzrilPageHeader(
            title = "Pengumpulan Saya",
            subtitle = "Riwayat tugas yang telah Anda kumpulkan, waktu pembaruan terakhir, dan status verifikasi admin."
        )

        if (mySubs.isEmpty()) {
            AzrilEmptyState(
                title = "Belum Ada Riwayat Pengumpulan",
                description = "Semua jawaban teks, berkas, dan tautan URL yang Anda kirimkan akan tersimpan riwayatnya di sini.",
                icon = Icons.Rounded.UploadFile,
                showIllustration = true
            )
        } else {
            mySubs.forEach { sub ->
                val completion = resolveTaskCompletionStatus(sub, myChecklists[sub.assignmentId])
                AzrilCard {
                    AzrilStatusBadge(
                        text = completion.label,
                        containerColor = if (completion == TaskCompletionStatus.COMPLETED) Emerald50 else Amber50,
                        contentColor = if (completion == TaskCompletionStatus.COMPLETED) Emerald700 else Amber700
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(sub.assignmentTitle, style = MaterialTheme.typography.titleMedium, color = Navy900)
                    Text("Pengumpulan pertama: ${sub.firstSubmittedAt}", style = MaterialTheme.typography.bodySmall, color = Slate500)
                    Text("Pembaruan terakhir: ${sub.lastUpdatedAt} • ${sub.revisionCount}x edit", style = MaterialTheme.typography.bodySmall, color = Slate500)
                    sub.driveLink?.let { Text("Tautan: $it", style = MaterialTheme.typography.labelMedium, color = RoyalBlue700) }
                    sub.uploadedFileName?.let { Text("File: $it", style = MaterialTheme.typography.labelMedium, color = Navy900) }
                    sub.answerText?.let { Text("Jawaban: $it", style = MaterialTheme.typography.bodySmall, color = InkMuted, maxLines = 2) }
                }
            }
        }
    }
}

@Composable
fun StudentGradesScreen(
    uiState: AzrilUiState,
    isExpanded: Boolean
) {
    val user = uiState.currentUser ?: return
    val graded = uiState.submissions.filter { it.studentId == user.id && it.finalScore != null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AzrilPageHeader(
            title = "Nilai dan Feedback",
            subtitle = "Lihat nilai resmi dan masukan dari Admin untuk setiap penugasan Anda (hanya baca)."
        )

        if (graded.isEmpty()) {
            AzrilEmptyState(
                title = "Belum Ada Nilai yang Diterbitkan",
                description = "Nilai final dan catatan revisi atau apresiasi dari Admin akan ditampilkan di halaman ini.",
                icon = Icons.Rounded.Grade,
                showIllustration = true
            )
        } else {
            graded.forEach { sub ->
                AzrilCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sub.assignmentTitle, style = MaterialTheme.typography.titleMedium, color = Navy900)
                            Text(sub.className, style = MaterialTheme.typography.bodySmall, color = Slate500)
                        }
                        AzrilStatusBadge("Nilai: ${sub.finalScore}", Emerald50, Emerald700)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = BorderLight)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Feedback Admin:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = Navy900)
                    Text(sub.adminFeedback ?: "-", style = MaterialTheme.typography.bodySmall, color = Slate600)
                }
            }
        }
    }
}

@Composable
fun StudentProfileScreen(
    uiState: AzrilUiState,
    viewModel: AzrilViewModel,
    isExpanded: Boolean,
    onLogout: () -> Unit
) {
    val user = uiState.currentUser ?: return
    val myStudentRecord = uiState.students.find { it.id == user.id }
    val badgeStatus = myStudentRecord?.badgeStatus ?: user.badgeStatus
    val displayName = myStudentRecord?.fullName ?: user.fullName
    val displayNim = myStudentRecord?.nim ?: user.nim ?: "-"
    val displayEmail = myStudentRecord?.email ?: user.email
    val displayProdi = myStudentRecord?.studyProgramName ?: user.studyProgramName ?: "-"
    val displayClass = myStudentRecord?.className ?: user.className ?: "-"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AzrilPageHeader(
            title = "Profil Saya",
            subtitle = "Informasi identitas akademik dan status mahasiswa (hanya baca — dikelola oleh Admin)."
        )

        AzrilDarkCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(LimePrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayName.take(2).uppercase(),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = DarkCardBg
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(displayName, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                    Text("NIM $displayNim • $displayProdi", style = MaterialTheme.typography.bodySmall, color = OnDarkMuted)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AzrilStatusBadge(
                            text = "Status: ${badgeStatus.label}",
                            containerColor = when (badgeStatus) {
                                StudentBadgeStatus.AFK -> Rose600
                                StudentBadgeStatus.LULUS -> Emerald50
                                StudentBadgeStatus.NEUTRAL -> DarkCardElevated
                            },
                            contentColor = when (badgeStatus) {
                                StudentBadgeStatus.AFK -> PureWhite
                                StudentBadgeStatus.LULUS -> Emerald700
                                StudentBadgeStatus.NEUTRAL -> LimePrimary
                            }
                        )
                    }
                }
            }
        }

        AzrilCard {
            Text("Data Akademik (Hanya Baca)", style = MaterialTheme.typography.titleMedium, color = Navy900)
            Spacer(modifier = Modifier.height(12.dp))
            ProfileFieldRow("Nama Lengkap", displayName)
            ProfileFieldRow("NIM (Username Login)", displayNim)
            ProfileFieldRow("Email Akademik", displayEmail)
            ProfileFieldRow("Program Studi", displayProdi)
            ProfileFieldRow("Kelas", displayClass)
            ProfileFieldRow("Status / Cap Mahasiswa", badgeStatus.label)
            ProfileFieldRow("Status Akun", (myStudentRecord?.status ?: user.status).label)
        }

        AzrilCard {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = LimeInk, modifier = Modifier.size(20.dp))
                Text(
                    text = "Data identitas akademik, status/cap mahasiswa (AFK / Netral / Lulus), serta pengaturan ulang kata sandi hanya dapat diubah oleh Admin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = Rose600, contentColor = PureWhite),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.testTag("student_logout_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Keluar dari Akun")
            }
        }
    }
}

@Composable
private fun ProfileFieldRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Slate500)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = Navy900)
        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = BorderLight)
    }
}
