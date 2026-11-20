package com.example

import com.example.data.auth.AuthResult
import com.example.data.auth.AzrilAuthRepository
import com.example.data.auth.RemoteDirectoryResult
import com.example.ui.model.AcademicClass
import com.example.ui.model.AccountStatus
import com.example.ui.model.StudentBadgeStatus
import com.example.ui.model.UserRole
import java.net.URLDecoder
import java.util.UUID
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end verification of:
 * 1. Multi-device Admin CRUD persistence (Phone 1 -> Supabase -> Phone 2) including `rememberMe = false`.
 * 2. Real INSERT / SELECT / UPDATE / DELETE on Supabase `classes` and `profiles`.
 * 3. Persistence across simulated app restart.
 * 4. Strict failure handling (RLS 403 -> returns error, never creates ghost local-only data).
 * 5. Cross-device Student login by NIM (including hidden whitespace/zero-width chars) from Phone 3.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MultiDeviceStudentCrudSyncTest {

    private val testSupabaseUrl = "https://dtavbwdnopbizlvokphd.supabase.co"
    private val testAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test-anon-key"

    private class FakeSupabaseServer {
        val prodiHesUuid = "11111111-1111-4111-8111-111111111111"
        val prodiPjkrUuid = "22222222-2222-4222-8222-222222222222"
        val prodiFarmasiUuid = "33333333-3333-4333-8333-333333333333"
        val adminUuid = "99999999-9999-4999-8999-999999999999"
        val adminJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.admin-live-jwt"

        var simulateRlsForbidden: Boolean = false
        var insertCount: Int = 0
        var updateCount: Int = 0
        var deleteCount: Int = 0

        data class FakeAuthUser(
            val id: String,
            val email: String,
            var password: String,
            val role: String
        )

        val authUsers = mutableMapOf<String, FakeAuthUser>(
            adminUuid to FakeAuthUser(adminUuid, "admin@azrilclas.ac.id", "Azril23admin7", "admin")
        )

        val studyPrograms = mutableListOf(
            JSONObject().apply {
                put("id", prodiHesUuid)
                put("code", "HES")
                put("name", "Hukum Ekonomi Syariah")
                put("faculty", "Fakultas Syariah")
                put("description", "Prodi HES")
                put("is_active", true)
            },
            JSONObject().apply {
                put("id", prodiPjkrUuid)
                put("code", "PJKR")
                put("name", "Pendidikan Jasmani Kesehatan dan Rekreasi")
                put("faculty", "FKIP")
                put("description", "Prodi PJKR")
                put("is_active", true)
            },
            JSONObject().apply {
                put("id", prodiFarmasiUuid)
                put("code", "FARMASI")
                put("name", "Farmasi")
                put("faculty", "Fakultas Ilmu Kesehatan")
                put("description", "Prodi FARMASI")
                put("is_active", true)
            }
        )

        val classes = mutableMapOf<String, JSONObject>()

        val profiles = mutableMapOf<String, JSONObject>(
            adminUuid to JSONObject().apply {
                put("id", adminUuid)
                put("full_name", "Administrator Utama Azril Clas")
                put("email", "admin@azrilclas.ac.id")
                put("username", "Azril12v")
                put("role", "admin")
                put("account_status", "active")
                put("must_change_password", false)
            }
        )

        private fun readBody(request: Request): String {
            val body = request.body ?: return ""
            val buf = Buffer()
            body.writeTo(buf)
            return buf.readUtf8()
        }

        private fun jsonResponse(request: Request, code: Int, payload: String): Response =
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code in 200..299) "OK" else "Error")
                .body(payload.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()

        private fun enrichProfileWithJoins(profile: JSONObject): JSONObject {
            val copy = JSONObject(profile.toString())
            val prodiId = copy.optString("study_program_id")
            val prodi = studyPrograms.find { it.optString("id") == prodiId }
            if (prodi != null) {
                copy.put("study_programs", JSONObject().apply {
                    put("id", prodi.optString("id"))
                    put("code", prodi.optString("code"))
                    put("name", prodi.optString("name"))
                })
            }
            val clsId = copy.optString("class_id")
            val cls = classes[clsId]
            if (cls != null) {
                copy.put("classes", JSONObject().apply {
                    put("id", cls.optString("id"))
                    put("name", cls.optString("name"))
                })
            }
            return copy
        }

        fun buildClient(): OkHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val path = req.url.encodedPath
                val query = req.url.encodedQuery.orEmpty()
                val method = req.method
                val authHeader = req.header("Authorization").orEmpty()
                val bodyStr = readBody(req)

                when {
                    // Edge functions not deployed in fallback test -> HTTP 404
                    path.startsWith("/functions/v1/") ->
                        jsonResponse(req, 404, """{"message":"Function not found"}""")

                    // RPC: resolve_admin_login_email
                    path == "/rest/v1/rpc/resolve_admin_login_email" -> {
                        val json = JSONObject(bodyStr)
                        val ident = json.optString("p_identifier").trim()
                        if (ident.equals("Azril12v", ignoreCase = true)) {
                            jsonResponse(req, 200, "\"admin@azrilclas.ac.id\"")
                        } else {
                            jsonResponse(req, 200, "null")
                        }
                    }

                    // RPC: verify_admin_session
                    path == "/rest/v1/rpc/verify_admin_session" -> {
                        if (authHeader == "Bearer $adminJwt") {
                            jsonResponse(req, 200, "true")
                        } else {
                            jsonResponse(req, 200, "false")
                        }
                    }

                    // RPC: resolve_login_email (strips hidden whitespace just like migration 008)
                    path == "/rest/v1/rpc/resolve_login_email" -> {
                        val rawIdent = JSONObject(bodyStr).optString("p_identifier")
                        val cleanIdent = rawIdent.replace(Regex("[\\u00A0\\u200B-\\u200D\\uFEFF]"), "").trim()
                        val matched = profiles.values.find { p ->
                            p.optString("role") == "student" &&
                                p.optString("account_status", "active") == "active" && (
                                p.optString("nim").equals(cleanIdent, ignoreCase = true) ||
                                    p.optString("username").equals(cleanIdent, ignoreCase = true) ||
                                    p.optString("email").equals(cleanIdent, ignoreCase = true)
                                )
                        }
                        if (matched == null) {
                            jsonResponse(req, 200, "null")
                        } else {
                            val email = matched.optString("internal_auth_email")
                                .ifBlank { matched.optString("email") }
                            jsonResponse(req, 200, JSONObject.quote(email))
                        }
                    }

                    // GoTrue Password Login
                    path == "/auth/v1/token" && query.contains("grant_type=password") -> {
                        val json = JSONObject(bodyStr)
                        val email = json.optString("email").trim().lowercase()
                        val password = json.optString("password")
                        val user = authUsers.values.find {
                            it.email.equals(email, ignoreCase = true) && it.password == password
                        }
                        if (user == null) {
                            jsonResponse(req, 400, """{"error_description":"Invalid login credentials"}""")
                        } else {
                            val token = if (user.role == "admin") adminJwt else "eyJhbGciOiJIUzI1NiJ9.student-${user.id}"
                            jsonResponse(req, 200, JSONObject().apply {
                                put("access_token", token)
                                put("user", JSONObject().apply {
                                    put("id", user.id)
                                    put("email", user.email)
                                })
                            }.toString())
                        }
                    }

                    // GoTrue SignUp (student account provisioning)
                    path == "/auth/v1/signup" -> {
                        if (simulateRlsForbidden) {
                            return@addInterceptor jsonResponse(req, 403, """{"msg":"Signup forbidden"}""")
                        }
                        val json = JSONObject(bodyStr)
                        val email = json.optString("email").trim().lowercase()
                        val password = json.optString("password")
                        val data = json.optJSONObject("data") ?: JSONObject()
                        val newUuid = UUID.randomUUID().toString()
                        authUsers[newUuid] = FakeAuthUser(newUuid, email, password, "student")
                        // Simulate DB trigger creating minimal profile row
                        profiles[newUuid] = JSONObject().apply {
                            put("id", newUuid)
                            put("full_name", data.optString("full_name", "Mahasiswa"))
                            put("nim", data.optString("nim", ""))
                            put("username", data.optString("username", data.optString("nim", "")))
                            put("email", email)
                            put("internal_auth_email", email)
                            put("role", "student")
                            put("study_program_id", data.optString("study_program_id", ""))
                            put("class_id", data.optString("class_id", ""))
                            put("semester", data.optInt("semester", 1))
                            put("cohort_year", data.optInt("cohort_year", 2026))
                            put("account_status", "active")
                            put("student_badge_status", data.optString("student_badge_status", "neutral"))
                            put("must_change_password", data.optBoolean("must_change_password", true))
                        }
                        jsonResponse(req, 200, JSONObject().apply {
                            put("id", newUuid)
                            put("email", email)
                            put("user", JSONObject().apply {
                                put("id", newUuid)
                                put("email", email)
                            })
                        }.toString())
                    }

                    // PostgREST: study_programs
                    path == "/rest/v1/study_programs" && method == "GET" -> {
                        val arr = JSONArray()
                        studyPrograms.forEach { arr.put(it) }
                        jsonResponse(req, 200, arr.toString())
                    }

                    // PostgREST: classes
                    path == "/rest/v1/classes" && method == "GET" -> {
                        val arr = JSONArray()
                        classes.values.forEach { arr.put(it) }
                        jsonResponse(req, 200, arr.toString())
                    }
                    path == "/rest/v1/classes" && method == "POST" -> {
                        if (authHeader != "Bearer $adminJwt" || simulateRlsForbidden) {
                            return@addInterceptor jsonResponse(req, 403, """{"message":"new row violates row-level security policy for table classes"}""")
                        }
                        val obj = JSONObject(bodyStr)
                        val id = obj.optString("id").ifBlank { UUID.randomUUID().toString() }
                        obj.put("id", id)
                        classes[id] = obj
                        jsonResponse(req, 201, JSONArray().put(obj).toString())
                    }

                    // PostgREST: profiles SELECT
                    path == "/rest/v1/profiles" && method == "GET" -> {
                        val result = JSONArray()
                        val idMatch = Regex("""id=eq\.([^&]+)""").find(query)?.groupValues?.get(1)
                        val nimMatch = Regex("""nim=ilike\.([^&]+)""").find(query)?.groupValues?.get(1)
                            ?.let { URLDecoder.decode(it, "UTF-8") }
                        val neqId = Regex("""id=neq\.([^&]+)""").find(query)?.groupValues?.get(1)
                        val onlyStudents = query.contains("role=eq.student")

                        profiles.values.forEach { p ->
                            val matchesId = idMatch == null || p.optString("id") == idMatch
                            val matchesNeq = neqId == null || p.optString("id") != neqId
                            val matchesNim = nimMatch == null || p.optString("nim").equals(nimMatch, ignoreCase = true)
                            val matchesRole = !onlyStudents || p.optString("role") == "student"
                            if (matchesId && matchesNeq && matchesNim && matchesRole) {
                                result.put(enrichProfileWithJoins(p))
                            }
                        }
                        jsonResponse(req, 200, result.toString())
                    }

                    // PostgREST: profiles UPSERT/INSERT
                    path == "/rest/v1/profiles" && method == "POST" -> {
                        if (authHeader != "Bearer $adminJwt" || simulateRlsForbidden) {
                            return@addInterceptor jsonResponse(req, 403, """{"message":"new row violates row-level security policy for table profiles"}""")
                        }
                        val obj = JSONObject(bodyStr)
                        val id = obj.optString("id")
                        // Reject non-UUID study_program_id just like real PostgreSQL!
                        val prodiId = obj.optString("study_program_id")
                        if (!prodiId.matches(Regex("^[0-9a-fA-F-]{36}$"))) {
                            return@addInterceptor jsonResponse(req, 400, """{"message":"invalid input syntax for type uuid: \"$prodiId\""}""")
                        }
                        val merged = profiles[id]?.let { JSONObject(it.toString()) } ?: JSONObject()
                        obj.keys().forEach { key ->
                            if (obj.isNull(key)) merged.remove(key) else merged.put(key, obj.get(key))
                        }
                        profiles[id] = merged
                        insertCount++
                        jsonResponse(req, 201, JSONArray().put(enrichProfileWithJoins(merged)).toString())
                    }

                    // PostgREST: profiles UPDATE (PATCH)
                    path == "/rest/v1/profiles" && method == "PATCH" -> {
                        if (authHeader != "Bearer $adminJwt" || simulateRlsForbidden) {
                            return@addInterceptor jsonResponse(req, 403, """{"message":"permission denied by RLS on profiles"}""")
                        }
                        val idMatch = Regex("""id=eq\.([^&]+)""").find(query)?.groupValues?.get(1)
                        val target = idMatch?.let { profiles[it] }
                        if (target == null) {
                            return@addInterceptor jsonResponse(req, 200, "[]")
                        }
                        val patch = JSONObject(bodyStr)
                        if (patch.has("study_program_id") && !patch.isNull("study_program_id")) {
                            val prodiId = patch.optString("study_program_id")
                            if (!prodiId.matches(Regex("^[0-9a-fA-F-]{36}$"))) {
                                return@addInterceptor jsonResponse(req, 400, """{"message":"invalid input syntax for type uuid: \"$prodiId\""}""")
                            }
                        }
                        patch.keys().forEach { key ->
                            if (patch.isNull(key)) target.remove(key) else target.put(key, patch.get(key))
                        }
                        updateCount++
                        jsonResponse(req, 200, JSONArray().put(enrichProfileWithJoins(target)).toString())
                    }

                    // PostgREST: profiles DELETE
                    path == "/rest/v1/profiles" && method == "DELETE" -> {
                        if (authHeader != "Bearer $adminJwt" || simulateRlsForbidden) {
                            return@addInterceptor jsonResponse(req, 403, """{"message":"permission denied by RLS on profiles"}""")
                        }
                        val idMatch = Regex("""id=eq\.([^&]+)""").find(query)?.groupValues?.get(1)
                        val removed = idMatch?.let { profiles.remove(it) }
                        if (removed == null) {
                            jsonResponse(req, 200, "[]")
                        } else {
                            deleteCount++
                            jsonResponse(req, 200, JSONArray().put(removed).toString())
                        }
                    }

                    else -> jsonResponse(req, 404, """{"message":"Unhandled mock endpoint: $method $path?$query"}""")
                }
            }
            .build()
    }

    @Test
    fun adminPhone1AddsEditsDeletesStudent_syncedAcrossPhone2ReloadAndPhone3StudentLogin() = runTest {
        val server = FakeSupabaseServer()
        val client = server.buildClient()

        // === PHONE 1: Admin logs in with rememberMe = false (previously wiped JWT!) ===
        val phone1Store = InMemoryAuthStore()
        val phone1Repo = AzrilAuthRepository(phone1Store, testSupabaseUrl, testAnonKey, client)

        val adminLoginPhone1 = phone1Repo.loginAdmin("Azril12v", "Azril23admin7", rememberMe = false)
        assertTrue("Admin login on Phone 1 must succeed", adminLoginPhone1 is AuthResult.Success)
        assertTrue("Phone 1 must keep live Supabase JWT in memory even when rememberMe=false", phone1Repo.hasLiveSupabaseSession())

        val hesClass = AcademicClass(
            id = "cls-hes-3b",
            name = "HES-3B",
            studyProgramId = "prodi-hes", // local placeholder ID -> service must resolve to real UUID!
            studyProgramName = "HES",
            semester = 3,
            academicYear = "2026/2027",
            cohortYear = 2025,
            description = "Kelas HES-3B"
        )
        assertNull("Class upsert to Supabase must succeed", phone1Repo.adminSaveClassToSupabase(hesClass, "HES"))

        // Phone 1 Admin adds a student -> must INSERT into Supabase Auth + public.profiles
        val createRes = phone1Repo.adminCreateStudentAccount(
            fullName = "Nadia Rahmawati",
            nim = "251601099",
            email = "251601099@mahasiswa.azrilclas.ac.id",
            studyProgramId = "prodi-hes", // verifies UUID resolution from code "HES"
            studyProgramCode = "HES",
            classId = hesClass.id,
            className = hesClass.name,
            semester = 3,
            cohortYear = 2025,
            accountStatus = AccountStatus.ACTIVE,
            badgeStatus = StudentBadgeStatus.NEUTRAL,
            initialPassword = "251601099",
            targetClass = hesClass
        )
        assertTrue("Add student on Phone 1 must succeed in Supabase", createRes is AuthResult.Success)
        val createdProfile = (createRes as AuthResult.Success).profile
        assertEquals("INSERT must execute on Supabase profiles", 1, server.insertCount)
        assertEquals("Study program ID must resolve to real PostgreSQL UUID", server.prodiHesUuid, createdProfile.studyProgramId)

        // === PHONE 2: Completely fresh device (empty local storage) logs in as Admin ===
        val phone2Store = InMemoryAuthStore()
        val phone2Repo = AzrilAuthRepository(phone2Store, testSupabaseUrl, testAnonKey, client)

        val adminLoginPhone2 = phone2Repo.loginAdmin("Azril12v", "Azril23admin7", rememberMe = true)
        assertTrue("Admin login on Phone 2 must succeed", adminLoginPhone2 is AuthResult.Success)

        val phone2Directory = phone2Repo.fetchStudentsAndClassesFromSupabase()
        assertTrue("Phone 2 fetch from Supabase must succeed", phone2Directory is RemoteDirectoryResult.Success)
        val dir2 = (phone2Directory as RemoteDirectoryResult.Success).directory
        assertEquals("Phone 2 must see the student added on Phone 1", 1, dir2.students.size)
        val syncedStudent = dir2.students.first()
        assertEquals(createdProfile.id, syncedStudent.id)
        assertEquals("Nadia Rahmawati", syncedStudent.fullName)
        assertEquals("251601099", syncedStudent.nim)
        assertEquals("HES", syncedStudent.studyProgramName)
        assertEquals("HES-3B", syncedStudent.className)
        assertTrue("Class memberIds must include student", dir2.classes.any { it.id == "cls-hes-3b" && it.memberIds.contains(createdProfile.id) })

        // === PHONE 1: Admin edits student -> must execute PATCH on Supabase ===
        val updateRes = phone1Repo.adminUpdateStudentAccount(
            studentId = createdProfile.id,
            fullName = "Nadia Rahmawati Putri",
            nim = "251601099",
            email = "nadia.putri@mahasiswa.azrilclas.ac.id",
            studyProgramId = "prodi-hes",
            studyProgramCode = "HES",
            classId = hesClass.id,
            className = hesClass.name,
            semester = 4,
            cohortYear = 2025,
            accountStatus = AccountStatus.ACTIVE,
            badgeStatus = StudentBadgeStatus.LULUS,
            previousNim = "251601099",
            targetClass = hesClass
        )
        assertTrue("Update student must succeed in Supabase", updateRes is AuthResult.Success)
        assertEquals("UPDATE (PATCH) must execute on Supabase profiles", 1, server.updateCount)

        // === SIMULATED APP RELOAD on Phone 2 (new repository instance using persisted store) ===
        val phone2ReloadedRepo = AzrilAuthRepository(phone2Store, testSupabaseUrl, testAnonKey, client)
        val restored = phone2ReloadedRepo.restoreSession()
        assertNotNull("Admin session must survive app reload", restored.profile)
        assertEquals(UserRole.ADMIN, restored.profile?.role)

        val afterEditDir = phone2ReloadedRepo.fetchStudentsAndClassesFromSupabase() as RemoteDirectoryResult.Success
        val editedStudent = afterEditDir.directory.students.first()
        assertEquals("Nadia Rahmawati Putri", editedStudent.fullName)
        assertEquals("nadia.putri@mahasiswa.azrilclas.ac.id", editedStudent.email)
        assertEquals(4, editedStudent.semester)
        assertEquals(StudentBadgeStatus.LULUS, editedStudent.badgeStatus)

        // === PHONE 3: Student logs in from brand-new phone using NIM with hidden spaces ===
        val phone3Store = InMemoryAuthStore()
        val phone3Repo = AzrilAuthRepository(phone3Store, testSupabaseUrl, testAnonKey, client)
        val studentLogin = phone3Repo.login(" 251601099\u200B ", "251601099", rememberMe = true)
        assertTrue("Student must be able to login on Phone 3 with NIM", studentLogin is AuthResult.Success)
        val loggedStudent = (studentLogin as AuthResult.Success).profile
        assertEquals(UserRole.STUDENT, loggedStudent.role)
        assertEquals("Nadia Rahmawati Putri", loggedStudent.fullName)
        assertEquals("251601099", loggedStudent.nim)
        assertEquals("HES-3B", loggedStudent.className)

        // === PHONE 2: Admin deletes student -> must execute DELETE on Supabase & sync to Phone 1 ===
        val deleteErr = phone2ReloadedRepo.adminDeleteStudentAccount(createdProfile.id, "251601099")
        assertNull("Delete student must succeed in Supabase", deleteErr)
        assertEquals("DELETE must execute on Supabase profiles", 1, server.deleteCount)

        val phone1AfterDelete = phone1Repo.fetchStudentsAndClassesFromSupabase() as RemoteDirectoryResult.Success
        assertTrue("Phone 1 must see empty student list after Phone 2 deleted student", phone1AfterDelete.directory.students.isEmpty())
    }

    @Test
    fun supabaseRlsOrNetworkFailure_rejectsOperationAndLeavesZeroGhostLocalData() = runTest {
        val server = FakeSupabaseServer()
        val client = server.buildClient()
        val store = InMemoryAuthStore()
        val repo = AzrilAuthRepository(store, testSupabaseUrl, testAnonKey, client)

        val login = repo.loginAdmin("Azril12v", "Azril23admin7", rememberMe = false)
        assertTrue(login is AuthResult.Success)

        // Simulate RLS 403 rejection on Supabase
        server.simulateRlsForbidden = true

        val createFailed = repo.adminCreateStudentAccount(
            fullName = "Mahasiswa Gagal Simpan",
            nim = "251609999",
            email = "251609999@mahasiswa.azrilclas.ac.id",
            studyProgramId = "prodi-hes",
            studyProgramCode = "HES",
            classId = null,
            className = null,
            semester = 1,
            cohortYear = 2026,
            accountStatus = AccountStatus.ACTIVE,
            badgeStatus = StudentBadgeStatus.NEUTRAL,
            initialPassword = "251609999"
        )
        assertTrue("Failed Supabase INSERT must return AuthResult.Failure", createFailed is AuthResult.Failure)

        // Verify NO ghost student was saved to local storage
        val restored = repo.restoreSession()
        assertFalse(
            "Local storage must NOT contain ghost student after failed Supabase INSERT",
            restored.registeredStudents.any { it.nim == "251609999" }
        )
    }
}
