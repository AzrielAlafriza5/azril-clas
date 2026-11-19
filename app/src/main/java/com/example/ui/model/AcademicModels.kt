package com.example.ui.model

import java.util.UUID

enum class UserRole(val label: String) {
    ADMIN("Admin"),
    STUDENT("Mahasiswa")
}

enum class AccountStatus(val label: String) {
    ACTIVE("Aktif"),
    PENDING_ACTIVATION("Menunggu Aktivasi"),
    INACTIVE("Nonaktif")
}

enum class StudentBadgeStatus(val label: String, val dbValue: String) {
    AFK("AFK", "afk"),
    NEUTRAL("Netral", "neutral"),
    LULUS("Lulus", "lulus");

    companion object {
        fun fromDbValue(value: String?): StudentBadgeStatus = when (value?.trim()?.lowercase()) {
            "afk" -> AFK
            "lulus" -> LULUS
            else -> NEUTRAL
        }
    }
}

enum class AssignmentTargetType(val label: String, val dbValue: String) {
    CLASS("Berdasarkan Kelas", "class"),
    STUDY_PROGRAM("Berdasarkan Program Studi", "study_program");

    companion object {
        fun fromDbValue(value: String?): AssignmentTargetType = when (value?.trim()?.lowercase()) {
            "study_program", "prodi" -> STUDY_PROGRAM
            else -> CLASS
        }
    }
}

enum class LearningMethod(val label: String) {
    DARING("Daring"),
    LURING("Luring")
}

enum class AttendanceStatus(val label: String) {
    HADIR("Hadir"),
    IZIN("Izin"),
    SAKIT("Sakit"),
    ALFA("Alfa")
}

enum class AttendanceSessionState(val label: String) {
    UPCOMING("Belum Dibuka"),
    OPEN("Sedang Dibuka"),
    CLOSED("Ditutup")
}

enum class SubmissionMethod(val label: String) {
    GOOGLE_DRIVE("Google Drive"),
    DIRECT_UPLOAD("Upload File"),
    BOTH("Drive & Upload")
}

enum class SubmissionStatus(val label: String) {
    NOT_SUBMITTED("Belum Mengumpulkan"),
    SUBMITTED("Sudah Mengumpulkan"),
    LATE("Terlambat"),
    AI_CHECKING("Sedang Diperiksa AI"),
    AWAITING_ADMIN("Menunggu Pemeriksaan Admin"),
    NEEDS_REVISION("Perlu Revisi"),
    GRADED("Selesai Dinilai")
}

/**
 * Unified 3-state task completion model strictly separating submission from admin verification:
 * - NOT_SUBMITTED: student has not submitted and admin has not verified
 * - AWAITING_VERIFICATION: student has submitted, waiting for admin checklist verification
 * - COMPLETED: admin has checked/verified completion (never auto-set by submission alone)
 */
enum class TaskCompletionStatus(val label: String) {
    NOT_SUBMITTED("Belum Mengumpulkan"),
    AWAITING_VERIFICATION("Menunggu Verifikasi"),
    COMPLETED("Selesai")
}

data class AssignmentChecklistRecord(
    val assignmentId: String,
    val studentId: String,
    val isCompleted: Boolean = false,
    val verifiedById: String? = null,
    val verifiedByName: String? = null,
    val verifiedAt: String? = null,
    val verificationNote: String? = null,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val compositeKey: String
        get() = "$assignmentId::$studentId"
}

fun resolveTaskCompletionStatus(
    submission: SubmissionRecord?,
    checklist: AssignmentChecklistRecord?
): TaskCompletionStatus = when {
    checklist?.isCompleted == true -> TaskCompletionStatus.COMPLETED
    submission != null -> TaskCompletionStatus.AWAITING_VERIFICATION
    else -> TaskCompletionStatus.NOT_SUBMITTED
}

enum class AiReviewStatus(val label: String) {
    CONFORMING("Sesuai"),
    NEEDS_REVISION("Perlu Revisi"),
    INCOMPLETE("Tidak Lengkap"),
    FAILED("Gagal Diperiksa"),
    PENDING("Menunggu Pemeriksaan")
}

enum class ToastType {
    SUCCESS, INFO, WARNING, ERROR
}

data class ToastMessage(
    val message: String,
    val type: ToastType = ToastType.INFO,
    val id: String = UUID.randomUUID().toString()
)

