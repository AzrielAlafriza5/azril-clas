package com.example.ui.screens.admin

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
import androidx.compose.material.icons.rounded.Class
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.School
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.ui.components.AzrilPaginationBar
import com.example.ui.components.AzrilSearchField
import com.example.ui.components.AzrilStatusBadge
import com.example.ui.model.AcademicClass
import com.example.ui.model.AccountStatus
import com.example.ui.model.CsvImportPreviewRow
import com.example.ui.model.StudentRecord
import com.example.ui.model.StudyProgram
import com.example.ui.model.ToastType
import com.example.ui.state.AzrilUiState
import com.example.ui.state.AzrilViewModel
import com.example.ui.theme.BorderLight
import com.example.ui.theme.Emerald50
import com.example.ui.theme.Emerald700
import com.example.ui.theme.Navy900
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Rose50
import com.example.ui.theme.Rose600
import com.example.ui.theme.Rose700
import com.example.ui.theme.RoyalBlue50
import com.example.ui.theme.RoyalBlue700
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
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
    var query by rememberSaveable { mutableStateOf("") }
    var selectedProdiFilter by rememberSaveable { mutableStateOf("ALL") }
    var currentPage by rememberSaveable { mutableIntStateOf(1) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showCsvDialog by rememberSaveable { mutableStateOf(false) }
    var deletingStudent by remember { mutableStateOf<StudentRecord?>(null) }

    val filtered = uiState.students.filter { stu ->
        val matchesQuery = query.isBlank() ||
            stu.fullName.contains(query, ignoreCase = true) ||
            stu.nim.contains(query, ignoreCase = true) ||
            stu.email.contains(query, ignoreCase = true)
        val matchesProdi = selectedProdiFilter == "ALL" || stu.studyProgramId == selectedProdiFilter
        matchesQuery && matchesProdi
    }
    val pageSize = 8
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
            title = "Manajemen Mahasiswa",
            subtitle = "Kelola profil akademik, validasi NIM unik, penempatan kelas, impor massal CSV, dan status akun.",
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showCsvDialog = true },
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Impor")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.showToast(
                                if (filtered.isEmpty()) "Belum ada data mahasiswa untuk diekspor."
                                else "Ekspor ${filtered.size} data mahasiswa ke CSV siap dijalankan di Tahap 3.",
                                ToastType.INFO
                            )
                        },
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Unduh")
                    }
                    Button(
                        onClick = { showAddDialog = true },
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.testTag("add_student_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tambah")
                    }
                }
            }
        )

        AzrilCard {
            AzrilSearchField(
                value = query,
                onValueChange = { query = it; currentPage = 1 },
                placeholder = "Cari berdasarkan nama lengkap, NIM, atau email...",
                modifier = Modifier.fillMaxWidth()
            )
            if (uiState.studyPrograms.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedProdiFilter == "ALL",
                        onClick = { selectedProdiFilter = "ALL" },
                        label = { Text("Semua Prodi") }
                    )
                    uiState.studyPrograms.forEach { prodi ->
                        FilterChip(
                            selected = selectedProdiFilter == prodi.id,
                            onClick = { selectedProdiFilter = prodi.id },
                            label = { Text(prodi.code) }
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            AzrilEmptyState(
                title = "Belum Ada Data Mahasiswa",
                description = "Tambahkan mahasiswa secara satuan atau impor massal via CSV. Password tidak disimpan di tabel profil (menggunakan Auth terpisah pada Tahap 2).",
                icon = Icons.Rounded.Groups,
                showIllustration = true,
                actionLabel = "Tambah Mahasiswa",
                onAction = { showAddDialog = true }
            )
        } else {
            pageItems.forEach { stu ->
                AzrilCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AzrilStatusBadge("NIM ${stu.nim}", RoyalBlue50, RoyalBlue700)
                                AzrilStatusBadge(
                                    text = stu.status.label,
                                    containerColor = if (stu.status == AccountStatus.ACTIVE) Emerald50 else Rose50,
                                    contentColor = if (stu.status == AccountStatus.ACTIVE) Emerald700 else Rose700
                                )
                            }
                            Text(stu.fullName, style = MaterialTheme.typography.titleMedium, color = Navy900)
                            Text(stu.email, style = MaterialTheme.typography.bodySmall, color = Slate500)
                            Text(
                                "${stu.studyProgramName} • ${stu.className ?: "Belum ada kelas"} • Sem ${stu.semester} (${stu.cohortYear})",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate600
                            )
                        }
                        Row {
                            TextButton(onClick = { viewModel.toggleStudentStatus(stu.id) }) {
                                Text(if (stu.status == AccountStatus.ACTIVE) "Nonaktifkan" else "Aktifkan")
                            }
                            IconButton(onClick = { deletingStudent = stu }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Hapus ${stu.fullName}", tint = Rose600)
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

    if (showAddDialog) {
        AddStudentDialog(
            studyPrograms = uiState.studyPrograms,
            classes = uiState.classes,
            onDismiss = { showAddDialog = false },
            onSave = { name, nim, email, prodiId, classId, sem, cohort ->
                val err = viewModel.addStudent(name, nim, email, prodiId, classId, sem, cohort, AccountStatus.PENDING_ACTIVATION)
                if (err == null) showAddDialog = false
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
            message = "Hapus ${stu.fullName} (NIM ${stu.nim}) dari daftar mahasiswa?",
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
    onSave: (String, String, String, String, String?, Int, Int) -> String?
) {
    var fullName by rememberSaveable { mutableStateOf("") }
    var nim by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var selectedProdiId by rememberSaveable { mutableStateOf(studyPrograms.firstOrNull()?.id ?: "") }
    var selectedClassId by rememberSaveable { mutableStateOf<String?>(null) }
    var semester by rememberSaveable { mutableStateOf("1") }
    var cohort by rememberSaveable { mutableStateOf("2026") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val matchingClasses = classes.filter { it.studyProgramId == selectedProdiId }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text("Tambah Mahasiswa", style = MaterialTheme.typography.titleLarge, color = Navy900) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (studyPrograms.isEmpty()) {
                    Text("Tambahkan Program Studi terlebih dahulu sebelum mendaftarkan mahasiswa.", style = MaterialTheme.typography.bodySmall, color = Rose700)
                } else {
                    Text("Program Studi", style = MaterialTheme.typography.labelMedium, color = Navy900)
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
                OutlinedTextField(value = fullName, onValueChange = { fullName = it; error = null }, label = { Text("Nama Lengkap") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = nim, onValueChange = { nim = it; error = null }, label = { Text("NIM (Unik)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it; error = null }, label = { Text("Email Akademik") }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
                    error = onSave(fullName, nim, email, selectedProdiId, selectedClassId, semester.toIntOrNull() ?: 1, cohort.toIntOrNull() ?: 2026)
                },
                enabled = studyPrograms.isNotEmpty(),
                shape = RoundedCornerShape(999.dp)
            ) { Text("Simpan") }
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
