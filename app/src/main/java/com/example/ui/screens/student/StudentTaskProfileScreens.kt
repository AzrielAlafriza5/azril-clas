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
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.AzrilCard
import com.example.ui.components.AzrilDarkCard
import com.example.ui.components.AzrilEmptyState
import com.example.ui.components.AzrilPageHeader
import com.example.ui.components.AzrilStatusBadge
import com.example.ui.model.Assignment
import com.example.ui.model.StudentBadgeStatus
import com.example.ui.model.SubmissionMethod
import com.example.ui.model.ToastType
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
fun StudentAssignmentsScreen(
    uiState: AzrilUiState,
    viewModel: AzrilViewModel,
    isExpanded: Boolean
) {
    val user = uiState.currentUser ?: return
    val myStudentRecord = uiState.students.find { it.id == user.id }
    val targetedAssignments = uiState.assignments.filter {
        AzrilViewModel.isAssignmentForStudent(it, user, myStudentRecord)
    }
    var submittingTarget by remember { mutableStateOf<Assignment?>(null) }
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
            title = "Daftar Tugas",
            subtitle = "Menampilkan penugasan yang ditujukan khusus untuk kelas atau program studi Anda lengkap dengan file tugas, panduan, dan status deadline."
        )

        if (targetedAssignments.isEmpty()) {
            AzrilEmptyState(
                title = "Belum Ada Tugas untuk Kelas / Prodi Anda",
                description = "Hanya penugasan yang ditujukan kepada kelas (${myStudentRecord?.className ?: user.className ?: "-"}) atau prodi (${myStudentRecord?.studyProgramName ?: user.studyProgramName ?: "-"}) Anda yang muncul di sini.",
                icon = Icons.AutoMirrored.Rounded.Assignment,
                showIllustration = true
            )
        } else {
            targetedAssignments.forEach { assignment ->
                val mySub = uiState.submissions.find { it.assignmentId == assignment.id && it.studentId == user.id }
                val isDeadlinePassed = assignment.isDeadlinePassed()

                AzrilCard {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AzrilStatusBadge(
                            text = mySub?.status?.label ?: "Belum Mengumpulkan",
                            containerColor = if (mySub != null) Emerald50 else Amber50,
                            contentColor = if (mySub != null) Emerald700 else Amber700
                        )
                        AzrilStatusBadge(
                            text = if (isDeadlinePassed) "Deadline Terlewat" else "Masih Dibuka",
                            containerColor = if (isDeadlinePassed) Rose50 else LightGreenSecondary,
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
                                "Batas Akhir: ${assignment.deadline} (Sudah Terlewati)"
                            } else {
                                "Batas Akhir: ${assignment.deadline}"
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
                            text = "Berkas Tugas (Klik untuk Buka / Unduh)",
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

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Format diizinkan: ${assignment.allowedFormats.joinToString(", ")} (maks ${assignment.maxFileSizeMb} MB) • Metode: ${assignment.submissionMethod.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate500
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { submittingTarget = assignment },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDeadlinePassed) DarkCardBg else LimePrimary,
                            contentColor = if (isDeadlinePassed) LimePrimary else InkText
                        )
                    ) {
                        Text(
                            text = when {
                                mySub != null -> "Perbarui Pengumpulan"
                                isDeadlinePassed -> "Kumpulkan (Terlambat)"
                                else -> "Kumpulkan Tugas"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    submittingTarget?.let { assignment ->
        SubmitAssignmentDialog(
            assignment = assignment,
            onDismiss = { submittingTarget = null },
            onSubmit = { driveUrl, fileName, fileSize ->
                val err = viewModel.submitAssignment(assignment.id, driveUrl, fileName, fileSize)
                if (err == null) submittingTarget = null
                err
            }
        )
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
    onDismiss: () -> Unit,
    onSubmit: (String?, String?, String?) -> String?
) {
    val context = LocalContext.current
    var driveUrl by rememberSaveable { mutableStateOf("") }
    var pickedFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var pickedFileSize by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    // Zero-permission Android Storage Access Framework Document Picker
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var name = "berkas_tugas"
            var sizeLabel = "Ukuran terverifikasi"
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
                            return@use
                        }
                    }
                }
            }
            pickedFileName = name
            pickedFileSize = sizeLabel
            error = null
        }
    }

    val allowDrive = assignment.submissionMethod != SubmissionMethod.DIRECT_UPLOAD
    val allowUpload = assignment.submissionMethod != SubmissionMethod.GOOGLE_DRIVE

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text("Kumpulkan Tugas", style = MaterialTheme.typography.titleLarge, color = Navy900) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(assignment.title, style = MaterialTheme.typography.titleSmall, color = Navy900)
                if (assignment.isDeadlinePassed()) {
                    AzrilStatusBadge("Perhatian: Deadline sudah terlewati (tercatat Terlambat)", Rose50, Rose700)
                }
                if (allowDrive) {
                    OutlinedTextField(
                        value = driveUrl,
                        onValueChange = { driveUrl = it; error = null },
                        label = { Text("Link Google Drive") },
                        placeholder = { Text("https://drive.google.com/file/d/...") },
                        leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (allowUpload) {
                    OutlinedButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(pickedFileName ?: "Pilih File dari Perangkat")
                    }
                    pickedFileName?.let {
                        Text("Terpilih: $it (${pickedFileSize ?: "-"})", style = MaterialTheme.typography.labelSmall, color = Emerald700)
                    }
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Rose600) }
            }
        },
        confirmButton = {
            Button(
                onClick = { error = onSubmit(driveUrl.ifBlank { null }, pickedFileName, pickedFileSize) },
                shape = RoundedCornerShape(999.dp)
            ) { Text("Kirim Tugas") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun StudentSubmissionsScreen(
    uiState: AzrilUiState,
    isExpanded: Boolean
) {
    val user = uiState.currentUser ?: return
    val mySubs = uiState.submissions.filter { it.studentId == user.id }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AzrilPageHeader(
            title = "Pengumpulan Saya",
            subtitle = "Riwayat tugas yang telah Anda kumpulkan, jumlah revisi, dan status pemeriksaan."
        )

        if (mySubs.isEmpty()) {
            AzrilEmptyState(
                title = "Belum Ada Riwayat Pengumpulan",
                description = "Semua berkas dan link Google Drive yang Anda kirimkan akan tersimpan riwayatnya di sini.",
                icon = Icons.Rounded.UploadFile,
                showIllustration = true
            )
        } else {
            mySubs.forEach { sub ->
                AzrilCard {
                    AzrilStatusBadge(sub.status.label, RoyalBlue50, RoyalBlue700)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(sub.assignmentTitle, style = MaterialTheme.typography.titleMedium, color = Navy900)
                    Text("Dikumpulkan: ${sub.submittedAt} • Revisi ke-${sub.revisionCount}", style = MaterialTheme.typography.bodySmall, color = Slate500)
                    sub.driveLink?.let { Text("Drive: $it", style = MaterialTheme.typography.labelMedium, color = RoyalBlue700) }
                    sub.uploadedFileName?.let { Text("File: $it", style = MaterialTheme.typography.labelMedium, color = Navy900) }
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
