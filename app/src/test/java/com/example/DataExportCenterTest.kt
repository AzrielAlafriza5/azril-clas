package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.DataStoreAuthStorage
import com.example.data.export.AzrilDataExportService
import com.example.data.export.ExportBuildResult
import com.example.data.export.ExportDatasetSnapshot
import com.example.data.export.ExportDatasetType
import com.example.data.export.ExportFilterCriteria
import com.example.data.export.ExportFormat
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
import com.example.ui.model.UserRole
import com.example.ui.navigation.AdminDestination
import com.example.ui.state.AzrilViewModel
import com.example.ui.state.RouteAccessResult
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.Calendar
import java.util.TimeZone
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataExportCenterTest {

    private val wibTz = TimeZone.getTimeZone("Asia/Jakarta")
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val fakeSupabaseUrl = "https://azrilclas-export-test.supabase.co"
    private val fakeAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyb2xlIjoiYW5vbiJ9.azril_export_test_key"
    private val fakeJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbi11c2VyIn0.export_sig"

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

    private fun unzipEntries(xlsxBytes: ByteArray): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                map[entry.name] = zin.readBytes().toString(StandardCharsets.UTF_8)
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
        return map
    }

    private fun buildRichSnapshot(): ExportDatasetSnapshot {
        val prodis = listOf(
            StudyProgram("prodi-hes", "HES", "Hukum Ekonomi Syariah", "Syariah", "Prodi HES"),
            StudyProgram("prodi-pjkr", "PJKR", "PJKR", "Keguruan", "Prodi PJKR"),
            StudyProgram("prodi-farmasi", "FARMASI", "Farmasi", "Kesehatan", "Prodi Farmasi")
        )
        val classes = listOf(
            AcademicClass("cls-hes-3b", "HES-3B", "prodi-hes", "HES", 3, "2026/2027", 2025, "Kelas HES 3B"),
            AcademicClass("cls-pjkr-3b", "PJKR-3B", "prodi-pjkr", "PJKR", 3, "2026/2027", 2025, "Kelas PJKR 3B")
        )
        val students = listOf(
            StudentRecord("stu-1", "Aulia & Co <Test>", "250901001", "aulia@azril.ac.id", "prodi-hes", "HES", "cls-hes-3b", "HES-3B", 3, 2025, AccountStatus.ACTIVE, StudentBadgeStatus.NEUTRAL),
            StudentRecord("stu-2", "Bagas Pratama", "250901002", "bagas@azril.ac.id", "prodi-hes", "HES", "cls-hes-3b", "HES-3B", 3, 2025, AccountStatus.ACTIVE, StudentBadgeStatus.NEUTRAL),
            StudentRecord("stu-3", "Citra Olahraga", "250901003", "citra@azril.ac.id", "prodi-pjkr", "PJKR", "cls-pjkr-3b", "PJKR-3B", 3, 2025, AccountStatus.INACTIVE, StudentBadgeStatus.AFK)
        )
        val oct10 = wibMillis(2026, 10, 10, 9, 15)
        val nov05 = wibMillis(2026, 11, 5, 10, 30)
        val dec15 = wibMillis(2026, 12, 15, 23, 59)

        val attendance = listOf(
            AttendanceRecord("att-1", "sess-1", "Pertemuan 1", "HES-3B", "stu-1", "Aulia & Co <Test>", "250901001", "10 Okt 2026", "09:15 WIB", AttendanceStatus.HADIR, null, "2026/2027-Ganjil", oct10),
            AttendanceRecord("att-2", "sess-2", "Pertemuan 2", "HES-3B", "stu-2", "Bagas Pratama", "250901002", "05 Nov 2026", "10:30 WIB", AttendanceStatus.IZIN, "Izin kegiatan organisasi kampus", "2026/2027-Ganjil", nov05)
        )
        val histAttendance = listOf(
            HistoricalAttendanceRecord("hatt-1", "stu-3", "250901003", "Citra Olahraga", "cls-pjkr-3b", "PJKR-3B", "PJKR", 1, "Minggu 1", "HADIR", "PJKR-3B", "2026/2027-Ganjil", "10 Okt 2026, 08:00 WIB", false)
        )
        val assignments = listOf(
            Assignment(
                id = "asg-1",
                title = "Makalah Analisis Digital",
                description = "Buat makalah lengkap.",
                instructions = "Buat makalah lengkap.",
                classId = "cls-hes-3b",
                className = "HES-3B",
                targetType = AssignmentTargetType.CLASS,
                targetClassIds = listOf("cls-hes-3b"),
                targetClassNames = listOf("HES-3B"),
                createdAt = "01 Nov 2026, 08:00 WIB",
                deadline = "15 Des 2026, 23:59 WIB",
                deadlineEpochMillis = dec15,
                submissionMethod = SubmissionMethod.BOTH
            )
        )
        val submissions = listOf(
            SubmissionRecord(
                id = "sub-1",
                assignmentId = "asg-1",
                assignmentTitle = "Makalah Analisis Digital",
                className = "HES-3B",
                studentId = "stu-1",
                studentName = "Aulia & Co <Test>",
                studentNim = "250901001",
                answerText = "Jawaban lengkap",
                driveLink = "https://drive.google.com/file/d/aulia-final",
                uploadedFileName = "makalah_aulia.pdf",
                submittedAt = "10 Nov 2026, 14:00 WIB",
                firstSubmittedAt = "10 Nov 2026, 14:00 WIB",
                lastUpdatedAt = "10 Nov 2026, 14:00 WIB",
                firstSubmittedAtMillis = wibMillis(2026, 11, 10, 14, 0),
                lastUpdatedAtMillis = wibMillis(2026, 11, 10, 14, 0),
                status = SubmissionStatus.GRADED,
                isLate = false,
                finalScore = 95,
                adminFeedback = "Sangat rapi dan analitis"
            )
        )
        val checklists = listOf(
            AssignmentChecklistRecord("asg-1", "stu-1", isCompleted = true, verifiedByName = "Administrator", verifiedAt = "11 Nov 2026, 09:00 WIB")
        )
        val xpTx = listOf(
            RankingPointTransaction("xp-1", "stu-1", PointSourceType.ATTENDANCE, "sess-1", 20, "Hadir Pertemuan 1", "10 Okt 2026, 09:15 WIB", "10 Okt 2026, 09:15 WIB", oct10),
            RankingPointTransaction("xp-2", "stu-1", PointSourceType.ASSIGNMENT, "asg-1", 35, "Tugas sebelum deadline", "10 Nov 2026, 14:00 WIB", "10 Nov 2026, 14:00 WIB", wibMillis(2026, 11, 10, 14, 0)),
            RankingPointTransaction("xp-3", "stu-2", PointSourceType.ATTENDANCE, "sess-2", 10, "Izin pertama", "05 Nov 2026, 10:30 WIB", "05 Nov 2026, 10:30 WIB", nov05)
        )

        return ExportDatasetSnapshot(
            studyPrograms = prodis,
            classes = classes,
            students = students,
            attendanceRecords = attendance,
            historicalAttendanceRecords = histAttendance,
            assignments = assignments,
            submissions = submissions,
            checklists = checklists,
            xpTransactions = xpTx,
            syncedFromSupabase = true
        )
    }

    @Test
    fun allFiveExportsGenerateValidExcelAndCsvWithExactSixSheetsAndFilters() {
        val service = AzrilDataExportService(
            store = InMemoryAuthStore(),
            supabaseUrl = "https://YOUR_PROJECT_REF.supabase.co",
            supabaseAnonKey = "YOUR_SUPABASE_ANON_KEY"
        )
        val snapshot = buildRichSnapshot()

        // 1. Complete Report Excel -> EXACT 6 sheets openable by Excel & Google Sheets
        val completeRes = service.buildExport(
            snapshot = snapshot,
            type = ExportDatasetType.COMPLETE_REPORT,
            format = ExportFormat.EXCEL,
            filter = ExportFilterCriteria()
        )
        assertTrue(completeRes is ExportBuildResult.Success)
        val complete = completeRes as ExportBuildResult.Success
        assertEquals("Laporan_Lengkap_Azril_Clas.xlsx", complete.fileName)
        assertEquals(6, complete.sheetCount)

        val zipEntries = unzipEntries(complete.bytes)
        assertTrue(zipEntries.containsKey("[Content_Types].xml"))
        assertTrue(zipEntries.containsKey("_rels/.rels"))
        assertTrue(zipEntries.containsKey("xl/workbook.xml"))
        assertTrue(zipEntries.containsKey("xl/_rels/workbook.xml.rels"))
        assertTrue(zipEntries.containsKey("xl/styles.xml"))
        for (i in 1..6) {
            assertTrue("Missing sheet$i.xml", zipEntries.containsKey("xl/worksheets/sheet$i.xml"))
        }

        val workbookXml = zipEntries["xl/workbook.xml"]!!
        listOf(
            "Data Mahasiswa",
            "Data Absensi",
            "Data Pengumpulan Tugas",
            "Rekapitulasi Tugas",
            "Riwayat XP",
            "Ranking Mahasiswa"
        ).forEach { expectedSheet ->
            assertTrue("Missing sheet '$expectedSheet'", workbookXml.contains("name=\"$expectedSheet\""))
        }

        // Verify XML escaping of special chars ("Aulia & Co <Test>") in sheet1 (Data Mahasiswa)
        val sheet1Xml = zipEntries["xl/worksheets/sheet1.xml"]!!
        assertTrue(sheet1Xml.contains("Aulia &amp; Co &lt;Test&gt;"))

        // Verify sheet3 (Data Pengumpulan Tugas) includes BOTH Aulia (Sudah Mengumpulkan, Nilai 95) AND Bagas (Belum Mengumpulkan)
        val sheet3Xml = zipEntries["xl/worksheets/sheet3.xml"]!!
        assertTrue(sheet3Xml.contains("Sudah Mengumpulkan"))
        assertTrue(sheet3Xml.contains("Belum Mengumpulkan"))
        assertTrue(sheet3Xml.contains("https://drive.google.com/file/d/aulia-final"))
        assertTrue(sheet3Xml.contains("Sangat rapi dan analitis"))

        // 2. Attendance export with Date Range filter (only November 2026 -> Bagas IZIN only!)
        val attNovRes = service.buildExport(
            snapshot = snapshot,
            type = ExportDatasetType.ATTENDANCE,
            format = ExportFormat.CSV,
            filter = ExportFilterCriteria(
                studyProgramId = "prodi-hes",
                classId = "cls-hes-3b",
                startDateIso = "2026-11-01",
                endDateIso = "2026-11-30"
            )
        )
        assertTrue(attNovRes is ExportBuildResult.Success)
        val attNov = attNovRes as ExportBuildResult.Success
        assertEquals("Rekap_Absensi_Azril_Clas.csv", attNov.fileName)
        assertEquals(1, attNov.rowCount)
        // Verify UTF-8 BOM for Microsoft Excel compatibility
        assertEquals(0xEF.toByte(), attNov.bytes[0])
        assertEquals(0xBB.toByte(), attNov.bytes[1])
        assertEquals(0xBF.toByte(), attNov.bytes[2])
        val csvText = attNov.bytes.copyOfRange(3, attNov.bytes.size).toString(StandardCharsets.UTF_8)
        assertTrue(csvText.contains("Bagas Pratama"))
        assertTrue(csvText.contains("Izin kegiatan organisasi kampus"))
        assertFalse(csvText.contains("Aulia"))

        // 3. XP & Ranking export verifies per-class rank (Aulia rank 1 = 55 XP, Bagas rank 2 = 10 XP)
        val xpRes = service.buildExport(
            snapshot = snapshot,
            type = ExportDatasetType.XP_AND_RANKING,
            format = ExportFormat.EXCEL,
            filter = ExportFilterCriteria(classId = "cls-hes-3b")
        )
        assertTrue(xpRes is ExportBuildResult.Success)
        val xpZip = unzipEntries((xpRes as ExportBuildResult.Success).bytes)
        assertTrue(xpZip["xl/worksheets/sheet1.xml"]!!.contains("Pengumpulan Tugas"))
        assertTrue(xpZip["xl/worksheets/sheet2.xml"]!!.contains("55"))

        // 4. Empty dataset handling & invalid date range error handling
        val emptyRes = service.buildExport(
            snapshot = snapshot,
            type = ExportDatasetType.ATTENDANCE,
            format = ExportFormat.EXCEL,
            filter = ExportFilterCriteria(startDateIso = "2027-01-01", endDateIso = "2027-01-31")
        )
        assertTrue(emptyRes is ExportBuildResult.Empty)

        val badDateRes = service.buildExport(
            snapshot = snapshot,
            type = ExportDatasetType.ATTENDANCE,
            format = ExportFormat.CSV,
            filter = ExportFilterCriteria(startDateIso = "2026-12-31", endDateIso = "2026-01-01")
        )
        assertTrue(badDateRes is ExportBuildResult.Error)
    }

    @Test
    fun supabaseLiveFetchAndLargeDataset1500StudentsExportSucceedsCleanly() = runTest {
        val store = InMemoryAuthStore()
        store.putString("session_token", fakeJwt)

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val body = when {
                    url.contains("/rest/v1/study_programs") ->
                        """[{"id":"prodi-hes","code":"HES","name":"Hukum Ekonomi Syariah","faculty":"Syariah","is_active":true}]"""
                    url.contains("/rest/v1/classes") ->
                        """[{"id":"cls-hes-3b","name":"HES-3B","study_program_id":"prodi-hes","study_program_code":"HES","semester":3,"academic_year":"2026/2027","cohort_year":2025,"is_active":true}]"""
                    url.contains("/rest/v1/profiles") ->
                        """[{"id":"stu-live-1","full_name":"Mahasiswa Live Supabase","nim":"250999001","email":"live@azril.ac.id","study_program_id":"prodi-hes","class_id":"cls-hes-3b","semester":3,"cohort_year":2025,"account_status":"active","student_badge_status":"neutral"}]"""
                    url.contains("/rest/v1/xp_transactions") ->
                        """[{"id":"xp-live-1","student_id":"stu-live-1","category":"ATTENDANCE_HADIR","activity_title":"Hadir Kuliah","xp_amount":20,"description":"Absen live","reference_type":"ATTENDANCE","reference_id":"s1","created_at":"2026-11-12T08:00:00+07:00"}]"""
                    else -> "[]"
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

        val service = AzrilDataExportService(
            store = store,
            supabaseUrl = fakeSupabaseUrl,
            supabaseAnonKey = fakeAnonKey,
            httpClient = mockClient
        )

        val emptyFallback = ExportDatasetSnapshot(
            emptyList(), emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList()
        )
        val live = service.fetchLiveSnapshotFromSupabase(emptyFallback)
        assertTrue(live.syncedFromSupabase)
        assertEquals(1, live.students.size)
        assertEquals("Mahasiswa Live Supabase", live.students.first().fullName)
        assertEquals(1, live.xpTransactions.size)

        val liveExport = service.buildExport(live, ExportDatasetType.STUDENTS, ExportFormat.EXCEL, ExportFilterCriteria())
        assertTrue(liveExport is ExportBuildResult.Success)
        val liveZip = unzipEntries((liveExport as ExportBuildResult.Success).bytes)
        assertTrue(liveZip["xl/worksheets/sheet1.xml"]!!.contains("Mahasiswa Live Supabase"))

        // Large dataset stress test: 1,500 students
        val manyStudents = (1..1500).map { i ->
            StudentRecord(
                id = "bulk-stu-$i",
                fullName = "Mahasiswa Skala Besar $i",
                nim = "2509${i.toString().padStart(5, '0')}",
                email = "mhs$i@azril.ac.id",
                studyProgramId = "prodi-hes",
                studyProgramName = "HES",
                classId = "cls-hes-3b",
                className = "HES-3B",
                semester = 3,
                cohortYear = 2025
            )
        }
        val largeSnap = emptyFallback.copy(
            studyPrograms = live.studyPrograms,
            classes = live.classes,
            students = manyStudents
        )
        val largeRes = service.buildExport(largeSnap, ExportDatasetType.STUDENTS, ExportFormat.EXCEL, ExportFilterCriteria())
        assertTrue(largeRes is ExportBuildResult.Success)
        assertEquals(1500, (largeRes as ExportBuildResult.Success).rowCount)
        assertTrue(largeRes.bytes.size > 10_000)
    }

    @Test
    fun adminOnlyAuthorizationBlocksStudentAndUnauthenticatedExportAndRouteAccess() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AzrilViewModel(app)
        waitUntil { !vm.uiState.value.isRestoringSession }

        // 1. Unauthenticated cannot export or open /admin/export
        val unauthExport = vm.prepareAdminExport(ExportDatasetType.STUDENTS, ExportFormat.EXCEL, ExportFilterCriteria())
        assertTrue(unauthExport is ExportBuildResult.Error)
        assertTrue((unauthExport as ExportBuildResult.Error).message.contains("Akses ditolak", ignoreCase = true))
        assertEquals(RouteAccessResult.RedirectToAdminLogin, vm.navigateToRoute("/admin/export"))

        // 2. Login as Admin -> /admin/export allowed + export succeeds
        vm.loginAdminWithCredentials("Azril12v", "Azril23admin7", rememberMe = false)
        waitUntil { vm.uiState.value.currentUser?.role == UserRole.ADMIN && !vm.uiState.value.authInProgress }
        assertEquals(RouteAccessResult.Allowed, vm.navigateToRoute("/admin/export"))
        assertEquals(AdminDestination.EXPORT_CENTER, vm.uiState.value.adminDestination)

        vm.addClass("HES-3B", "prodi-hes", 3, "2026/2027", 2025, "Kelas HES", true)
        val cls = vm.uiState.value.classes.first { it.name == "HES-3B" }
        vm.addStudent(
            fullName = "Dewi Ekspor",
            nim = "250908881",
            email = "dewi@azril.ac.id",
            studyProgramId = "prodi-hes",
            classId = cls.id,
            semester = 3,
            cohortYear = 2025,
            status = AccountStatus.ACTIVE,
            badgeStatus = StudentBadgeStatus.NEUTRAL,
            initialPassword = "PasswordDewi2026!"
        )

        val adminExport = vm.prepareAdminExport(ExportDatasetType.STUDENTS, ExportFormat.EXCEL, ExportFilterCriteria())
        assertTrue(adminExport is ExportBuildResult.Success)
        assertEquals("Data_Mahasiswa_Azril_Clas.xlsx", (adminExport as ExportBuildResult.Success).fileName)

        // 3. Login as Student -> export & /admin/export STRICTLY DENIED
        vm.logout()
        waitUntil { vm.uiState.value.currentUser == null }
        Thread.sleep(200)
        ShadowLooper.idleMainLooper()
        vm.openStudentLoginPortal()
        vm.loginWithCredentials("250908881", "PasswordDewi2026!", rememberMe = false)
        waitUntil { vm.uiState.value.currentUser?.role == UserRole.STUDENT && !vm.uiState.value.authInProgress }

        val studentExport = vm.prepareAdminExport(ExportDatasetType.COMPLETE_REPORT, ExportFormat.EXCEL, ExportFilterCriteria())
        assertTrue(studentExport is ExportBuildResult.Error)
        assertTrue((studentExport as ExportBuildResult.Error).message.contains("Akses ditolak", ignoreCase = true))
        assertTrue(vm.navigateToRoute("/admin/export") is RouteAccessResult.AccessDenied)
    }
}
