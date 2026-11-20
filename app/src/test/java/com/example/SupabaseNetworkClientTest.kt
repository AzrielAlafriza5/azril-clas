package com.example

import com.example.data.auth.AuthKeyValueStore
import com.example.data.auth.AuthResult
import com.example.data.auth.AzrilAuthRepository
import com.example.data.network.ResilientSupabaseDns
import com.example.data.network.SupabaseNetworkClient
import kotlinx.coroutines.test.runTest
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
class SupabaseNetworkClientTest {

    @Test
    fun normalizeBaseUrl_stripsRestAndAuthSuffixesAndTrailingSlashes() {
        assertEquals(
            "https://dtavbwdnopbizlvokphd.supabase.co",
            SupabaseNetworkClient.normalizeBaseUrl("https://dtavbwdnopbizlvokphd.supabase.co/rest/v1/")
        )
        assertEquals(
            "https://dtavbwdnopbizlvokphd.supabase.co",
            SupabaseNetworkClient.normalizeBaseUrl("https://dtavbwdnopbizlvokphd.supabase.co/rest/v1")
        )
        assertEquals(
            "https://dtavbwdnopbizlvokphd.supabase.co",
            SupabaseNetworkClient.normalizeBaseUrl("https://dtavbwdnopbizlvokphd.supabase.co/auth/v1/")
        )
        assertEquals(
            "https://dtavbwdnopbizlvokphd.supabase.co",
            SupabaseNetworkClient.normalizeBaseUrl("  https://dtavbwdnopbizlvokphd.supabase.co/  ")
        )
    }

    @Test
    fun resilientDns_fallsBackToDohWhenSystemDnsFailsAndCachesResult() {
        val failingSystemDns = object : Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> {
                throw UnknownHostException("Unable to resolve host \"$hostname\": No address associated with hostname")
            }
        }

