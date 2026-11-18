package com.example.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.AzrilCard
import com.example.ui.components.AzrilConfirmDeleteDialog
import com.example.ui.components.AzrilEmptyState
import com.example.ui.components.AzrilPageHeader
import com.example.ui.components.AzrilStatusBadge
import com.example.ui.model.AcademicClass
import com.example.ui.model.Assignment
import com.example.ui.model.SubmissionMethod
import com.example.ui.model.SubmissionRecord
import com.example.ui.model.SubmissionStatus
import com.example.ui.state.AzrilUiState
import com.example.ui.state.AzrilViewModel
import com.example.ui.theme.Amber50
import com.example.ui.theme.Amber700
import com.example.ui.theme.BorderLight
import com.example.ui.theme.Emerald50
import com.example.ui.theme.Emerald700
import com.example.ui.theme.Indigo50
import com.example.ui.theme.Indigo600
import com.example.ui.theme.Navy900
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Rose600
import com.example.ui.theme.Rose700
import com.example.ui.components.AzrilDarkCard
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.DarkCardElevated
import com.example.ui.theme.LimePrimary
import com.example.ui.theme.OnDarkMuted
import com.example.ui.theme.RoyalBlue50
import com.example.ui.theme.RoyalBlue700
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.SurfaceMuted

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminAssignmentsScreen(
    uiState: AzrilUiState,
    viewModel: AzrilViewModel,
    isExpanded: Boolean
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var deletingAssignment by remember { mutableStateOf<Assignment?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AzrilPageHeader(
            title = "Manajemen Penugasan",
            subtitle = "Buat penugasan per kelas, atur format file diizinkan, batas ukuran, metode Drive/Upload, dan deadline.",
            action = {
                Button(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.testTag("add_assignment_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Buat Tugas")
                }
            }
        )

        if (uiState.assignments.isEmpty()) {
            AzrilEmptyState(
                title = "Belum Ada Penugasan",
                description = "Buat tugas kuliah dengan instruksi lengkap, lampiran materi, format file (PDF, DOCX, PPTX, XLSX, ZIP, JPG, PNG), dan batas waktu.",
                icon = Icons.Rounded.Assignment,
                showIllustration = true,
                actionLabel = "Buat Penugasan",
                onAction = { showCreateDialog = true }
            )
        } else {
            uiState.assignments.forEach { assignment ->
                val subCount = uiState.submissions.count { it.assignmentId == assignment.id }
                AzrilCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AzrilStatusBadge(
                                    text = if (assignment.isActive) "Aktif" else "Nonaktif",
                                    containerColor = if (assignment.isActive) Emerald50 else SurfaceMuted,
                                    contentColor = if (assignment.isActive) Emerald700 else Slate500
                                )
                                AzrilStatusBadge(assignment.submissionMethod.label, RoyalBlue50, RoyalBlue700)
                            }
                            Text(assignment.title, style = MaterialTheme.typography.titleMedium, color = Navy900)
                            Text("${assignment.className} • ${assignment.courseCategory}", style = MaterialTheme.typography.bodySmall, color = Slate600)
                            Text("Deadline: ${assignment.deadline} • Maks ${assignment.maxFileSizeMb} MB", style = MaterialTheme.typography.labelMedium, color = Amber700)
                            Text("Format: ${assignment.allowedFormats.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = Slate500)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = assignment.isActive, onCheckedChange = { viewModel.toggleAssignmentActive(assignment.id) })
                            IconButton(onClick = { deletingAssignment = assignment }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Hapus tugas", tint = Rose600)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = BorderLight)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("$subCount pengumpulan diterima", style = MaterialTheme.typography.labelMedium, color = Navy900)
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateAssignmentDialog(
            classes = uiState.classes,
            onDismiss = { showCreateDialog = false },
            onSave = { title, desc, instructions, classId, category, deadline, formats, maxMb, method, material ->
                val err = viewModel.addAssignment(title, desc, instructions, classId, category, deadline, formats, maxMb, method, material)
                if (err == null) showCreateDialog = false
                err
            }
        )
    }

    deletingAssignment?.let { item ->
        AzrilConfirmDeleteDialog(
            title = "Hapus Penugasan?",
            message = "Hapus tugas '${item.title}'? Penghapusan ditolak jika sudah ada pengumpulan mahasiswa.",
            onConfirm = {
                viewModel.deleteAssignment(item.id)
                deletingAssignment = null
            },
            onDismiss = { deletingAssignment = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateAssignmentDialog(
    classes: List<AcademicClass>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, List<String>, Int, SubmissionMethod, String) -> String?
) {
    val allFormats = listOf("PDF", "DOCX", "PPTX", "XLSX", "ZIP", "JPG", "PNG")
    var classId by rememberSaveable { mutableStateOf(classes.firstOrNull()?.id ?: "") }
    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var instructions by rememberSaveable { mutableStateOf("") }
    var deadline by rememberSaveable { mutableStateOf("30 Nov 2026, 23:59 WIB") }
    var maxSizeMb by rememberSaveable { mutableStateOf("10") }
    var method by rememberSaveable { mutableStateOf(SubmissionMethod.BOTH) }
    var selectedFormats by remember { mutableStateOf(setOf("PDF", "DOCX")) }
    var material by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text("Buat Penugasan Baru", style = MaterialTheme.typography.titleLarge, color = Navy900) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (classes.isEmpty()) {
                    Text("Buat Kelas terlebih dahulu sebelum membuat penugasan.", style = MaterialTheme.typography.bodySmall, color = Rose700)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        classes.forEach { cls ->
                            FilterChip(selected = classId == cls.id, onClick = { classId = cls.id }, label = { Text(cls.name) })
                        }
                    }
                }
                OutlinedTextField(value = title, onValueChange = { title = it; error = null }, label = { Text("Judul Tugas") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Mata Kuliah / Kategori") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Deskripsi Singkat") }, maxLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = instructions, onValueChange = { instructions = it; error = null }, label = { Text("Instruksi Pengerjaan") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = deadline, onValueChange = { deadline = it }, label = { Text("Deadline") }, singleLine = true, modifier = Modifier.weight(1.5f))
                    OutlinedTextField(value = maxSizeMb, onValueChange = { maxSizeMb = it.filter(Char::isDigit) }, label = { Text("Maks MB") }, singleLine = true, modifier = Modifier.weight(0.7f))
                }
                Text("Metode Pengumpulan", style = MaterialTheme.typography.labelMedium, color = Navy900)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubmissionMethod.entries.forEach { m ->
                        FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m.label) })
                    }
                }
                Text("Format File Diizinkan", style = MaterialTheme.typography.labelMedium, color = Navy900)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    allFormats.forEach { fmt ->
                        val active = selectedFormats.contains(fmt)
                        FilterChip(
                            selected = active,
                            onClick = {
                                selectedFormats = if (active) selectedFormats - fmt else selectedFormats + fmt
                            },
                            label = { Text(fmt) }
                        )
                    }
                }
                OutlinedTextField(value = material, onValueChange = { material = it }, label = { Text("Nama Lampiran Materi (Opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Rose600) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    error = onSave(title, description, instructions, classId, category, deadline, selectedFormats.toList(), maxSizeMb.toIntOrNull() ?: 10, method, material)
                },
                enabled = classes.isNotEmpty(),
                shape = RoundedCornerShape(999.dp)
            ) { Text("Publikasikan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminSubmissionsScreen(
    uiState: AzrilUiState,
    viewModel: AzrilViewModel,
    isExpanded: Boolean
) {
    var statusFilter by rememberSaveable { mutableStateOf<String>("ALL") }
    var gradingTarget by remember { mutableStateOf<SubmissionRecord?>(null) }

    val filtered = uiState.submissions.filter {
        statusFilter == "ALL" || it.status.name == statusFilter
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AzrilPageHeader(
            title = "Pengumpulan Mahasiswa",
            subtitle = "Pantau berkas unggahan langsung maupun link Google Drive beserta status evaluasi."
        )

        AzrilCard {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = statusFilter == "ALL", onClick = { statusFilter = "ALL" }, label = { Text("Semua") })
                SubmissionStatus.entries.forEach { st ->
                    FilterChip(selected = statusFilter == st.name, onClick = { statusFilter = st.name }, label = { Text(st.label) })
                }
            }
        }

        if (filtered.isEmpty()) {
            AzrilEmptyState(
                title = "Belum Ada Pengumpulan",
                description = "Semua pengumpulan tugas dari mahasiswa (Google Drive atau upload file privat) akan tampil di sini.",
                icon = Icons.Rounded.UploadFile,
                showIllustration = true
            )
        } else {
            filtered.forEach { sub ->
                AzrilCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AzrilStatusBadge(sub.status.label, RoyalBlue50, RoyalBlue700)
                            Text(sub.assignmentTitle, style = MaterialTheme.typography.titleMedium, color = Navy900)
                            Text("${sub.studentName} (${sub.studentNim}) • ${sub.className}", style = MaterialTheme.typography.bodySmall, color = Slate600)
                            Text("Dikumpulkan: ${sub.submittedAt}", style = MaterialTheme.typography.bodySmall, color = Slate500)
                            sub.driveLink?.let { Text("Drive: $it", style = MaterialTheme.typography.labelMedium, color = RoyalBlue700) }
                            sub.uploadedFileName?.let { Text("File: $it (${sub.uploadedFileSizeLabel ?: "-"})", style = MaterialTheme.typography.labelMedium, color = Navy900) }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.requestAiCheckStageNotice(sub.id) }, shape = RoundedCornerShape(999.dp)) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Periksa dengan AI")
                        }
                        Button(onClick = { gradingTarget = sub }, shape = RoundedCornerShape(999.dp)) {
                            Text("Beri Nilai / Revisi")
                        }
                    }
                }
            }
        }
    }

    gradingTarget?.let { sub ->
        GradeSubmissionDialog(
            submission = sub,
            onDismiss = { gradingTarget = null },
            onSave = { score, feedback, revision ->
                val err = viewModel.saveGrade(sub.id, score, feedback, revision)
                if (err == null) gradingTarget = null
                err
            }
        )
    }
}

