package com.example.ui.screens.admin

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Class
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.importer.ExcelStudentImportParser
import com.example.ui.components.AzrilCard
import com.example.ui.components.AzrilConfirmDeleteDialog
import com.example.ui.components.AzrilEmptyState
import com.example.ui.components.AzrilPageHeader
import com.example.ui.components.AzrilPaginationBar
import com.example.ui.components.AzrilSearchField
import com.example.ui.components.AzrilStatCard
import com.example.ui.components.AzrilStatusBadge
import com.example.ui.theme.Amber50
import com.example.ui.theme.Amber700
import com.example.ui.model.AcademicClass
import com.example.ui.model.AccountStatus
import com.example.ui.model.CsvImportPreviewRow
import com.example.ui.model.StudentBadgeStatus
import com.example.ui.model.StudentRecord
import com.example.ui.model.StudyProgram
import com.example.ui.model.ToastType
import com.example.ui.state.AzrilUiState
import com.example.ui.state.AzrilViewModel
import com.example.ui.theme.BorderLight
import com.example.ui.theme.BorderMint
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.Emerald50
import com.example.ui.theme.Emerald600
import com.example.ui.theme.Emerald700
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkText
import com.example.ui.theme.LightGreenSecondary
import com.example.ui.theme.LimeInk
import com.example.ui.theme.LimePrimary
import com.example.ui.theme.Navy900
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
fun AdminStudyProgramsScreen(
    uiState: AzrilUiState,
    viewModel: AzrilViewModel,
    isExpanded: Boolean
) {
    var query by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf("ALL") }
    var currentPage by rememberSaveable { mutableIntStateOf(1) }
    var showFormDialog by rememberSaveable { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<StudyProgram?>(null) }
    var deletingItem by remember { mutableStateOf<StudyProgram?>(null) }

    val filtered = uiState.studyPrograms.filter { prodi ->
        val matchesQuery = query.isBlank() ||
            prodi.name.contains(query, ignoreCase = true) ||
            prodi.code.contains(query, ignoreCase = true) ||
            prodi.faculty.contains(query, ignoreCase = true)
        val matchesStatus = when (statusFilter) {
            "ACTIVE" -> prodi.isActive
            "INACTIVE" -> !prodi.isActive
            else -> true
        }
        matchesQuery && matchesStatus
    }
    val pageSize = 6
    val totalPages = ((filtered.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    val safePage = currentPage.coerceIn(1, totalPages)
    val pageItems = filtered.drop((safePage - 1) * pageSize).take(pageSize)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AzrilPageHeader(
            title = "Manajemen Program Studi",
            subtitle = "Kelola daftar program studi, fakultas, jumlah kelas, dan mahasiswa terkait.",
            action = {
                Button(
                    onClick = { editingItem = null; showFormDialog = true },
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.testTag("add_study_program_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tambah Prodi")
                }
            }
        )

        AzrilCard {
            AzrilSearchField(
                value = query,
                onValueChange = { query = it; currentPage = 1 },
                placeholder = "Cari kode, nama program studi, atau fakultas...",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = statusFilter == "ALL", onClick = { statusFilter = "ALL" }, label = { Text("Semua") })
                FilterChip(selected = statusFilter == "ACTIVE", onClick = { statusFilter = "ACTIVE" }, label = { Text("Aktif") })
                FilterChip(selected = statusFilter == "INACTIVE", onClick = { statusFilter = "INACTIVE" }, label = { Text("Nonaktif") })
            }
        }

        if (filtered.isEmpty()) {
            AzrilEmptyState(
                title = "Belum Ada Program Studi",
                description = "Tambahkan program studi (contoh: Teknik Informatika, Sistem Informasi) sebagai dasar pembuatan kelas dan pendaftaran mahasiswa.",
                icon = Icons.Rounded.School,
                showIllustration = true,
                actionLabel = "Tambah Program Studi",
                onAction = { editingItem = null; showFormDialog = true }
            )
        } else {
            pageItems.forEach { prodi ->
                val classCount = uiState.classes.count { it.studyProgramId == prodi.id }
                val studentCount = uiState.students.count { it.studyProgramId == prodi.id }
                AzrilCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AzrilStatusBadge(prodi.code, RoyalBlue50, RoyalBlue700)
                                AzrilStatusBadge(
                                    text = if (prodi.isActive) "Aktif" else "Nonaktif",
                                    containerColor = if (prodi.isActive) Emerald50 else SurfaceMuted,
                                    contentColor = if (prodi.isActive) Emerald700 else Slate500
                                )
                            }
                            Text(prodi.name, style = MaterialTheme.typography.titleMedium, color = Navy900)
                            Text(prodi.faculty, style = MaterialTheme.typography.bodySmall, color = Slate500)
                            if (prodi.description.isNotBlank()) {
                                Text(prodi.description, style = MaterialTheme.typography.bodySmall, color = Slate600)
                            }
                        }
                        Row {
                            IconButton(onClick = { editingItem = prodi; showFormDialog = true }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit ${prodi.name}", tint = Slate600)
                            }
                            IconButton(onClick = { deletingItem = prodi }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Hapus ${prodi.name}", tint = Rose600)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = BorderLight)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("$classCount Kelas", style = MaterialTheme.typography.labelMedium, color = Navy900)
                        Text("$studentCount Mahasiswa", style = MaterialTheme.typography.labelMedium, color = Navy900)
                    }
                }
            }
            AzrilPaginationBar(
                currentPage = safePage,
                totalPages = totalPages,
                totalItems = filtered.size,
                onPageChange = { currentPage = it }
            )
        }
    }

    if (showFormDialog) {
        StudyProgramFormDialog(
            initial = editingItem,
            onDismiss = { showFormDialog = false },
            onSave = { code, name, faculty, desc, active ->
                val err = if (editingItem == null) {
                    viewModel.addStudyProgram(code, name, faculty, desc, active)
                } else {
                    viewModel.updateStudyProgram(editingItem!!.id, code, name, faculty, desc, active)
                }
                if (err == null) showFormDialog = false
                err
            }
        )
    }

    deletingItem?.let { item ->
        AzrilConfirmDeleteDialog(
            title = "Hapus Program Studi?",
            message = "Anda akan menghapus ${item.name} (${item.code}). Penghapusan ditolak jika masih memiliki kelas atau mahasiswa aktif.",
            onConfirm = {
                viewModel.deleteStudyProgram(item.id)
                deletingItem = null
            },
            onDismiss = { deletingItem = null }
        )
    }
}

