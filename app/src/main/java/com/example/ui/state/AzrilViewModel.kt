package com.example.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthResult
import com.example.data.auth.AzrilAuthRepository
import com.example.data.auth.DataStoreAuthStorage
import com.example.data.auth.OfficialStudyPrograms
import com.example.ui.model.AcademicClass
import com.example.ui.model.AccountStatus
import com.example.ui.model.AppNotification
import com.example.ui.model.AppSettingsState
import com.example.ui.model.Assignment
import com.example.ui.model.AttendanceRecord
import com.example.ui.model.AttendanceSession
import com.example.ui.model.AttendanceSessionState
import com.example.ui.model.AttendanceStatus
import com.example.ui.model.ClassSchedule
import com.example.ui.model.CsvImportPreviewRow
import com.example.ui.model.GradeAuditEntry
import com.example.ui.model.LearningMethod
import com.example.ui.model.PointSourceType
import com.example.ui.model.RankingPeriodFilter
import com.example.ui.model.RankingPointAuditLog
import com.example.ui.model.RankingPointTransaction
import com.example.ui.model.ScreenLoadState
import com.example.ui.model.StudentRankingEntry
import com.example.ui.model.StudentRecord
import com.example.ui.model.StudyProgram
import com.example.ui.model.SubmissionMethod
import com.example.ui.model.SubmissionRecord
import com.example.ui.model.SubmissionStatus
import com.example.ui.model.ToastMessage
import com.example.ui.model.ToastType
import com.example.ui.model.UserProfile
import com.example.ui.model.UserRole
import com.example.ui.navigation.AdminDestination
import com.example.ui.navigation.StudentDestination
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AzrilUiState(
    val currentUser: UserProfile? = null,
    val isRestoringSession: Boolean = true,
    val authInProgress: Boolean = false,
    val authErrorMessage: String? = null,
    val rememberedIdentifier: String = "",
    val rememberMeEnabled: Boolean = true,
    val isSupabaseConfigured: Boolean = false,
    val adminDestination: AdminDestination = AdminDestination.DASHBOARD,
    val studentDestination: StudentDestination = StudentDestination.DASHBOARD,
    val loadState: ScreenLoadState = ScreenLoadState.Ready,
    val toastMessage: ToastMessage? = null,
    // Official study programs seeded strictly with HES, PJKR, FARMASI
    val studyPrograms: List<StudyProgram> = OfficialStudyPrograms.list,
    val classes: List<AcademicClass> = emptyList(),
    val students: List<StudentRecord> = emptyList(),
    val schedules: List<ClassSchedule> = emptyList(),
    val attendanceSessions: List<AttendanceSession> = emptyList(),
    val attendanceRecords: List<AttendanceRecord> = emptyList(),
    val assignments: List<Assignment> = emptyList(),
    val submissions: List<SubmissionRecord> = emptyList(),
    val rankingPoints: List<RankingPointTransaction> = emptyList(),
    val rankingAuditLogs: List<RankingPointAuditLog> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val settings: AppSettingsState = AppSettingsState()
) {
    val unreadNotificationCount: Int
        get() {
            val role = currentUser?.role ?: return 0
            return notifications.count { it.targetRole == role && !it.isRead }
        }
}

class AzrilViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AzrilAuthRepository(DataStoreAuthStorage(application))

    private val _uiState = MutableStateFlow(
        AzrilUiState(isSupabaseConfigured = authRepository.isSupabaseConfigured())
    )
    val uiState: StateFlow<AzrilUiState> = _uiState.asStateFlow()

    init {
        restoreSavedSession()
    }

    private fun restoreSavedSession() {
        viewModelScope.launch {
            val restored = authRepository.restoreSession()
            val mappedStudents = restored.registeredStudents.map { it.toStudentRecord() }
            _uiState.update { state ->
                state.copy(
                    currentUser = restored.profile,
                    isRestoringSession = false,
                    rememberedIdentifier = restored.rememberedIdentifier,
                    rememberMeEnabled = restored.rememberMeEnabled,
                    isSupabaseConfigured = restored.isSupabaseConfigured,
                    students = mergeRegisteredStudents(state.students, mappedStudents)
                )
            }
        }
    }

    private fun UserProfile.toStudentRecord(): StudentRecord = StudentRecord(
        id = id,
        fullName = fullName,
        nim = nim ?: "-",
        email = email,
        studyProgramId = studyProgramId ?: OfficialStudyPrograms.findByCode(studyProgramName ?: "")?.id ?: "prodi-hes",
        studyProgramName = studyProgramName ?: "HES",
        classId = null,
        className = className,
        semester = semester ?: 1,
        cohortYear = cohortYear ?: 2026,
        status = status
    )

    private fun mergeRegisteredStudents(
        existing: List<StudentRecord>,
        incoming: List<StudentRecord>
    ): List<StudentRecord> {
        val byId = existing.associateBy { it.id }.toMutableMap()
        incoming.forEach { stu ->
            if (!byId.containsKey(stu.id)) {
                byId[stu.id] = stu
            }
        }
        return byId.values.toList()
    }

    fun clearAuthError() {
        _uiState.update { it.copy(authErrorMessage = null) }
    }

    fun registerStudentAccount(
        fullName: String,
        nim: String,
        emailOrUsername: String,
        password: String,
        confirmPassword: String,
        studyProgramCode: String,
        rememberMe: Boolean
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(authInProgress = true, authErrorMessage = null) }
            when (
                val result = authRepository.registerStudent(
                    fullName = fullName,
                    nim = nim,
                    emailOrUsername = emailOrUsername,
                    password = password,
                    confirmPassword = confirmPassword,
                    studyProgramCode = studyProgramCode,
                    rememberMe = rememberMe
                )
            ) {
                is AuthResult.Success -> {
                    val registered = authRepository.getRegisteredStudents().map { it.toStudentRecord() }
                    _uiState.update { state ->
                        state.copy(
                            authInProgress = false,
                            authErrorMessage = null,
                            currentUser = result.profile,
                            studentDestination = StudentDestination.DASHBOARD,
                            rememberMeEnabled = rememberMe,
                            rememberedIdentifier = if (rememberMe) emailOrUsername.trim() else "",
                            students = mergeRegisteredStudents(state.students, listOf(result.profile.toStudentRecord()) + registered),
                            toastMessage = ToastMessage(
                                message = "Pendaftaran berhasil! Selamat datang, ${result.profile.fullName} (${result.profile.studyProgramName}).",
                                type = ToastType.SUCCESS
                            )
                        )
                    }
                }
                is AuthResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            authInProgress = false,
                            authErrorMessage = result.message,
                            toastMessage = ToastMessage(result.message, ToastType.ERROR)
                        )
                    }
                }
            }
        }
    }

    fun loginWithCredentials(
        identifier: String,
        password: String,
        rememberMe: Boolean
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(authInProgress = true, authErrorMessage = null) }
            when (val result = authRepository.login(identifier, password, rememberMe)) {
                is AuthResult.Success -> {
                    _uiState.update { state ->
                        val updatedStudents = if (result.profile.role == UserRole.STUDENT) {
                            mergeRegisteredStudents(state.students, listOf(result.profile.toStudentRecord()))
                        } else state.students
                        state.copy(
                            authInProgress = false,
                            authErrorMessage = null,
                            currentUser = result.profile,
                            adminDestination = AdminDestination.DASHBOARD,
                            studentDestination = StudentDestination.DASHBOARD,
                            rememberMeEnabled = rememberMe,
                            rememberedIdentifier = if (rememberMe) identifier.trim() else "",
                            students = updatedStudents,
                            toastMessage = ToastMessage(
                                message = "Selamat datang kembali, ${result.profile.fullName}!",
                                type = ToastType.SUCCESS
                            )
                        )
                    }
                }
                is AuthResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            authInProgress = false,
                            authErrorMessage = result.message,
                            toastMessage = ToastMessage(result.message, ToastType.ERROR)
                        )
                    }
                }
            }
        }
    }

    private fun currentTimeLabel(): String {
        val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm 'WIB'", Locale.forLanguageTag("id-ID"))
        return formatter.format(Date())
    }

    fun showToast(message: String, type: ToastType = ToastType.INFO) {
        _uiState.update { it.copy(toastMessage = ToastMessage(message = message, type = type)) }
    }

    fun dismissToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun enterAdminPreview() {
        _uiState.update {
            it.copy(
                currentUser = UserProfile(
                    id = "preview-admin",
                    fullName = "Administrator Akademik",
                    email = "admin@azrilclas.ac.id",
                    role = UserRole.ADMIN,
                    status = AccountStatus.ACTIVE
                ),
                adminDestination = AdminDestination.DASHBOARD,
                toastMessage = ToastMessage(
                    message = "Masuk ke Panel Admin.",
                    type = ToastType.INFO
                )
            )
        }
    }

    fun enterStudentPreview() {
        val activeStudent = _uiState.value.students.firstOrNull()
        _uiState.update {
            it.copy(
                currentUser = UserProfile(
                    id = activeStudent?.id ?: "preview-student",
                    fullName = activeStudent?.fullName ?: "Mahasiswa (Pratinjau)",
                    email = activeStudent?.email ?: "mahasiswa@azrilclas.ac.id",
                    role = UserRole.STUDENT,
                    nim = activeStudent?.nim ?: "Belum terdaftar",
                    studyProgramId = activeStudent?.studyProgramId,
                    studyProgramName = activeStudent?.studyProgramName ?: "HES",
                    className = activeStudent?.className ?: "Belum terdaftar di kelas",
                    semester = activeStudent?.semester,
                    cohortYear = activeStudent?.cohortYear,
                    status = AccountStatus.ACTIVE
                ),
                studentDestination = StudentDestination.DASHBOARD,
                toastMessage = ToastMessage(
                    message = "Masuk ke Panel Mahasiswa.",
                    type = ToastType.INFO
                )
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            val restored = authRepository.restoreSession()
            _uiState.update {
                it.copy(
                    currentUser = null,
                    authErrorMessage = null,
                    rememberedIdentifier = restored.rememberedIdentifier,
                    rememberMeEnabled = restored.rememberMeEnabled,
                    adminDestination = AdminDestination.DASHBOARD,
                    studentDestination = StudentDestination.DASHBOARD,
                    toastMessage = ToastMessage(
                        message = "Anda telah keluar dari akun. Sesi login berakhir.",
                        type = ToastType.SUCCESS
                    )
                )
            }
        }
    }

    fun navigateAdmin(destination: AdminDestination) {
        _uiState.update { it.copy(adminDestination = destination, loadState = ScreenLoadState.Ready) }
    }

    fun navigateStudent(destination: StudentDestination) {
        _uiState.update { it.copy(studentDestination = destination, loadState = ScreenLoadState.Ready) }
    }

    fun triggerRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loadState = ScreenLoadState.Loading) }
            delay(420)
            _uiState.update {
                it.copy(
                    loadState = ScreenLoadState.Ready,
                    toastMessage = ToastMessage(
                        message = "Tampilan diperbarui (Pratinjau lokal Tahap 1).",
                        type = ToastType.INFO
                    )
                )
            }
        }
    }

    // --- STUDY PROGRAMS ---
    fun addStudyProgram(code: String, name: String, faculty: String, description: String, isActive: Boolean): String? {
        val normalizedCode = code.trim().uppercase()
        val normalizedName = name.trim()
        if (normalizedCode.length < 2) return "Kode program studi minimal 2 karakter."
        if (normalizedName.length < 3) return "Nama program studi minimal 3 karakter."
        if (faculty.trim().length < 3) return "Nama fakultas wajib diisi."
        if (_uiState.value.studyPrograms.any { it.code.equals(normalizedCode, ignoreCase = true) }) {
            return "Kode program studi '$normalizedCode' sudah terdaftar."
        }
        val item = StudyProgram(
            code = normalizedCode,
            name = normalizedName,
            faculty = faculty.trim(),
            description = description.trim(),
            isActive = isActive
        )
        _uiState.update {
            it.copy(
                studyPrograms = listOf(item) + it.studyPrograms,
                toastMessage = ToastMessage("Program studi ${item.name} ditambahkan ke pratinjau.", ToastType.SUCCESS)
            )
        }
        return null
    }

    fun updateStudyProgram(id: String, code: String, name: String, faculty: String, description: String, isActive: Boolean): String? {
        val normalizedCode = code.trim().uppercase()
        val normalizedName = name.trim()
        if (normalizedCode.length < 2) return "Kode program studi minimal 2 karakter."
        if (normalizedName.length < 3) return "Nama program studi minimal 3 karakter."
        if (faculty.trim().length < 3) return "Nama fakultas wajib diisi."
        if (_uiState.value.studyPrograms.any { it.id != id && it.code.equals(normalizedCode, ignoreCase = true) }) {
            return "Kode program studi '$normalizedCode' sudah digunakan."
        }
        _uiState.update { state ->
            state.copy(
                studyPrograms = state.studyPrograms.map {
                    if (it.id == id) it.copy(
                        code = normalizedCode,
                        name = normalizedName,
                        faculty = faculty.trim(),
                        description = description.trim(),
                        isActive = isActive
                    ) else it
                },
                classes = state.classes.map { cls ->
                    if (cls.studyProgramId == id) cls.copy(studyProgramName = normalizedName) else cls
                },
                students = state.students.map { stu ->
                    if (stu.studyProgramId == id) stu.copy(studyProgramName = normalizedName) else stu
                },
                toastMessage = ToastMessage("Program studi diperbarui.", ToastType.SUCCESS)
            )
        }
        return null
    }

    fun deleteStudyProgram(id: String) {
        val hasClasses = _uiState.value.classes.any { it.studyProgramId == id }
        val hasStudents = _uiState.value.students.any { it.studyProgramId == id }
        if (hasClasses || hasStudents) {
            showToast("Program studi tidak dapat dihapus karena masih memiliki kelas atau mahasiswa aktif.", ToastType.ERROR)
            return
        }
        _uiState.update { state ->
            state.copy(
                studyPrograms = state.studyPrograms.filterNot { it.id == id },
                toastMessage = ToastMessage("Program studi berhasil dihapus.", ToastType.SUCCESS)
            )
        }
    }

    // --- CLASSES ---
    fun addClass(
        name: String,
        studyProgramId: String,
        semester: Int,
        academicYear: String,
        cohortYear: Int,
        description: String,
        isActive: Boolean
    ): String? {
        val prodi = _uiState.value.studyPrograms.find { it.id == studyProgramId }
            ?: return "Pilih program studi terlebih dahulu."
        if (name.trim().length < 2) return "Nama kelas wajib diisi."
        if (semester !in 1..14) return "Semester harus antara 1 sampai 14."
        if (academicYear.trim().length < 4) return "Tahun akademik wajib diisi (contoh: 2026/2027)."
        val newClass = AcademicClass(
            name = name.trim(),
            studyProgramId = prodi.id,
            studyProgramName = prodi.name,
            semester = semester,
            academicYear = academicYear.trim(),
            cohortYear = cohortYear,
            description = description.trim(),
            isActive = isActive
        )
        _uiState.update {
            it.copy(
                classes = listOf(newClass) + it.classes,
                toastMessage = ToastMessage("Kelas ${newClass.name} berhasil ditambahkan.", ToastType.SUCCESS)
            )
        }
        return null
    }

    fun deleteClass(id: String) {
        val hasStudents = _uiState.value.students.any { it.classId == id }
        val hasSchedules = _uiState.value.schedules.any { it.classId == id }
        val hasAssignments = _uiState.value.assignments.any { it.classId == id }
        if (hasStudents || hasSchedules || hasAssignments) {
            showToast("Kelas tidak dapat dihapus karena masih memiliki relasi mahasiswa, jadwal, atau tugas.", ToastType.ERROR)
            return
        }
        _uiState.update { state ->
            state.copy(
                classes = state.classes.filterNot { it.id == id },
                toastMessage = ToastMessage("Kelas berhasil dihapus.", ToastType.SUCCESS)
            )
        }
    }

    fun assignStudentToClass(studentId: String, classId: String?) {
        val targetClass = _uiState.value.classes.find { it.id == classId }
        _uiState.update { state ->
            val updatedStudents = state.students.map { stu ->
                if (stu.id == studentId) stu.copy(classId = targetClass?.id, className = targetClass?.name)
                else stu
            }
            val updatedClasses = state.classes.map { cls ->
                val members = updatedStudents.filter { it.classId == cls.id }.map { it.id }
                cls.copy(memberIds = members)
            }
            state.copy(
                students = updatedStudents,
                classes = updatedClasses,
                toastMessage = ToastMessage(
                    if (targetClass != null) "Mahasiswa ditambahkan ke ${targetClass.name}."
                    else "Mahasiswa dikeluarkan dari kelas.",
                    ToastType.SUCCESS
                )
            )
        }
    }

    // --- STUDENTS ---
    fun addStudent(
        fullName: String,
        nim: String,
        email: String,
        studyProgramId: String,
        classId: String?,
        semester: Int,
        cohortYear: Int,
        status: AccountStatus
    ): String? {
        val cleanNim = nim.trim()
        val cleanEmail = email.trim().lowercase()
        val prodi = _uiState.value.studyPrograms.find { it.id == studyProgramId }
            ?: return "Pilih program studi terlebih dahulu."
        if (fullName.trim().length < 3) return "Nama lengkap minimal 3 karakter."
        if (cleanNim.length < 5) return "NIM minimal 5 karakter."
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) return "Format email tidak valid."
        if (_uiState.value.students.any { it.nim.equals(cleanNim, ignoreCase = true) }) {
            return "NIM '$cleanNim' sudah terdaftar (harus unik)."
        }
        val selectedClass = _uiState.value.classes.find { it.id == classId }
        val student = StudentRecord(
            fullName = fullName.trim(),
            nim = cleanNim,
            email = cleanEmail,
            studyProgramId = prodi.id,
            studyProgramName = prodi.name,
            classId = selectedClass?.id,
            className = selectedClass?.name,
            semester = semester,
            cohortYear = cohortYear,
            status = status
        )
        _uiState.update { state ->
            val nextStudents = listOf(student) + state.students
            val nextClasses = state.classes.map { cls ->
                if (cls.id == selectedClass?.id) cls.copy(memberIds = cls.memberIds + student.id) else cls
            }
            state.copy(
                students = nextStudents,
                classes = nextClasses,
                toastMessage = ToastMessage("Mahasiswa ${student.fullName} ditambahkan (NIM unik tervalidasi).", ToastType.SUCCESS)
            )
        }
        return null
    }

    fun deleteStudent(id: String) {
        _uiState.update { state ->
            state.copy(
                students = state.students.filterNot { it.id == id },
                classes = state.classes.map { cls -> cls.copy(memberIds = cls.memberIds.filterNot { m -> m == id }) },
                toastMessage = ToastMessage("Data mahasiswa dihapus dari pratinjau.", ToastType.SUCCESS)
            )
        }
    }

    fun toggleStudentStatus(id: String) {
        _uiState.update { state ->
            state.copy(
                students = state.students.map { stu ->
                    if (stu.id == id) {
                        val next = if (stu.status == AccountStatus.ACTIVE) AccountStatus.INACTIVE else AccountStatus.ACTIVE
                        stu.copy(status = next)
                    } else stu
                },
                toastMessage = ToastMessage("Status akun mahasiswa diperbarui.", ToastType.INFO)
            )
        }
    }

    fun parseCsvPreview(rawCsv: String): List<CsvImportPreviewRow> {
        val existingNims = _uiState.value.students.map { it.nim.lowercase() }.toMutableSet()
        val seenInBatch = mutableSetOf<String>()
        return rawCsv.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { index, line ->
                val parts = line.split(",").map { it.trim() }
                val name = parts.getOrElse(0) { "" }
                val nim = parts.getOrElse(1) { "" }
                val email = parts.getOrElse(2) { "" }
                val sem = parts.getOrElse(3) { "1" }.toIntOrNull() ?: 1
                val cohort = parts.getOrElse(4) { "2026" }.toIntOrNull() ?: 2026
                val error = when {
                    parts.size < 3 -> "Kolom tidak lengkap (minimal Nama, NIM, Email)."
                    name.length < 3 -> "Nama terlalu pendek."
                    nim.length < 5 -> "NIM minimal 5 karakter."
                    !email.contains("@") -> "Format email tidak valid."
                    existingNims.contains(nim.lowercase()) -> "NIM duplikat dengan data tersimpan."
                    !seenInBatch.add(nim.lowercase()) -> "NIM duplikat di dalam file CSV."
                    else -> null
                }
                CsvImportPreviewRow(
                    rowNumber = index + 1,
                    fullName = name,
                    nim = nim,
                    email = email,
                    semester = sem,
                    cohortYear = cohort,
                    errorReason = error
                )
            }
    }

    fun commitCsvImport(rows: List<CsvImportPreviewRow>, studyProgramId: String, classId: String?) {
        val prodi = _uiState.value.studyPrograms.find { it.id == studyProgramId }
        if (prodi == null) {
            showToast("Pilih program studi tujuan sebelum impor.", ToastType.ERROR)
            return
        }
        val selectedClass = _uiState.value.classes.find { it.id == classId }
        val validRows = rows.filter { it.errorReason == null }
        if (validRows.isEmpty()) {
            showToast("Tidak ada baris valid untuk diimpor.", ToastType.WARNING)
            return
        }
        val created = validRows.map {
            StudentRecord(
                fullName = it.fullName,
                nim = it.nim,
                email = it.email,
                studyProgramId = prodi.id,
                studyProgramName = prodi.name,
                classId = selectedClass?.id,
                className = selectedClass?.name,
                semester = it.semester,
                cohortYear = it.cohortYear,
                status = AccountStatus.PENDING_ACTIVATION
            )
        }
        val failedCount = rows.size - validRows.size
        _uiState.update { state ->
            val nextStudents = created + state.students
            val nextClasses = state.classes.map { cls ->
                if (cls.id == selectedClass?.id) cls.copy(memberIds = cls.memberIds + created.map { it.id }) else cls
            }
            state.copy(
                students = nextStudents,
                classes = nextClasses,
                toastMessage = ToastMessage(
                    "Impor selesai: ${created.size} berhasil, $failedCount gagal dilewati.",
                    if (failedCount == 0) ToastType.SUCCESS else ToastType.WARNING
                )
            )
        }
    }

    // --- SCHEDULES ---
    fun addSchedule(
        classId: String,
        courseTitle: String,
        weekLabel: String,
        dateLabel: String,
        startTime: String,
        endTime: String,
        lecturer: String,
        method: LearningMethod,
        meetingUrl: String,
        roomLocation: String,
        materialTitle: String,
        notes: String
    ): String? {
        val cls = _uiState.value.classes.find { it.id == classId } ?: return "Pilih kelas terlebih dahulu."
        if (courseTitle.trim().length < 3) return "Nama mata kuliah / pertemuan wajib diisi."
        if (dateLabel.trim().isEmpty()) return "Tanggal perkuliahan wajib diisi."
        if (lecturer.trim().length < 3) return "Nama dosen / pengajar wajib diisi."
        if (method == LearningMethod.DARING && meetingUrl.trim().isNotEmpty() &&
            !meetingUrl.startsWith("https://") && !meetingUrl.startsWith("http://")
        ) {
            return "Link meeting harus diawali https://"
        }
        if (method == LearningMethod.LURING && roomLocation.trim().isEmpty()) {
            return "Lokasi ruang kelas wajib diisi untuk metode Luring."
        }
        val schedule = ClassSchedule(
            studyProgramName = cls.studyProgramName,
            classId = cls.id,
            className = cls.name,
            courseTitle = courseTitle.trim(),
            weekLabel = weekLabel.ifBlank { "Minggu 1" },
            dateLabel = dateLabel.trim(),
            startTime = startTime.ifBlank { "08:00" },
            endTime = endTime.ifBlank { "09:40" },
            lecturer = lecturer.trim(),
            method = method,
            meetingUrl = if (method == LearningMethod.DARING) meetingUrl.trim().ifBlank { null } else null,
            roomLocation = if (method == LearningMethod.LURING) roomLocation.trim() else null,
            materialTitle = materialTitle.trim().ifBlank { null },
            notes = notes.trim()
        )
        _uiState.update {
            it.copy(
                schedules = listOf(schedule) + it.schedules,
                toastMessage = ToastMessage("Jadwal ${schedule.courseTitle} (${schedule.method.label}) disimpan.", ToastType.SUCCESS)
            )
        }
        return null
    }

    fun deleteSchedule(id: String) {
        _uiState.update { state ->
            state.copy(
                schedules = state.schedules.filterNot { it.id == id },
                toastMessage = ToastMessage("Jadwal dihapus.", ToastType.SUCCESS)
            )
        }
    }

    // --- ATTENDANCE ---
    fun openAttendanceSession(
        classId: String,
        meetingTitle: String,
        method: LearningMethod,
        dateLabel: String,
        openTime: String,
        closeTime: String,
        notes: String
    ): String? {
        val cls = _uiState.value.classes.find { it.id == classId } ?: return "Pilih kelas terlebih dahulu."
        if (meetingTitle.trim().length < 3) return "Judul pertemuan wajib diisi."
        if (openTime.trim().isEmpty() || closeTime.trim().isEmpty()) return "Waktu mulai dan tutup absensi wajib diisi."
        val token = if (method == LearningMethod.LURING) {
            "AZR-${UUID.randomUUID().toString().take(8).uppercase()}"
        } else null
        val session = AttendanceSession(
            classId = cls.id,
            className = cls.name,
            meetingTitle = meetingTitle.trim(),
            method = method,
            dateLabel = dateLabel.ifBlank { "23 Nov 2026" },
            openTime = openTime.trim(),
            closeTime = closeTime.trim(),
            state = AttendanceSessionState.OPEN,
            notes = notes.trim(),
            qrTokenPreview = token
        )
        val notif = AppNotification(
            targetRole = UserRole.STUDENT,
            title = "Absensi Dibuka: ${session.meetingTitle}",
            body = "Metode ${session.method.label} • ${session.openTime} - ${session.closeTime} WIB",
            category = "Absensi",
            timestamp = currentTimeLabel()
        )
        _uiState.update {
            it.copy(
                attendanceSessions = listOf(session) + it.attendanceSessions,
                notifications = listOf(notif) + it.notifications,
                toastMessage = ToastMessage("Sesi absensi ${session.meetingTitle} dibuka.", ToastType.SUCCESS)
            )
        }
        return null
    }

    fun closeAttendanceSession(sessionId: String) {
        _uiState.update { state ->
            state.copy(
                attendanceSessions = state.attendanceSessions.map {
                    if (it.id == sessionId) it.copy(state = AttendanceSessionState.CLOSED, qrTokenPreview = null) else it
                },
                toastMessage = ToastMessage("Sesi absensi ditutup dan token QR dinonaktifkan.", ToastType.INFO)
            )
        }
    }

    fun recordStudentAttendance(sessionId: String) {
        val user = _uiState.value.currentUser ?: return
        val session = _uiState.value.attendanceSessions.find { it.id == sessionId } ?: return
        if (session.state != AttendanceSessionState.OPEN) {
            showToast("Absensi ditolak: sesi belum dibuka atau sudah ditutup.", ToastType.ERROR)
            return
        }
        val alreadyRecorded = _uiState.value.attendanceRecords.any {
            it.sessionId == sessionId && it.studentId == user.id
        }
        if (alreadyRecorded) {
            showToast("Anda sudah tercatat hadir pada sesi ini (maksimal 1x per sesi).", ToastType.WARNING)
            return
        }
        val now = currentTimeLabel()
        val record = AttendanceRecord(
            sessionId = session.id,
            meetingTitle = session.meetingTitle,
            className = session.className,
            studentId = user.id,
            studentName = user.fullName,
            studentNim = user.nim ?: "-",
            dateLabel = session.dateLabel,
            recordedAt = now,
            status = AttendanceStatus.HADIR
        )
        val earnedPoints = pointsForAttendance(AttendanceStatus.HADIR)
        _uiState.update { state ->
            val updatedPoints = upsertPointTransaction(
                existing = state.rankingPoints,
                studentId = user.id,
                sourceType = PointSourceType.ATTENDANCE,
                sourceId = session.id,
                points = earnedPoints,
                description = "Absensi ${session.meetingTitle} (${AttendanceStatus.HADIR.label})",
                nowLabel = now
            )
            state.copy(
                attendanceRecords = listOf(record) + state.attendanceRecords,
                rankingPoints = updatedPoints,
                toastMessage = ToastMessage(
                    "Kehadiran Anda pada ${session.meetingTitle} berhasil dicatat (+$earnedPoints poin).",
                    ToastType.SUCCESS
                )
            )
        }
    }

    fun correctAttendance(recordId: String, newStatus: AttendanceStatus, reason: String): String? {
        if (reason.trim().length < 5) return "Alasan koreksi wajib diisi minimal 5 karakter untuk audit."
        val targetRecord = _uiState.value.attendanceRecords.find { it.id == recordId }
            ?: return "Catatan absensi tidak ditemukan."
        val adminName = _uiState.value.currentUser?.fullName ?: "Administrator"
        val now = currentTimeLabel()
        val newPts = pointsForAttendance(newStatus)
        val oldPts = _uiState.value.rankingPoints.find {
            it.studentId == targetRecord.studentId &&
                it.sourceType == PointSourceType.ATTENDANCE &&
                it.sourceId == targetRecord.sessionId
        }?.points ?: pointsForAttendance(targetRecord.status)

        _uiState.update { state ->
            val updatedPoints = upsertPointTransaction(
                existing = state.rankingPoints,
                studentId = targetRecord.studentId,
                sourceType = PointSourceType.ATTENDANCE,
                sourceId = targetRecord.sessionId,
                points = newPts,
                description = "Koreksi Absensi ${targetRecord.meetingTitle} (${newStatus.label})",
                nowLabel = now
            )
            val auditEntry = RankingPointAuditLog(
                studentId = targetRecord.studentId,
                studentName = targetRecord.studentName,
                adminName = adminName,
                reason = "Koreksi absensi (${targetRecord.status.label} -> ${newStatus.label}): ${reason.trim()}",
                pointsBefore = oldPts,
                pointsAfter = newPts,
                changedAt = now
            )
            state.copy(
                attendanceRecords = state.attendanceRecords.map { rec ->
                    if (rec.id == recordId) rec.copy(status = newStatus, correctionReason = reason.trim()) else rec
                },
                rankingPoints = updatedPoints,
                rankingAuditLogs = listOf(auditEntry) + state.rankingAuditLogs,
                toastMessage = ToastMessage(
                    "Koreksi absensi disimpan & poin dihitung ulang ($oldPts -> $newPts poin).",
                    ToastType.SUCCESS
                )
            )
        }
        return null
    }

    // --- ASSIGNMENTS ---
    fun addAssignment(
        title: String,
        description: String,
        instructions: String,
        classId: String,
        courseCategory: String,
        deadline: String,
        allowedFormats: List<String>,
        maxFileSizeMb: Int,
        submissionMethod: SubmissionMethod,
        materialAttachment: String
    ): String? {
        val cls = _uiState.value.classes.find { it.id == classId } ?: return "Pilih kelas tujuan terlebih dahulu."
        if (title.trim().length < 3) return "Judul tugas minimal 3 karakter."
        if (instructions.trim().length < 5) return "Instruksi pengerjaan wajib diisi."
        if (deadline.trim().isEmpty()) return "Batas waktu (deadline) wajib diisi."
        if (allowedFormats.isEmpty()) return "Pilih minimal satu format file yang diizinkan."
        val assignment = Assignment(
            title = title.trim(),
            description = description.trim(),
            instructions = instructions.trim(),
            classId = cls.id,
            className = cls.name,
            courseCategory = courseCategory.ifBlank { "Umum" },
            createdAt = currentTimeLabel(),
            deadline = deadline.trim(),
            allowedFormats = allowedFormats,
            maxFileSizeMb = maxFileSizeMb.coerceIn(1, 100),
            submissionMethod = submissionMethod,
            materialAttachment = materialAttachment.trim().ifBlank { null },
            isActive = true
        )
        val notif = AppNotification(
            targetRole = UserRole.STUDENT,
            title = "Tugas Baru: ${assignment.title}",
            body = "Kelas ${assignment.className} • Deadline ${assignment.deadline}",
            category = "Tugas",
            timestamp = currentTimeLabel()
        )
        _uiState.update {
            it.copy(
                assignments = listOf(assignment) + it.assignments,
                notifications = listOf(notif) + it.notifications,
                toastMessage = ToastMessage("Tugas '${assignment.title}' berhasil dipublikasikan.", ToastType.SUCCESS)
            )
        }
        return null
    }

    fun toggleAssignmentActive(id: String) {
        _uiState.update { state ->
            state.copy(
                assignments = state.assignments.map {
                    if (it.id == id) it.copy(isActive = !it.isActive) else it
                },
                toastMessage = ToastMessage("Status tugas diperbarui.", ToastType.INFO)
            )
        }
    }

    fun deleteAssignment(id: String) {
        val hasSubmissions = _uiState.value.submissions.any { it.assignmentId == id }
        if (hasSubmissions) {
            showToast("Tugas tidak dapat dihapus karena sudah memiliki pengumpulan mahasiswa.", ToastType.ERROR)
            return
        }
        _uiState.update { state ->
            state.copy(
                assignments = state.assignments.filterNot { it.id == id },
                toastMessage = ToastMessage("Tugas dihapus.", ToastType.SUCCESS)
            )
        }
    }

    // --- SUBMISSIONS & GRADING ---
    fun submitAssignment(
        assignmentId: String,
        driveLink: String?,
        uploadedFileName: String?,
        uploadedFileSizeLabel: String?,
        isLate: Boolean = false
    ): String? {
        val user = _uiState.value.currentUser ?: return "Sesi pengguna tidak ditemukan."
        val assignment = _uiState.value.assignments.find { it.id == assignmentId }
            ?: return "Tugas tidak ditemukan."
        val cleanDrive = driveLink?.trim()?.ifBlank { null }
        val cleanFile = uploadedFileName?.trim()?.ifBlank { null }
        if (cleanDrive == null && cleanFile == null) {
            return "Lampirkan link Google Drive atau pilih file untuk dikumpulkan."
        }
        if (cleanDrive != null && !cleanDrive.startsWith("https://drive.google.com/") && !cleanDrive.startsWith("https://docs.google.com/")) {
            return "Link harus menggunakan URL Google Drive / Google Docs resmi (https://drive.google.com/...)."
        }
        val now = currentTimeLabel()
        val existing = _uiState.value.submissions.find { it.assignmentId == assignmentId && it.studentId == user.id }
        val submissionStatus = if (isLate) SubmissionStatus.LATE else SubmissionStatus.AWAITING_ADMIN
        val earnedPoints = if (isLate) 5 else 10
        val record = SubmissionRecord(
            id = existing?.id ?: UUID.randomUUID().toString(),
            assignmentId = assignment.id,
            assignmentTitle = assignment.title,
            className = assignment.className,
            studentId = user.id,
            studentName = user.fullName,
            studentNim = user.nim ?: "-",
            driveLink = cleanDrive,
            uploadedFileName = cleanFile,
            uploadedFileSizeLabel = uploadedFileSizeLabel,
            submittedAt = now,
            status = submissionStatus,
            isLate = isLate,
            revisionCount = if (existing != null) existing.revisionCount + 1 else 0
        )
        val adminNotif = AppNotification(
            targetRole = UserRole.ADMIN,
            title = if (isLate) "Pengumpulan Terlambat: ${assignment.title}" else "Pengumpulan Baru: ${assignment.title}",
            body = "${user.fullName} mengumpulkan tugas (${cleanFile ?: "Google Drive"}).",
            category = "Pengumpulan",
            timestamp = now
        )
        _uiState.update { state ->
            val filtered = state.submissions.filterNot { it.id == record.id }
            // Unique constraint per (studentId, ASSIGNMENT, assignmentId) prevents duplicate points on revisions
            val updatedPoints = upsertPointTransaction(
                existing = state.rankingPoints,
                studentId = user.id,
                sourceType = PointSourceType.ASSIGNMENT,
                sourceId = assignment.id,
                points = earnedPoints,
                description = if (isLate) {
                    "Tugas '${assignment.title}' dikumpulkan terlambat"
                } else {
                    "Tugas '${assignment.title}' dikumpulkan tepat waktu"
                },
                nowLabel = now
            )
            state.copy(
                submissions = listOf(record) + filtered,
                rankingPoints = updatedPoints,
                notifications = listOf(adminNotif) + state.notifications,
                toastMessage = ToastMessage(
                    if (existing != null) "Revisi tugas '${assignment.title}' disimpan (poin diperbarui tanpa duplikasi: +$earnedPoints)."
                    else "Tugas '${assignment.title}' berhasil dikirim (+$earnedPoints poin).",
                    ToastType.SUCCESS
                )
            )
        }
        return null
    }

    fun requestAiCheckStageNotice(submissionId: String) {
        showToast(
            "Pemeriksaan Gemini AI aktif penuh pada Tahap 6 (Firebase AI Logic). Saat ini Anda dapat melakukan penilaian manual.",
            ToastType.INFO
        )
    }

    fun saveGrade(submissionId: String, score: Int, feedback: String, requestRevision: Boolean): String? {
        if (score !in 0..100) return "Nilai harus berada pada rentang 0 - 100."
        if (feedback.trim().length < 3) return "Feedback wajib diisi minimal 3 karakter."
        val target = _uiState.value.submissions.find { it.id == submissionId } ?: return "Pengumpulan tidak ditemukan."
        val historyEntry = GradeAuditEntry(
            changedAt = currentTimeLabel(),
            previousScore = target.finalScore,
            newScore = score,
            note = if (requestRevision) "Diminta revisi oleh Admin" else "Dinilai oleh Admin"
        )
        val nextStatus = if (requestRevision) SubmissionStatus.NEEDS_REVISION else SubmissionStatus.GRADED
        val studentNotif = AppNotification(
            targetRole = UserRole.STUDENT,
            title = if (requestRevision) "Tugas Perlu Revisi" else "Nilai Tugas Tersedia",
            body = "${target.assignmentTitle} • Nilai: $score",
            category = "Penilaian",
            timestamp = currentTimeLabel()
        )
        _uiState.update { state ->
            state.copy(
                submissions = state.submissions.map { sub ->
                    if (sub.id == submissionId) {
                        sub.copy(
                            finalScore = score,
                            adminFeedback = feedback.trim(),
                            status = nextStatus,
                            gradeHistory = listOf(historyEntry) + sub.gradeHistory
                        )
                    } else sub
                },
                notifications = listOf(studentNotif) + state.notifications,
                toastMessage = ToastMessage(
                    if (requestRevision) "Permintaan revisi dikirim ke mahasiswa."
                    else "Nilai final ($score) dan feedback disimpan.",
                    ToastType.SUCCESS
                )
            )
        }
        return null
    }

    // --- NOTIFICATIONS & SETTINGS ---
    fun markNotificationRead(id: String) {
        _uiState.update { state ->
            state.copy(
                notifications = state.notifications.map { if (it.id == id) it.copy(isRead = true) else it }
            )
        }
    }

    fun markAllNotificationsRead() {
        val role = _uiState.value.currentUser?.role ?: return
        _uiState.update { state ->
            state.copy(
                notifications = state.notifications.map {
                    if (it.targetRole == role) it.copy(isRead = true) else it
                },
                toastMessage = ToastMessage("Semua notifikasi ditandai sudah dibaca.", ToastType.INFO)
            )
        }
    }

    fun updateSettings(newSettings: AppSettingsState) {
        _uiState.update {
            it.copy(
                settings = newSettings,
                toastMessage = ToastMessage("Pengaturan aplikasi diperbarui.", ToastType.SUCCESS)
            )
        }
    }

    // --- STUDENT RANKING & LEADERBOARD ENGINE ---
    private fun pointsForAttendance(status: AttendanceStatus): Int = when (status) {
        AttendanceStatus.HADIR -> 10
        AttendanceStatus.IZIN -> 5
        AttendanceStatus.SAKIT -> 5
        AttendanceStatus.ALFA -> 0
    }

    private fun upsertPointTransaction(
        existing: List<RankingPointTransaction>,
        studentId: String,
        sourceType: PointSourceType,
        sourceId: String,
        points: Int,
        description: String,
        nowLabel: String
    ): List<RankingPointTransaction> {
        val current = existing.find {
            it.studentId == studentId && it.sourceType == sourceType && it.sourceId == sourceId
        }
        return if (current != null) {
            existing.map { tx ->
                if (tx.id == current.id) {
                    tx.copy(points = points, description = description, updatedAt = nowLabel)
                } else tx
            }
        } else {
            val created = RankingPointTransaction(
                studentId = studentId,
                sourceType = sourceType,
                sourceId = sourceId,
                points = points,
                description = description,
                createdAt = nowLabel,
                updatedAt = nowLabel
            )
            listOf(created) + existing
        }
    }

    fun adjustStudentPoints(
        studentId: String,
        studentName: String,
        deltaPoints: Int,
        reason: String
    ): String? {
        val currentUser = _uiState.value.currentUser
        if (currentUser?.role != UserRole.ADMIN) {
            return "Akses ditolak: hanya Admin yang dapat melakukan penyesuaian poin."
        }
        if (deltaPoints == 0) return "Perubahan poin tidak boleh 0."
        if (reason.trim().length < 5) return "Alasan penyesuaian wajib diisi minimal 5 karakter untuk audit."

        val now = currentTimeLabel()
        val beforeTotal = _uiState.value.rankingPoints
            .filter { it.studentId == studentId }
            .sumOf { it.points }
        val afterTotal = (beforeTotal + deltaPoints).coerceAtLeast(0)
        val effectiveDelta = afterTotal - beforeTotal

        val tx = RankingPointTransaction(
            studentId = studentId,
            sourceType = PointSourceType.MANUAL_ADJUSTMENT,
            sourceId = "manual-${UUID.randomUUID()}",
            points = effectiveDelta,
            description = "Penyesuaian Admin: ${reason.trim()}",
            createdAt = now,
            updatedAt = now
        )
        val audit = RankingPointAuditLog(
            studentId = studentId,
            studentName = studentName,
            adminName = currentUser.fullName,
            reason = reason.trim(),
            pointsBefore = beforeTotal,
            pointsAfter = afterTotal,
            changedAt = now
        )
        _uiState.update { state ->
            state.copy(
                rankingPoints = listOf(tx) + state.rankingPoints,
                rankingAuditLogs = listOf(audit) + state.rankingAuditLogs,
                toastMessage = ToastMessage(
                    "Penyesuaian poin $studentName disimpan ($beforeTotal -> $afterTotal poin) dengan log audit.",
                    ToastType.SUCCESS
                )
            )
        }
        return null
    }

    companion object {
        fun computeRankings(
            state: AzrilUiState,
            periodFilter: RankingPeriodFilter = RankingPeriodFilter.SEMESTER,
            studyProgramId: String? = null,
            classId: String? = null,
            semester: Int? = null
        ): List<StudentRankingEntry> {
            val nowMillis = System.currentTimeMillis()
            val minTimestamp = when (periodFilter) {
                RankingPeriodFilter.WEEKLY -> nowMillis - 7L * 24 * 60 * 60 * 1000
                RankingPeriodFilter.MONTHLY -> nowMillis - 30L * 24 * 60 * 60 * 1000
                RankingPeriodFilter.SEMESTER, RankingPeriodFilter.CUSTOM -> 0L
            }

            // Build student pool from actual registered students + any active student profile with real point activity
            val registeredMap = state.students.associateBy { it.id }.toMutableMap()
            val activeUser = state.currentUser
            if (activeUser != null && activeUser.role == UserRole.STUDENT && !registeredMap.containsKey(activeUser.id)) {
                val hasActivity = state.rankingPoints.any { it.studentId == activeUser.id } ||
                    state.attendanceRecords.any { it.studentId == activeUser.id } ||
                    state.submissions.any { it.studentId == activeUser.id }
                if (hasActivity) {
                    registeredMap[activeUser.id] = StudentRecord(
                        id = activeUser.id,
                        fullName = activeUser.fullName,
                        nim = activeUser.nim ?: "-",
                        email = activeUser.email,
                        studyProgramId = "preview-prodi",
                        studyProgramName = activeUser.studyProgramName ?: "Belum terdaftar",
                        classId = null,
                        className = activeUser.className ?: "Belum ada kelas",
                        semester = activeUser.semester ?: 1,
                        cohortYear = activeUser.cohortYear ?: 2026
                    )
                }
            }

            val filteredStudents = registeredMap.values.filter { stu ->
                (studyProgramId == null || stu.studyProgramId == studyProgramId) &&
                    (classId == null || stu.classId == classId) &&
                    (semester == null || stu.semester == semester)
            }

            val periodTransactions = state.rankingPoints.filter { it.timestampMillis >= minTimestamp }

            val rawEntries = filteredStudents.map { stu ->
                val stuTx = periodTransactions.filter { it.studentId == stu.id }
                val totalPoints = stuTx.sumOf { it.points }.coerceAtLeast(0)

                val stuAttendances = state.attendanceRecords.filter { it.studentId == stu.id }
                val hadirCount = stuAttendances.count { it.status == AttendanceStatus.HADIR }
                val izinCount = stuAttendances.count { it.status == AttendanceStatus.IZIN }
                val sakitCount = stuAttendances.count { it.status == AttendanceStatus.SAKIT }
                val alfaCount = stuAttendances.count { it.status == AttendanceStatus.ALFA }

                val relevantAssignments = state.assignments.filter { a ->
                    a.isActive && (stu.classId == null || a.classId == stu.classId)
                }
                val stuSubs = state.submissions.filter { it.studentId == stu.id }
                val onTimeCount = stuSubs.count { !it.isLate && it.status != SubmissionStatus.LATE && it.status != SubmissionStatus.NOT_SUBMITTED }
                val lateCount = stuSubs.count { it.isLate || it.status == SubmissionStatus.LATE }
                val missingCount = relevantAssignments.count { a -> stuSubs.none { it.assignmentId == a.id } }

                StudentRankingEntry(
                    rank = 1,
                    studentId = stu.id,
                    studentName = stu.fullName,
                    nim = stu.nim,
                    studyProgramId = stu.studyProgramId,
                    studyProgramName = stu.studyProgramName,
                    classId = stu.classId,
                    className = stu.className ?: "Belum ada kelas",
                    semester = stu.semester,
                    totalPoints = totalPoints,
                    hadirCount = hadirCount,
                    izinCount = izinCount,
                    sakitCount = sakitCount,
                    alfaCount = alfaCount,
                    onTimeAssignmentsCount = onTimeCount,
                    lateAssignmentsCount = lateCount,
                    missingAssignmentsCount = missingCount,
                    pointGeneratingActivityCount = stuTx.count { it.points > 0 }
                )
            }

            // Deterministic ordering with exact 3-tier tie-breaker:
            // 1. Total points DESC
            // 2. On-time assignments DESC
            // 3. Hadir count DESC
            // 4. Alfa count ASC
            val sorted = rawEntries.sortedWith(
                compareByDescending<StudentRankingEntry> { it.totalPoints }
                    .thenByDescending { it.onTimeAssignmentsCount }
                    .thenByDescending { it.hadirCount }
                    .thenBy { it.alfaCount }
                    .thenBy { it.studentName }
            )

            // Assign shared rank when all 4 ranking criteria are equal + compute point gap to rank above
            val result = mutableListOf<StudentRankingEntry>()
            sorted.forEachIndexed { index, entry ->
                val prev = result.lastOrNull()
                val sameStanding = prev != null &&
                    prev.totalPoints == entry.totalPoints &&
                    prev.onTimeAssignmentsCount == entry.onTimeAssignmentsCount &&
                    prev.hadirCount == entry.hadirCount &&
                    prev.alfaCount == entry.alfaCount
                val assignedRank = if (sameStanding && prev != null) prev.rank else index + 1
                val higherEntry = result.lastOrNull { it.rank < assignedRank }
                val gap = higherEntry?.let { (it.totalPoints - entry.totalPoints).coerceAtLeast(0) }
                result.add(
                    entry.copy(
                        rank = assignedRank,
                        pointGapToAbove = gap,
                        rankAbove = higherEntry?.rank
                    )
                )
            }
            return result
        }
    }
}
