package com.example.ui.screens.admin

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.AzrilCard
import com.example.ui.components.AzrilConfirmDeleteDialog
import com.example.ui.components.AzrilDarkCard
import com.example.ui.components.AzrilEmptyState
import com.example.ui.components.AzrilPageHeader
import com.example.ui.components.AzrilStatusBadge
import com.example.ui.model.AcademicClass
import com.example.ui.model.Assignment
import com.example.ui.model.AssignmentTargetType
import com.example.ui.model.StudyProgram
import com.example.ui.model.SubmissionMethod
import com.example.ui.model.SubmissionRecord
import com.example.ui.model.SubmissionStatus
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
import com.example.ui.theme.Indigo50
import com.example.ui.theme.Indigo600
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
            subtitle = "Buat penugasan untuk beberapa kelas sekaligus atau per program studi, sertakan 2 file tugas, panduan panjang, dan deadline.",
            action = {
                Button(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkCardBg, contentColor = LimePrimary),
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
                description = "Buat tugas kuliah berdasarkan beberapa kelas sekaligus atau program studi (HES, PJKR, FARMASI) lengkap dengan 2 file tugas dan deskripsi panjang.",
                icon = Icons.AutoMirrored.Rounded.Assignment,
                showIllustration = true,
                actionLabel = "Buat Penugasan",
                onAction = { showCreateDialog = true }
            )
        } else {
            uiState.assignments.forEach { assignment ->
                val subCount = uiState.submissions.count { it.assignmentId == assignment.id }
                val isOverdue = assignment.isDeadlinePassed()
                AzrilCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                AzrilStatusBadge(
                                    text = if (assignment.isActive) "Aktif" else "Nonaktif",
                                    containerColor = if (assignment.isActive) Emerald50 else SurfaceMuted,
                                    contentColor = if (assignment.isActive) Emerald700 else Slate500
                                )
                                AzrilStatusBadge(
                                    text = assignment.targetType.label,
                                    containerColor = LightGreenSecondary,
                                    contentColor = LimeInk
                                )
                                if (isOverdue) {
                                    AzrilStatusBadge("Deadline Terlewat", Rose50, Rose700)
                                }
                            }
                            Text(assignment.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = InkText)
                            Text("${assignment.targetSummaryLabel()} • ${assignment.courseCategory}", style = MaterialTheme.typography.bodySmall, color = Slate600)
                            Text(
                                "Deadline: ${assignment.deadline}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isOverdue) Rose700 else Amber700
                            )
                            if (assignment.description.isNotBlank()) {
                                Text(
                                    text = assignment.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkMuted,
                                    maxLines = 4
                                )
                            }
                            val fileLabels = listOfNotNull(
                                assignment.file1Name?.let { "File 1: $it" },
                                assignment.file2Name?.let { "File 2: $it" }
                            )
                            if (fileLabels.isNotEmpty()) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    fileLabels.forEach { lbl ->
                                        AzrilStatusBadge(lbl, SoftMintBg, InkText)
                                    }
                                }
                            }
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
                    Text("$subCount pengumpulan diterima • Format: ${assignment.allowedFormats.joinToString(", ")}", style = MaterialTheme.typography.labelMedium, color = Navy900)
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateAssignmentDialog(
            classes = uiState.classes,
            studyPrograms = uiState.studyPrograms,
            onDismiss = { showCreateDialog = false },
            onSave = { title, desc, targetType, classIds, prodiIds, category, deadlineDate, deadlineTime, formats, maxMb, method, f1Name, f1Url, f2Name, f2Url ->
                val err = viewModel.addAssignment(
                    title = title,
                    description = desc,
                    targetType = targetType,
                    targetClassIds = classIds,
                    targetStudyProgramIds = prodiIds,
                    courseCategory = category,
                    deadlineDate = deadlineDate,
                    deadlineTime = deadlineTime,
                    allowedFormats = formats,
                    maxFileSizeMb = maxMb,
                    submissionMethod = method,
                    file1Name = f1Name,
                    file1Url = f1Url,
                    file2Name = f2Name,
                    file2Url = f2Url
                )
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
    studyPrograms: List<StudyProgram>,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        description: String,
        targetType: AssignmentTargetType,
        targetClassIds: List<String>,
        targetStudyProgramIds: List<String>,
        category: String,
        deadlineDate: String,
        deadlineTime: String,
        formats: List<String>,
        maxMb: Int,
        method: SubmissionMethod,
        file1Name: String?,
        file1Url: String?,
        file2Name: String?,
        file2Url: String?
    ) -> String?
) {
    val context = LocalContext.current
    val allFormats = listOf("PDF", "DOCX", "PPTX", "XLSX", "ZIP", "JPG", "PNG")
    var targetType by rememberSaveable { mutableStateOf(AssignmentTargetType.CLASS) }
    var selectedClassIds by remember { mutableStateOf(setOf<String>()) }
    var selectedProdiIds by remember { mutableStateOf(setOf<String>()) }

    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }

    var file1Name by rememberSaveable { mutableStateOf("") }
    var file1Url by rememberSaveable { mutableStateOf("") }
    var file2Name by rememberSaveable { mutableStateOf("") }
    var file2Url by rememberSaveable { mutableStateOf("") }

    var deadlineDate by rememberSaveable { mutableStateOf("2026-12-15") }
    var deadlineTime by rememberSaveable { mutableStateOf("23:59") }
    var maxSizeMb by rememberSaveable { mutableStateOf("10") }
    var method by rememberSaveable { mutableStateOf(SubmissionMethod.BOTH) }
    var selectedFormats by remember { mutableStateOf(setOf("PDF", "DOCX")) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    // Zero-permission SAF Document Pickers for File Tugas 1 & File Tugas 2
    fun queryDisplayName(uri: Uri, fallback: String): String {
        var displayName = fallback
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) displayName = cursor.getString(idx) ?: fallback
            }
        }
        return displayName
    }

    val file1Picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            file1Name = queryDisplayName(uri, "file_tugas_1")
            file1Url = uri.toString()
        }
    }

    val file2Picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            file2Name = queryDisplayName(uri, "file_tugas_2")
            file2Url = uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text("Buat Penugasan Baru", style = MaterialTheme.typography.titleLarge, color = Navy900) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Judul Penugasan
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; error = null },
                    label = { Text("1. Judul Penugasan *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("assignment_title_input")
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Mata Kuliah / Kategori (Opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 2. File Tugas Pertama
                Surface(
                    color = SoftMintBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BorderMint),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("2. File Tugas Pertama (Upload atau Link)", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = InkText)
                        OutlinedButton(
                            onClick = { file1Picker.launch(arrayOf("*/*")) },
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (file1Name.isBlank()) "Pilih File Tugas 1 dari Perangkat" else "Terpilih: $file1Name")
                        }
                        OutlinedTextField(
                            value = file1Name,
                            onValueChange = { file1Name = it },
                            label = { Text("Nama File 1") },
                            placeholder = { Text("Contoh: Soal_UTS_HES.pdf") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = file1Url,
                            onValueChange = { file1Url = it },
                            label = { Text("Link File 1 (URL / Drive)") },
                            placeholder = { Text("https://...") },
                            leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 3. File Tugas Kedua
                Surface(
                    color = SoftMintBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BorderMint),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("3. File Tugas Kedua (Upload atau Link)", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = InkText)
                        OutlinedButton(
                            onClick = { file2Picker.launch(arrayOf("*/*")) },
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (file2Name.isBlank()) "Pilih File Tugas 2 dari Perangkat" else "Terpilih: $file2Name")
                        }
                        OutlinedTextField(
                            value = file2Name,
                            onValueChange = { file2Name = it },
                            label = { Text("Nama File 2") },
                            placeholder = { Text("Contoh: Template_Laporan.docx") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = file2Url,
                            onValueChange = { file2Url = it },
                            label = { Text("Link File 2 (URL / Drive)") },
                            placeholder = { Text("https://...") },
                            leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 4. Deskripsi / Panduan Tugas (Unlimited Textarea)
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it; error = null },
                    label = { Text("4. Deskripsi / Panduan Tugas (Tanpa Batas Kata) *") },
                    placeholder = { Text("Tuliskan panduan lengkap, rubrik, format penulisan, dan instruksi pengerjaan...") },
                    minLines = 5,
                    maxLines = Int.MAX_VALUE,
                    modifier = Modifier.fillMaxWidth().testTag("assignment_description_input")
                )

                // 5. Tanggal & Waktu Deadline
                Text("5. Batas Akhir Pengumpulan (Deadline) *", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = InkText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = deadlineDate,
                        onValueChange = { deadlineDate = it; error = null },
                        label = { Text("Tanggal (YYYY-MM-DD)") },
                        placeholder = { Text("2026-12-15") },
                        singleLine = true,
                        modifier = Modifier.weight(1.25f)
                    )
                    OutlinedTextField(
                        value = deadlineTime,
                        onValueChange = { deadlineTime = it; error = null },
                        label = { Text("Waktu (HH:mm)") },
                        placeholder = { Text("23:59") },
                        singleLine = true,
                        modifier = Modifier.weight(0.85f)
                    )
                }

                // 6. Pilihan Target Penugasan (Multi-Kelas vs Program Studi)
                Text("6. Target Penerima Penugasan *", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = InkText)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssignmentTargetType.entries.forEach { tType ->
                        FilterChip(
                            selected = targetType == tType,
                            onClick = { targetType = tType; error = null },
                            label = { Text(tType.label) },
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.testTag("target_type_${tType.dbValue}")
                        )
                    }
                }

                if (targetType == AssignmentTargetType.CLASS) {
                    if (classes.isEmpty()) {
                        Text(
                            "Belum ada kelas tersedia. Buat kelas di menu Kelas terlebih dahulu atau pilih target Berdasarkan Program Studi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Rose700
                        )
                    } else {
                        Surface(
                            color = SoftMintBg,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, BorderMint),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "Pilih satu atau beberapa kelas sekaligus (${selectedClassIds.size} dipilih):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkMuted
                                )
                                classes.forEach { cls ->
                                    val checked = selectedClassIds.contains(cls.id)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedClassIds = if (checked) selectedClassIds - cls.id else selectedClassIds + cls.id
                                                error = null
                                            }
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { isChecked ->
                                                selectedClassIds = if (isChecked) selectedClassIds + cls.id else selectedClassIds - cls.id
                                                error = null
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = DarkCardBg, checkmarkColor = LimePrimary)
                                        )
                                        Column {
                                            Text(cls.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = InkText)
                                            Text("${cls.studyProgramName} • Semester ${cls.semester}", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        color = SoftMintBg,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, BorderMint),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Pilih program studi tujuan (${selectedProdiIds.size} dipilih):",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkMuted
                            )
                            studyPrograms.forEach { prodi ->
                                val checked = selectedProdiIds.contains(prodi.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedProdiIds = if (checked) selectedProdiIds - prodi.id else selectedProdiIds + prodi.id
                                            error = null
                                        }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { isChecked ->
                                            selectedProdiIds = if (isChecked) selectedProdiIds + prodi.id else selectedProdiIds - prodi.id
                                            error = null
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = DarkCardBg, checkmarkColor = LimePrimary)
                                    )
                                    Column {
                                        Text(prodi.code, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = InkText)
                                        Text(prodi.faculty, style = MaterialTheme.typography.labelSmall, color = InkMuted)
                                    }
                                }
                            }
                        }
                    }
                }

                // Pengaturan Pengumpulan Tambahan
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = maxSizeMb,
                        onValueChange = { maxSizeMb = it.filter(Char::isDigit) },
                        label = { Text("Maks MB") },
                        singleLine = true,
                        modifier = Modifier.weight(0.6f)
                    )
                    Column(modifier = Modifier.weight(1.4f)) {
                        Text("Metode Pengumpulan", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SubmissionMethod.entries.forEach { m ->
                                FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m.label) })
                            }
                        }
                    }
                }
                Text("Format File Diizinkan", style = MaterialTheme.typography.labelSmall, color = InkMuted)
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

                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Rose600) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    error = onSave(
                        title,
                        description,
                        targetType,
                        selectedClassIds.toList(),
                        selectedProdiIds.toList(),
                        category,
                        deadlineDate,
                        deadlineTime,
                        selectedFormats.toList(),
                        maxSizeMb.toIntOrNull() ?: 10,
                        method,
                        file1Name.ifBlank { null },
                        file1Url.ifBlank { null },
                        file2Name.ifBlank { null },
                        file2Url.ifBlank { null }
                    )
                },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LimePrimary, contentColor = InkText)
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
