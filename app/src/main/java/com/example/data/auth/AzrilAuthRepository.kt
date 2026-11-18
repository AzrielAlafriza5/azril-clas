package com.example.data.auth

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import com.example.ui.model.AccountStatus
import com.example.ui.model.StudyProgram
import com.example.ui.model.UserProfile
import com.example.ui.model.UserRole
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private val Context.azrilAuthDataStore: DataStore<Preferences> by preferencesDataStore(name = "azril_clas_auth_prefs")

object OfficialStudyPrograms {
    val ALLOWED_CODES = setOf("HES", "PJKR", "FARMASI")

    val list: List<StudyProgram> = listOf(
        StudyProgram(
            id = "prodi-hes",
            code = "HES",
            name = "HES",
            faculty = "Hukum Ekonomi Syariah",
            description = "Program Studi Hukum Ekonomi Syariah (HES)",
            isActive = true
        ),
        StudyProgram(
            id = "prodi-pjkr",
            code = "PJKR",
            name = "PJKR",
            faculty = "Pendidikan Jasmani, Kesehatan, dan Rekreasi",
            description = "Program Studi PJKR",
            isActive = true
        ),
        StudyProgram(
            id = "prodi-farmasi",
            code = "FARMASI",
            name = "FARMASI",
            faculty = "Ilmu Kesehatan",
            description = "Program Studi Farmasi",
            isActive = true
        )
    )

    fun findByCode(code: String): StudyProgram? =
        list.find { it.code.equals(code.trim(), ignoreCase = true) }

    fun findById(id: String): StudyProgram? =
        list.find { it.id == id }
}

object PasswordHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 65_536
    private const val KEY_LENGTH_BITS = 256

    fun hashPassword(password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val derived = derive(password, salt)
        val saltB64 = encodeBase64(salt)
        val hashB64 = encodeBase64(derived)
        return "pbkdf2_sha256$$ITERATIONS$$saltB64$$hashB64"
    }

    fun verifyPassword(password: String, encoded: String): Boolean {
        val parts = encoded.split("$")
        if (parts.size != 4 || parts[0] != "pbkdf2_sha256") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = decodeBase64(parts[2])
        val expectedHash = decodeBase64(parts[3])
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, expectedHash.size * 8)
        val actualHash = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        spec.clearPassword()
        return MessageDigest.isEqual(expectedHash, actualHash)
    }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val encoded = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        spec.clearPassword()
        return encoded
    }

    private fun encodeBase64(bytes: ByteArray): String =
        try {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }

    private fun decodeBase64(value: String): ByteArray =
        try {
            Base64.decode(value, Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getDecoder().decode(value)
        }
}

interface AuthKeyValueStore {
    suspend fun getString(key: String): String?
    suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean
    suspend fun getLong(key: String, defaultValue: Long): Long
    suspend fun putString(key: String, value: String?)
    suspend fun putBoolean(key: String, value: Boolean)
    suspend fun putLong(key: String, value: Long)
}

class DataStoreAuthStorage(private val context: Context) : AuthKeyValueStore {
    override suspend fun getString(key: String): String? =
        context.azrilAuthDataStore.data.first()[stringPreferencesKey(key)]

    override suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        context.azrilAuthDataStore.data.first()[booleanPreferencesKey(key)] ?: defaultValue

    override suspend fun getLong(key: String, defaultValue: Long): Long =
        context.azrilAuthDataStore.data.first()[longPreferencesKey(key)] ?: defaultValue

    override suspend fun putString(key: String, value: String?) {
        context.azrilAuthDataStore.edit { prefs ->
            val prefKey = stringPreferencesKey(key)
            if (value == null) prefs.remove(prefKey) else prefs[prefKey] = value
        }
    }

    override suspend fun putBoolean(key: String, value: Boolean) {
        context.azrilAuthDataStore.edit { prefs ->
            prefs[booleanPreferencesKey(key)] = value
        }
    }

