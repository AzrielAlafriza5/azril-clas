package com.example.ui.screens.admin

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.data.importer.ExcelStudentImportParser
import com.example.ui.components.AzrilStatusBadge
import com.example.ui.model.ExcelImportPreviewRow
import com.example.ui.model.ExcelImportPreviewSummary
import com.example.ui.model.ExcelImportValidationStatus
import com.example.ui.model.ToastType
import com.example.ui.state.AzrilViewModel
import com.example.ui.theme.Amber50
import com.example.ui.theme.Amber700
import com.example.ui.theme.BorderLight
import com.example.ui.theme.BorderMint
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.Emerald50
import com.example.ui.theme.Emerald600
import com.example.ui.theme.Emerald700
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSubtle
import com.example.ui.theme.InkText
import com.example.ui.theme.LightGreenSecondary
import com.example.ui.theme.LimeInk
import com.example.ui.theme.LimePrimary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Rose50
import com.example.ui.theme.Rose600
import com.example.ui.theme.Rose700
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.SoftMintBg
import com.example.ui.theme.SurfaceMuted

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminExcelImportDialog(
    viewModel: AzrilViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var previewSummary by remember { mutableStateOf<ExcelImportPreviewSummary?>(null) }
    var parseError by rememberSaveable { mutableStateOf<String?>(null) }
    var filterTab by rememberSaveable { mutableStateOf("ALL") }
    var selectedSheetFilter by rememberSaveable { mutableStateOf("ALL") }
    var importHistoricalAttendance by rememberSaveable { mutableStateOf(true) }
    var showConfirmDialog by rememberSaveable { mutableStateOf(false) }

    val openXlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        parseError = null
        val displayName = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "import_mahasiswa.xlsx"

        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()

        if (bytes == null || bytes.isEmpty()) {
            parseError = "Gagal membaca file '$displayName'. Pastikan file berformat Excel (.xlsx)."
            return@rememberLauncherForActivityResult
        }

        val result = viewModel.parseExcelStudentImport(displayName, bytes)
        result.onSuccess { summary ->
            previewSummary = summary
            filterTab = "ALL"
            selectedSheetFilter = "ALL"
            importHistoricalAttendance = summary.hasWeeklyAttendance
        }.onFailure { err ->
            previewSummary = null
            parseError = err.message ?: "File Excel (.xlsx) tidak dapat dibaca."
        }
    }

    val saveTemplateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val templateBytes = ExcelStudentImportParser.buildBlankTemplateXlsx()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(templateBytes)
                out.flush()
            }
        }.onSuccess {
            viewModel.showToast("Template Excel (.xlsx) berhasil disimpan (sheet PJKR-3B, FARMASI-3, HES-3B).", ToastType.SUCCESS)
        }.onFailure {
            viewModel.showToast("Gagal menyimpan template Excel.", ToastType.ERROR)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .padding(vertical = 12.dp),
        containerColor = PureWhite,
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(DarkCardBg, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.TableChart, contentDescription = null, tint = LimePrimary, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text("Import Data Mahasiswa (.xlsx)", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = InkText)
                    Text("Multi-sheet • Deteksi Warna Merah (AFK) & Blok Hitam (Dilewati)", style = MaterialTheme.typography.labelSmall, color = InkSubtle)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 540.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // File Picker & Template Controls
                Surface(
                    color = SoftMintBg,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, BorderMint),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Pilih file Excel (.xlsx) dengan sheet kelas (contoh: PJKR-3B, FARMASI-3, HES-3B). Sistem membaca warna sel langsung: Blok Hitam dilewati total, Warna Merah masuk kategori AFK, warna lain diproses normal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    openXlsxLauncher.launch(
                                        arrayOf(
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "application/vnd.ms-excel",
                                            "*/*"
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DarkCardBg, contentColor = LimePrimary),
                                modifier = Modifier.testTag("pick_excel_file_button")
                            ) {
                                Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (previewSummary == null) "Pilih File Excel (.xlsx)" else "Ganti File Excel")
                            }
                            OutlinedButton(
                                onClick = { saveTemplateLauncher.launch("Template_Absensi_AzrilClas.xlsx") },
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.testTag("download_excel_template_button")
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Template .xlsx")
                            }
                        }
                    }
                }

                parseError?.let { err ->
                    Surface(
                        color = Rose50,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Rose600.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Rose700, modifier = Modifier.size(18.dp))
                            Text(err, style = MaterialTheme.typography.bodySmall, color = Rose700)
                        }
                    }
                }

                val summary = previewSummary
                if (summary != null) {
                    // Sheet mapping chips
                    Surface(
                        color = PureWhite,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, BorderMint),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "File: ${summary.fileName} (${summary.sheetMappings.size} Sheet Terdeteksi)",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = InkText
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                summary.sheetMappings.forEach { sm ->
                                    AzrilStatusBadge(
                                        text = "${sm.sheetName} -> ${sm.studyProgramCode} (${sm.classLabel})",
                                        containerColor = if (sm.isRecognized) LightGreenSecondary else Rose50,
                                        contentColor = if (sm.isRecognized) LimeInk else Rose700
                                    )
                                }
                            }
                        }
                    }

                    // 4 Required Category Summary Cards
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PreviewCountTile(
                            label = "Siap Diimpor",
                            count = summary.validNormalCount,
                            bg = Emerald50,
                            fg = Emerald700,
                            modifier = Modifier.weight(1f)
                        )
                        PreviewCountTile(
                            label = "AFK (Merah)",
                            count = summary.validAfkCount,
                            bg = Rose50,
                            fg = Rose700,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PreviewCountTile(
                            label = "Blok Hitam (Lewati)",
                            count = summary.skippedBlackCount,
                            bg = DarkCardBg,
                            fg = PureWhite,
                            modifier = Modifier.weight(1f)
                        )
                        PreviewCountTile(
                            label = "Duplikat / Invalid",
                            count = summary.totalDuplicateOrInvalidAndExistingCount,
                            bg = Amber50,
                            fg = Amber700,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Historical attendance toggle (Weeks 1..8)
                    if (summary.hasWeeklyAttendance) {
                        Surface(
                            color = LightGreenSecondary.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, BorderMint),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        "Impor Riwayat Absensi (Minggu 1–8)",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = InkText
                                    )
                                    Text(
                                        "Status asli Excel (HADIR, IZIN, ALPHA, TIDAK HADIR, DISPEN, KIP) dipertahankan sebagai data historis tanpa pemberian XP otomatis.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InkMuted
                                    )
                                }
                                Switch(
                                    checked = importHistoricalAttendance,
                                    onCheckedChange = { importHistoricalAttendance = it },
                                    modifier = Modifier.testTag("toggle_import_historical_attendance")
                                )
                            }
                        }
                    }

                    // Account auto-provisioning policy note
                    Surface(
                        color = SoftMintBg,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Info, contentDescription = null, tint = LimeInk, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Setiap mahasiswa valid (${summary.totalImportableCount} orang) otomatis dibuatkan 1 akun dengan Username = NIM, Password awal = NIM, dan wajib mengganti password saat login pertama.",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkMuted
                            )
                        }
                    }

                    // Sheet filter & category filter tabs
                    if (summary.sheetMappings.size > 1) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = selectedSheetFilter == "ALL",
                                onClick = { selectedSheetFilter = "ALL" },
                                label = { Text("Semua Sheet") },
                                shape = RoundedCornerShape(999.dp)
                            )
                            summary.sheetMappings.forEach { sm ->
                                FilterChip(
                                    selected = selectedSheetFilter == sm.sheetName,
                                    onClick = { selectedSheetFilter = sm.sheetName },
                                    label = { Text(sm.sheetName) },
                                    shape = RoundedCornerShape(999.dp)
                                )
                            }
                        }
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = filterTab == "ALL",
                            onClick = { filterTab = "ALL" },
                            label = { Text("Semua (${summary.rows.size})") },
                            shape = RoundedCornerShape(999.dp)
                        )
                        FilterChip(
                            selected = filterTab == "VALID",
                            onClick = { filterTab = "VALID" },
                            label = { Text("Valid (${summary.validNormalCount})") },
                            shape = RoundedCornerShape(999.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Emerald600,
                                selectedLabelColor = PureWhite
                            )
                        )
                        FilterChip(
                            selected = filterTab == "AFK",
                            onClick = { filterTab = "AFK" },
                            label = { Text("AFK Merah (${summary.validAfkCount})") },
                            shape = RoundedCornerShape(999.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Rose600,
                                selectedLabelColor = PureWhite
                            )
                        )
                        FilterChip(
                            selected = filterTab == "BLACK",
                            onClick = { filterTab = "BLACK" },
                            label = { Text("Blok Hitam (${summary.skippedBlackCount})") },
                            shape = RoundedCornerShape(999.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DarkCardBg,
                                selectedLabelColor = PureWhite
                            )
                        )
                        FilterChip(
                            selected = filterTab == "PROBLEM",
                            onClick = { filterTab = "PROBLEM" },
                            label = { Text("Bermasalah (${summary.totalDuplicateOrInvalidAndExistingCount})") },
                            shape = RoundedCornerShape(999.dp)
                        )
                    }

                    val filteredRows = summary.rows.filter { row ->
                        val sheetOk = selectedSheetFilter == "ALL" || row.sheetName == selectedSheetFilter
                        val tabOk = when (filterTab) {
                            "VALID" -> row.validationStatus == ExcelImportValidationStatus.VALID_NORMAL
                            "AFK" -> row.validationStatus == ExcelImportValidationStatus.VALID_AFK
                            "BLACK" -> row.validationStatus == ExcelImportValidationStatus.SKIPPED_BLACK
                            "PROBLEM" -> row.validationStatus == ExcelImportValidationStatus.INVALID_OR_DUPLICATE ||
                                row.validationStatus == ExcelImportValidationStatus.ALREADY_EXISTS
                            else -> true
                        }
                        sheetOk && tabOk
                    }

                    if (filteredRows.isEmpty()) {
                        Text("Tidak ada baris pada filter ini.", style = MaterialTheme.typography.bodySmall, color = Slate500)
                    } else {
                        filteredRows.take(80).forEach { row ->
                            PreviewStudentRowCard(row = row)
                        }
                        if (filteredRows.size > 80) {
                            Text(
                                "Menampilkan 80 dari ${filteredRows.size} baris. Gunakan filter sheet/kategori untuk melihat baris lainnya.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate500
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val summary = previewSummary
            Button(
                onClick = { showConfirmDialog = true },
                enabled = summary != null && summary.totalImportableCount > 0,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LimePrimary, contentColor = InkText),
                modifier = Modifier.testTag("confirm_excel_import_button")
            ) {
                Text(
                    if (summary != null) "Konfirmasi Import (${summary.totalImportableCount})" else "Konfirmasi Import",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )

    if (showConfirmDialog && previewSummary != null) {
        val s = previewSummary!!
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            containerColor = PureWhite,
            title = { Text("Konfirmasi Eksekusi Import", style = MaterialTheme.typography.titleLarge, color = InkText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Pastikan hasil preview sudah sesuai sebelum sistem membuat akun mahasiswa secara massal:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate600
                    )
                    Text("• Mahasiswa Normal siap diimpor: ${s.validNormalCount}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = Emerald700)
                    Text("• Mahasiswa AFK (Warna Merah): ${s.validAfkCount}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = Rose700)
                    Text("• Blok Hitam dilewati total: ${s.skippedBlackCount}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = InkText)
                    Text("• Duplikat / Tidak Valid / Sudah Ada: ${s.totalDuplicateOrInvalidAndExistingCount}", style = MaterialTheme.typography.bodySmall, color = Amber700)
                    HorizontalDivider(color = BorderLight)
                    Text(
                        text = if (importHistoricalAttendance && s.hasWeeklyAttendance) {
                            "Riwayat absensi Minggu 1–8 akan disimpan sebagai data historis (tanpa pemberian XP otomatis)."
                        } else {
                            "Hanya data & akun mahasiswa yang akan diimpor (riwayat absensi tidak diimpor)."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.commitExcelStudentImport(s, importHistoricalAttendance)
                        showConfirmDialog = false
                        onDismiss()
                    },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkCardBg, contentColor = LimePrimary),
                    modifier = Modifier.testTag("execute_excel_import_button")
                ) {
                    Text("Ya, Jalankan Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Kembali") }
            }
        )
    }
}

@Composable
private fun PreviewCountTile(
    label: String,
    count: Int,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = fg)
            Text(label, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.9f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewStudentRowCard(row: ExcelImportPreviewRow) {
    val (bg, border, badgeBg, badgeFg, icon) = when (row.validationStatus) {
        ExcelImportValidationStatus.VALID_NORMAL -> Quintuple(PureWhite, BorderMint, Emerald50, Emerald700, Icons.Rounded.CheckCircle)
        ExcelImportValidationStatus.VALID_AFK -> Quintuple(Rose50.copy(alpha = 0.45f), Rose600.copy(alpha = 0.45f), Rose600, PureWhite, Icons.Rounded.WarningAmber)
        ExcelImportValidationStatus.SKIPPED_BLACK -> Quintuple(DarkCardBg.copy(alpha = 0.08f), DarkCardBg.copy(alpha = 0.4f), DarkCardBg, PureWhite, Icons.Rounded.Block)
        ExcelImportValidationStatus.ALREADY_EXISTS -> Quintuple(SurfaceMuted, BorderLight, SurfaceMuted, Slate600, Icons.Rounded.Info)
        ExcelImportValidationStatus.INVALID_OR_DUPLICATE -> Quintuple(Amber50.copy(alpha = 0.6f), Amber700.copy(alpha = 0.4f), Amber50, Amber700, Icons.Rounded.ErrorOutline)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = bg,
        border = BorderStroke(1.dp, border)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(icon, contentDescription = null, tint = badgeFg, modifier = Modifier.size(16.dp))
                    Text(
                        text = "${row.sheetName} • Baris ${row.rowNumber}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Slate600
                    )
                }
                AzrilStatusBadge(row.validationStatus.label, badgeBg, badgeFg)
            }
            Text(
                text = row.fullName.ifBlank { "(Nama Kosong)" },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = if (row.validationStatus == ExcelImportValidationStatus.VALID_AFK) Rose700 else InkText
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AzrilStatusBadge("NIM: ${row.nim.ifBlank { "(Kosong)" }}", SoftMintBg, InkText)
                AzrilStatusBadge("Prodi: ${row.studyProgramCode}", LightGreenSecondary, LimeInk)
                AzrilStatusBadge("Kelas: ${row.className}", SurfaceMuted, Slate600)
            }
            if (row.issues.isNotEmpty()) {
                row.issues.forEach { issue ->
                    Text("• $issue", style = MaterialTheme.typography.labelSmall, color = if (row.validationStatus == ExcelImportValidationStatus.SKIPPED_BLACK) InkText else Rose700)
                }
            }
            if (row.weeklyAttendance.isNotEmpty() && row.isImportable) {
                val previewWeeks = row.weeklyAttendance.entries.sortedBy { it.key }.joinToString(" | ") { (w, st) -> "M$w:$st" }
                Text(
                    text = "Absensi: $previewWeeks",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )
            }
        }
    }
}

private data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
