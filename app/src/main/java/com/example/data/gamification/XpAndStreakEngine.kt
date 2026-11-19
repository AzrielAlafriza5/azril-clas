package com.example.data.gamification

import com.example.ui.model.Assignment
import com.example.ui.model.AttendanceRecord
import com.example.ui.model.AttendanceStatus
import com.example.ui.model.ParticipationXpCategory
import com.example.ui.model.PointSourceType
import com.example.ui.model.RankingPointTransaction
import com.example.ui.model.StreakNotificationLog
import com.example.ui.model.StreakReminderSlot
import com.example.ui.model.StudentIzinQuotaSummary
import com.example.ui.model.StudentStreakState
import com.example.ui.model.SubmissionTimeliness
import com.example.ui.model.UserProfile
import com.example.ui.model.UserRole
import com.example.ui.model.XpCategory
import com.example.ui.model.XpHistoryTimeRange
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class StreakDayVisual(
    val dateWib: String,
    val dayShortLabel: String,
    val dayOfMonth: String,
    val loggedIn: Boolean,
    val isToday: Boolean
)

data class StreakLoginUpdateResult(
    val updatedStreak: StudentStreakState,
    val updatedTransactions: List<RankingPointTransaction>,
    val isFirstLoginToday: Boolean,
    val awardedMilestoneDays: Int?,
    val awardedBonusXp: Int
)

data class StreakReminderDispatchResult(
    val newlyDispatched: List<StreakNotificationLog>,
    val updatedLogs: List<StreakNotificationLog>
)

/**
 * Pure, deterministic gamification engine for Azril Clas.
 * All date/time computations strictly use Asia/Jakarta (WIB).
 * Total XP is ALWAYS derived by summing stored [RankingPointTransaction] rows.
 */
object XpAndStreakEngine {
    val WIB_TIME_ZONE: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")
    private val ID_LOCALE: Locale = Locale.forLanguageTag("id-ID")

    const val XP_HADIR = 20
    const val XP_IZIN_PAID = 10
    const val XP_IZIN_EXCEEDED = 0
    const val XP_ALFA = 0
    const val XP_SAKIT = 0

    const val XP_ASSIGNMENT_EARLY = 35
    const val XP_ASSIGNMENT_ON_TIME = 30
    const val XP_ASSIGNMENT_LATE = 10

    const val EARLY_SUBMISSION_WINDOW_MS = 24L * 60L * 60L * 1000L

    val STREAK_MILESTONE_BONUSES: Map<Int, Int> = linkedMapOf(
        3 to 10,
        7 to 30,
        14 to 50,
        30 to 100
    )

