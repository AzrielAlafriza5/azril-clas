package com.example.ui.screens.student

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import com.example.ui.components.AzrilCard
import com.example.ui.components.AzrilDarkCard
import com.example.ui.components.AzrilEmptyState
import com.example.ui.components.AzrilPageHeader
import com.example.ui.components.AzrilStatusBadge
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.DarkCardElevated
import com.example.ui.theme.LimePrimary
import com.example.ui.theme.OnDarkMuted
import com.example.ui.model.Assignment
import com.example.ui.model.SubmissionMethod
import com.example.ui.model.ToastType
import com.example.ui.state.AzrilUiState
import com.example.ui.state.AzrilViewModel
import com.example.ui.theme.Amber50
import com.example.ui.theme.Amber700
import com.example.ui.theme.BorderLight
import com.example.ui.theme.Emerald50
import com.example.ui.theme.Emerald700
import com.example.ui.theme.Navy900
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Rose600
import com.example.ui.theme.RoyalBlue50
import com.example.ui.theme.RoyalBlue700
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600

@Composable
fun StudentAssignmentsScreen(
    uiState: AzrilUiState,
    viewModel: AzrilViewModel,
    isExpanded: Boolean
) {
    val user = uiState.currentUser ?: return
    val activeAssignments = uiState.assignments.filter { it.isActive }
    var submittingTarget by remember { mutableStateOf<Assignment?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AzrilPageHeader(
            title = "Daftar Tugas",
            subtitle = "Baca instruksi pengerjaan, unduh lampiran materi, dan kumpulkan melalui Google Drive atau upload file."
        )

        if (activeAssignments.isEmpty()) {
            AzrilEmptyState(
                title = "Belum Ada Tugas Aktif",
                description = "Tugas dari kelas yang Anda ikuti akan muncul di sini lengkap dengan deadline dan format pengumpulan.",
                icon = Icons.AutoMirrored.Rounded.Assignment,
                showIllustration = true
            )
        } else {
            activeAssignments.forEach { assignment ->
                val mySub = uiState.submissions.find { it.assignmentId == assignment.id && it.studentId == user.id }
                AzrilCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AzrilStatusBadge(
                            text = mySub?.status?.label ?: "Belum Mengumpulkan",
                            containerColor = if (mySub != null) Emerald50 else Amber50,
                            contentColor = if (mySub != null) Emerald700 else Amber700
                        )
                        AzrilStatusBadge(assignment.submissionMethod.label, RoyalBlue50, RoyalBlue700)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(assignment.title, style = MaterialTheme.typography.titleMedium, color = Navy900)
                    Text("${assignment.className} • ${assignment.courseCategory}", style = MaterialTheme.typography.bodySmall, color = Slate600)
                    Text("Deadline: ${assignment.deadline}", style = MaterialTheme.typography.labelMedium, color = Amber700)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(assignment.instructions, style = MaterialTheme.typography.bodySmall, color = Slate600)
                    Text(
                        "Format diizinkan: ${assignment.allowedFormats.joinToString(", ")} (maks ${assignment.maxFileSizeMb} MB)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate500
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { submittingTarget = assignment },
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(if (mySub == null) "Kumpulkan Tugas" else "Perbarui Pengumpulan")
                        }
                        if (assignment.materialAttachment != null) {
                            OutlinedButton(
                                onClick = { viewModel.showToast("Unduh lampiran '${assignment.materialAttachment}' terhubung pada Tahap 5.", ToastType.INFO) },
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text("Unduh Materi")
                            }
                        }
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
    var showChangePassword by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AzrilPageHeader(
            title = "Profil Saya",
            subtitle = "Informasi identitas akademik dan keamanan akun mahasiswa."
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
                        text = user.fullName.take(2).uppercase(),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = DarkCardBg
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(user.fullName, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                    Text("NIM ${user.nim ?: "-"} • ${user.studyProgramName ?: "-"}", style = MaterialTheme.typography.bodySmall, color = OnDarkMuted)
                    Spacer(modifier = Modifier.height(2.dp))
                    AzrilStatusBadge(user.status.label, DarkCardElevated, LimePrimary)
                }
            }
        }

        AzrilCard {
            Text("Data Akademik", style = MaterialTheme.typography.titleMedium, color = Navy900)
            Spacer(modifier = Modifier.height(12.dp))
            ProfileFieldRow("Nama Lengkap", user.fullName)
            ProfileFieldRow("NIM", user.nim ?: "-")
            ProfileFieldRow("Username", user.username ?: "-")
            ProfileFieldRow("Email Akademik", user.email)
            ProfileFieldRow("Program Studi", user.studyProgramName ?: "-")
            ProfileFieldRow("Kelas", user.className ?: "-")
            ProfileFieldRow("Status Akun", user.status.label)
        }

        AzrilCard {
            Text("Keamanan Akun", style = MaterialTheme.typography.titleMedium, color = Navy900)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { showChangePassword = true },
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Icon(Icons.Rounded.LockReset, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ubah Kata Sandi")
                }
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(containerColor = Rose600, contentColor = PureWhite),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Keluar")
                }
            }
        }
    }

    if (showChangePassword) {
        var currentPw by rememberSaveable { mutableStateOf("") }
        var newPw by rememberSaveable { mutableStateOf("") }
        var confirmPw by rememberSaveable { mutableStateOf("") }
        var error by rememberSaveable { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showChangePassword = false },
            containerColor = PureWhite,
            title = { Text("Ubah Kata Sandi", style = MaterialTheme.typography.titleLarge, color = Navy900) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = currentPw, onValueChange = { currentPw = it; error = null }, label = { Text("Kata Sandi Saat Ini") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = newPw, onValueChange = { newPw = it; error = null }, label = { Text("Kata Sandi Baru (min. 8 karakter)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = confirmPw, onValueChange = { confirmPw = it; error = null }, label = { Text("Konfirmasi Kata Sandi Baru") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Rose600) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            currentPw.isBlank() -> error = "Kata sandi saat ini wajib diisi."
                            newPw.length < 8 -> error = "Kata sandi baru minimal 8 karakter."
                            newPw != confirmPw -> error = "Konfirmasi kata sandi tidak cocok."
                            else -> {
                                showChangePassword = false
                                viewModel.showToast("Validasi kata sandi berhasil. Pembaruan kredensial terhubung ke Auth di Tahap 2.", ToastType.INFO)
                            }
                        }
                    },
                    shape = RoundedCornerShape(999.dp)
                ) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showChangePassword = false }) { Text("Batal") } }
        )
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
