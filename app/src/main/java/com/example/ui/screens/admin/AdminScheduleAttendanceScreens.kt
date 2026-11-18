package com.example.ui.screens.admin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.VideoCall
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.components.AzrilCard
import com.example.ui.components.AzrilEmptyState
import com.example.ui.components.AzrilPageHeader
import com.example.ui.components.AzrilStatusBadge
import com.example.ui.model.AcademicClass
import com.example.ui.model.AttendanceRecord
import com.example.ui.model.AttendanceSession
import com.example.ui.model.AttendanceSessionState
import com.example.ui.model.AttendanceStatus
import com.example.ui.model.LearningMethod
import com.example.ui.state.AzrilUiState
import com.example.ui.state.AzrilViewModel
import com.example.ui.theme.Amber50
import com.example.ui.theme.Amber700
import com.example.ui.theme.BorderLight
import com.example.ui.theme.Emerald50
import com.example.ui.theme.Emerald700
import com.example.ui.theme.Navy900
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Rose50
import com.example.ui.theme.Rose600
import com.example.ui.theme.Rose700
import com.example.ui.theme.RoyalBlue50
import com.example.ui.theme.RoyalBlue600
import com.example.ui.theme.RoyalBlue700
import com.example.ui.theme.Sky50
import com.example.ui.theme.Sky700
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.SurfaceMuted

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminSchedulesScreen(
    uiState: AzrilUiState,
    viewModel: AzrilViewModel,
    isExpanded: Boolean
) {
    var viewMode by rememberSaveable { mutableStateOf("LIST") }
    var methodFilter by rememberSaveable { mutableStateOf("ALL") }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    val filtered = uiState.schedules.filter {
        when (methodFilter) {
            "DARING" -> it.method == LearningMethod.DARING
            "LURING" -> it.method == LearningMethod.LURING
            else -> true
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
            title = "Jadwal Perkuliahan",
            subtitle = "Atur jadwal mingguan Daring (Meet/Zoom) dan Luring (Ruang Kelas & QR Absensi) secara fleksibel.",
            action = {
                Button(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.testTag("add_schedule_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Buat Jadwal")
                }
            }
        )

        AzrilCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = methodFilter == "ALL", onClick = { methodFilter = "ALL" }, label = { Text("Semua") })
                    FilterChip(selected = methodFilter == "DARING", onClick = { methodFilter = "DARING" }, label = { Text("Daring") })
                    FilterChip(selected = methodFilter == "LURING", onClick = { methodFilter = "LURING" }, label = { Text("Luring") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = viewMode == "LIST", onClick = { viewMode = "LIST" }, label = { Text("Daftar") })
                    FilterChip(selected = viewMode == "CALENDAR", onClick = { viewMode = "CALENDAR" }, label = { Text("Kalender") })
                }
            }
        }

        if (viewMode == "CALENDAR") {
            AcademicCalendarGridCard(scheduleCount = filtered.size)
        }

        if (filtered.isEmpty()) {
            AzrilEmptyState(
                title = "Belum Ada Jadwal Perkuliahan",
                description = "Jadwal dapat dibuat berbeda setiap minggu (misal: Minggu 1 Daring, Minggu 2 Luring). Tambahkan kelas terlebih dahulu lalu buat jadwal.",
                icon = Icons.Rounded.CalendarMonth,
                showIllustration = true,
                actionLabel = "Buat Jadwal",
                onAction = { showAddDialog = true }
            )
        } else {
            filtered.forEach { schedule ->
                AzrilCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AzrilStatusBadge(
                                    text = schedule.method.label,
                                    containerColor = if (schedule.method == LearningMethod.DARING) Sky50 else RoyalBlue50,
                                    contentColor = if (schedule.method == LearningMethod.DARING) Sky700 else RoyalBlue700
                                )
                                AzrilStatusBadge(schedule.weekLabel, SurfaceMuted, Slate600)
                            }
                            Text(schedule.courseTitle, style = MaterialTheme.typography.titleMedium, color = Navy900)
                            Text("${schedule.className} • Dosen: ${schedule.lecturer}", style = MaterialTheme.typography.bodySmall, color = Slate600)
                            Text("${schedule.dateLabel} • ${schedule.startTime} - ${schedule.endTime} WIB", style = MaterialTheme.typography.bodySmall, color = Slate500)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (schedule.method == LearningMethod.DARING) Icons.Rounded.VideoCall else Icons.Rounded.LocationOn,
                                    contentDescription = null,
                                    tint = RoyalBlue600,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (schedule.method == LearningMethod.DARING) {
                                        schedule.meetingUrl ?: "Link meeting belum dicantumkan"
                                    } else {
                                        schedule.roomLocation ?: "Lokasi ruang kelas"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = RoyalBlue700
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.deleteSchedule(schedule.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Hapus jadwal", tint = Rose600)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddScheduleDialog(
            classes = uiState.classes,
            onDismiss = { showAddDialog = false },
            onSave = { classId, title, week, date, start, end, lecturer, method, url, room, material, notes ->
                val err = viewModel.addSchedule(classId, title, week, date, start, end, lecturer, method, url, room, material, notes)
                if (err == null) showAddDialog = false
                err
            }
        )
    }
}