    fun formatWibDate(epochMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = WIB_TIME_ZONE }
        return sdf.format(Date(epochMillis))
    }

    fun formatWibMonth(epochMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US).apply { timeZone = WIB_TIME_ZONE }
        return sdf.format(Date(epochMillis))
    }

    fun formatWibDateTimeLabel(epochMillis: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm 'WIB'", ID_LOCALE).apply { timeZone = WIB_TIME_ZONE }
        return sdf.format(Date(epochMillis))
    }

    fun currentWibHour(epochMillis: Long): Int {
        val cal = Calendar.getInstance(WIB_TIME_ZONE).apply { timeInMillis = epochMillis }
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    fun shiftWibDate(dateWib: String, deltaDays: Int): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = WIB_TIME_ZONE
            isLenient = false
        }
        val base = sdf.parse(dateWib) ?: Date()
        val cal = Calendar.getInstance(WIB_TIME_ZONE).apply {
            time = base
            add(Calendar.DAY_OF_YEAR, deltaDays)
        }
        return sdf.format(cal.time)
    }

    /**
     * Computes individual student's Izin usage and remaining paid XP quota for a given semester.
     */
    fun computeStudentIzinQuota(
        studentId: String,
        semesterKey: String,
        attendanceRecords: List<AttendanceRecord>,
        maxPaidIzin: Int = 2
    ): StudentIzinQuotaSummary {
        val izinCount = attendanceRecords.count {
            it.studentId == studentId &&
                it.semesterKey == semesterKey &&
                it.status == AttendanceStatus.IZIN
        }
        val paidUsed = izinCount.coerceAtMost(maxPaidIzin)
        val remaining = (maxPaidIzin - paidUsed).coerceAtLeast(0)
        return StudentIzinQuotaSummary(
            studentId = studentId,
            semesterKey = semesterKey,
            usedIzinCount = izinCount,
            paidIzinUsed = paidUsed,
            remainingPaidIzin = remaining,
            maxPaidIzin = maxPaidIzin
        )
    }

    /**
     * Recomputes all ATTENDANCE XP transactions for [studentId] within [semesterKey] chronologically.
     * Guarantees:
     * - Hadir = +20 XP
     * - Izin #1 = +10 XP, Izin #2 = +10 XP, Izin #3+ = 0 XP (still recorded!)
     * - Alfa = 0 XP
     * - Zero duplicate transactions per (studentId, ATTENDANCE, sessionId)
     * - Full consistency whenever Admin corrects any attendance status.
     */
    fun recalculateAttendanceXpForStudentSemester(
        studentId: String,
        semesterKey: String,
        attendanceRecords: List<AttendanceRecord>,
        existingTransactions: List<RankingPointTransaction>,
        maxPaidIzin: Int = 2,
        nowMillis: Long = System.currentTimeMillis()
    ): List<RankingPointTransaction> {
        val studentSemesterAttendance = attendanceRecords
            .filter { it.studentId == studentId && it.semesterKey == semesterKey }
            .sortedWith(compareBy<AttendanceRecord> { it.timestampMillis }.thenBy { it.id })

        val nowLabel = formatWibDateTimeLabel(nowMillis)
        var izinOrdinal = 0

        // Index existing attendance transactions for this student & semester to preserve IDs/createdAt
        val existingBySession = existingTransactions
            .filter { it.studentId == studentId && it.sourceType == PointSourceType.ATTENDANCE }
            .associateBy { it.sourceId }

        val updatedAttendanceTxs = mutableListOf<RankingPointTransaction>()
        val processedSessionIds = mutableSetOf<String>()

        for (rec in studentSemesterAttendance) {
            if (!processedSessionIds.add(rec.sessionId)) continue

            val (xp, category, title, desc) = when (rec.status) {
                AttendanceStatus.HADIR -> Quadruple(
                    XP_HADIR,
                    XpCategory.ATTENDANCE_HADIR,
                    "Hadir dalam pembelajaran",
                    "Kehadiran pada ${rec.meetingTitle} (${rec.className})"
                )
                AttendanceStatus.IZIN -> {
                    izinOrdinal += 1
                    val earned = if (izinOrdinal <= maxPaidIzin) XP_IZIN_PAID else XP_IZIN_EXCEEDED
                    val quotaNote = if (izinOrdinal <= maxPaidIzin) {
                        "Izin ke-$izinOrdinal dari $maxPaidIzin kuota XP semester $semesterKey (${rec.meetingTitle})"
                    } else {
                        "Izin ke-$izinOrdinal semester $semesterKey — batas kuota XP izin ($maxPaidIzin/semester) telah terpakai (${rec.meetingTitle})"
                    }
                    Quadruple(
                        earned,
                        XpCategory.ATTENDANCE_IZIN,
                        if (izinOrdinal <= maxPaidIzin) "Izin (Ke-$izinOrdinal)" else "Izin (Kuota XP Habis)",
                        quotaNote
                    )
                }
                AttendanceStatus.SAKIT -> Quadruple(
                    XP_SAKIT,
                    XpCategory.ATTENDANCE_IZIN,
                    "Sakit",
                    "Catatan sakit pada ${rec.meetingTitle} (${rec.className})"
                )
                AttendanceStatus.ALFA -> Quadruple(
                    XP_ALFA,
                    XpCategory.ATTENDANCE_ALFA,
                    "Alfa",
                    "Tidak hadir tanpa keterangan pada ${rec.meetingTitle}"
                )
            }

            val prev = existingBySession[rec.sessionId]
            updatedAttendanceTxs.add(
                RankingPointTransaction(
                    id = prev?.id ?: UUID.randomUUID().toString(),
                    studentId = studentId,
                    sourceType = PointSourceType.ATTENDANCE,
                    sourceId = rec.sessionId,
                    points = xp,
                    description = desc,
                    createdAt = prev?.createdAt ?: rec.recordedAt.ifBlank { nowLabel },
                    updatedAt = nowLabel,
                    timestampMillis = prev?.timestampMillis ?: rec.timestampMillis,
                    xpCategory = category,
                    activityTitle = title,
                    semesterKey = semesterKey
                )
            )
        }

        val unaffected = existingTransactions.filterNot { tx ->
            tx.studentId == studentId &&
                tx.sourceType == PointSourceType.ATTENDANCE &&
                (tx.semesterKey == semesterKey || tx.sourceId in processedSessionIds)
        }
        return (updatedAttendanceTxs + unaffected).sortedByDescending { it.timestampMillis }
    }

    /**
     * Determines assignment submission timeliness:
     * - > 24h before deadline -> BEFORE_DEADLINE (+35 XP)
     * - within 24h up to deadline -> ON_TIME (+30 XP)
     * - after deadline -> LATE (+10 XP)
     */
    fun determineSubmissionTimeliness(
        assignment: Assignment,
        submittedAtMillis: Long = System.currentTimeMillis()
    ): SubmissionTimeliness {
        val deadline = assignment.deadlineEpochMillis ?: return SubmissionTimeliness.ON_TIME
        return when {
            submittedAtMillis > deadline -> SubmissionTimeliness.LATE
            deadline - submittedAtMillis >= EARLY_SUBMISSION_WINDOW_MS -> SubmissionTimeliness.BEFORE_DEADLINE
            else -> SubmissionTimeliness.ON_TIME
        }
    }

    /**
     * Upserts a single unique XP transaction per (studentId, ASSIGNMENT, assignmentId).
     * Re-submitting or refreshing NEVER duplicates XP.
     */
    fun upsertAssignmentSubmissionXp(
        existingTransactions: List<RankingPointTransaction>,
        studentId: String,
        assignment: Assignment,
        timeliness: SubmissionTimeliness,
        semesterKey: String,
        submittedAtMillis: Long = System.currentTimeMillis()
    ): List<RankingPointTransaction> {
        val nowLabel = formatWibDateTimeLabel(submittedAtMillis)
        val prev = existingTransactions.find {
            it.studentId == studentId &&
                it.sourceType == PointSourceType.ASSIGNMENT &&
                it.sourceId == assignment.id
        }
        val title = when (timeliness) {
            SubmissionTimeliness.BEFORE_DEADLINE -> "Mengumpulkan tugas sebelum deadline"
            SubmissionTimeliness.ON_TIME -> "Mengumpulkan tugas tepat waktu"
            SubmissionTimeliness.LATE -> "Mengumpulkan tugas terlambat"
        }
        val desc = "Tugas '${assignment.title}' (${timeliness.label})"
        val updatedTx = RankingPointTransaction(
            id = prev?.id ?: UUID.randomUUID().toString(),
            studentId = studentId,
            sourceType = PointSourceType.ASSIGNMENT,
            sourceId = assignment.id,
            points = timeliness.xpAmount,
            description = desc,
            createdAt = prev?.createdAt ?: nowLabel,
            updatedAt = nowLabel,
            timestampMillis = prev?.timestampMillis ?: submittedAtMillis,
            xpCategory = timeliness.xpCategory,
            activityTitle = title,
            semesterKey = semesterKey
        )
        val rest = existingTransactions.filterNot {
            it.studentId == studentId &&
                it.sourceType == PointSourceType.ASSIGNMENT &&
                it.sourceId == assignment.id
        }
        return (listOf(updatedTx) + rest).sortedByDescending { it.timestampMillis }
    }

    /**
     * Creates a manual participation XP transaction from Admin (ONLY Regular +10 XP or Critical +20 XP).
     * Stores Admin ID, Admin Name, reason/note, and WIB timestamp.
     */
    fun createParticipationXpTransaction(
        studentId: String,
        category: ParticipationXpCategory,
        reasonNote: String,
        adminId: String,
        adminName: String,
        semesterKey: String,
        nowMillis: Long = System.currentTimeMillis(),
        referenceId: String = "part-${UUID.randomUUID()}"
    ): RankingPointTransaction {
        val nowLabel = formatWibDateTimeLabel(nowMillis)
        val cleanNote = reasonNote.trim().ifBlank { "${category.label} dalam diskusi kelas" }
        return RankingPointTransaction(
            id = UUID.randomUUID().toString(),
            studentId = studentId,
            sourceType = PointSourceType.PARTICIPATION,
            sourceId = referenceId,
            points = category.xpAmount,
            description = cleanNote,
            createdAt = nowLabel,
            updatedAt = nowLabel,
            timestampMillis = nowMillis,
            xpCategory = category.xpCategory,
            activityTitle = category.label,
            semesterKey = semesterKey,
            adminId = adminId,
            adminName = adminName
        )
    }

    /**
     * Records a student's daily login in Asia/Jakarta (WIB):
     * - Multiple logins on the same WIB date do NOT increase streak or duplicate XP.
     * - Consecutive WIB day increments streak by +1 in the same streakCycleId.
     * - Skipping 1+ full WIB calendar day resets streak to 1 in a fresh streakCycleId.
     * - Awards milestone bonus XP (3d=10, 7d=30, 14d=50, 30d=100) strictly once per streak cycle.
     */
    fun recordStudentDailyLogin(
        studentId: String,
        existingStreak: StudentStreakState?,
        existingTransactions: List<RankingPointTransaction>,
        semesterKey: String,
        nowMillis: Long = System.currentTimeMillis()
    ): StreakLoginUpdateResult {
        val todayWib = formatWibDate(nowMillis)
        val yesterdayWib = shiftWibDate(todayWib, -1)
        val base = existingStreak ?: StudentStreakState(studentId = studentId)

        // Already logged in today (WIB) -> idempotent no-op
        if (base.lastLoginDateWib == todayWib) {
            return StreakLoginUpdateResult(
                updatedStreak = base,
                updatedTransactions = existingTransactions,
                isFirstLoginToday = false,
                awardedMilestoneDays = null,
                awardedBonusXp = 0
            )
        }

        val isConsecutive = base.lastLoginDateWib == yesterdayWib && base.currentStreak > 0
        val newStreak = if (isConsecutive) base.currentStreak + 1 else 1
        val newLongest = maxOf(base.longestStreak, newStreak)
        val cycleId = if (isConsecutive) base.streakCycleId else UUID.randomUUID().toString()
        val claimedInCycle = if (isConsecutive) base.claimedMilestonesInCycle else emptyList()
        val mergedDates = (base.loginDatesWib + todayWib).distinct().sorted().takeLast(60)

        var awardedDays: Int? = null
        var awardedXp = 0
        var nextClaimed = claimedInCycle
        var nextTransactions = existingTransactions

        val bonusForDay = STREAK_MILESTONE_BONUSES[newStreak]
        if (bonusForDay != null && newStreak !in claimedInCycle) {
            val milestoneSourceId = "streak:$cycleId:$newStreak"
            val alreadyInLedger = existingTransactions.any {
                it.studentId == studentId &&
                    it.sourceType == PointSourceType.STREAK_BONUS &&
                    it.sourceId == milestoneSourceId
            }
            if (!alreadyInLedger) {
                val nowLabel = formatWibDateTimeLabel(nowMillis)
                val bonusTx = RankingPointTransaction(
                    studentId = studentId,
                    sourceType = PointSourceType.STREAK_BONUS,
                    sourceId = milestoneSourceId,
                    points = bonusForDay,
                    description = "Pencapaian konsistensi login $newStreak hari berturut-turut (WIB)",
                    createdAt = nowLabel,
                    updatedAt = nowLabel,
                    timestampMillis = nowMillis,
                    xpCategory = XpCategory.STREAK_BONUS,
                    activityTitle = "Bonus Login Streak $newStreak Hari",
                    semesterKey = semesterKey
                )
                nextTransactions = (listOf(bonusTx) + existingTransactions).sortedByDescending { it.timestampMillis }
                awardedDays = newStreak
                awardedXp = bonusForDay
            }
            nextClaimed = (claimedInCycle + newStreak).distinct()
        }

        val updatedStreak = StudentStreakState(
            studentId = studentId,
            currentStreak = newStreak,
            longestStreak = newLongest,
            lastLoginDateWib = todayWib,
            streakCycleId = cycleId,
            loginDatesWib = mergedDates,
            claimedMilestonesInCycle = nextClaimed
        )

        return StreakLoginUpdateResult(
            updatedStreak = updatedStreak,
            updatedTransactions = nextTransactions,
            isFirstLoginToday = true,
            awardedMilestoneDays = awardedDays,
            awardedBonusXp = awardedXp
        )
    }

    /**
     * Computes effective active streak as of [nowMillis] in WIB:
     * If the student missed a full calendar day (last login is neither today nor yesterday),
     * returns 0!
     */
    fun effectiveCurrentStreak(
        streak: StudentStreakState?,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        if (streak == null || streak.lastLoginDateWib.isNullOrBlank()) return 0
        val todayWib = formatWibDate(nowMillis)
        val yesterdayWib = shiftWibDate(todayWib, -1)
        return if (streak.lastLoginDateWib == todayWib || streak.lastLoginDateWib == yesterdayWib) {
            streak.currentStreak
        } else {
            0
        }
    }

    fun hasLoggedInTodayWib(
        streak: StudentStreakState?,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (streak == null) return false
        val todayWib = formatWibDate(nowMillis)
        return streak.lastLoginDateWib == todayWib || streak.loginDatesWib.contains(todayWib)
    }

    /**
     * Builds the last 7 calendar days (WIB) visual strip for the Student Dashboard Login Streak card.
     */
    fun buildLast7DaysVisual(
        streak: StudentStreakState?,
        nowMillis: Long = System.currentTimeMillis()
    ): List<StreakDayVisual> {
        val todayWib = formatWibDate(nowMillis)
        val loggedSet = streak?.loginDatesWib?.toSet() ?: emptySet()
        val parseSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = WIB_TIME_ZONE }
        val dayNameSdf = SimpleDateFormat("EEE", ID_LOCALE).apply { timeZone = WIB_TIME_ZONE }
        val dayNumSdf = SimpleDateFormat("dd", Locale.US).apply { timeZone = WIB_TIME_ZONE }

        return (-6..0).map { offset ->
            val dStr = shiftWibDate(todayWib, offset)
            val parsedDate = parseSdf.parse(dStr) ?: Date(nowMillis)
            StreakDayVisual(
                dateWib = dStr,
                dayShortLabel = dayNameSdf.format(parsedDate).take(3),
                dayOfMonth = dayNumSdf.format(parsedDate),
                loggedIn = dStr in loggedSet,
                isToday = dStr == todayWib
            )
        }
    }

    /**
     * Evaluates streak reminder notifications for 08:00, 12:00, and 17:00 WIB.
     * - Immediately stops reminders if the student already logged in today (WIB).
     * - Never dispatches duplicate notifications for the same (studentId, dateWib, slot).
     */
    fun evaluateDueStreakReminders(
        studentId: String,
        streak: StudentStreakState?,
        existingLogs: List<StreakNotificationLog>,
        nowMillis: Long = System.currentTimeMillis()
    ): StreakReminderDispatchResult {
        val todayWib = formatWibDate(nowMillis)
        if (hasLoggedInTodayWib(streak, nowMillis)) {
            return StreakReminderDispatchResult(emptyList(), existingLogs)
        }
        val hourWib = currentWibHour(nowMillis)
        val dueSlots = StreakReminderSlot.entries.filter { hourWib >= it.hourWib }
        val alreadySentSlots = existingLogs
            .filter { it.studentId == studentId && it.reminderDateWib == todayWib }
            .map { it.slot }
            .toSet()

        val newlyCreated = dueSlots
            .filterNot { it in alreadySentSlots }
            .map { slot ->
                StreakNotificationLog(
                    studentId = studentId,
                    reminderDateWib = todayWib,
                    slot = slot,
                    message = slot.message,
                    sentAtLabel = formatWibDateTimeLabel(nowMillis),
                    timestampMillis = nowMillis
                )
            }
        return StreakReminderDispatchResult(
            newlyDispatched = newlyCreated,
            updatedLogs = (newlyCreated + existingLogs).sortedByDescending { it.timestampMillis }
        )
    }

    /**
     * Strict RBAC XP History query:
     * - Students can ONLY read their own XP transactions (returns emptyList if requesting another student's ID).
     * - Admins can inspect any student's XP history.
     */
    fun getAuthorizedXpHistory(
        requester: UserProfile?,
        targetStudentId: String,
        allTransactions: List<RankingPointTransaction>,
        categoryFilter: PointSourceType? = null,
        timeRange: XpHistoryTimeRange = XpHistoryTimeRange.ALL,
        activeSemesterKey: String = "2026/2027-Ganjil",
        nowMillis: Long = System.currentTimeMillis()
    ): List<RankingPointTransaction> {
        if (requester == null) return emptyList()
        if (requester.role != UserRole.ADMIN && requester.id != targetStudentId) {
            return emptyList()
        }
        val currentMonth = formatWibMonth(nowMillis)
        val sevenDaysAgo = nowMillis - 7L * 24L * 60L * 60L * 1000L

        return allTransactions
            .filter { tx ->
                tx.studentId == targetStudentId &&
                    (categoryFilter == null || tx.sourceType == categoryFilter) &&
                    when (timeRange) {
                        XpHistoryTimeRange.ALL -> true
                        XpHistoryTimeRange.LAST_7_DAYS -> tx.timestampMillis >= sevenDaysAgo
                        XpHistoryTimeRange.THIS_MONTH -> formatWibMonth(tx.timestampMillis) == currentMonth
                        XpHistoryTimeRange.THIS_SEMESTER -> tx.semesterKey == activeSemesterKey
                    }
            }
            .sortedByDescending { it.timestampMillis }
    }

    /**
     * Single source of truth for Total XP: ALWAYS derived from summing all stored transactions.
     */
    fun computeTotalXp(studentId: String, transactions: List<RankingPointTransaction>): Int =
        transactions.filter { it.studentId == studentId }.sumOf { it.points }.coerceAtLeast(0)

    /**
     * Computes Total XP earned in the current Asia/Jakarta calendar month.
     */
    fun computeMonthlyXp(
        studentId: String,
        transactions: List<RankingPointTransaction>,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        val thisMonth = formatWibMonth(nowMillis)
        return transactions
            .filter { it.studentId == studentId && formatWibMonth(it.timestampMillis) == thisMonth }
            .sumOf { it.points }
            .coerceAtLeast(0)
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