data class UserProfile(
    val id: String,
    val fullName: String,
    val email: String,
    val username: String? = null,
    val role: UserRole,
    val nim: String? = null,
    val studyProgramId: String? = null,
    val studyProgramName: String? = null,
    val classId: String? = null,
    val className: String? = null,
    val semester: Int? = null,
    val cohortYear: Int? = null,
    val status: AccountStatus = AccountStatus.ACTIVE,
    val badgeStatus: StudentBadgeStatus = StudentBadgeStatus.NEUTRAL,
    val mustChangePassword: Boolean = false,
    val createdAt: String? = null
)

data class StudyProgram(
    val id: String = UUID.randomUUID().toString(),
    val code: String,
    val name: String,
    val faculty: String,
    val description: String,
    val isActive: Boolean = true
)

data class AcademicClass(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val studyProgramId: String,
    val studyProgramName: String,
    val semester: Int,
    val academicYear: String,
    val cohortYear: Int,
    val description: String,
    val isActive: Boolean = true,
    val memberIds: List<String> = emptyList()
)

data class StudentRecord(
    val id: String = UUID.randomUUID().toString(),
    val fullName: String,
    val nim: String,
    val email: String,
    val studyProgramId: String,
    val studyProgramName: String,
    val classId: String?,
    val className: String?,
    val semester: Int,
    val cohortYear: Int,
    val status: AccountStatus = AccountStatus.ACTIVE,
    val badgeStatus: StudentBadgeStatus = StudentBadgeStatus.NEUTRAL,
    val mustChangePassword: Boolean = false
)

data class ClassSchedule(
    val id: String = UUID.randomUUID().toString(),
    val studyProgramName: String,
    val classId: String,
    val className: String,
    val courseTitle: String,
    val weekLabel: String,
    val dateLabel: String,
    val startTime: String,
    val endTime: String,
    val lecturer: String,
    val method: LearningMethod,
    val meetingUrl: String? = null,
    val roomLocation: String? = null,
    val materialTitle: String? = null,
    val notes: String = ""
)

data class AttendanceSession(
    val id: String = UUID.randomUUID().toString(),
    val classId: String,
    val className: String,
    val meetingTitle: String,
    val method: LearningMethod,
    val dateLabel: String,
    val openTime: String,
    val closeTime: String,
    val state: AttendanceSessionState,
    val notes: String = "",
    val qrTokenPreview: String? = null
)

data class AttendanceRecord(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val meetingTitle: String,
    val className: String,
    val studentId: String,
    val studentName: String,
    val studentNim: String,
    val dateLabel: String,
    val recordedAt: String,
    val status: AttendanceStatus,
    val correctionReason: String? = null,
    val semesterKey: String = "2026/2027-Ganjil",
    val timestampMillis: Long = System.currentTimeMillis()
)

data class Assignment(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val instructions: String,
    val classId: String = "",
    val className: String = "",
    val targetType: AssignmentTargetType = AssignmentTargetType.CLASS,
    val targetClassIds: List<String> = listOfNotNull(classId.ifBlank { null }),
    val targetClassNames: List<String> = listOfNotNull(className.ifBlank { null }),
    val targetStudyProgramIds: List<String> = emptyList(),
    val targetStudyProgramCodes: List<String> = emptyList(),
    val courseCategory: String = "Umum",
    val createdAt: String,
    val deadline: String,
    val deadlineEpochMillis: Long? = null,
    val allowedFormats: List<String> = listOf("PDF", "DOCX"),
    val maxFileSizeMb: Int = 10,
    val submissionMethod: SubmissionMethod = SubmissionMethod.BOTH,
    val file1Name: String? = null,
    val file1Url: String? = null,
    val file2Name: String? = null,
    val file2Url: String? = null,
    val materialAttachment: String? = null,
    val isActive: Boolean = true
) {
    fun targetSummaryLabel(): String = when (targetType) {
        AssignmentTargetType.CLASS -> {
            val names = targetClassNames.ifEmpty { listOfNotNull(className.ifBlank { null }) }
            if (names.isEmpty()) "Target Kelas" else "Kelas: ${names.joinToString(", ")}"
        }
        AssignmentTargetType.STUDY_PROGRAM -> {
            if (targetStudyProgramCodes.isEmpty()) "Target Prodi"
            else "Prodi: ${targetStudyProgramCodes.joinToString(", ")}"
        }
    }

    fun isDeadlinePassed(nowMillis: Long = System.currentTimeMillis()): Boolean =
        deadlineEpochMillis != null && nowMillis > deadlineEpochMillis
}

