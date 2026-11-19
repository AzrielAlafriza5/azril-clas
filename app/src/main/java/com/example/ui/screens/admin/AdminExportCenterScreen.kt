package com.example.ui.screens.admin

import android.net.Uri
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.export.ExportBuildResult
import com.example.data.export.ExportDatasetType
import com.example.data.export.ExportFilterCriteria
import com.example.data.export.ExportFormat
import com.example.ui.components.AzrilCard
import com.example.ui.components.AzrilDarkCard
import com.example.ui.components.AzrilMintCard
import com.example.ui.components.AzrilPageHeader
import com.example.ui.components.AzrilStatusBadge
import com.example.ui.model.StudentRecord
import com.example.ui.model.ToastType
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
import com.example.ui.theme.SoftMintBg
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminExportCenterScreen(
    uiState: AzrilUiState,
    viewModel: AzrilViewModel,
    isExpanded: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Ensure latest assignments, submissions, & checklists are synced when Admin opens Export Center
    LaunchedEffect(Unit) {
        viewModel.syncAssignmentsAndChecklists()
    }

    // Filter state (Prodi, Kelas, Mahasiswa, Rentang Tanggal WIB)
    var selectedProdiId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedClassId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedStudentId by rememberSaveable { mutableStateOf<String?>(null) }
    var startDateIso by rememberSaveable { mutableStateOf("") }
    var endDateIso by rememberSaveable { mutableStateOf("") }
    var showStudentPicker by remember { mutableStateOf(false) }

    // Per-card format selector (defaults to Excel .xlsx)
    val cardFormats = remember {
        mutableStateMapOf<ExportDatasetType, ExportFormat>().apply {
            ExportDatasetType.entries.forEach { put(it, ExportFormat.EXCEL) }
        }
    }

    // Active export progress & pending file save payload
    var exportingType by remember { mutableStateOf<ExportDatasetType?>(null) }
    var pendingSaveResult by remember { mutableStateOf<ExportBuildResult.Success?>(null) }
    var lastSavedBanner by remember { mutableStateOf<String?>(null) }

    // Zero-permission Android SAF CreateDocument launchers (separate per MIME type for Excel vs CSV)
    fun persistToUri(uri: Uri?, payload: ExportBuildResult.Success?) {
        if (uri == null || payload == null) {
            pendingSaveResult = null
            return
        }
        scope.launch {
            val writeResult = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(payload.bytes)
                        stream.flush()
                    } ?: error("Tidak dapat membuka lokasi penyimpanan file.")
                }
            }
            pendingSaveResult = null
            writeResult.fold(
                onSuccess = {
                    val msg = "File ${payload.fileName} berhasil disimpan (${payload.rowCount} baris, ${payload.sheetCount} sheet)."
                    lastSavedBanner = msg
                    viewModel.showToast(msg, ToastType.SUCCESS)
                },
                onFailure = { ex ->
                    viewModel.showToast(
                        "Gagal menyimpan file ${payload.fileName}: ${ex.message ?: "akses ditolak"}",
                        ToastType.ERROR
                    )
                }
            )
        }
    }

    val createExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ExportFormat.EXCEL.mimeType)
    ) { uri -> persistToUri(uri, pendingSaveResult) }

    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ExportFormat.CSV.mimeType)
    ) { uri -> persistToUri(uri, pendingSaveResult) }

    // Derived filter lists
    val availableClasses = remember(uiState.classes, selectedProdiId) {
        if (selectedProdiId == null) uiState.classes
        else uiState.classes.filter { it.studyProgramId == selectedProdiId }
    }
    val availableStudents = remember(uiState.students, selectedProdiId, selectedClassId) {
        uiState.students.filter { stu ->
            val okProdi = selectedProdiId == null || stu.studyProgramId == selectedProdiId
            val okClass = selectedClassId == null || stu.classId == selectedClassId
            okProdi && okClass
        }.sortedBy { it.fullName.lowercase(Locale.US) }
    }
    val selectedStudentObj = remember(uiState.students, selectedStudentId) {
        uiState.students.find { it.id == selectedStudentId }
    }

    val activeFilter = remember(selectedProdiId, selectedClassId, selectedStudentId, startDateIso, endDateIso) {
        ExportFilterCriteria(
            studyProgramId = selectedProdiId,
            classId = selectedClassId,
            studentId = selectedStudentId,
            startDateIso = startDateIso.trim().ifBlank { null },
            endDateIso = endDateIso.trim().ifBlank { null }
        )
    }

    fun triggerExport(type: ExportDatasetType) {
        if (exportingType != null) return
        val format = cardFormats[type] ?: ExportFormat.EXCEL
        exportingType = type
        scope.launch {
            val result = viewModel.prepareAdminExport(type = type, format = format, filter = activeFilter)
            exportingType = null
            if (result is ExportBuildResult.Success) {
                pendingSaveResult = result
                when (format) {
                    ExportFormat.EXCEL -> createExcelLauncher.launch(result.fileName)
                    ExportFormat.CSV -> createCsvLauncher.launch(result.fileName)
                }
            }
        }
    }

    fun applyQuickDatePreset(daysBack: Int?) {
        if (daysBack == null) {
            startDateIso = ""
            endDateIso = ""
            return
        }
        val tz = TimeZone.getTimeZone("Asia/Jakarta")
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }
        val now = Calendar.getInstance(tz)
        val endStr = sdf.format(Date(now.timeInMillis))
        now.add(Calendar.DAY_OF_YEAR, -daysBack)
        val startStr = sdf.format(Date(now.timeInMillis))
        startDateIso = startStr
        endDateIso = endStr
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Page Header
        AzrilPageHeader(
            title = "Pusat Ekspor Data",
            subtitle = "Unduh seluruh data akademik mahasiswa dalam format Excel (.xlsx) dan CSV (.csv) langsung dari database Supabase.",
            action = {
                OutlinedButton(
                    onClick = { viewModel.triggerRefresh() },
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.testTag("export_refresh_data_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sinkronkan Data")
                }
            }
        )

        // Hero Summary Dark Card (#171A17 + Lime #B5D45D)
        AzrilDarkCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AzrilStatusBadge(
                            text = if (uiState.isSupabaseConfigured) "SUPABASE LIVE" else "DATA LOKAL TERVERIFIKASI",
                            containerColor = LimePrimary,
                            contentColor = DarkCardBg
                        )
                        AzrilStatusBadge(
                            text = "KHUSUS ADMIN",
                            containerColor = DarkCardElevated,
                            contentColor = LimePrimary
                        )
                    }
                    Text(
                        text = "Dokumentasi & Rekapitulasi Akademik Siap Unduh",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = PureWhite
                    )
                    Text(
                        text = "Seluruh file kompatibel dengan Microsoft Excel & Google Sheets tanpa data dummy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnDarkMuted
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(LimePrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDone,
                        contentDescription = null,
                        tint = DarkCardBg,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val totalAtt = uiState.attendanceRecords.size + uiState.historicalAttendanceRecords.size
                ExportHeroStatPill("Mahasiswa", "${uiState.students.size}")
                ExportHeroStatPill("Data Absensi", "$totalAtt")
                ExportHeroStatPill("Penugasan", "${uiState.assignments.size}")
                ExportHeroStatPill("Pengumpulan", "${uiState.submissions.size}")
                ExportHeroStatPill("Transaksi XP", "${uiState.rankingPoints.size}")
            }
        }

        // Last Saved Confirmation Banner
        if (lastSavedBanner != null) {
            Surface(
                color = Emerald50,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Emerald700.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Emerald700)
                    Text(
                        text = lastSavedBanner.orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = Emerald700,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { lastSavedBanner = null }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Tutup", tint = Emerald700, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Global Filter Card (Prodi, Kelas, Mahasiswa, Rentang Tanggal WIB)
        AzrilCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(LightGreenSecondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.FilterList, contentDescription = null, tint = DarkCardBg, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text("Filter Data Ekspor", style = MaterialTheme.typography.titleMedium, color = InkText)
                        Text(
                            text = "Berlaku untuk seluruh kartu ekspor termasuk Absensi, Tugas, XP, dan Laporan Lengkap.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSubtle
                        )
                    }
                }
                if (activeFilter.hasActiveFilter) {
                    TextButton(
                        onClick = {
                            selectedProdiId = null
                            selectedClassId = null
                            selectedStudentId = null
                            startDateIso = ""
                            endDateIso = ""
                        },
                        modifier = Modifier.testTag("export_reset_filter_button")
                    ) {
                        Text("Reset Filter", color = LimeDeep)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Program Studi Filter Chips
            Text("Program Studi", style = MaterialTheme.typography.labelLarge, color = InkText)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedProdiId == null,
                    onClick = {
                        selectedProdiId = null
                        selectedClassId = null
                        selectedStudentId = null
                    },
                    label = { Text("Semua Prodi") },
                    shape = RoundedCornerShape(999.dp),
                    colors = exportChipColors(),
                    modifier = Modifier.testTag("export_filter_prodi_all")
                )
                uiState.studyPrograms.forEach { prodi ->
                    FilterChip(
                        selected = selectedProdiId == prodi.id,
                        onClick = {
                            selectedProdiId = prodi.id
                            if (selectedClassId != null && uiState.classes.none { it.id == selectedClassId && it.studyProgramId == prodi.id }) {
                                selectedClassId = null
                            }
                            if (selectedStudentId != null && uiState.students.none { it.id == selectedStudentId && it.studyProgramId == prodi.id }) {
                                selectedStudentId = null
                            }
                        },
                        label = { Text(prodi.code) },
                        shape = RoundedCornerShape(999.dp),
                        colors = exportChipColors(),
                        modifier = Modifier.testTag("export_filter_prodi_${prodi.code.lowercase(Locale.US)}")
                    )
                }
            }

            // 2. Kelas Filter Chips
            if (availableClasses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Kelas", style = MaterialTheme.typography.labelLarge, color = InkText)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedClassId == null,
                        onClick = {
                            selectedClassId = null
                            selectedStudentId = null
                        },
                        label = { Text("Semua Kelas") },
                        shape = RoundedCornerShape(999.dp),
                        colors = exportChipColors()
                    )
                    availableClasses.forEach { cls ->
                        FilterChip(
                            selected = selectedClassId == cls.id,
                            onClick = {
                                selectedClassId = cls.id
                                if (selectedStudentId != null && uiState.students.none { it.id == selectedStudentId && it.classId == cls.id }) {
                                    selectedStudentId = null
                                }
                            },
                            label = { Text(cls.name) },
                            shape = RoundedCornerShape(999.dp),
                            colors = exportChipColors()
                        )
                    }
                }
            }

            // 3. Mahasiswa Picker
            Spacer(modifier = Modifier.height(12.dp))
            Text("Mahasiswa", style = MaterialTheme.typography.labelLarge, color = InkText)
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                onClick = { showStudentPicker = true },
                color = SoftMintBg,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderMint),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_filter_student_selector")
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
                        Icon(Icons.Rounded.PersonSearch, contentDescription = null, tint = LimeDeep, modifier = Modifier.size(20.dp))
                        Text(
                            text = selectedStudentObj?.let { "${it.fullName} (${it.nim})" }
                                ?: "Semua Mahasiswa (${availableStudents.size} terdaftar)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (selectedStudentObj != null) {
                        IconButton(
                            onClick = { selectedStudentId = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Hapus filter mahasiswa", tint = InkMuted, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Text(
                            text = "Pilih",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = LimeDeep
                        )
                    }
                }
            }

            // 4. Rentang Tanggal (WIB)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Rentang Tanggal (WIB — Format YYYY-MM-DD)", style = MaterialTheme.typography.labelLarge, color = InkText)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = startDateIso.isBlank() && endDateIso.isBlank(),
                    onClick = { applyQuickDatePreset(null) },
                    label = { Text("Semua Tanggal") },
                    shape = RoundedCornerShape(999.dp),
                    colors = exportChipColors()
                )
                FilterChip(
                    selected = false,
                    onClick = { applyQuickDatePreset(7) },
                    label = { Text("7 Hari Terakhir") },
                    shape = RoundedCornerShape(999.dp),
                    colors = exportChipColors()
                )
                FilterChip(
                    selected = false,
                    onClick = { applyQuickDatePreset(30) },
                    label = { Text("30 Hari Terakhir") },
                    shape = RoundedCornerShape(999.dp),
                    colors = exportChipColors()
                )
                FilterChip(
                    selected = false,
                    onClick = { applyQuickDatePreset(180) },
                    label = { Text("Semester Ini") },
                    shape = RoundedCornerShape(999.dp),
                    colors = exportChipColors()
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (isExpanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = startDateIso,
                        onValueChange = { startDateIso = it },
                        label = { Text("Tanggal Awal") },
                        placeholder = { Text("2026-10-01") },
                        leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).testTag("export_start_date_input")
                    )
                    OutlinedTextField(
                        value = endDateIso,
                        onValueChange = { endDateIso = it },
                        label = { Text("Tanggal Akhir") },
                        placeholder = { Text("2026-12-31") },
                        leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).testTag("export_end_date_input")
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = startDateIso,
                        onValueChange = { startDateIso = it },
                        label = { Text("Tanggal Awal (YYYY-MM-DD)") },
                        placeholder = { Text("2026-10-01") },
                        leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("export_start_date_input")
                    )
                    OutlinedTextField(
                        value = endDateIso,
                        onValueChange = { endDateIso = it },
                        label = { Text("Tanggal Akhir (YYYY-MM-DD)") },
                        placeholder = { Text("2026-12-31") },
                        leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("export_end_date_input")
                    )
                }
            }
        }

        // 5 Export Cards Grid
        val individualSpecs = listOf(
            ExportCardSpec(
                type = ExportDatasetType.ATTENDANCE,
                icon = Icons.Rounded.FactCheck,
                highlights = listOf("Nama, NIM, Prodi & Kelas", "Tanggal & Waktu Absen WIB", "Status & Keterangan Izin")
            ),
            ExportCardSpec(
                type = ExportDatasetType.ASSIGNMENTS,
                icon = Icons.AutoMirrored.Rounded.Assignment,
                highlights = listOf("Sudah / Belum / Terlambat", "Link & Lokasi File Tugas", "Nilai, Catatan & Verifikasi")
            ),
            ExportCardSpec(
                type = ExportDatasetType.XP_AND_RANKING,
                icon = Icons.Rounded.EmojiEvents,
                highlights = listOf("Total XP & Peringkat di Kelas", "Riwayat & Sumber XP Lengkap", "Tanggal Perolehan XP (WIB)")
            ),
            ExportCardSpec(
                type = ExportDatasetType.STUDENTS,
                icon = Icons.Rounded.Groups,
                highlights = listOf("Daftar Mahasiswa Terdaftar", "Prodi, Kelas & Total XP", "Status Akun & Akademik")
            )
        )

        val cols = if (isExpanded) 2 else 1
        individualSpecs.chunked(cols).forEach { rowSpecs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                rowSpecs.forEach { spec ->
                    val rowCount = remember(uiState, activeFilter, spec.type) {
                        viewModel.estimateExportRowCount(spec.type, activeFilter)
                    }
                    ExportDatasetCard(
                        spec = spec,
                        selectedFormat = cardFormats[spec.type] ?: ExportFormat.EXCEL,
                        estimatedRows = rowCount,
                        isExporting = exportingType == spec.type,
                        isAnyExporting = exportingType != null,
                        onFormatSelected = { cardFormats[spec.type] = it },
                        onDownloadClick = { triggerExport(spec.type) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowSpecs.size < cols) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Card 5: Ekspor Laporan Lengkap (Full-Width Mint Highlight Card with 6 Excel Sheets)
        val completeRowCount = remember(uiState, activeFilter) {
            viewModel.estimateExportRowCount(ExportDatasetType.COMPLETE_REPORT, activeFilter)
        }
        CompleteReportExportCard(
            selectedFormat = cardFormats[ExportDatasetType.COMPLETE_REPORT] ?: ExportFormat.EXCEL,
            estimatedRows = completeRowCount,
            isExporting = exportingType == ExportDatasetType.COMPLETE_REPORT,
            isAnyExporting = exportingType != null,
            onFormatSelected = { cardFormats[ExportDatasetType.COMPLETE_REPORT] = it },
            onDownloadClick = { triggerExport(ExportDatasetType.COMPLETE_REPORT) }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showStudentPicker) {
        StudentFilterPickerDialog(
            students = availableStudents,
            selectedStudentId = selectedStudentId,
            onSelect = { id ->
                selectedStudentId = id
                showStudentPicker = false
            },
            onDismiss = { showStudentPicker = false }
        )
    }
}

private data class ExportCardSpec(
    val type: ExportDatasetType,
    val icon: ImageVector,
    val highlights: List<String>
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExportDatasetCard(
    spec: ExportCardSpec,
    selectedFormat: ExportFormat,
    estimatedRows: Int,
    isExporting: Boolean,
    isAnyExporting: Boolean,
    onFormatSelected: (ExportFormat) -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AzrilCard(modifier = modifier.testTag("export_card_${spec.type.name.lowercase(Locale.US)}")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(LightGreenSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(spec.icon, contentDescription = null, tint = DarkCardBg, modifier = Modifier.size(22.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = spec.type.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = InkText
                    )
                    Text(
                        text = "${spec.type.baseFileName}.${selectedFormat.extension}",
                        style = MaterialTheme.typography.labelSmall,
                        color = LimeDeep,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            AzrilStatusBadge(
                text = "$estimatedRows baris",
                containerColor = if (estimatedRows > 0) LightGreenSecondary else Amber50,
                contentColor = if (estimatedRows > 0) LimeInk else Amber700
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = spec.type.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted
        )

        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            spec.highlights.forEach { item ->
                Surface(
                    color = SoftMintBg,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, BorderMint)
                ) {
                    Text(
                        text = item,
                        style = MaterialTheme.typography.labelSmall,
                        color = InkText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = BorderMint.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ExportFormat.entries.forEach { fmt ->
                    val chosen = selectedFormat == fmt
                    FilterChip(
                        selected = chosen,
                        onClick = { onFormatSelected(fmt) },
                        label = {
                            Text(
                                text = if (fmt == ExportFormat.EXCEL) "Excel" else "CSV",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = exportChipColors(),
                        modifier = Modifier.testTag("export_format_${spec.type.name.lowercase(Locale.US)}_${fmt.extension}")
                    )
                }
            }

            Button(
                onClick = onDownloadClick,
                enabled = !isAnyExporting,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkCardBg, contentColor = LimePrimary),
                modifier = Modifier.testTag("export_download_${spec.type.name.lowercase(Locale.US)}")
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        color = LimePrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Menyiapkan...")
                } else {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Unduh")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompleteReportExportCard(
    selectedFormat: ExportFormat,
    estimatedRows: Int,
    isExporting: Boolean,
    isAnyExporting: Boolean,
    onFormatSelected: (ExportFormat) -> Unit,
    onDownloadClick: () -> Unit
) {
    val sixSheets = listOf(
        "1. Data Mahasiswa",
        "2. Data Absensi",
        "3. Data Pengumpulan Tugas",
        "4. Rekapitulasi Tugas",
        "5. Riwayat XP",
        "6. Ranking Mahasiswa"
    )

    AzrilMintCard(modifier = Modifier.fillMaxWidth().testTag("export_card_complete_report")) {
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
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkCardBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.FolderZip, contentDescription = null, tint = LimePrimary, modifier = Modifier.size(24.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = ExportDatasetType.COMPLETE_REPORT.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = InkText
                    )
                    Text(
                        text = "${ExportDatasetType.COMPLETE_REPORT.baseFileName}.${selectedFormat.extension}",
                        style = MaterialTheme.typography.labelMedium,
                        color = LimeInk
                    )
                }
            }
            AzrilStatusBadge(
                text = "6 Sheet • $estimatedRows baris",
                containerColor = DarkCardBg,
                contentColor = LimePrimary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Laporan gabungan komprehensif untuk dokumentasi, rekapitulasi semester, evaluasi, dan pelaporan pembelajaran. Pada format Excel (.xlsx), seluruh kategori disusun otomatis menjadi 6 sheet terpisah.",
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted
        )

        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sixSheets.forEach { sheetName ->
                Surface(
                    color = PureWhite,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, BorderMint)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Rounded.TableChart, contentDescription = null, tint = LimeDeep, modifier = Modifier.size(14.dp))
                        Text(sheetName, style = MaterialTheme.typography.labelSmall, color = InkText)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportFormat.entries.forEach { fmt ->
                    FilterChip(
                        selected = selectedFormat == fmt,
                        onClick = { onFormatSelected(fmt) },
                        label = { Text(fmt.label) },
                        shape = RoundedCornerShape(999.dp),
                        colors = exportChipColors(),
                        modifier = Modifier.testTag("export_format_complete_report_${fmt.extension}")
                    )
                }
            }
            Button(
                onClick = onDownloadClick,
                enabled = !isAnyExporting,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkCardBg, contentColor = LimePrimary),
                modifier = Modifier.testTag("export_download_complete_report")
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        color = LimePrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Menyiapkan...")
                } else {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Unduh Laporan")
                }
            }
        }
    }
}

@Composable
private fun ExportHeroStatPill(label: String, value: String) {
    Surface(
        color = DarkCardElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, LimePrimary.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = LimePrimary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = OnDarkMuted
            )
        }
    }
}

@Composable
private fun StudentFilterPickerDialog(
    students: List<StudentRecord>,
    selectedStudentId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(students, query) {
        val q = query.trim().lowercase(Locale.US)
        if (q.isEmpty()) students
        else students.filter {
            it.fullName.lowercase(Locale.US).contains(q) ||
                it.nim.lowercase(Locale.US).contains(q) ||
                it.className.orEmpty().lowercase(Locale.US).contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Pilih Mahasiswa", style = MaterialTheme.typography.titleLarge, color = InkText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Cari Nama / NIM") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        Surface(
                            color = if (selectedStudentId == null) LightGreenSecondary else SoftMintBg,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(null) }
                        ) {
                            Text(
                                text = "Semua Mahasiswa (${students.size})",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = InkText,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    items(filtered, key = { it.id }) { stu ->
                        val isSelected = stu.id == selectedStudentId
                        Surface(
                            color = if (isSelected) DarkCardBg else SoftMintBg,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(stu.id) }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = stu.fullName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isSelected) LimePrimary else InkText
                                )
                                Text(
                                    text = "NIM ${stu.nim} • ${stu.studyProgramName} • ${stu.className ?: "-"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) OnDarkMuted else InkSubtle
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup", color = DarkCardBg)
            }
        }
    )
}

@Composable
private fun exportChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = DarkCardBg,
    selectedLabelColor = LimePrimary,
    containerColor = SoftMintBg,
    labelColor = InkText
)
