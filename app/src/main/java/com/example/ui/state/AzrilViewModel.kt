package com.example.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthResult
import com.example.data.auth.AzrilAuthRepository
import com.example.data.auth.DataStoreAuthStorage
import com.example.data.auth.OfficialStudyPrograms
import com.example.data.gamification.StreakReminderNotifier
import com.example.data.gamification.XpAndStreakEngine
import com.example.data.importer.ExcelStudentImportParser
import com.example.ui.model.AcademicClass
import com.example.ui.model.AccountStatus
import com.example.ui.model.AppNotification
import com.example.ui.model.AppSettingsState
import com.example.ui.model.Assignment
import com.example.ui.model.AssignmentTargetType
import com.example.ui.model.AttendanceRecord
import com.example.ui.model.AttendanceSession
import com.example.ui.model.AttendanceSessionState
import com.example.ui.model.AttendanceStatus
import com.example.ui.model.ClassSchedule
import com.example.ui.model.CsvImportPreviewRow
import com.example.ui.model.ExcelImportPreviewSummary
import com.example.ui.model.ExcelImportValidationStatus
import com.example.ui.model.GradeAuditEntry
import com.example.ui.model.HistoricalAttendanceRecord
import com.example.ui.model.LearningMethod
import com.example.ui.model.ParticipationXpCategory
import com.example.ui.model.PointSourceType
import com.example.ui.model.RankingPeriodFilter
import com.example.ui.model.RankingPointAuditLog
import com.example.ui.model.RankingPointTransaction
import com.example.ui.model.ScreenLoadState
import com.example.ui.model.StreakNotificationLog
import com.example.ui.model.StudentBadgeStatus
import com.example.ui.model.StudentIzinQuotaSummary
import com.example.ui.model.StudentRankingEntry
import com.example.ui.model.StudentRecord
import com.example.ui.model.StudentStreakState
import com.example.ui.model.StudyProgram
import com.example.ui.model.SubmissionMethod
import com.example.ui.model.SubmissionRecord
import com.example.ui.model.SubmissionStatus
import com.example.ui.model.SubmissionTimeliness
import com.example.ui.model.ToastMessage
import com.example.ui.model.ToastType
import com.example.ui.model.UserProfile
import com.example.ui.model.UserRole
import com.example.ui.model.XpCategory
import com.example.ui.model.XpHistoryTimeRange
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
    val historicalAttendanceRecords: List<HistoricalAttendanceRecord> = emptyList(),
    val assignments: List<Assignment> = emptyList(),
    val submissions: List<SubmissionRecord> = emptyList(),
    val rankingPoints: List<RankingPointTransaction> = emptyList(),
    val rankingAuditLogs: List<RankingPointAuditLog> = emptyList(),
    val studentStreaks: Map<String, StudentStreakState> = emptyMap(),
    val streakNotificationLogs: List<StreakNotificationLog> = emptyList(),
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
            _uiState.update { state ->
                val mergedStudents = mergeRegisteredStudents(state.students, restored.registeredStudents)
                val restoredUser = restored.profile?.let { profile ->
                    val matchingStudent = mergedStudents.find { it.id == profile.id }
                    if (matchingStudent != null) {
                        profile.copy(
                            fullName = matchingStudent.fullName,
                            nim = matchingStudent.nim,
                            email = matchingStudent.email,
                            studyProgramId = matchingStudent.studyProgramId,
                            studyProgramName = matchingStudent.studyProgramName,
                            classId = matchingStudent.classId,
                            className = matchingStudent.className ?: "Belum terdaftar di kelas",
                            semester = matchingStudent.semester,
                            cohortYear = matchingStudent.cohortYear,
                            status = matchingStudent.status,
                            badgeStatus = matchingStudent.badgeStatus,
                            mustChangePassword = matchingStudent.mustChangePassword
                        )
                    } else profile
                }
                state.copy(
                    currentUser = restoredUser,
                    isRestoringSession = false,
                    rememberedIdentifier = restored.rememberedIdentifier,
                    rememberMeEnabled = restored.rememberMeEnabled,
                    isSupabaseConfigured = restored.isSupabaseConfigured,
                    students = mergedStudents,
                    classes = if (restored.savedClasses.isNotEmpty()) restored.savedClasses else state.classes,
                    assignments = if (restored.savedAssignments.isNotEmpty()) restored.savedAssignments else state.assignments,
                    rankingPoints = if (restored.savedXpTransactions.isNotEmpty()) restored.savedXpTransactions else state.rankingPoints,
                    attendanceRecords = if (restored.savedAttendanceRecords.isNotEmpty()) restored.savedAttendanceRecords else state.attendanceRecords,
                    historicalAttendanceRecords = if (restored.savedHistoricalAttendanceRecords.isNotEmpty()) restored.savedHistoricalAttendanceRecords else state.historicalAttendanceRecords,
                    studentStreaks = if (restored.savedStreaks.isNotEmpty()) restored.savedStreaks else state.studentStreaks,
                    streakNotificationLogs = if (restored.savedStreakNotifications.isNotEmpty()) restored.savedStreakNotifications else state.streakNotificationLogs
                )
            }
            val current = _uiState.value.currentUser
            if (current?.role == UserRole.STUDENT && !current.mustChangePassword) {
                recordStudentDailyLogin(current.id)
            } else if (current == null || current.role == UserRole.ADMIN) {
                evaluateStreakRemindersForInactiveStudents()
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
        classId = classId,
        className = className?.takeIf { it != "Belum terdaftar di kelas" },
        semester = semester ?: 1,
        cohortYear = cohortYear ?: 2026,
        status = status,
        badgeStatus = badgeStatus,
        mustChangePassword = mustChangePassword
    )

    private fun mergeRegisteredStudents(
        existing: List<StudentRecord>,
        incoming: List<StudentRecord>
    ): List<StudentRecord> {
        val byId = existing.associateBy { it.id }.toMutableMap()
        incoming.forEach { stu ->
            byId[stu.id] = stu
        }
        return byId.values.toList()
    }

    fun clearAuthError() {
        _uiState.update { it.copy(authErrorMessage = null) }
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
                        val syncedStudent = state.students.find { it.id == result.profile.id }
                        val enrichedProfile = if (syncedStudent != null && result.profile.role == UserRole.STUDENT) {
                            result.profile.copy(
                                fullName = syncedStudent.fullName,
                                nim = syncedStudent.nim,
                                email = syncedStudent.email,
                                studyProgramId = syncedStudent.studyProgramId,
                                studyProgramName = syncedStudent.studyProgramName,
                                classId = syncedStudent.classId,
                                className = syncedStudent.className ?: "Belum terdaftar di kelas",
                                semester = syncedStudent.semester,
                                cohortYear = syncedStudent.cohortYear,
                                status = syncedStudent.status,
                                badgeStatus = syncedStudent.badgeStatus,
                                mustChangePassword = result.profile.mustChangePassword || syncedStudent.mustChangePassword
                            )
                        } else result.profile
                        val updatedStudents = if (enrichedProfile.role == UserRole.STUDENT) {
                            mergeRegisteredStudents(state.students, listOf(enrichedProfile.toStudentRecord()))
                        } else state.students
                        val loginToast = if (enrichedProfile.role == UserRole.STUDENT && enrichedProfile.mustChangePassword) {
                            ToastMessage(
                                message = "Login pertama terdeteksi. Silakan ganti password awal (NIM) Anda terlebih dahulu.",
                                type = ToastType.INFO
                            )
                        } else {
                            ToastMessage(
                                message = "Selamat datang kembali, ${enrichedProfile.fullName}!",
                                type = ToastType.SUCCESS
                            )
                        }
                        state.copy(
                            authInProgress = false,
                            authErrorMessage = null,
                            currentUser = enrichedProfile,
                            adminDestination = AdminDestination.DASHBOARD,
                            studentDestination = StudentDestination.DASHBOARD,
                            rememberMeEnabled = rememberMe,
                            rememberedIdentifier = if (rememberMe) (enrichedProfile.nim ?: identifier.trim()) else "",
                            students = updatedStudents,
                            toastMessage = loginToast
                        )
                    }
                    val active = _uiState.value.currentUser
                    if (active?.role == UserRole.STUDENT && !active.mustChangePassword) {
                        recordStudentDailyLogin(active.id)
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

    fun completeFirstLoginPasswordChange(
        newPassword: String,
        confirmPassword: String
    ) {
        val current = _uiState.value.currentUser ?: return
        val cleanNew = newPassword.trim()
        val cleanConfirm = confirmPassword.trim()
        if (cleanNew.length < 6) {
            _uiState.update { it.copy(authErrorMessage = "Kata sandi baru minimal 6 karakter.") }
            return
        }
        if (cleanNew != cleanConfirm) {
            _uiState.update { it.copy(authErrorMessage = "Konfirmasi kata sandi tidak cocok.") }
            return
        }
        if (current.nim != null && cleanNew.equals(current.nim.trim(), ignoreCase = true)) {
            _uiState.update { it.copy(authErrorMessage = "Kata sandi baru tidak boleh sama dengan NIM Anda.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(authInProgress = true, authErrorMessage = null) }
            when (val res = authRepository.completeFirstLoginPasswordChange(current.id, cleanNew)) {
                is AuthResult.Success -> {
                    _uiState.update { state ->
                        val updatedProfile = current.copy(mustChangePassword = false)
                        val updatedStudents = state.students.map { stu ->
                            if (stu.id == current.id) stu.copy(mustChangePassword = false) else stu
                        }
                        state.copy(
                            authInProgress = false,
                            authErrorMessage = null,
                            currentUser = updatedProfile,
                            students = updatedStudents,
                            studentDestination = StudentDestination.DASHBOARD,
                            toastMessage = ToastMessage(
                                "Kata sandi berhasil diperbarui! Selamat datang di Azril Clas, ${updatedProfile.fullName}.",
                                ToastType.SUCCESS
                            )
                        )
                    }
                    recordStudentDailyLogin(current.id)
                }
                is AuthResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            authInProgress = false,
                            authErrorMessage = res.message,
                            toastMessage = ToastMessage(res.message, ToastType.ERROR)
                        )
                    }
                }
            }
        }
    }

    private fun currentTimeLabel(): String = XpAndStreakEngine.formatWibDateTimeLabel(System.currentTimeMillis())

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
        val previewStudentId = activeStudent?.id ?: "preview-student"
        _uiState.update {
            it.copy(
                currentUser = UserProfile(
                    id = previewStudentId,
                    fullName = activeStudent?.fullName ?: "Mahasiswa (Pratinjau)",
                    email = activeStudent?.email ?: "mahasiswa@azrilclas.ac.id",
                    role = UserRole.STUDENT,
                    nim = activeStudent?.nim ?: "Belum terdaftar",
                    studyProgramId = activeStudent?.studyProgramId,
                    studyProgramName = activeStudent?.studyProgramName ?: "HES",
                    classId = activeStudent?.classId,
                    className = activeStudent?.className ?: "Belum terdaftar di kelas",
                    semester = activeStudent?.semester,
                    cohortYear = activeStudent?.cohortYear,
                    status = activeStudent?.status ?: AccountStatus.ACTIVE,
                    badgeStatus = activeStudent?.badgeStatus ?: StudentBadgeStatus.NEUTRAL
                ),
                studentDestination = StudentDestination.DASHBOARD,
                toastMessage = ToastMessage(
                    message = "Masuk ke Panel Mahasiswa.",
                    type = ToastType.INFO
                )
            )
        }
        recordStudentDailyLogin(previewStudentId)
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
        val nextClasses = listOf(newClass) + _uiState.value.classes
        _uiState.update {
            it.copy(
                classes = nextClasses,
                toastMessage = ToastMessage("Kelas ${newClass.name} berhasil ditambahkan.", ToastType.SUCCESS)
            )
        }
        viewModelScope.launch { authRepository.saveClasses(nextClasses) }
        return null
    }

    fun deleteClass(id: String) {
        val hasStudents = _uiState.value.students.any { it.classId == id }
        val hasSchedules = _uiState.value.schedules.any { it.classId == id }
        val hasAssignments = _uiState.value.assignments.any {
            it.classId == id || it.targetClassIds.contains(id)
        }
        if (hasStudents || hasSchedules || hasAssignments) {
            showToast("Kelas tidak dapat dihapus karena masih memiliki relasi mahasiswa, jadwal, atau tugas.", ToastType.ERROR)
            return
        }
        val nextClasses = _uiState.value.classes.filterNot { it.id == id }
        _uiState.update { state ->
            state.copy(
                classes = nextClasses,
                toastMessage = ToastMessage("Kelas berhasil dihapus.", ToastType.SUCCESS)
            )
        }
        viewModelScope.launch { authRepository.saveClasses(nextClasses) }
    }

    fun assignStudentToClass(studentId: String, classId: String?) {
        val targetClass = _uiState.value.classes.find { it.id == classId }
        val targetStudent = _uiState.value.students.find { it.id == studentId }
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
        if (targetStudent != null) {
            val prodiCode = resolveStudyProgramCode(
                targetStudent.studyProgramId,
                targetStudent.studyProgramName,
                _uiState.value.studyPrograms
            )
            viewModelScope.launch {
                authRepository.adminUpdateStudentAccount(
                    studentId = targetStudent.id,
                    fullName = targetStudent.fullName,
                    nim = targetStudent.nim,
                    email = targetStudent.email,
                    studyProgramId = targetStudent.studyProgramId,
                    studyProgramCode = prodiCode,
                    classId = targetClass?.id,
                    className = targetClass?.name,
                    semester = targetStudent.semester,
                    cohortYear = targetStudent.cohortYear,
                    accountStatus = targetStudent.status,
                    badgeStatus = targetStudent.badgeStatus
                )
                authRepository.saveClasses(_uiState.value.classes)
            }
        }
    }

    // --- STUDENTS (ADMIN-ONLY PROVISIONING, PASSWORD MANAGEMENT, & CAP STATUS) ---
    fun addStudent(
        fullName: String,
        nim: String,
        email: String,
        studyProgramId: String,
        classId: String?,
        semester: Int,
        cohortYear: Int,
        status: AccountStatus = AccountStatus.ACTIVE,
        initialPassword: String = "",
        badgeStatus: StudentBadgeStatus = StudentBadgeStatus.NEUTRAL
    ): String? {
        if (_uiState.value.currentUser?.role != UserRole.ADMIN) {
            return "Akses ditolak: hanya Admin yang dapat menambahkan data mahasiswa."
        }
        val cleanNim = nim.trim()
        val cleanName = fullName.trim()
        val cleanEmail = email.trim().lowercase().ifBlank { "$cleanNim@mahasiswa.azrilclas.ac.id" }
        val resolvedPassword = initialPassword.trim().ifBlank { cleanNim }
        val prodi = _uiState.value.studyPrograms.find { it.id == studyProgramId }
            ?: return "Pilih program studi terlebih dahulu."
        if (cleanName.length < 3) return "Nama lengkap minimal 3 karakter."
        if (cleanNim.length < 5) return "NIM minimal 5 karakter."
        if (cleanNim.contains(" ")) return "NIM tidak boleh mengandung spasi."
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) return "Format email tidak valid."
        if (resolvedPassword.length < 6) return "Password awal mahasiswa minimal 6 karakter."
        if (_uiState.value.students.any { it.nim.equals(cleanNim, ignoreCase = true) }) {
            return "NIM '$cleanNim' sudah terdaftar (harus unik)."
        }
        val selectedClass = _uiState.value.classes.find { it.id == classId }
        val student = StudentRecord(
            fullName = cleanName,
            nim = cleanNim,
            email = cleanEmail,
            studyProgramId = prodi.id,
            studyProgramName = prodi.code,
            classId = selectedClass?.id,
            className = selectedClass?.name,
            semester = semester,
            cohortYear = cohortYear,
            status = status,
            badgeStatus = badgeStatus
        )
        _uiState.update { state ->
            val nextStudents = listOf(student) + state.students
            val nextClasses = state.classes.map { cls ->
                if (cls.id == selectedClass?.id) cls.copy(memberIds = cls.memberIds + student.id) else cls
            }
            state.copy(
                students = nextStudents,
                classes = nextClasses,
                toastMessage = ToastMessage(
                    "Akun mahasiswa ${student.fullName} (NIM ${student.nim}) dibuat. Mahasiswa dapat login dengan NIM & password.",
                    ToastType.SUCCESS
                )
            )
        }
        viewModelScope.launch {
            authRepository.adminCreateStudentAccount(
                studentId = student.id,
                fullName = student.fullName,
                nim = student.nim,
                email = student.email,
                studyProgramId = prodi.id,
                studyProgramCode = prodi.code,
                classId = selectedClass?.id,
                className = selectedClass?.name,
                semester = semester,
                cohortYear = cohortYear,
                accountStatus = status,
                badgeStatus = badgeStatus,
                initialPassword = resolvedPassword
            )
            authRepository.saveClasses(_uiState.value.classes)
        }
        return null
    }

    fun updateStudent(
        studentId: String,
        fullName: String,
        nim: String,
        email: String,
        studyProgramId: String,
        classId: String?,
        semester: Int,
        cohortYear: Int,
        accountStatus: AccountStatus,
        badgeStatus: StudentBadgeStatus
    ): String? {
        if (_uiState.value.currentUser?.role != UserRole.ADMIN) {
            return "Akses ditolak: hanya Admin yang dapat mengubah data mahasiswa."
        }
        val cleanName = fullName.trim()
        val cleanNim = nim.trim()
        val cleanEmail = email.trim().lowercase()
        val prodi = _uiState.value.studyPrograms.find { it.id == studyProgramId }
            ?: return "Pilih program studi terlebih dahulu."
        if (cleanName.length < 3) return "Nama lengkap minimal 3 karakter."
        if (cleanNim.length < 5) return "NIM minimal 5 karakter."
        if (cleanNim.contains(" ")) return "NIM tidak boleh mengandung spasi."
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) return "Format email tidak valid."
        if (_uiState.value.students.any { it.id != studentId && it.nim.equals(cleanNim, ignoreCase = true) }) {
            return "NIM '$cleanNim' sudah terdaftar pada mahasiswa lain."
        }
        val selectedClass = _uiState.value.classes.find { it.id == classId }

        _uiState.update { state ->
            val updatedStudents = state.students.map { stu ->
                if (stu.id == studentId) {
                    stu.copy(
                        fullName = cleanName,
                        nim = cleanNim,
                        email = cleanEmail,
                        studyProgramId = prodi.id,
                        studyProgramName = prodi.code,
                        classId = selectedClass?.id,
                        className = selectedClass?.name,
                        semester = semester,
                        cohortYear = cohortYear,
                        status = accountStatus,
                        badgeStatus = badgeStatus
                    )
                } else stu
            }
            val updatedClasses = state.classes.map { cls ->
                val members = updatedStudents.filter { it.classId == cls.id }.map { it.id }
                cls.copy(memberIds = members)
            }
            // Maintain full NIM & student-name consistency across attendance, historical attendance, and submissions
            val updatedAttendance = state.attendanceRecords.map { rec ->
                if (rec.studentId == studentId) rec.copy(studentNim = cleanNim, studentName = cleanName) else rec
            }
            val updatedHistorical = state.historicalAttendanceRecords.map { rec ->
                if (rec.studentId == studentId) rec.copy(
                    studentNim = cleanNim,
                    studentName = cleanName,
                    classId = selectedClass?.id ?: rec.classId,
                    className = selectedClass?.name ?: rec.className,
                    studyProgramCode = prodi.code
                ) else rec
            }
            val updatedSubmissions = state.submissions.map { sub ->
                if (sub.studentId == studentId) sub.copy(studentNim = cleanNim, studentName = cleanName) else sub
            }
            state.copy(
                students = updatedStudents,
                classes = updatedClasses,
                attendanceRecords = updatedAttendance,
                historicalAttendanceRecords = updatedHistorical,
                submissions = updatedSubmissions,
                toastMessage = ToastMessage("Data mahasiswa $cleanName (NIM $cleanNim) berhasil diperbarui secara konsisten.", ToastType.SUCCESS)
            )
        }
        viewModelScope.launch {
            authRepository.adminUpdateStudentAccount(
                studentId = studentId,
                fullName = cleanName,
                nim = cleanNim,
                email = cleanEmail,
                studyProgramId = prodi.id,
                studyProgramCode = prodi.code,
                classId = selectedClass?.id,
                className = selectedClass?.name,
                semester = semester,
                cohortYear = cohortYear,
                accountStatus = accountStatus,
                badgeStatus = badgeStatus
            )
            authRepository.saveClasses(_uiState.value.classes)
            authRepository.saveAttendanceRecords(_uiState.value.attendanceRecords)
            authRepository.saveHistoricalAttendanceRecords(_uiState.value.historicalAttendanceRecords)
        }
        return null
    }

    fun updateStudentBadgeStatus(studentId: String, badgeStatus: StudentBadgeStatus): String? {
        if (_uiState.value.currentUser?.role != UserRole.ADMIN) {
            return "Akses ditolak: hanya Admin yang dapat mengubah status/cap mahasiswa."
        }
        val student = _uiState.value.students.find { it.id == studentId }
            ?: return "Mahasiswa tidak ditemukan."
        _uiState.update { state ->
            state.copy(
                students = state.students.map { stu ->
                    if (stu.id == studentId) stu.copy(badgeStatus = badgeStatus) else stu
                },
                toastMessage = ToastMessage(
                    "Status mahasiswa ${student.fullName} diubah menjadi ${badgeStatus.label}.",
                    when (badgeStatus) {
                        StudentBadgeStatus.AFK -> ToastType.WARNING
                        StudentBadgeStatus.LULUS -> ToastType.SUCCESS
                        StudentBadgeStatus.NEUTRAL -> ToastType.INFO
                    }
                )
            )
        }
        viewModelScope.launch {
            authRepository.adminUpdateStudentBadgeStatus(studentId, badgeStatus)
        }
        return null
    }

    fun resetStudentPassword(
        studentId: String,
        newPassword: String,
        requirePasswordChange: Boolean = true
    ): String? {
        if (_uiState.value.currentUser?.role != UserRole.ADMIN) {
            return "Akses ditolak: hanya Admin yang dapat mengatur ulang kata sandi mahasiswa."
        }
        val cleanPw = newPassword.trim()
        if (cleanPw.length < 6) return "Kata sandi baru minimal 6 karakter."
        val student = _uiState.value.students.find { it.id == studentId }
            ?: return "Data mahasiswa tidak ditemukan."
        _uiState.update { state ->
            state.copy(
                students = state.students.map { stu ->
                    if (stu.id == studentId) stu.copy(mustChangePassword = requirePasswordChange) else stu
                }
            )
        }
        viewModelScope.launch {
            val err = authRepository.adminResetStudentPassword(studentId, cleanPw, requirePasswordChange)
            if (err != null) {
                showToast(err, ToastType.ERROR)
            } else {
                showToast(
                    "Kata sandi untuk ${student.fullName} (NIM ${student.nim}) berhasil direset. Mahasiswa wajib mengganti password saat login berikutnya.",
                    ToastType.SUCCESS
                )
            }
        }
        return null
    }

    fun deleteStudent(id: String) {
        if (_uiState.value.currentUser?.role != UserRole.ADMIN) {
            showToast("Hanya Admin yang dapat menghapus data mahasiswa.", ToastType.ERROR)
            return
        }
        _uiState.update { state ->
            state.copy(
                students = state.students.filterNot { it.id == id },
                classes = state.classes.map { cls -> cls.copy(memberIds = cls.memberIds.filterNot { m -> m == id }) },
                toastMessage = ToastMessage("Data mahasiswa dan kredensial akses dihapus.", ToastType.SUCCESS)
            )
        }
        viewModelScope.launch {
            authRepository.adminDeleteStudentAccount(id)
            authRepository.saveClasses(_uiState.value.classes)
        }
    }

    fun toggleStudentStatus(id: String) {
        if (_uiState.value.currentUser?.role != UserRole.ADMIN) return
        val target = _uiState.value.students.find { it.id == id } ?: return
        val next = if (target.status == AccountStatus.ACTIVE) AccountStatus.INACTIVE else AccountStatus.ACTIVE
        _uiState.update { state ->
            state.copy(
                students = state.students.map { stu ->
                    if (stu.id == id) stu.copy(status = next) else stu
                },
                toastMessage = ToastMessage("Status akun ${target.fullName}: ${next.label}.", ToastType.INFO)
            )
        }
        val prodiCode = resolveStudyProgramCode(target.studyProgramId, target.studyProgramName, _uiState.value.studyPrograms)
        viewModelScope.launch {
            authRepository.adminUpdateStudentAccount(
                studentId = target.id,
                fullName = target.fullName,
                nim = target.nim,
                email = target.email,
                studyProgramId = target.studyProgramId,
                studyProgramCode = prodiCode,
                classId = target.classId,
                className = target.className,
                semester = target.semester,
                cohortYear = target.cohortYear,
                accountStatus = next,
                badgeStatus = target.badgeStatus
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
                studyProgramName = prodi.code,
                classId = selectedClass?.id,
                className = selectedClass?.name,
                semester = it.semester,
                cohortYear = it.cohortYear,
                status = AccountStatus.ACTIVE,
                badgeStatus = StudentBadgeStatus.NEUTRAL
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
                    "Impor selesai: ${created.size} berhasil (password awal = NIM masing-masing), $failedCount gagal dilewati.",
                    if (failedCount == 0) ToastType.SUCCESS else ToastType.WARNING
                )
            )
        }
        viewModelScope.launch {
            created.forEach { stu ->
                authRepository.adminCreateStudentAccount(
                    studentId = stu.id,
                    fullName = stu.fullName,
                    nim = stu.nim,
                    email = stu.email,
                    studyProgramId = prodi.id,
                    studyProgramCode = prodi.code,
                    classId = stu.classId,
                    className = stu.className,
                    semester = stu.semester,
                    cohortYear = stu.cohortYear,
                    accountStatus = stu.status,
                    badgeStatus = stu.badgeStatus,
                    initialPassword = stu.nim
                )
            }
            authRepository.saveClasses(_uiState.value.classes)
        }
    }

    // --- BULK EXCEL (.XLSX) STUDENT IMPORT, COLOR FILTERING (RED=AFK, BLACK=SKIP), & HISTORICAL ATTENDANCE ---
    fun parseExcelStudentImport(
        fileName: String,
        bytes: ByteArray
    ): Result<ExcelImportPreviewSummary> {
        return ExcelStudentImportParser.parseXlsx(
            fileName = fileName,
            bytes = bytes,
            existingStudents = _uiState.value.students,
            studyPrograms = _uiState.value.studyPrograms
        )
    }

    /**
     * Commits a verified Excel import preview:
     * - Imports ONLY VALID_NORMAL and VALID_AFK rows.
     * - Strictly excludes SKIPPED_BLACK, ALREADY_EXISTS, and INVALID_OR_DUPLICATE rows.
     * - Never overwrites existing student accounts.
     * - Username = NIM, Initial Password = NIM, mustChangePassword = true.
     * - Optional historical attendance import preserves exact Excel statuses with ZERO automatic XP.
     */
    fun commitExcelStudentImport(
        preview: ExcelImportPreviewSummary,
        importHistoricalAttendance: Boolean
    ): String? {
        if (_uiState.value.currentUser?.role != UserRole.ADMIN) {
            return "Akses ditolak: hanya Admin yang dapat mengimpor data mahasiswa."
        }

        val stateSnapshot = _uiState.value
        val existingNimSet = stateSnapshot.students.map { it.nim.trim().lowercase(Locale.US) }.toMutableSet()
        val importableRows = preview.rows.filter { row ->
            row.isImportable && existingNimSet.add(row.nim.trim().lowercase(Locale.US))
        }

        if (importableRows.isEmpty()) {
            val msg = "Tidak ada mahasiswa baru yang siap diimpor (seluruh data sudah memiliki akun, blok hitam, atau tidak valid)."
            showToast(msg, ToastType.WARNING)
            return msg
        }

        // Auto-create missing classes mapped from sheet names (PJKR-3B, FARMASI-3, HES-3B, etc.)
        val updatedClasses = stateSnapshot.classes.toMutableList()
        fun ensureClassForRow(className: String, prodiId: String, prodiCode: String, semester: Int): AcademicClass {
            val existing = updatedClasses.find { it.name.equals(className, ignoreCase = true) }
            if (existing != null) return existing
            val created = AcademicClass(
                id = "cls-${className.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-")}",
                name = className,
                studyProgramId = prodiId,
                studyProgramName = prodiCode,
                semester = semester,
                academicYear = stateSnapshot.settings.activeAcademicYear,
                cohortYear = 2025,
                description = "Dibuat otomatis dari impor Excel sheet $className",
                isActive = true
            )
            updatedClasses.add(created)
            return created
        }

        val importedAtLabel = currentTimeLabel()
        val semesterKey = stateSnapshot.settings.activeSemesterKey
        val newStudents = mutableListOf<StudentRecord>()
        val newHistoricalRecords = mutableListOf<HistoricalAttendanceRecord>()
        val existingHistKeys = stateSnapshot.historicalAttendanceRecords
            .map { "${it.studentId}|${it.semesterKey}|${it.weekNumber}" }
            .toMutableSet()

        importableRows.forEach { row ->
            val prodi = stateSnapshot.studyPrograms.find { it.id == row.studyProgramId }
                ?: OfficialStudyPrograms.findByCode(row.studyProgramCode)
                ?: return@forEach
            val targetClass = ensureClassForRow(row.className, prodi.id, prodi.code, row.semester)
            val badgeStatus = if (row.validationStatus == ExcelImportValidationStatus.VALID_AFK) {
                StudentBadgeStatus.AFK
            } else {
                StudentBadgeStatus.NEUTRAL
            }
            val studentId = UUID.randomUUID().toString()
            val stu = StudentRecord(
                id = studentId,
                fullName = row.fullName,
                nim = row.nim,
                email = "${row.nim.lowercase(Locale.US)}@mahasiswa.azrilclas.ac.id",
                studyProgramId = prodi.id,
                studyProgramName = prodi.code,
                classId = targetClass.id,
                className = targetClass.name,
                semester = row.semester,
                cohortYear = 2025,
                status = AccountStatus.ACTIVE,
                badgeStatus = badgeStatus,
                mustChangePassword = true
            )
            newStudents.add(stu)

            if (importHistoricalAttendance && row.weeklyAttendance.isNotEmpty()) {
                row.weeklyAttendance.toSortedMap().forEach { (weekNum, rawStatus) ->
                    val histKey = "$studentId|$semesterKey|$weekNum"
                    if (rawStatus.isNotBlank() && existingHistKeys.add(histKey)) {
                        newHistoricalRecords.add(
                            HistoricalAttendanceRecord(
                                studentId = studentId,
                                studentNim = stu.nim,
                                studentName = stu.fullName,
                                classId = targetClass.id,
                                className = targetClass.name,
                                studyProgramCode = prodi.code,
                                weekNumber = weekNum,
                                weekLabel = "Minggu ke-$weekNum",
                                rawStatus = rawStatus.trim(),
                                sourceSheet = row.sheetName,
                                semesterKey = semesterKey,
                                importedAt = importedAtLabel,
                                xpCalculated = false
                            )
                        )
                    }
                }
            }
        }

        val mergedStudents = newStudents + stateSnapshot.students
        val syncedClasses = updatedClasses.map { cls ->
            cls.copy(memberIds = mergedStudents.filter { it.classId == cls.id }.map { it.id })
        }
        val mergedHistorical = newHistoricalRecords + stateSnapshot.historicalAttendanceRecords
        val afkImported = newStudents.count { it.badgeStatus == StudentBadgeStatus.AFK }
        val normalImported = newStudents.size - afkImported

        val summaryText = buildString {
            append("Import berhasil: ${newStudents.size} akun mahasiswa dibuat ")
            append("($normalImported Normal, $afkImported AFK Merah). ")
            if (preview.skippedBlackCount > 0) append("${preview.skippedBlackCount} blok hitam dilewati. ")
            if (importHistoricalAttendance && newHistoricalRecords.isNotEmpty()) {
                append("${newHistoricalRecords.size} data absensi historis disimpan tanpa XP otomatis.")
            }
        }

        _uiState.update { state ->
            state.copy(
                students = mergedStudents,
                classes = syncedClasses,
                historicalAttendanceRecords = mergedHistorical,
                toastMessage = ToastMessage(summaryText, ToastType.SUCCESS)
            )
        }

        viewModelScope.launch {
            authRepository.adminBulkImportStudents(
                newStudents = newStudents,
                fileName = preview.fileName,
                semesterKey = semesterKey,
                importHistoricalAttendance = importHistoricalAttendance,
                previewRowsForSync = importableRows
            )
            authRepository.saveClasses(syncedClasses)
            if (newHistoricalRecords.isNotEmpty()) {
                authRepository.saveHistoricalAttendanceRecords(mergedHistorical)
            }
        }
        return null
    }

    /**
     * Explicit Admin-confirmed calculation of XP from historical Excel attendance records.
     * Never triggered automatically during import to prevent duplicate XP.
     */
    fun calculateHistoricalAttendanceXp(
        classNameFilter: String? = null,
        countDispenAsHadir: Boolean = false,
        countKipAsHadir: Boolean = false
    ): String {
        val currentUser = _uiState.value.currentUser
        if (currentUser?.role != UserRole.ADMIN) {
            return "Akses ditolak: hanya Admin yang dapat menghitung XP historis."
        }
        val state = _uiState.value
        val candidates = state.historicalAttendanceRecords.filter { rec ->
            !rec.xpCalculated && (classNameFilter == null || rec.className.equals(classNameFilter, ignoreCase = true))
        }
        if (candidates.isEmpty()) {
            val msg = "Tidak ada data absensi historis yang belum dihitung XP-nya."
            showToast(msg, ToastType.INFO)
            return msg
        }

        val nowMillis = System.currentTimeMillis()
        val nowLabel = XpAndStreakEngine.formatWibDateTimeLabel(nowMillis)
        val existingSourceKeys = state.rankingPoints.map { "${it.studentId}|${it.sourceId}" }.toMutableSet()
        val newTxs = mutableListOf<RankingPointTransaction>()
        val processedIds = mutableSetOf<String>()

        // Track paid izin usage per student per semester (including existing attendance records)
        val izinCounter = mutableMapOf<String, Int>()
        state.attendanceRecords.filter { it.status == AttendanceStatus.IZIN }.forEach { r ->
            val key = "${r.studentId}|${r.semesterKey}"
            izinCounter[key] = (izinCounter[key] ?: 0) + 1
        }
        val maxPaidIzin = state.settings.maxPaidIzinPerSemester

        candidates.sortedWith(compareBy<HistoricalAttendanceRecord> { it.studentId }.thenBy { it.weekNumber })
            .forEach { rec ->
                processedIds.add(rec.id)
                val normalized = rec.rawStatus.trim().uppercase(Locale.US)
                val sourceId = "hist-att-${rec.semesterKey}-w${rec.weekNumber}"
                val dedupKey = "${rec.studentId}|$sourceId"
                if (existingSourceKeys.contains(dedupKey)) return@forEach

                val tx: RankingPointTransaction? = when {
                    normalized == "HADIR" || (countDispenAsHadir && normalized == "DISPEN") || (countKipAsHadir && normalized == "KIP") -> {
                        RankingPointTransaction(
                            studentId = rec.studentId,
                            sourceType = PointSourceType.ATTENDANCE,
                            sourceId = sourceId,
                            points = XpAndStreakEngine.XP_HADIR,
                            description = "Absensi Historis ${rec.weekLabel} (${rec.rawStatus} • ${rec.className})",
                            createdAt = nowLabel,
                            updatedAt = nowLabel,
                            timestampMillis = nowMillis,
                            xpCategory = XpCategory.ATTENDANCE_HADIR,
                            activityTitle = "Hadir Historis (${rec.weekLabel})",
                            semesterKey = rec.semesterKey,
                            adminId = currentUser.id,
                            adminName = currentUser.fullName
                        )
                    }
                    normalized == "IZIN" -> {
                        val izinKey = "${rec.studentId}|${rec.semesterKey}"
                        val nextCount = (izinCounter[izinKey] ?: 0) + 1
                        izinCounter[izinKey] = nextCount
                        val xp = if (nextCount <= maxPaidIzin) XpAndStreakEngine.XP_IZIN_PAID else 0
                        RankingPointTransaction(
                            studentId = rec.studentId,
                            sourceType = PointSourceType.ATTENDANCE,
                            sourceId = sourceId,
                            points = xp,
                            description = if (xp > 0) {
                                "Absensi Historis ${rec.weekLabel}: Izin ke-$nextCount ($xp XP)"
                            } else {
                                "Absensi Historis ${rec.weekLabel}: Izin ke-$nextCount (melebihi kuota $maxPaidIzin izin/semester)"
                            },
                            createdAt = nowLabel,
                            updatedAt = nowLabel,
                            timestampMillis = nowMillis,
                            xpCategory = XpCategory.ATTENDANCE_IZIN,
                            activityTitle = "Izin Historis ke-$nextCount (${rec.weekLabel})",
                            semesterKey = rec.semesterKey,
                            adminId = currentUser.id,
                            adminName = currentUser.fullName
                        )
                    }
                    else -> null // ALPHA, TIDAK HADIR, or unmapped DISPEN/KIP generate 0 XP
                }

                if (tx != null && existingSourceKeys.add(dedupKey)) {
                    newTxs.add(tx)
                }
            }

        val updatedHistorical = state.historicalAttendanceRecords.map { rec ->
            if (processedIds.contains(rec.id)) rec.copy(xpCalculated = true) else rec
        }
        val nextTxs = (newTxs + state.rankingPoints).sortedByDescending { it.timestampMillis }
        val msg = "XP historis dikonfirmasi: ${processedIds.size} catatan diproses, ${newTxs.size} transaksi XP ditambahkan."

        _uiState.update { s ->
            s.copy(
                historicalAttendanceRecords = updatedHistorical,
                rankingPoints = nextTxs,
                toastMessage = ToastMessage(msg, ToastType.SUCCESS)
            )
        }
        viewModelScope.launch {
            authRepository.saveHistoricalAttendanceRecords(updatedHistorical)
            authRepository.saveXpTransactions(nextTxs)
        }
        return msg
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
        val nowMillis = System.currentTimeMillis()
        val now = XpAndStreakEngine.formatWibDateTimeLabel(nowMillis)
        val semesterKey = _uiState.value.settings.activeSemesterKey
        val maxPaidIzin = _uiState.value.settings.maxPaidIzinPerSemester
        val record = AttendanceRecord(
            sessionId = session.id,
            meetingTitle = session.meetingTitle,
            className = session.className,
            studentId = user.id,
            studentName = user.fullName,
            studentNim = user.nim ?: "-",
            dateLabel = session.dateLabel,
            recordedAt = now,
            status = AttendanceStatus.HADIR,
            semesterKey = semesterKey,
            timestampMillis = nowMillis
        )
        _uiState.update { state ->
            val nextRecords = listOf(record) + state.attendanceRecords
            val updatedXp = XpAndStreakEngine.recalculateAttendanceXpForStudentSemester(
                studentId = user.id,
                semesterKey = semesterKey,
                attendanceRecords = nextRecords,
                existingTransactions = state.rankingPoints,
                maxPaidIzin = maxPaidIzin,
                nowMillis = nowMillis
            )
            state.copy(
                attendanceRecords = nextRecords,
                rankingPoints = updatedXp,
                toastMessage = ToastMessage(
                    "Kehadiran Anda pada ${session.meetingTitle} berhasil dicatat (+${XpAndStreakEngine.XP_HADIR} XP).",
                    ToastType.SUCCESS
                )
            )
        }
        viewModelScope.launch {
            authRepository.saveAttendanceRecords(_uiState.value.attendanceRecords)
            authRepository.saveXpTransactions(_uiState.value.rankingPoints)
        }
    }

    /**
     * Allows Admin to record attendance (Hadir / Izin / Sakit / Alfa) for any student in a session
     * while enforcing individual semester Izin XP quota (max 2 paid izins/semester) & zero duplication.
     */
    fun adminRecordStudentAttendance(
        sessionId: String,
        studentId: String,
        status: AttendanceStatus,
        note: String = ""
    ): String? {
        if (_uiState.value.currentUser?.role != UserRole.ADMIN) {
            return "Akses ditolak: hanya Admin yang dapat mencatat absensi manual."
        }
        val session = _uiState.value.attendanceSessions.find { it.id == sessionId }
            ?: return "Pilih sesi absensi terlebih dahulu."
        val student = _uiState.value.students.find { it.id == studentId }
            ?: return "Pilih mahasiswa terlebih dahulu."

        val alreadyExists = _uiState.value.attendanceRecords.any {
            it.sessionId == session.id && it.studentId == student.id
        }
        if (alreadyExists) {
            return "Catatan absensi ${student.fullName} pada sesi ini sudah ada. Gunakan tombol Koreksi untuk mengubah statusnya."
        }

        val nowMillis = System.currentTimeMillis()
        val now = XpAndStreakEngine.formatWibDateTimeLabel(nowMillis)
        val semesterKey = _uiState.value.settings.activeSemesterKey
        val maxPaidIzin = _uiState.value.settings.maxPaidIzinPerSemester
        val record = AttendanceRecord(
            sessionId = session.id,
            meetingTitle = session.meetingTitle,
            className = session.className,
            studentId = student.id,
            studentName = student.fullName,
            studentNim = student.nim,
            dateLabel = session.dateLabel,
            recordedAt = now,
            status = status,
            correctionReason = note.trim().ifBlank { null },
            semesterKey = semesterKey,
            timestampMillis = nowMillis
        )
        _uiState.update { state ->
            val nextRecords = listOf(record) + state.attendanceRecords
            val updatedXp = XpAndStreakEngine.recalculateAttendanceXpForStudentSemester(
                studentId = student.id,
                semesterKey = semesterKey,
                attendanceRecords = nextRecords,
                existingTransactions = state.rankingPoints,
                maxPaidIzin = maxPaidIzin,
                nowMillis = nowMillis
            )
            val earnedTx = updatedXp.find {
                it.studentId == student.id && it.sourceType == PointSourceType.ATTENDANCE && it.sourceId == session.id
            }
            val earnedXp = earnedTx?.points ?: 0
            val quota = XpAndStreakEngine.computeStudentIzinQuota(student.id, semesterKey, nextRecords, maxPaidIzin)
            val detailMsg = if (status == AttendanceStatus.IZIN) {
                "Izin ${student.fullName} dicatat (+$earnedXp XP • Kuota izin terpakai ${quota.usedIzinCount}/${quota.maxPaidIzin})."
            } else {
                "Absensi ${student.fullName} (${status.label}) dicatat (+$earnedXp XP)."
            }
            state.copy(
                attendanceRecords = nextRecords,
                rankingPoints = updatedXp,
                toastMessage = ToastMessage(detailMsg, ToastType.SUCCESS)
            )
        }
        viewModelScope.launch {
            authRepository.saveAttendanceRecords(_uiState.value.attendanceRecords)
            authRepository.saveXpTransactions(_uiState.value.rankingPoints)
        }
        return null
    }

    fun correctAttendance(recordId: String, newStatus: AttendanceStatus, reason: String): String? {
        if (reason.trim().length < 5) return "Alasan koreksi wajib diisi minimal 5 karakter untuk audit."
        val targetRecord = _uiState.value.attendanceRecords.find { it.id == recordId }
            ?: return "Catatan absensi tidak ditemukan."
        val adminName = _uiState.value.currentUser?.fullName ?: "Administrator"
        val nowMillis = System.currentTimeMillis()
        val now = XpAndStreakEngine.formatWibDateTimeLabel(nowMillis)
        val maxPaidIzin = _uiState.value.settings.maxPaidIzinPerSemester
        val oldTotalXp = XpAndStreakEngine.computeTotalXp(targetRecord.studentId, _uiState.value.rankingPoints)

        _uiState.update { state ->
            val nextRecords = state.attendanceRecords.map { rec ->
                if (rec.id == recordId) rec.copy(status = newStatus, correctionReason = reason.trim()) else rec
            }
            val updatedXp = XpAndStreakEngine.recalculateAttendanceXpForStudentSemester(
                studentId = targetRecord.studentId,
                semesterKey = targetRecord.semesterKey,
                attendanceRecords = nextRecords,
                existingTransactions = state.rankingPoints,
                maxPaidIzin = maxPaidIzin,
                nowMillis = nowMillis
            )
            val newTotalXp = XpAndStreakEngine.computeTotalXp(targetRecord.studentId, updatedXp)
            val auditEntry = RankingPointAuditLog(
                studentId = targetRecord.studentId,
                studentName = targetRecord.studentName,
                adminName = adminName,
                reason = "Koreksi absensi (${targetRecord.status.label} -> ${newStatus.label}): ${reason.trim()}",
                pointsBefore = oldTotalXp,
                pointsAfter = newTotalXp,
                changedAt = now
            )
            state.copy(
                attendanceRecords = nextRecords,
                rankingPoints = updatedXp,
                rankingAuditLogs = listOf(auditEntry) + state.rankingAuditLogs,
                toastMessage = ToastMessage(
                    "Koreksi absensi disimpan & XP dihitung ulang secara konsisten ($oldTotalXp -> $newTotalXp XP).",
                    ToastType.SUCCESS
                )
            )
        }
        viewModelScope.launch {
            authRepository.saveAttendanceRecords(_uiState.value.attendanceRecords)
            authRepository.saveXpTransactions(_uiState.value.rankingPoints)
        }
        return null
    }

    // --- ASSIGNMENTS (MULTI-CLASS / PRODI TARGET, 2 FILES, UNLIMITED DESCRIPTION, DEADLINE) ---
    fun addAssignment(
        title: String,
        description: String,
        targetType: AssignmentTargetType,
        targetClassIds: List<String>,
        targetStudyProgramIds: List<String>,
        courseCategory: String,
        deadlineDate: String,
        deadlineTime: String,
        allowedFormats: List<String>,
        maxFileSizeMb: Int,
        submissionMethod: SubmissionMethod,
        file1Name: String?,
        file1Url: String?,
        file2Name: String?,
        file2Url: String?
    ): String? {
        if (_uiState.value.currentUser?.role != UserRole.ADMIN) {
            return "Hanya Admin yang dapat menambahkan penugasan."
        }
        val cleanTitle = title.trim()
        val cleanDesc = description.trim()
        if (cleanTitle.length < 3) return "Judul penugasan minimal 3 karakter."
        if (cleanDesc.isEmpty()) return "Deskripsi atau panduan tugas wajib diisi."
        if (deadlineDate.trim().isEmpty() || deadlineTime.trim().isEmpty()) {
            return "Tanggal dan waktu batas akhir pengumpulan (deadline) wajib diisi."
        }
        val deadlineMillis = parseDeadlineMillis(deadlineDate.trim(), deadlineTime.trim())
            ?: return "Format deadline tidak valid. Gunakan format YYYY-MM-DD dan HH:mm (contoh: 2026-12-15 & 23:59)."
        if (allowedFormats.isEmpty()) return "Pilih minimal satu format file yang diizinkan."

        val selectedClasses = if (targetType == AssignmentTargetType.CLASS) {
            _uiState.value.classes.filter { it.id in targetClassIds }
        } else emptyList()

        val selectedProdis = if (targetType == AssignmentTargetType.STUDY_PROGRAM) {
            _uiState.value.studyPrograms.filter { it.id in targetStudyProgramIds }
        } else emptyList()

        if (targetType == AssignmentTargetType.CLASS && selectedClasses.isEmpty()) {
            return "Pilih minimal satu kelas tujuan penugasan."
        }
        if (targetType == AssignmentTargetType.STUDY_PROGRAM && selectedProdis.isEmpty()) {
            return "Pilih minimal satu program studi tujuan (HES, PJKR, atau FARMASI)."
        }

        val cleanFile1Url = file1Url?.trim()?.ifBlank { null }
        val cleanFile1Name = file1Name?.trim()?.ifBlank { null } ?: cleanFile1Url?.substringAfterLast("/")?.ifBlank { "File Tugas 1" }
        val cleanFile2Url = file2Url?.trim()?.ifBlank { null }
        val cleanFile2Name = file2Name?.trim()?.ifBlank { null } ?: cleanFile2Url?.substringAfterLast("/")?.ifBlank { "File Tugas 2" }

        val formattedDeadline = formatDeadlineDisplay(deadlineMillis, "${deadlineDate.trim()}, ${deadlineTime.trim()} WIB")
        val primaryClass = selectedClasses.firstOrNull()
        val summaryLabel = when (targetType) {
            AssignmentTargetType.CLASS -> selectedClasses.joinToString(", ") { it.name }
            AssignmentTargetType.STUDY_PROGRAM -> "Prodi ${selectedProdis.joinToString(", ") { it.code }}"
        }

        val assignment = Assignment(
            title = cleanTitle,
            description = cleanDesc,
            instructions = cleanDesc,
            classId = primaryClass?.id ?: "",
            className = summaryLabel,
            targetType = targetType,
            targetClassIds = selectedClasses.map { it.id },
            targetClassNames = selectedClasses.map { it.name },
            targetStudyProgramIds = selectedProdis.map { it.id },
            targetStudyProgramCodes = selectedProdis.map { it.code },
            courseCategory = courseCategory.trim().ifBlank { "Umum" },
            createdAt = currentTimeLabel(),
            deadline = formattedDeadline,
            deadlineEpochMillis = deadlineMillis,
            allowedFormats = allowedFormats,
            maxFileSizeMb = maxFileSizeMb.coerceIn(1, 100),
            submissionMethod = submissionMethod,
            file1Name = cleanFile1Name,
            file1Url = cleanFile1Url,
            file2Name = cleanFile2Name,
            file2Url = cleanFile2Url,
            materialAttachment = listOfNotNull(cleanFile1Name, cleanFile2Name).joinToString(" • ").ifBlank { null },
            isActive = true
        )
        val notif = AppNotification(
            targetRole = UserRole.STUDENT,
            title = "Tugas Baru: ${assignment.title}",
            body = "${assignment.targetSummaryLabel()} • Deadline ${assignment.deadline}",
            category = "Tugas",
            timestamp = currentTimeLabel()
        )
        val nextAssignments = listOf(assignment) + _uiState.value.assignments
        _uiState.update {
            it.copy(
                assignments = nextAssignments,
                notifications = listOf(notif) + it.notifications,
                toastMessage = ToastMessage("Penugasan '${assignment.title}' dipublikasikan ke ${assignment.targetSummaryLabel()}.", ToastType.SUCCESS)
            )
        }
        viewModelScope.launch { authRepository.saveAssignments(nextAssignments) }
        return null
    }

    fun toggleAssignmentActive(id: String) {
        val nextAssignments = _uiState.value.assignments.map {
            if (it.id == id) it.copy(isActive = !it.isActive) else it
        }
        _uiState.update { state ->
            state.copy(
                assignments = nextAssignments,
                toastMessage = ToastMessage("Status tugas diperbarui.", ToastType.INFO)
            )
        }
        viewModelScope.launch { authRepository.saveAssignments(nextAssignments) }
    }

    fun deleteAssignment(id: String) {
        val hasSubmissions = _uiState.value.submissions.any { it.assignmentId == id }
        if (hasSubmissions) {
            showToast("Tugas tidak dapat dihapus karena sudah memiliki pengumpulan mahasiswa.", ToastType.ERROR)
            return
        }
        val nextAssignments = _uiState.value.assignments.filterNot { it.id == id }
        _uiState.update { state ->
            state.copy(
                assignments = nextAssignments,
                toastMessage = ToastMessage("Tugas dihapus.", ToastType.SUCCESS)
            )
        }
        viewModelScope.launch { authRepository.saveAssignments(nextAssignments) }
    }

    // --- SUBMISSIONS & GRADING ---
    fun submitAssignment(
        assignmentId: String,
        driveLink: String?,
        uploadedFileName: String?,
        uploadedFileSizeLabel: String?,
        isLate: Boolean? = null
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
        val nowMillis = System.currentTimeMillis()
        val timeliness = when (isLate) {
            true -> SubmissionTimeliness.LATE
            false -> SubmissionTimeliness.ON_TIME
            null -> XpAndStreakEngine.determineSubmissionTimeliness(assignment, nowMillis)
        }
        val effectiveIsLate = timeliness == SubmissionTimeliness.LATE
        val now = XpAndStreakEngine.formatWibDateTimeLabel(nowMillis)
        val semesterKey = _uiState.value.settings.activeSemesterKey
        val existing = _uiState.value.submissions.find { it.assignmentId == assignmentId && it.studentId == user.id }
        val submissionStatus = if (effectiveIsLate) SubmissionStatus.LATE else SubmissionStatus.AWAITING_ADMIN
        val earnedXp = timeliness.xpAmount
        val record = SubmissionRecord(
            id = existing?.id ?: UUID.randomUUID().toString(),
            assignmentId = assignment.id,
            assignmentTitle = assignment.title,
            className = assignment.targetSummaryLabel(),
            studentId = user.id,
            studentName = user.fullName,
            studentNim = user.nim ?: "-",
            driveLink = cleanDrive,
            uploadedFileName = cleanFile,
            uploadedFileSizeLabel = uploadedFileSizeLabel,
            submittedAt = now,
            status = submissionStatus,
            isLate = effectiveIsLate,
            revisionCount = if (existing != null) existing.revisionCount + 1 else 0
        )
        val adminNotif = AppNotification(
            targetRole = UserRole.ADMIN,
            title = if (effectiveIsLate) "Pengumpulan Terlambat: ${assignment.title}" else "Pengumpulan Baru: ${assignment.title}",
            body = "${user.fullName} mengumpulkan tugas (${cleanFile ?: "Google Drive"}).",
            category = "Pengumpulan",
            timestamp = now
        )
        _uiState.update { state ->
            val filtered = state.submissions.filterNot { it.id == record.id }
            val updatedXp = XpAndStreakEngine.upsertAssignmentSubmissionXp(
                existingTransactions = state.rankingPoints,
                studentId = user.id,
                assignment = assignment,
                timeliness = timeliness,
                semesterKey = semesterKey,
                submittedAtMillis = nowMillis
            )
            state.copy(
                submissions = listOf(record) + filtered,
                rankingPoints = updatedXp,
                notifications = listOf(adminNotif) + state.notifications,
                toastMessage = ToastMessage(
                    if (existing != null) "Revisi tugas '${assignment.title}' disimpan (XP diperbarui tanpa duplikasi: +$earnedXp XP)."
                    else "Tugas '${assignment.title}' berhasil dikirim (${timeliness.label}: +$earnedXp XP).",
                    ToastType.SUCCESS
                )
            )
        }
        viewModelScope.launch {
            authRepository.saveXpTransactions(_uiState.value.rankingPoints)
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

    // --- GAMIFICATION: LOGIN STREAK (WIB), PARTICIPATION XP, IZIN QUOTA & STREAK REMINDERS ---
    fun recordStudentDailyLogin(studentId: String, nowMillis: Long = System.currentTimeMillis()) {
        val state = _uiState.value
        val result = XpAndStreakEngine.recordStudentDailyLogin(
            studentId = studentId,
            existingStreak = state.studentStreaks[studentId],
            existingTransactions = state.rankingPoints,
            semesterKey = state.settings.activeSemesterKey,
            nowMillis = nowMillis
        )
        val nextStreaks = state.studentStreaks + (studentId to result.updatedStreak)
        val bonusNotif = if (result.awardedMilestoneDays != null && result.awardedBonusXp > 0) {
            AppNotification(
                targetRole = UserRole.STUDENT,
                title = "🔥 Bonus Login Streak ${result.awardedMilestoneDays} Hari!",
                body = "Selamat! Anda memperoleh +${result.awardedBonusXp} XP atas konsistensi login ${result.awardedMilestoneDays} hari berturut-turut.",
                category = "Login Streak",
                timestamp = XpAndStreakEngine.formatWibDateTimeLabel(nowMillis)
            )
        } else null

        _uiState.update { current ->
            current.copy(
                studentStreaks = nextStreaks,
                rankingPoints = result.updatedTransactions,
                notifications = if (bonusNotif != null) listOf(bonusNotif) + current.notifications else current.notifications,
                toastMessage = if (result.awardedMilestoneDays != null) {
                    ToastMessage(
                        "🔥 Streak ${result.awardedMilestoneDays} hari tercapai! Bonus +${result.awardedBonusXp} XP ditambahkan.",
                        ToastType.SUCCESS
                    )
                } else current.toastMessage
            )
        }
        viewModelScope.launch {
            authRepository.saveStudentStreaks(nextStreaks)
            authRepository.saveXpTransactions(result.updatedTransactions)
            authRepository.syncLoginStreakToSupabaseIfConnected()
        }
    }

    /**
     * Admin manual Participation XP award (ONLY Regular +10 XP or Critical +20 XP).
     */
    fun awardParticipationXp(
        studentId: String,
        category: ParticipationXpCategory,
        reasonNote: String
    ): String? {
        val currentUser = _uiState.value.currentUser
        if (currentUser?.role != UserRole.ADMIN) {
            return "Akses ditolak: hanya Admin yang dapat memberikan XP keaktifan mahasiswa."
        }
        val student = _uiState.value.students.find { it.id == studentId }
            ?: return "Pilih mahasiswa tujuan terlebih dahulu."

        val nowMillis = System.currentTimeMillis()
        val nowLabel = XpAndStreakEngine.formatWibDateTimeLabel(nowMillis)
        val semesterKey = _uiState.value.settings.activeSemesterKey
        val beforeTotal = XpAndStreakEngine.computeTotalXp(student.id, _uiState.value.rankingPoints)
        val tx = XpAndStreakEngine.createParticipationXpTransaction(
            studentId = student.id,
            category = category,
            reasonNote = reasonNote,
            adminId = currentUser.id,
            adminName = currentUser.fullName,
            semesterKey = semesterKey,
            nowMillis = nowMillis
        )
        val afterTotal = beforeTotal + category.xpAmount
        val audit = RankingPointAuditLog(
            studentId = student.id,
            studentName = student.fullName,
            adminName = currentUser.fullName,
            reason = "${category.label} (+${category.xpAmount} XP): ${tx.description}",
            pointsBefore = beforeTotal,
            pointsAfter = afterTotal,
            changedAt = nowLabel
        )
        val studentNotif = AppNotification(
            targetRole = UserRole.STUDENT,
            title = "+${category.xpAmount} XP Keaktifan: ${category.label}",
            body = "${tx.description} • Diberikan oleh ${currentUser.fullName}",
            category = "XP Keaktifan",
            timestamp = nowLabel
        )
        val nextTxs = (listOf(tx) + _uiState.value.rankingPoints).sortedByDescending { it.timestampMillis }
        _uiState.update { state ->
            state.copy(
                rankingPoints = nextTxs,
                rankingAuditLogs = listOf(audit) + state.rankingAuditLogs,
                notifications = listOf(studentNotif) + state.notifications,
                toastMessage = ToastMessage(
                    "+${category.xpAmount} XP (${category.label}) berhasil diberikan kepada ${student.fullName}.",
                    ToastType.SUCCESS
                )
            )
        }
        viewModelScope.launch {
            authRepository.saveXpTransactions(nextTxs)
            authRepository.syncParticipationXpToSupabaseIfConnected(
                studentId = student.id,
                category = category,
                note = tx.description,
                semesterKey = semesterKey
            )
        }
        return null
    }

    fun evaluateStreakRemindersForInactiveStudents(nowMillis: Long = System.currentTimeMillis()) {
        val state = _uiState.value
        if (!state.settings.streakRemindersEnabled) return

        var rollingLogs = state.streakNotificationLogs
        val allNewLogs = mutableListOf<StreakNotificationLog>()
        state.students.filter { it.status == AccountStatus.ACTIVE }.forEach { stu ->
            val res = XpAndStreakEngine.evaluateDueStreakReminders(
                studentId = stu.id,
                streak = state.studentStreaks[stu.id],
                existingLogs = rollingLogs,
                nowMillis = nowMillis
            )
            if (res.newlyDispatched.isNotEmpty()) {
                allNewLogs.addAll(res.newlyDispatched)
                rollingLogs = res.updatedLogs
            }
        }
        if (allNewLogs.isEmpty()) return

        val appNotifs = allNewLogs.map { log ->
            StreakReminderNotifier.postReminderNotification(getApplication(), log)
            AppNotification(
                targetRole = UserRole.STUDENT,
                title = "${log.slot.title} (${log.slot.timeLabel})",
                body = log.message,
                category = "Pengingat Streak",
                timestamp = log.sentAtLabel
            )
        }
        _uiState.update { current ->
            current.copy(
                streakNotificationLogs = rollingLogs,
                notifications = appNotifs + current.notifications
            )
        }
        viewModelScope.launch {
            authRepository.saveStreakNotificationLogs(rollingLogs)
        }
    }

    fun getStudentIzinQuota(studentId: String): StudentIzinQuotaSummary {
        val s = _uiState.value
        return XpAndStreakEngine.computeStudentIzinQuota(
            studentId = studentId,
            semesterKey = s.settings.activeSemesterKey,
            attendanceRecords = s.attendanceRecords,
            maxPaidIzin = s.settings.maxPaidIzinPerSemester
        )
    }

    fun getAuthorizedXpHistory(
        targetStudentId: String,
        categoryFilter: PointSourceType? = null,
        timeRange: XpHistoryTimeRange = XpHistoryTimeRange.ALL
    ): List<RankingPointTransaction> {
        val s = _uiState.value
        return XpAndStreakEngine.getAuthorizedXpHistory(
            requester = s.currentUser,
            targetStudentId = targetStudentId,
            allTransactions = s.rankingPoints,
            categoryFilter = categoryFilter,
            timeRange = timeRange,
            activeSemesterKey = s.settings.activeSemesterKey
        )
    }

    fun adjustStudentPoints(
        studentId: String,
        studentName: String,
        deltaPoints: Int,
        reason: String
    ): String? {
        val currentUser = _uiState.value.currentUser
        if (currentUser?.role != UserRole.ADMIN) {
            return "Akses ditolak: hanya Admin yang dapat melakukan penyesuaian XP."
        }
        if (deltaPoints == 0) return "Perubahan XP tidak boleh 0."
        if (reason.trim().length < 5) return "Alasan penyesuaian wajib diisi minimal 5 karakter untuk audit."

        val nowMillis = System.currentTimeMillis()
        val now = XpAndStreakEngine.formatWibDateTimeLabel(nowMillis)
        val semesterKey = _uiState.value.settings.activeSemesterKey
        val beforeTotal = XpAndStreakEngine.computeTotalXp(studentId, _uiState.value.rankingPoints)
        val afterTotal = (beforeTotal + deltaPoints).coerceAtLeast(0)
        val effectiveDelta = afterTotal - beforeTotal

        val tx = RankingPointTransaction(
            studentId = studentId,
            sourceType = PointSourceType.MANUAL_ADJUSTMENT,
            sourceId = "manual-${UUID.randomUUID()}",
            points = effectiveDelta,
            description = "Penyesuaian Admin: ${reason.trim()}",
            createdAt = now,
            updatedAt = now,
            timestampMillis = nowMillis,
            xpCategory = XpCategory.MANUAL_ADJUSTMENT,
            activityTitle = "Penyesuaian XP Admin",
            semesterKey = semesterKey,
            adminId = currentUser.id,
            adminName = currentUser.fullName
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
        val nextTxs = (listOf(tx) + _uiState.value.rankingPoints).sortedByDescending { it.timestampMillis }
        _uiState.update { state ->
            state.copy(
                rankingPoints = nextTxs,
                rankingAuditLogs = listOf(audit) + state.rankingAuditLogs,
                toastMessage = ToastMessage(
                    "Penyesuaian XP $studentName disimpan ($beforeTotal -> $afterTotal XP) dengan log audit.",
                    ToastType.SUCCESS
                )
            )
        }
        viewModelScope.launch {
            authRepository.saveXpTransactions(nextTxs)
        }
        return null
    }

    companion object {
        fun parseDeadlineMillis(dateStr: String, timeStr: String): Long? {
            val combined = "${dateStr.trim()} ${timeStr.trim()}"
            val patterns = listOf("yyyy-MM-dd HH:mm", "dd-MM-yyyy HH:mm", "dd/MM/yyyy HH:mm")
            for (pattern in patterns) {
                val parsed = runCatching {
                    SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(combined)?.time
                }.getOrNull()
                if (parsed != null) return parsed
            }
            return null
        }

        fun formatDeadlineDisplay(epochMillis: Long, fallback: String): String =
            runCatching {
                SimpleDateFormat("dd MMM yyyy, HH:mm 'WIB'", Locale.forLanguageTag("id-ID")).format(Date(epochMillis))
            }.getOrDefault(fallback)

        fun resolveStudyProgramCode(
            studyProgramId: String?,
            studyProgramName: String?,
            studyPrograms: List<StudyProgram> = OfficialStudyPrograms.list
        ): String {
            val byId = studyProgramId?.let { id -> studyPrograms.find { it.id == id } }
            if (byId != null) return byId.code
            val byNameOrCode = studyProgramName?.let { name ->
                studyPrograms.find {
                    it.code.equals(name.trim(), ignoreCase = true) ||
                        it.name.equals(name.trim(), ignoreCase = true)
                }
            }
            return byNameOrCode?.code ?: studyProgramName?.trim()?.uppercase()?.ifBlank { "PRODI" } ?: "PRODI"
        }

        /**
         * Determines whether an assignment should be visible to a specific student.
         * - CLASS target: student's classId MUST be in assignment.targetClassIds (or legacy classId).
         * - STUDY_PROGRAM target: student's studyProgramId OR studyProgramCode MUST match assignment's targeted prodi(s).
         */
        fun isAssignmentForStudent(
            assignment: Assignment,
            user: UserProfile?,
            studentRecord: StudentRecord?
        ): Boolean {
            if (!assignment.isActive) return false
            val studentClassId = studentRecord?.classId ?: user?.classId
            val studentProdiId = studentRecord?.studyProgramId ?: user?.studyProgramId
            val studentProdiCode = resolveStudyProgramCode(
                studentProdiId,
                studentRecord?.studyProgramName ?: user?.studyProgramName
            )
            return when (assignment.targetType) {
                AssignmentTargetType.CLASS -> {
                    val targets = assignment.targetClassIds.ifEmpty {
                        listOfNotNull(assignment.classId.ifBlank { null })
                    }
                    studentClassId != null && targets.any { it == studentClassId }
                }
                AssignmentTargetType.STUDY_PROGRAM -> {
                    val matchById = studentProdiId != null && assignment.targetStudyProgramIds.any { it == studentProdiId }
                    val matchByCode = assignment.targetStudyProgramCodes.any { it.equals(studentProdiCode, ignoreCase = true) }
                    matchById || matchByCode
                }
            }
        }

        private fun buildStudentPool(state: AzrilUiState): Collection<StudentRecord> {
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
                        studyProgramId = activeUser.studyProgramId ?: "prodi-hes",
                        studyProgramName = activeUser.studyProgramName ?: "HES",
                        classId = activeUser.classId,
                        className = activeUser.className ?: "Belum ada kelas",
                        semester = activeUser.semester ?: 1,
                        cohortYear = activeUser.cohortYear ?: 2026,
                        status = activeUser.status,
                        badgeStatus = activeUser.badgeStatus
                    )
                }
            }
            return registeredMap.values
        }

        private fun buildRankingEntry(
            stu: StudentRecord,
            state: AzrilUiState,
            periodTransactions: List<RankingPointTransaction>
        ): StudentRankingEntry {
            val stuTx = periodTransactions.filter { it.studentId == stu.id }
            val totalPoints = stuTx.sumOf { it.points }.coerceAtLeast(0)

            val stuAttendances = state.attendanceRecords.filter { it.studentId == stu.id }
            val hadirCount = stuAttendances.count { it.status == AttendanceStatus.HADIR }
            val izinCount = stuAttendances.count { it.status == AttendanceStatus.IZIN }
            val sakitCount = stuAttendances.count { it.status == AttendanceStatus.SAKIT }
            val alfaCount = stuAttendances.count { it.status == AttendanceStatus.ALFA }

            val relevantAssignments = state.assignments.filter { a ->
                isAssignmentForStudent(a, null, stu)
            }
            val stuSubs = state.submissions.filter { it.studentId == stu.id }
            val onTimeCount = stuSubs.count { !it.isLate && it.status != SubmissionStatus.LATE && it.status != SubmissionStatus.NOT_SUBMITTED }
            val lateCount = stuSubs.count { it.isLate || it.status == SubmissionStatus.LATE }
            val missingCount = relevantAssignments.count { a -> stuSubs.none { it.assignmentId == a.id } }
            val prodiCode = resolveStudyProgramCode(stu.studyProgramId, stu.studyProgramName, state.studyPrograms)

            return StudentRankingEntry(
                rank = 0,
                studentId = stu.id,
                studentName = stu.fullName,
                nim = stu.nim,
                studyProgramId = stu.studyProgramId,
                studyProgramName = stu.studyProgramName,
                studyProgramCode = prodiCode,
                classId = stu.classId,
                className = stu.className ?: "Belum ada kelas",
                semester = stu.semester,
                badgeStatus = stu.badgeStatus,
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

        /**
         * Computes the active student leaderboard (Lulus & Netral ONLY).
         * Students marked as AFK are strictly excluded here and returned via [computeAfkStudents].
         */
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

            val filteredStudents = buildStudentPool(state).filter { stu ->
                stu.badgeStatus != StudentBadgeStatus.AFK &&
                    (studyProgramId == null || stu.studyProgramId == studyProgramId) &&
                    (classId == null || stu.classId == classId) &&
                    (semester == null || stu.semester == semester)
            }

            val periodTransactions = state.rankingPoints.filter { it.timestampMillis >= minTimestamp }
            val rawEntries = filteredStudents.map { stu -> buildRankingEntry(stu, state, periodTransactions) }

            val sorted = rawEntries.sortedWith(
                compareByDescending<StudentRankingEntry> { it.totalPoints }
                    .thenByDescending { it.onTimeAssignmentsCount }
                    .thenByDescending { it.hadirCount }
                    .thenBy { it.alfaCount }
                    .thenBy { it.studentName }
            )

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

        /**
         * Returns AFK students in a dedicated separate list (never mixed into active rankings).
         */
        fun computeAfkStudents(
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
            val periodTransactions = state.rankingPoints.filter { it.timestampMillis >= minTimestamp }
            return buildStudentPool(state)
                .filter { stu ->
                    stu.badgeStatus == StudentBadgeStatus.AFK &&
                        (studyProgramId == null || stu.studyProgramId == studyProgramId) &&
                        (classId == null || stu.classId == classId) &&
                        (semester == null || stu.semester == semester)
                }
                .map { stu -> buildRankingEntry(stu, state, periodTransactions) }
                .sortedBy { it.studentName }
        }
    }
}
