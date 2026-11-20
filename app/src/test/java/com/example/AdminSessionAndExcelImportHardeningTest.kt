package com.example

import com.example.data.auth.AuthKeyValueStore
import com.example.data.auth.AuthResult
import com.example.data.auth.AzrilAuthRepository
import com.example.data.auth.RemoteDirectoryResult
import com.example.data.importer.ExcelStudentImportParser
import com.example.ui.model.AcademicClass
import com.example.ui.model.AccountStatus
import com.example.ui.model.ExcelImportValidationStatus
import com.example.ui.model.StudentBadgeStatus
import com.example.ui.model.StudentRecord
import com.example.ui.model.UserRole
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLDecoder
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdminSessionAndExcelImportHardeningTest {

    private val testSupabaseUrl = "https://dtavbwdnopbizlvokphd.supabase.co"
    private val testAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test-anon-key"

    private class TestKeyValueStore : AuthKeyValueStore {
        private val strings = mutableMapOf<String, String>()
        private val booleans = mutableMapOf<String, Boolean>()
        private val longs = mutableMapOf<String, Long>()

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

    private class HardeningFakeSupabase {
        val prodiHesUuid = "11111111-1111-4111-8111-111111111111"
        val prodiPjkrUuid = "22222222-2222-4222-8222-222222222222"
        val prodiFarmasiUuid = "33333333-3333-4333-8333-333333333333"
        val adminUuid = "99999999-9999-4999-8999-999999999999"

        var validAdminJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.admin-jwt-v1"
        val refreshedAdminJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.admin-jwt-v2-refreshed"
        val adminRefreshToken = "rt_admin_supabase_refresh_12345"
        var simulateVerifyRpc404 = true
        var simulate42P17Recursion = false
        var refreshCallCount = 0

        data class AuthUser(val id: String, val email: String, val password: String)

        val authUsers = mutableMapOf(
            adminUuid to AuthUser(adminUuid, "azril.admin@kampus.ac.id", "AdminSecret#2026")
        )

        val studyPrograms = listOf(
            JSONObject().put("id", prodiHesUuid).put("code", "HES").put("name", "Hukum Ekonomi Syariah").put("is_active", true),
            JSONObject().put("id", prodiPjkrUuid).put("code", "PJKR").put("name", "PJKR").put("is_active", true),
            JSONObject().put("id", prodiFarmasiUuid).put("code", "FARMASI").put("name", "Farmasi").put("is_active", true)
        )

        val classes = mutableMapOf<String, JSONObject>()

        val profiles = mutableMapOf(
            adminUuid to JSONObject()
                .put("id", adminUuid)
                .put("full_name", "Administrator Azril")
                .put("email", "azril.admin@kampus.ac.id")
                .put("username", "Azril12v")
                .put("role", "admin")
                .put("account_status", "active")
        )

        private fun readBody(request: Request): String {
            val body = request.body ?: return ""
            val buf = Buffer()
            body.writeTo(buf)
            return buf.readUtf8()
        }

        private fun jsonResp(req: Request, code: Int, payload: String): Response =
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("HTTP $code")
                .body(payload.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()

        fun buildClient(): OkHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val path = req.url.encodedPath
                val query = req.url.encodedQuery.orEmpty()
                val method = req.method
                val auth = req.header("Authorization").orEmpty().removePrefix("Bearer ").trim()

                when {
                    path.startsWith("/functions/v1/") -> jsonResp(req, 404, """{"error":"Function not deployed"}""")

                    path == "/rest/v1/rpc/resolve_admin_login_email" -> jsonResp(req, 404, """{"message":"Not Found"}""")

                    path == "/rest/v1/rpc/verify_admin_session" -> {
                        if (simulateVerifyRpc404) {
                            jsonResp(req, 404, """{"message":"Not Found"}""")
                        } else if (auth == validAdminJwt) {
                            jsonResp(req, 200, "true")
                        } else {
                            jsonResp(req, 401, """{"message":"JWT expired"}""")
                        }
                    }

                    path == "/rest/v1/rpc/get_student_leaderboard_directory" -> {
                        if (auth != validAdminJwt && !auth.contains("student-")) {
                            jsonResp(req, 401, """{"message":"JWT expired"}""")
                        } else {
                            val arr = JSONArray()
                            profiles.values.filter { !it.optString("role").equals("admin", ignoreCase = true) }.forEach { row ->
                                val prodi = studyPrograms.find { it.optString("id") == row.optString("study_program_id") }
                                val cls = classes[row.optString("class_id")]
                                arr.put(
                                    JSONObject()
                                        .put("id", row.optString("id"))
                                        .put("full_name", row.optString("full_name"))
                                        .put("nim", row.optString("nim"))
                                        .put("study_program_id", row.optString("study_program_id"))
                                        .put("study_program_code", prodi?.optString("code") ?: "HES")
                                        .put("class_id", row.optString("class_id"))
                                        .put("class_name", cls?.optString("name") ?: "")
                                        .put("semester", row.optInt("semester", 3))
                                        .put("cohort_year", row.optInt("cohort_year", 2025))
                                        .put("account_status", row.optString("account_status", "active"))
                                        .put("student_badge_status", row.optString("student_badge_status", "neutral"))
                                        .put("total_xp", 250)
                                )
                            }
                            jsonResp(req, 200, arr.toString())
                        }
                    }

                    path == "/rest/v1/rpc/resolve_login_email" -> {
                        val ident = JSONObject(readBody(req)).optString("p_identifier").trim()
                        val match = profiles.values.find {
                            it.optString("nim").equals(ident, ignoreCase = true)
                        }
                        if (match != null) {
                            jsonResp(req, 200, JSONObject.quote(match.optString("internal_auth_email", match.optString("email"))))
                        } else {
                            jsonResp(req, 200, "null")
                        }
                    }

                    path == "/auth/v1/token" && query.contains("grant_type=password") -> {
                        val payload = JSONObject(readBody(req))
                        val email = payload.optString("email").lowercase()
                        val pw = payload.optString("password")
                        val found = authUsers.values.find { it.email.equals(email, ignoreCase = true) && it.password == pw }
                        if (found == null) {
                            jsonResp(req, 400, """{"error":"invalid_grant","error_description":"Invalid login credentials"}""")
                        } else {
                            val token = if (found.id == adminUuid) validAdminJwt else "eyJhbGciOiJIUzI1NiJ9.student-${found.id}"
                            val res = JSONObject()
                                .put("access_token", token)
                                .put("refresh_token", if (found.id == adminUuid) adminRefreshToken else "rt_stu_${found.id}")
                                .put("expires_in", 3600)
                                .put("user", JSONObject().put("id", found.id).put("email", found.email))
                            jsonResp(req, 200, res.toString())
                        }
                    }

                    path == "/auth/v1/token" && query.contains("grant_type=refresh_token") -> {
                        refreshCallCount++
                        val rt = JSONObject(readBody(req)).optString("refresh_token")
                        if (rt == adminRefreshToken) {
                            validAdminJwt = refreshedAdminJwt
                            val res = JSONObject()
                                .put("access_token", refreshedAdminJwt)
                                .put("refresh_token", adminRefreshToken)
                                .put("expires_in", 3600)
                                .put("user", JSONObject().put("id", adminUuid).put("email", "azril.admin@kampus.ac.id"))
                            jsonResp(req, 200, res.toString())
                        } else {
                            jsonResp(req, 400, """{"error":"invalid_grant"}""")
                        }
                    }

                    path == "/auth/v1/signup" && method == "POST" -> {
                        val payload = JSONObject(readBody(req))
                        val email = payload.optString("email").lowercase()
                        val pw = payload.optString("password")
                        if (authUsers.values.any { it.email.equals(email, ignoreCase = true) }) {
                            jsonResp(req, 400, """{"msg":"User already registered"}""")
                        } else {
                            val uid = UUID.randomUUID().toString()
                            authUsers[uid] = AuthUser(uid, email, pw)
                            jsonResp(req, 200, JSONObject().put("user", JSONObject().put("id", uid).put("email", email)).toString())
                        }
                    }

                    path == "/rest/v1/study_programs" && method == "GET" -> {
                        if (auth != validAdminJwt && !auth.contains("student-")) {
                            jsonResp(req, 401, """{"message":"JWT expired"}""")
                        } else {
                            val arr = JSONArray()
                            studyPrograms.forEach { arr.put(it) }
                            jsonResp(req, 200, arr.toString())
                        }
                    }

                    path == "/rest/v1/classes" && method == "GET" -> {
                        if (auth != validAdminJwt && !auth.contains("student-")) {
                            jsonResp(req, 401, """{"message":"JWT expired"}""")
                        } else {
                            val arr = JSONArray()
                            classes.values.forEach { arr.put(it) }
                            jsonResp(req, 200, arr.toString())
                        }
                    }

                    path == "/rest/v1/classes" && method == "POST" -> {
                        if (auth != validAdminJwt) {
                            jsonResp(req, 401, """{"message":"JWT expired"}""")
                        } else {
                            val body = JSONObject(readBody(req))
                            classes[body.getString("id")] = body
                            jsonResp(req, 201, JSONArray().put(body).toString())
                        }
                    }

                    path == "/rest/v1/profiles" && method == "GET" -> {
                        if (simulate42P17Recursion) {
                            jsonResp(req, 500, """{"code":"42P17","message":"infinite recursion detected in policy for relation \"profiles\""}""")
                        } else if (auth != validAdminJwt && !auth.contains("student-")) {
                            jsonResp(req, 401, """{"message":"JWT expired"}""")
                        } else {
                            val decoded = URLDecoder.decode(query, "UTF-8")
                            val rows = profiles.values.filter { row ->
                                when {
                                    decoded.contains("id=eq.") -> {
                                        val targetId = decoded.substringAfter("id=eq.").substringBefore("&")
                                        row.optString("id") == targetId
                                    }
                                    decoded.contains("nim=ilike.") -> {
                                        val targetNim = decoded.substringAfter("nim=ilike.").substringBefore("&")
                                        row.optString("nim").equals(targetNim, ignoreCase = true)
                                    }
                                    decoded.contains("role=eq.student") -> row.optString("role") == "student"
                                    decoded.contains("role=neq.admin") -> !row.optString("role").equals("admin", ignoreCase = true)
                                    else -> true
                                }
                            }
                            val arr = JSONArray()
                            rows.forEach { row ->
                                val enriched = JSONObject(row.toString())
                                val prodi = studyPrograms.find { it.optString("id") == row.optString("study_program_id") }
                                if (prodi != null) enriched.put("study_programs", prodi)
                                val cls = classes[row.optString("class_id")]
                                if (cls != null) enriched.put("classes", JSONObject().put("id", cls.optString("id")).put("name", cls.optString("name")))
                                arr.put(enriched)
                            }
                            jsonResp(req, 200, arr.toString())
                        }
                    }

                    path == "/rest/v1/profiles" && method == "POST" -> {
                        if (auth != validAdminJwt) {
                            jsonResp(req, 401, """{"message":"JWT expired"}""")
                        } else {
                            val body = JSONObject(readBody(req))
                            profiles[body.getString("id")] = body
                            jsonResp(req, 201, JSONArray().put(body).toString())
                        }
                    }

                    else -> jsonResp(req, 404, """{"error":"Unhandled: $method $path?$query"}""")
                }
            }
            .build()
    }

    @Test
    fun adminSupabaseEmailLogin_survivesAppReloadWhenRpc404_andAutoRefreshesExpiredJwt() = runTest {
        val server = HardeningFakeSupabase()
        val client = server.buildClient()
        val store = TestKeyValueStore()

        val repo = AzrilAuthRepository(store, testSupabaseUrl, testAnonKey, client)

        // 1. Admin logs in using their Supabase Auth email (with rememberMe = true)
        val login = repo.loginAdmin("azril.admin@kampus.ac.id", "AdminSecret#2026", rememberMe = true)
        assertTrue("Admin email login must succeed via Supabase Auth", login is AuthResult.Success && login.usingSupabase)
        assertTrue(repo.hasLiveSupabaseSession())

        // 2. Simulate app restart (new repository instance over same DataStore) when verify_admin_session RPC is 404
        val repoAfterRestart = AzrilAuthRepository(store, testSupabaseUrl, testAnonKey, client)
        val restored = repoAfterRestart.restoreSession()
        assertNotNull("Admin session MUST survive reload even when verify_admin_session RPC returns 404", restored.profile)
        assertEquals(UserRole.ADMIN, restored.profile?.role)
        assertTrue(repoAfterRestart.hasLiveSupabaseSession())

        // 3. Simulate 1-hour JWT expiry on server side; repository MUST auto-refresh using refresh_token
        server.validAdminJwt = "expired-old-token-no-longer-accepted"
        val dirResult = repoAfterRestart.fetchStudentsAndClassesFromSupabase()
        assertTrue("Directory fetch must succeed after transparent refresh_token rotation", dirResult is RemoteDirectoryResult.Success)
        assertEquals("Refresh token endpoint should be called once", 1, server.refreshCallCount)
        assertEquals(server.refreshedAdminJwt, repoAfterRestart.getActiveSupabaseJwt())
    }

    @Test
    fun legacyUppercaseRoleStudent_visibleInAdminDashboard_and42P17GivesClearDiagnostic() = runTest {
        val server = HardeningFakeSupabase()
        val legacyStudentId = "55555555-5555-4555-8555-555555555555"
        server.authUsers[legacyStudentId] = HardeningFakeSupabase.AuthUser(
            id = legacyStudentId,
            email = "251601099@mahasiswa.azrilclas.ac.id",
            password = "251601099"
        )
        // Legacy profile row has uppercase role = "STUDENT" so `role=eq.student` returns []!
        server.profiles[legacyStudentId] = JSONObject()
            .put("id", legacyStudentId)
            .put("full_name", "Rizky Legacy Mahasiswa")
            .put("nim", "251601099")
            .put("username", "251601099")
            .put("email", "251601099@mahasiswa.azrilclas.ac.id")
            .put("internal_auth_email", "251601099@mahasiswa.azrilclas.ac.id")
            .put("study_program_id", server.prodiHesUuid)
            .put("role", "STUDENT")
            .put("account_status", "active")

        val client = server.buildClient()
        val adminRepo = AzrilAuthRepository(TestKeyValueStore(), testSupabaseUrl, testAnonKey, client)
        assertTrue(adminRepo.loginAdmin("azril.admin@kampus.ac.id", "AdminSecret#2026", rememberMe = true) is AuthResult.Success)

        // Admin must see the legacy student despite uppercase role="STUDENT"
        val dir = adminRepo.fetchStudentsAndClassesFromSupabase()
        assertTrue(dir is RemoteDirectoryResult.Success)
        val students = (dir as RemoteDirectoryResult.Success).directory.students
        assertEquals(1, students.size)
        assertEquals("251601099", students.first().nim)
        assertEquals("Rizky Legacy Mahasiswa", students.first().fullName)

        // Same legacy student logs in cleanly with NIM
        val studentRepo = AzrilAuthRepository(TestKeyValueStore(), testSupabaseUrl, testAnonKey, client)
        val stuLogin = studentRepo.login("251601099", "251601099", rememberMe = true)
        assertTrue(stuLogin is AuthResult.Success)
        assertEquals("Rizky Legacy Mahasiswa", (stuLogin as AuthResult.Success).profile.fullName)

        // Simulate legacy 001 RLS infinite recursion (42P17) -> must produce clear Indonesian diagnostic
        server.simulate42P17Recursion = true
        val errDir = adminRepo.fetchStudentsAndClassesFromSupabase()
        assertTrue(errDir is RemoteDirectoryResult.Error)
        val errMsg = (errDir as RemoteDirectoryResult.Error).message
        assertTrue("Error should identify 42P17 recursion: $errMsg", errMsg.contains("42P17"))
        assertTrue("Error should point to migration 009: $errMsg", errMsg.contains("009_admin_auth_and_legacy_student_visibility.sql"))
    }

    @Test
    fun streamingExcelParser_handlesRelativePathsAndEmbeddedMedia_rejectsInvalidFiles_andSyncsAcrossDevices() = runTest {
        // 1. Reject legacy .xls OLE2 magic bytes cleanly without crashing
        val fakeXlsBytes = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte())
        val xlsRes = ExcelStudentImportParser.parseXlsx("data_lama.xls", fakeXlsBytes, emptyList())
        assertTrue(xlsRes.isFailure)
        assertTrue(xlsRes.exceptionOrNull()?.message.orEmpty().contains(".xls", ignoreCase = true))

        // 2. Reject plain text / invalid non-ZIP file cleanly without crashing
        val textRes = ExcelStudentImportParser.parseXlsx("bukan_excel.xlsx", "hello world".toByteArray(), emptyList())
        assertTrue(textRes.isFailure)
        assertTrue(textRes.exceptionOrNull()?.message.orEmpty().contains(".xlsx", ignoreCase = true))

        // 3. Reject oversized file (> 15 MB) cleanly without crashing
        val oversized = ByteArray((ExcelStudentImportParser.MAX_XLSX_FILE_BYTES + 64).toInt())
        val sizeRes = ExcelStudentImportParser.parseXlsx("raksasa.xlsx", oversized, emptyList())
        assertTrue(sizeRes.isFailure)
        assertTrue(sizeRes.exceptionOrNull()?.message.orEmpty().contains("15 MB"))

        // 4. Build a valid multi-sheet .xlsx with RED AFK, BLACK skip, normal rows, weekly attendance,
        //    plus an extra xl/media/huge.png entry and relative `../worksheets/sheet1.xml` path
        val baseXlsx = ExcelStudentImportParser.buildStyledXlsx(
            sheets = listOf(
                ExcelStudentImportParser.XlsxSheetBuildSpec(
                    name = "PJKR-3B",
                    rows = listOf(
                        ExcelStudentImportParser.XlsxRowBuildSpec(listOf("No.", "NIM", "Nama", "Minggu ke-1", "Minggu ke-2")),
                        ExcelStudentImportParser.XlsxRowBuildSpec(listOf("1", "250701101", "Budi Santoso", "HADIR", "HADIR")),
                        ExcelStudentImportParser.XlsxRowBuildSpec(listOf("2", "250701102", "Siti Merah AFK", "ALPHA", "ALPHA"), fillArgb = "FFFF0000"),
                        ExcelStudentImportParser.XlsxRowBuildSpec(listOf("3", "250701103", "Dihapus Blok Hitam", "ALPHA", "ALPHA"), fillArgb = "FF000000")
                    )
                )
            )
        )
        // Inject an unrelated xl/media/logo.png and rewrite target to `./worksheets/sheet1.xml`
        val augmentedBaos = ByteArrayOutputStream()
        ZipOutputStream(augmentedBaos).use { zos ->
            ZipInputStream(baseXlsx.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val content = zis.readBytes()
                    zos.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == "xl/_rels/workbook.xml.rels") {
                        val modified = String(content, Charsets.UTF_8)
                            .replace("Target=\"worksheets/sheet1.xml\"", "Target=\"./worksheets/sheet1.xml\"")
                        zos.write(modified.toByteArray(Charsets.UTF_8))
                    } else {
                        zos.write(content)
                    }
                    zos.closeEntry()
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            // Extra media binary that selective unzip MUST skip
            zos.putNextEntry(ZipEntry("xl/media/logo.png"))
            zos.write(ByteArray(4096) { 0x7F })
            zos.closeEntry()
        }

        val parsed = ExcelStudentImportParser.parseXlsx("daftar_pjkr.xlsx", augmentedBaos.toByteArray(), emptyList())
        assertTrue("Streaming SAX parser must parse augmented xlsx cleanly: ${parsed.exceptionOrNull()}", parsed.isSuccess)
        val summary = parsed.getOrThrow()
        assertEquals(1, summary.validNormalCount)
        assertEquals(1, summary.validAfkCount)
        assertEquals(1, summary.skippedBlackCount)
        assertEquals(2, summary.totalImportableCount)

        // 5. Commit Excel import via Admin Phone 1 -> verify persisted to Supabase -> visible on Admin Phone 2!
        val server = HardeningFakeSupabase()
        val client = server.buildClient()
        val adminPhone1 = AzrilAuthRepository(TestKeyValueStore(), testSupabaseUrl, testAnonKey, client)
        assertTrue(adminPhone1.loginAdmin("azril.admin@kampus.ac.id", "AdminSecret#2026", rememberMe = true) is AuthResult.Success)

        val targetClass = AcademicClass(
            id = "cls-pjkr-3b",
            name = "PJKR-3B",
            studyProgramId = server.prodiPjkrUuid,
            studyProgramName = "PJKR",
            semester = 3,
            academicYear = "2026/2027",
            cohortYear = 2025,
            description = "Kelas PJKR 3B"
        )
        val importableRows = summary.rows.filter { it.isImportable }
        val newStudents = importableRows.map { row ->
            StudentRecord(
                id = UUID.randomUUID().toString(),
                fullName = row.fullName,
                nim = row.nim,
                email = "${row.nim}@mahasiswa.azrilclas.ac.id",
                studyProgramId = server.prodiPjkrUuid,
                studyProgramName = "PJKR",
                classId = targetClass.id,
                className = targetClass.name,
                semester = 3,
                cohortYear = 2025,
                status = AccountStatus.ACTIVE,
                badgeStatus = if (row.validationStatus == ExcelImportValidationStatus.VALID_AFK) StudentBadgeStatus.AFK else StudentBadgeStatus.NEUTRAL,
                mustChangePassword = true
            )
        }

        val report = adminPhone1.adminBulkImportStudentsToSupabase(
            newStudents = newStudents,
            classes = listOf(targetClass),
            fileName = "daftar_pjkr.xlsx",
            semesterKey = "2026/2027-Ganjil",
            importHistoricalAttendance = true,
            previewRowsForSync = importableRows
        )
        assertEquals(2, report.syncedToSupabaseCount)
        assertEquals(0, report.alreadyInSupabaseCount)

        // Admin Phone 2 (fresh device) logs in and fetches from Supabase -> MUST see both imported students (and NOT black row)
        val adminPhone2 = AzrilAuthRepository(TestKeyValueStore(), testSupabaseUrl, testAnonKey, client)
        assertTrue(adminPhone2.loginAdmin("azril.admin@kampus.ac.id", "AdminSecret#2026", rememberMe = true) is AuthResult.Success)
        val dirPhone2 = adminPhone2.fetchStudentsAndClassesFromSupabase()
        assertTrue(dirPhone2 is RemoteDirectoryResult.Success)
        val phone2Students = (dirPhone2 as RemoteDirectoryResult.Success).directory.students
        assertEquals(2, phone2Students.size)
        assertTrue(phone2Students.any { it.nim == "250701101" && it.badgeStatus == StudentBadgeStatus.NEUTRAL })
        assertTrue(phone2Students.any { it.nim == "250701102" && it.badgeStatus == StudentBadgeStatus.AFK })
        assertFalse(phone2Students.any { it.nim == "250701103" })
    }

    @Test
    fun diskStreamingExcelImport_multiSheetLargeRoster_tracksProgress_deduplicatesNim_preservesAfkAndBlackSkip_andSyncsToSupabase() = runTest {
        // Build 3 sheets (PJKR-3B, FARMASI-3, HES-3B) with 156 total rows:
        // Per sheet: 45 Normal + 4 RED AFK + 2 BLACK skip + 1 cross-sheet duplicate = 52 rows/sheet
        fun buildSheetRows(prefix: String, sheetIdx: Int): List<ExcelStudentImportParser.XlsxRowBuildSpec> {
            val rows = mutableListOf<ExcelStudentImportParser.XlsxRowBuildSpec>()
            rows.add(ExcelStudentImportParser.XlsxRowBuildSpec(listOf("No.", "NIM", "Nama", "Minggu ke-1", "Minggu ke-2", "Minggu ke-3")))
            var no = 1
            for (i in 1..45) {
                val nim = "${prefix}${String.format("%03d", i)}"
                rows.add(
                    ExcelStudentImportParser.XlsxRowBuildSpec(
                        values = listOf(no++.toString(), nim, "Mahasiswa Normal $prefix $i", "HADIR", "HADIR", "IZIN")
                    )
                )
            }
            for (i in 1..4) {
                val nim = "${prefix}90$i"
                rows.add(
                    ExcelStudentImportParser.XlsxRowBuildSpec(
                        values = listOf(no++.toString(), nim, "Mahasiswa AFK $prefix $i", "ALPHA", "ALPHA", "ALPHA"),
                        fillArgb = "FFFF0000"
                    )
                )
            }
            for (i in 1..2) {
                val nim = "${prefix}80$i"
                rows.add(
                    ExcelStudentImportParser.XlsxRowBuildSpec(
                        values = listOf(no++.toString(), nim, "Dihapus Hitam $prefix $i", "ALPHA", "ALPHA", "ALPHA"),
                        fillArgb = "FF000000"
                    )
                )
            }
            // Cross-sheet duplicate NIM on sheet 0 and sheet 1 (must be flagged as INVALID_OR_DUPLICATE)
            if (sheetIdx == 0 || sheetIdx == 1) {
                rows.add(
                    ExcelStudentImportParser.XlsxRowBuildSpec(
                        values = listOf(no.toString(), "259999999", "Duplikat Lintas Sheet $sheetIdx", "HADIR", "HADIR", "HADIR")
                    )
                )
            }
            return rows
        }

        val multiSheetBytes = ExcelStudentImportParser.buildStyledXlsx(
            sheets = listOf(
                ExcelStudentImportParser.XlsxSheetBuildSpec("PJKR-3B", buildSheetRows("2510", 0)),
                ExcelStudentImportParser.XlsxSheetBuildSpec("FARMASI-3", buildSheetRows("2520", 1)),
                ExcelStudentImportParser.XlsxSheetBuildSpec("HES-3B", buildSheetRows("2530", 2))
            )
        )

        val tempFile = File.createTempFile("azril_multisheet_150_", ".xlsx")
        try {
            tempFile.writeBytes(multiSheetBytes)
            val progressSteps = mutableListOf<Float>()
            val parseRes = ExcelStudentImportParser.parseXlsxFile(
                fileName = "rekap_3_prodi_150_baris.xlsx",
                file = tempFile,
                existingStudents = emptyList(),
                onProgress = { progress, _ -> progressSteps.add(progress) }
            )
            assertTrue("Disk-streaming parseXlsxFile must succeed: ${parseRes.exceptionOrNull()}", parseRes.isSuccess)
            val summary = parseRes.getOrThrow()

            // Verify progress reporting moved monotonically to 1.0f
            assertTrue("Progress callbacks must be emitted", progressSteps.size >= 5)
            assertEquals(1.0f, progressSteps.last(), 0.001f)

            // Verify sheet mappings & counts across all 3 prodis
            assertEquals(3, summary.sheetMappings.size)
            assertEquals(135, summary.validNormalCount) // 45 * 3
            assertEquals(12, summary.validAfkCount)     // 4 * 3
            assertEquals(6, summary.skippedBlackCount)  // 2 * 3
            assertEquals(2, summary.invalidOrDuplicateCount) // cross-sheet duplicate blocked on both rows
            assertEquals(147, summary.totalImportableCount)
            assertTrue(summary.hasWeeklyAttendance)

            // Persist a representative multi-prodi subset to Supabase with progress callback & verify Student leaderboard sync
            val server = HardeningFakeSupabase()
            val client = server.buildClient()
            val adminRepo = AzrilAuthRepository(TestKeyValueStore(), testSupabaseUrl, testAnonKey, client)
            val connectRes = adminRepo.connectSupabaseAdminSession("azril.admin@kampus.ac.id", "AdminSecret#2026")
            assertTrue("In-place Admin Supabase connect must succeed", connectRes is AuthResult.Success)

            val sampleRows = listOf(
                summary.rows.first { it.sheetName == "PJKR-3B" && it.validationStatus == ExcelImportValidationStatus.VALID_NORMAL },
                summary.rows.first { it.sheetName == "FARMASI-3" && it.validationStatus == ExcelImportValidationStatus.VALID_AFK },
                summary.rows.first { it.sheetName == "HES-3B" && it.validationStatus == ExcelImportValidationStatus.VALID_NORMAL }
            )
            val prodiIds = mapOf("PJKR" to server.prodiPjkrUuid, "FARMASI" to server.prodiFarmasiUuid, "HES" to server.prodiHesUuid)
            val studentsToSync = sampleRows.map { row ->
                StudentRecord(
                    id = UUID.randomUUID().toString(),
                    fullName = row.fullName,
                    nim = row.nim,
                    email = "${row.nim}@mahasiswa.azrilclas.ac.id",
                    studyProgramId = prodiIds[row.studyProgramCode] ?: server.prodiHesUuid,
                    studyProgramName = row.studyProgramCode,
                    classId = "cls-${row.className.lowercase()}",
                    className = row.className,
                    semester = 3,
                    cohortYear = 2025,
                    status = AccountStatus.ACTIVE,
                    badgeStatus = if (row.validationStatus == ExcelImportValidationStatus.VALID_AFK) StudentBadgeStatus.AFK else StudentBadgeStatus.NEUTRAL,
                    mustChangePassword = true
                )
            }
            val commitProgressUpdates = mutableListOf<Int>()
            val report1 = adminRepo.adminBulkImportStudentsToSupabase(
                newStudents = studentsToSync,
                classes = emptyList(),
                fileName = "rekap_3_prodi_150_baris.xlsx",
                semesterKey = "2026/2027-Ganjil",
                importHistoricalAttendance = true,
                previewRowsForSync = sampleRows,
                onProgress = { processed, _, _ -> commitProgressUpdates.add(processed) }
            )
            assertEquals(3, report1.syncedToSupabaseCount)
            assertTrue("Commit progress callback must report progress", commitProgressUpdates.isNotEmpty())

            // Re-importing same students MUST NOT create duplicates
            val report2 = adminRepo.adminBulkImportStudentsToSupabase(
                newStudents = studentsToSync,
                classes = emptyList(),
                fileName = "rekap_3_prodi_150_baris.xlsx",
                semesterKey = "2026/2027-Ganjil",
                importHistoricalAttendance = false,
                previewRowsForSync = sampleRows
            )
            assertEquals(0, report2.syncedToSupabaseCount)
            assertEquals(3, report2.alreadyInSupabaseCount)

            // Student logs in and fetches unified leaderboard -> MUST see the exact same 3 students + XP from Supabase!
            val studentRepo = AzrilAuthRepository(TestKeyValueStore(), testSupabaseUrl, testAnonKey, client)
            val firstStudentNim = studentsToSync.first().nim
            val stuLogin = studentRepo.login(firstStudentNim, firstStudentNim, rememberMe = true)
            assertTrue("Imported student must log in cleanly via NIM", stuLogin is AuthResult.Success)
            val stuProfile = (stuLogin as AuthResult.Success).profile
            val leaderboardDir = studentRepo.fetchStudentLeaderboardAndXpFromSupabase(stuProfile.id)
            assertTrue("Student leaderboard sync must succeed", leaderboardDir is RemoteDirectoryResult.Success)
            val syncedLeaderboard = (leaderboardDir as RemoteDirectoryResult.Success).directory
            assertEquals("Student Ranking & Admin must see the exact same 3 students", 3, syncedLeaderboard.students.size)
            assertEquals("Unified RPC must preserve XP balances", 3, syncedLeaderboard.xpTransactions.size)
            assertTrue(syncedLeaderboard.xpTransactions.all { it.points == 250 })
        } finally {
            tempFile.delete()
        }
    }
}
