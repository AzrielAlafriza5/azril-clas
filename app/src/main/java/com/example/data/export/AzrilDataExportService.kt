package com.example.data.export

import com.example.BuildConfig
import com.example.data.assignments.AssignmentSyncService
import com.example.data.auth.AuthKeyValueStore
import com.example.data.gamification.XpAndStreakEngine
import com.example.ui.model.AcademicClass
import com.example.ui.model.AccountStatus
import com.example.ui.model.Assignment
import com.example.ui.model.AssignmentChecklistRecord
import com.example.ui.model.AssignmentTargetType
import com.example.ui.model.AttendanceRecord
import com.example.ui.model.AttendanceStatus
import com.example.ui.model.HistoricalAttendanceRecord
import com.example.ui.model.PointSourceType
import com.example.ui.model.RankingPointTransaction
import com.example.ui.model.StudentBadgeStatus
import com.example.ui.model.StudentRecord
import com.example.ui.model.StudyProgram
import com.example.ui.model.SubmissionMethod
import com.example.ui.model.SubmissionRecord
import com.example.ui.model.SubmissionStatus
import com.example.ui.model.TaskCompletionStatus
import com.example.ui.model.XpCategory
import com.example.ui.model.resolveTaskCompletionStatus
import com.example.ui.state.AzrilViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

enum class ExportFormat(
    val label: String,
    val extension: String,
    val mimeType: String
) {
    EXCEL(
        label = "Excel (.xlsx)",
        extension = "xlsx",
        mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    ),
    CSV(
        label = "CSV (.csv)",
        extension = "csv",
        mimeType = "text/csv"
    )
}

enum class ExportDatasetType(
    val title: String,
    val baseFileName: String,
    val subtitle: String
) {
    ATTENDANCE(
        title = "Ekspor Absensi",
        baseFileName = "Rekap_Absensi_Azril_Clas",
        subtitle = "Nama, NIM, prodi, kelas, tanggal, status kehadiran, waktu absen, & keterangan izin."
    ),
    ASSIGNMENTS(
        title = "Ekspor Pengumpulan Tugas",
        baseFileName = "Rekap_Pengumpulan_Tugas_Azril_Clas",
        subtitle = "Penugasan, deadline, waktu kumpul, status (sudah/belum/terlambat), link file, nilai, & catatan."
    ),
    XP_AND_RANKING(
        title = "Ekspor XP dan Ranking",
        baseFileName = "Rekap_XP_Ranking_Azril_Clas",
        subtitle = "Total XP, riwayat & sumber perolehan XP, tanggal perolehan, serta peringkat di kelas."
    ),
    STUDENTS(
        title = "Ekspor Data Mahasiswa",
        baseFileName = "Data_Mahasiswa_Azril_Clas",
        subtitle = "Daftar seluruh mahasiswa terdaftar lengkap dengan NIM, prodi, kelas, total XP, & status akun."
    ),
    COMPLETE_REPORT(
        title = "Ekspor Laporan Lengkap",
        baseFileName = "Laporan_Lengkap_Azril_Clas",
        subtitle = "Workbook gabungan 6 sheet: Mahasiswa, Absensi, Pengumpulan, Rekap Tugas, Riwayat XP, & Ranking."
    )
}

data class ExportFilterCriteria(
    val studyProgramId: String? = null,
    val classId: String? = null,
    val studentId: String? = null,
    val startDateIso: String? = null, // YYYY-MM-DD (WIB)
    val endDateIso: String? = null    // YYYY-MM-DD (WIB)
) {
    val hasActiveFilter: Boolean
        get() = studyProgramId != null ||
            classId != null ||
            studentId != null ||
            !startDateIso.isNullOrBlank() ||
            !endDateIso.isNullOrBlank()
}

data class ExportDatasetSnapshot(
    val studyPrograms: List<StudyProgram>,
    val classes: List<AcademicClass>,
    val students: List<StudentRecord>,
    val attendanceRecords: List<AttendanceRecord>,
    val historicalAttendanceRecords: List<HistoricalAttendanceRecord>,
    val assignments: List<Assignment>,
    val submissions: List<SubmissionRecord>,
    val checklists: List<AssignmentChecklistRecord>,
    val xpTransactions: List<RankingPointTransaction>,
    val syncedFromSupabase: Boolean = false
)

sealed interface ExportBuildResult {
    data class Success(
        val datasetType: ExportDatasetType,
        val format: ExportFormat,
        val fileName: String,
        val mimeType: String,
        val bytes: ByteArray,
        val rowCount: Int,
        val sheetCount: Int,
        val syncedFromSupabase: Boolean,
        val summaryMessage: String
    ) : ExportBuildResult

    data class Empty(val message: String) : ExportBuildResult
    data class Error(val message: String) : ExportBuildResult
}

/**
 * Admin Data Export Engine for Azril Clas.
 *
 * - Reads live data directly from existing Supabase tables (using paginated Range queries for large datasets)
 *   without modifying schema or injecting any dummy data.
 * - Produces real multi-sheet OOXML (.xlsx) and UTF-8-BOM (.csv) files openable in Excel & Google Sheets.
 */