@Composable
private fun StudyProgramFormDialog(
    initial: StudyProgram?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Boolean) -> String?
) {
    var code by rememberSaveable(initial?.id) { mutableStateOf(initial?.code ?: "") }
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var faculty by rememberSaveable(initial?.id) { mutableStateOf(initial?.faculty ?: "") }
    var description by rememberSaveable(initial?.id) { mutableStateOf(initial?.description ?: "") }
    var isActive by rememberSaveable(initial?.id) { mutableStateOf(initial?.isActive ?: true) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = {
            Text(
                if (initial == null) "Tambah Program Studi" else "Edit Program Studi",
                style = MaterialTheme.typography.titleLarge,
                color = Navy900
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it; error = null },
                    label = { Text("Kode Prodi (contoh: TI)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("prodi_code_input")
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Nama Program Studi") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("prodi_name_input")
                )
                OutlinedTextField(
                    value = faculty,
                    onValueChange = { faculty = it; error = null },
                    label = { Text("Fakultas") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("prodi_faculty_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Deskripsi (Opsional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Status Aktif", style = MaterialTheme.typography.bodyMedium, color = Navy900)
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Rose600) }
            }
        },
        confirmButton = {
            Button(
                onClick = { error = onSave(code, name, faculty, description, isActive) },
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.testTag("save_prodi_button")
            ) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminClassesScreen(
    uiState: AzrilUiState,
    viewModel: AzrilViewModel,
    isExpanded: Boolean
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showCsvImport by rememberSaveable { mutableStateOf(false) }
    var deletingClass by remember { mutableStateOf<AcademicClass?>(null) }

    val filtered = uiState.classes.filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true) || it.studyProgramName.contains(query, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AzrilPageHeader(
            title = "Manajemen Kelas",
            subtitle = "Kelola kelas paralel (contoh: Teknik Informatika 3A, 3B, 5A), anggota mahasiswa, dan impor CSV.",
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showCsvImport = true },
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.testTag("import_csv_class_button")
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Impor CSV")
                    }
                    Button(
                        onClick = { showAddDialog = true },
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.testTag("add_class_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tambah Kelas")
                    }
                }
            }
        )

        AzrilCard {
            AzrilSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Cari nama kelas atau program studi...",
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (filtered.isEmpty()) {
            AzrilEmptyState(
                title = "Belum Ada Kelas Perkuliahan",
                description = "Buat kelas seperti Teknik Informatika 3A atau 5B, lalu tambahkan mahasiswa secara manual atau melalui impor CSV.",
                icon = Icons.Rounded.Class,
                showIllustration = true,
                actionLabel = "Tambah Kelas",
                onAction = { showAddDialog = true }
            )
        } else {
            filtered.forEach { cls ->
                val members = uiState.students.filter { it.classId == cls.id }
                AzrilCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AzrilStatusBadge("Semester ${cls.semester}", RoyalBlue50, RoyalBlue700)
                                AzrilStatusBadge(cls.academicYear, SurfaceMuted, Slate600)
                            }
                            Text(cls.name, style = MaterialTheme.typography.titleMedium, color = Navy900)
                            Text("${cls.studyProgramName} • Angkatan ${cls.cohortYear}", style = MaterialTheme.typography.bodySmall, color = Slate500)
                            if (cls.description.isNotBlank()) {
                                Text(cls.description, style = MaterialTheme.typography.bodySmall, color = Slate600)
                            }
                        }
                        IconButton(onClick = { deletingClass = cls }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Hapus kelas ${cls.name}", tint = Rose600)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = BorderLight)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${members.size} Mahasiswa terdaftar di kelas ini",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Navy900
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddClassDialog(
            studyPrograms = uiState.studyPrograms,
            onDismiss = { showAddDialog = false },
            onSave = { name, prodiId, semester, year, cohort, desc, active ->
                val err = viewModel.addClass(name, prodiId, semester, year, cohort, desc, active)
                if (err == null) showAddDialog = false
                err
            }
        )
    }

    if (showCsvImport) {
        CsvStudentImportDialog(
            studyPrograms = uiState.studyPrograms,
            classes = uiState.classes,
            viewModel = viewModel,
            onDismiss = { showCsvImport = false }
        )
    }

    deletingClass?.let { cls ->
        AzrilConfirmDeleteDialog(
            title = "Hapus Kelas?",
            message = "Anda akan menghapus kelas ${cls.name}. Penghapusan ditolak jika masih memiliki mahasiswa, jadwal, atau tugas aktif.",
            onConfirm = {
                viewModel.deleteClass(cls.id)
                deletingClass = null
            },
            onDismiss = { deletingClass = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddClassDialog(
    studyPrograms: List<StudyProgram>,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, String, Int, String, Boolean) -> String?
) {
    var name by rememberSaveable { mutableStateOf("") }
    var selectedProdiId by rememberSaveable { mutableStateOf(studyPrograms.firstOrNull()?.id ?: "") }
    var semester by rememberSaveable { mutableStateOf("3") }
    var academicYear by rememberSaveable { mutableStateOf("2026/2027") }
    var cohortYear by rememberSaveable { mutableStateOf("2025") }
    var description by rememberSaveable { mutableStateOf("") }
    var isActive by rememberSaveable { mutableStateOf(true) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text("Tambah Kelas Baru", style = MaterialTheme.typography.titleLarge, color = Navy900) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (studyPrograms.isEmpty()) {
                    Text(
                        "Belum ada Program Studi. Tambahkan Program Studi terlebih dahulu pada menu Program Studi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Rose700
                    )
                } else {
                    Text("Pilih Program Studi", style = MaterialTheme.typography.labelMedium, color = Navy900)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        studyPrograms.forEach { prodi ->
                            FilterChip(
                                selected = selectedProdiId == prodi.id,
                                onClick = { selectedProdiId = prodi.id; error = null },
                                label = { Text("${prodi.code} - ${prodi.name}") }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Nama Kelas (cth: Teknik Informatika 3A)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = semester,
                        onValueChange = { semester = it.filter(Char::isDigit) },
                        label = { Text("Semester") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cohortYear,
                        onValueChange = { cohortYear = it.filter(Char::isDigit) },
                        label = { Text("Angkatan") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = academicYear,
                    onValueChange = { academicYear = it },
                    label = { Text("Tahun Akademik") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Deskripsi") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Rose600) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    error = onSave(
                        name,
                        selectedProdiId,
                        semester.toIntOrNull() ?: 0,
                        academicYear,
                        cohortYear.toIntOrNull() ?: 2026,
                        description,
                        isActive
                    )
                },
                enabled = studyPrograms.isNotEmpty(),
                shape = RoundedCornerShape(999.dp)
            ) { Text("Simpan Kelas") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminStudentsScreen(
    uiState: AzrilUiState,
    viewModel: AzrilViewModel,
    isExpanded: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedProdiFilter by rememberSaveable { mutableStateOf("ALL") }
    var selectedClassFilter by rememberSaveable { mutableStateOf("ALL") }
    var selectedAccountFilter by rememberSaveable { mutableStateOf("ALL") }
    var selectedBadgeFilter by rememberSaveable { mutableStateOf("ALL") }
    var currentPage by rememberSaveable { mutableIntStateOf(1) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showCsvDialog by rememberSaveable { mutableStateOf(false) }
    var showConnectSupabaseDialog by rememberSaveable { mutableStateOf(false) }
    var editingStudent by remember { mutableStateOf<StudentRecord?>(null) }
    var resettingPasswordStudent by remember { mutableStateOf<StudentRecord?>(null) }
    var deletingStudent by remember { mutableStateOf<StudentRecord?>(null) }

    val filtered = uiState.students.filter { stu ->
        val matchesQuery = query.isBlank() ||
            stu.fullName.contains(query, ignoreCase = true) ||
            stu.nim.contains(query, ignoreCase = true) ||
            stu.email.contains(query, ignoreCase = true)
        val matchesProdi = selectedProdiFilter == "ALL" || stu.studyProgramId == selectedProdiFilter
        val matchesClass = selectedClassFilter == "ALL" || stu.classId == selectedClassFilter
        val matchesAccount = when (selectedAccountFilter) {
            "ACTIVE" -> stu.status == AccountStatus.ACTIVE
            "INACTIVE" -> stu.status != AccountStatus.ACTIVE
            "MUST_CHANGE_PW" -> stu.mustChangePassword
            else -> true
        }
        val matchesBadge = selectedBadgeFilter == "ALL" || stu.badgeStatus.name == selectedBadgeFilter
        matchesQuery && matchesProdi && matchesClass && matchesAccount && matchesBadge
    }

    // Hoisted SAF launchers registered unconditionally at screen level (never inside conditional AlertDialog)
    val openXlsxDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.startExcelFileParseFromUri(context, uri)
        }
    }

    val fallbackGetContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.startExcelFileParseFromUri(context, uri)
        }
    }

    val saveExcelTemplateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val templateBytes = ExcelStudentImportParser.buildBlankTemplateXlsx()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(templateBytes)
                        out.flush()
                    } ?: error("Gagal membuka lokasi penyimpanan.")
                }
            }
            saved.onSuccess {
                viewModel.showToast("Template Excel (.xlsx) berhasil disimpan (sheet PJKR-3B, FARMASI-3, HES-3B).", ToastType.SUCCESS)
            }.onFailure {
                viewModel.showToast("Gagal menyimpan template Excel: ${it.message ?: "kesalahan penyimpanan"}.", ToastType.ERROR)
            }
        }
    }

    fun launchSafeExcelFilePicker() {
        try {
            openXlsxDocumentLauncher.launch(
                arrayOf(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/octet-stream",
                    "*/*"
                )
            )
        } catch (_: ActivityNotFoundException) {
            try {
                fallbackGetContentLauncher.launch("*/*")
            } catch (_: Throwable) {
                viewModel.reportExcelPickerError(
                    "Tidak ditemukan aplikasi pemilih file (File Manager) di perangkat Android ini."
                )
            }
        } catch (_: Throwable) {
            try {
                fallbackGetContentLauncher.launch("*/*")
            } catch (t2: Throwable) {
                viewModel.reportExcelPickerError(
                    "Gagal membuka pemilih file Android: ${t2.message ?: "kesalahan sistem"}."
                )
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val csvText = ExcelStudentImportParser.buildStudentsCsvExport(filtered)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(csvText.toByteArray(Charsets.UTF_8))
                out.flush()
            }
        }.onSuccess {
            viewModel.showToast("Berhasil mengekspor ${filtered.size} data mahasiswa ke CSV.", ToastType.SUCCESS)
        }.onFailure {
            viewModel.showToast("Gagal mengekspor data mahasiswa.", ToastType.ERROR)
        }
    }

    val pageSize = 10
    val totalPages = ((filtered.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    val safePage = currentPage.coerceIn(1, totalPages)
    val pageItems = filtered.drop((safePage - 1) * pageSize).take(pageSize)

    val totalStudents = uiState.students.size
    val pjkrCount = uiState.students.count { it.studyProgramName.equals("PJKR", ignoreCase = true) }
    val farmasiCount = uiState.students.count { it.studyProgramName.equals("FARMASI", ignoreCase = true) }
    val hesCount = uiState.students.count { it.studyProgramName.equals("HES", ignoreCase = true) }
    val activeAccountCount = uiState.students.count { it.status == AccountStatus.ACTIVE }
    val inactiveAccountCount = uiState.students.count { it.status != AccountStatus.ACTIVE }
    val mustChangePwCount = uiState.students.count { it.mustChangePassword }

    val afkCount = uiState.students.count { it.badgeStatus == StudentBadgeStatus.AFK }
    val neutralCount = uiState.students.count { it.badgeStatus == StudentBadgeStatus.NEUTRAL }
    val lulusCount = uiState.students.count { it.badgeStatus == StudentBadgeStatus.LULUS }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AzrilPageHeader(
            title = "Data Mahasiswa",
            subtitle = "Kelola akun mahasiswa berbasis NIM, impor Excel (.xlsx) multi-sheet, filter AFK, reset password, dan ekspor data.",
            action = {
                if (isExpanded) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.openExcelImportDialog() },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LimePrimary, contentColor = InkText),
                            modifier = Modifier.testTag("open_excel_import_button")
                        ) {
                            Icon(Icons.Rounded.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import Excel", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = {
                                if (filtered.isEmpty()) {
                                    viewModel.showToast("Belum ada data mahasiswa untuk diekspor.", ToastType.INFO)
                                } else {
                                    exportCsvLauncher.launch("Data_Mahasiswa_AzrilClas.csv")
                                }
                            },
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.testTag("export_students_csv_button")
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ekspor", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (uiState.isSupabaseConfigured) {
                            OutlinedButton(
                                onClick = { viewModel.refreshStudentsAndClassesFromSupabase(showToastOnComplete = true, force = true) },
                                enabled = !uiState.isSyncingStudents,
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.testTag("refresh_students_supabase_button")
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (uiState.isSyncingStudents) "Memuat..." else "Muat Ulang", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (uiState.students.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { viewModel.syncAllStudentsToSupabase() },
                                    enabled = !uiState.isSyncingStudents,
                                    shape = RoundedCornerShape(999.dp),
                                    modifier = Modifier.testTag("sync_students_supabase_button")
                                ) {
                                    Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (uiState.isSyncingStudents) "Menyinkronkan..." else "Sinkron Supabase", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        Button(
                            onClick = { showAddDialog = true },
                            enabled = !uiState.isSyncingStudents,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkCardBg, contentColor = LimePrimary),
                            modifier = Modifier.testTag("add_student_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tambah", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                } else {
                    // Compact mobile layout: balanced 2-column pill button grid with zero horizontal overflow
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.openExcelImportDialog() },
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = LimePrimary, contentColor = InkText),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("open_excel_import_button")
                            ) {
                                Icon(Icons.Rounded.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import Excel", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Button(
                                onClick = { showAddDialog = true },
                                enabled = !uiState.isSyncingStudents,
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DarkCardBg, contentColor = LimePrimary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("add_student_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tambah", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (filtered.isEmpty()) {
                                        viewModel.showToast("Belum ada data mahasiswa untuk diekspor.", ToastType.INFO)
                                    } else {
                                        exportCsvLauncher.launch("Data_Mahasiswa_AzrilClas.csv")
                                    }
                                },
                                shape = RoundedCornerShape(999.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("export_students_csv_button")
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ekspor", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (uiState.isSupabaseConfigured) {
                                OutlinedButton(
                                    onClick = { viewModel.refreshStudentsAndClassesFromSupabase(showToastOnComplete = true, force = true) },
                                    enabled = !uiState.isSyncingStudents,
                                    shape = RoundedCornerShape(999.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("refresh_students_supabase_button")
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (uiState.isSyncingStudents) "Memuat..." else "Muat Ulang", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if (uiState.isSupabaseConfigured && uiState.students.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { viewModel.syncAllStudentsToSupabase() },
                                enabled = !uiState.isSyncingStudents,
                                shape = RoundedCornerShape(999.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 46.dp)
                                    .testTag("sync_students_supabase_button")
                            ) {
                                Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (uiState.isSyncingStudents) "Menyinkronkan..." else "Sinkron Supabase", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        )

        if (uiState.isSyncingStudents) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().testTag("students_sync_progress"),
                color = LimePrimary,
                trackColor = LightGreenSecondary
            )
        }

        val shouldShowSupabaseBanner = uiState.isSupabaseConfigured &&
            (!uiState.hasLiveSupabaseSession || !uiState.studentsSyncError.isNullOrBlank())
        if (shouldShowSupabaseBanner) {
            AdminSupabaseSyncStatusBanner(
                errorMessage = uiState.studentsSyncError
                    ?: "Sesi Admin belum terhubung ke Supabase Auth. Hubungkan akun Admin Supabase sekarang agar seluruh data mahasiswa langsung tersinkronisasi.",
                hasLiveSession = uiState.hasLiveSupabaseSession,
                isSyncing = uiState.isSyncingStudents,
                onRetrySync = { viewModel.refreshStudentsAndClassesFromSupabase(showToastOnComplete = true, force = true) },
                onOpenConnectDialog = {
                    viewModel.clearAdminSupabaseConnectError()
                    showConnectSupabaseDialog = true
                }
            )
        }

        // 6 Required Statistics Cards
        val statCards = listOf(
            Triple("Total Mahasiswa", "$totalStudents", "${uiState.classes.size} kelas • $afkCount AFK"),
            Triple("Mahasiswa PJKR", "$pjkrCount", "Prodi PJKR"),
            Triple("Mahasiswa FARMASI", "$farmasiCount", "Prodi FARMASI"),
            Triple("Mahasiswa HES", "$hesCount", "Prodi HES"),
            Triple("Akun Aktif", "$activeAccountCount", "Siap login dengan NIM"),
            Triple("Belum Aktif", "$inactiveAccountCount", "$mustChangePwCount belum ganti password")
        )
        val statIcons = listOf(
            Icons.Rounded.Groups,
            Icons.Rounded.School,
            Icons.Rounded.School,
            Icons.Rounded.School,
            Icons.Rounded.CheckCircle,
            Icons.Rounded.PendingActions
        )
        val statCols = if (isExpanded) 3 else 2
        statCards.chunked(statCols).forEachIndexed { rowIdx, chunk ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                chunk.forEachIndexed { colIdx, (label, value, helper) ->
                    val globalIdx = rowIdx * statCols + colIdx
                    val isWarning = globalIdx == 5 && (inactiveAccountCount > 0 || mustChangePwCount > 0)
                    AzrilStatCard(
                        label = label,
                        value = value,
                        helper = helper,
                        icon = statIcons[globalIdx],
                        accentColor = if (isWarning) Amber700 else LimeInk,
                        accentBg = if (isWarning) Amber50 else LightGreenSecondary,
                        mintVariant = globalIdx == 0 || globalIdx == 4,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(statCols - chunk.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }

        // Student Count per Class Summary (clickable filter, horizontal scroll on mobile)
        if (uiState.classes.isNotEmpty()) {
            AzrilCard {
                Text("Jumlah Mahasiswa per Kelas", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = InkText)
                Spacer(modifier = Modifier.height(8.dp))
                val classChipsContent: @Composable () -> Unit = {
                    FilterChip(
                        selected = selectedClassFilter == "ALL",
                        onClick = { selectedClassFilter = "ALL"; currentPage = 1 },
                        label = { Text("Semua Kelas ($totalStudents)", maxLines = 1) },
                        shape = RoundedCornerShape(999.dp)
                    )
                    uiState.classes.forEach { cls ->
                        val countInClass = uiState.students.count { it.classId == cls.id }
                        val afkInClass = uiState.students.count { it.classId == cls.id && it.badgeStatus == StudentBadgeStatus.AFK }
                        FilterChip(
                            selected = selectedClassFilter == cls.id,
                            onClick = {
                                selectedClassFilter = if (selectedClassFilter == cls.id) "ALL" else cls.id
                                currentPage = 1
                            },
                            label = {
                                Text(
                                    text = if (afkInClass > 0) "${cls.name}: $countInClass ($afkInClass AFK)"
                                    else "${cls.name}: $countInClass",
                                    maxLines = 1
                                )
                            },
                            shape = RoundedCornerShape(999.dp)
                        )
                    }
                }
                if (isExpanded) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        classChipsContent()
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        classChipsContent()
                    }
                }
            }
        }

        // Search & Filters (horizontal scroll strips on compact mobile to avoid vertical bloat)
        AzrilCard {
            AzrilSearchField(
                value = query,
                onValueChange = { query = it; currentPage = 1 },
                placeholder = "Cari nama lengkap, NIM, atau email...",
                modifier = Modifier.fillMaxWidth()
            )
            if (uiState.studyPrograms.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text("Program Studi", style = MaterialTheme.typography.labelMedium, color = InkMuted)
                Spacer(modifier = Modifier.height(4.dp))
                val prodiChipsContent: @Composable () -> Unit = {
                    FilterChip(
                        selected = selectedProdiFilter == "ALL",
                        onClick = { selectedProdiFilter = "ALL"; currentPage = 1 },
                        label = { Text("Semua Prodi", maxLines = 1) },
                        shape = RoundedCornerShape(999.dp)
                    )
                    uiState.studyPrograms.forEach { prodi ->
                        FilterChip(
                            selected = selectedProdiFilter == prodi.id,
                            onClick = { selectedProdiFilter = prodi.id; currentPage = 1 },
                            label = { Text(prodi.code, maxLines = 1) },
                            shape = RoundedCornerShape(999.dp)
                        )
                    }
                }
                if (isExpanded) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { prodiChipsContent() }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) { prodiChipsContent() }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text("Status Akun", style = MaterialTheme.typography.labelMedium, color = InkMuted)
            Spacer(modifier = Modifier.height(4.dp))
            val accountChipsContent: @Composable () -> Unit = {
                FilterChip(selected = selectedAccountFilter == "ALL", onClick = { selectedAccountFilter = "ALL"; currentPage = 1 }, label = { Text("Semua Akun", maxLines = 1) }, shape = RoundedCornerShape(999.dp))
                FilterChip(selected = selectedAccountFilter == "ACTIVE", onClick = { selectedAccountFilter = "ACTIVE"; currentPage = 1 }, label = { Text("Aktif ($activeAccountCount)", maxLines = 1) }, shape = RoundedCornerShape(999.dp))
                FilterChip(selected = selectedAccountFilter == "INACTIVE", onClick = { selectedAccountFilter = "INACTIVE"; currentPage = 1 }, label = { Text("Nonaktif ($inactiveAccountCount)", maxLines = 1) }, shape = RoundedCornerShape(999.dp))
                FilterChip(selected = selectedAccountFilter == "MUST_CHANGE_PW", onClick = { selectedAccountFilter = "MUST_CHANGE_PW"; currentPage = 1 }, label = { Text("Belum Ganti Password ($mustChangePwCount)", maxLines = 1) }, shape = RoundedCornerShape(999.dp))
            }
            if (isExpanded) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { accountChipsContent() }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) { accountChipsContent() }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Filter Status / Cap Mahasiswa", style = MaterialTheme.typography.labelMedium, color = InkMuted)
            Spacer(modifier = Modifier.height(4.dp))
            val badgeChipsContent: @Composable () -> Unit = {
                FilterChip(
                    selected = selectedBadgeFilter == "ALL",
                    onClick = { selectedBadgeFilter = "ALL"; currentPage = 1 },
                    label = { Text("Semua (${uiState.students.size})", maxLines = 1) },
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.testTag("filter_badge_all")
                )
                FilterChip(
                    selected = selectedBadgeFilter == StudentBadgeStatus.AFK.name,
                    onClick = { selectedBadgeFilter = StudentBadgeStatus.AFK.name; currentPage = 1 },
                    label = { Text("AFK ($afkCount)", maxLines = 1) },
                    shape = RoundedCornerShape(999.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Rose600,
                        selectedLabelColor = PureWhite,
                        containerColor = Rose50,
                        labelColor = Rose700
                    ),
                    modifier = Modifier.testTag("filter_badge_afk")
                )
                FilterChip(
                    selected = selectedBadgeFilter == StudentBadgeStatus.NEUTRAL.name,
                    onClick = { selectedBadgeFilter = StudentBadgeStatus.NEUTRAL.name; currentPage = 1 },
                    label = { Text("Netral ($neutralCount)", maxLines = 1) },
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.testTag("filter_badge_neutral")
                )
                FilterChip(
                    selected = selectedBadgeFilter == StudentBadgeStatus.LULUS.name,
                    onClick = { selectedBadgeFilter = StudentBadgeStatus.LULUS.name; currentPage = 1 },
                    label = { Text("Lulus ($lulusCount)", maxLines = 1) },
                    shape = RoundedCornerShape(999.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Emerald600,
                        selectedLabelColor = PureWhite,
                        containerColor = Emerald50,
                        labelColor = Emerald700
                    ),
                    modifier = Modifier.testTag("filter_badge_lulus")
                )
            }
            if (isExpanded) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { badgeChipsContent() }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) { badgeChipsContent() }
            }
        }

        if (filtered.isEmpty()) {
            AzrilEmptyState(
                title = "Belum Ada Data Mahasiswa",
                description = "Import file Excel (.xlsx) multi-sheet (PJKR-3B, FARMASI-3, HES-3B) untuk membuat akun mahasiswa secara massal, atau tambahkan mahasiswa satu per satu.",
                icon = Icons.Rounded.Groups,
                showIllustration = true,
                actionLabel = "Import Excel (.xlsx)",
                onAction = { viewModel.openExcelImportDialog() }
            )
        } else {
            pageItems.forEach { stu ->
                val isAfk = stu.badgeStatus == StudentBadgeStatus.AFK
                val isLulus = stu.badgeStatus == StudentBadgeStatus.LULUS
                val cardBorderColor = when (stu.badgeStatus) {
                    StudentBadgeStatus.AFK -> Rose600.copy(alpha = 0.45f)
                    StudentBadgeStatus.LULUS -> Emerald600.copy(alpha = 0.4f)
                    StudentBadgeStatus.NEUTRAL -> BorderMint
                }
                val cardBgColor = when (stu.badgeStatus) {
                    StudentBadgeStatus.AFK -> Rose50.copy(alpha = 0.35f)
                    StudentBadgeStatus.LULUS -> Emerald50.copy(alpha = 0.3f)
                    StudentBadgeStatus.NEUTRAL -> PureWhite
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AzrilStatusBadge("NIM ${stu.nim}", DarkCardBg, LimePrimary)
                                    AzrilStatusBadge(
                                        text = stu.badgeStatus.label,
                                        containerColor = when (stu.badgeStatus) {
                                            StudentBadgeStatus.AFK -> Rose50
                                            StudentBadgeStatus.LULUS -> Emerald50
                                            StudentBadgeStatus.NEUTRAL -> SurfaceMuted
                                        },
                                        contentColor = when (stu.badgeStatus) {
                                            StudentBadgeStatus.AFK -> Rose700
                                            StudentBadgeStatus.LULUS -> Emerald700
                                            StudentBadgeStatus.NEUTRAL -> Slate600
                                        }
                                    )
                                    AzrilStatusBadge(
                                        text = stu.status.label,
                                        containerColor = if (stu.status == AccountStatus.ACTIVE) LightGreenSecondary else Rose50,
                                        contentColor = if (stu.status == AccountStatus.ACTIVE) LimeInk else Rose700
                                    )
                                    if (stu.mustChangePassword) {
                                        AzrilStatusBadge("Password Awal (NIM)", Amber50, Amber700)
                                    }
                                }
                                Text(
                                    text = "${stu.fullName} — ${stu.studyProgramName}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isAfk) Rose700 else InkText,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stu.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate500,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${stu.className ?: "Belum ada kelas"} • Sem ${stu.semester} (${stu.cohortYear})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate600,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row {
                                IconButton(
                                    onClick = { editingStudent = stu },
                                    modifier = Modifier.testTag("edit_student_${stu.nim}")
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit ${stu.fullName}", tint = InkText)
                                }
                                IconButton(
                                    onClick = { deletingStudent = stu },
                                    modifier = Modifier.testTag("delete_student_${stu.nim}")
                                ) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Hapus ${stu.fullName}", tint = Rose600)
                                }
                            }
                        }

                        HorizontalDivider(color = cardBorderColor.copy(alpha = 0.6f))

                        // Quick Admin Cap Status Selector (AFK / Netral / Lulus)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Status / Cap Mahasiswa (Khusus Admin)", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                StudentBadgeStatus.entries.forEach { cap ->
                                    val selected = stu.badgeStatus == cap
                                    FilterChip(
                                        selected = selected,
                                        onClick = { viewModel.updateStudentBadgeStatus(stu.id, cap) },
                                        label = { Text(cap.label) },
                                        shape = RoundedCornerShape(999.dp),
                                        colors = when (cap) {
                                            StudentBadgeStatus.AFK -> FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Rose600,
                                                selectedLabelColor = PureWhite,
                                                containerColor = PureWhite,
                                                labelColor = Rose700
                                            )
                                            StudentBadgeStatus.LULUS -> FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Emerald600,
                                                selectedLabelColor = PureWhite,
                                                containerColor = PureWhite,
                                                labelColor = Emerald700
                                            )
                                            StudentBadgeStatus.NEUTRAL -> FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = DarkCardBg,
                                                selectedLabelColor = LimePrimary,
                                                containerColor = PureWhite,
                                                labelColor = InkText
                                            )
                                        },
                                        modifier = Modifier.testTag("set_badge_${stu.nim}_${cap.dbValue}")
                                    )
                                }
                            }
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { resettingPasswordStudent = stu },
                                shape = RoundedCornerShape(999.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("reset_password_${stu.nim}")
                            ) {
                                Icon(Icons.Rounded.LockReset, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Atur / Reset Password")
                            }
                            TextButton(onClick = { viewModel.toggleStudentStatus(stu.id) }) {
                                Text(if (stu.status == AccountStatus.ACTIVE) "Nonaktifkan Akun" else "Aktifkan Akun")
                            }
                            if (isLulus) {
                                AzrilStatusBadge("Tercatat Lulus", Emerald50, Emerald700)
                            }
                        }
                    }
                }
            }
            AzrilPaginationBar(
                currentPage = safePage,
                totalPages = totalPages,
                totalItems = filtered.size,
                onPageChange = { currentPage = it }
            )
        }
    }

    if (showConnectSupabaseDialog) {
        ConnectSupabaseAdminDialog(
            isConnecting = uiState.isConnectingAdminSupabase,
            errorMessage = uiState.adminSupabaseConnectError,
            onConnect = { identifier, password ->
                viewModel.connectAdminSupabaseSession(identifier, password) {
                    showConnectSupabaseDialog = false
                }
            },
            onClearError = { viewModel.clearAdminSupabaseConnectError() },
            onDismiss = {
                viewModel.clearAdminSupabaseConnectError()
                showConnectSupabaseDialog = false
            }
        )
    }

    if (uiState.excelImportState.isDialogOpen) {
        AdminExcelImportDialog(
            importState = uiState.excelImportState,
            onPickExcelFile = { launchSafeExcelFilePicker() },
            onDownloadTemplate = {
                try {
                    saveExcelTemplateLauncher.launch("Template_Absensi_AzrilClas.xlsx")
                } catch (_: Throwable) {
                    viewModel.reportExcelPickerError("Gagal membuka dialog penyimpanan template di perangkat ini.")
                }
            },
            onCancelActiveOperation = { viewModel.cancelActiveExcelImport() },
            onCommitImport = { summary, importHist ->
                viewModel.commitExcelStudentImport(summary, importHist)
            },
            onDismiss = { viewModel.dismissExcelImportDialog() }
        )
    }

    if (showAddDialog) {
        AddStudentDialog(
            studyPrograms = uiState.studyPrograms,
            classes = uiState.classes,
            onDismiss = { showAddDialog = false },
            onSave = { name, nim, email, prodiId, classId, sem, cohort, initialPassword, badgeStatus ->
                val err = viewModel.addStudent(
                    fullName = name,
                    nim = nim,
                    email = email,
                    studyProgramId = prodiId,
                    classId = classId,
                    semester = sem,
                    cohortYear = cohort,
                    status = AccountStatus.ACTIVE,
                    initialPassword = initialPassword,
                    badgeStatus = badgeStatus
                )
                if (err == null) showAddDialog = false
                err
            }
        )
    }

    editingStudent?.let { stu ->
        EditStudentDialog(
            student = stu,
            studyPrograms = uiState.studyPrograms,
            classes = uiState.classes,
            onDismiss = { editingStudent = null },
            onSave = { name, nim, email, prodiId, classId, sem, cohort, accStatus, badgeStatus ->
                val err = viewModel.updateStudent(
                    studentId = stu.id,
                    fullName = name,
                    nim = nim,
                    email = email,
                    studyProgramId = prodiId,
                    classId = classId,
                    semester = sem,
                    cohortYear = cohort,
                    accountStatus = accStatus,
                    badgeStatus = badgeStatus
                )
                if (err == null) editingStudent = null
                err
            }
        )
    }

    resettingPasswordStudent?.let { stu ->
        ResetStudentPasswordDialog(
            student = stu,
            onDismiss = { resettingPasswordStudent = null },
            onSave = { newPw ->
                val err = viewModel.resetStudentPassword(stu.id, newPw, requirePasswordChange = true)
                if (err == null) resettingPasswordStudent = null
                err
            }
        )
    }

    if (showCsvDialog) {
        CsvStudentImportDialog(
            studyPrograms = uiState.studyPrograms,
            classes = uiState.classes,
            viewModel = viewModel,
            onDismiss = { showCsvDialog = false }
        )
    }

    deletingStudent?.let { stu ->
        AzrilConfirmDeleteDialog(
            title = "Hapus Data Mahasiswa?",
            message = "Hapus ${stu.fullName} (NIM ${stu.nim}) dari daftar mahasiswa beserta kredensial loginnya?",
            onConfirm = {
                viewModel.deleteStudent(stu.id)
                deletingStudent = null
            },
            onDismiss = { deletingStudent = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddStudentDialog(
    studyPrograms: List<StudyProgram>,
    classes: List<AcademicClass>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String?, Int, Int, String, StudentBadgeStatus) -> String?
) {
    var fullName by rememberSaveable { mutableStateOf("") }
    var nim by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var initialPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var selectedProdiId by rememberSaveable { mutableStateOf(studyPrograms.firstOrNull()?.id ?: "") }
    var selectedClassId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedBadge by rememberSaveable { mutableStateOf(StudentBadgeStatus.NEUTRAL) }
    var semester by rememberSaveable { mutableStateOf("1") }
    var cohort by rememberSaveable { mutableStateOf("2026") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val matchingClasses = classes.filter { it.studyProgramId == selectedProdiId }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text("Tambah Mahasiswa Baru", style = MaterialTheme.typography.titleLarge, color = Navy900) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Mahasiswa akan login menggunakan NIM sebagai username dan kata sandi awal di bawah ini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate600
                )
                if (studyPrograms.isEmpty()) {
                    Text("Tambahkan Program Studi terlebih dahulu sebelum mendaftarkan mahasiswa.", style = MaterialTheme.typography.bodySmall, color = Rose700)
                } else {
                    Text("Program Studi *", style = MaterialTheme.typography.labelMedium, color = Navy900)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        studyPrograms.forEach { prodi ->
                            FilterChip(
                                selected = selectedProdiId == prodi.id,
                                onClick = { selectedProdiId = prodi.id; selectedClassId = null; error = null },
                                label = { Text(prodi.code) }
                            )
                        }
                    }
                }
                if (matchingClasses.isNotEmpty()) {
                    Text("Kelas (Opsional)", style = MaterialTheme.typography.labelMedium, color = Navy900)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        matchingClasses.forEach { cls ->
                            FilterChip(
                                selected = selectedClassId == cls.id,
                                onClick = { selectedClassId = if (selectedClassId == cls.id) null else cls.id },
                                label = { Text(cls.name) }
                            )
                        }
                    }
                }
                OutlinedTextField(value = fullName, onValueChange = { fullName = it; error = null }, label = { Text("Nama Lengkap *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = nim, onValueChange = { nim = it; error = null }, label = { Text("NIM (Username Login, Unik) *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it; error = null }, label = { Text("Email Akademik (Opsional)") }, placeholder = { Text("otomatis NIM@mahasiswa.azrilclas.ac.id") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = initialPassword,
                    onValueChange = { initialPassword = it; error = null },
                    label = { Text("Password Awal Mahasiswa *") },
                    placeholder = { Text("Minimal 6 karakter") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Tampilkan kata sandi"
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Status / Cap Mahasiswa", style = MaterialTheme.typography.labelMedium, color = Navy900)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudentBadgeStatus.entries.forEach { cap ->
                        FilterChip(
                            selected = selectedBadge == cap,
                            onClick = { selectedBadge = cap },
                            label = { Text(cap.label) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = semester, onValueChange = { semester = it.filter(Char::isDigit) }, label = { Text("Semester") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = cohort, onValueChange = { cohort = it.filter(Char::isDigit) }, label = { Text("Angkatan") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Rose600) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    error = onSave(
                        fullName,
                        nim,
                        email,
                        selectedProdiId,
                        selectedClassId,
                        semester.toIntOrNull() ?: 1,
                        cohort.toIntOrNull() ?: 2026,
                        initialPassword,
                        selectedBadge
                    )
                },
                enabled = studyPrograms.isNotEmpty(),
                shape = RoundedCornerShape(999.dp)
            ) { Text("Simpan Akun") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditStudentDialog(
    student: StudentRecord,
    studyPrograms: List<StudyProgram>,
    classes: List<AcademicClass>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String?, Int, Int, AccountStatus, StudentBadgeStatus) -> String?
) {
    var fullName by rememberSaveable(student.id) { mutableStateOf(student.fullName) }
    var nim by rememberSaveable(student.id) { mutableStateOf(student.nim) }
    var email by rememberSaveable(student.id) { mutableStateOf(student.email) }
    var selectedProdiId by rememberSaveable(student.id) { mutableStateOf(student.studyProgramId) }
    var selectedClassId by rememberSaveable(student.id) { mutableStateOf(student.classId) }
    var selectedBadge by rememberSaveable(student.id) { mutableStateOf(student.badgeStatus) }
    var semester by rememberSaveable(student.id) { mutableStateOf(student.semester.toString()) }
    var cohort by rememberSaveable(student.id) { mutableStateOf(student.cohortYear.toString()) }
    var error by rememberSaveable(student.id) { mutableStateOf<String?>(null) }

    val matchingClasses = classes.filter { it.studyProgramId == selectedProdiId }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text("Edit Data Mahasiswa", style = MaterialTheme.typography.titleLarge, color = Navy900) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Program Studi", style = MaterialTheme.typography.labelMedium, color = Navy900)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    studyPrograms.forEach { prodi ->
                        FilterChip(
                            selected = selectedProdiId == prodi.id,
                            onClick = { selectedProdiId = prodi.id; selectedClassId = null },
                            label = { Text(prodi.code) }
                        )
                    }
                }
                if (matchingClasses.isNotEmpty()) {
                    Text("Kelas", style = MaterialTheme.typography.labelMedium, color = Navy900)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        matchingClasses.forEach { cls ->
                            FilterChip(
                                selected = selectedClassId == cls.id,
                                onClick = { selectedClassId = if (selectedClassId == cls.id) null else cls.id },
                                label = { Text(cls.name) }
                            )
                        }
                    }
                }
                OutlinedTextField(value = fullName, onValueChange = { fullName = it; error = null }, label = { Text("Nama Lengkap") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = nim, onValueChange = { nim = it; error = null }, label = { Text("NIM (Username Login)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it; error = null }, label = { Text("Email Akademik") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Status / Cap Mahasiswa", style = MaterialTheme.typography.labelMedium, color = Navy900)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudentBadgeStatus.entries.forEach { cap ->
                        FilterChip(
                            selected = selectedBadge == cap,
                            onClick = { selectedBadge = cap },
                            label = { Text(cap.label) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = semester, onValueChange = { semester = it.filter(Char::isDigit) }, label = { Text("Semester") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = cohort, onValueChange = { cohort = it.filter(Char::isDigit) }, label = { Text("Angkatan") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Rose600) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    error = onSave(
                        fullName,
                        nim,
                        email,
                        selectedProdiId,
                        selectedClassId,
                        semester.toIntOrNull() ?: student.semester,
                        cohort.toIntOrNull() ?: student.cohortYear,
                        student.status,
                        selectedBadge
                    )
                },
                shape = RoundedCornerShape(999.dp)
            ) { Text("Simpan Perubahan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun ResetStudentPasswordDialog(
    student: StudentRecord,
    onDismiss: () -> Unit,
    onSave: (String) -> String?
) {
    var newPassword by rememberSaveable(student.id) { mutableStateOf("") }
    var confirmPassword by rememberSaveable(student.id) { mutableStateOf("") }
    var visible by rememberSaveable(student.id) { mutableStateOf(false) }
    var error by rememberSaveable(student.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text("Atur / Reset Kata Sandi", style = MaterialTheme.typography.titleLarge, color = Navy900) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Tetapkan kata sandi baru untuk ${student.fullName} (NIM ${student.nim}). Setelah direset, mahasiswa wajib mengganti kata sandi saat login berikutnya.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate600
                )
                OutlinedButton(
                    onClick = {
                        newPassword = student.nim
                        confirmPassword = student.nim
                        error = null
                    },
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text("Gunakan NIM sebagai Password Reset (${student.nim})")
                }
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; error = null },
                    label = { Text("Kata Sandi Baru (min. 6 karakter)") },
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Tampilkan kata sandi"
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; error = null },
                    label = { Text("Konfirmasi Kata Sandi Baru") },
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Rose600) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    error = when {
                        newPassword.trim().length < 6 -> "Kata sandi baru minimal 6 karakter."
                        newPassword != confirmPassword -> "Konfirmasi kata sandi tidak cocok."
                        else -> onSave(newPassword.trim())
                    }
                },
                shape = RoundedCornerShape(999.dp)
            ) { Text("Simpan Password") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CsvStudentImportDialog(
    studyPrograms: List<StudyProgram>,
    classes: List<AcademicClass>,
    viewModel: AzrilViewModel,
    onDismiss: () -> Unit
) {
    var rawCsv by rememberSaveable { mutableStateOf("") }
    var selectedProdiId by rememberSaveable { mutableStateOf(studyPrograms.firstOrNull()?.id ?: "") }
    var selectedClassId by rememberSaveable { mutableStateOf<String?>(null) }
    var previewRows by remember { mutableStateOf<List<CsvImportPreviewRow>>(emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text("Impor Mahasiswa (CSV / Excel)", style = MaterialTheme.typography.titleLarge, color = Navy900) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Format per baris: Nama Lengkap, NIM, Email, Semester, Angkatan. Sistem otomatis memvalidasi NIM duplikat dan melaporkan baris gagal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate600
                )
                if (studyPrograms.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        studyPrograms.forEach { prodi ->
                            FilterChip(
                                selected = selectedProdiId == prodi.id,
                                onClick = { selectedProdiId = prodi.id; selectedClassId = null },
                                label = { Text(prodi.code) }
                            )
                        }
                    }
                    val clsList = classes.filter { it.studyProgramId == selectedProdiId }
                    if (clsList.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            clsList.forEach { cls ->
                                FilterChip(
                                    selected = selectedClassId == cls.id,
                                    onClick = { selectedClassId = if (selectedClassId == cls.id) null else cls.id },
                                    label = { Text(cls.name) }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = rawCsv,
                    onValueChange = { rawCsv = it },
                    label = { Text("Tempel Data CSV") },
                    placeholder = { Text("Budi Santoso, 230101001, budi@kampus.ac.id, 3, 2025") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { previewRows = viewModel.parseCsvPreview(rawCsv) },
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text("Pratinjau & Validasi Baris")
                }
                if (previewRows.isNotEmpty()) {
                    val valid = previewRows.count { it.errorReason == null }
                    val invalid = previewRows.size - valid
                    Text("Hasil Validasi: $valid valid • $invalid gagal", style = MaterialTheme.typography.labelMedium, color = Navy900)
                    previewRows.take(5).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("#${row.rowNumber} ${row.fullName.ifBlank { "-" }} (${row.nim})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            AzrilStatusBadge(
                                text = row.errorReason ?: "Valid",
                                containerColor = if (row.errorReason == null) Emerald50 else Rose50,
                                contentColor = if (row.errorReason == null) Emerald700 else Rose700
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rows = if (previewRows.isEmpty()) viewModel.parseCsvPreview(rawCsv) else previewRows
                    viewModel.commitCsvImport(rows, selectedProdiId, selectedClassId)
                    onDismiss()
                },
                enabled = rawCsv.isNotBlank() && studyPrograms.isNotEmpty(),
                shape = RoundedCornerShape(999.dp)
            ) { Text("Impor Data Valid") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}
