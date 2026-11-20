package com.example.data.auth

import com.example.data.network.SupabaseNetworkClient
import com.example.ui.model.AcademicClass
import com.example.ui.model.AccountStatus
import com.example.ui.model.StudentBadgeStatus
import com.example.ui.model.StudentRecord
import com.example.ui.model.StudyProgram
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class RemoteAcademicDirectory(
    val studyPrograms: List<StudyProgram>,
    val classes: List<AcademicClass>,
    val students: List<StudentRecord>
)

sealed interface RemoteDirectoryResult {
    data class Success(val directory: RemoteAcademicDirectory) : RemoteDirectoryResult
    data class Error(val message: String, val isUnauthorized: Boolean = false) : RemoteDirectoryResult
}

sealed interface RemoteStudentWriteResult {
    data class Success(val student: StudentRecord) : RemoteStudentWriteResult
    data class Error(val message: String) : RemoteStudentWriteResult
}

sealed interface RemoteDeleteResult {
    data object Success : RemoteDeleteResult
    data class Error(val message: String) : RemoteDeleteResult
}

/**
 * Authoritative Supabase CRUD service for Admin student management (`public.profiles`,
 * `public.classes`, `public.study_programs`, and `auth.users`).
 *
 * Guarantees:
 * - Never fakes success when Supabase rejects a write or when Admin JWT is missing.
 * - Resolves real Supabase `study_programs.id` UUIDs (by code: HES/PJKR/FARMASI) so
 *   PostgreSQL never fails on local placeholder IDs like `"prodi-hes"`.
 * - Persists INSERT/UPDATE/DELETE directly to Supabase and supports full re-fetch
 *   so multiple Admin devices and Student phones always see identical authoritative data.
 */
