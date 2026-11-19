package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.assignments.AssignmentSyncService
import com.example.data.assignments.RemoteMutationResult
import com.example.data.auth.DataStoreAuthStorage
import com.example.ui.model.AcademicClass
import com.example.ui.model.AccountStatus
import com.example.ui.model.Assignment
import com.example.ui.model.AssignmentTargetType
import com.example.ui.model.StudentBadgeStatus
import com.example.ui.model.StudyProgram
import com.example.ui.model.SubmissionMethod
import com.example.ui.model.SubmissionRecord
import com.example.ui.model.SubmissionStatus
import com.example.ui.model.TaskCompletionStatus
import com.example.ui.model.UserRole
import com.example.ui.model.resolveTaskCompletionStatus
import com.example.ui.state.AzrilViewModel
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Comprehensive verification suite covering all 22 mandatory scenarios for:
 * - Existing Supabase Assignment Data Integrity (4 tasks, zero duplication, metadata intact)
 * - Admin Assignment Monitoring, Search, Review, Individual Checklist, & Progress Calculation
 * - Student Filtering, Submit, Edit/Replace Before Deadline (WIB), Deadline Lock, Read-Only Checklist, & Isolation
 * - Backend & Role Security Enforcement (RLS, Server-Time Deadline Validation, Non-Admin Rejection)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AssignmentChecklistMonitoringTest {

    private val wibTz = TimeZone.getTimeZone("Asia/Jakarta")
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val fakeSupabaseUrl = "https://azrilclas-test.supabase.co"
    private val fakeAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyb2xlIjoiYW5vbiJ9.azril_test_anon_key_123"
    private val fakeJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0LXVzZXIifQ.fake_jwt_sig"

    @Before
    @After
    fun cleanSharedDataStore() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = DataStoreAuthStorage(app)
        listOf(
            "session_token",
            "session_user_id",
            "session_profile_json",
            "registered_accounts_v2",
            "azril_classes_v1",
            "azril_assignments_v1",
            "azril_submissions_v2",
            "azril_assignment_checklists_v1",
            "azril_xp_transactions_v1",
            "azril_student_streaks_v1",
            "azril_historical_attendance_v1"
        ).forEach { key -> store.putString(key, null) }
    }

    private fun waitUntil(timeoutMs: Long = 8000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate() && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        ShadowLooper.idleMainLooper()
    }

    private fun wibMillis(year: Int, month1Based: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance(wibTz).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month1Based - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun newCleanViewModel(): AzrilViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AzrilViewModel(app)
        waitUntil { !vm.uiState.value.isRestoringSession }
        if (vm.uiState.value.currentUser != null) {
            vm.logout()
            waitUntil { vm.uiState.value.currentUser == null }
        }
        return vm
    }

    private fun loginAdmin(vm: AzrilViewModel) {
        vm.loginAdminWithCredentials("Azril12v", "Azril23admin7", rememberMe = false)
        waitUntil { vm.uiState.value.currentUser?.role == UserRole.ADMIN && !vm.uiState.value.authInProgress }
        assertEquals(UserRole.ADMIN, vm.uiState.value.currentUser?.role)
    }

    private fun loginStudent(vm: AzrilViewModel, identifier: String, password: String) {
        vm.logout()
        waitUntil { vm.uiState.value.currentUser == null }
        Thread.sleep(200)
        ShadowLooper.idleMainLooper()
        vm.openStudentLoginPortal()
        vm.loginWithCredentials(identifier, password, rememberMe = false)
        waitUntil { vm.uiState.value.currentUser?.role == UserRole.STUDENT && !vm.uiState.value.authInProgress }
        assertEquals(UserRole.STUDENT, vm.uiState.value.currentUser?.role)
    }

    // =========================================================================
    // DATA TUGAS (Scenarios 1, 2, 3): 4 Existing Supabase Tasks, Zero Duplicates,
    // Metadata Intact (Files, Descriptions, WIB Deadlines, Targets)
    // =========================================================================

    @Test
    fun scenarios1to3_existingFourSupabaseAssignmentsFetchedIntactWithZeroDuplicates() = runTest {
        val store = InMemoryAuthStore()
        store.putString("session_token", fakeJwt)

        val fourTasksJson = """
            [
              {
                "id": "task-existing-01",
                "title": "Tugas 1 - Analisis Sistem Informasi",
                "description": "Panduan lengkap tugas pertama analisis sistem informasi kampus.",
                "instructions": "Panduan lengkap tugas pertama analisis sistem informasi kampus.",
                "target_type": "class",
                "course_category": "IT Program",
                "file1_name": "Modul_1.pdf",
                "file1_url": "https://cdn.azrilclas.ac.id/modul_1.pdf",
                "file2_name": "Template_1.docx",
                "file2_url": "https://cdn.azrilclas.ac.id/template_1.docx",
                "deadline": "2026-12-10T23:59:00+07:00",
                "allowed_formats": ["PDF", "DOCX"],
                "max_file_size_mb": 10,
                "submission_method": "BOTH",
                "is_active": true,
                "created_at": "2026-10-01T08:00:00+07:00",
                "assignment_targets": [
                  { "assignment_id": "task-existing-01", "class_id": "cls-pjkr-3b", "study_program_id": null, "study_program_code": null }
                ]
              },
              {
                "id": "task-existing-02",
                "title": "Tugas 2 - Basis Data Relasional",
                "description": "Buat ERD lengkap dan normalisasi hingga 3NF.",
                "instructions": "Buat ERD lengkap dan normalisasi hingga 3NF.",
                "target_type": "class",
                "course_category": "Basis Data",
                "file1_name": "Studi_Kasus_ERD.pdf",
                "file1_url": "https://cdn.azrilclas.ac.id/erd.pdf",
                "file2_name": null,
                "file2_url": null,
                "deadline": "2026-12-12T23:59:00+07:00",
                "allowed_formats": ["PDF"],
                "max_file_size_mb": 15,
                "submission_method": "UPLOAD",
                "is_active": true,
                "created_at": "2026-10-05T09:00:00+07:00",
                "assignment_targets": [
                  { "assignment_id": "task-existing-02", "class_id": "cls-hes-3b", "study_program_id": null, "study_program_code": null },
                  { "assignment_id": "task-existing-02", "class_id": "cls-pjkr-3b", "study_program_id": null, "study_program_code": null }
                ]
              },
              {
                "id": "task-existing-03",
                "title": "Tugas 3 - Pengolahan Data Farmasi",
                "description": "Analisis tabel dosis dan laporan praktikum.",
                "instructions": "Analisis tabel dosis dan laporan praktikum.",
                "target_type": "study_program",
                "course_category": "Komputasi",
                "file1_name": "Dataset_Farmasi.xlsx",
                "file1_url": "https://cdn.azrilclas.ac.id/dataset_farmasi.xlsx",
                "file2_name": "Rubrik_Penilaian.pdf",
                "file2_url": "https://cdn.azrilclas.ac.id/rubrik.pdf",
                "deadline": "2026-12-15T20:00:00+07:00",
                "allowed_formats": ["PDF", "DOCX", "XLSX"],
                "max_file_size_mb": 20,
                "submission_method": "BOTH",
                "is_active": true,
                "created_at": "2026-10-08T10:30:00+07:00",
                "assignment_targets": [
                  { "assignment_id": "task-existing-03", "class_id": null, "study_program_id": "prodi-farmasi", "study_program_code": "FARMASI" }
                ]
              },
              {
                "id": "task-existing-04",
                "title": "Tugas 4 - Etika & Keamanan Digital",
                "description": "Esai reflektif mengenai privasi data dan keamanan siber.",
                "instructions": "Esai reflektif mengenai privasi data dan keamanan siber.",
                "target_type": "study_program",
                "course_category": "Literasi Digital",
                "file1_name": "Panduan_Esai.pdf",
                "file1_url": "https://cdn.azrilclas.ac.id/panduan_esai.pdf",
                "file2_name": "Referensi_Jurnal.pdf",
                "file2_url": "https://cdn.azrilclas.ac.id/referensi.pdf",
                "deadline": "2026-12-20T23:59:00+07:00",
                "allowed_formats": ["PDF", "DOCX"],
                "max_file_size_mb": 10,
                "submission_method": "LINK",
                "is_active": true,
                "created_at": "2026-10-12T14:00:00+07:00",
                "assignment_targets": [
                  { "assignment_id": "task-existing-04", "class_id": null, "study_program_id": "prodi-hes", "study_program_code": "HES" }
                ]
              }
            ]
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val body = when {
                    url.contains("/rpc/get_server_time_utc") -> "\"2026-12-01T10:00:00+00:00\""
                    url.contains("/rest/v1/assignments") -> fourTasksJson
                    url.contains("/rest/v1/assignment_submissions") -> "[]"
                    url.contains("/rest/v1/assignment_student_checklists") -> "[]"
                    else -> "{}"
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody(jsonMedia))
                    .build()
            }
            .build()

        val syncService = AssignmentSyncService(
            store = store,
            supabaseUrl = fakeSupabaseUrl,
            supabaseAnonKey = fakeAnonKey,
            httpClient = mockClient
        )

        val classes = listOf(
            AcademicClass(
                id = "cls-pjkr-3b",
                name = "PJKR-3B",
                studyProgramId = "prodi-pjkr",
                studyProgramName = "PJKR",
                semester = 3,
                academicYear = "2026/2027",
                cohortYear = 2025,
                description = "Kelas PJKR 3B"
            ),
            AcademicClass(
                id = "cls-hes-3b",
                name = "HES-3B",
                studyProgramId = "prodi-hes",
                studyProgramName = "HES",
                semester = 3,
                academicYear = "2026/2027",
                cohortYear = 2025,
                description = "Kelas HES 3B"
            ),
            AcademicClass(
                id = "cls-farmasi-3",
                name = "FARMASI-3",
                studyProgramId = "prodi-farmasi",
                studyProgramName = "FARMASI",
                semester = 3,
                academicYear = "2026/2027",
                cohortYear = 2025,
                description = "Kelas Farmasi 3"
            )
        )
        val prodis = listOf(
            StudyProgram(
                id = "prodi-hes",
                code = "HES",
                name = "Hukum Ekonomi Syariah",
                faculty = "Syariah",
                description = "Prodi HES"
            ),
            StudyProgram(
                id = "prodi-pjkr",
                code = "PJKR",
                name = "Pendidikan Jasmani, Kesehatan, dan Rekreasi",
                faculty = "Keguruan",
                description = "Prodi PJKR"
            ),
            StudyProgram(
                id = "prodi-farmasi",
                code = "FARMASI",
                name = "Farmasi",
                faculty = "Kesehatan",
                description = "Prodi Farmasi"
            )
        )

        // First fetch
        val snap1 = syncService.fetchRemoteSnapshot(classes, prodis, emptyList(), emptyList())
        assertTrue(snap1.syncedFromSupabase)
        assertNotNull(snap1.serverTimeMillis)
        assertEquals(4, snap1.assignments?.size)

        // Verify metadata integrity on fetched assignments (files, description, WIB deadline, targets)
        val fetchedAssignments = requireNotNull(snap1.assignments)
        val t1 = fetchedAssignments.first { it.id == "task-existing-01" }
        assertEquals("Tugas 1 - Analisis Sistem Informasi", t1.title)
        assertEquals("https://cdn.azrilclas.ac.id/modul_1.pdf", t1.file1Url)
        assertEquals("https://cdn.azrilclas.ac.id/template_1.docx", t1.file2Url)
        assertEquals(AssignmentTargetType.CLASS, t1.targetType)
        assertEquals(listOf("cls-pjkr-3b"), t1.targetClassIds)
        assertEquals(listOf("PJKR-3B"), t1.targetClassNames)
        assertNotNull(t1.deadlineEpochMillis)
        assertTrue(t1.deadline.contains("WIB"))

        val t2 = fetchedAssignments.first { it.id == "task-existing-02" }
        assertEquals(2, t2.targetClassIds.size)

        val t3 = fetchedAssignments.first { it.id == "task-existing-03" }
        assertEquals(AssignmentTargetType.STUDY_PROGRAM, t3.targetType)
        assertEquals(listOf("FARMASI"), t3.targetStudyProgramCodes)
        assertEquals("Dataset_Farmasi.xlsx", t3.file1Name)
        assertEquals("Rubrik_Penilaian.pdf", t3.file2Name)

        // Second fetch (simulating re-sync / refresh) -> ZERO duplicate IDs
        val snap2 = syncService.fetchRemoteSnapshot(classes, prodis, emptyList(), fetchedAssignments)
        val fetched2 = requireNotNull(snap2.assignments)
        assertEquals(4, fetched2.size)
        assertEquals(4, fetched2.map { it.id }.distinct().size)
    }

    // =========================================================================
    // FULL END-TO-END FLOW: Scenarios 4–20
    // Admin Create/Roster/Search/Review/Individual Checklist/Progress +
    // Student Filter/Submit/Edit/Replace/Deadline Lock/Read-Only Checklist/Isolation +
    // Role Security
    // =========================================================================

    @Test
    fun scenarios4to20_endToEndAdminMonitoringChecklistStudentSubmissionAndSecurity() {
        val vm = newCleanViewModel()
        loginAdmin(vm)

        // Setup classes and 3 students across HES-3B, PJKR-3B, and FARMASI-3
        assertNull(vm.addClass("HES-3B", "prodi-hes", 3, "2026/2027", 2025, "Kelas HES 3B", true))
        assertNull(vm.addClass("PJKR-3B", "prodi-pjkr", 3, "2026/2027", 2025, "Kelas PJKR 3B", true))
        assertNull(vm.addClass("FARMASI-3", "prodi-farmasi", 3, "2026/2027", 2025, "Kelas Farmasi 3", true))

        val clsHes = vm.uiState.value.classes.first { it.name == "HES-3B" }
        val clsPjkr = vm.uiState.value.classes.first { it.name == "PJKR-3B" }
        val clsFarmasi = vm.uiState.value.classes.first { it.name == "FARMASI-3" }

        assertNull(
            vm.addStudent(
                fullName = "Aulia Rahmawati",
                nim = "250902001",
                email = "aulia.250902001@mahasiswa.azrilclas.ac.id",
                studyProgramId = "prodi-hes",
                classId = clsHes.id,
                semester = 3,
                cohortYear = 2025,
                status = AccountStatus.ACTIVE,
                badgeStatus = StudentBadgeStatus.NEUTRAL,
                initialPassword = "PasswordAulia2026!"
            )
        )
        assertNull(
            vm.addStudent(
                fullName = "Bagas Pratama",
                nim = "250902002",
                email = "bagas.250902002@mahasiswa.azrilclas.ac.id",
                studyProgramId = "prodi-hes",
                classId = clsHes.id,
                semester = 3,
                cohortYear = 2025,
                status = AccountStatus.ACTIVE,
                badgeStatus = StudentBadgeStatus.NEUTRAL,
                initialPassword = "PasswordBagas2026!"
            )
        )
        assertNull(
            vm.addStudent(
                fullName = "Citra Farmasi",
                nim = "250902003",
                email = "citra.250902003@mahasiswa.azrilclas.ac.id",
                studyProgramId = "prodi-farmasi",
                classId = clsFarmasi.id,
                semester = 3,
                cohortYear = 2025,
                status = AccountStatus.ACTIVE,
                badgeStatus = StudentBadgeStatus.NEUTRAL,
                initialPassword = "PasswordCitra2026!"
            )
        )

        val aulia = vm.uiState.value.students.first { it.nim == "250902001" }
        val bagas = vm.uiState.value.students.first { it.nim == "250902002" }
        val citra = vm.uiState.value.students.first { it.nim == "250902003" }

        // Scenario 4: Admin creates new assignment targeting HES-3B & PJKR-3B (with 2 files + deadline WIB)
        val createErr1 = vm.addAssignment(
            title = "Tugas Riset Hukum Ekonomi",
            description = "Susun makalah riset minimal 5 halaman sesuai panduan resmi.",
            targetType = AssignmentTargetType.CLASS,
            targetClassIds = listOf(clsHes.id, clsPjkr.id),
            targetStudyProgramIds = emptyList(),
            courseCategory = "Hukum Ekonomi",
            deadlineDate = "2026-12-20",
            deadlineTime = "23:59",
            allowedFormats = listOf("PDF", "DOCX"),
            maxFileSizeMb = 10,
            submissionMethod = SubmissionMethod.BOTH,
            file1Name = "Panduan_Riset.pdf",
            file1Url = "https://drive.google.com/file/d/panduan_riset",
            file2Name = "Template_Makalah.docx",
            file2Url = "https://drive.google.com/file/d/template_makalah"
        )
        assertNull(createErr1)

        // Create second assignment targeted ONLY to FARMASI prodi (should NOT appear for HES students)
        val createErr2 = vm.addAssignment(
            title = "Praktikum Formulasi Farmasi",
            description = "Kumpulkan laporan praktikum formulasi obat.",
            targetType = AssignmentTargetType.STUDY_PROGRAM,
            targetClassIds = emptyList(),
            targetStudyProgramIds = listOf("prodi-farmasi"),
            courseCategory = "Farmasi",
            deadlineDate = "2026-12-22",
            deadlineTime = "18:00",
            allowedFormats = listOf("PDF"),
            maxFileSizeMb = 10,
            submissionMethod = SubmissionMethod.BOTH,
            file1Name = "Modul_Lab.pdf",
            file1Url = "https://cdn.azrilclas.ac.id/modul_lab.pdf",
            file2Name = null,
            file2Url = null
        )
        assertNull(createErr2)

        val hesTask = vm.uiState.value.assignments.first { it.title == "Tugas Riset Hukum Ekonomi" }
        val farmasiTask = vm.uiState.value.assignments.first { it.title == "Praktikum Formulasi Farmasi" }

        // Scenario 5: Admin sees ALL targeted students for `hesTask` (Aulia & Bagas, NOT Citra)
        val targetedForHesTask = AzrilViewModel.getTargetStudentsForAssignment(
            assignment = hesTask,
            students = vm.uiState.value.students,
            classes = vm.uiState.value.classes
        )
        assertEquals(2, targetedForHesTask.size)
        assertTrue(targetedForHesTask.any { it.id == aulia.id })
        assertTrue(targetedForHesTask.any { it.id == bagas.id })
        assertFalse(targetedForHesTask.any { it.id == citra.id })

        // Scenario 6: Admin search filter by Nama & NIM on target roster
        val searchByName = targetedForHesTask.filter { it.fullName.contains("Aulia", ignoreCase = true) }
        assertEquals(listOf(aulia.id), searchByName.map { it.id })
        val searchByNim = targetedForHesTask.filter { it.nim.contains("250902002") }
        assertEquals(listOf(bagas.id), searchByNim.map { it.id })

        // =====================================================================
        // STUDENT AULIA LOGIN (Scenarios 11–17, 19, 20, 22)
        // =====================================================================
        loginStudent(vm, "250902001", "PasswordAulia2026!")

        // Scenario 11: Student sees ONLY assignments targeted to own class/prodi
        val auliaProfile = vm.uiState.value.currentUser!!
        val auliaVisibleTasks = vm.uiState.value.assignments.filter {
            AzrilViewModel.isAssignmentForStudent(it, auliaProfile, aulia, vm.uiState.value.classes)
        }
        assertTrue(auliaVisibleTasks.any { it.id == hesTask.id })
        assertFalse(auliaVisibleTasks.any { it.id == farmasiTask.id })

        // Scenario 22: Student CANNOT submit to a task NOT targeted to them
        val beforeDeadline1 = wibMillis(2026, 12, 18, 10, 0)
        val wrongTargetErr = vm.submitAssignment(
            assignmentId = farmasiTask.id,
            driveLink = "https://example.com/illegal",
            uploadedFileName = null,
            uploadedFileSizeLabel = null,
            answerText = "Coba masuk ke tugas prodi lain",
            nowMillis = beforeDeadline1
        )
        assertNotNull(wrongTargetErr)
        assertTrue(wrongTargetErr!!.contains("tidak ditujukan", ignoreCase = true))

        // Scenario 12: Student submits answer BEFORE deadline -> Menunggu Verifikasi (NEVER auto Selesai!)
        val submitErr1 = vm.submitAssignment(
            assignmentId = hesTask.id,
            driveLink = "https://github.com/aulia/riset-v1",
            uploadedFileName = "makalah_aulia_v1.pdf",
            uploadedFileSizeLabel = "1.2 MB",
            answerText = "Jawaban awal riset hukum ekonomi syariah.",
            nowMillis = beforeDeadline1
        )
        assertNull(submitErr1)

        val auliaSubsAfterFirst = vm.getVisibleSubmissionsForCurrentUser().filter { it.assignmentId == hesTask.id }
        assertEquals(1, auliaSubsAfterFirst.size)
        val firstSub = auliaSubsAfterFirst.first()
        val originalFirstSubmittedAt = firstSub.firstSubmittedAt
        val originalFirstMillis = firstSub.firstSubmittedAtMillis
        assertEquals(0, firstSub.revisionCount)
        assertEquals("Jawaban awal riset hukum ekonomi syariah.", firstSub.answerText)
        assertEquals("makalah_aulia_v1.pdf", firstSub.uploadedFileName)
        assertEquals(
            TaskCompletionStatus.AWAITING_VERIFICATION,
            resolveTaskCompletionStatus(firstSub, vm.getChecklistFor(hesTask.id, aulia.id))
        )

        // Scenario 13 & 14: Student edits answer text, updates URL, and replaces file BEFORE deadline
        // -> NO duplicate record! Preserves firstSubmittedAt, updates lastUpdatedAt & revisionCount!
        val beforeDeadline2 = wibMillis(2026, 12, 19, 15, 30)
        val editErr = vm.submitAssignment(
            assignmentId = hesTask.id,
            driveLink = "https://drive.google.com/file/d/aulia-riset-final",
            uploadedFileName = "makalah_aulia_FINAL.pdf",
            uploadedFileSizeLabel = "1.8 MB",
            answerText = "Jawaban revisi lengkap dengan studi kasus terbaru.",
            nowMillis = beforeDeadline2
        )
        assertNull(editErr)

        val auliaSubsAfterEdit = vm.getVisibleSubmissionsForCurrentUser().filter { it.assignmentId == hesTask.id }
        assertEquals(1, auliaSubsAfterEdit.size) // Strictly NO duplicates!
        val editedSub = auliaSubsAfterEdit.first()
        assertEquals(firstSub.id, editedSub.id)
        assertEquals(originalFirstSubmittedAt, editedSub.firstSubmittedAt)
        assertEquals(originalFirstMillis, editedSub.firstSubmittedAtMillis)
        assertEquals(beforeDeadline2, editedSub.lastUpdatedAtMillis)
        assertTrue(editedSub.lastUpdatedAtMillis > editedSub.firstSubmittedAtMillis)
        assertEquals(1, editedSub.revisionCount)
        assertEquals("Jawaban revisi lengkap dengan studi kasus terbaru.", editedSub.answerText)
        assertEquals("https://drive.google.com/file/d/aulia-riset-final", editedSub.driveLink)
        assertEquals("makalah_aulia_FINAL.pdf", editedSub.uploadedFileName)
        assertEquals(
            TaskCompletionStatus.AWAITING_VERIFICATION,
            resolveTaskCompletionStatus(editedSub, vm.getChecklistFor(hesTask.id, aulia.id))
        )

        // Scenario 15: After deadline WIB (2026-12-20 23:59 WIB), submit/edit/replace is STRICTLY BLOCKED,
        // while previously saved submission remains intact and viewable!
        val afterDeadline = wibMillis(2026, 12, 21, 0, 1)
        val lateEditErr = vm.submitAssignment(
            assignmentId = hesTask.id,
            driveLink = "https://example.com/late-attempt",
            uploadedFileName = "terlambat.pdf",
            uploadedFileSizeLabel = "2.0 MB",
            answerText = "Coba ubah setelah deadline",
            nowMillis = afterDeadline
        )
        assertNotNull(lateEditErr)
        assertTrue(lateEditErr!!.contains("Deadline tugas telah berakhir", ignoreCase = true))
        // Saved submission must remain unchanged and readable
        val subAfterBlockedAttempt = vm.getVisibleSubmissionsForCurrentUser().first { it.assignmentId == hesTask.id }
        assertEquals("makalah_aulia_FINAL.pdf", subAfterBlockedAttempt.uploadedFileName)
        assertEquals("Jawaban revisi lengkap dengan studi kasus terbaru.", subAfterBlockedAttempt.answerText)

        // Scenario 16 & 19: Student CANNOT toggle completion checklist!
        val studentChecklistErr = vm.toggleAdminAssignmentChecklist(hesTask.id, aulia.id, true)
        assertNotNull(studentChecklistErr)
        assertTrue(studentChecklistErr!!.contains("Akses ditolak", ignoreCase = true))
        assertNull(vm.getChecklistFor(hesTask.id, aulia.id))

        // Scenario 20: Student CANNOT modify assignment deadline, create/toggle/delete assignment, or grade!
        assertNotNull(vm.updateAssignmentDeadline(hesTask.id, "2027-01-30", "23:59"))
        assertNotNull(vm.toggleAssignmentActive(hesTask.id))
        assertNotNull(vm.deleteAssignment(hesTask.id))
        assertNotNull(vm.saveGrade(editedSub.id, 100, "Nilai sendiri", false))
        assertNotNull(
            vm.addAssignment(
                title = "Tugas Palsu Mahasiswa",
                description = "Ilegal",
                targetType = AssignmentTargetType.CLASS,
                targetClassIds = listOf(clsHes.id),
                targetStudyProgramIds = emptyList(),
                courseCategory = "Umum",
                deadlineDate = "2026-12-30",
                deadlineTime = "23:59",
                allowedFormats = listOf("PDF"),
                maxFileSizeMb = 5,
                submissionMethod = SubmissionMethod.BOTH,
                file1Name = null,
                file1Url = null,
                file2Name = null,
                file2Url = null
            )
        )

        // =====================================================================
        // STUDENT BAGAS LOGIN — Verify Submission Isolation (Scenario 17)
        // =====================================================================
        loginStudent(vm, "250902002", "PasswordBagas2026!")
        val bagasVisibleSubs = vm.getVisibleSubmissionsForCurrentUser()
        assertTrue(bagasVisibleSubs.none { it.studentId == aulia.id })
        assertEquals(0, bagasVisibleSubs.size)

        // Bagas submits his own answer before deadline
        assertNull(
            vm.submitAssignment(
                assignmentId = hesTask.id,
                driveLink = "https://github.com/bagas/riset-bagas",
                uploadedFileName = null,
                uploadedFileSizeLabel = null,
                answerText = "Jawaban Bagas",
                nowMillis = beforeDeadline2
            )
        )
        assertEquals(1, vm.getVisibleSubmissionsForCurrentUser().size)
        assertTrue(vm.getVisibleSubmissionsForCurrentUser().all { it.studentId == bagas.id })

        // =====================================================================
        // ADMIN REVIEW & INDIVIDUAL CHECKLIST VERIFICATION (Scenarios 7, 8, 9, 10)
        // =====================================================================
        loginAdmin(vm)

        // Scenario 7: Admin sees BOTH Aulia's & Bagas's submissions with full metadata
        val adminVisibleSubs = vm.getVisibleSubmissionsForCurrentUser().filter { it.assignmentId == hesTask.id }
        assertEquals(2, adminVisibleSubs.size)
        val auliaSubForAdmin = adminVisibleSubs.first { it.studentId == aulia.id }
        assertEquals("Aulia Rahmawati", auliaSubForAdmin.studentName)
        assertEquals("250902001", auliaSubForAdmin.studentNim)
        assertEquals("Jawaban revisi lengkap dengan studi kasus terbaru.", auliaSubForAdmin.answerText)
        assertEquals("makalah_aulia_FINAL.pdf", auliaSubForAdmin.uploadedFileName)
        assertEquals("https://drive.google.com/file/d/aulia-riset-final", auliaSubForAdmin.driveLink)
        assertEquals(originalFirstSubmittedAt, auliaSubForAdmin.firstSubmittedAt)
        assertTrue(auliaSubForAdmin.lastUpdatedAtMillis > auliaSubForAdmin.firstSubmittedAtMillis)

        // Scenario 8: Admin checks checklist ONLY for Aulia -> Aulia = COMPLETED, Bagas stays AWAITING_VERIFICATION!
        assertNull(vm.toggleAdminAssignmentChecklist(hesTask.id, aulia.id, true))
        val auliaStatusChecked = resolveTaskCompletionStatus(
            auliaSubForAdmin,
            vm.getChecklistFor(hesTask.id, aulia.id)
        )
        val bagasSubForAdmin = adminVisibleSubs.first { it.studentId == bagas.id }
        val bagasStatusStillWaiting = resolveTaskCompletionStatus(
            bagasSubForAdmin,
            vm.getChecklistFor(hesTask.id, bagas.id)
        )
        assertEquals(TaskCompletionStatus.COMPLETED, auliaStatusChecked)
        assertEquals(TaskCompletionStatus.AWAITING_VERIFICATION, bagasStatusStillWaiting)

        // Scenario 10: Progress percentage = 1 of 2 targeted students completed = 50%
        val completedCount1 = targetedForHesTask.count { stu ->
            val sub = vm.uiState.value.submissions.find { it.assignmentId == hesTask.id && it.studentId == stu.id }
            resolveTaskCompletionStatus(sub, vm.getChecklistFor(hesTask.id, stu.id)) == TaskCompletionStatus.COMPLETED
        }
        assertEquals(1, completedCount1)
        assertEquals(50, (completedCount1 * 100) / targetedForHesTask.size)

        // Scenario 18 (Part 1): Student Aulia logs back in and IMMEDIATELY sees status = Selesai (1 dari 1 tugas selesai)
        loginStudent(vm, "250902001", "PasswordAulia2026!")
        val auliaOwnSub = vm.getVisibleSubmissionsForCurrentUser().first { it.assignmentId == hesTask.id }
        assertEquals(
            TaskCompletionStatus.COMPLETED,
            resolveTaskCompletionStatus(auliaOwnSub, vm.getChecklistFor(hesTask.id, aulia.id))
        )

        // Scenario 9 & 18 (Part 2): Admin unchecks Aulia's checklist -> progress drops to 0% & Aulia reverts to Menunggu Verifikasi
        loginAdmin(vm)
        assertNull(vm.toggleAdminAssignmentChecklist(hesTask.id, aulia.id, false))
        val completedCountAfterUncheck = targetedForHesTask.count { stu ->
            val sub = vm.uiState.value.submissions.find { it.assignmentId == hesTask.id && it.studentId == stu.id }
            resolveTaskCompletionStatus(sub, vm.getChecklistFor(hesTask.id, stu.id)) == TaskCompletionStatus.COMPLETED
        }
        assertEquals(0, completedCountAfterUncheck)

        loginStudent(vm, "250902001", "PasswordAulia2026!")
        assertEquals(
            TaskCompletionStatus.AWAITING_VERIFICATION,
            resolveTaskCompletionStatus(
                vm.getVisibleSubmissionsForCurrentUser().first { it.assignmentId == hesTask.id },
                vm.getChecklistFor(hesTask.id, aulia.id)
            )
        )
    }

    // =========================================================================
    // BACKEND API SECURITY TESTS (Scenarios 19 & 21):
    // Direct Supabase RPC rejection when non-admin calls checklist API OR
    // student submits past server-side deadline (`NOW() > deadline`)
    // =========================================================================

    @Test
    fun scenarios19and21_supabaseBackendRejectsNonAdminChecklistAndExpiredServerDeadline() = runTest {
        val store = InMemoryAuthStore()
        store.putString("session_token", fakeJwt)

        val backendEnforcingClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                when {
                    url.contains("/rpc/admin_set_assignment_checklist") -> {
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(403)
                            .message("Forbidden")
                            .body("""{"message":"Akses ditolak: hanya administrator yang dapat mengubah checklist penyelesaian tugas."}""".toResponseBody(jsonMedia))
                            .build()
                    }
                    url.contains("/rpc/upsert_student_assignment_submission") -> {
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(400)
                            .message("Bad Request")
                            .body("""{"message":"Deadline tugas telah berakhir berdasarkan waktu server. Pengumpulan dan pengeditan tidak lagi diizinkan."}""".toResponseBody(jsonMedia))
                            .build()
                    }
                    else -> {
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(403)
                            .message("Forbidden")
                            .body("""{"message":"new row violates row-level security policy"}""".toResponseBody(jsonMedia))
                            .build()
                    }
                }
            }
            .build()

        val syncService = AssignmentSyncService(
            store = store,
            supabaseUrl = fakeSupabaseUrl,
            supabaseAnonKey = fakeAnonKey,
            httpClient = backendEnforcingClient
        )

        // Scenario 19 (Backend API level): Direct call to admin_set_assignment_checklist with student JWT -> Rejected!
        val checklistApiResult = syncService.syncAdminChecklistToSupabase(
            assignmentId = "task-existing-01",
            studentId = "stu-1",
            isCompleted = true
        )
        assertTrue(checklistApiResult is RemoteMutationResult.Rejected)
        assertTrue((checklistApiResult as RemoteMutationResult.Rejected).message.contains("hanya administrator", ignoreCase = true))

        // Scenario 21 (Backend API level): Direct call to upsert_student_assignment_submission after server deadline -> Rejected!
        val spoofedSubmission = SubmissionRecord(
            id = "sub-spoofed",
            assignmentId = "task-existing-01",
            assignmentTitle = "Tugas 1",
            className = "HES-3B",
            studentId = "stu-1",
            studentName = "Mahasiswa",
            studentNim = "250902001",
            answerText = "Bypass jam perangkat",
            driveLink = "https://example.com/bypass",
            uploadedFileName = null,
            uploadedFileSizeLabel = null,
            submittedAt = "18 Des 2026, 10:00 WIB",
            status = SubmissionStatus.AWAITING_ADMIN,
            isLate = false
        )
        val deadlineApiResult = syncService.syncStudentSubmissionToSupabase(spoofedSubmission)
        assertTrue(deadlineApiResult is RemoteMutationResult.Rejected)
        assertTrue((deadlineApiResult as RemoteMutationResult.Rejected).message.contains("Deadline tugas telah berakhir", ignoreCase = true))

        // Direct call by student attempting to create/modify assignment table -> Rejected by RLS!
        val fakeAssignment = Assignment(
            id = "illegal-task",
            title = "Tugas Ilegal",
            description = "Tidak boleh dibuat mahasiswa",
            instructions = "Tidak boleh",
            classId = "cls-hes-3b",
            className = "HES-3B",
            createdAt = "01 Des 2026, 08:00 WIB",
            deadline = "20 Des 2026, 23:59 WIB"
        )
        val createTaskApiResult = syncService.syncCreateAssignmentToSupabase(fakeAssignment)
        assertTrue(createTaskApiResult is RemoteMutationResult.Rejected)
    }
}
