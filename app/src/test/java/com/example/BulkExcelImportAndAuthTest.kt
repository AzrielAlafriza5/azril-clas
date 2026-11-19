package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.AuthResult
import com.example.data.auth.AzrilAuthRepository
import com.example.data.auth.OfficialStudyPrograms
import com.example.data.importer.ExcelStudentImportParser
import com.example.data.importer.ExcelStudentImportParser.XlsxRowBuildSpec
import com.example.data.importer.ExcelStudentImportParser.XlsxSheetBuildSpec
import com.example.ui.model.AccountStatus
import com.example.ui.model.ExcelImportValidationStatus
import com.example.ui.model.ExcelRowColorFlag
import com.example.ui.model.StudentBadgeStatus
import com.example.ui.model.StudentRecord
import com.example.ui.model.UserRole
import com.example.ui.state.AzrilViewModel
import kotlinx.coroutines.delay
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BulkExcelImportAndAuthTest {

    private val headerRow = XlsxRowBuildSpec(
        values = listOf(
            "No.", "NIM", "Nama",
            "Minggu ke-1", "Minggu ke-2", "Minggu ke-3", "Minggu ke-4",
            "Minggu ke-5", "Minggu ke-6", "Minggu ke-7", "Minggu ke-8"
        )
    )

    private fun buildComprehensiveTestWorkbook(): ByteArray {
        val pjkrSheet = XlsxSheetBuildSpec(
            name = "PJKR-3B",
            rows = listOf(
                headerRow,
                // Row 2: Normal student with varied historical attendance statuses
                XlsxRowBuildSpec(
                    values = listOf(
                        "1", "250701046", "ABDUL GHANI",
                        "HADIR", "IZIN", "ALPHA", "TIDAK HADIR", "DISPEN", "KIP", "HADIR", "HADIR"
                    )
                ),
                // Row 3: RED fill -> MUST be imported with badgeStatus = AFK
                XlsxRowBuildSpec(
                    values = listOf(
                        "2", "250701047", "AHMAD FAUZI AFK",
                        "ALPHA", "ALPHA", "TIDAK HADIR", "ALPHA", "ALPHA", "ALPHA", "ALPHA", "ALPHA"
                    ),
                    fillArgb = "FFFF0000"
                ),
                // Row 4: BLACK fill -> MUST be SKIPPED ("Dilewati - Blok Hitam"), NEVER imported
                XlsxRowBuildSpec(
                    values = listOf(
                        "3", "250701048", "MAHASISWA BLOK HITAM",
                        "HADIR", "HADIR", "HADIR", "HADIR", "HADIR", "HADIR", "HADIR", "HADIR"
                    ),
                    fillArgb = "FF000000"
                ),
                // Row 5: MIXED RED + BLACK cells in same row -> Priority BLACK > RED -> SKIPPED_BLACK!
                XlsxRowBuildSpec(
                    values = listOf(
                        "4", "250701049", "CAMPURAN MERAH HITAM",
                        "HADIR", "ALPHA", "HADIR", "HADIR", "HADIR", "HADIR", "HADIR", "HADIR"
                    ),
                    cellFillArgbs = mapOf(
                        1 to "FFFF0000", // Red on NIM cell
                        2 to "FF000000"  // Black on Nama cell -> BLACK MUST WIN
                    )
                ),
                // Row 6: OTHER color (Yellow + Green + Blue) -> IGNORED, imported as normal!
                XlsxRowBuildSpec(
                    values = listOf(
                        "5", "250701050", "WARNA LAIN NORMAL",
                        "HADIR", "HADIR", "IZIN", "HADIR", "HADIR", "HADIR", "HADIR", "HADIR"
                    ),
                    cellFillArgbs = mapOf(
                        0 to "FFFFFF00", // Yellow
                        1 to "FF00B050", // Green
                        2 to "FF0070C0"  // Blue
                    )
                ),
                // Row 7: Empty NIM on a RED row (mirrors Row 39 MUHAMMAD AS'AD in real sheet) -> INVALID_OR_DUPLICATE
                XlsxRowBuildSpec(
                    values = listOf("39", "", "MUHAMMAD AS'AD", "ALPHA", "ALPHA"),
                    fillArgb = "FFFF0000"
                ),
                // Row 8: Empty Name -> INVALID_OR_DUPLICATE
                XlsxRowBuildSpec(
                    values = listOf("40", "250701099", "", "HADIR")
                )
            )
        )

        val farmasiSheet = XlsxSheetBuildSpec(
            name = "FARMASI-3",
            rows = listOf(
                headerRow,
                // Row 2: Normal FARMASI student
                XlsxRowBuildSpec(
                    values = listOf(
                        "1", "250801001", "SITI NURHALIZA",
                        "HADIR", "HADIR", "DISPEN", "KIP", "IZIN", "IZIN", "IZIN", "HADIR"
                    )
                ),
                // Row 3: Dark Red AFK student in FARMASI-3
                XlsxRowBuildSpec(
                    values = listOf("2", "250801002", "BUDI AFK FARMASI", "ALPHA", "ALPHA"),
                    fillArgb = "FFC00000"
                ),
                // Row 4: Conflict NIM (same NIM used by TWO DIFFERENT NAMES across sheets!)
                XlsxRowBuildSpec(
                    values = listOf("3", "250999001", "NAMA PERTAMA KONFLIK", "HADIR")
                )
            )
        )

        val hesSheet = XlsxSheetBuildSpec(
            name = "HES-3B",
            rows = listOf(
                headerRow,
                // Row 2: Normal HES student
                XlsxRowBuildSpec(
                    values = listOf("1", "250901001", "AZRIL HAKIM", "HADIR", "HADIR", "HADIR", "IZIN")
                ),
                // Row 3: Same NIM as FARMASI-3 Row 4 but DIFFERENT name -> must detect 1 NIM > 1 Nama
                XlsxRowBuildSpec(
                    values = listOf("2", "250999001", "NAMA KEDUA KONFLIK", "HADIR")
                ),
                // Row 4 & 5: Exact duplicate NIM with same name
                XlsxRowBuildSpec(
                    values = listOf("3", "250901055", "DUPLIKAT SAMA", "HADIR")
                ),
                XlsxRowBuildSpec(
                    values = listOf("4", "250901055", "DUPLIKAT SAMA", "HADIR")
                ),
                // Row 6: Already existing student NIM (when passed in existingStudents)
                XlsxRowBuildSpec(
                    values = listOf("5", "250901090", "MAHASISWA SUDAH ADA", "HADIR")
                )
            )
        )

        return ExcelStudentImportParser.buildStyledXlsx(listOf(pjkrSheet, farmasiSheet, hesSheet))
    }

    @Test
    fun multiSheetMappingColorPriorityAndValidationRulesWorkAccurately() {
        val existingStudent = StudentRecord(
            id = "existing-1",
            fullName = "MAHASISWA SUDAH ADA (ASLI)",
            nim = "250901090",
            email = "250901090@mahasiswa.azrilclas.ac.id",
            studyProgramId = "prodi-hes",
            studyProgramName = "HES",
            classId = "cls-hes-3b",
            className = "HES-3B",
            semester = 3,
            cohortYear = 2025,
            status = AccountStatus.ACTIVE,
            badgeStatus = StudentBadgeStatus.NEUTRAL,
            mustChangePassword = false
        )

        val xlsxBytes = buildComprehensiveTestWorkbook()
        val parsed = ExcelStudentImportParser.parseXlsx(
            fileName = "Absensi_IT_PROGRAM_2026_Minggu_1_sd_8.xlsx",
            bytes = xlsxBytes,
            existingStudents = listOf(existingStudent),
            studyPrograms = OfficialStudyPrograms.list
        ).getOrThrow()

        // 1. Sheet mappings: PJKR-3B -> PJKR / 3B, FARMASI-3 -> FARMASI / 3, HES-3B -> HES / 3B
        assertEquals(3, parsed.sheetMappings.size)
        val pjkrMap = parsed.sheetMappings.first { it.sheetName == "PJKR-3B" }
        assertEquals("PJKR", pjkrMap.studyProgramCode)
        assertEquals("3B", pjkrMap.classLabel)
        assertTrue(pjkrMap.isRecognized)

        val farmasiMap = parsed.sheetMappings.first { it.sheetName == "FARMASI-3" }
        assertEquals("FARMASI", farmasiMap.studyProgramCode)
        assertEquals("3", farmasiMap.classLabel)
        assertTrue(farmasiMap.isRecognized)

        val hesMap = parsed.sheetMappings.first { it.sheetName == "HES-3B" }
        assertEquals("HES", hesMap.studyProgramCode)
        assertEquals("3B", hesMap.classLabel)
        assertTrue(hesMap.isRecognized)

        // 2. Category counts (4 preview categories)
        // Normal valid: ABDUL GHANI, WARNA LAIN NORMAL, SITI NURHALIZA, AZRIL HAKIM = 4
        assertEquals(4, parsed.validNormalCount)
        // AFK valid (Red): AHMAD FAUZI AFK, BUDI AFK FARMASI = 2
        assertEquals(2, parsed.validAfkCount)
        assertEquals(6, parsed.totalImportableCount)
        // Black skipped: MAHASISWA BLOK HITAM + CAMPURAN MERAH HITAM = 2
        assertEquals(2, parsed.skippedBlackCount)
        // Already exists: MAHASISWA SUDAH ADA = 1
        assertEquals(1, parsed.alreadyExistsCount)
        // Invalid or duplicate: MUHAMMAD AS'AD (empty NIM), empty Name, 2x conflict NIM, 2x duplicate NIM = 6
        assertEquals(6, parsed.invalidOrDuplicateCount)

        // 3. Verify BLACK priority over RED
        val mixedRow = parsed.rows.first { it.nim == "250701049" }
        assertEquals(ExcelRowColorFlag.BLACK_SKIP, mixedRow.colorFlag)
        assertEquals(ExcelImportValidationStatus.SKIPPED_BLACK, mixedRow.validationStatus)
        assertFalse(mixedRow.isImportable)
        assertTrue(mixedRow.weeklyAttendance.isEmpty())
        assertTrue(mixedRow.issues.any { it.contains("Blok Hitam", ignoreCase = true) })

        // 4. Verify OTHER colors (Yellow/Green/Blue) are ignored and treated as normal
        val otherColorRow = parsed.rows.first { it.nim == "250701050" }
        assertEquals(ExcelRowColorFlag.OTHER_IGNORED, otherColorRow.colorFlag)
        assertEquals(ExcelImportValidationStatus.VALID_NORMAL, otherColorRow.validationStatus)
        assertTrue(otherColorRow.isImportable)

        // 5. Verify RED fill marked as AFK
        val redRow = parsed.rows.first { it.nim == "250701047" }
        assertEquals(ExcelRowColorFlag.RED_AFK, redRow.colorFlag)
        assertEquals(ExcelImportValidationStatus.VALID_AFK, redRow.validationStatus)
        assertTrue(redRow.isImportable)

        // 6. Verify 1 NIM used by multiple different names is flagged with sheet + row locations
        val conflictRows = parsed.rows.filter { it.nim == "250999001" }
        assertEquals(2, conflictRows.size)
        assertTrue(conflictRows.all { it.validationStatus == ExcelImportValidationStatus.INVALID_OR_DUPLICATE })
        assertTrue(conflictRows.first().issues.any { it.contains("lebih dari satu nama", ignoreCase = true) })

        // 7. Verify empty NIM on red row (MUHAMMAD AS'AD) is blocked and reports sheet + row
        val emptyNimRow = parsed.rows.first { it.fullName == "MUHAMMAD AS'AD" }
        assertEquals(ExcelImportValidationStatus.INVALID_OR_DUPLICATE, emptyNimRow.validationStatus)
        assertTrue(emptyNimRow.issues.any { it.contains("NIM kosong", ignoreCase = true) })

        // 8. Verify exact original historical attendance statuses preserved for ABDUL GHANI
        val ghaniRow = parsed.rows.first { it.nim == "250701046" }
        assertEquals("HADIR", ghaniRow.weeklyAttendance[1])
        assertEquals("IZIN", ghaniRow.weeklyAttendance[2])
        assertEquals("ALPHA", ghaniRow.weeklyAttendance[3])
        assertEquals("TIDAK HADIR", ghaniRow.weeklyAttendance[4])
        assertEquals("DISPEN", ghaniRow.weeklyAttendance[5])
        assertEquals("KIP", ghaniRow.weeklyAttendance[6])
    }

    @Test
    fun bulkAccountCreationMandatoryFirstLoginPasswordChangeAndReImportIdempotency() = runTest {
        val store = InMemoryAuthStore()
        val repo = AzrilAuthRepository(
            store = store,
            supabaseUrl = "https://YOUR_PROJECT_REF.supabase.co",
            supabaseAnonKey = "YOUR_SUPABASE_ANON_KEY"
        )

        // Existing student already has custom password & mustChangePassword = false
        val preExisting = repo.adminCreateStudentAccount(
            studentId = "existing-1",
            fullName = "MAHASISWA SUDAH ADA",
            nim = "250901090",
            email = "250901090@mahasiswa.azrilclas.ac.id",
            studyProgramId = "prodi-hes",
            studyProgramCode = "HES",
            classId = "cls-hes-3b",
            className = "HES-3B",
            semester = 3,
            cohortYear = 2025,
            accountStatus = AccountStatus.ACTIVE,
            badgeStatus = StudentBadgeStatus.NEUTRAL,
            initialPassword = "CustomPassword99"
        )
        assertTrue(preExisting is AuthResult.Success)
        // Simulate preExisting already changed password
        val preChange = repo.completeFirstLoginPasswordChange("existing-1", "CustomPassword99!")
        assertTrue(preChange is AuthResult.Success)

        val existingStudents = repo.getRegisteredStudents()
        val parsed = ExcelStudentImportParser.parseXlsx(
            fileName = "Absensi_IT_PROGRAM_2026_Minggu_1_sd_8.xlsx",
            bytes = buildComprehensiveTestWorkbook(),
            existingStudents = existingStudents
        ).getOrThrow()

        val toCreate = parsed.rows.filter { it.isImportable }.map { row ->
            StudentRecord(
                fullName = row.fullName,
                nim = row.nim,
                email = "${row.nim}@mahasiswa.azrilclas.ac.id",
                studyProgramId = row.studyProgramId!!,
                studyProgramName = row.studyProgramCode,
                classId = "cls-${row.className.lowercase()}",
                className = row.className,
                semester = row.semester,
                cohortYear = 2025,
                status = AccountStatus.ACTIVE,
                badgeStatus = if (row.validationStatus == ExcelImportValidationStatus.VALID_AFK) StudentBadgeStatus.AFK else StudentBadgeStatus.NEUTRAL,
                mustChangePassword = true
            )
        }

        val created = repo.adminBulkImportStudents(
            newStudents = toCreate,
            fileName = parsed.fileName,
            semesterKey = "2026/2027-Ganjil",
            importHistoricalAttendance = true,
            previewRowsForSync = parsed.rows.filter { it.isImportable }
        )
        assertEquals(6, created.size)

        // Black-highlighted rows NEVER get accounts!
        val blackLogin = repo.login("250701048", "250701048", rememberMe = false)
        assertTrue(blackLogin is AuthResult.Failure)
        val mixedBlackLogin = repo.login("250701049", "250701049", rememberMe = false)
        assertTrue(mixedBlackLogin is AuthResult.Failure)

        // Normal student logs in with Username = NIM, Password = NIM -> MUST change password!
        val firstLogin = repo.login("250701046", "250701046", rememberMe = true)
        assertTrue(firstLogin is AuthResult.Success)
        val firstProfile = (firstLogin as AuthResult.Success).profile
        assertEquals("250701046", firstProfile.nim)
        assertEquals("PJKR", firstProfile.studyProgramName)
        assertEquals("PJKR-3B", firstProfile.className)
        assertTrue(firstProfile.mustChangePassword)

        // Cannot set new password equal to NIM
        val sameAsNim = repo.completeFirstLoginPasswordChange(firstProfile.id, "250701046")
        assertTrue(sameAsNim is AuthResult.Failure)

        // Valid new password clears mustChangePassword
        val changed = repo.completeFirstLoginPasswordChange(firstProfile.id, "RahasiaBaru2026")
        assertTrue(changed is AuthResult.Success)
        assertFalse((changed as AuthResult.Success).profile.mustChangePassword)

        // Logout -> old NIM password FAILS; new password SUCCEEDS without forcing password change
        repo.logout()
        val oldPwAttempt = repo.login("250701046", "250701046", rememberMe = false)
        assertTrue(oldPwAttempt is AuthResult.Failure)

        val newPwAttempt = repo.login("250701046", "RahasiaBaru2026", rememberMe = true)
        assertTrue(newPwAttempt is AuthResult.Success)
        assertFalse((newPwAttempt as AuthResult.Success).profile.mustChangePassword)

        // Red-highlighted AFK student CAN log in and retains AFK badge status
        val afkLogin = repo.login("250701047", "250701047", rememberMe = false)
        assertTrue(afkLogin is AuthResult.Success)
        assertEquals(StudentBadgeStatus.AFK, (afkLogin as AuthResult.Success).profile.badgeStatus)
        assertTrue(afkLogin.profile.mustChangePassword)

        // Re-import same file -> ZERO duplicate accounts and existing passwords are NOT overwritten!
        val reImportAttempt = repo.adminBulkImportStudents(
            newStudents = toCreate,
            fileName = parsed.fileName,
            semesterKey = "2026/2027-Ganjil",
            importHistoricalAttendance = true
        )
        assertEquals(0, reImportAttempt.size)
        // Existing student's changed password still works!
        val stillWorks = repo.login("250701046", "RahasiaBaru2026", rememberMe = false)
        assertTrue(stillWorks is AuthResult.Success)
        val preExistingStillWorks = repo.login("250901090", "CustomPassword99!", rememberMe = false)
        assertTrue(preExistingStillWorks is AuthResult.Success)

        // Admin reset password re-enables mustChangePassword = true
        val resetErr = repo.adminResetStudentPassword(firstProfile.id, "250701046", requirePasswordChange = true)
        assertNull(resetErr)
        val afterResetLogin = repo.login("250701046", "250701046", rememberMe = false)
        assertTrue(afterResetLogin is AuthResult.Success)
        assertTrue((afterResetLogin as AuthResult.Success).profile.mustChangePassword)
    }

    @Test
    fun endToEndViewModelBulkImportHistoricalAttendanceZeroAutoXpExplicitCalcAndNimConsistency() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AzrilViewModel(app)

        fun waitUntil(timeoutMs: Long = 8000L, predicate: () -> Boolean) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (!predicate() && System.currentTimeMillis() < deadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(25)
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }

        waitUntil { !vm.uiState.value.isRestoringSession }

        // Enter Admin session
        vm.enterAdminPreview()
        assertEquals(UserRole.ADMIN, vm.uiState.value.currentUser?.role)

        // Parse multi-sheet workbook
        val preview = vm.parseExcelStudentImport(
            fileName = "Absensi_IT_PROGRAM_2026_Minggu_1_sd_8.xlsx",
            bytes = buildComprehensiveTestWorkbook()
        ).getOrThrow()

        // Commit import WITH historical attendance enabled
        val err = vm.commitExcelStudentImport(preview = preview, importHistoricalAttendance = true)
        assertNull(err)

        val stateAfterImport = vm.uiState.value

        // 7 valid students in workbook without pre-existing (4 normal + 2 AFK + 250901090 since clean DB) = 7
        assertEquals(7, stateAfterImport.students.size)
        // Classes PJKR-3B, FARMASI-3, HES-3B auto-created
        assertTrue(stateAfterImport.classes.any { it.name == "PJKR-3B" && it.studyProgramName == "PJKR" })
        assertTrue(stateAfterImport.classes.any { it.name == "FARMASI-3" && it.studyProgramName == "FARMASI" })
        assertTrue(stateAfterImport.classes.any { it.name == "HES-3B" && it.studyProgramName == "HES" })

        // BLACK rows strictly absent from students AND historical attendance!
        assertFalse(stateAfterImport.students.any { it.nim == "250701048" || it.nim == "250701049" })
        assertFalse(stateAfterImport.historicalAttendanceRecords.any { it.studentNim == "250701048" || it.studentNim == "250701049" })

        // RED rows imported as AFK and excluded from active leaderboard
        val afkStudent = stateAfterImport.students.firstOrNull { it.nim == "250701047" }
        assertNotNull(afkStudent)
        assertEquals(StudentBadgeStatus.AFK, afkStudent!!.badgeStatus)
        val activeLeaderboard = AzrilViewModel.computeRankings(stateAfterImport)
        assertFalse(activeLeaderboard.any { it.studentId == afkStudent.id })
        assertTrue(AzrilViewModel.computeAfkStudents(stateAfterImport).any { it.studentId == afkStudent.id })

        // Historical attendance preserves EXACT raw strings and awards ZERO automatic XP!
        val ghaniHistory = stateAfterImport.historicalAttendanceRecords.filter { it.studentNim == "250701046" }
        assertEquals(8, ghaniHistory.size)
        assertTrue(ghaniHistory.all { !it.xpCalculated })
        assertEquals("DISPEN", ghaniHistory.first { it.weekNumber == 5 }.rawStatus)
        assertEquals("KIP", ghaniHistory.first { it.weekNumber == 6 }.rawStatus)
        assertEquals("TIDAK HADIR", ghaniHistory.first { it.weekNumber == 4 }.rawStatus)
        assertEquals(0, stateAfterImport.rankingPoints.size)

        // Explicit Admin confirmation calculates historical XP accurately with Izin limit (max 2 paid Izin = 10 each, 3rd = 0)
        // SITI NURHALIZA (FARMASI-3) has: 3x HADIR (60), 3x IZIN (10+10+0=20), DISPEN (0), KIP (0) -> 80 XP!
        vm.calculateHistoricalAttendanceXp(
            classNameFilter = "FARMASI-3",
            countDispenAsHadir = false,
            countKipAsHadir = false
        )
        val siti = vm.uiState.value.students.first { it.nim == "250801001" }
        val sitiXp = vm.uiState.value.rankingPoints.filter { it.studentId == siti.id }.sumOf { it.points }
        assertEquals(80, sitiXp)

        // Running historical XP calculation again for FARMASI-3 creates ZERO duplicate XP!
        val txCountBefore = vm.uiState.value.rankingPoints.size
        vm.calculateHistoricalAttendanceXp(classNameFilter = "FARMASI-3")
        assertEquals(txCountBefore, vm.uiState.value.rankingPoints.size)

        // Edit NIM consistency across student record and historical attendance
        val ghani = vm.uiState.value.students.first { it.nim == "250701046" }
        val editErr = vm.updateStudent(
            studentId = ghani.id,
            fullName = "ABDUL GHANI UPDATED",
            nim = "250701888",
            email = ghani.email,
            studyProgramId = ghani.studyProgramId,
            classId = ghani.classId,
            semester = ghani.semester,
            cohortYear = ghani.cohortYear,
            accountStatus = ghani.status,
            badgeStatus = ghani.badgeStatus
        )
        assertNull(editErr)
        assertTrue(vm.uiState.value.historicalAttendanceRecords.filter { it.studentId == ghani.id }.all { it.studentNim == "250701888" })

        // CSV Export contains updated NIM and AFK status
        val csv = ExcelStudentImportParser.buildStudentsCsvExport(vm.uiState.value.students)
        assertTrue(csv.contains("250701888"))
        assertTrue(csv.contains("ABDUL GHANI UPDATED"))
        assertTrue(csv.contains("AFK"))

        // Wait briefly for background DataStore persistence of imported accounts, then test Student login + RBAC
        Thread.sleep(600)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        vm.logout()
        waitUntil { vm.uiState.value.currentUser == null }

        vm.loginWithCredentials("250801001", "250801001", rememberMe = false)
        waitUntil { vm.uiState.value.currentUser != null && !vm.uiState.value.authInProgress }
        assertEquals(UserRole.STUDENT, vm.uiState.value.currentUser?.role)
        assertTrue(vm.uiState.value.currentUser!!.mustChangePassword)

        // Student CANNOT trigger Admin Excel import or historical XP calculation
        val studentImportAttempt = vm.commitExcelStudentImport(preview, importHistoricalAttendance = false)
        assertNotNull(studentImportAttempt)
        assertTrue(studentImportAttempt!!.contains("Akses ditolak", ignoreCase = true))
        val studentCalcAttempt = vm.calculateHistoricalAttendanceXp()
        assertTrue(studentCalcAttempt.contains("Akses ditolak", ignoreCase = true))
    }
}