class AzrilDataExportService(
    private val store: AuthKeyValueStore,
    private val supabaseUrl: String = BuildConfig.SUPABASE_URL,
    private val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    companion object {
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val PAGE_SIZE = 1000
        private const val MAX_PAGES = 50
        private val WIB_TZ: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")
        private val ID_LOCALE = Locale("in", "ID")
        private val ISO_DATE_REGEX = Regex("""^\d{4}-\d{2}-\d{2}$""")

        private val MONTH_MAP_ID = mapOf(
            "jan" to 1, "januari" to 1,
            "feb" to 2, "februari" to 2,
            "mar" to 3, "maret" to 3,
            "apr" to 4, "april" to 4,
            "mei" to 5, "may" to 5,
            "jun" to 6, "juni" to 6,
            "jul" to 7, "juli" to 7,
            "agu" to 8, "ags" to 8, "agustus" to 8, "aug" to 8,
            "sep" to 9, "sept" to 9, "september" to 9,
            "okt" to 10, "oktober" to 10, "oct" to 10,
            "nov" to 11, "november" to 11,
            "des" to 12, "desember" to 12, "dec" to 12
        )

        fun isValidIsoDate(raw: String?): Boolean {
            val clean = raw?.trim().orEmpty()
            if (!ISO_DATE_REGEX.matches(clean)) return false
            return parseIsoDateBoundaryMillis(clean, endOfDay = false) != null
        }

        fun parseIsoDateBoundaryMillis(isoDate: String?, endOfDay: Boolean): Long? {
            val clean = isoDate?.trim().orEmpty()
            if (!ISO_DATE_REGEX.matches(clean)) return null
            val parts = clean.split("-")
            val y = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            val d = parts[2].toIntOrNull() ?: return null
            if (m !in 1..12 || d !in 1..31) return null
            return runCatching {
                Calendar.getInstance(WIB_TZ).apply {
                    isLenient = false
                    set(Calendar.YEAR, y)
                    set(Calendar.MONTH, m - 1)
                    set(Calendar.DAY_OF_MONTH, d)
                    if (endOfDay) {
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    } else {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                }.timeInMillis
            }.getOrNull()
        }

        /**
         * Resolves a record's epoch millis from either explicit timestampMillis or Indonesian/ISO date string.
         */
        fun resolveRecordMillis(timestampMillis: Long?, dateText: String?): Long? {
            if (timestampMillis != null && timestampMillis > 0L) return timestampMillis
            val raw = dateText?.trim().orEmpty()
            if (raw.isEmpty() || raw == "-") return null
            AssignmentSyncService.parseIsoTimestampMillis(raw)?.let { return it }
            parseIsoDateBoundaryMillis(raw.take(10), endOfDay = false)?.let { return it }

            // Parse Indonesian display format e.g. "12 Des 2026" or "12 Des 2026, 14:30 WIB"
            val match = Regex("""(\d{1,2})\s+([A-Za-z]+)\s+(\d{4})""").find(raw)
            if (match != null) {
                val day = match.groupValues[1].toIntOrNull() ?: return null
                val monthName = match.groupValues[2].lowercase(Locale.US)
                val year = match.groupValues[3].toIntOrNull() ?: return null
                val month = MONTH_MAP_ID[monthName] ?: return null
                return runCatching {
                    Calendar.getInstance(WIB_TZ).apply {
                        isLenient = false
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month - 1)
                        set(Calendar.DAY_OF_MONTH, day)
                        set(Calendar.HOUR_OF_DAY, 12)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }.getOrNull()
            }
            return null
        }

        fun formatWibDateOnly(millis: Long): String =
            SimpleDateFormat("dd MMM yyyy", ID_LOCALE).apply { timeZone = WIB_TZ }.format(Date(millis))

        fun formatWibTimeOnly(millis: Long): String =
            SimpleDateFormat("HH:mm 'WIB'", ID_LOCALE).apply { timeZone = WIB_TZ }.format(Date(millis))
    }

    fun isSupabaseConfigured(): Boolean {
        val cleanUrl = supabaseUrl.trim()
        val cleanKey = supabaseAnonKey.trim()
        return cleanUrl.startsWith("https://") &&
            !cleanUrl.contains("YOUR_PROJECT_REF") &&
            cleanKey.length > 20 &&
            !cleanKey.contains("YOUR_SUPABASE_ANON_KEY")
    }

    private suspend fun activeSupabaseJwt(): String? {
        val jwt = store.getString(KEY_SESSION_TOKEN) ?: return null
        if (jwt.isBlank() || jwt.startsWith("azr_sess_") || jwt.startsWith("azr_admin_sess_")) return null
        return jwt
    }

    /**
     * Fetches the latest live records directly from existing Supabase tables using paginated Range requests
     * so large datasets (>1000 rows) are retrieved completely without truncation.
     * Falls back gracefully per table to [fallbackSnapshot] if Supabase is not configured or a specific optional table isn't provisioned.
     */
    suspend fun fetchLiveSnapshotFromSupabase(
        fallbackSnapshot: ExportDatasetSnapshot
    ): ExportDatasetSnapshot = withContext(Dispatchers.IO) {
        if (!isSupabaseConfigured()) return@withContext fallbackSnapshot
        val jwt = activeSupabaseJwt() ?: return@withContext fallbackSnapshot
        val base = supabaseUrl.trimEnd('/')

        val remoteProdis = fetchStudyPrograms(base, jwt)
        val effectiveProdis = remoteProdis?.takeIf { it.isNotEmpty() } ?: fallbackSnapshot.studyPrograms
        val prodiById = effectiveProdis.associateBy { it.id }
        val prodiByCode = effectiveProdis.associateBy { it.code.uppercase(Locale.US) }

        val remoteClasses = fetchClasses(base, jwt, prodiById, prodiByCode)
        val effectiveClasses = mergeById(remoteClasses, fallbackSnapshot.classes) { it.id }
        val classById = effectiveClasses.associateBy { it.id }

        val remoteStudents = fetchStudentProfiles(base, jwt, prodiById, classById)
        val effectiveStudents = mergeById(remoteStudents, fallbackSnapshot.students) { it.id }

        val remoteAssignments = fetchAssignments(base, jwt, effectiveClasses, effectiveProdis)
        val effectiveAssignments = mergeById(remoteAssignments, fallbackSnapshot.assignments) { it.id }

        val remoteSubmissions = fetchSubmissions(base, jwt, effectiveStudents, effectiveAssignments)
        val effectiveSubmissions = mergeById(remoteSubmissions, fallbackSnapshot.submissions) { "${it.assignmentId}::${it.studentId}" }

        val remoteChecklists = fetchChecklists(base, jwt)
        val effectiveChecklists = mergeById(remoteChecklists, fallbackSnapshot.checklists) { it.compositeKey }

        val remoteXp = fetchXpTransactions(base, jwt)
        val effectiveXp = mergeById(remoteXp, fallbackSnapshot.xpTransactions) { it.id }

        val remoteHistoricalAtt = fetchHistoricalAttendance(base, jwt)
        val effectiveHistoricalAtt = mergeById(remoteHistoricalAtt, fallbackSnapshot.historicalAttendanceRecords) { it.id }

        val remoteLiveAtt = fetchLiveAttendanceRecords(base, jwt, effectiveStudents)
        val effectiveLiveAtt = mergeById(remoteLiveAtt, fallbackSnapshot.attendanceRecords) { it.id }

        val anySynced = listOf(
            remoteProdis,
            remoteClasses,
            remoteStudents,
            remoteAssignments,
            remoteSubmissions,
            remoteChecklists,
            remoteXp,
            remoteHistoricalAtt,
            remoteLiveAtt
        ).any { it != null }

        ExportDatasetSnapshot(
            studyPrograms = effectiveProdis,
            classes = effectiveClasses,
            students = effectiveStudents,
            attendanceRecords = effectiveLiveAtt,
            historicalAttendanceRecords = effectiveHistoricalAtt,
            assignments = effectiveAssignments,
            submissions = effectiveSubmissions,
            checklists = effectiveChecklists,
            xpTransactions = effectiveXp,
            syncedFromSupabase = anySynced
        )
    }

    /**
     * Generates a complete Excel (.xlsx) or CSV (.csv) export payload from [snapshot] according to [type], [format], and [filter].
     */
    fun buildExport(
        snapshot: ExportDatasetSnapshot,
        type: ExportDatasetType,
        format: ExportFormat,
        filter: ExportFilterCriteria
    ): ExportBuildResult {
        // Validate date range filter
        val startMillis = if (!filter.startDateIso.isNullOrBlank()) {
            parseIsoDateBoundaryMillis(filter.startDateIso, endOfDay = false)
                ?: return ExportBuildResult.Error("Format tanggal awal tidak valid. Gunakan format YYYY-MM-DD (contoh: 2026-10-01).")
        } else null

        val endMillis = if (!filter.endDateIso.isNullOrBlank()) {
            parseIsoDateBoundaryMillis(filter.endDateIso, endOfDay = true)
                ?: return ExportBuildResult.Error("Format tanggal akhir tidak valid. Gunakan format YYYY-MM-DD (contoh: 2026-12-31).")
        } else null

        if (startMillis != null && endMillis != null && startMillis > endMillis) {
            return ExportBuildResult.Error("Tanggal awal tidak boleh lebih besar daripada tanggal akhir.")
        }

        val filteredStudents = filterStudents(snapshot, filter)
        val filteredStudentIds = filteredStudents.map { it.id }.toSet()
        val classRankMap = computeClassRankMap(snapshot)
        val globalRankMap = computeGlobalRankMap(snapshot)
        val totalXpByStudent = computeTotalXpMap(snapshot.xpTransactions)

        val sheets: List<ExportSheetData> = when (type) {
            ExportDatasetType.ATTENDANCE -> listOf(
                buildAttendanceSheet(snapshot, filteredStudents, filteredStudentIds, startMillis, endMillis)
            )
            ExportDatasetType.ASSIGNMENTS -> listOf(
                buildAssignmentSubmissionsSheet(snapshot, filteredStudents, filteredStudentIds, startMillis, endMillis)
            )
            ExportDatasetType.XP_AND_RANKING -> {
                val xpHistorySheet = buildXpHistorySheet(
                    snapshot = snapshot,
                    filteredStudents = filteredStudents,
                    filteredStudentIds = filteredStudentIds,
                    totalXpByStudent = totalXpByStudent,
                    classRankMap = classRankMap,
                    startMillis = startMillis,
                    endMillis = endMillis
                )
                val rankingSheet = buildRankingSheet(
                    snapshot = snapshot,
                    filteredStudents = filteredStudents,
                    totalXpByStudent = totalXpByStudent,
                    classRankMap = classRankMap,
                    globalRankMap = globalRankMap
                )
                if (format == ExportFormat.EXCEL) {
                    listOf(xpHistorySheet, rankingSheet)
                } else {
                    if (xpHistorySheet.rows.isNotEmpty()) listOf(xpHistorySheet) else listOf(rankingSheet)
                }
            }
            ExportDatasetType.STUDENTS -> listOf(
                buildStudentsSheet(snapshot, filteredStudents, totalXpByStudent)
            )
            ExportDatasetType.COMPLETE_REPORT -> listOf(
                buildStudentsSheet(snapshot, filteredStudents, totalXpByStudent),
                buildAttendanceSheet(snapshot, filteredStudents, filteredStudentIds, startMillis, endMillis),
                buildAssignmentSubmissionsSheet(snapshot, filteredStudents, filteredStudentIds, startMillis, endMillis),
                buildAssignmentSummarySheet(snapshot, filteredStudents, startMillis, endMillis),
                buildXpHistorySheet(
                    snapshot = snapshot,
                    filteredStudents = filteredStudents,
                    filteredStudentIds = filteredStudentIds,
                    totalXpByStudent = totalXpByStudent,
                    classRankMap = classRankMap,
                    startMillis = startMillis,
                    endMillis = endMillis
                ),
                buildRankingSheet(
                    snapshot = snapshot,
                    filteredStudents = filteredStudents,
                    totalXpByStudent = totalXpByStudent,
                    classRankMap = classRankMap,
                    globalRankMap = globalRankMap
                )
            )
        }

        val totalDataRows = sheets.sumOf { it.rows.size }
        if (totalDataRows == 0) {
            return ExportBuildResult.Empty(
                "Tidak ada data ${type.title.lowercase(ID_LOCALE)} yang sesuai dengan filter yang dipilih."
            )
        }

        val bytes = runCatching {
            when (format) {
                ExportFormat.EXCEL -> SpreadsheetExportWriter.writeXlsxBytes(sheets)
                ExportFormat.CSV -> SpreadsheetExportWriter.writeCsvBytes(sheets)
            }
        }.getOrElse { ex ->
            return ExportBuildResult.Error("Gagal menyusun file ${format.label}: ${ex.message ?: "kesalahan internal"}")
        }

        val fileName = "${type.baseFileName}.${format.extension}"
        val sourceNote = if (snapshot.syncedFromSupabase) "Supabase Live" else "Data Tersimpan"
        return ExportBuildResult.Success(
            datasetType = type,
            format = format,
            fileName = fileName,
            mimeType = format.mimeType,
            bytes = bytes,
            rowCount = totalDataRows,
            sheetCount = sheets.size,
            syncedFromSupabase = snapshot.syncedFromSupabase,
            summaryMessage = "Berhasil menyiapkan $fileName ($totalDataRows baris, ${sheets.size} sheet • $sourceNote)."
        )
    }

    /**
     * Fast preview count for UI cards so Admin sees exact filtered row count before downloading.
     */
    fun estimateRowCount(
        snapshot: ExportDatasetSnapshot,
        type: ExportDatasetType,
        filter: ExportFilterCriteria
    ): Int {
        val startMillis = filter.startDateIso?.takeIf { it.isNotBlank() }?.let { parseIsoDateBoundaryMillis(it, false) }
        val endMillis = filter.endDateIso?.takeIf { it.isNotBlank() }?.let { parseIsoDateBoundaryMillis(it, true) }
        val filteredStudents = filterStudents(snapshot, filter)
        val filteredIds = filteredStudents.map { it.id }.toSet()

        return when (type) {
            ExportDatasetType.STUDENTS -> filteredStudents.size
            ExportDatasetType.ATTENDANCE -> {
                val live = snapshot.attendanceRecords.count { rec ->
                    rec.studentId in filteredIds && isWithinDateRange(
                        resolveRecordMillis(rec.timestampMillis, rec.dateLabel),
                        startMillis,
                        endMillis
                    )
                }
                val hist = snapshot.historicalAttendanceRecords.count { rec ->
                    rec.studentId in filteredIds && isWithinDateRange(
                        resolveRecordMillis(null, rec.importedAt),
                        startMillis,
                        endMillis
                    )
                }
                live + hist
            }
            ExportDatasetType.ASSIGNMENTS -> buildAssignmentSubmissionsSheet(
                snapshot, filteredStudents, filteredIds, startMillis, endMillis
            ).rows.size
            ExportDatasetType.XP_AND_RANKING -> {
                val xpRows = snapshot.xpTransactions.count { tx ->
                    tx.studentId in filteredIds && isWithinDateRange(
                        resolveRecordMillis(tx.timestampMillis, tx.createdAt),
                        startMillis,
                        endMillis
                    )
                }
                if (xpRows > 0) xpRows else filteredStudents.size
            }
            ExportDatasetType.COMPLETE_REPORT -> {
                val studentsCount = filteredStudents.size
                val attCount = estimateRowCount(snapshot, ExportDatasetType.ATTENDANCE, filter)
                val assignRows = estimateRowCount(snapshot, ExportDatasetType.ASSIGNMENTS, filter)
                val xpCount = snapshot.xpTransactions.count { tx ->
                    tx.studentId in filteredIds && isWithinDateRange(
                        resolveRecordMillis(tx.timestampMillis, tx.createdAt),
                        startMillis,
                        endMillis
                    )
                }
                studentsCount + attCount + assignRows + snapshot.assignments.size + xpCount + studentsCount
            }
        }
    }

    // =========================================================================
    // SHEET BUILDERS
    // =========================================================================

    private fun buildStudentsSheet(
        snapshot: ExportDatasetSnapshot,
        filteredStudents: List<StudentRecord>,
        totalXpByStudent: Map<String, Int>
    ): ExportSheetData {
        val prodiById = snapshot.studyPrograms.associateBy { it.id }
        val headers = listOf(
            "No",
            "Nama Lengkap",
            "NIM",
            "Program Studi",
            "Kelas",
            "Total XP",
            "Status Akun",
            "Status Akademik"
        )
        val sorted = filteredStudents.sortedWith(
            compareBy<StudentRecord>({ it.studyProgramName }, { it.className.orEmpty() }, { it.fullName.lowercase(Locale.US) })
        )
        val rows = sorted.mapIndexed { idx, stu ->
            val prodiLabel = prodiById[stu.studyProgramId]?.code ?: stu.studyProgramName
            listOf(
                intCell(idx + 1),
                textCell(stu.fullName),
                textCell(stu.nim),
                textCell(prodiLabel),
                textCell(stu.className?.ifBlank { "-" } ?: "-"),
                intCell(totalXpByStudent[stu.id] ?: 0),
                textCell(stu.status.label),
                textCell(stu.badgeStatus.label)
            )
        }
        return ExportSheetData(name = "Data Mahasiswa", headers = headers, rows = rows)
    }

    private fun buildAttendanceSheet(
        snapshot: ExportDatasetSnapshot,
        filteredStudents: List<StudentRecord>,
        filteredStudentIds: Set<String>,
        startMillis: Long?,
        endMillis: Long?
    ): ExportSheetData {
        val studentById = filteredStudents.associateBy { it.id }
        val prodiById = snapshot.studyPrograms.associateBy { it.id }
        val headers = listOf(
            "No",
            "Nama Lengkap Mahasiswa",
            "NIM",
            "Program Studi",
            "Kelas",
            "Tanggal Absensi",
            "Status Kehadiran",
            "Waktu Melakukan Absensi",
            "Keterangan Izin / Ketidakhadiran",
            "Sesi / Sumber Absensi"
        )

        data class UnifiedAttRow(
            val sortMillis: Long,
            val fullName: String,
            val nim: String,
            val prodi: String,
            val className: String,
            val dateLabel: String,
            val status: String,
            val timeLabel: String,
            val note: String,
            val sessionLabel: String
        )

        val unified = mutableListOf<UnifiedAttRow>()

        // 1. Live attendance records
        snapshot.attendanceRecords
            .filter { it.studentId in filteredStudentIds }
            .forEach { rec ->
                val recMillis = resolveRecordMillis(rec.timestampMillis, rec.dateLabel)
                if (!isWithinDateRange(recMillis, startMillis, endMillis)) return@forEach
                val stu = studentById[rec.studentId]
                val prodi = stu?.let { prodiById[it.studyProgramId]?.code ?: it.studyProgramName } ?: "-"
                val cls = stu?.className?.ifBlank { null } ?: rec.className.ifBlank { "-" }
                val dateText = rec.dateLabel.ifBlank { recMillis?.let { formatWibDateOnly(it) } ?: "-" }
                val timeText = rec.recordedAt.ifBlank { recMillis?.let { formatWibTimeOnly(it) } ?: "-" }
                val note = rec.correctionReason?.trim()?.ifBlank { "-" } ?: "-"
                unified += UnifiedAttRow(
                    sortMillis = recMillis ?: 0L,
                    fullName = stu?.fullName ?: rec.studentName,
                    nim = stu?.nim ?: rec.studentNim,
                    prodi = prodi,
                    className = cls,
                    dateLabel = dateText,
                    status = rec.status.label,
                    timeLabel = timeText,
                    note = note,
                    sessionLabel = rec.meetingTitle.ifBlank { "Absensi Harian" }
                )
            }

        // 2. Historical Excel attendance records (Weeks 1..8)
        snapshot.historicalAttendanceRecords
            .filter { it.studentId in filteredStudentIds }
            .forEach { rec ->
                val recMillis = resolveRecordMillis(null, rec.importedAt)
                if (!isWithinDateRange(recMillis, startMillis, endMillis)) return@forEach
                val stu = studentById[rec.studentId]
                val prodi = rec.studyProgramCode.ifBlank {
                    stu?.let { prodiById[it.studyProgramId]?.code ?: it.studyProgramName } ?: "-"
                }
                val cls = rec.className.ifBlank { stu?.className ?: "-" }
                val note = when (rec.rawStatus.trim().uppercase(Locale.US)) {
                    "IZIN", "SAKIT", "DISPEN", "KIP" -> "Status historis: ${rec.rawStatus} (${rec.weekLabel})"
                    "ALPHA", "ALFA", "TIDAK HADIR" -> "Tanpa keterangan (${rec.weekLabel})"
                    else -> "-"
                }
                unified += UnifiedAttRow(
                    sortMillis = recMillis ?: rec.weekNumber.toLong(),
                    fullName = stu?.fullName ?: rec.studentName,
                    nim = stu?.nim ?: rec.studentNim,
                    prodi = prodi,
                    className = cls,
                    dateLabel = rec.weekLabel,
                    status = rec.rawStatus,
                    timeLabel = rec.importedAt.ifBlank { "Rekap Historis" },
                    note = note,
                    sessionLabel = "Historis Excel (${rec.sourceSheet})"
                )
            }

        val sorted = unified.sortedWith(
            compareByDescending<UnifiedAttRow> { it.sortMillis }
                .thenBy { it.className }
                .thenBy { it.fullName.lowercase(Locale.US) }
        )

        val rows = sorted.mapIndexed { idx, r ->
            listOf(
                intCell(idx + 1),
                textCell(r.fullName),
                textCell(r.nim),
                textCell(r.prodi),
                textCell(r.className),
                textCell(r.dateLabel),
                textCell(r.status),
                textCell(r.timeLabel),
                textCell(r.note),
                textCell(r.sessionLabel)
            )
        }
        return ExportSheetData(name = "Data Absensi", headers = headers, rows = rows)
    }

    private fun buildAssignmentSubmissionsSheet(
        snapshot: ExportDatasetSnapshot,
        filteredStudents: List<StudentRecord>,
        filteredStudentIds: Set<String>,
        startMillis: Long?,
        endMillis: Long?
    ): ExportSheetData {
        val prodiById = snapshot.studyPrograms.associateBy { it.id }
        val submissionByPair = snapshot.submissions.associateBy { "${it.assignmentId}::${it.studentId}" }
        val checklistByPair = snapshot.checklists.associateBy { it.compositeKey }

        val headers = listOf(
            "No",
            "Nama Mahasiswa",
            "NIM",
            "Program Studi",
            "Kelas",
            "Nama Tugas",
            "Tanggal Pemberian Tugas",
            "Deadline Tugas",
            "Waktu Pengumpulan",
            "Status Pengumpulan",
            "Status Verifikasi Admin",
            "Link / Lokasi File Tugas",
            "Nilai",
            "Catatan Penilaian"
        )

        val rows = mutableListOf<List<ExportCellValue>>()
        var rowNumber = 1

        val sortedAssignments = snapshot.assignments.sortedByDescending { it.deadlineEpochMillis ?: 0L }

        sortedAssignments.forEach { assignment ->
            // Filter by date range using submission time OR assignment deadline/created date
            val assignDateMillis = assignment.deadlineEpochMillis ?: resolveRecordMillis(null, assignment.createdAt)
            val targetStudents = AzrilViewModel.getTargetStudentsForAssignment(
                assignment = assignment,
                students = filteredStudents,
                classes = snapshot.classes
            )

            // Also include any filtered student who already submitted to this assignment even if class later changed
            val submittedExtra = snapshot.submissions
                .filter { it.assignmentId == assignment.id && it.studentId in filteredStudentIds }
                .mapNotNull { sub -> filteredStudents.find { it.id == sub.studentId } }

            val combinedStudents = (targetStudents + submittedExtra)
                .distinctBy { it.id }
                .sortedWith(compareBy<StudentRecord>({ it.className.orEmpty() }, { it.fullName.lowercase(Locale.US) }))

            combinedStudents.forEach { stu ->
                val pairKey = "${assignment.id}::${stu.id}"
                val sub = submissionByPair[pairKey]
                val checklist = checklistByPair[pairKey]

                val effectiveMillis = sub?.let {
                    resolveRecordMillis(it.lastUpdatedAtMillis.takeIf { ms -> ms > 0L } ?: it.firstSubmittedAtMillis, it.submittedAt)
                } ?: assignDateMillis

                if (!isWithinDateRange(effectiveMillis, startMillis, endMillis)) return@forEach

                val submissionStatusLabel = when {
                    sub == null -> "Belum Mengumpulkan"
                    sub.isLate || sub.status == SubmissionStatus.LATE -> "Terlambat"
                    else -> "Sudah Mengumpulkan"
                }

                val verificationStatus = resolveTaskCompletionStatus(sub, checklist).label
                val linkOrFile = buildString {
                    val parts = listOfNotNull(
                        sub?.driveLink?.takeIf { it.isNotBlank() },
                        sub?.uploadedFileUrl?.takeIf { it.isNotBlank() },
                        sub?.uploadedFileName?.takeIf { it.isNotBlank() }?.let { "File: $it" }
                    )
                    append(if (parts.isEmpty()) "-" else parts.joinToString(" | "))
                }
                val prodiLabel = prodiById[stu.studyProgramId]?.code ?: stu.studyProgramName
                val submittedTimeText = sub?.let {
                    if (it.revisionCount > 0 && it.lastUpdatedAt.isNotBlank()) {
                        "${it.firstSubmittedAt} (Revisi: ${it.lastUpdatedAt})"
                    } else {
                        it.firstSubmittedAt.ifBlank { it.submittedAt }
                    }
                } ?: "-"
                val noteParts = listOfNotNull(
                    sub?.adminFeedback?.takeIf { it.isNotBlank() },
                    checklist?.verificationNote?.takeIf { it.isNotBlank() }
                )

                rows += listOf(
                    intCell(rowNumber++),
                    textCell(stu.fullName),
                    textCell(stu.nim),
                    textCell(prodiLabel),
                    textCell(stu.className?.ifBlank { "-" } ?: "-"),
                    textCell(assignment.title),
                    textCell(assignment.createdAt.ifBlank { "-" }),
                    textCell(assignment.deadline.ifBlank { "-" }),
                    textCell(submittedTimeText),
                    textCell(submissionStatusLabel),
                    textCell(verificationStatus),
                    textCell(linkOrFile),
                    sub?.finalScore?.let { intCell(it) } ?: textCell("-"),
                    textCell(if (noteParts.isEmpty()) "-" else noteParts.joinToString(" • "))
                )
            }
        }

        return ExportSheetData(name = "Data Pengumpulan Tugas", headers = headers, rows = rows)
    }

    private fun buildAssignmentSummarySheet(
        snapshot: ExportDatasetSnapshot,
        filteredStudents: List<StudentRecord>,
        startMillis: Long?,
        endMillis: Long?
    ): ExportSheetData {
        val headers = listOf(
            "No",
            "Nama Tugas",
            "Target Penerima",
            "Tanggal Pemberian",
            "Deadline Tugas",
            "Total Mahasiswa Target",
            "Sudah Mengumpulkan",
            "Terlambat",
            "Belum Mengumpulkan",
            "Terverifikasi Selesai",
            "Persentase Selesai (%)"
        )
        val submissionByPair = snapshot.submissions.associateBy { "${it.assignmentId}::${it.studentId}" }
        val checklistByPair = snapshot.checklists.associateBy { it.compositeKey }

        val rows = mutableListOf<List<ExportCellValue>>()
        var rowNo = 1

        snapshot.assignments
            .sortedByDescending { it.deadlineEpochMillis ?: 0L }
            .forEach { assignment ->
                val assignMillis = assignment.deadlineEpochMillis ?: resolveRecordMillis(null, assignment.createdAt)
                if (!isWithinDateRange(assignMillis, startMillis, endMillis)) return@forEach

                val targetStudents = AzrilViewModel.getTargetStudentsForAssignment(
                    assignment = assignment,
                    students = filteredStudents,
                    classes = snapshot.classes
                )
                if (targetStudents.isEmpty() && filteredStudents.size != snapshot.students.size) {
                    return@forEach
                }

                var submittedOnTime = 0
                var lateCount = 0
                var notSubmitted = 0
                var verifiedDone = 0

                targetStudents.forEach { stu ->
                    val key = "${assignment.id}::${stu.id}"
                    val sub = submissionByPair[key]
                    val chk = checklistByPair[key]
                    when {
                        sub == null -> notSubmitted++
                        sub.isLate || sub.status == SubmissionStatus.LATE -> lateCount++
                        else -> submittedOnTime++
                    }
                    if (resolveTaskCompletionStatus(sub, chk) == TaskCompletionStatus.COMPLETED) {
                        verifiedDone++
                    }
                }

                val totalTarget = targetStudents.size
                val pct = if (totalTarget == 0) 0 else (verifiedDone * 100) / totalTarget

                rows += listOf(
                    intCell(rowNo++),
                    textCell(assignment.title),
                    textCell(assignment.targetSummaryLabel()),
                    textCell(assignment.createdAt.ifBlank { "-" }),
                    textCell(assignment.deadline.ifBlank { "-" }),
                    intCell(totalTarget),
                    intCell(submittedOnTime + lateCount),
                    intCell(lateCount),
                    intCell(notSubmitted),
                    intCell(verifiedDone),
                    intCell(pct)
                )
            }

        return ExportSheetData(name = "Rekapitulasi Tugas", headers = headers, rows = rows)
    }

    private fun buildXpHistorySheet(
        snapshot: ExportDatasetSnapshot,
        filteredStudents: List<StudentRecord>,
        filteredStudentIds: Set<String>,
        totalXpByStudent: Map<String, Int>,
        classRankMap: Map<String, Int?>,
        startMillis: Long?,
        endMillis: Long?
    ): ExportSheetData {
        val studentById = filteredStudents.associateBy { it.id }
        val prodiById = snapshot.studyPrograms.associateBy { it.id }

        val headers = listOf(
            "No",
            "Nama Mahasiswa",
            "NIM",
            "Program Studi",
            "Kelas",
            "Peringkat di Kelas",
            "Total XP",
            "Perolehan XP",
            "Sumber Perolehan XP",
            "Riwayat Perolehan XP",
            "Tanggal Perolehan XP"
        )

        val matchingTx = snapshot.xpTransactions
            .filter { tx ->
                tx.studentId in filteredStudentIds && isWithinDateRange(
                    resolveRecordMillis(tx.timestampMillis, tx.createdAt),
                    startMillis,
                    endMillis
                )
            }
            .sortedByDescending { it.timestampMillis }

        val rows = matchingTx.mapIndexed { idx, tx ->
            val stu = studentById[tx.studentId]
            val prodiLabel = stu?.let { prodiById[it.studyProgramId]?.code ?: it.studyProgramName } ?: "-"
            val rankInClass = classRankMap[tx.studentId]
            val rankCell: ExportCellValue = when {
                stu?.badgeStatus == StudentBadgeStatus.AFK -> textCell("AFK (Non-Ranking)")
                rankInClass != null -> intCell(rankInClass)
                else -> textCell("-")
            }
            val detail = buildString {
                if (tx.activityTitle.isNotBlank()) append(tx.activityTitle)
                if (tx.description.isNotBlank() && tx.description != tx.activityTitle) {
                    if (isNotEmpty()) append(" — ")
                    append(tx.description)
                }
            }.ifBlank { "-" }

            listOf(
                intCell(idx + 1),
                textCell(stu?.fullName ?: "Mahasiswa"),
                textCell(stu?.nim ?: "-"),
                textCell(prodiLabel),
                textCell(stu?.className?.ifBlank { "-" } ?: "-"),
                rankCell,
                intCell(totalXpByStudent[tx.studentId] ?: 0),
                intCell(tx.points),
                textCell(tx.sourceType.label),
                textCell(detail),
                textCell(tx.createdAt.ifBlank { XpAndStreakEngine.formatWibDateTimeLabel(tx.timestampMillis) })
            )
        }

        return ExportSheetData(name = "Riwayat XP", headers = headers, rows = rows)
    }

    private fun buildRankingSheet(
        snapshot: ExportDatasetSnapshot,
        filteredStudents: List<StudentRecord>,
        totalXpByStudent: Map<String, Int>,
        classRankMap: Map<String, Int?>,
        globalRankMap: Map<String, Int?>
    ): ExportSheetData {
        val prodiById = snapshot.studyPrograms.associateBy { it.id }

        val headers = listOf(
            "Peringkat Global",
            "Peringkat di Kelas",
            "Nama Mahasiswa",
            "NIM",
            "Program Studi",
            "Kelas",
            "Total XP",
            "Hadir",
            "Izin",
            "Alfa",
            "Tugas Dikumpulkan",
            "Tugas Selesai (Verifikasi)",
            "Status Akademik"
        )

        val sorted = filteredStudents.sortedWith(
            compareBy<StudentRecord> { it.badgeStatus == StudentBadgeStatus.AFK }
                .thenByDescending { totalXpByStudent[it.id] ?: 0 }
                .thenBy { it.fullName.lowercase(Locale.US) }
        )

        val rows = sorted.map { stu ->
            val prodiLabel = prodiById[stu.studyProgramId]?.code ?: stu.studyProgramName
            val myAtt = snapshot.attendanceRecords.filter { it.studentId == stu.id }
            val myHist = snapshot.historicalAttendanceRecords.filter { it.studentId == stu.id }

            val hadirCount = myAtt.count { it.status == AttendanceStatus.HADIR } +
                myHist.count { it.rawStatus.trim().equals("HADIR", ignoreCase = true) }
            val izinCount = myAtt.count { it.status == AttendanceStatus.IZIN || it.status == AttendanceStatus.SAKIT } +
                myHist.count {
                    val s = it.rawStatus.trim().uppercase(Locale.US)
                    s == "IZIN" || s == "SAKIT" || s == "DISPEN"
                }
            val alfaCount = myAtt.count { it.status == AttendanceStatus.ALFA } +
                myHist.count {
                    val s = it.rawStatus.trim().uppercase(Locale.US)
                    s == "ALFA" || s == "ALPHA" || s == "TIDAK HADIR"
                }

            val submittedCount = snapshot.submissions.count { it.studentId == stu.id }
            val verifiedCount = snapshot.checklists.count { it.studentId == stu.id && it.isCompleted }

            val globalRank = globalRankMap[stu.id]
            val classRank = classRankMap[stu.id]
            val isAfk = stu.badgeStatus == StudentBadgeStatus.AFK

            listOf(
                if (isAfk || globalRank == null) textCell("AFK") else intCell(globalRank),
                if (isAfk || classRank == null) textCell("AFK") else intCell(classRank),
                textCell(stu.fullName),
                textCell(stu.nim),
                textCell(prodiLabel),
                textCell(stu.className?.ifBlank { "-" } ?: "-"),
                intCell(totalXpByStudent[stu.id] ?: 0),
                intCell(hadirCount),
                intCell(izinCount),
                intCell(alfaCount),
                intCell(submittedCount),
                intCell(verifiedCount),
                textCell(stu.badgeStatus.label)
            )
        }

        return ExportSheetData(name = "Ranking Mahasiswa", headers = headers, rows = rows)
    }

    // =========================================================================
    // FILTERING & RANKING HELPERS
    // =========================================================================

    private fun filterStudents(
        snapshot: ExportDatasetSnapshot,
        filter: ExportFilterCriteria
    ): List<StudentRecord> {
        val selectedClass = filter.classId?.let { id -> snapshot.classes.find { it.id == id } }
        val selectedProdi = filter.studyProgramId?.let { id -> snapshot.studyPrograms.find { it.id == id } }

        return snapshot.students.filter { stu ->
            val matchProdi = filter.studyProgramId == null ||
                stu.studyProgramId == filter.studyProgramId ||
                (selectedProdi != null && stu.studyProgramName.equals(selectedProdi.code, ignoreCase = true))

            val matchClass = filter.classId == null ||
                stu.classId == filter.classId ||
                (selectedClass != null && (
                    stu.className.equals(selectedClass.name, ignoreCase = true) ||
                        stu.id in selectedClass.memberIds
                    ))

            val matchStudent = filter.studentId == null || stu.id == filter.studentId
            matchProdi && matchClass && matchStudent
        }
    }

    private fun isWithinDateRange(recordMillis: Long?, startMillis: Long?, endMillis: Long?): Boolean {
        if (startMillis == null && endMillis == null) return true
        if (recordMillis == null) return false
        if (startMillis != null && recordMillis < startMillis) return false
        if (endMillis != null && recordMillis > endMillis) return false
        return true
    }

    private fun computeTotalXpMap(transactions: List<RankingPointTransaction>): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        transactions.forEach { tx ->
            map[tx.studentId] = (map[tx.studentId] ?: 0) + tx.points
        }
        return map
    }

    private fun computeClassRankMap(snapshot: ExportDatasetSnapshot): Map<String, Int?> {
        val totalXp = computeTotalXpMap(snapshot.xpTransactions)
        val result = mutableMapOf<String, Int?>()
        val grouped = snapshot.students.groupBy {
            it.classId?.ifBlank { null } ?: it.className?.trim()?.lowercase(Locale.US) ?: "__unassigned__"
        }
        grouped.values.forEach { classStudents ->
            val active = classStudents
                .filter { it.badgeStatus != StudentBadgeStatus.AFK }
                .sortedWith(
                    compareByDescending<StudentRecord> { totalXp[it.id] ?: 0 }
                        .thenBy { it.fullName.lowercase(Locale.US) }
                        .thenBy { it.nim }
                )
            active.forEachIndexed { idx, stu -> result[stu.id] = idx + 1 }
            classStudents.filter { it.badgeStatus == StudentBadgeStatus.AFK }.forEach { stu ->
                result[stu.id] = null
            }
        }
        return result
    }

    private fun computeGlobalRankMap(snapshot: ExportDatasetSnapshot): Map<String, Int?> {
        val totalXp = computeTotalXpMap(snapshot.xpTransactions)
        val result = mutableMapOf<String, Int?>()
        val active = snapshot.students
            .filter { it.badgeStatus != StudentBadgeStatus.AFK }
            .sortedWith(
                compareByDescending<StudentRecord> { totalXp[it.id] ?: 0 }
                    .thenBy { it.fullName.lowercase(Locale.US) }
                    .thenBy { it.nim }
            )
        active.forEachIndexed { idx, stu -> result[stu.id] = idx + 1 }
        snapshot.students.filter { it.badgeStatus == StudentBadgeStatus.AFK }.forEach { stu ->
            result[stu.id] = null
        }
        return result
    }

    private fun <T> mergeById(remote: List<T>?, local: List<T>, keySelector: (T) -> String): List<T> {
        if (remote == null) return local
        val map = LinkedHashMap<String, T>()
        remote.forEach { map[keySelector(it)] = it }
        local.forEach { item ->
            val key = keySelector(item)
            if (!map.containsKey(key)) map[key] = item
        }
        return map.values.toList()
    }

    // =========================================================================
    // SUPABASE PAGINATED LIVE FETCHERS (EXISTING TABLES ONLY, ZERO SCHEMA CHANGE)
    // =========================================================================

    private fun fetchPaginatedJsonArray(baseUrl: String, pathAndQuery: String, jwt: String): JSONArray? {
        val merged = JSONArray()
        var succeededAtLeastOnce = false
        for (page in 0 until MAX_PAGES) {
            val from = page * PAGE_SIZE
            val to = from + PAGE_SIZE - 1
            val req = Request.Builder()
                .url("$baseUrl$pathAndQuery")
                .get()
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Authorization", "Bearer $jwt")
                .addHeader("Range-Unit", "items")
                .addHeader("Range", "$from-$to")
                .build()
            val pageArray = runCatching {
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful && resp.code != 206) return@use null
                    val body = resp.body?.string().orEmpty().trim()
                    if (body.isEmpty()) JSONArray() else JSONArray(body)
                }
            }.getOrNull() ?: break

            succeededAtLeastOnce = true
            for (i in 0 until pageArray.length()) {
                merged.put(pageArray.get(i))
            }
            if (pageArray.length() < PAGE_SIZE) break
        }
        return if (succeededAtLeastOnce) merged else null
    }

    private fun fetchStudyPrograms(base: String, jwt: String): List<StudyProgram>? {
        val arr = fetchPaginatedJsonArray(base, "/rest/v1/study_programs?select=*&order=code.asc", jwt) ?: return null
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                StudyProgram(
                    id = o.getString("id"),
                    code = o.optString("code", ""),
                    name = o.optString("name", o.optString("code", "")),
                    faculty = o.optString("faculty", ""),
                    description = o.optString("description", ""),
                    isActive = o.optBoolean("is_active", true)
                )
            }.getOrNull()
        }
    }

    private fun fetchClasses(
        base: String,
        jwt: String,
        prodiById: Map<String, StudyProgram>,
        prodiByCode: Map<String, StudyProgram>
    ): List<AcademicClass>? {
        val arr = fetchPaginatedJsonArray(base, "/rest/v1/classes?select=*&order=name.asc", jwt) ?: return null
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                val pId = o.optString("study_program_id").takeIf { it.isNotBlank() && it != "null" }
                val pCode = o.optString("study_program_code").takeIf { it.isNotBlank() && it != "null" }
                val resolvedProdi = pId?.let { prodiById[it] } ?: pCode?.let { prodiByCode[it.uppercase(Locale.US)] }
                AcademicClass(
                    id = o.getString("id"),
                    name = o.optString("name", ""),
                    studyProgramId = resolvedProdi?.id ?: pId.orEmpty(),
                    studyProgramName = resolvedProdi?.code ?: pCode.orEmpty(),
                    semester = o.optInt("semester", 1),
                    academicYear = o.optString("academic_year", "2026/2027"),
                    cohortYear = o.optInt("cohort_year", 2026),
                    description = o.optString("description", ""),
                    isActive = o.optBoolean("is_active", true)
                )
            }.getOrNull()
        }
    }

    private fun fetchStudentProfiles(
        base: String,
        jwt: String,
        prodiById: Map<String, StudyProgram>,
        classById: Map<String, AcademicClass>
    ): List<StudentRecord>? {
        val arr = fetchPaginatedJsonArray(base, "/rest/v1/profiles?select=*&role=eq.student&order=full_name.asc", jwt) ?: return null
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                val pId = o.optString("study_program_id").takeIf { it.isNotBlank() && it != "null" }.orEmpty()
                val cId = o.optString("class_id").takeIf { it.isNotBlank() && it != "null" }
                val cls = cId?.let { classById[it] }
                val prodi = prodiById[pId]
                val statusStr = o.optString("account_status", "active").lowercase(Locale.US)
                val status = when (statusStr) {
                    "inactive" -> AccountStatus.INACTIVE
                    "pending_activation" -> AccountStatus.PENDING_ACTIVATION
                    else -> AccountStatus.ACTIVE
                }
                StudentRecord(
                    id = o.getString("id"),
                    fullName = o.optString("full_name", "Mahasiswa"),
                    nim = o.optString("nim", "-"),
                    email = o.optString("email", ""),
                    studyProgramId = prodi?.id ?: pId,
                    studyProgramName = prodi?.code ?: cls?.studyProgramName.orEmpty().ifBlank { "-" },
                    classId = cId,
                    className = cls?.name ?: o.optString("imported_from_sheet").takeIf { it.isNotBlank() && it != "null" },
                    semester = o.optInt("semester", 1),
                    cohortYear = o.optInt("cohort_year", 2026),
                    status = status,
                    badgeStatus = StudentBadgeStatus.fromDbValue(o.optString("student_badge_status", "neutral")),
                    mustChangePassword = o.optBoolean("must_change_password", false)
                )
            }.getOrNull()
        }
    }

    private fun fetchAssignments(
        base: String,
        jwt: String,
        knownClasses: List<AcademicClass>,
        knownProdis: List<StudyProgram>
    ): List<Assignment>? {
        val arr = fetchPaginatedJsonArray(base, "/rest/v1/assignments?select=*,assignment_targets(*)&order=created_at.desc", jwt)
            ?: return null
        val classById = knownClasses.associateBy { it.id }
        val prodiById = knownProdis.associateBy { it.id }
        val prodiByCode = knownProdis.associateBy { it.code.uppercase(Locale.US) }

        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                val targetArr = o.optJSONArray("assignment_targets")
                val targetClassIds = mutableListOf<String>()
                val targetClassNames = mutableListOf<String>()
                val targetProdiIds = mutableListOf<String>()
                val targetProdiCodes = mutableListOf<String>()

                if (targetArr != null) {
                    for (tIdx in 0 until targetArr.length()) {
                        val t = targetArr.getJSONObject(tIdx)
                        val cId = t.optString("class_id").takeIf { it.isNotBlank() && it != "null" }
                        val pId = t.optString("study_program_id").takeIf { it.isNotBlank() && it != "null" }
                        val pCode = t.optString("study_program_code").takeIf { it.isNotBlank() && it != "null" }
                        if (cId != null && cId !in targetClassIds) {
                            targetClassIds += cId
                            targetClassNames += (classById[cId]?.name ?: cId)
                        }
                        if (pId != null && pId !in targetProdiIds) targetProdiIds += pId
                        val resolvedCode = pCode ?: pId?.let { prodiById[it]?.code }
                        if (resolvedCode != null && resolvedCode !in targetProdiCodes) {
                            targetProdiCodes += resolvedCode.uppercase(Locale.US)
                        }
                        if (pId == null && resolvedCode != null) {
                            prodiByCode[resolvedCode.uppercase(Locale.US)]?.id?.let { mappedId ->
                                if (mappedId !in targetProdiIds) targetProdiIds += mappedId
                            }
                        }
                    }
                }

                val targetType = AssignmentTargetType.fromDbValue(o.optString("target_type", "class"))
                val deadlineMillis = AssignmentSyncService.parseIsoTimestampMillis(o.optString("deadline"))
                val createdMillis = AssignmentSyncService.parseIsoTimestampMillis(o.optString("created_at"))
                Assignment(
                    id = o.getString("id"),
                    title = o.optString("title", "Tugas"),
                    description = o.optString("description", ""),
                    instructions = o.optString("instructions", o.optString("description", "")),
                    classId = targetClassIds.firstOrNull().orEmpty(),
                    className = when (targetType) {
                        AssignmentTargetType.CLASS -> targetClassNames.joinToString(", ")
                        AssignmentTargetType.STUDY_PROGRAM ->
                            if (targetProdiCodes.isEmpty()) "Program Studi" else "Prodi ${targetProdiCodes.joinToString(", ")}"
                    },
                    targetType = targetType,
                    targetClassIds = targetClassIds,
                    targetClassNames = targetClassNames,
                    targetStudyProgramIds = targetProdiIds,
                    targetStudyProgramCodes = targetProdiCodes,
                    courseCategory = o.optString("course_category", "Umum"),
                    createdAt = createdMillis?.let { XpAndStreakEngine.formatWibDateTimeLabel(it) } ?: o.optString("created_at", "-"),
                    deadline = deadlineMillis?.let { XpAndStreakEngine.formatWibDateTimeLabel(it) } ?: o.optString("deadline", "-"),
                    deadlineEpochMillis = deadlineMillis,
                    submissionMethod = runCatching {
                        SubmissionMethod.valueOf(o.optString("submission_method", SubmissionMethod.BOTH.name))
                    }.getOrDefault(SubmissionMethod.BOTH),
                    file1Name = o.optString("file1_name").takeIf { it.isNotBlank() && it != "null" },
                    file1Url = o.optString("file1_url").takeIf { it.isNotBlank() && it != "null" },
                    file2Name = o.optString("file2_name").takeIf { it.isNotBlank() && it != "null" },
                    file2Url = o.optString("file2_url").takeIf { it.isNotBlank() && it != "null" },
                    isActive = o.optBoolean("is_active", true)
                )
            }.getOrNull()
        }
    }

    private fun fetchSubmissions(
        base: String,
        jwt: String,
        students: List<StudentRecord>,
        assignments: List<Assignment>
    ): List<SubmissionRecord>? {
        val arr = fetchPaginatedJsonArray(base, "/rest/v1/assignment_submissions?select=*&order=last_updated_at.desc", jwt)
            ?: return null
        val studentById = students.associateBy { it.id }
        val assignmentById = assignments.associateBy { it.id }

        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                val assignmentId = o.getString("assignment_id")
                val studentId = o.getString("student_id")
                val assignment = assignmentById[assignmentId]
                val student = studentById[studentId]
                val firstMillis = AssignmentSyncService.parseIsoTimestampMillis(o.optString("first_submitted_at")) ?: 0L
                val updatedMillis = AssignmentSyncService.parseIsoTimestampMillis(o.optString("last_updated_at")) ?: firstMillis
                val firstLabel = if (firstMillis > 0L) XpAndStreakEngine.formatWibDateTimeLabel(firstMillis) else "-"
                val updatedLabel = if (updatedMillis > 0L) XpAndStreakEngine.formatWibDateTimeLabel(updatedMillis) else firstLabel
                val statusStr = o.optString("status", "awaiting_admin").lowercase(Locale.US)
                val isLate = statusStr == "late" ||
                    (assignment?.deadlineEpochMillis != null && firstMillis > assignment.deadlineEpochMillis)
                val status = when (statusStr) {
                    "late" -> SubmissionStatus.LATE
                    "submitted" -> SubmissionStatus.SUBMITTED
                    "needs_revision" -> SubmissionStatus.NEEDS_REVISION
                    "graded" -> SubmissionStatus.GRADED
                    else -> SubmissionStatus.AWAITING_ADMIN
                }
                SubmissionRecord(
                    id = o.getString("id"),
                    assignmentId = assignmentId,
                    assignmentTitle = assignment?.title ?: "Tugas",
                    className = student?.className ?: assignment?.targetSummaryLabel().orEmpty(),
                    studentId = studentId,
                    studentName = student?.fullName ?: "Mahasiswa",
                    studentNim = student?.nim ?: "-",
                    answerText = o.optString("answer_text").takeIf { it.isNotBlank() && it != "null" },
                    driveLink = o.optString("submission_url").takeIf { it.isNotBlank() && it != "null" },
                    uploadedFileName = o.optString("file_name").takeIf { it.isNotBlank() && it != "null" },
                    uploadedFileUrl = o.optString("file_url").takeIf { it.isNotBlank() && it != "null" },
                    uploadedFileSizeLabel = o.optString("file_size_label").takeIf { it.isNotBlank() && it != "null" },
                    submittedAt = updatedLabel,
                    firstSubmittedAt = firstLabel,
                    lastUpdatedAt = updatedLabel,
                    firstSubmittedAtMillis = firstMillis,
                    lastUpdatedAtMillis = updatedMillis,
                    status = status,
                    isLate = isLate,
                    revisionCount = o.optInt("revision_count", 0),
                    finalScore = if (!o.isNull("final_score")) o.optInt("final_score") else null,
                    adminFeedback = o.optString("admin_feedback").takeIf { it.isNotBlank() && it != "null" }
                )
            }.getOrNull()
        }
    }

    private fun fetchChecklists(base: String, jwt: String): List<AssignmentChecklistRecord>? {
        val arr = fetchPaginatedJsonArray(base, "/rest/v1/assignment_student_checklists?select=*", jwt) ?: return null
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                val verifiedMillis = AssignmentSyncService.parseIsoTimestampMillis(o.optString("verified_at"))
                val updatedMillis = AssignmentSyncService.parseIsoTimestampMillis(o.optString("updated_at")) ?: System.currentTimeMillis()
                AssignmentChecklistRecord(
                    assignmentId = o.getString("assignment_id"),
                    studentId = o.getString("student_id"),
                    isCompleted = o.optBoolean("is_completed", false),
                    verifiedById = o.optString("verified_by").takeIf { it.isNotBlank() && it != "null" },
                    verifiedByName = o.optString("verified_by_name").takeIf { it.isNotBlank() && it != "null" },
                    verifiedAt = verifiedMillis?.let { XpAndStreakEngine.formatWibDateTimeLabel(it) },
                    verificationNote = o.optString("verification_note").takeIf { it.isNotBlank() && it != "null" },
                    updatedAtMillis = updatedMillis
                )
            }.getOrNull()
        }
    }

    private fun fetchXpTransactions(base: String, jwt: String): List<RankingPointTransaction>? {
        val primary = fetchPaginatedJsonArray(base, "/rest/v1/xp_transactions?select=*&order=created_at.desc", jwt)
        if (primary != null && primary.length() > 0) {
            return (0 until primary.length()).mapNotNull { i ->
                runCatching {
                    val o = primary.getJSONObject(i)
                    val createdMillis = AssignmentSyncService.parseIsoTimestampMillis(o.optString("created_at"))
                        ?: System.currentTimeMillis()
                    val label = XpAndStreakEngine.formatWibDateTimeLabel(createdMillis)
                    val refType = o.optString("reference_type", "MANUAL_ADJUSTMENT").uppercase(Locale.US)
                    val sourceType = when (refType) {
                        "ATTENDANCE" -> PointSourceType.ATTENDANCE
                        "ASSIGNMENT" -> PointSourceType.ASSIGNMENT
                        "PARTICIPATION" -> PointSourceType.PARTICIPATION
                        "STREAK_MILESTONE" -> PointSourceType.STREAK_BONUS
                        else -> PointSourceType.MANUAL_ADJUSTMENT
                    }
                    val cat = runCatching { XpCategory.valueOf(o.optString("category")) }
                        .getOrElse { XpCategory.fromSourceType(sourceType) }
                    RankingPointTransaction(
                        id = o.getString("id"),
                        studentId = o.getString("student_id"),
                        sourceType = sourceType,
                        sourceId = o.optString("reference_id", ""),
                        points = o.optInt("xp_amount", 0),
                        description = o.optString("description", ""),
                        createdAt = label,
                        updatedAt = label,
                        timestampMillis = createdMillis,
                        xpCategory = cat,
                        activityTitle = o.optString("activity_title", o.optString("description", "")),
                        semesterKey = o.optString("semester_key", "2026/2027-Ganjil"),
                        adminId = o.optString("admin_id").takeIf { it.isNotBlank() && it != "null" }
                    )
                }.getOrNull()
            }
        }

        // Fallback to legacy ranking_points table if xp_transactions is empty
        val legacy = fetchPaginatedJsonArray(base, "/rest/v1/ranking_points?select=*&order=created_at.desc", jwt)
            ?: return primary?.let { emptyList() }
        return (0 until legacy.length()).mapNotNull { i ->
            runCatching {
                val o = legacy.getJSONObject(i)
                val createdMillis = AssignmentSyncService.parseIsoTimestampMillis(o.optString("created_at"))
                    ?: System.currentTimeMillis()
                val label = XpAndStreakEngine.formatWibDateTimeLabel(createdMillis)
                val sourceType = runCatching {
                    PointSourceType.valueOf(o.optString("source_type", PointSourceType.MANUAL_ADJUSTMENT.name))
                }.getOrDefault(PointSourceType.MANUAL_ADJUSTMENT)
                RankingPointTransaction(
                    id = o.getString("id"),
                    studentId = o.getString("student_id"),
                    sourceType = sourceType,
                    sourceId = o.optString("source_id", ""),
                    points = o.optInt("points", 0),
                    description = o.optString("description", ""),
                    createdAt = label,
                    updatedAt = label,
                    timestampMillis = createdMillis,
                    xpCategory = XpCategory.fromSourceType(sourceType),
                    activityTitle = o.optString("description", "")
                )
            }.getOrNull()
        }
    }

    private fun fetchHistoricalAttendance(base: String, jwt: String): List<HistoricalAttendanceRecord>? {
        val arr = fetchPaginatedJsonArray(base, "/rest/v1/historical_attendance_records?select=*&order=imported_at.desc", jwt)
            ?: return null
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                val impMillis = AssignmentSyncService.parseIsoTimestampMillis(o.optString("imported_at"))
                HistoricalAttendanceRecord(
                    id = o.getString("id"),
                    studentId = o.getString("student_id"),
                    studentNim = o.optString("student_nim", "-"),
                    studentName = o.optString("student_name", "Mahasiswa"),
                    classId = o.optString("class_id").takeIf { it.isNotBlank() && it != "null" },
                    className = o.optString("class_name", "-"),
                    studyProgramCode = o.optString("study_program_code", "-"),
                    weekNumber = o.optInt("week_number", 1),
                    weekLabel = o.optString("week_label", "Minggu 1"),
                    rawStatus = o.optString("raw_status", "-"),
                    sourceSheet = o.optString("source_sheet", "-"),
                    semesterKey = o.optString("semester_key", "2026/2027-Ganjil"),
                    importedAt = impMillis?.let { XpAndStreakEngine.formatWibDateTimeLabel(it) } ?: o.optString("imported_at", ""),
                    xpCalculated = o.optBoolean("xp_calculated", false)
                )
            }.getOrNull()
        }
    }

    private fun fetchLiveAttendanceRecords(
        base: String,
        jwt: String,
        students: List<StudentRecord>
    ): List<AttendanceRecord>? {
        val arr = fetchPaginatedJsonArray(base, "/rest/v1/attendance_records?select=*&order=created_at.desc", jwt)
            ?: return null
        val studentById = students.associateBy { it.id }
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                val studentId = o.getString("student_id")
                val stu = studentById[studentId]
                val ts = AssignmentSyncService.parseIsoTimestampMillis(
                    o.optString("recorded_at").ifBlank { o.optString("created_at") }
                ) ?: System.currentTimeMillis()
                val statusStr = o.optString("status", "HADIR").uppercase(Locale.US)
                val status = runCatching { AttendanceStatus.valueOf(statusStr) }.getOrDefault(AttendanceStatus.HADIR)
                AttendanceRecord(
                    id = o.getString("id"),
                    sessionId = o.optString("session_id", ""),
                    meetingTitle = o.optString("meeting_title", "Sesi Absensi"),
                    className = o.optString("class_name", stu?.className ?: "-"),
                    studentId = studentId,
                    studentName = o.optString("student_name", stu?.fullName ?: "Mahasiswa"),
                    studentNim = o.optString("student_nim", stu?.nim ?: "-"),
                    dateLabel = o.optString("date_label").ifBlank { formatWibDateOnly(ts) },
                    recordedAt = formatWibTimeOnly(ts),
                    status = status,
                    correctionReason = o.optString("correction_reason")
                        .ifBlank { o.optString("notes") }
                        .takeIf { it.isNotBlank() && it != "null" },
                    semesterKey = o.optString("semester_key", "2026/2027-Ganjil"),
                    timestampMillis = ts
                )
            }.getOrNull()
        }
    }
}
