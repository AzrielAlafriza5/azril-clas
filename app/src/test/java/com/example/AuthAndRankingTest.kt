package com.example

import com.example.data.auth.AuthKeyValueStore
import com.example.data.auth.AuthResult
import com.example.data.auth.AzrilAuthRepository
import com.example.data.auth.OfficialStudyPrograms
import com.example.ui.model.AccountStatus
import com.example.ui.model.Assignment
import com.example.ui.model.AssignmentTargetType
import com.example.ui.model.PointSourceType
import com.example.ui.model.RankingPointTransaction
import com.example.ui.model.StudentBadgeStatus
import com.example.ui.model.StudentRecord
import com.example.ui.model.UserRole
import com.example.ui.state.AzrilUiState
import com.example.ui.state.AzrilViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class InMemoryAuthStore : AuthKeyValueStore {
    val strings = mutableMapOf<String, String>()
    val booleans = mutableMapOf<String, Boolean>()
    val longs = mutableMapOf<String, Long>()

    override suspend fun getString(key: String): String? = strings[key]
    override suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean = booleans[key] ?: defaultValue
    override suspend fun getLong(key: String, defaultValue: Long): Long = longs[key] ?: defaultValue
    override suspend fun putString(key: String, value: String?) {
        if (value == null) strings.remove(key) else strings[key] = value
    }
    override suspend fun putBoolean(key: String, value: Boolean) {
        booleans[key] = value
    }
    override suspend fun putLong(key: String, value: Long) {
        longs[key] = value
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthAndRankingTest {

    private fun createRepo(store: InMemoryAuthStore): AzrilAuthRepository =
        AzrilAuthRepository(
            store = store,
            supabaseUrl = "https://YOUR_PROJECT_REF.supabase.co",
            supabaseAnonKey = "YOUR_SUPABASE_ANON_KEY"
        )

    @Test
    fun officialStudyProgramsContainExactlyHesPjkrFarmasi() {
        val codes = OfficialStudyPrograms.list.map { it.code }
        assertEquals(listOf("HES", "PJKR", "FARMASI"), codes)
    }

    @Test
    fun adminProvisioningNimLoginRememberMePasswordResetAndNoPlaintextPassword() = runTest {
        val store = InMemoryAuthStore()
        val repo = createRepo(store)
        val initialPassword = "InitialAdminPassword123!"
        val resetPassword = "ResetAdminPassword456!"

        // 1. Admin creates student account with HES prodi and Lulus status
        val created = repo.adminCreateStudentAccount(
            studentId = "stu-azril",
            fullName = "Azril Al Afriza",
            nim = "202601001",
            email = "azril@kampus.ac.id",
            studyProgramId = "prodi-hes",
            studyProgramCode = "HES",
            classId = "cls-hes-a",
            className = "HES-3A",
            semester = 3,
            cohortYear = 2025,
            accountStatus = AccountStatus.ACTIVE,
            badgeStatus = StudentBadgeStatus.LULUS,
            initialPassword = initialPassword
        )
        assertTrue(created is AuthResult.Success)
        val createdProfile = (created as AuthResult.Success).profile
        assertEquals("Azril Al Afriza", createdProfile.fullName)
        assertEquals("202601001", createdProfile.nim)
        assertEquals("HES", createdProfile.studyProgramName)
        assertEquals(StudentBadgeStatus.LULUS, createdProfile.badgeStatus)
        assertEquals(UserRole.STUDENT, createdProfile.role)

        // Ensure plaintext password is NEVER stored anywhere in key-value store
        assertTrue(store.strings.values.none { it.contains(initialPassword) })

        // Reject duplicate NIM
        val duplicateNim = repo.adminCreateStudentAccount(
            fullName = "Budi Duplikat",
            nim = "202601001",
            email = "budi@kampus.ac.id",
            studyProgramId = "prodi-pjkr",
            studyProgramCode = "PJKR",
            classId = null,
            className = null,
            semester = 1,
            cohortYear = 2026,
            accountStatus = AccountStatus.ACTIVE,
            badgeStatus = StudentBadgeStatus.NEUTRAL,
            initialPassword = initialPassword
        )
        assertTrue(duplicateNim is AuthResult.Failure)

        // 2. Student logs in using NIM + password with Remember Me = true
        val loginRes = repo.login(
            identifier = "202601001",
            password = initialPassword,
            rememberMe = true
        )
        assertTrue(loginRes is AuthResult.Success)
        val loggedIn = (loginRes as AuthResult.Success).profile
        assertEquals("Azril Al Afriza", loggedIn.fullName)
        assertEquals("202601001", loggedIn.nim)
        assertEquals("HES", loggedIn.studyProgramName)
        assertEquals("cls-hes-a", loggedIn.classId)
        assertEquals(StudentBadgeStatus.LULUS, loggedIn.badgeStatus)

        // 3. Reopen app with Remember Me -> session persists automatically
        val restored = repo.restoreSession()
        assertNotNull(restored.profile)
        assertEquals("202601001", restored.profile?.nim)
        assertEquals(StudentBadgeStatus.LULUS, restored.profile?.badgeStatus)

        // 4. Logout clears session token but retains remembered NIM
        repo.logout()
        val afterLogout = repo.restoreSession()
        assertNull(afterLogout.profile)
        assertEquals("202601001", afterLogout.rememberedIdentifier)

        // 5. Admin resets student password -> old password fails, new password succeeds, still no plaintext
        val resetErr = repo.adminResetStudentPassword("stu-azril", resetPassword)
        assertNull(resetErr)
        assertTrue(store.strings.values.none { it.contains(resetPassword) })

        val oldPwAttempt = repo.login("202601001", initialPassword, rememberMe = false)
        assertTrue(oldPwAttempt is AuthResult.Failure)

        val newPwAttempt = repo.login("202601001", resetPassword, rememberMe = false)
        assertTrue(newPwAttempt is AuthResult.Success)

        // Remember Me = false -> reopening app does NOT auto-restore session
        val afterNoRemember = repo.restoreSession()
        assertNull(afterNoRemember.profile)
        assertEquals("", afterNoRemember.rememberedIdentifier)

        // 6. Admin updates student badge status to AFK and it persists
        assertTrue(repo.adminUpdateStudentBadgeStatus("stu-azril", StudentBadgeStatus.AFK))
        val updatedList = repo.getRegisteredStudents()
        assertEquals(StudentBadgeStatus.AFK, updatedList.first { it.id == "stu-azril" }.badgeStatus)
    }

    @Test
    fun multiClassAndProdiAssignmentTargetingFilesLongDescriptionAndDeadline() = runTest {
        val store = InMemoryAuthStore()
        val repo = createRepo(store)

        val hesClassAStudent = StudentRecord(
            id = "s-hes-a",
            fullName = "Azril Al Afriza",
            nim = "202601001",
            email = "azril@kampus.ac.id",
            studyProgramId = "prodi-hes",
            studyProgramName = "HES",
            classId = "cls-hes-a",
            className = "HES-3A",
            semester = 3,
            cohortYear = 2025
        )
        val pjkrClassBStudent = StudentRecord(
            id = "s-pjkr-b",
            fullName = "Raka Pratama",
            nim = "202602001",
            email = "raka@kampus.ac.id",
            studyProgramId = "prodi-pjkr",
            studyProgramName = "PJKR",
            classId = "cls-pjkr-b",
            className = "PJKR-2B",
            semester = 2,
            cohortYear = 2025
        )
        val farmasiClassCStudent = StudentRecord(
            id = "s-farmasi-c",
            fullName = "Siti Aisyah",
            nim = "202603001",
            email = "siti@kampus.ac.id",
            studyProgramId = "prodi-farmasi",
            studyProgramName = "FARMASI",
            classId = "cls-farmasi-c",
            className = "FARMASI-1C",
            semester = 1,
            cohortYear = 2026
        )

        val longDescription = buildString {
            repeat(80) { idx ->
                append("Panduan lengkap penugasan bagian ${idx + 1}: pastikan struktur analisis, referensi jurnal, dan lampiran data disusun sistematis tanpa pemotongan teks. ")
            }
        }
        assertTrue(longDescription.length > 2500)

        val pastMillis = AzrilViewModel.parseDeadlineMillis("2024-01-01", "23:59")!!
        val futureMillis = AzrilViewModel.parseDeadlineMillis("2030-12-31", "23:59")!!

        // Multi-class assignment targeting cls-hes-a and cls-pjkr-b ONLY
        val multiClassAssignment = Assignment(
            id = "asg-multi-class",
            title = "Studi Kasus Lintas Kelas",
            description = longDescription,
            instructions = longDescription,
            targetType = AssignmentTargetType.CLASS,
            targetClassIds = listOf("cls-hes-a", "cls-pjkr-b"),
            targetClassNames = listOf("HES-3A", "PJKR-2B"),
            createdAt = "2026-11-18",
            deadline = "01 Jan 2024, 23:59 WIB",
            deadlineEpochMillis = pastMillis,
            file1Name = "Modul_Kasus_1.pdf",
            file1Url = "https://example.edu/files/modul1.pdf",
            file2Name = "Rubrik_Penilaian_2.docx",
            file2Url = "https://example.edu/files/rubrik2.docx",
            isActive = true
        )

        // Prodi assignment targeting HES ONLY
        val hesProdiAssignment = Assignment(
            id = "asg-prodi-hes",
            title = "Analisis Hukum Ekonomi Syariah",
            description = "Tugas khusus seluruh mahasiswa Program Studi HES.",
            instructions = "Tugas khusus seluruh mahasiswa Program Studi HES.",
            targetType = AssignmentTargetType.STUDY_PROGRAM,
            targetStudyProgramIds = listOf("prodi-hes"),
            targetStudyProgramCodes = listOf("HES"),
            createdAt = "2026-11-18",
            deadline = "31 Des 2030, 23:59 WIB",
            deadlineEpochMillis = futureMillis,
            file1Name = "Panduan_HES.pdf",
            file1Url = "https://example.edu/files/hes.pdf",
            file2Name = "Template_Makalah.docx",
            file2Url = "https://example.edu/files/template.docx",
            isActive = true
        )

        // Verify targeting isolation
        assertTrue(AzrilViewModel.isAssignmentForStudent(multiClassAssignment, null, hesClassAStudent))
        assertTrue(AzrilViewModel.isAssignmentForStudent(multiClassAssignment, null, pjkrClassBStudent))
        assertFalse(AzrilViewModel.isAssignmentForStudent(multiClassAssignment, null, farmasiClassCStudent))

        assertTrue(AzrilViewModel.isAssignmentForStudent(hesProdiAssignment, null, hesClassAStudent))
        assertFalse(AzrilViewModel.isAssignmentForStudent(hesProdiAssignment, null, pjkrClassBStudent))
        assertFalse(AzrilViewModel.isAssignmentForStudent(hesProdiAssignment, null, farmasiClassCStudent))

        // Verify deadline evaluation
        assertTrue(multiClassAssignment.isDeadlinePassed())
        assertFalse(hesProdiAssignment.isDeadlinePassed())

        // Verify persistence keeps unlimited description and both files intact
        repo.saveAssignments(listOf(multiClassAssignment, hesProdiAssignment))
        val restored = repo.restoreSession()
        assertEquals(2, restored.savedAssignments.size)
        val restoredMulti = restored.savedAssignments.first { it.id == "asg-multi-class" }
        assertEquals(longDescription, restoredMulti.description)
        assertEquals("Modul_Kasus_1.pdf", restoredMulti.file1Name)
        assertEquals("https://example.edu/files/modul1.pdf", restoredMulti.file1Url)
        assertEquals("Rubrik_Penilaian_2.docx", restoredMulti.file2Name)
        assertEquals("https://example.edu/files/rubrik2.docx", restoredMulti.file2Url)
        assertEquals(listOf("cls-hes-a", "cls-pjkr-b"), restoredMulti.targetClassIds)
    }

    @Test
    fun rankingExcludesAfkShowsSeparateAfkListAndFormatsNameWithProdi() {
        val stuLulusHes = StudentRecord(
            id = "s1",
            fullName = "Azril Al Afriza",
            nim = "202601001",
            email = "azril@kampus.ac.id",
            studyProgramId = "prodi-hes",
            studyProgramName = "HES",
            classId = "cls-hes-a",
            className = "HES-3A",
            semester = 3,
            cohortYear = 2025,
            badgeStatus = StudentBadgeStatus.LULUS
        )
        val stuNeutralPjkr = StudentRecord(
            id = "s2",
            fullName = "Budi Santoso",
            nim = "202602001",
            email = "budi@kampus.ac.id",
            studyProgramId = "prodi-pjkr",
            studyProgramName = "PJKR",
            classId = "cls-pjkr-a",
            className = "PJKR-3A",
            semester = 3,
            cohortYear = 2025,
            badgeStatus = StudentBadgeStatus.NEUTRAL
        )
        val stuAfkFarmasi = StudentRecord(
            id = "s3",
            fullName = "Citra Lestari",
            nim = "202603001",
            email = "citra@kampus.ac.id",
            studyProgramId = "prodi-farmasi",
            studyProgramName = "FARMASI",
            classId = "cls-farmasi-a",
            className = "FARMASI-3A",
            semester = 3,
            cohortYear = 2025,
            badgeStatus = StudentBadgeStatus.AFK
        )

        val state = AzrilUiState(
            studyPrograms = OfficialStudyPrograms.list,
            students = listOf(stuLulusHes, stuNeutralPjkr, stuAfkFarmasi),
            rankingPoints = listOf(
                RankingPointTransaction(
                    studentId = "s1",
                    sourceType = PointSourceType.ATTENDANCE,
                    sourceId = "ses-1",
                    points = 20,
                    description = "Hadir",
                    createdAt = "2026-11-18",
                    updatedAt = "2026-11-18"
                ),
                RankingPointTransaction(
                    studentId = "s2",
                    sourceType = PointSourceType.ATTENDANCE,
                    sourceId = "ses-1",
                    points = 10,
                    description = "Hadir",
                    createdAt = "2026-11-18",
                    updatedAt = "2026-11-18"
                ),
                // Even if AFK student had higher points previously, AFK must NEVER appear in active ranking
                RankingPointTransaction(
                    studentId = "s3",
                    sourceType = PointSourceType.ATTENDANCE,
                    sourceId = "ses-1",
                    points = 90,
                    description = "Hadir lama",
                    createdAt = "2026-11-18",
                    updatedAt = "2026-11-18"
                )
            )
        )

        // Fakultas / Keseluruhan ranking excludes AFK
        val allRankings = AzrilViewModel.computeRankings(state)
        assertEquals(2, allRankings.size)
        assertTrue(allRankings.none { it.badgeStatus == StudentBadgeStatus.AFK })
        assertEquals("Azril Al Afriza — HES", allRankings[0].displayNameWithProdi)
        assertEquals(1, allRankings[0].rank)
        assertEquals(StudentBadgeStatus.LULUS, allRankings[0].badgeStatus)
        assertEquals("Budi Santoso — PJKR", allRankings[1].displayNameWithProdi)
        assertEquals(2, allRankings[1].rank)

        // Prodi filter
        val pjkrRankings = AzrilViewModel.computeRankings(state, studyProgramId = "prodi-pjkr")
        assertEquals(1, pjkrRankings.size)
        assertEquals("Budi Santoso — PJKR", pjkrRankings[0].displayNameWithProdi)

        // Class filter
        val hesClassRankings = AzrilViewModel.computeRankings(state, classId = "cls-hes-a")
        assertEquals(1, hesClassRankings.size)
        assertEquals("Azril Al Afriza — HES", hesClassRankings[0].displayNameWithProdi)

        // Separate AFK list contains ONLY AFK students with "Nama — PRODI" format
        val afkList = AzrilViewModel.computeAfkStudents(state)
        assertEquals(1, afkList.size)
        assertEquals("Citra Lestari — FARMASI", afkList[0].displayNameWithProdi)
        assertEquals(StudentBadgeStatus.AFK, afkList[0].badgeStatus)
    }
}
