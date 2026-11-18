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
    val className: String? = null,
    val semester: Int? = null,
    val cohortYear: Int? = null,
    val status: AccountStatus = AccountStatus.ACTIVE,
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
    val status: AccountStatus = AccountStatus.ACTIVE
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
    val correctionReason: String? = null
)

data class Assignment(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val instructions: String,
    val classId: String,
    val className: String,
    val courseCategory: String,
    val createdAt: String,
    val deadline: String,
    val allowedFormats: List<String>,
    val maxFileSizeMb: Int,
    val submissionMethod: SubmissionMethod,
    val materialAttachment: String? = null,
    val isActive: Boolean = true
)

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
    val driveLink: String? = null,
    val uploadedFileName: String? = null,
    val uploadedFileSizeLabel: String? = null,
    val submittedAt: String,
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
    val allowGlobalStudentRanking: Boolean = true
)

enum class PointSourceType(val label: String) {
    ATTENDANCE("Absensi"),
    ASSIGNMENT("Pengumpulan Tugas"),
    MANUAL_ADJUSTMENT("Penyesuaian Admin")
}

enum class RankingPeriodFilter(val label: String) {
    WEEKLY("Mingguan"),
    MONTHLY("Bulanan"),
    SEMESTER("Semester"),
    CUSTOM("Periode Khusus")
}

enum class RankingScope(val label: String) {
    CLASS("Di Kelas"),
    STUDY_PROGRAM("Di Program Studi"),
    ALL("Seluruh Mahasiswa")
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
    val timestampMillis: Long = System.currentTimeMillis()
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
    val classId: String?,
    val className: String,
    val semester: Int,
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
)

data class CsvImportPreviewRow(
    val rowNumber: Int,
    val fullName: String,
    val nim: String,
    val email: String,
    val semester: Int,
    val cohortYear: Int,
    val errorReason: String? = null
)

sealed interface ScreenLoadState {
    data object Ready : ScreenLoadState
    data object Loading : ScreenLoadState
    data class Error(val message: String) : ScreenLoadState
}
