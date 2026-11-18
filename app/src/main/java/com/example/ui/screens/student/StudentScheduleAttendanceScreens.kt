package com.example.ui.screens.student

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.VideoCall
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.example.ui.components.AzrilCard
import com.example.ui.components.AzrilEmptyState
import com.example.ui.components.AzrilPageHeader
import com.example.ui.components.AzrilStatusBadge
import com.example.ui.model.AttendanceSession
import com.example.ui.model.AttendanceSessionState
import com.example.ui.model.AttendanceStatus
import com.example.ui.model.LearningMethod
import com.example.ui.model.ToastType
import com.example.ui.navigation.StudentDestination
import com.example.ui.screens.admin.AcademicCalendarGridCard
import com.example.ui.state.AzrilUiState
import com.example.ui.state.AzrilViewModel
import com.example.ui.theme.BorderLight
import com.example.ui.theme.Emerald50
import com.example.ui.theme.Emerald600
import com.example.ui.theme.Emerald700
import com.example.ui.theme.Navy900
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RoyalBlue50
import com.example.ui.theme.RoyalBlue600
import com.example.ui.theme.RoyalBlue700
import androidx.compose.ui.draw.clip
import com.example.ui.components.AzrilDarkCard
import com.example.ui.theme.DarkCardElevated
import com.example.ui.theme.LimePrimary
import com.example.ui.theme.OnDarkMuted
import com.example.ui.theme.Sky50
import com.example.ui.theme.Sky700
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.SurfaceMuted