class SupabaseStudentCrudService(
    private val supabaseUrl: String,
    private val supabaseAnonKey: String,
    private val httpClient: OkHttpClient
) {
    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val PAGE_SIZE = 1000
        private const val MAX_PAGES = 30
        private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        fun isUuid(value: String?): Boolean = value != null && UUID_REGEX.matches(value.trim())

        fun sanitizeNim(raw: String): String =
            raw.replace(Regex("[\\s\\u00A0\\u200B-\\u200D\\uFEFF]+"), "").trim()

        fun sanitizeIdentifier(raw: String): String =
            raw.replace(Regex("[\\u00A0\\u200B-\\u200D\\uFEFF]+"), "").trim()
    }

    private fun baseUrl(): String = SupabaseNetworkClient.normalizeBaseUrl(supabaseUrl)

    private fun nowIsoUtc(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

    private fun parsePostgrestError(code: Int, rawBody: String, fallback: String): String {
        val parsed = runCatching { JSONObject(rawBody) }.getOrNull()
        val msg = parsed?.optString("message").orEmpty()
            .ifBlank { parsed?.optString("error_description").orEmpty() }
            .ifBlank { parsed?.optString("error").orEmpty() }
            .ifBlank { parsed?.optString("msg").orEmpty() }
        val details = parsed?.optString("details").orEmpty()
        val hint = parsed?.optString("hint").orEmpty()
        val combined = listOf(msg, details, hint).filter { it.isNotBlank() }.joinToString(" — ")
        val pgCode = parsed?.optString("code").orEmpty()
        return when {
            code == 401 -> "Sesi Admin Supabase telah berakhir atau tidak valid (HTTP 401). Silakan logout lalu login ulang menggunakan akun Admin Supabase."
            pgCode == "42P17" || combined.contains("infinite recursion", ignoreCase = true) ->
                "Kebijakan RLS tabel profiles mengalami rekursi (42P17). Jalankan migrasi SQL 009_admin_auth_and_legacy_student_visibility.sql di Supabase SQL Editor."
            code == 403 -> "Akses ditolak oleh kebijakan keamanan RLS Supabase (HTTP 403). Pastikan akun Anda terdaftar dengan role 'admin' di tabel profiles."
            combined.contains("duplicate key", ignoreCase = true) || code == 409 ->
                "Data duplikat di database Supabase (NIM atau email sudah terdaftar)."
            combined.isNotBlank() -> "$fallback: $combined"
            else -> "$fallback (HTTP $code)."
        }
    }

    /**
     * Fetches the authoritative list of study programs, classes, and student profiles
     * directly from Supabase using the authenticated Admin JWT.
     */
    fun fetchDirectory(jwt: String): RemoteDirectoryResult {
        val base = baseUrl()
        if (!SupabaseNetworkClient.isConfigured(base, supabaseAnonKey)) {
            return RemoteDirectoryResult.Error("Konfigurasi Supabase belum aktif.")
        }
        if (jwt.isBlank()) {
            return RemoteDirectoryResult.Error(
                message = "Sesi Admin belum terhubung ke Supabase Auth. Logout lalu login ulang menggunakan akun Admin Supabase (username atau email Admin yang terdaftar di Supabase Auth dengan role='admin').",
                isUnauthorized = true
            )
        }
        return try {
            val prodisOutcome = fetchStudyProgramsWithStatus(base, jwt)
            if (prodisOutcome is FetchOutcome.Failure) {
                return RemoteDirectoryResult.Error(
                    message = parsePostgrestError(
                        prodisOutcome.code,
                        prodisOutcome.rawBody,
                        "Gagal memuat daftar program studi dari Supabase"
                    ),
                    isUnauthorized = prodisOutcome.code == 401
                )
            }
            val prodis = (prodisOutcome as FetchOutcome.Success).data
            val effectiveProdis = prodis.ifEmpty { OfficialStudyPrograms.list }
            val prodiById = effectiveProdis.associateBy { it.id }
            val prodiByCode = effectiveProdis.associateBy { it.code.uppercase(Locale.US) }

            val classesOutcome = fetchClassesWithStatus(base, jwt, prodiById, prodiByCode)
            val rawClasses = when (classesOutcome) {
                is FetchOutcome.Success -> classesOutcome.data
                is FetchOutcome.Failure -> {
                    if (classesOutcome.code == 401) {
                        return RemoteDirectoryResult.Error(
                            message = parsePostgrestError(401, classesOutcome.rawBody, "Gagal memuat kelas"),
                            isUnauthorized = true
                        )
                    }
                    emptyList()
                }
            }
            val classById = rawClasses.associateBy { it.id }

            val studentsOutcome = fetchStudentsWithStatus(base, jwt, prodiById, prodiByCode, classById)
            if (studentsOutcome is FetchOutcome.Failure) {
                return RemoteDirectoryResult.Error(
                    message = parsePostgrestError(
                        studentsOutcome.code,
                        studentsOutcome.rawBody,
                        "Gagal memuat data mahasiswa dari tabel profiles Supabase"
                    ),
                    isUnauthorized = studentsOutcome.code == 401
                )
            }
            val students = (studentsOutcome as FetchOutcome.Success).data

            // Recompute class memberIds directly from authoritative student list
            val syncedClasses = rawClasses.map { cls ->
                cls.copy(memberIds = students.filter { it.classId == cls.id }.map { it.id })
            }

            RemoteDirectoryResult.Success(
                RemoteAcademicDirectory(
                    studyPrograms = effectiveProdis,
                    classes = syncedClasses,
                    students = students
                )
            )
        } catch (e: Exception) {
            RemoteDirectoryResult.Error(SupabaseNetworkClient.formatConnectionError(e, base))
        }
    }

    /**
     * Persists a newly added student into Supabase (`auth.users` + `public.classes` + `public.profiles`).
     * Strictly verifies the write succeeds on Supabase before returning [RemoteStudentWriteResult.Success].
     */
    fun createStudent(
        jwt: String,
        student: StudentRecord,
        initialPassword: String,
        targetClass: AcademicClass?
    ): RemoteStudentWriteResult {
        val base = baseUrl()
        if (jwt.isBlank()) {
            return RemoteStudentWriteResult.Error(
                "Gagal menyimpan ke Supabase: sesi Admin saat ini bersifat lokal. Pastikan akun Admin terdaftar di Supabase Auth & tabel profiles (role='admin'), lalu login ulang."
            )
        }

        return try {
            val cleanNim = sanitizeNim(student.nim)
            val cleanName = student.fullName.trim()
            val cleanEmail = student.email.trim().lowercase(Locale.US)
                .ifBlank { "${cleanNim.lowercase(Locale.US)}@mahasiswa.azrilclas.ac.id" }
            val prodiCode = student.studyProgramName.trim().uppercase(Locale.US).ifBlank { "HES" }

            // 1. Resolve canonical Supabase study_programs.id UUID (never send "prodi-hes" to a UUID column)
            val remoteProdis = fetchStudyPrograms(base, jwt).orEmpty()
            val resolvedProdi = remoteProdis.find { it.code.equals(prodiCode, ignoreCase = true) }
                ?: remoteProdis.find { it.id == student.studyProgramId }
            val resolvedProdiUuid = resolvedProdi?.id?.takeIf { isUuid(it) }
                ?: student.studyProgramId.takeIf { isUuid(it) }
                ?: return RemoteStudentWriteResult.Error(
                    "Program studi '$prodiCode' tidak ditemukan di tabel study_programs Supabase. Jalankan migrasi 001–008 di SQL Editor Supabase."
                )

            // 2. Ensure target class exists in public.classes before referencing class_id FK
            val effectiveClassId = if (targetClass != null) {
                ensureClassExistsInSupabase(base, jwt, targetClass, resolvedProdiUuid, prodiCode)
                    ?: return RemoteStudentWriteResult.Error(
                        "Gagal menyimpan data kelas '${targetClass.name}' ke tabel classes Supabase."
                    )
            } else {
                student.classId?.takeIf { it.isNotBlank() }
            }

            // 3. Primary path: server-side Edge Function `admin-bulk-import-students` (action = "create_student")
            val edgePayload = JSONObject().apply {
                put("action", "create_student")
                put("fullName", cleanName)
                put("nim", cleanNim)
                put("email", cleanEmail)
                put("password", initialPassword)
                put("studyProgramCode", prodiCode)
                put("studyProgramId", resolvedProdiUuid)
                put("classId", effectiveClassId ?: JSONObject.NULL)
                put("className", targetClass?.name ?: student.className ?: JSONObject.NULL)
                put("semester", student.semester)
                put("cohortYear", student.cohortYear)
                put("accountStatus", student.status.toDbValue())
                put("badgeStatus", student.badgeStatus.dbValue)
                put("mustChangePassword", student.mustChangePassword)
            }
            val edgeReq = Request.Builder()
                .url("$base/functions/v1/admin-bulk-import-students")
                .post(edgePayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Authorization", "Bearer $jwt")
                .addHeader("Content-Type", "application/json")
                .build()

            httpClient.newCall(edgeReq).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val json = runCatching { JSONObject(rawBody) }.getOrNull()
                    val createdObj = json?.optJSONObject("student")
                    val createdId = createdObj?.optString("id").orEmpty().ifBlank { json?.optString("id").orEmpty() }
                    if (createdId.isNotBlank()) {
                        return RemoteStudentWriteResult.Success(
                            student.copy(
                                id = createdId,
                                fullName = cleanName,
                                nim = cleanNim,
                                email = cleanEmail,
                                studyProgramId = resolvedProdiUuid,
                                studyProgramName = prodiCode,
                                classId = effectiveClassId,
                                className = targetClass?.name ?: student.className
                            )
                        )
                    }
                } else if (resp.code != 404) {
                    return RemoteStudentWriteResult.Error(
                        parsePostgrestError(resp.code, rawBody, "Gagal menambahkan mahasiswa di Supabase")
                    )
                }
            }

            // 4. Fallback path when Edge Function is not deployed (HTTP 404):
            //    Check duplicate NIM first on public.profiles, then create auth.users via /auth/v1/signup,
            //    then UPSERT full profile row via Admin JWT on /rest/v1/profiles?on_conflict=id.
            if (isNimAlreadyInSupabase(base, jwt, cleanNim, excludeId = null)) {
                return RemoteStudentWriteResult.Error("NIM '$cleanNim' sudah terdaftar di database Supabase.")
            }

            val signupEmail = cleanEmail
            val meta = JSONObject().apply {
                put("full_name", cleanName)
                put("nim", cleanNim)
                put("username", cleanNim)
                put("academic_email", cleanEmail)
                put("study_program_code", prodiCode)
                put("study_program_id", resolvedProdiUuid)
                if (effectiveClassId != null) put("class_id", effectiveClassId)
                put("class_name", targetClass?.name ?: student.className ?: "")
                put("semester", student.semester)
                put("cohort_year", student.cohortYear)
                put("student_badge_status", student.badgeStatus.dbValue)
                put("must_change_password", student.mustChangePassword)
            }
            val signupPayload = JSONObject().apply {
                put("email", signupEmail)
                put("password", initialPassword)
                put("data", meta)
            }
            val signupReq = Request.Builder()
                .url("$base/auth/v1/signup")
                .post(signupPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Content-Type", "application/json")
                .build()

            val createdAuthUserId = httpClient.newCall(signupReq).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                val lower = rawBody.lowercase(Locale.US)
                if (!resp.isSuccessful) {
                    return RemoteStudentWriteResult.Error(
                        when {
                            resp.code == 429 || lower.contains("rate limit") ->
                                "Batas pendaftaran akun Supabase Auth tercapai (rate limit). Coba lagi beberapa menit atau deploy Edge Function admin-bulk-import-students."
                            lower.contains("already registered") || lower.contains("user already exists") ->
                                "Email '$signupEmail' sudah terdaftar di Supabase Auth. Gunakan email lain atau cek daftar mahasiswa."
                            else -> parsePostgrestError(resp.code, rawBody, "Gagal membuat akun login mahasiswa di Supabase Auth")
                        }
                    )
                }
                val parsed = runCatching { JSONObject(rawBody) }.getOrNull()
                val userObj = parsed?.optJSONObject("user") ?: parsed
                val identities = userObj?.optJSONArray("identities")
                if (identities != null && identities.length() == 0) {
                    return RemoteStudentWriteResult.Error(
                        "Email '$signupEmail' sudah terdaftar di Supabase Auth."
                    )
                }
                val uid = userObj?.optString("id").orEmpty()
                if (!isUuid(uid)) {
                    return RemoteStudentWriteResult.Error(
                        "Supabase Auth tidak mengembalikan ID pengguna yang valid saat membuat akun mahasiswa."
                    )
                }
                uid
            }

            // Upsert complete profile row into public.profiles using Admin JWT so ALL fields
            // (including class_id, semester, cohort_year, account_status, badge_status, internal_auth_email)
            // are guaranteed to be stored persistently in Supabase.
            val profileBody = JSONObject().apply {
                put("id", createdAuthUserId)
                put("full_name", cleanName)
                put("nim", cleanNim)
                put("username", cleanNim)
                put("email", cleanEmail)
                put("internal_auth_email", signupEmail)
                put("study_program_id", resolvedProdiUuid)
                put("class_id", effectiveClassId ?: JSONObject.NULL)
                put("semester", student.semester)
                put("cohort_year", student.cohortYear)
                put("role", "student")
                put("account_status", student.status.toDbValue())
                put("student_badge_status", student.badgeStatus.dbValue)
                put("must_change_password", student.mustChangePassword)
                put("updated_at", nowIsoUtc())
            }
            val upsertReq = Request.Builder()
                .url("$base/rest/v1/profiles?on_conflict=id")
                .post(profileBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Authorization", "Bearer $jwt")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                .build()

            httpClient.newCall(upsertReq).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return RemoteStudentWriteResult.Error(
                        parsePostgrestError(resp.code, rawBody, "Gagal menyimpan profil mahasiswa ke tabel profiles Supabase")
                    )
                }
                val arr = runCatching { JSONArray(rawBody) }.getOrNull()
                if (arr == null || arr.length() == 0) {
                    return RemoteStudentWriteResult.Error(
                        "Data mahasiswa tidak tersimpan di tabel profiles (0 baris terpengaruh). Periksa kebijakan RLS Admin."
                    )
                }
            }

            RemoteStudentWriteResult.Success(
                student.copy(
                    id = createdAuthUserId,
                    fullName = cleanName,
                    nim = cleanNim,
                    email = cleanEmail,
                    studyProgramId = resolvedProdiUuid,
                    studyProgramName = prodiCode,
                    classId = effectiveClassId,
                    className = targetClass?.name ?: student.className
                )
            )
        } catch (e: Exception) {
            RemoteStudentWriteResult.Error(SupabaseNetworkClient.formatConnectionError(e, base))
        }
    }

    /**
     * Executes an authoritative UPDATE on `public.profiles` in Supabase for an existing student.
     */
    fun updateStudent(
        jwt: String,
        student: StudentRecord,
        previousNim: String?,
        targetClass: AcademicClass?
    ): RemoteStudentWriteResult {
        val base = baseUrl()
        if (jwt.isBlank()) {
            return RemoteStudentWriteResult.Error(
                "Gagal memperbarui data di Supabase: sesi Admin saat ini bersifat lokal. Login ulang sebagai Admin Supabase."
            )
        }

        return try {
            val cleanNim = sanitizeNim(student.nim)
            val cleanName = student.fullName.trim()
            val cleanEmail = student.email.trim().lowercase(Locale.US)
            val prodiCode = student.studyProgramName.trim().uppercase(Locale.US).ifBlank { "HES" }

            val remoteProdis = fetchStudyPrograms(base, jwt).orEmpty()
            val resolvedProdiUuid = remoteProdis.find { it.code.equals(prodiCode, ignoreCase = true) }?.id
                ?.takeIf { isUuid(it) }
                ?: student.studyProgramId.takeIf { isUuid(it) }
                ?: return RemoteStudentWriteResult.Error("Program studi '$prodiCode' tidak ditemukan di Supabase.")

            val effectiveClassId = if (targetClass != null) {
                ensureClassExistsInSupabase(base, jwt, targetClass, resolvedProdiUuid, prodiCode)
                    ?: return RemoteStudentWriteResult.Error("Gagal menyimpan kelas '${targetClass.name}' di Supabase.")
            } else {
                student.classId?.takeIf { it.isNotBlank() }
            }

            // Resolve canonical Supabase profile ID (handles legacy local IDs by looking up previousNim if needed)
            val canonicalId = resolveCanonicalProfileId(base, jwt, student.id, previousNim ?: cleanNim)
                ?: return RemoteStudentWriteResult.Error(
                    "Data mahasiswa tidak ditemukan di tabel profiles Supabase. Muat ulang data mahasiswa terlebih dahulu."
                )

            // 1. Primary path: Edge Function `update_student` (syncs auth.users + profiles atomically)
            val edgePayload = JSONObject().apply {
                put("action", "update_student")
                put("studentId", canonicalId)
                put("fullName", cleanName)
                put("nim", cleanNim)
                put("email", cleanEmail)
                put("studyProgramId", resolvedProdiUuid)
                put("studyProgramCode", prodiCode)
                put("classId", effectiveClassId ?: JSONObject.NULL)
                put("semester", student.semester)
                put("cohortYear", student.cohortYear)
                put("accountStatus", student.status.toDbValue())
                put("badgeStatus", student.badgeStatus.dbValue)
            }
            val edgeReq = Request.Builder()
                .url("$base/functions/v1/admin-bulk-import-students")
                .post(edgePayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Authorization", "Bearer $jwt")
                .addHeader("Content-Type", "application/json")
                .build()

            var handledByEdge = false
            httpClient.newCall(edgeReq).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    handledByEdge = true
                } else if (resp.code != 404) {
                    return RemoteStudentWriteResult.Error(
                        parsePostgrestError(resp.code, rawBody, "Gagal memperbarui data mahasiswa di Supabase")
                    )
                }
            }

            if (!handledByEdge) {
                // 2. Direct PostgREST PATCH on public.profiles with Admin JWT
                if (isNimAlreadyInSupabase(base, jwt, cleanNim, excludeId = canonicalId)) {
                    return RemoteStudentWriteResult.Error("NIM '$cleanNim' sudah digunakan mahasiswa lain di Supabase.")
                }

                val patchBody = JSONObject().apply {
                    put("full_name", cleanName)
                    put("nim", cleanNim)
                    put("username", cleanNim)
                    put("email", cleanEmail)
                    put("study_program_id", resolvedProdiUuid)
                    put("class_id", effectiveClassId ?: JSONObject.NULL)
                    put("semester", student.semester)
                    put("cohort_year", student.cohortYear)
                    put("account_status", student.status.toDbValue())
                    put("student_badge_status", student.badgeStatus.dbValue)
                    put("updated_at", nowIsoUtc())
                }
                val patchReq = Request.Builder()
                    .url("$base/rest/v1/profiles?id=eq.$canonicalId")
                    .patch(patchBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .addHeader("apikey", supabaseAnonKey.trim())
                    .addHeader("Authorization", "Bearer $jwt")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .build()

                httpClient.newCall(patchReq).execute().use { resp ->
                    val rawBody = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return RemoteStudentWriteResult.Error(
                            parsePostgrestError(resp.code, rawBody, "Gagal memperbarui data mahasiswa di Supabase")
                        )
                    }
                    val arr = runCatching { JSONArray(rawBody) }.getOrNull()
                    if (arr == null || arr.length() == 0) {
                        return RemoteStudentWriteResult.Error(
                            "Pembaruan mahasiswa tidak memengaruhi baris mana pun di Supabase (0 baris). Periksa hak akses Admin RLS."
                        )
                    }
                }

                // Best-effort sync of historical_attendance_records when NIM changes
                if (!previousNim.isNullOrBlank() && !previousNim.equals(cleanNim, ignoreCase = true)) {
                    runCatching {
                        val rpcPayload = JSONObject().apply {
                            put("p_student_id", canonicalId)
                            put("p_new_nim", cleanNim)
                        }
                        val rpcReq = Request.Builder()
                            .url("$base/rest/v1/rpc/admin_update_student_nim")
                            .post(rpcPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                            .addHeader("apikey", supabaseAnonKey.trim())
                            .addHeader("Authorization", "Bearer $jwt")
                            .addHeader("Content-Type", "application/json")
                            .build()
                        httpClient.newCall(rpcReq).execute().close()
                    }
                }
            }

            RemoteStudentWriteResult.Success(
                student.copy(
                    id = canonicalId,
                    fullName = cleanName,
                    nim = cleanNim,
                    email = cleanEmail,
                    studyProgramId = resolvedProdiUuid,
                    studyProgramName = prodiCode,
                    classId = effectiveClassId,
                    className = targetClass?.name ?: student.className
                )
            )
        } catch (e: Exception) {
            RemoteStudentWriteResult.Error(SupabaseNetworkClient.formatConnectionError(e, base))
        }
    }

    /**
     * Executes an authoritative PATCH on `student_badge_status` in `public.profiles`.
     */
    fun updateStudentBadge(
        jwt: String,
        studentId: String,
        studentNim: String?,
        badgeStatus: StudentBadgeStatus
    ): RemoteDeleteResult {
        val base = baseUrl()
        if (jwt.isBlank()) {
            return RemoteDeleteResult.Error(
                "Gagal memperbarui status mahasiswa: sesi Admin belum terhubung ke Supabase Auth."
            )
        }
        return try {
            val canonicalId = resolveCanonicalProfileId(base, jwt, studentId, studentNim)
                ?: return RemoteDeleteResult.Error("Profil mahasiswa tidak ditemukan di Supabase.")

            val patchBody = JSONObject().apply {
                put("student_badge_status", badgeStatus.dbValue)
                put("updated_at", nowIsoUtc())
            }
            val req = Request.Builder()
                .url("$base/rest/v1/profiles?id=eq.$canonicalId")
                .patch(patchBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Authorization", "Bearer $jwt")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return RemoteDeleteResult.Error(
                        parsePostgrestError(resp.code, rawBody, "Gagal mengubah cap mahasiswa di Supabase")
                    )
                }
                val arr = runCatching { JSONArray(rawBody) }.getOrNull()
                if (arr == null || arr.length() == 0) {
                    return RemoteDeleteResult.Error("Perubahan cap mahasiswa ditolak oleh RLS atau data tidak ditemukan.")
                }
            }
            RemoteDeleteResult.Success
        } catch (e: Exception) {
            RemoteDeleteResult.Error(SupabaseNetworkClient.formatConnectionError(e, base))
        }
    }

    /**
     * Executes an authoritative DELETE for a student in Supabase (`auth.users` via Edge Function
     * or `DELETE /rest/v1/profiles?id=eq.<id>` via Admin JWT).
     */
    fun deleteStudent(
        jwt: String,
        studentId: String,
        studentNim: String?
    ): RemoteDeleteResult {
        val base = baseUrl()
        if (jwt.isBlank()) {
            return RemoteDeleteResult.Error(
                "Gagal menghapus data di Supabase: sesi Admin saat ini bersifat lokal. Login ulang sebagai Admin Supabase."
            )
        }
        return try {
            val canonicalId = resolveCanonicalProfileId(base, jwt, studentId, studentNim)
                ?: return RemoteDeleteResult.Error("Data mahasiswa sudah tidak ditemukan di database Supabase.")

            // 1. Primary path: Edge Function `delete_student` (deletes from auth.users + cascades to profiles)
            val edgePayload = JSONObject().apply {
                put("action", "delete_student")
                put("studentId", canonicalId)
            }
            val edgeReq = Request.Builder()
                .url("$base/functions/v1/admin-bulk-import-students")
                .post(edgePayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Authorization", "Bearer $jwt")
                .addHeader("Content-Type", "application/json")
                .build()

            httpClient.newCall(edgeReq).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    return RemoteDeleteResult.Success
                } else if (resp.code != 404) {
                    return RemoteDeleteResult.Error(
                        parsePostgrestError(resp.code, rawBody, "Gagal menghapus mahasiswa di Supabase")
                    )
                }
            }

            // 2. Direct PostgREST DELETE on public.profiles with Admin JWT
            val deleteReq = Request.Builder()
                .url("$base/rest/v1/profiles?id=eq.$canonicalId")
                .delete()
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Authorization", "Bearer $jwt")
                .addHeader("Prefer", "return=representation")
                .build()

            httpClient.newCall(deleteReq).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return RemoteDeleteResult.Error(
                        parsePostgrestError(resp.code, rawBody, "Gagal menghapus data mahasiswa di tabel profiles Supabase")
                    )
                }
                val arr = runCatching { JSONArray(rawBody) }.getOrNull()
                if (arr == null || arr.length() == 0) {
                    return RemoteDeleteResult.Error(
                        "Penghapusan mahasiswa tidak memengaruhi baris mana pun di Supabase (0 baris). Periksa kebijakan RLS Admin."
                    )
                }
            }
            RemoteDeleteResult.Success
        } catch (e: Exception) {
            RemoteDeleteResult.Error(SupabaseNetworkClient.formatConnectionError(e, base))
        }
    }

    /**
     * Upserts a class in `public.classes` so foreign key references on `profiles.class_id` always succeed.
     */
    fun ensureClassExistsInSupabase(
        base: String,
        jwt: String,
        academicClass: AcademicClass,
        resolvedProdiUuid: String,
        prodiCode: String
    ): String? {
        val body = JSONObject().apply {
            put("id", academicClass.id)
            put("name", academicClass.name.trim())
            put("study_program_id", resolvedProdiUuid)
            put("study_program_code", prodiCode)
            put("semester", academicClass.semester)
            put("academic_year", academicClass.academicYear.ifBlank { "2026/2027" })
            put("cohort_year", academicClass.cohortYear)
            put("description", academicClass.description)
            put("is_active", academicClass.isActive)
            put("updated_at", nowIsoUtc())
        }
        val req = Request.Builder()
            .url("$base/rest/v1/classes?on_conflict=id")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("apikey", supabaseAnonKey.trim())
            .addHeader("Authorization", "Bearer $jwt")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
            .build()

        return httpClient.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) academicClass.id else null
        }
    }

    fun upsertClass(
        jwt: String,
        academicClass: AcademicClass,
        studyProgramCode: String
    ): RemoteDeleteResult {
        val base = baseUrl()
        if (jwt.isBlank()) {
            return RemoteDeleteResult.Error(
                "Gagal menyimpan kelas ke Supabase: sesi Admin Supabase tidak ditemukan. Silakan login ulang sebagai Admin."
            )
        }
        return try {
            val prodiCode = studyProgramCode.trim().uppercase(Locale.US).ifBlank { "HES" }
            val remoteProdis = fetchStudyPrograms(base, jwt).orEmpty()
            val resolvedProdiUuid = remoteProdis.find { it.code.equals(prodiCode, ignoreCase = true) }?.id
                ?: remoteProdis.find { it.id == academicClass.studyProgramId }?.id
                ?: academicClass.studyProgramId.takeIf { isUuid(it) }
                ?: return RemoteDeleteResult.Error("Program studi '$prodiCode' tidak ditemukan di Supabase.")
            val savedId = ensureClassExistsInSupabase(base, jwt, academicClass, resolvedProdiUuid, prodiCode)
            if (savedId != null) RemoteDeleteResult.Success
            else RemoteDeleteResult.Error("Gagal menyimpan kelas '${academicClass.name}' ke tabel classes Supabase.")
        } catch (e: Exception) {
            RemoteDeleteResult.Error(SupabaseNetworkClient.formatConnectionError(e, base))
        }
    }

    fun deleteClass(jwt: String, classId: String): RemoteDeleteResult {
        val base = baseUrl()
        if (jwt.isBlank()) {
            return RemoteDeleteResult.Error("Gagal menghapus kelas di Supabase: sesi Admin tidak aktif.")
        }
        return try {
            val encodedId = URLEncoder.encode(classId, "UTF-8")
            val req = Request.Builder()
                .url("$base/rest/v1/classes?id=eq.$encodedId")
                .delete()
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Authorization", "Bearer $jwt")
                .addHeader("Prefer", "return=representation")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val raw = resp.body?.string().orEmpty()
                    return RemoteDeleteResult.Error(parsePostgrestError(resp.code, raw, "Gagal menghapus kelas di Supabase"))
                }
            }
            RemoteDeleteResult.Success
        } catch (e: Exception) {
            RemoteDeleteResult.Error(SupabaseNetworkClient.formatConnectionError(e, base))
        }
    }

    private fun resolveCanonicalProfileId(
        base: String,
        jwt: String,
        candidateId: String,
        fallbackNim: String?
    ): String? {
        if (isUuid(candidateId)) {
            val checkReq = Request.Builder()
                .url("$base/rest/v1/profiles?id=eq.$candidateId&select=id")
                .get()
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Authorization", "Bearer $jwt")
                .build()
            val exists = runCatching {
                httpClient.newCall(checkReq).execute().use { resp ->
                    resp.isSuccessful && JSONArray(resp.body?.string().orEmpty()).length() > 0
                }
            }.getOrDefault(false)
            if (exists) return candidateId
        }
        val cleanNim = sanitizeNim(fallbackNim.orEmpty())
        if (cleanNim.isBlank()) return null
        val encoded = URLEncoder.encode(cleanNim, "UTF-8")
        val lookupReq = Request.Builder()
            .url("$base/rest/v1/profiles?nim=ilike.$encoded&select=id&limit=1")
            .get()
            .addHeader("apikey", supabaseAnonKey.trim())
            .addHeader("Authorization", "Bearer $jwt")
            .build()
        return runCatching {
            httpClient.newCall(lookupReq).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val arr = JSONArray(resp.body?.string().orEmpty())
                if (arr.length() == 0) null else arr.getJSONObject(0).optString("id").takeIf { isUuid(it) }
            }
        }.getOrNull()
    }

    private fun isNimAlreadyInSupabase(
        base: String,
        jwt: String,
        nim: String,
        excludeId: String?
    ): Boolean {
        val encoded = URLEncoder.encode(nim, "UTF-8")
        val excludeClause = if (isUuid(excludeId)) "&id=neq.$excludeId" else ""
        val req = Request.Builder()
            .url("$base/rest/v1/profiles?nim=ilike.$encoded$excludeClause&select=id&limit=1")
            .get()
            .addHeader("apikey", supabaseAnonKey.trim())
            .addHeader("Authorization", "Bearer $jwt")
            .build()
        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                JSONArray(resp.body?.string().orEmpty()).length() > 0
            }
        }.getOrDefault(false)
    }

    private sealed interface FetchOutcome<out T> {
        data class Success<T>(val data: T) : FetchOutcome<T>
        data class Failure(val code: Int, val rawBody: String) : FetchOutcome<Nothing>
    }

    private fun fetchStudyPrograms(base: String, jwt: String): List<StudyProgram>? =
        when (val res = fetchStudyProgramsWithStatus(base, jwt)) {
            is FetchOutcome.Success -> res.data
            is FetchOutcome.Failure -> null
        }

    private fun fetchStudyProgramsWithStatus(base: String, jwt: String): FetchOutcome<List<StudyProgram>> {
        val arrOutcome = fetchPaginatedJsonArrayWithStatus(
            base = base,
            pathAndQuery = "/rest/v1/study_programs?select=id,code,name,faculty,description,is_active&order=code.asc",
            jwt = jwt
        )
        if (arrOutcome is FetchOutcome.Failure) return arrOutcome
        val arr = (arrOutcome as FetchOutcome.Success).data
        val list = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").trim()
            val code = o.optString("code").trim().uppercase(Locale.US)
            if (id.isBlank() || code.isBlank()) return@mapNotNull null
            StudyProgram(
                id = id,
                code = code,
                name = o.optString("name", code).ifBlank { code },
                faculty = o.optString("faculty", ""),
                description = o.optString("description", ""),
                isActive = o.optBoolean("is_active", true)
            )
        }
        return FetchOutcome.Success(list)
    }

    private fun fetchClassesWithStatus(
        base: String,
        jwt: String,
        prodiById: Map<String, StudyProgram>,
        prodiByCode: Map<String, StudyProgram>
    ): FetchOutcome<List<AcademicClass>> {
        val arrOutcome = fetchPaginatedJsonArrayWithStatus(
            base = base,
            pathAndQuery = "/rest/v1/classes?select=id,name,study_program_id,study_program_code,semester,academic_year,cohort_year,description,is_active&order=name.asc",
            jwt = jwt
        )
        if (arrOutcome is FetchOutcome.Failure) return arrOutcome
        val arr = (arrOutcome as FetchOutcome.Success).data
        val list = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").trim()
            val name = o.optString("name").trim()
            if (id.isBlank() || name.isBlank()) return@mapNotNull null
            val prodiId = o.optString("study_program_id").trim()
            val prodiCodeRaw = o.optString("study_program_code").trim().uppercase(Locale.US)
            val resolvedProdi = prodiById[prodiId] ?: prodiByCode[prodiCodeRaw]
            AcademicClass(
                id = id,
                name = name,
                studyProgramId = resolvedProdi?.id ?: prodiId,
                studyProgramName = resolvedProdi?.code ?: prodiCodeRaw.ifBlank { "HES" },
                semester = o.optInt("semester", 1),
                academicYear = o.optString("academic_year", "2026/2027").ifBlank { "2026/2027" },
                cohortYear = o.optInt("cohort_year", 2026),
                description = o.optString("description", ""),
                isActive = o.optBoolean("is_active", true),
                memberIds = emptyList()
            )
        }
        return FetchOutcome.Success(list)
    }

    private fun fetchStudentsWithStatus(
        base: String,
        jwt: String,
        prodiById: Map<String, StudyProgram>,
        prodiByCode: Map<String, StudyProgram>,
        classById: Map<String, AcademicClass>
    ): FetchOutcome<List<StudentRecord>> {
        // 1. Primary rich query with study_programs + classes joins
        val primary = fetchPaginatedJsonArrayWithStatus(
            base = base,
            pathAndQuery = "/rest/v1/profiles?role=eq.student&select=*,study_programs(id,code,name),classes(id,name)&order=created_at.desc",
            jwt = jwt
        )
        if (primary is FetchOutcome.Failure && primary.code == 401) {
            return primary
        }

        // 2. If primary succeeded with rows, use it. Otherwise also try resilient fallback queries
        //    so legacy student rows (e.g. role='STUDENT' uppercase, role=NULL, or schema without classes FK)
        //    are NEVER hidden from Admin Dashboard.
        var selectedArray: JSONArray? = (primary as? FetchOutcome.Success)?.data?.takeIf { it.length() > 0 }
        var lastFailure: FetchOutcome.Failure? = primary as? FetchOutcome.Failure

        if (selectedArray == null) {
            val fallbackQueries = listOf(
                "/rest/v1/profiles?role=eq.student&select=*&order=created_at.desc",
                "/rest/v1/profiles?role=neq.admin&select=*,study_programs(id,code,name),classes(id,name)",
                "/rest/v1/profiles?select=*"
            )
            for (query in fallbackQueries) {
                when (val attempt = fetchPaginatedJsonArrayWithStatus(base, query, jwt)) {
                    is FetchOutcome.Success -> {
                        if (attempt.data.length() > 0 || selectedArray == null) {
                            selectedArray = attempt.data
                        }
                        if (attempt.data.length() > 0) break
                    }
                    is FetchOutcome.Failure -> {
                        if (attempt.code == 401) return attempt
                        lastFailure = attempt
                    }
                }
            }
        }

        val arr = selectedArray
            ?: return lastFailure ?: FetchOutcome.Failure(500, "")

        val parsed = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").trim()
            if (id.isBlank()) return@mapNotNull null
            val roleValue = o.optString("role", "student").trim()
            if (roleValue.equals("admin", ignoreCase = true)) return@mapNotNull null
            val prodiObj = o.optJSONObject("study_programs")
            val classObj = o.optJSONObject("classes")
            val rawProdiId = o.optString("study_program_id").trim()
            val joinedProdiCode = prodiObj?.optString("code")?.trim()?.uppercase(Locale.US).orEmpty()
                .ifBlank { o.optString("study_program_code").trim().uppercase(Locale.US) }
            val resolvedProdi = prodiById[rawProdiId]
                ?: prodiByCode[joinedProdiCode]
                ?: OfficialStudyPrograms.findByCode(joinedProdiCode)
            val classId = o.optString("class_id").trim().ifBlank { null }
            val className = classObj?.optString("name")?.trim()?.ifBlank { null }
                ?: o.optString("class_name").trim().ifBlank { null }
                ?: classId?.let { classById[it]?.name }
            val statusStr = o.optString("account_status", "active").lowercase(Locale.US)
            val accountStatus = when (statusStr) {
                "inactive" -> AccountStatus.INACTIVE
                "pending_activation" -> AccountStatus.PENDING_ACTIVATION
                else -> AccountStatus.ACTIVE
            }
            val nim = sanitizeNim(o.optString("nim", o.optString("username", "")))
            StudentRecord(
                id = id,
                fullName = o.optString("full_name", "Mahasiswa").trim().ifBlank { nim.ifBlank { "Mahasiswa" } },
                nim = nim.ifBlank { "-" },
                email = o.optString("email", "").trim().lowercase(Locale.US),
                studyProgramId = resolvedProdi?.id ?: rawProdiId.ifBlank { OfficialStudyPrograms.list.first().id },
                studyProgramName = resolvedProdi?.code ?: joinedProdiCode.ifBlank { "HES" },
                classId = classId,
                className = className,
                semester = o.optInt("semester", 1),
                cohortYear = o.optInt("cohort_year", 2026),
                status = accountStatus,
                badgeStatus = StudentBadgeStatus.fromDbValue(o.optString("student_badge_status", "neutral")),
                mustChangePassword = o.optBoolean("must_change_password", false)
            )
        }
        return FetchOutcome.Success(parsed)
    }

    private fun fetchPaginatedJsonArrayWithStatus(
        base: String,
        pathAndQuery: String,
        jwt: String
    ): FetchOutcome<JSONArray> {
        val merged = JSONArray()
        for (page in 0 until MAX_PAGES) {
            val from = page * PAGE_SIZE
            val to = from + PAGE_SIZE - 1
            val req = Request.Builder()
                .url("$base$pathAndQuery")
                .get()
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Authorization", "Bearer $jwt")
                .addHeader("Range-Unit", "items")
                .addHeader("Range", "$from-$to")
                .build()

            val batch = httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty().trim()
                if (!resp.isSuccessful) {
                    if (resp.code == 416 && page > 0) return@use JSONArray()
                    return FetchOutcome.Failure(resp.code, raw)
                }
                if (raw.isEmpty()) JSONArray() else JSONArray(raw)
            }
            for (i in 0 until batch.length()) {
                merged.put(batch.get(i))
            }
            if (batch.length() < PAGE_SIZE) break
        }
        return FetchOutcome.Success(merged)
    }

    private fun AccountStatus.toDbValue(): String = when (this) {
        AccountStatus.ACTIVE -> "active"
        AccountStatus.INACTIVE -> "inactive"
        AccountStatus.PENDING_ACTIVATION -> "pending_activation"
    }
}