data class AiReviewReport(
    val id: String = UUID.randomUUID().toString(),
    val submissionId: String,
    val studentName: String,
    val assignmentTitle: String,
    val fileStatus: String,
    val completenessStatus: String,
    val matchPercentage: Int?,
    val contentSummary: String,
    val compliantSections: List<String>,
    val missingSections: List<String>,
    val recommendation: String,
    val checkedAt: String,
    val status: AiReviewStatus
)

data class GradeAuditEntry(
    val changedAt: String,
    val previousScore: Int?,
    val newScore: Int,
    val note: String
)

data class SubmissionRecord(
    val id: String = UUID.randomUUID().toString(),
    val assignmentId: String,
    val assignmentTitle: String,
    val className: String,
    val studentId: String,
    val studentName: String,
    val studentNim: String,
    val answerText: String? = null,
    val driveLink: String? = null,
    val uploadedFileName: String? = null,
    val uploadedFileUrl: String? = null,
    val uploadedFileSizeLabel: String? = null,
    val submittedAt: String,
    val firstSubmittedAt: String = submittedAt,
    val lastUpdatedAt: String = submittedAt,
    val firstSubmittedAtMillis: Long = 0L,
    val lastUpdatedAtMillis: Long = 0L,
    val status: SubmissionStatus,
    val isLate: Boolean = false,
    val revisionCount: Int = 0,
    val aiReview: AiReviewReport? = null,
    val finalScore: Int? = null,
    val adminFeedback: String? = null,
    val gradeHistory: List<GradeAuditEntry> = emptyList()
)

data class AppNotification(
    val id: String = UUID.randomUUID().toString(),
    val targetRole: UserRole,
    val title: String,
    val body: String,
    val category: String,
    val timestamp: String,
    val isRead: Boolean = false
)

data class AppSettingsState(
    val institutionName: String = "Azril Clas Academic Portal",
    val activeAcademicYear: String = "2026/2027",
    val activeSemester: String = "Ganjil",
    val enableAiInitialScore: Boolean = true,
    val defaultAttendanceWindowMinutes: Int = 45,
    val requireQrRotationMinutes: Int = 5,
    val allowGlobalStudentRanking: Boolean = true,
    val maxPaidIzinPerSemester: Int = 2,
    val streakRemindersEnabled: Boolean = true
) {
    val activeSemesterKey: String
        get() = "${activeAcademicYear.trim()}-${activeSemester.trim()}"
}

enum class PointSourceType(val label: String) {
    ATTENDANCE("Absensi"),
    ASSIGNMENT("Pengumpulan Tugas"),
    PARTICIPATION("Keaktifan"),
    STREAK_BONUS("Bonus Streak"),
    MANUAL_ADJUSTMENT("Penyesuaian Admin")
}

enum class XpCategory(
    val groupLabel: String,
    val defaultTitle: String,
    val sourceType: PointSourceType
) {
    ATTENDANCE_HADIR("Absensi", "Hadir dalam pembelajaran", PointSourceType.ATTENDANCE),
    ATTENDANCE_IZIN("Absensi", "Izin", PointSourceType.ATTENDANCE),
    ATTENDANCE_ALFA("Absensi", "Alfa", PointSourceType.ATTENDANCE),
    ASSIGNMENT_EARLY("Pengumpulan Tugas", "Mengumpulkan tugas sebelum deadline", PointSourceType.ASSIGNMENT),
    ASSIGNMENT_ON_TIME("Pengumpulan Tugas", "Mengumpulkan tugas tepat waktu", PointSourceType.ASSIGNMENT),
    ASSIGNMENT_LATE("Pengumpulan Tugas", "Mengumpulkan tugas terlambat", PointSourceType.ASSIGNMENT),
    PARTICIPATION_REGULAR("Keaktifan", "Bertanya / Berpendapat Biasa", PointSourceType.PARTICIPATION),
    PARTICIPATION_CRITICAL("Keaktifan", "Bertanya / Berpendapat Kritis", PointSourceType.PARTICIPATION),
    STREAK_BONUS("Bonus Streak", "Bonus Login Streak", PointSourceType.STREAK_BONUS),
    MANUAL_ADJUSTMENT("Penyesuaian Admin", "Penyesuaian XP Admin", PointSourceType.MANUAL_ADJUSTMENT);

    companion object {
        fun fromSourceType(type: PointSourceType): XpCategory = when (type) {
            PointSourceType.ATTENDANCE -> ATTENDANCE_HADIR
            PointSourceType.ASSIGNMENT -> ASSIGNMENT_ON_TIME
            PointSourceType.PARTICIPATION -> PARTICIPATION_REGULAR
            PointSourceType.STREAK_BONUS -> STREAK_BONUS
            PointSourceType.MANUAL_ADJUSTMENT -> MANUAL_ADJUSTMENT
        }
    }
}