@Composable
fun AcademicCalendarGridCard(scheduleCount: Int) {
    val days = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
    AzrilCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Kalender Perkuliahan • November 2026", style = MaterialTheme.typography.titleMedium, color = Navy900)
            AzrilStatusBadge("$scheduleCount Pertemuan", RoyalBlue50, RoyalBlue700)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            days.forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Slate500,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val dates = (1..30).toList()
        dates.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                week.forEach { dayNum ->
                    val isToday = dayNum == 18
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isToday) RoyalBlue600 else SurfaceMuted.copy(alpha = 0.55f))
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$dayNum",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isToday) PureWhite else Navy900
                        )
                    }
                }
                repeat(7 - week.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddScheduleDialog(
    classes: List<AcademicClass>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String, LearningMethod, String, String, String, String) -> String?
) {
    var classId by rememberSaveable { mutableStateOf(classes.firstOrNull()?.id ?: "") }
    var courseTitle by rememberSaveable { mutableStateOf("") }
    var weekLabel by rememberSaveable { mutableStateOf("Minggu 1") }
    var dateLabel by rememberSaveable { mutableStateOf("23 November 2026") }
    var startTime by rememberSaveable { mutableStateOf("08:00") }
    var endTime by rememberSaveable { mutableStateOf("09:40") }
    var lecturer by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf(LearningMethod.DARING) }
    var meetingUrl by rememberSaveable { mutableStateOf("") }
    var roomLocation by rememberSaveable { mutableStateOf("") }
    var materialTitle by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text("Buat Jadwal Perkuliahan", style = MaterialTheme.typography.titleLarge, color = Navy900) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (classes.isEmpty()) {
                    Text("Buat Kelas terlebih dahulu sebelum menambahkan jadwal.", style = MaterialTheme.typography.bodySmall, color = Rose700)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        classes.forEach { cls ->
                            FilterChip(selected = classId == cls.id, onClick = { classId = cls.id }, label = { Text(cls.name) })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = method == LearningMethod.DARING,
                        onClick = { method = LearningMethod.DARING },
                        label = { Text("Daring") }
                    )
                    FilterChip(
                        selected = method == LearningMethod.LURING,
                        onClick = { method = LearningMethod.LURING },
                        label = { Text("Luring") }
                    )
                }
                OutlinedTextField(value = courseTitle, onValueChange = { courseTitle = it; error = null }, label = { Text("Mata Kuliah / Pertemuan") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = weekLabel, onValueChange = { weekLabel = it }, label = { Text("Minggu") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = dateLabel, onValueChange = { dateLabel = it }, label = { Text("Tanggal") }, singleLine = true, modifier = Modifier.weight(1.4f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = startTime, onValueChange = { startTime = it }, label = { Text("Mulai") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = endTime, onValueChange = { endTime = it }, label = { Text("Selesai") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = lecturer, onValueChange = { lecturer = it; error = null }, label = { Text("Dosen / Pengajar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (method == LearningMethod.DARING) {
                    OutlinedTextField(value = meetingUrl, onValueChange = { meetingUrl = it; error = null }, label = { Text("Link Google Meet / Zoom") }, placeholder = { Text("https://meet.google.com/...") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(value = roomLocation, onValueChange = { roomLocation = it; error = null }, label = { Text("Lokasi Ruang Kelas") }, placeholder = { Text("Gedung B - Ruang 304") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(value = materialTitle, onValueChange = { materialTitle = it }, label = { Text("Materi Perkuliahan (Opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Catatan Tambahan") }, maxLines = 2, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Rose600) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    error = onSave(classId, courseTitle, weekLabel, dateLabel, startTime, endTime, lecturer, method, meetingUrl, roomLocation, materialTitle, notes)
                },
                enabled = classes.isNotEmpty(),
                shape = RoundedCornerShape(999.dp)
            ) { Text("Simpan Jadwal") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminAttendanceScreen(
    uiState: AzrilUiState,
    viewModel: AzrilViewModel,
    isExpanded: Boolean
) {
    var showOpenDialog by rememberSaveable { mutableStateOf(false) }
    var qrPreviewSession by remember { mutableStateOf<AttendanceSession?>(null) }
    var correctingRecord by remember { mutableStateOf<AttendanceRecord?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AzrilPageHeader(
            title = "Sistem Absensi Berbatas Waktu",
            subtitle = "Buka sesi daring atau luring dengan batas waktu ketat, token QR dinamis, dan audit koreksi kehadiran.",
            action = {
                Button(
                    onClick = { showOpenDialog = true },
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.testTag("open_attendance_session_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Buka Sesi")
                }
            }
        )

        if (uiState.attendanceSessions.isEmpty()) {
            AzrilEmptyState(
                title = "Belum Ada Sesi Absensi",
                description = "Buat sesi absensi untuk pertemuan daring (tombol Hadir) atau luring (QR Code unik berbatas waktu). Validasi waktu server akan dihubungkan pada Tahap 4.",
                icon = Icons.Rounded.FactCheck,
                showIllustration = true,
                actionLabel = "Buka Sesi Absensi",
                onAction = { showOpenDialog = true }
            )
        } else {
            Text("Daftar Sesi Absensi", style = MaterialTheme.typography.titleMedium, color = Navy900)
            uiState.attendanceSessions.forEach { session ->
                AzrilCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AzrilStatusBadge(
                                    text = session.state.label,
                                    containerColor = if (session.state == AttendanceSessionState.OPEN) Emerald50 else SurfaceMuted,
                                    contentColor = if (session.state == AttendanceSessionState.OPEN) Emerald700 else Slate500
                                )
                                AzrilStatusBadge(
                                    text = session.method.label,
                                    containerColor = if (session.method == LearningMethod.DARING) Sky50 else RoyalBlue50,
                                    contentColor = if (session.method == LearningMethod.DARING) Sky700 else RoyalBlue700
                                )
                            }
                            Text(session.meetingTitle, style = MaterialTheme.typography.titleMedium, color = Navy900)
                            Text("${session.className} • ${session.dateLabel}", style = MaterialTheme.typography.bodySmall, color = Slate600)
                            Text("Waktu absensi: ${session.openTime} - ${session.closeTime} WIB", style = MaterialTheme.typography.labelMedium, color = RoyalBlue700)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (session.method == LearningMethod.LURING && session.state == AttendanceSessionState.OPEN) {
                            OutlinedButton(
                                onClick = { qrPreviewSession = session },
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tampilkan QR")
                            }
                        }
                        if (session.state == AttendanceSessionState.OPEN) {
                            OutlinedButton(
                                onClick = { viewModel.closeAttendanceSession(session.id) },
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text("Tutup Sesi", color = Rose600)
                            }
                        }
                    }
                }
            }
        }

        // Attendance Records & Audit Correction
        AzrilCard {
            Text("Rekap & Koreksi Kehadiran", style = MaterialTheme.typography.titleMedium, color = Navy900)
            Spacer(modifier = Modifier.height(6.dp))
            if (uiState.attendanceRecords.isEmpty()) {
                Text(
                    "Belum ada catatan kehadiran mahasiswa. Setiap perubahan status oleh Admin wajib menyertakan alasan untuk riwayat audit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500
                )
            } else {
                uiState.attendanceRecords.forEach { rec ->
                    HorizontalDivider(color = BorderLight, modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${rec.studentName} (${rec.studentNim})", style = MaterialTheme.typography.titleSmall, color = Navy900)
                            Text("${rec.meetingTitle} • ${rec.recordedAt}", style = MaterialTheme.typography.bodySmall, color = Slate500)
                            rec.correctionReason?.let {
                                Text("Alasan koreksi: $it", style = MaterialTheme.typography.labelSmall, color = Amber700)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AzrilStatusBadge(rec.status.label, Emerald50, Emerald700)
                            TextButton(onClick = { correctingRecord = rec }) { Text("Koreksi") }
                        }
                    }
                }
            }
        }
    }

    if (showOpenDialog) {
        OpenAttendanceSessionDialog(
            classes = uiState.classes,
            onDismiss = { showOpenDialog = false },
            onSave = { classId, title, method, date, openTime, closeTime, notes ->
                val err = viewModel.openAttendanceSession(classId, title, method, date, openTime, closeTime, notes)
                if (err == null) showOpenDialog = false
                err
            }
        )
    }

    qrPreviewSession?.let { session ->
        DynamicQrSessionDialog(session = session, onDismiss = { qrPreviewSession = null })
    }

    correctingRecord?.let { rec ->
        AttendanceCorrectionDialog(
            record = rec,
            onDismiss = { correctingRecord = null },
            onSave = { status, reason ->
                val err = viewModel.correctAttendance(rec.id, status, reason)
                if (err == null) correctingRecord = null
                err
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpenAttendanceSessionDialog(
    classes: List<AcademicClass>,
    onDismiss: () -> Unit,
    onSave: (String, String, LearningMethod, String, String, String, String) -> String?
) {
    var classId by rememberSaveable { mutableStateOf(classes.firstOrNull()?.id ?: "") }
    var meetingTitle by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf(LearningMethod.DARING) }
    var dateLabel by rememberSaveable { mutableStateOf("23 November 2026") }
    var openTime by rememberSaveable { mutableStateOf("07:45") }
    var closeTime by rememberSaveable { mutableStateOf("08:30") }
    var notes by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text("Buka Sesi Absensi", style = MaterialTheme.typography.titleLarge, color = Navy900) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (classes.isEmpty()) {
                    Text("Buat Kelas terlebih dahulu sebelum membuka sesi absensi.", style = MaterialTheme.typography.bodySmall, color = Rose700)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        classes.forEach { cls ->
                            FilterChip(selected = classId == cls.id, onClick = { classId = cls.id }, label = { Text(cls.name) })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = method == LearningMethod.DARING, onClick = { method = LearningMethod.DARING }, label = { Text("Daring") })
                    FilterChip(selected = method == LearningMethod.LURING, onClick = { method = LearningMethod.LURING }, label = { Text("Luring (QR)") })
                }
                OutlinedTextField(value = meetingTitle, onValueChange = { meetingTitle = it; error = null }, label = { Text("Pertemuan / Mata Kuliah") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = dateLabel, onValueChange = { dateLabel = it }, label = { Text("Tanggal") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = openTime, onValueChange = { openTime = it }, label = { Text("Dibuka (WIB)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = closeTime, onValueChange = { closeTime = it }, label = { Text("Ditutup (WIB)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Keterangan") }, maxLines = 2, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Rose600) }
            }
        },
        confirmButton = {
            Button(
                onClick = { error = onSave(classId, meetingTitle, method, dateLabel, openTime, closeTime, notes) },
                enabled = classes.isNotEmpty(),
                shape = RoundedCornerShape(999.dp)
            ) { Text("Buka Sesi") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun DynamicQrSessionDialog(
    session: AttendanceSession,
    onDismiss: () -> Unit
) {
    val token = session.qrTokenPreview ?: "TOKEN-EXPIRED"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text("QR Code Absensi Luring", style = MaterialTheme.typography.titleLarge, color = Navy900) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("${session.meetingTitle} • ${session.className}", style = MaterialTheme.typography.titleSmall, color = Navy900)
                // Visual QR Pattern Preview derived deterministically from session token
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = PureWhite,
                    shadowElevation = 2.dp
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(180.dp)
                            .padding(14.dp)
                            .aspectRatio(1f)
                    ) {
                        val grid = 11
                        val cell = size.width / grid
                        val seed = token.hashCode()
                        for (r in 0 until grid) {
                            for (c in 0 until grid) {
                                val isFinder = (r < 3 && c < 3) || (r < 3 && c >= grid - 3) || (r >= grid - 3 && c < 3)
                                val bit = ((seed shr ((r * 3 + c) % 24)) and 1) == 1
                                if (isFinder || (bit && (r + c) % 2 == 0)) {
                                    drawRect(
                                        color = Navy900,
                                        topLeft = Offset(c * cell, r * cell),
                                        size = Size(cell - 1.5f, cell - 1.5f)
                                    )
                                }
                            }
                        }
                    }
                }
                AzrilStatusBadge("Token Dinamis: $token", RoyalBlue50, RoyalBlue700)
                Text(
                    "Berlaku ${session.openTime} - ${session.closeTime} WIB. Token QR terenkripsi & validasi waktu server diaktifkan pada Tahap 4.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(999.dp)) { Text("Tutup") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttendanceCorrectionDialog(
    record: AttendanceRecord,
    onDismiss: () -> Unit,
    onSave: (AttendanceStatus, String) -> String?
) {
    var status by rememberSaveable { mutableStateOf(record.status) }
    var reason by rememberSaveable { mutableStateOf(record.correctionReason ?: "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text("Koreksi Kehadiran", style = MaterialTheme.typography.titleLarge, color = Navy900) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${record.studentName} • ${record.meetingTitle}", style = MaterialTheme.typography.bodyMedium, color = Slate600)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AttendanceStatus.entries.forEach { st ->
                        FilterChip(selected = status == st, onClick = { status = st }, label = { Text(st.label) })
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it; error = null },
                    label = { Text("Alasan Koreksi (Wajib untuk Audit)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Rose600) }
            }
        },
        confirmButton = {
            Button(onClick = { error = onSave(status, reason) }, shape = RoundedCornerShape(999.dp)) {
                Text("Simpan Koreksi")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