@Composable
fun StudentScheduleScreen(
    uiState: AzrilUiState,
    viewModel: AzrilViewModel,
    isExpanded: Boolean,
    onNavigate: (StudentDestination) -> Unit
) {
    var viewMode by rememberSaveable { mutableStateOf("LIST") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AzrilPageHeader(
            title = "Jadwal Saya",
            subtitle = "Informasi perkuliahan Daring (Meet/Zoom) dan Luring (Ruang Kelas & QR Absensi) sesuai kelas Anda."
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = viewMode == "LIST", onClick = { viewMode = "LIST" }, label = { Text("Daftar Mingguan") })
            FilterChip(selected = viewMode == "CALENDAR", onClick = { viewMode = "CALENDAR" }, label = { Text("Kalender") })
        }

        if (viewMode == "CALENDAR") {
            AcademicCalendarGridCard(scheduleCount = uiState.schedules.size)
        }

        if (uiState.schedules.isEmpty()) {
            AzrilEmptyState(
                title = "Belum Ada Jadwal Perkuliahan",
                description = "Jadwal kelas yang Anda ikuti akan tampil otomatis setelah Admin mempublikasikan jadwal perkuliahan.",
                icon = Icons.Rounded.CalendarMonth,
                showIllustration = true
            )
        } else {
            uiState.schedules.forEach { schedule ->
                AzrilCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AzrilStatusBadge(
                            text = schedule.method.label,
                            containerColor = if (schedule.method == LearningMethod.DARING) Sky50 else RoyalBlue50,
                            contentColor = if (schedule.method == LearningMethod.DARING) Sky700 else RoyalBlue700
                        )
                        AzrilStatusBadge(schedule.weekLabel, SurfaceMuted, Slate600)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(schedule.courseTitle, style = MaterialTheme.typography.titleMedium, color = Navy900)
                    Text("${schedule.className} • Dosen: ${schedule.lecturer}", style = MaterialTheme.typography.bodySmall, color = Slate600)
                    Text("${schedule.dateLabel} • ${schedule.startTime} - ${schedule.endTime} WIB", style = MaterialTheme.typography.bodySmall, color = Slate500)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (schedule.method == LearningMethod.DARING) Icons.Rounded.VideoCall else Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = RoyalBlue600,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (schedule.method == LearningMethod.DARING) {
                                schedule.meetingUrl ?: "Link pertemuan online akan tersedia sebelum kuliah"
                            } else {
                                schedule.roomLocation ?: "Ruang kelas luring"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = RoyalBlue700
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onNavigate(StudentDestination.ATTENDANCE) },
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(if (schedule.method == LearningMethod.DARING) "Buka Absensi Daring" else "Scan QR Absensi")
                        }
                        if (schedule.materialTitle != null) {
                            OutlinedButton(
                                onClick = { viewModel.showToast("Unduh materi '${schedule.materialTitle}' terhubung ke Storage pada Tahap 5.", ToastType.INFO) },
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text("Materi")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudentAttendanceScreen(
    uiState: AzrilUiState,
    viewModel: AzrilViewModel,
    isExpanded: Boolean
) {
    val user = uiState.currentUser ?: return
    val openSessions = uiState.attendanceSessions.filter { it.state == AttendanceSessionState.OPEN }
    val myRecords = uiState.attendanceRecords.filter { it.studentId == user.id }
    val attendanceRate = if (myRecords.isEmpty()) 0f else {
        myRecords.count { it.status == AttendanceStatus.HADIR }.toFloat() / myRecords.size.toFloat()
    }
    var qrScanSession by remember { mutableStateOf<AttendanceSession?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AzrilPageHeader(
            title = "Absensi Perkuliahan",
            subtitle = "Lakukan absensi daring dengan tombol Hadir atau scan QR Code untuk pertemuan luring sesuai batas waktu."
        )

        // Attendance Summary Bar (Signature Dark Card + Lime Green Progress)
        AzrilDarkCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Persentase Kehadiran Anda", style = MaterialTheme.typography.titleMedium, color = PureWhite)
                Text("${(attendanceRate * 100).toInt()}%", style = MaterialTheme.typography.headlineSmall, color = LimePrimary)
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { attendanceRate },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(999.dp)),
                color = LimePrimary,
                trackColor = DarkCardElevated
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Hadir: ${myRecords.count { it.status == AttendanceStatus.HADIR }} • Izin: ${myRecords.count { it.status == AttendanceStatus.IZIN }} • Sakit: ${myRecords.count { it.status == AttendanceStatus.SAKIT }} • Alfa: ${myRecords.count { it.status == AttendanceStatus.ALFA }}",
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkMuted
            )
        }

        // Active Sessions
        Text("Sesi Absensi Aktif", style = MaterialTheme.typography.titleMedium, color = Navy900)
        if (openSessions.isEmpty()) {
            AzrilEmptyState(
                title = "Tidak Ada Sesi Absensi Aktif",
                description = "Absensi hanya dapat dilakukan saat Admin membuka sesi dan masih berada dalam rentang waktu yang ditentukan.",
                icon = Icons.Rounded.FactCheck
            )
        } else {
            openSessions.forEach { session ->
                val alreadyChecked = myRecords.any { it.sessionId == session.id }
                AzrilCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AzrilStatusBadge("Sedang Dibuka", Emerald50, Emerald700)
                        AzrilStatusBadge(session.method.label, RoyalBlue50, RoyalBlue700)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(session.meetingTitle, style = MaterialTheme.typography.titleMedium, color = Navy900)
                    Text("${session.className} • ${session.dateLabel} (${session.openTime} - ${session.closeTime} WIB)", style = MaterialTheme.typography.bodySmall, color = Slate600)
                    Spacer(modifier = Modifier.height(12.dp))
                    if (alreadyChecked) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Emerald600, modifier = Modifier.size(18.dp))
                            Text("Kehadiran sudah tercatat (maks. 1x per sesi)", style = MaterialTheme.typography.labelMedium, color = Emerald700)
                        }
                    } else if (session.method == LearningMethod.DARING) {
                        Button(
                            onClick = { viewModel.recordStudentAttendance(session.id) },
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text("Catat Hadir (Daring)")
                        }
                    } else {
                        Button(
                            onClick = { qrScanSession = session },
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan QR Code (Luring)")
                        }
                    }
                }
            }
        }

        // History
        AzrilCard {
            Text("Riwayat Kehadiran", style = MaterialTheme.typography.titleMedium, color = Navy900)
            Spacer(modifier = Modifier.height(8.dp))
            if (myRecords.isEmpty()) {
                Text("Belum ada riwayat absensi tercatat.", style = MaterialTheme.typography.bodySmall, color = Slate500)
            } else {
                myRecords.forEach { rec ->
                    HorizontalDivider(color = BorderLight, modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rec.meetingTitle, style = MaterialTheme.typography.titleSmall, color = Navy900)
                            Text("${rec.dateLabel} • Tercatat: ${rec.recordedAt}", style = MaterialTheme.typography.bodySmall, color = Slate500)
                        }
                        AzrilStatusBadge(rec.status.label, Emerald50, Emerald700)
                    }
                }
            }
        }
    }

    qrScanSession?.let { session ->
        AlertDialog(
            onDismissRequest = { qrScanSession = null },
            containerColor = PureWhite,
            title = { Text("Verifikasi QR Absensi Luring", style = MaterialTheme.typography.titleLarge, color = Navy900) },
            text = {
                Text(
                    "Pemindaian kamera CameraX + verifikasi token QR terenkripsi di server akan diaktifkan pada Tahap 4. Untuk pengujian UI Tahap 1, Anda dapat menguji pencatatan kehadiran pada sesi '${session.meetingTitle}'.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate600
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.recordStudentAttendance(session.id)
                        qrScanSession = null
                    },
                    shape = RoundedCornerShape(999.dp)
                ) { Text("Simulasikan Verifikasi Hadir") }
            },
            dismissButton = { TextButton(onClick = { qrScanSession = null }) { Text("Tutup") } }
        )
    }
}