enum class ParticipationXpCategory(
    val label: String,
    val xpAmount: Int,
    val xpCategory: XpCategory,
    val description: String
) {
    REGULAR(
        label = "Bertanya / Berpendapat Biasa",
        xpAmount = 10,
        xpCategory = XpCategory.PARTICIPATION_REGULAR,
        description = "Untuk pertanyaan umum, pendapat biasa, atau partisipasi diskusi."
    ),
    CRITICAL(
        label = "Bertanya / Berpendapat Kritis",
        xpAmount = 20,
        xpCategory = XpCategory.PARTICIPATION_CRITICAL,
        description = "Untuk pertanyaan mendalam, analisis, argumentasi, atau pendapat kritis yang relevan dengan materi."
    )
}

enum class SubmissionTimeliness(
    val label: String,
    val xpAmount: Int,
    val xpCategory: XpCategory
) {
    BEFORE_DEADLINE("Sebelum Deadline", 35, XpCategory.ASSIGNMENT_EARLY),
    ON_TIME("Tepat Waktu", 30, XpCategory.ASSIGNMENT_ON_TIME),
    LATE("Terlambat", 10, XpCategory.ASSIGNMENT_LATE)
}

enum class StreakReminderSlot(
    val hourWib: Int,
    val timeLabel: String,
    val title: String,
    val message: String
) {
    MORNING_08(
        hourWib = 8,
        timeLabel = "08.00 WIB",
        title = "Pengingat Streak Pagi",
        message = "Selamat pagi! 🔥 Jangan lupa nyalakan streak Azril Clas hari ini."
    ),
    NOON_12(
        hourWib = 12,
        timeLabel = "12.00 WIB",
        title = "Pengingat Streak Siang",
        message = "Sudah login hari ini? Pertahankan konsistensi belajarmu!"
    ),
    EVENING_17(
        hourWib = 17,
        timeLabel = "17.00 WIB",
        title = "Pengingat Streak Sore",
        message = "Jangan sampai streak-mu terputus. Login sekarang!"
    )
}

data class StudentStreakState(
    val studentId: String,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastLoginDateWib: String? = null,
    val streakCycleId: String = UUID.randomUUID().toString(),
    val loginDatesWib: List<String> = emptyList(),
    val claimedMilestonesInCycle: List<Int> = emptyList()
)