@Composable
fun AdminAiAssistantScreen(
    uiState: AzrilUiState,
    viewModel: AzrilViewModel,
    isExpanded: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AzrilPageHeader(
            title = "AI Assignment Assistant",
            subtitle = "Modul pemeriksaan kesesuaian tugas berbasis Gemini AI untuk membantu verifikasi kelengkapan dokumen."
        )

        // Mandatory Human-in-the-loop Disclaimer Banner
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Amber50,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Amber700.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = Amber700)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Rekomendasi AI — Bukan Keputusan Nilai Final",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Amber700
                    )
                    Text(
                        "AI tidak dapat mengubah nilai final atau meluluskan tugas secara mutlak. Wewenang penuh untuk menyetujui, menolak, meminta revisi, dan memberikan nilai final tetap berada pada Admin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Navy900
                    )
                }
            }
        }

        // Structured AI Schema Overview Card (Dark Card + Lime Green Accents)
        AzrilDarkCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Struktur Laporan Pemeriksaan AI", style = MaterialTheme.typography.titleMedium, color = PureWhite)
                AzrilStatusBadge("Gemini AI", LimePrimary, DarkCardBg)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Setiap pengumpulan diperiksa secara terstruktur mencakup: Status File, Kelengkapan, Persentase Kesesuaian, Ringkasan Isi, Bagian Sesuai, Bagian Belum Sesuai, Rekomendasi Revisi, dan Waktu Pemeriksaan.",
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkMuted
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AzrilStatusBadge("Sesuai", LimePrimary, DarkCardBg)
                AzrilStatusBadge("Perlu Revisi", Amber50, Amber700)
                AzrilStatusBadge("Tidak Lengkap", DarkCardElevated, OnDarkMuted)
            }
        }

        if (uiState.submissions.isEmpty()) {
            AzrilEmptyState(
                title = "Belum Ada Pengumpulan untuk Diperiksa AI",
                description = "Ketika mahasiswa mengumpulkan tugas, gunakan tombol 'Periksa dengan AI', 'Periksa Ulang', 'Setujui', atau 'Minta Revisi' dari daftar antrean di sini.",
                icon = Icons.Rounded.AutoAwesome
            )
        } else {
            uiState.submissions.forEach { sub ->
                AzrilCard {
                    Text(sub.assignmentTitle, style = MaterialTheme.typography.titleMedium, color = Navy900)
                    Text("${sub.studentName} • ${sub.className}", style = MaterialTheme.typography.bodySmall, color = Slate500)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.requestAiCheckStageNotice(sub.id) }, shape = RoundedCornerShape(999.dp)) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Periksa dengan AI")
                        }
                        OutlinedButton(onClick = { viewModel.requestAiCheckStageNotice(sub.id) }, shape = RoundedCornerShape(999.dp)) {
                            Text("Periksa Ulang")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminGradingScreen(
    uiState: AzrilUiState,
    viewModel: AzrilViewModel,
    isExpanded: Boolean
) {
    var gradingTarget by remember { mutableStateOf<SubmissionRecord?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AzrilPageHeader(
            title = "Penilaian & Feedback",
            subtitle = "Berikan nilai final, tulis catatan feedback, minta revisi, dan pantau riwayat perubahan nilai."
        )

        if (uiState.submissions.isEmpty()) {
            AzrilEmptyState(
                title = "Belum Ada Tugas untuk Dinilai",
                description = "Daftar pengumpulan tugas mahasiswa beserta riwayat perubahan nilai akan muncul di sini.",
                icon = Icons.Rounded.Grade,
                showIllustration = true
            )
        } else {
            uiState.submissions.forEach { sub ->
                AzrilCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(sub.assignmentTitle, style = MaterialTheme.typography.titleMedium, color = Navy900)
                            Text("${sub.studentName} (${sub.studentNim})", style = MaterialTheme.typography.bodySmall, color = Slate600)
                            sub.adminFeedback?.let {
                                Text("Feedback: $it", style = MaterialTheme.typography.bodySmall, color = Slate500)
                            }
                            if (sub.gradeHistory.isNotEmpty()) {
                                Text("Riwayat perubahan nilai: ${sub.gradeHistory.size}x", style = MaterialTheme.typography.labelSmall, color = RoyalBlue700)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AzrilStatusBadge(
                                text = sub.finalScore?.let { "Nilai: $it" } ?: "Belum Dinilai",
                                containerColor = if (sub.finalScore != null) Emerald50 else Amber50,
                                contentColor = if (sub.finalScore != null) Emerald700 else Amber700
                            )
                            Button(onClick = { gradingTarget = sub }, shape = RoundedCornerShape(999.dp)) {
                                Text(if (sub.finalScore == null) "Beri Nilai" else "Ubah Nilai")
                            }
                        }
                    }
                }
            }
        }
    }

    gradingTarget?.let { sub ->
        GradeSubmissionDialog(
            submission = sub,
            onDismiss = { gradingTarget = null },
            onSave = { score, feedback, revision ->
                val err = viewModel.saveGrade(sub.id, score, feedback, revision)
                if (err == null) gradingTarget = null
                err
            }
        )
    }
}