    override suspend fun putLong(key: String, value: Long) {
        context.azrilAuthDataStore.edit { prefs ->
            prefs[longPreferencesKey(key)] = value
        }
    }
}

sealed interface AuthResult {
    data class Success(val profile: UserProfile, val usingSupabase: Boolean) : AuthResult
    data class Failure(val message: String) : AuthResult
}

data class RestoredSession(
    val profile: UserProfile?,
    val rememberedIdentifier: String,
    val rememberMeEnabled: Boolean,
    val registeredStudents: List<UserProfile>,
    val isSupabaseConfigured: Boolean
)

data class StoredAccountRecord(
    val id: String,
    val fullName: String,
    val nim: String,
    val email: String,
    val username: String,
    val studyProgramId: String,
    val studyProgramCode: String,
    val role: UserRole,
    val passwordHash: String,
    val createdAt: String
) {
    fun toUserProfile(): UserProfile = UserProfile(
        id = id,
        fullName = fullName,
        email = email,
        username = username,
        role = role,
        nim = nim,
        studyProgramId = studyProgramId,
        studyProgramName = studyProgramCode,
        className = "Belum terdaftar di kelas",
        semester = 1,
        cohortYear = 2026,
        status = AccountStatus.ACTIVE,
        createdAt = createdAt
    )
}