data class StreakNotificationLog(
    val id: String = UUID.randomUUID().toString(),
    val studentId: String,
    val reminderDateWib: String,
    val slot: StreakReminderSlot,
    val message: String,
    val sentAtLabel: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class StudentIzinQuotaSummary(
    val studentId: String,
    val semesterKey: String,
    val usedIzinCount: Int,
    val paidIzinUsed: Int,
    val remainingPaidIzin: Int,
    val maxPaidIzin: Int = 2
)

enum class XpHistoryTimeRange(val label: String) {
    ALL("Semua Waktu"),
    LAST_7_DAYS("7 Hari Terakhir"),
    THIS_MONTH("Bulan Ini"),
    THIS_SEMESTER("Semester Ini")
}

enum class RankingPeriodFilter(val label: String) {
    WEEKLY("Mingguan"),
    MONTHLY("Bulanan"),
    SEMESTER("Semester"),
    CUSTOM("Periode Khusus")
}

enum class RankingScope(val label: String) {
    CLASS("Berdasarkan Kelas"),
    STUDY_PROGRAM("Berdasarkan Prodi"),
    ALL("Tingkat Fakultas / Keseluruhan")
}

data class RankingPointTransaction(
    val id: String = UUID.randomUUID().toString(),
    val studentId: String,
    val sourceType: PointSourceType,
    val sourceId: String,
    val points: Int,
    val description: String,
    val createdAt: String,
    val updatedAt: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val xpCategory: XpCategory = XpCategory.fromSourceType(sourceType),
    val activityTitle: String = description,
    val semesterKey: String = "2026/2027-Ganjil",
    val adminId: String? = null,
    val adminName: String? = null
)

data class RankingPointAuditLog(
    val id: String = UUID.randomUUID().toString(),
    val studentId: String,
    val studentName: String,
    val adminName: String,
    val reason: String,
    val pointsBefore: Int,
    val pointsAfter: Int,
    val changedAt: String
)

data class StudentRankingEntry(
    val rank: Int,
    val studentId: String,
    val studentName: String,
    val nim: String,
    val studyProgramId: String,
    val studyProgramName: String,
    val studyProgramCode: String = studyProgramName,
    val classId: String?,
    val className: String,
    val semester: Int,
    val badgeStatus: StudentBadgeStatus = StudentBadgeStatus.NEUTRAL,
    val totalPoints: Int,
    val hadirCount: Int,
    val izinCount: Int,
    val sakitCount: Int,
    val alfaCount: Int,
    val onTimeAssignmentsCount: Int,
    val lateAssignmentsCount: Int,
    val missingAssignmentsCount: Int,
    val pointGeneratingActivityCount: Int,
    val pointGapToAbove: Int? = null,
    val rankAbove: Int? = null
) {
    val displayNameWithProdi: String
        get() = "$studentName — $studyProgramCode"
}

data class CsvImportPreviewRow(
    val rowNumber: Int,
    val fullName: String,
    val nim: String,
    val email: String,
    val semester: Int,
    val cohortYear: Int,
    val errorReason: String? = null
)

enum class ExcelRowColorFlag(val label: String) {
    NONE("Normal"),
    RED_AFK("Merah (AFK)"),
    BLACK_SKIP("Blok Hitam (Dilewati)"),
    OTHER_IGNORED("Warna Lain (Normal)")
}

enum class ExcelImportValidationStatus(val label: String) {
    VALID_NORMAL("Valid - Siap Diimpor"),
    VALID_AFK("AFK (Warna Merah)"),
    SKIPPED_BLACK("Dilewati - Blok Hitam"),
    ALREADY_EXISTS("Sudah Memiliki Akun"),
    INVALID_OR_DUPLICATE("Duplikat / Tidak Valid")
}

data class ExcelSheetMapping(
    val sheetName: String,
    val studyProgramId: String?,
    val studyProgramCode: String,
    val className: String,
    val classLabel: String,
    val semester: Int,
    val isRecognized: Boolean
)

data class ExcelImportPreviewRow(
    val sheetName: String,
    val rowNumber: Int,
    val studentNumber: String,
    val nim: String,
    val fullName: String,
    val studyProgramId: String?,
    val studyProgramCode: String,
    val className: String,
    val classLabel: String,
    val semester: Int,
    val colorFlag: ExcelRowColorFlag,
    val validationStatus: ExcelImportValidationStatus,
    val issues: List<String> = emptyList(),
    val weeklyAttendance: Map<Int, String> = emptyMap()
) {
    val isImportable: Boolean
        get() = validationStatus == ExcelImportValidationStatus.VALID_NORMAL ||
            validationStatus == ExcelImportValidationStatus.VALID_AFK
}

data class ExcelImportPreviewSummary(
    val fileName: String,
    val sheetMappings: List<ExcelSheetMapping>,
    val rows: List<ExcelImportPreviewRow>
) {
    val validNormalCount: Int get() = rows.count { it.validationStatus == ExcelImportValidationStatus.VALID_NORMAL }
    val validAfkCount: Int get() = rows.count { it.validationStatus == ExcelImportValidationStatus.VALID_AFK }
    val totalImportableCount: Int get() = validNormalCount + validAfkCount
    val skippedBlackCount: Int get() = rows.count { it.validationStatus == ExcelImportValidationStatus.SKIPPED_BLACK }
    val alreadyExistsCount: Int get() = rows.count { it.validationStatus == ExcelImportValidationStatus.ALREADY_EXISTS }
    val invalidOrDuplicateCount: Int get() = rows.count { it.validationStatus == ExcelImportValidationStatus.INVALID_OR_DUPLICATE }
    val totalDuplicateOrInvalidAndExistingCount: Int get() = alreadyExistsCount + invalidOrDuplicateCount
    val hasWeeklyAttendance: Boolean get() = rows.any { it.isImportable && it.weeklyAttendance.isNotEmpty() }
}

data class HistoricalAttendanceRecord(
    val id: String = UUID.randomUUID().toString(),
    val studentId: String,
    val studentNim: String,
    val studentName: String,
    val classId: String?,
    val className: String,
    val studyProgramCode: String,
    val weekNumber: Int,
    val weekLabel: String,
    val rawStatus: String,
    val sourceSheet: String,
    val semesterKey: String = "2026/2027-Ganjil",
    val importedAt: String,
    val xpCalculated: Boolean = false
)

sealed interface ScreenLoadState {
    data object Ready : ScreenLoadState
    data object Loading : ScreenLoadState
    data class Error(val message: String) : ScreenLoadState
}
