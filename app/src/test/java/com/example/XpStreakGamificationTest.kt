package com.example

import com.example.data.auth.AzrilAuthRepository
import com.example.data.gamification.XpAndStreakEngine
import com.example.ui.model.Assignment
import com.example.ui.model.AttendanceRecord
import com.example.ui.model.AttendanceStatus
import com.example.ui.model.ParticipationXpCategory
import com.example.ui.model.PointSourceType
import com.example.ui.model.RankingPointTransaction
import com.example.ui.model.StreakReminderSlot
import com.example.ui.model.StudentStreakState
import com.example.ui.model.SubmissionTimeliness
import com.example.ui.model.UserProfile
import com.example.ui.model.UserRole
import com.example.ui.model.XpHistoryTimeRange
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class XpStreakGamificationTest {

    private fun wibMillis(dateTimeWib: String): Long {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            timeZone = XpAndStreakEngine.WIB_TIME_ZONE
        }
        return sdf.parse(dateTimeWib)!!.time
    }

    @Test
    fun academicXpHadir20Izin1And2Get10Izin3Gets0Alfa0AndResetsNextSemester() {
        val studentId = "stu-1"
        val sem1 = "2026/2027-Ganjil"
        val sem2 = "2026/2027-Genap"

        val records = listOf(
            AttendanceRecord(
                id = "r1", sessionId = "ses-1", meetingTitle = "Pertemuan 1", className = "HES-3A",
                studentId = studentId, studentName = "Azril", studentNim = "202601001",
                dateLabel = "01 Nov 2026", recordedAt = "01 Nov 2026, 08:00 WIB",
                status = AttendanceStatus.HADIR, semesterKey = sem1, timestampMillis = wibMillis("2026-11-01 08:00")
            ),
            AttendanceRecord(
                id = "r2", sessionId = "ses-2", meetingTitle = "Pertemuan 2", className = "HES-3A",
                studentId = studentId, studentName = "Azril", studentNim = "202601001",
                dateLabel = "02 Nov 2026", recordedAt = "02 Nov 2026, 08:00 WIB",
                status = AttendanceStatus.IZIN, semesterKey = sem1, timestampMillis = wibMillis("2026-11-02 08:00")
            ),
            AttendanceRecord(
                id = "r3", sessionId = "ses-3", meetingTitle = "Pertemuan 3", className = "HES-3A",
                studentId = studentId, studentName = "Azril", studentNim = "202601001",
                dateLabel = "03 Nov 2026", recordedAt = "03 Nov 2026, 08:00 WIB",
                status = AttendanceStatus.IZIN, semesterKey = sem1, timestampMillis = wibMillis("2026-11-03 08:00")
            ),
            AttendanceRecord(
                id = "r4", sessionId = "ses-4", meetingTitle = "Pertemuan 4", className = "HES-3A",
                studentId = studentId, studentName = "Azril", studentNim = "202601001",
                dateLabel = "04 Nov 2026", recordedAt = "04 Nov 2026, 08:00 WIB",
                status = AttendanceStatus.IZIN, semesterKey = sem1, timestampMillis = wibMillis("2026-11-04 08:00")
            ),
            AttendanceRecord(
                id = "r5", sessionId = "ses-5", meetingTitle = "Pertemuan 5", className = "HES-3A",
                studentId = studentId, studentName = "Azril", studentNim = "202601001",
                dateLabel = "05 Nov 2026", recordedAt = "05 Nov 2026, 08:00 WIB",
                status = AttendanceStatus.ALFA, semesterKey = sem1, timestampMillis = wibMillis("2026-11-05 08:00")
            )
        )

        var txs = XpAndStreakEngine.recalculateAttendanceXpForStudentSemester(
            studentId = studentId,
            semesterKey = sem1,
            attendanceRecords = records,
            existingTransactions = emptyList()
        )

        // Test 1: Hadir -> 20 XP
        assertEquals(20, txs.first { it.sourceId == "ses-1" }.points)
        // Test 2: Izin ke-1 -> 10 XP
        assertEquals(10, txs.first { it.sourceId == "ses-2" }.points)
        // Test 3: Izin ke-2 -> 10 XP
        assertEquals(10, txs.first { it.sourceId == "ses-3" }.points)
        // Test 4: Izin ke-3 -> 0 XP (still recorded!)
        assertEquals(0, txs.first { it.sourceId == "ses-4" }.points)
        // Alfa -> 0 XP
        assertEquals(0, txs.first { it.sourceId == "ses-5" }.points)
        assertEquals(40, XpAndStreakEngine.computeTotalXp(studentId, txs))

        // Verify individual semester quota summary
        val quotaSem1 = XpAndStreakEngine.computeStudentIzinQuota(studentId, sem1, records)
        assertEquals(3, quotaSem1.usedIzinCount)
        assertEquals(2, quotaSem1.paidIzinUsed)
        assertEquals(0, quotaSem1.remainingPaidIzin)

        // Verify consistency when Admin corrects Izin #1 -> Hadir:
        // Now ses-2 becomes Hadir (+20), ses-3 becomes Izin #1 (+10), ses-4 becomes Izin #2 (+10)!
        val correctedRecords = records.map { if (it.id == "r2") it.copy(status = AttendanceStatus.HADIR) else it }
        txs = XpAndStreakEngine.recalculateAttendanceXpForStudentSemester(
            studentId = studentId,
            semesterKey = sem1,
            attendanceRecords = correctedRecords,
            existingTransactions = txs
        )
        assertEquals(20, txs.first { it.sourceId == "ses-2" }.points)
        assertEquals(10, txs.first { it.sourceId == "ses-3" }.points)
        assertEquals(10, txs.first { it.sourceId == "ses-4" }.points)
        assertEquals(60, XpAndStreakEngine.computeTotalXp(studentId, txs))

        // Test 13: Re-processing attendance NEVER creates duplicate XP transactions
        val reprocessed = XpAndStreakEngine.recalculateAttendanceXpForStudentSemester(
            studentId = studentId,
            semesterKey = sem1,
            attendanceRecords = correctedRecords,
            existingTransactions = txs
        )
        assertEquals(txs.size, reprocessed.size)
        assertEquals(60, XpAndStreakEngine.computeTotalXp(studentId, reprocessed))

        // Entering a new semester resets Izin quota back to 0 used / 2 remaining
        val sem2Record = AttendanceRecord(
            id = "r6", sessionId = "ses-6", meetingTitle = "Pertemuan 1 Genap", className = "HES-4A",
            studentId = studentId, studentName = "Azril", studentNim = "202601001",
            dateLabel = "10 Feb 2027", recordedAt = "10 Feb 2027, 08:00 WIB",
            status = AttendanceStatus.IZIN, semesterKey = sem2, timestampMillis = wibMillis("2027-02-10 08:00")
        )
        val allRecords = correctedRecords + sem2Record
        val quotaSem2 = XpAndStreakEngine.computeStudentIzinQuota(studentId, sem2, allRecords)
        assertEquals(1, quotaSem2.usedIzinCount)
        assertEquals(1, quotaSem2.remainingPaidIzin)

        val withSem2 = XpAndStreakEngine.recalculateAttendanceXpForStudentSemester(
            studentId = studentId,
            semesterKey = sem2,
            attendanceRecords = allRecords,
            existingTransactions = reprocessed
        )
        assertEquals(10, withSem2.first { it.sourceId == "ses-6" }.points)
        assertEquals(70, XpAndStreakEngine.computeTotalXp(studentId, withSem2))
    }

    @Test
    fun assignmentSubmissionXpEarly35OnTime30Late10AndNoDuplicates() {
        val studentId = "stu-1"
        val deadline = wibMillis("2026-11-20 23:59")
        val asg1 = Assignment(
            id = "asg-1", title = "Makalah HES", description = "Desc", instructions = "Inst",
            createdAt = "01 Nov 2026", deadline = "20 Nov 2026, 23:59 WIB", deadlineEpochMillis = deadline
        )
        val asg2 = asg1.copy(id = "asg-2", title = "Studi Kasus")
        val asg3 = asg1.copy(id = "asg-3", title = "Review Jurnal")

        val earlyTime = wibMillis("2026-11-18 10:00")     // > 24h before deadline -> 35 XP
        val onTime = wibMillis("2026-11-20 15:00")        // within 24h before deadline -> 30 XP
        val lateTime = wibMillis("2026-11-21 09:00")      // after deadline -> 10 XP

        assertEquals(SubmissionTimeliness.BEFORE_DEADLINE, XpAndStreakEngine.determineSubmissionTimeliness(asg1, earlyTime))
        assertEquals(SubmissionTimeliness.ON_TIME, XpAndStreakEngine.determineSubmissionTimeliness(asg2, onTime))
        assertEquals(SubmissionTimeliness.LATE, XpAndStreakEngine.determineSubmissionTimeliness(asg3, lateTime))

        var txs = emptyList<RankingPointTransaction>()
        txs = XpAndStreakEngine.upsertAssignmentSubmissionXp(txs, studentId, asg1, SubmissionTimeliness.BEFORE_DEADLINE, "2026/2027-Ganjil", earlyTime)
        txs = XpAndStreakEngine.upsertAssignmentSubmissionXp(txs, studentId, asg2, SubmissionTimeliness.ON_TIME, "2026/2027-Ganjil", onTime)
        txs = XpAndStreakEngine.upsertAssignmentSubmissionXp(txs, studentId, asg3, SubmissionTimeliness.LATE, "2026/2027-Ganjil", lateTime)

        assertEquals(35, txs.first { it.sourceId == "asg-1" }.points)
        assertEquals(30, txs.first { it.sourceId == "asg-2" }.points)
        assertEquals(10, txs.first { it.sourceId == "asg-3" }.points)
        assertEquals(75, XpAndStreakEngine.computeTotalXp(studentId, txs))

        // Re-submitting same assignment NEVER duplicates XP
        txs = XpAndStreakEngine.upsertAssignmentSubmissionXp(txs, studentId, asg1, SubmissionTimeliness.BEFORE_DEADLINE, "2026/2027-Ganjil", earlyTime + 60_000)
        assertEquals(3, txs.size)
        assertEquals(75, XpAndStreakEngine.computeTotalXp(studentId, txs))
    }

    @Test
    fun participationXpRegular10Critical20RecordsAdminIdentityAndTimestamp() {
        // Admin strictly has ONLY 2 participation categories
        assertEquals(2, ParticipationXpCategory.entries.size)

        // Test 5: Bertanya / Berpendapat Biasa -> 10 XP
        val regularTx = XpAndStreakEngine.createParticipationXpTransaction(
            studentId = "stu-1",
            category = ParticipationXpCategory.REGULAR,
            reasonNote = "Bertanya konsep akad murabahah",
            adminId = "admin-1",
            adminName = "Dosen Pengampu",
            semesterKey = "2026/2027-Ganjil",
            nowMillis = wibMillis("2026-11-18 10:15")
        )
        assertEquals(10, regularTx.points)
        assertEquals("Bertanya / Berpendapat Biasa", regularTx.activityTitle)
        assertEquals("admin-1", regularTx.adminId)
        assertEquals("Dosen Pengampu", regularTx.adminName)

        // Test 6: Bertanya / Berpendapat Kritis -> 20 XP
        val criticalTx = XpAndStreakEngine.createParticipationXpTransaction(
            studentId = "stu-1",
            category = ParticipationXpCategory.CRITICAL,
            reasonNote = "Analisis kritis perbandingan fatwa DSN-MUI",
            adminId = "admin-1",
            adminName = "Dosen Pengampu",
            semesterKey = "2026/2027-Ganjil",
            nowMillis = wibMillis("2026-11-18 10:45")
        )
        assertEquals(20, criticalTx.points)
        assertEquals("Bertanya / Berpendapat Kritis", criticalTx.activityTitle)
        assertEquals(30, XpAndStreakEngine.computeTotalXp("stu-1", listOf(regularTx, criticalTx)))
    }

    @Test
    fun loginStreakMilestonesRepeatSameDayNoIncrementAndResetOnMissedDay() {
        val studentId = "stu-1"
        val semesterKey = "2026/2027-Ganjil"
        var streak: StudentStreakState? = null
        var txs = emptyList<RankingPointTransaction>()

        // Simulate 30 consecutive WIB days starting 2026-10-01 09:00 WIB
        val day1Millis = wibMillis("2026-10-01 09:00")
        val dayMs = 24L * 60L * 60L * 1000L

        for (dayIndex in 0 until 30) {
            val todayMillis = day1Millis + dayIndex * dayMs
            val res = XpAndStreakEngine.recordStudentDailyLogin(studentId, streak, txs, semesterKey, todayMillis)
            streak = res.updatedStreak
            txs = res.updatedTransactions
            val dayNum = dayIndex + 1

            assertEquals(dayNum, streak.currentStreak)
            assertTrue(res.isFirstLoginToday)

            // Test 8: Repeated login on the SAME WIB day does NOT increment streak or duplicate bonus
            val repeatSameDay = XpAndStreakEngine.recordStudentDailyLogin(
                studentId, streak, txs, semesterKey, todayMillis + 3L * 60L * 60L * 1000L
            )
            assertFalse(repeatSameDay.isFirstLoginToday)
            assertEquals(dayNum, repeatSameDay.updatedStreak.currentStreak)
            assertNull(repeatSameDay.awardedMilestoneDays)
            assertEquals(0, repeatSameDay.awardedBonusXp)
            assertEquals(txs.size, repeatSameDay.updatedTransactions.size)

            // Test 7: Milestone bonuses awarded strictly at 3 (+10), 7 (+30), 14 (+50), 30 (+100)
            when (dayNum) {
                3 -> { assertEquals(3, res.awardedMilestoneDays); assertEquals(10, res.awardedBonusXp) }
                7 -> { assertEquals(7, res.awardedMilestoneDays); assertEquals(30, res.awardedBonusXp) }
                14 -> { assertEquals(14, res.awardedMilestoneDays); assertEquals(50, res.awardedBonusXp) }
                30 -> { assertEquals(30, res.awardedMilestoneDays); assertEquals(100, res.awardedBonusXp) }
                else -> { assertNull(res.awardedMilestoneDays); assertEquals(0, res.awardedBonusXp) }
            }
        }

        // Total milestone bonus XP across 30 consecutive days = 10 + 30 + 50 + 100 = 190 XP
        assertEquals(4, txs.count { it.sourceType == PointSourceType.STREAK_BONUS })
        assertEquals(190, XpAndStreakEngine.computeTotalXp(studentId, txs))

        // Test 9: Skipping 1 full calendar day (WIB) resets effective streak to 0, and next login restarts at 1
        val day30Millis = day1Millis + 29L * dayMs
        val day32Millis = day30Millis + 2L * dayMs // Skipped day 31 completely!
        assertEquals(0, XpAndStreakEngine.effectiveCurrentStreak(streak, day32Millis))

        val afterBreak = XpAndStreakEngine.recordStudentDailyLogin(studentId, streak, txs, semesterKey, day32Millis)
        assertEquals(1, afterBreak.updatedStreak.currentStreak)
        assertEquals(30, afterBreak.updatedStreak.longestStreak)
    }

    @Test
    fun streakRemindersScheduleStopAfterLoginAndPreventDuplicates() {
        val studentId = "stu-1"
        val morning08 = wibMillis("2026-11-18 08:05")
        val noon12 = wibMillis("2026-11-18 12:10")
        val evening17 = wibMillis("2026-11-18 17:15")

        // Before login at 08:05 WIB -> ONLY MORNING_08 sent
        val r1 = XpAndStreakEngine.evaluateDueStreakReminders(studentId, null, emptyList(), morning08)
        assertEquals(listOf(StreakReminderSlot.MORNING_08), r1.newlyDispatched.map { it.slot })

        // Re-running at 08:30 WIB -> ZERO duplicate notification
        val r1Dup = XpAndStreakEngine.evaluateDueStreakReminders(studentId, null, r1.updatedLogs, wibMillis("2026-11-18 08:30"))
        assertTrue(r1Dup.newlyDispatched.isEmpty())

        // At 12:10 WIB still not logged in -> ONLY NOON_12 sent
        val r2 = XpAndStreakEngine.evaluateDueStreakReminders(studentId, null, r1.updatedLogs, noon12)
        assertEquals(listOf(StreakReminderSlot.NOON_12), r2.newlyDispatched.map { it.slot })

        // Student logs in at 13:00 WIB -> reminders STOP for the rest of the day (17:15 WIB sends NOTHING!)
        val loginRes = XpAndStreakEngine.recordStudentDailyLogin(
            studentId = studentId,
            existingStreak = null,
            existingTransactions = emptyList(),
            semesterKey = "2026/2027-Ganjil",
            nowMillis = wibMillis("2026-11-18 13:00")
        )
        val r3AfterLogin = XpAndStreakEngine.evaluateDueStreakReminders(studentId, loginRes.updatedStreak, r2.updatedLogs, evening17)
        assertTrue(r3AfterLogin.newlyDispatched.isEmpty())
    }

    @Test
    fun xpHistoryMatchesTotalXpEnforcesStudentIsolationAndPersistsAcrossLogoutLogin() = runTest {
        val store = InMemoryAuthStore()
        val repo = AzrilAuthRepository(
            store = store,
            supabaseUrl = "https://YOUR_PROJECT_REF.supabase.co",
            supabaseAnonKey = "YOUR_SUPABASE_ANON_KEY"
        )

        val studentA = UserProfile(id = "stu-a", fullName = "Azril", email = "azril@kampus.ac.id", role = UserRole.STUDENT, nim = "202601001")
        val studentB = UserProfile(id = "stu-b", fullName = "Budi", email = "budi@kampus.ac.id", role = UserRole.STUDENT, nim = "202601002")
        val admin = UserProfile(id = "admin-1", fullName = "Admin", email = "admin@kampus.ac.id", role = UserRole.ADMIN)

        val txA1 = XpAndStreakEngine.createParticipationXpTransaction(
            studentId = studentA.id, category = ParticipationXpCategory.REGULAR,
            reasonNote = "Diskusi", adminId = admin.id, adminName = admin.fullName,
            semesterKey = "2026/2027-Ganjil", nowMillis = wibMillis("2026-11-18 09:00")
        )
        val txA2 = XpAndStreakEngine.createParticipationXpTransaction(
            studentId = studentA.id, category = ParticipationXpCategory.CRITICAL,
            reasonNote = "Analisis kritis", adminId = admin.id, adminName = admin.fullName,
            semesterKey = "2026/2027-Ganjil", nowMillis = wibMillis("2026-11-18 10:00")
        )
        val txB1 = XpAndStreakEngine.createParticipationXpTransaction(
            studentId = studentB.id, category = ParticipationXpCategory.CRITICAL,
            reasonNote = "Presentasi", adminId = admin.id, adminName = admin.fullName,
            semesterKey = "2026/2027-Ganjil", nowMillis = wibMillis("2026-11-18 11:00")
        )
        val allTxs = listOf(txA1, txA2, txB1)

        // Test 10 & 11: Student A sees all own transactions and Total XP == sum of all own transactions
        val historyA = XpAndStreakEngine.getAuthorizedXpHistory(
            requester = studentA,
            targetStudentId = studentA.id,
            allTransactions = allTxs,
            timeRange = XpHistoryTimeRange.ALL,
            nowMillis = wibMillis("2026-11-18 12:00")
        )
        assertEquals(2, historyA.size)
        assertEquals(historyA.sumOf { it.points }, XpAndStreakEngine.computeTotalXp(studentA.id, allTxs))
        assertEquals(30, XpAndStreakEngine.computeTotalXp(studentA.id, allTxs))

        // Test 12: Student A CANNOT access Student B's XP history; Admin CAN access any student's XP history
        val unauthorizedAttempt = XpAndStreakEngine.getAuthorizedXpHistory(
            requester = studentA,
            targetStudentId = studentB.id,
            allTransactions = allTxs
        )
        assertTrue(unauthorizedAttempt.isEmpty())

        val adminAccessToB = XpAndStreakEngine.getAuthorizedXpHistory(
            requester = admin,
            targetStudentId = studentB.id,
            allTransactions = allTxs
        )
        assertEquals(1, adminAccessToB.size)
        assertEquals(20, adminAccessToB.first().points)

        // Persistence across logout and subsequent login
        repo.saveXpTransactions(allTxs)
        repo.logout()
        val restored = repo.restoreSession()
        assertEquals(3, restored.savedXpTransactions.size)
        assertEquals(30, XpAndStreakEngine.computeTotalXp(studentA.id, restored.savedXpTransactions))
    }
}