        var dohCallCount = 0
        val mockDohClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                dohCallCount++
                val url = chain.request().url.toString()
                if (url.startsWith("https://1.1.1.1/")) {
                    // Simulate first DoH endpoint unavailable -> must fall back to 8.8.8.8
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(503)
                        .message("Service Unavailable")
                        .body("".toResponseBody("application/json".toMediaType()))
                        .build()
                } else {
                    val json = """
                        {
                          "Status": 0,
                          "Answer": [
                            {"name": "dtavbwdnopbizlvokphd.supabase.co.", "type": 1, "TTL": 120, "data": "104.18.38.10"},
                            {"name": "dtavbwdnopbizlvokphd.supabase.co.", "type": 1, "TTL": 120, "data": "172.64.149.246"}
                          ]
                        }
                    """.trimIndent()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(json.toResponseBody("application/dns-json".toMediaType()))
                        .build()
                }
            }
            .build()

        var now = 1_000_000L
        val dns = ResilientSupabaseDns(
            primaryDns = failingSystemDns,
            dohClient = mockDohClient,
            clockMillis = { now }
        )

        val addresses = dns.lookup("dtavbwdnopbizlvokphd.supabase.co")
        assertEquals(2, addresses.size)
        assertEquals("dtavbwdnopbizlvokphd.supabase.co", addresses[0].hostName)
        assertEquals("104.18.38.10", addresses[0].hostAddress)
        assertEquals("172.64.149.246", addresses[1].hostAddress)
        assertEquals(2, dohCallCount) // 1.1.1.1 failed -> 8.8.8.8 succeeded

        // Second lookup within TTL must hit in-memory cache without calling DoH again
        now += 30_000L
        val cachedAddresses = dns.lookup("dtavbwdnopbizlvokphd.supabase.co")
        assertEquals(2, cachedAddresses.size)
        assertEquals(2, dohCallCount)
    }

    @Test
    fun authRepository_acceptsSupabaseRestV1ApiUrlAndCallsNormalizedAuthEndpoint() = runTest {
        val requestedUrls = mutableListOf<String>()
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                requestedUrls.add(url)
                val body = when {
                    url.endsWith("/rest/v1/rpc/resolve_login_email") ->
                        "\"nim_251601001@auth.azrilclas.internal\""
                    url.endsWith("/auth/v1/token?grant_type=password") ->
                        """{"access_token":"jwt_student_token","user":{"id":"stu-251601001"}}"""
                    url.contains("/rest/v1/profiles?id=eq.stu-251601001") ->
                        """[{"id":"stu-251601001","full_name":"Ahmad Mahasiswa","nim":"251601001","email":"251601001@mahasiswa.azrilclas.ac.id","username":"251601001","study_program_id":"prodi-pjkr","class_id":"cls-pjkr-3b","semester":3,"cohort_year":2025,"account_status":"active","student_badge_status":"aktif","must_change_password":false,"role":"student","study_programs":{"code":"PJKR","name":"PJKR"}}]"""
                    else -> "[]"
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val inMemoryStore = object : AuthKeyValueStore {
            private val strings = mutableMapOf<String, String?>()
            private val booleans = mutableMapOf<String, Boolean>()
            private val longs = mutableMapOf<String, Long>()
            override suspend fun getString(key: String): String? = strings[key]
            override suspend fun putString(key: String, value: String?) { strings[key] = value }
            override suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean = booleans[key] ?: defaultValue
            override suspend fun putBoolean(key: String, value: Boolean) { booleans[key] = value }
            override suspend fun getLong(key: String, defaultValue: Long): Long = longs[key] ?: defaultValue
            override suspend fun putLong(key: String, value: Long) { longs[key] = value }
        }

        val repo = AzrilAuthRepository(
            store = inMemoryStore,
            supabaseUrl = "https://dtavbwdnopbizlvokphd.supabase.co/rest/v1/",
            supabaseAnonKey = "sb_publishable_eKuisubHOBl-K95eEC02Rw_0SJHtzzJ",
            httpClient = mockClient
        )

        val result = repo.login("251601001", "251601001", rememberMe = true)
        assertTrue("Expected AuthResult.Success, got $result", result is AuthResult.Success)
        assertTrue(requestedUrls.any { it == "https://dtavbwdnopbizlvokphd.supabase.co/rest/v1/rpc/resolve_login_email" })
        assertTrue(requestedUrls.any { it == "https://dtavbwdnopbizlvokphd.supabase.co/auth/v1/token?grant_type=password" })
    }

    @Test
    fun studentLogin_succeedsWhenResolveRpcMissingAndFallsBackToDeterministicStudentEmailAndMetadata() = runTest {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val reqBody = okio.Buffer().also { chain.request().body?.writeTo(it) }.readUtf8()
                when {
                    url.endsWith("/rest/v1/rpc/resolve_login_email") ->
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(404)
                            .message("Not Found")
                            .body("""{"message":"Could not find the function public.resolve_login_email"}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    url.endsWith("/auth/v1/token?grant_type=password") && reqBody.contains("251601001@mahasiswa.azrilclas.ac.id") ->
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(
                                """
                                {
                                  "access_token": "jwt_student_251601001",
                                  "user": {
                                    "id": "stu-251601001",
                                    "email": "251601001@mahasiswa.azrilclas.ac.id",
                                    "user_metadata": {
                                      "full_name": "AWANGKU NIMAS PUTRI KARTIKA S",
                                      "nim": "251601001",
                                      "username": "251601001",
                                      "study_program_code": "FARMASI",
                                      "class_name": "FARMASI-3",
                                      "semester": 3,
                                      "cohort_year": 2025,
                                      "must_change_password": true
                                    }
                                  }
                                }
                                """.trimIndent().toResponseBody("application/json".toMediaType())
                            )
                            .build()
                    url.endsWith("/auth/v1/token?grant_type=password") ->
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(400)
                            .message("Bad Request")
                            .body("""{"error":"invalid_grant","error_description":"Invalid login credentials"}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    url.contains("/rest/v1/profiles") ->
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("[]".toResponseBody("application/json".toMediaType()))
                            .build()
                    else ->
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(404)
                            .message("Not Found")
                            .body("".toResponseBody("application/json".toMediaType()))
                            .build()
                }
            }
            .build()

        val inMemoryStore = object : AuthKeyValueStore {
            private val strings = mutableMapOf<String, String?>()
            private val booleans = mutableMapOf<String, Boolean>()
            private val longs = mutableMapOf<String, Long>()
            override suspend fun getString(key: String): String? = strings[key]
            override suspend fun putString(key: String, value: String?) { strings[key] = value }
            override suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean = booleans[key] ?: defaultValue
            override suspend fun putBoolean(key: String, value: Boolean) { booleans[key] = value }
            override suspend fun getLong(key: String, defaultValue: Long): Long = longs[key] ?: defaultValue
            override suspend fun putLong(key: String, value: Long) { longs[key] = value }
        }

        val repo = AzrilAuthRepository(
            store = inMemoryStore,
            supabaseUrl = "https://dtavbwdnopbizlvokphd.supabase.co",
            supabaseAnonKey = "sb_publishable_eKuisubHOBl-K95eEC02Rw_0SJHtzzJ",
            httpClient = mockClient
        )

        val result = repo.login("251601001", "251601001", rememberMe = true)
        assertTrue("Expected AuthResult.Success via fallback email & metadata, got $result", result is AuthResult.Success)
        val profile = (result as AuthResult.Success).profile
        assertEquals("AWANGKU NIMAS PUTRI KARTIKA S", profile.fullName)
        assertEquals("251601001", profile.nim)
        assertEquals("FARMASI", profile.studyProgramName)
        assertEquals("FARMASI-3", profile.className)
    }

    @Test
    fun syncStudentsToSupabase_repairsLocalCredentialsAndProvisionsViaSignupFallback() = runTest {
        val signupEmails = mutableListOf<String>()
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val reqBody = okio.Buffer().also { chain.request().body?.writeTo(it) }.readUtf8()
                if (url.endsWith("/auth/v1/signup")) {
                    val email = org.json.JSONObject(reqBody).optString("email")
                    signupEmails.add(email)
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"access_token":"tok","user":{"id":"u-1","email":"$email","email_confirmed_at":"2026-11-19T10:00:00Z","identities":[{"id":"id-1"}]}}""".toResponseBody("application/json".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody("application/json".toMediaType()))
                        .build()
                }
            }
            .build()

        val inMemoryStore = object : AuthKeyValueStore {
            private val strings = mutableMapOf<String, String?>()
            private val booleans = mutableMapOf<String, Boolean>()
            private val longs = mutableMapOf<String, Long>()
            override suspend fun getString(key: String): String? = strings[key]
            override suspend fun putString(key: String, value: String?) { strings[key] = value }
            override suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean = booleans[key] ?: defaultValue
            override suspend fun putBoolean(key: String, value: Boolean) { booleans[key] = value }
            override suspend fun getLong(key: String, defaultValue: Long): Long = longs[key] ?: defaultValue
            override suspend fun putLong(key: String, value: Long) { longs[key] = value }
        }

        val repo = AzrilAuthRepository(
            store = inMemoryStore,
            supabaseUrl = "https://dtavbwdnopbizlvokphd.supabase.co",
            supabaseAnonKey = "sb_publishable_eKuisubHOBl-K95eEC02Rw_0SJHtzzJ",
            httpClient = mockClient
        )

        val student = com.example.ui.model.StudentRecord(
            id = "stu-farmasi-1",
            fullName = "AWANGKU NIMAS PUTRI KARTIKA S",
            nim = "251601001",
            email = "251601001@mahasiswa.azrilclas.ac.id",
            studyProgramId = "prodi-farmasi",
            studyProgramName = "FARMASI",
            classId = "cls-farmasi-3",
            className = "FARMASI-3",
            semester = 3,
            cohortYear = 2025
        )

        val report = repo.syncStudentsToSupabase(listOf(student), "2026/2027-Ganjil")
        assertEquals(1, report.localRepairedCount)
        assertEquals(1, report.syncedToSupabaseCount)
        assertEquals(listOf("251601001@mahasiswa.azrilclas.ac.id"), signupEmails)

        // Even if Supabase Auth is unreachable afterward, repaired local login works immediately
        val loginRes = repo.login("251601001", "251601001", rememberMe = true)
        assertTrue("Expected repaired local login success, got $loginRes", loginRes is AuthResult.Success)
    }
}
