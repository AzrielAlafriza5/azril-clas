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
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.components.AzrilCard
import com.example.ui.components.AzrilEmptyState
import com.example.ui.components.AzrilPageHeader
import com.example.ui.components.AzrilStatusBadge
import com.example.ui.model.ToastType
import com.example.ui.model.UserRole
import com.example.ui.state.AzrilUiState
import com.example.ui.state.AzrilViewModel
import com.example.ui.theme.BorderLight
import com.example.ui.theme.Navy900
import androidx.compose.material3.FilterChipDefaults
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.InkText
import com.example.ui.theme.LimePrimary
import com.example.ui.theme.RoyalBlue50
import com.example.ui.theme.RoyalBlue700
import com.example.ui.theme.SoftMintBg
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.SurfaceMuted

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminReportsScreen(
    uiState: AzrilUiState,
    viewModel: AzrilViewModel,
    isExpanded: Boolean
) {
    val reportTypes = listOf(
        "Rekap Mahasiswa",
        "Rekap Tugas",
        "Rekap Pengumpulan",
        "Rekap Keterlambatan",
        "Rekap Penilaian",
        "Rekap Absensi",
        "Kehadiran per Kelas",
        "Kehadiran per Mahasiswa"
    )
    var selectedReport by rememberSaveable { mutableStateOf(reportTypes.first()) }
    var periodFilter by rememberSaveable { mutableStateOf("Semester Ganjil 2026/2027") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AzrilPageHeader(
            title = "Laporan & Rekapitulasi",
            subtitle = "Hasilkan rekap akademik berdasarkan program studi, kelas, semester, dan periode tanggal.",
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.showToast("Ekspor CSV '$selectedReport' disiapkan penuh pada Tahap 7.", ToastType.INFO) },
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("CSV")
                    }
                    Button(
                        onClick = { viewModel.showToast("Ekspor PDF '$selectedReport' disiapkan penuh pada Tahap 7.", ToastType.INFO) },
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PDF")
                    }
                }
            }
        )

        AzrilCard {
            Text("Pilih Jenis Laporan", style = MaterialTheme.typography.titleSmall, color = Navy900)
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                reportTypes.forEach { report ->
                    FilterChip(
                        selected = selectedReport == report,
                        onClick = { selectedReport = report },
                        label = { Text(report) },
                        shape = RoundedCornerShape(999.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DarkCardBg,
                            selectedLabelColor = LimePrimary,
                            containerColor = SoftMintBg,
                            labelColor = InkText
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = periodFilter,
                onValueChange = { periodFilter = it },
                label = { Text("Periode / Semester") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        AzrilEmptyState(
            title = "$selectedReport — Belum Ada Data",
            description = "Ringkasan tabel dan grafik untuk $selectedReport pada periode $periodFilter akan terisi otomatis saat data akademik aktif di Tahap 2–7.",
            icon = Icons.Rounded.Analytics,
            showIllustration = true
        )
    }
}

@Composable
fun RoleNotificationsScreen(
    uiState: AzrilUiState,
    role: UserRole,
    viewModel: AzrilViewModel,
    isExpanded: Boolean
) {
    val items = uiState.notifications.filter { it.targetRole == role }
    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    val visible = if (unreadOnly) items.filterNot { it.isRead } else items

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AzrilPageHeader(
            title = "Notifikasi",
            subtitle = if (role == UserRole.ADMIN) {
                "Pemberitahuan pengumpulan baru, keterlambatan, status AI, dan sesi absensi."
            } else {
                "Pemberitahuan tugas baru, deadline mendekat, sesi absensi dibuka, hasil AI, dan nilai."
            },
            action = {
                if (items.any { !it.isRead }) {
                    OutlinedButton(
                        onClick = { viewModel.markAllNotificationsRead() },
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tandai Dibaca")
                    }
                }
            }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !unreadOnly, onClick = { unreadOnly = false }, label = { Text("Semua (${items.size})") })
            FilterChip(selected = unreadOnly, onClick = { unreadOnly = true }, label = { Text("Belum Dibaca (${items.count { !it.isRead }})") })
        }

        if (visible.isEmpty()) {
            AzrilEmptyState(
                title = "Belum Ada Notifikasi",
                description = "Aktivitas terbaru terkait absensi, pengumpulan tugas, pemeriksaan AI, dan penilaian akan muncul di sini.",
                icon = Icons.Rounded.NotificationsNone
            )
        } else {
            visible.forEach { notif ->
                AzrilCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AzrilStatusBadge(notif.category, RoyalBlue50, RoyalBlue700)
                                if (!notif.isRead) {
                                    AzrilStatusBadge("Baru", SurfaceMuted, Navy900)
                                }
                            }
                            Text(notif.title, style = MaterialTheme.typography.titleSmall, color = Navy900)
                            Text(notif.body, style = MaterialTheme.typography.bodySmall, color = Slate600)
                            Text(notif.timestamp, style = MaterialTheme.typography.labelSmall, color = Slate500)
                        }
                        if (!notif.isRead) {
                            TextButton(onClick = { viewModel.markNotificationRead(notif.id) }) {
                                Text("Dibaca")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminSettingsScreen(
    uiState: AzrilUiState,
    viewModel: AzrilViewModel,
    isExpanded: Boolean
) {
    var institution by rememberSaveable { mutableStateOf(uiState.settings.institutionName) }
    var academicYear by rememberSaveable { mutableStateOf(uiState.settings.activeAcademicYear) }
    var semester by rememberSaveable { mutableStateOf(uiState.settings.activeSemester) }
    var enableAiScore by rememberSaveable { mutableStateOf(uiState.settings.enableAiInitialScore) }
    var allowGlobalRanking by rememberSaveable { mutableStateOf(uiState.settings.allowGlobalStudentRanking) }
    var attendanceWindow by rememberSaveable { mutableStateOf(uiState.settings.defaultAttendanceWindowMinutes.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AzrilPageHeader(
            title = "Pengaturan Aplikasi",
            subtitle = "Konfigurasi periode akademik, kebijakan absensi, dan preferensi AI Assignment Assistant."
        )

        AzrilCard {
            Text("Konfigurasi Akademik", style = MaterialTheme.typography.titleMedium, color = Navy900)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = institution,
                onValueChange = { institution = it },
                label = { Text("Nama Institusi / Portal") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = academicYear,
                    onValueChange = { academicYear = it },
                    label = { Text("Tahun Akademik") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = semester,
                    onValueChange = { semester = it },
                    label = { Text("Semester Aktif") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = attendanceWindow,
                onValueChange = { attendanceWindow = it.filter(Char::isDigit) },
                label = { Text("Durasi Default Sesi Absensi (Menit)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BorderLight)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Aktifkan Skor Kesesuaian Awal AI", style = MaterialTheme.typography.titleSmall, color = Navy900)
                    Text("Skor bersifat rekomendasi awal, tidak mengubah nilai final otomatis.", style = MaterialTheme.typography.bodySmall, color = Slate500)
                }
                Switch(checked = enableAiScore, onCheckedChange = { enableAiScore = it })
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Izinkan Mahasiswa Melihat Ranking Global", style = MaterialTheme.typography.titleSmall, color = Navy900)
                    Text("Jika nonaktif, mahasiswa hanya melihat peringkat di kelas & program studi.", style = MaterialTheme.typography.bodySmall, color = Slate500)
                }
                Switch(checked = allowGlobalRanking, onCheckedChange = { allowGlobalRanking = it })
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.updateSettings(
                        uiState.settings.copy(
                            institutionName = institution.trim().ifBlank { "Azril Clas Academic Portal" },
                            activeAcademicYear = academicYear.trim().ifBlank { "2026/2027" },
                            activeSemester = semester.trim().ifBlank { "Ganjil" },
                            enableAiInitialScore = enableAiScore,
                            defaultAttendanceWindowMinutes = (attendanceWindow.toIntOrNull() ?: 45).coerceIn(5, 180),
                            allowGlobalStudentRanking = allowGlobalRanking
                        )
                    )
                },
                shape = RoundedCornerShape(999.dp)
            ) {
                Text("Simpan Pengaturan")
            }
        }

        AzrilCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Security, contentDescription = null, tint = RoyalBlue700)
                Text("Keamanan & Integrasi Backend", style = MaterialTheme.typography.titleMedium, color = Navy900)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Autentikasi, verifikasi role dari database, aturan akses (RLS / Security Rules), dan Gemini AI backend akan diaktifkan bertahap pada Tahap 2 sampai Tahap 6 tanpa pernah menaruh secret key di sisi klien.",
                style = MaterialTheme.typography.bodySmall,
                color = Slate600
            )
        }
    }
}