class AzrilAuthRepository(
    private val store: AuthKeyValueStore,
    private val supabaseUrl: String = BuildConfig.SUPABASE_URL,
    private val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    companion object {
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_SESSION_USER_ID = "session_user_id"
        private const val KEY_SESSION_PROFILE_JSON = "session_profile_json"
        private const val KEY_SESSION_EXPIRES_AT = "session_expires_at"
        private const val KEY_REMEMBER_ME = "remember_me_enabled"
        private const val KEY_REMEMBERED_IDENTIFIER = "remembered_identifier"
        private const val KEY_ACCOUNTS_JSON = "registered_accounts_v1"

        private const val REMEMBER_SESSION_DURATION_MS = 30L * 24 * 60 * 60 * 1000
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    fun isSupabaseConfigured(): Boolean {
        val cleanUrl = supabaseUrl.trim()
        val cleanKey = supabaseAnonKey.trim()
        return cleanUrl.startsWith("https://") &&
            !cleanUrl.contains("YOUR_PROJECT_REF") &&
            cleanKey.length > 20 &&
            !cleanKey.contains("YOUR_SUPABASE_ANON_KEY")
    }

    private fun nowIso(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

    suspend fun restoreSession(): RestoredSession = withContext(Dispatchers.IO) {
        val rememberMe = store.getBoolean(KEY_REMEMBER_ME, true)
        val rememberedIdentifier = store.getString(KEY_REMEMBERED_IDENTIFIER).orEmpty()
        val token = store.getString(KEY_SESSION_TOKEN)
        val expiresAt = store.getLong(KEY_SESSION_EXPIRES_AT, 0L)
        val accounts = loadLocalAccounts()
        val registeredProfiles = accounts.filter { it.role == UserRole.STUDENT }.map { it.toUserProfile() }

        val restoredProfile = if (rememberMe && !token.isNullOrBlank() && System.currentTimeMillis() < expiresAt) {
            val userId = store.getString(KEY_SESSION_USER_ID)
            val fromLocal = accounts.find { it.id == userId }?.toUserProfile()
            fromLocal ?: deserializeProfile(store.getString(KEY_SESSION_PROFILE_JSON))
        } else {
            // Session expired or Remember Me disabled -> clear stale session token while keeping remembered identifier
            store.putString(KEY_SESSION_TOKEN, null)
            store.putString(KEY_SESSION_USER_ID, null)
            store.putString(KEY_SESSION_PROFILE_JSON, null)
            store.putLong(KEY_SESSION_EXPIRES_AT, 0L)
            null
        }

        RestoredSession(
            profile = restoredProfile,
            rememberedIdentifier = rememberedIdentifier,
            rememberMeEnabled = rememberMe,
            registeredStudents = registeredProfiles,
            isSupabaseConfigured = isSupabaseConfigured()
        )
    }

    suspend fun registerStudent(
        fullName: String,
        nim: String,
        emailOrUsername: String,
        password: String,
        confirmPassword: String,
        studyProgramCode: String,
        rememberMe: Boolean
    ): AuthResult = withContext(Dispatchers.IO) {
        val cleanName = fullName.trim()
        val cleanNim = nim.trim()
        val cleanInput = emailOrUsername.trim().lowercase(Locale.US)
        val prodi = OfficialStudyPrograms.findByCode(studyProgramCode)

        if (cleanName.length < 3) return@withContext AuthResult.Failure("Nama lengkap wajib diisi (minimal 3 karakter).")
        if (cleanNim.length < 5) return@withContext AuthResult.Failure("NIM wajib diisi (minimal 5 karakter).")
        if (cleanInput.length < 3) return@withContext AuthResult.Failure("Email atau username wajib diisi (minimal 3 karakter).")
        if (cleanInput.contains(" ")) return@withContext AuthResult.Failure("Email atau username tidak boleh mengandung spasi.")
        if (password.length < 8) return@withContext AuthResult.Failure("Kata sandi minimal 8 karakter.")
        if (password != confirmPassword) return@withContext AuthResult.Failure("Konfirmasi kata sandi tidak cocok.")
        if (prodi == null) return@withContext AuthResult.Failure("Pilih program studi yang valid (HES, PJKR, atau FARMASI).")

        val isEmailInput = cleanInput.contains("@") && cleanInput.contains(".")
        val resolvedEmail = if (isEmailInput) cleanInput else "$cleanInput@mahasiswa.azrilclas.ac.id"
        val resolvedUsername = if (isEmailInput) cleanInput.substringBefore("@") else cleanInput

        if (isSupabaseConfigured()) {
            val remoteResult = registerViaSupabase(
                fullName = cleanName,
                nim = cleanNim,
                email = resolvedEmail,
                username = resolvedUsername,
                password = password,
                prodi = prodi,
                rememberMe = rememberMe,
                loginIdentifier = cleanInput
            )
            if (remoteResult is AuthResult.Success || remoteResult is AuthResult.Failure) {
                return@withContext remoteResult
            }
        }

        // Secure local credential store fallback (PBKDF2WithHmacSHA256 + unique NIM/email/username check)
        val accounts = loadLocalAccounts()
        if (accounts.any { it.nim.equals(cleanNim, ignoreCase = true) }) {
            return@withContext AuthResult.Failure("NIM '$cleanNim' sudah terdaftar pada akun lain.")
        }
        if (accounts.any { it.email.equals(resolvedEmail, ignoreCase = true) }) {
            return@withContext AuthResult.Failure("Email '$resolvedEmail' sudah digunakan oleh akun lain.")
        }
        if (accounts.any { it.username.equals(resolvedUsername, ignoreCase = true) }) {
            return@withContext AuthResult.Failure("Username '$resolvedUsername' sudah digunakan oleh akun lain.")
        }

        val newRecord = StoredAccountRecord(
            id = UUID.randomUUID().toString(),
            fullName = cleanName,
            nim = cleanNim,
            email = resolvedEmail,
            username = resolvedUsername,
            studyProgramId = prodi.id,
            studyProgramCode = prodi.code,
            role = UserRole.STUDENT, // Always assigned by system, never user-chosen
            passwordHash = PasswordHasher.hashPassword(password),
            createdAt = nowIso()
        )
        saveLocalAccounts(listOf(newRecord) + accounts)

        val profile = newRecord.toUserProfile()
        persistSessionState(
            profile = profile,
            token = "azr_sess_${UUID.randomUUID()}",
            identifier = cleanInput,
            rememberMe = rememberMe
        )
        AuthResult.Success(profile = profile, usingSupabase = false)
    }

    suspend fun login(
        identifier: String,
        password: String,
        rememberMe: Boolean
    ): AuthResult = withContext(Dispatchers.IO) {
        val cleanId = identifier.trim().lowercase(Locale.US)
        if (cleanId.isEmpty()) return@withContext AuthResult.Failure("Email atau username wajib diisi.")
        if (password.isEmpty()) return@withContext AuthResult.Failure("Kata sandi wajib diisi.")

        if (isSupabaseConfigured()) {
            return@withContext loginViaSupabase(cleanId, password, rememberMe)
        }

        val accounts = loadLocalAccounts()
        val matched = accounts.find {
            it.email.equals(cleanId, ignoreCase = true) || it.username.equals(cleanId, ignoreCase = true)
        } ?: return@withContext AuthResult.Failure("Akun tidak ditemukan. Periksa username/email atau daftar akun baru.")

        if (!PasswordHasher.verifyPassword(password, matched.passwordHash)) {
            return@withContext AuthResult.Failure("Kata sandi salah. Silakan coba lagi.")
        }

        val profile = matched.toUserProfile()
        persistSessionState(
            profile = profile,
            token = "azr_sess_${UUID.randomUUID()}",
            identifier = cleanId,
            rememberMe = rememberMe
        )
        AuthResult.Success(profile = profile, usingSupabase = false)
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        val token = store.getString(KEY_SESSION_TOKEN)
        if (isSupabaseConfigured() && !token.isNullOrBlank()) {
            runCatching {
                val req = Request.Builder()
                    .url("${supabaseUrl.trimEnd('/')}/auth/v1/logout")
                    .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                    .addHeader("apikey", supabaseAnonKey.trim())
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                httpClient.newCall(req).execute().close()
            }
        }
        // End session immediately; keep remembered identifier ONLY if Remember Me was enabled (NEVER password)
        store.putString(KEY_SESSION_TOKEN, null)
        store.putString(KEY_SESSION_USER_ID, null)
        store.putString(KEY_SESSION_PROFILE_JSON, null)
        store.putLong(KEY_SESSION_EXPIRES_AT, 0L)
        if (!store.getBoolean(KEY_REMEMBER_ME, false)) {
            store.putString(KEY_REMEMBERED_IDENTIFIER, null)
        }
    }

    suspend fun getRegisteredStudents(): List<UserProfile> = withContext(Dispatchers.IO) {
        loadLocalAccounts().filter { it.role == UserRole.STUDENT }.map { it.toUserProfile() }
    }

    private suspend fun persistSessionState(
        profile: UserProfile,
        token: String,
        identifier: String,
        rememberMe: Boolean
    ) {
        store.putBoolean(KEY_REMEMBER_ME, rememberMe)
        if (rememberMe) {
            store.putString(KEY_REMEMBERED_IDENTIFIER, identifier)
            store.putString(KEY_SESSION_TOKEN, token)
            store.putString(KEY_SESSION_USER_ID, profile.id)
            store.putString(KEY_SESSION_PROFILE_JSON, serializeProfile(profile))
            store.putLong(KEY_SESSION_EXPIRES_AT, System.currentTimeMillis() + REMEMBER_SESSION_DURATION_MS)
        } else {
            // Do not persist long-lived session across app restart when Remember Me is unchecked
            store.putString(KEY_REMEMBERED_IDENTIFIER, null)
            store.putString(KEY_SESSION_TOKEN, null)
            store.putString(KEY_SESSION_USER_ID, null)
            store.putString(KEY_SESSION_PROFILE_JSON, null)
            store.putLong(KEY_SESSION_EXPIRES_AT, 0L)
        }
    }

    private suspend fun registerViaSupabase(
        fullName: String,
        nim: String,
        email: String,
        username: String,
        password: String,
        prodi: StudyProgram,
        rememberMe: Boolean,
        loginIdentifier: String
    ): AuthResult {
        return try {
            val base = supabaseUrl.trimEnd('/')
            val payload = JSONObject().apply {
                put("email", email)
                put("password", password)
                put("data", JSONObject().apply {
                    put("full_name", fullName)
                    put("nim", nim)
                    put("username", username)
                    put("study_program_code", prodi.code)
                })
            }
            val request = Request.Builder()
                .url("$base/auth/v1/signup")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Content-Type", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val msg = runCatching { JSONObject(body).optString("msg").ifBlank { JSONObject(body).optString("error_description") } }
                        .getOrDefault("")
                        .ifBlank { "Pendaftaran gagal (${response.code}). Pastikan NIM, email, dan username belum terdaftar." }
                    return AuthResult.Failure(msg)
                }
                val json = JSONObject(body)
                val accessToken = json.optString("access_token")
                val userObj = json.optJSONObject("user") ?: json
                val userId = userObj.optString("id").ifBlank { UUID.randomUUID().toString() }
                val profile = UserProfile(
                    id = userId,
                    fullName = fullName,
                    email = email,
                    username = username,
                    role = UserRole.STUDENT,
                    nim = nim,
                    studyProgramId = prodi.id,
                    studyProgramName = prodi.code,
                    className = "Belum terdaftar di kelas",
                    semester = 1,
                    cohortYear = 2026,
                    status = AccountStatus.ACTIVE,
                    createdAt = nowIso()
                )
                persistSessionState(profile, accessToken.ifBlank { "supabase_pending" }, loginIdentifier, rememberMe)
                AuthResult.Success(profile = profile, usingSupabase = true)
            }
        } catch (e: Exception) {
            AuthResult.Failure("Tidak dapat terhubung ke Supabase Auth: ${e.localizedMessage ?: "kesalahan jaringan"}")
        }
    }

    private suspend fun loginViaSupabase(
        identifier: String,
        password: String,
        rememberMe: Boolean
    ): AuthResult {
        return try {
            val base = supabaseUrl.trimEnd('/')
            val resolvedEmail = if (identifier.contains("@")) {
                identifier
            } else {
                resolveEmailFromSupabaseUsername(base, identifier)
                    ?: return AuthResult.Failure("Username tidak ditemukan di database.")
            }

            val payload = JSONObject().apply {
                put("email", resolvedEmail)
                put("password", password)
            }
            val request = Request.Builder()
                .url("$base/auth/v1/token?grant_type=password")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Content-Type", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return AuthResult.Failure("Email/username atau kata sandi tidak sesuai.")
                }
                val json = JSONObject(body)
                val accessToken = json.optString("access_token")
                val userObj = json.optJSONObject("user")
                val userId = userObj?.optString("id").orEmpty()
                val profile = fetchSupabaseProfile(base, accessToken, userId, resolvedEmail)
                    ?: return AuthResult.Failure("Profil akun tidak ditemukan di tabel profiles.")

                persistSessionState(profile, accessToken, identifier, rememberMe)
                AuthResult.Success(profile = profile, usingSupabase = true)
            }
        } catch (e: Exception) {
            AuthResult.Failure("Gagal terhubung ke Supabase Auth: ${e.localizedMessage ?: "kesalahan jaringan"}")
        }
    }

    private fun resolveEmailFromSupabaseUsername(baseUrl: String, username: String): String? {
        val body = JSONObject().apply { put("p_identifier", username) }
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/resolve_login_email")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("apikey", supabaseAnonKey.trim())
            .addHeader("Content-Type", "application/json")
            .build()
        return httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.string()?.trim()?.removeSurrounding("\"")?.takeIf { it.contains("@") }
        }
    }

    private fun fetchSupabaseProfile(baseUrl: String, jwt: String, userId: String, fallbackEmail: String): UserProfile? {
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/profiles?id=eq.$userId&select=id,full_name,nim,email,username,study_program_id,role,created_at,study_programs(code,name)")
            .get()
            .addHeader("apikey", supabaseAnonKey.trim())
            .addHeader("Authorization", "Bearer $jwt")
            .build()
        return httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val arr = JSONArray(resp.body?.string().orEmpty())
            if (arr.length() == 0) return null
            val row = arr.getJSONObject(0)
            val prodiObj = row.optJSONObject("study_programs")
            val roleStr = row.optString("role", "student")
            UserProfile(
                id = row.optString("id", userId),
                fullName = row.optString("full_name", "Mahasiswa"),
                email = row.optString("email", fallbackEmail),
                username = row.optString("username").ifBlank { null },
                role = if (roleStr.equals("admin", ignoreCase = true)) UserRole.ADMIN else UserRole.STUDENT,
                nim = row.optString("nim").ifBlank { null },
                studyProgramId = row.optString("study_program_id").ifBlank { null },
                studyProgramName = prodiObj?.optString("code")?.ifBlank { null } ?: prodiObj?.optString("name"),
                className = "Terhubung ke Supabase",
                status = AccountStatus.ACTIVE,
                createdAt = row.optString("created_at").ifBlank { null }
            )
        }
    }

    private suspend fun loadLocalAccounts(): List<StoredAccountRecord> {
        val raw = store.getString(KEY_ACCOUNTS_JSON) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                StoredAccountRecord(
                    id = o.getString("id"),
                    fullName = o.getString("full_name"),
                    nim = o.getString("nim"),
                    email = o.getString("email"),
                    username = o.getString("username"),
                    studyProgramId = o.getString("study_program_id"),
                    studyProgramCode = o.getString("study_program_code"),
                    role = if (o.optString("role") == "ADMIN") UserRole.ADMIN else UserRole.STUDENT,
                    passwordHash = o.getString("password_hash"),
                    createdAt = o.optString("created_at")
                )
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun saveLocalAccounts(accounts: List<StoredAccountRecord>) {
        val arr = JSONArray()
        accounts.forEach { acc ->
            arr.put(JSONObject().apply {
                put("id", acc.id)
                put("full_name", acc.fullName)
                put("nim", acc.nim)
                put("email", acc.email)
                put("username", acc.username)
                put("study_program_id", acc.studyProgramId)
                put("study_program_code", acc.studyProgramCode)
                put("role", acc.role.name)
                put("password_hash", acc.passwordHash)
                put("created_at", acc.createdAt)
            })
        }
        store.putString(KEY_ACCOUNTS_JSON, arr.toString())
    }

    private fun serializeProfile(profile: UserProfile): String = JSONObject().apply {
        put("id", profile.id)
        put("full_name", profile.fullName)
        put("email", profile.email)
        put("username", profile.username ?: "")
        put("role", profile.role.name)
        put("nim", profile.nim ?: "")
        put("study_program_id", profile.studyProgramId ?: "")
        put("study_program_name", profile.studyProgramName ?: "")
        put("created_at", profile.createdAt ?: "")
    }.toString()

    private fun deserializeProfile(raw: String?): UserProfile? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(raw)
            UserProfile(
                id = o.getString("id"),
                fullName = o.getString("full_name"),
                email = o.getString("email"),
                username = o.optString("username").ifBlank { null },
                role = if (o.optString("role") == "ADMIN") UserRole.ADMIN else UserRole.STUDENT,
                nim = o.optString("nim").ifBlank { null },
                studyProgramId = o.optString("study_program_id").ifBlank { null },
                studyProgramName = o.optString("study_program_name").ifBlank { null },
                className = "Belum terdaftar di kelas",
                status = AccountStatus.ACTIVE,
                createdAt = o.optString("created_at").ifBlank { null }
            )
        }.getOrNull()
    }
}