@Composable
private fun GradeSubmissionDialog(
    submission: SubmissionRecord,
    onDismiss: () -> Unit,
    onSave: (Int, String, Boolean) -> String?
) {
    var scoreText by rememberSaveable { mutableStateOf(submission.finalScore?.toString() ?: "") }
    var feedback by rememberSaveable { mutableStateOf(submission.adminFeedback ?: "") }
    var requestRevision by rememberSaveable { mutableStateOf(submission.status == SubmissionStatus.NEEDS_REVISION) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text("Penilaian Tugas", style = MaterialTheme.typography.titleLarge, color = Navy900) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${submission.studentName} • ${submission.assignmentTitle}", style = MaterialTheme.typography.bodyMedium, color = Slate600)
                OutlinedTextField(
                    value = scoreText,
                    onValueChange = { scoreText = it.filter(Char::isDigit); error = null },
                    label = { Text("Nilai Akhir (0 - 100)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = feedback,
                    onValueChange = { feedback = it; error = null },
                    label = { Text("Feedback untuk Mahasiswa") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tandai Perlu Revisi", style = MaterialTheme.typography.bodyMedium, color = Navy900)
                    Switch(checked = requestRevision, onCheckedChange = { requestRevision = it })
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Rose600) }
            }
        },
        confirmButton = {
            Button(
                onClick = { error = onSave(scoreText.toIntOrNull() ?: -1, feedback, requestRevision) },
                shape = RoundedCornerShape(999.dp)
            ) { Text("Simpan Nilai") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
