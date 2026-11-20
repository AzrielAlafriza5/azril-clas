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
import com.example.data.network.SupabaseNetworkClient
import com.example.ui.model.AcademicClass
import com.example.ui.model.AccountStatus
import com.example.ui.model.Assignment
import com.example.ui.model.AssignmentTargetType
import com.example.ui.model.AttendanceRecord
import com.example.ui.model.AttendanceStatus
import com.example.ui.model.ExcelImportPreviewRow
import com.example.ui.model.HistoricalAttendanceRecord
import com.example.ui.model.ParticipationXpCategory
import com.example.ui.model.PointSourceType
import com.example.ui.model.RankingPointTransaction
import com.example.ui.model.StreakNotificationLog
import com.example.ui.model.StreakReminderSlot
import com.example.ui.model.StudentBadgeStatus
import com.example.ui.model.StudentRecord
import com.example.ui.model.StudentStreakState
import com.example.ui.model.StudyProgram
import com.example.ui.model.SubmissionMethod
import com.example.ui.model.UserProfile
import com.example.ui.model.UserRole
import com.example.ui.model.XpCategory
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    const val BULK_STUDENT_ITERATIONS = 10_000
    private const val KEY_LENGTH_BITS = 256

    fun hashPassword(password: String, iterations: Int = ITERATIONS): String {
        val safeIterations = iterations.coerceAtLeast(1_000)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val derived = derive(password, salt, safeIterations)
        val saltB64 = encodeBase64(salt)
        val hashB64 = encodeBase64(derived)
        return "pbkdf2_sha256$$safeIterations$$saltB64$$hashB64"
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

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
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
    data class Success(
        val profile: UserProfile,
        val usingSupabase: Boolean,
        val sessionExpiresAt: Long = 0L
    ) : AuthResult
    data class Failure(val message: String) : AuthResult
}

data class RestoredSession(
    val profile: UserProfile?,
    val rememberedIdentifier: String,
    val rememberMeEnabled: Boolean,
    val registeredStudents: List<StudentRecord>,
    val savedClasses: List<AcademicClass>,
    val savedAssignments: List<Assignment>,
    val savedXpTransactions: List<RankingPointTransaction> = emptyList(),
    val savedAttendanceRecords: List<AttendanceRecord> = emptyList(),
    val savedHistoricalAttendanceRecords: List<HistoricalAttendanceRecord> = emptyList(),
    val savedStreaks: Map<String, StudentStreakState> = emptyMap(),
    val savedStreakNotifications: List<StreakNotificationLog> = emptyList(),
    val isSupabaseConfigured: Boolean,
    val sessionExpired: Boolean = false,
    val sessionExpiresAt: Long? = null
)

data class SupabaseStudentSyncReport(
    val totalProcessed: Int = 0,
    val localRepairedCount: Int = 0,
    val syncedToSupabaseCount: Int = 0,
    val alreadyInSupabaseCount: Int = 0,
    val usedEdgeFunction: Boolean = false,
    val rateLimited: Boolean = false,
    val emailConfirmationPending: Boolean = false,
    val diagnosticMessage: String? = null
)

data class StoredAccountRecord(
    val id: String,
    val fullName: String,
    val nim: String,
    val email: String,
    val username: String,
    val studyProgramId: String,
    val studyProgramCode: String,
    val classId: String? = null,
    val className: String? = null,
    val semester: Int = 1,
    val cohortYear: Int = 2026,
    val accountStatus: AccountStatus = AccountStatus.ACTIVE,
    val badgeStatus: StudentBadgeStatus = StudentBadgeStatus.NEUTRAL,
    val mustChangePassword: Boolean = false,
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
        classId = classId,
        className = className ?: "Belum terdaftar di kelas",
        semester = semester,
        cohortYear = cohortYear,
        status = accountStatus,
        badgeStatus = badgeStatus,
        mustChangePassword = mustChangePassword,
        createdAt = createdAt
    )

    fun toStudentRecord(): StudentRecord = StudentRecord(
        id = id,
        fullName = fullName,
        nim = nim,
        email = email,
        studyProgramId = studyProgramId,
        studyProgramName = studyProgramCode,
        classId = classId,
        className = className,
        semester = semester,
        cohortYear = cohortYear,
        status = accountStatus,
        badgeStatus = badgeStatus,
        mustChangePassword = mustChangePassword
    )
}

class AzrilAuthRepository(
    private val store: AuthKeyValueStore,
    supabaseUrl: String = BuildConfig.SUPABASE_URL,
    private val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY,
    private val httpClient: OkHttpClient = SupabaseNetworkClient.defaultHttpClient
) {
    private val supabaseUrl: String = SupabaseNetworkClient.normalizeBaseUrl(supabaseUrl)
    private val accountsMutex = Mutex()
    @Volatile
    private var activeAccessToken: String? = null
    @Volatile
    private var activeRefreshToken: String? = null
    @Volatile
    private var lastAdminSupabaseDiagnostic: String? = null
    private val studentCrudService = SupabaseStudentCrudService(
        supabaseUrl = this.supabaseUrl,
        supabaseAnonKey = this.supabaseAnonKey,
        httpClient = this.httpClient
    )

    companion object {
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_SESSION_REFRESH_TOKEN = "session_refresh_token"
        private const val KEY_SESSION_USER_ID = "session_user_id"
        private const val KEY_SESSION_ROLE = "session_role"
        private const val KEY_SESSION_PROFILE_JSON = "session_profile_json"
        private const val KEY_SESSION_EXPIRES_AT = "session_expires_at"
        private const val KEY_REMEMBER_ME = "remember_me_enabled"
        private const val KEY_REMEMBERED_IDENTIFIER = "remembered_identifier"
        private const val KEY_ACCOUNTS_JSON = "registered_accounts_v1"
        private const val KEY_CLASSES_JSON = "azril_classes_v1"
        private const val KEY_ASSIGNMENTS_JSON = "azril_assignments_v1"
        private const val KEY_XP_TRANSACTIONS_JSON = "azril_xp_transactions_v1"
        private const val KEY_ATTENDANCE_RECORDS_JSON = "azril_attendance_records_v1"
        private const val KEY_HISTORICAL_ATTENDANCE_JSON = "azril_historical_attendance_v1"
        private const val KEY_STUDENT_STREAKS_JSON = "azril_student_streaks_v1"
        private const val KEY_STREAK_NOTIFS_JSON = "azril_streak_notifs_v1"

        const val PRIMARY_ADMIN_ID = "admin-primary-azril12v"
        const val PRIMARY_ADMIN_USERNAME = "Azril12v"
        private const val PRIMARY_ADMIN_EMAIL = "admin@azrilclas.ac.id"
        // One-way salted PBKDF2-HMAC-SHA256 verifier (65,536 iterations) used ONLY when Supabase
        // is not yet configured. Plaintext password is NEVER embedded in source code or storage.
        private const val PRIMARY_ADMIN_VERIFIER_HASH =
            "pbkdf2_sha256\$65536\$FO/pZfq2Hy2ECpIGssCCYw==\$RTgrE7uY6SqeKqa78yvtTY6byHMPwKHMPAT4n5KN9kA="

        const val REMEMBER_SESSION_DURATION_MS = 30L * 24 * 60 * 60 * 1000
        const val ADMIN_SESSION_DURATION_MS = 8L * 60 * 60 * 1000
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun internalAuthEmailForNim(nim: String): String {
            val safe = nim.trim().lowercase(Locale.US).filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
            return "nim_${safe}@auth.azrilclas.internal"
        }

        fun internalAuthEmailForAdmin(username: String): String {
            val safe = username.trim().lowercase(Locale.US).filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
            return "admin_${safe}@auth.azrilclas.internal"
        }
    }

    fun isSupabaseConfigured(): Boolean =
        SupabaseNetworkClient.isConfigured(supabaseUrl, supabaseAnonKey)

    private fun isLocalSessionToken(token: String?): Boolean =
        token.isNullOrBlank() || token.startsWith("azr_sess_") || token.startsWith("azr_admin_sess_")

    suspend fun getActiveSupabaseJwt(): String? {
        val inMemory = activeAccessToken
        if (!isLocalSessionToken(inMemory)) return inMemory
        val persisted = store.getString(KEY_SESSION_TOKEN)
        if (!isLocalSessionToken(persisted)) {
            activeAccessToken = persisted
            return persisted
        }
        return null
    }

    private suspend fun getActiveRefreshToken(): String? {
        val inMemory = activeRefreshToken
        if (!inMemory.isNullOrBlank()) return inMemory
        val persisted = store.getString(KEY_SESSION_REFRESH_TOKEN)
        if (!persisted.isNullOrBlank()) {
            activeRefreshToken = persisted
            return persisted
        }
        return null
    }

    /**
     * Exchanges the stored Supabase `refresh_token` for a fresh short-lived JWT access token
     * so Admin and Student sessions remain connected to Supabase Auth across 1-hour expiry windows
     * and Android app restarts.
     */
    suspend fun refreshSupabaseAccessTokenIfNeeded(
        nowMillis: Long = System.currentTimeMillis()
    ): String? = withContext(Dispatchers.IO) {
        if (!isSupabaseConfigured()) return@withContext null
        val refreshToken = getActiveRefreshToken() ?: return@withContext null
        runCatching {
            val base = supabaseUrl.trimEnd('/')
            val payload = JSONObject().apply { put("refresh_token", refreshToken) }
            val req = Request.Builder()
                .url("$base/auth/v1/token?grant_type=refresh_token")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Content-Type", "application/json")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                val newAccess = json.optString("access_token")
                if (newAccess.isBlank()) return@use null
                val newRefresh = json.optString("refresh_token").ifBlank { refreshToken }
                val storedRole = store.getString(KEY_SESSION_ROLE)
                val maxDuration = if (storedRole.equals(UserRole.ADMIN.name, ignoreCase = true)) {
                    ADMIN_SESSION_DURATION_MS
                } else {
                    REMEMBER_SESSION_DURATION_MS
                }
                val newExpiresAt = nowMillis + maxDuration
                activeAccessToken = newAccess
                activeRefreshToken = newRefresh
                if (store.getBoolean(KEY_REMEMBER_ME, true)) {
                    store.putString(KEY_SESSION_TOKEN, newAccess)
                    store.putString(KEY_SESSION_REFRESH_TOKEN, newRefresh)
                    store.putLong(KEY_SESSION_EXPIRES_AT, newExpiresAt)
                }
                newAccess
            }
        }.getOrNull()
    }

    suspend fun hasLiveSupabaseSession(): Boolean =
        isSupabaseConfigured() && getActiveSupabaseJwt() != null

    fun getLastAdminSupabaseDiagnostic(): String? = lastAdminSupabaseDiagnostic

    private suspend fun getOrRefreshActiveSupabaseJwt(): String {
        val current = getActiveSupabaseJwt().orEmpty()
        if (current.isNotBlank()) return current
        return refreshSupabaseAccessTokenIfNeeded().orEmpty()
    }

    /**
     * Connects an already logged-in Admin directly to Supabase Auth in-place without
     * forcing a full logout, strictly verifying `role = 'admin'` on the backend.
     */
    suspend fun connectSupabaseAdminSession(
        identifier: String,
        password: String,
        rememberMe: Boolean = true,
        nowMillis: Long = System.currentTimeMillis()
    ): AuthResult = withContext(Dispatchers.IO) {
        val cleanId = identifier.trim()
        if (cleanId.isEmpty()) return@withContext AuthResult.Failure("Email atau username Admin Supabase wajib diisi.")
        if (password.isEmpty()) return@withContext AuthResult.Failure("Password Admin Supabase wajib diisi.")
        if (!isSupabaseConfigured()) return@withContext AuthResult.Failure("Konfigurasi Supabase belum aktif.")

        when (
            val outcome = loginAdminViaSupabase(
                username = cleanId,
                password = password,
                rememberMe = rememberMe,
                nowMillis = nowMillis
            )
        ) {
            is SupabaseAdminLoginOutcome.Authenticated -> {
                lastAdminSupabaseDiagnostic = null
                outcome.result
            }
            is SupabaseAdminLoginOutcome.Rejected -> outcome.result
            SupabaseAdminLoginOutcome.BackendNotProvisioned -> {
                AuthResult.Failure(
                    lastAdminSupabaseDiagnostic ?: "Akun '$cleanId' belum ditemukan di Supabase Auth atau belum memiliki role='admin' di tabel profiles."
                )
            }
        }
    }

    /**
     * Fetches the authoritative study programs, classes, student list, XP transactions,
     * and login streaks directly from Supabase so all Admin devices see identical database state.
     * Automatically refreshes an expired Supabase JWT once via `refresh_token` if available.
     */
    suspend fun fetchStudentsAndClassesFromSupabase(): RemoteDirectoryResult = withContext(Dispatchers.IO) {
        if (!isSupabaseConfigured()) {
            return@withContext RemoteDirectoryResult.Error("Konfigurasi Supabase belum aktif.")
        }
        var jwt = getOrRefreshActiveSupabaseJwt()
        if (jwt.isBlank()) {
            val diag = lastAdminSupabaseDiagnostic
            val msg = if (!diag.isNullOrBlank()) {
                "Sesi Admin belum terhubung ke Supabase Auth. $diag"
            } else {
                "Sesi Admin belum terhubung ke Supabase Auth. Klik 'Hubungkan Supabase' untuk masuk menggunakan Email & Password akun Admin Supabase (role='admin')."
            }
            return@withContext RemoteDirectoryResult.Error(msg, isUnauthorized = true)
        }
        var result = studentCrudService.fetchDirectory(jwt)
        if (result is RemoteDirectoryResult.Error && result.isUnauthorized) {
            val refreshed = refreshSupabaseAccessTokenIfNeeded()
            if (!refreshed.isNullOrBlank() && refreshed != jwt) {
                jwt = refreshed
                result = studentCrudService.fetchDirectory(jwt)
            }
        }
        when (result) {
            is RemoteDirectoryResult.Success -> {
                lastAdminSupabaseDiagnostic = null
                syncLocalCachesFromDirectory(result.directory, replaceAllStudents = true)
                result
            }
            is RemoteDirectoryResult.Error -> result
        }
    }

    /**
     * Fetches the authoritative student leaderboard roster, classes, study programs,
     * XP transactions, and streaks from Supabase for an authenticated Student.
     * Uses RLS-safe RPC + own-row queries so Student Ranking and Admin Dashboard stay consistent.
     */
    suspend fun fetchStudentLeaderboardAndXpFromSupabase(
        currentStudentId: String
    ): RemoteDirectoryResult = withContext(Dispatchers.IO) {
        if (!isSupabaseConfigured()) {
            return@withContext RemoteDirectoryResult.Error("Konfigurasi Supabase belum aktif.")
        }
        var jwt = getOrRefreshActiveSupabaseJwt()
        if (jwt.isBlank()) {
            return@withContext RemoteDirectoryResult.Error("Sesi mahasiswa belum terhubung ke Supabase Auth.", isUnauthorized = true)
        }
        var result = studentCrudService.fetchDirectoryForStudent(jwt, currentStudentId)
        if (result is RemoteDirectoryResult.Error && result.isUnauthorized) {
            val refreshed = refreshSupabaseAccessTokenIfNeeded()
            if (!refreshed.isNullOrBlank() && refreshed != jwt) {
                jwt = refreshed
                result = studentCrudService.fetchDirectoryForStudent(jwt, currentStudentId)
            }
        }
        if (result is RemoteDirectoryResult.Success) {
            if (result.directory.classes.isNotEmpty()) {
                saveClasses(result.directory.classes)
            }
            if (result.directory.xpTransactions.isNotEmpty()) {
                val ownDetailed = result.directory.xpTransactions.filter { it.studentId == currentStudentId && !it.id.startsWith("sb-lb-") }
                if (ownDetailed.isNotEmpty()) {
                    val existing = loadLocalXpTransactions()
                    val byKey = linkedMapOf<String, RankingPointTransaction>()
                    ownDetailed.forEach { byKey["${it.studentId}|${it.sourceId}"] = it }
                    existing.forEach { tx ->
                        val k = "${tx.studentId}|${tx.sourceId}"
                        if (!byKey.containsKey(k)) byKey[k] = tx
                    }
                    saveXpTransactions(byKey.values.sortedByDescending { it.timestampMillis })
                }
            }
        }
        result
    }

    private suspend fun syncLocalCachesFromDirectory(
        directory: RemoteAcademicDirectory,
        replaceAllStudents: Boolean
    ) {
        if (replaceAllStudents) {
            accountsMutex.withLock {
                val existing = loadLocalAccounts()
                val existingAdmins = existing.filter { it.role == UserRole.ADMIN }
                val existingById = existing.associateBy { it.id }
                val existingByNim = existing.associateBy { it.nim.trim().lowercase(Locale.US) }
                val now = nowIso()
                val refreshedStudents = directory.students.map { stu ->
                    val prev = existingById[stu.id] ?: existingByNim[stu.nim.trim().lowercase(Locale.US)]
                    StoredAccountRecord(
                        id = stu.id,
                        fullName = stu.fullName,
                        nim = stu.nim,
                        email = stu.email,
                        username = stu.nim,
                        studyProgramId = stu.studyProgramId,
                        studyProgramCode = stu.studyProgramName,
                        classId = stu.classId,
                        className = stu.className,
                        semester = stu.semester,
                        cohortYear = stu.cohortYear,
                        accountStatus = stu.status,
                        badgeStatus = stu.badgeStatus,
                        mustChangePassword = stu.mustChangePassword,
                        role = UserRole.STUDENT,
                        passwordHash = prev?.passwordHash
                            ?: PasswordHasher.hashPassword(stu.nim, PasswordHasher.BULK_STUDENT_ITERATIONS),
                        createdAt = prev?.createdAt ?: now
                    )
                }
                saveLocalAccounts(refreshedStudents + existingAdmins)
            }
        }
        if (directory.classes.isNotEmpty()) {
            saveClasses(directory.classes)
        }
        if (directory.xpTransactions.isNotEmpty()) {
            val existingXp = loadLocalXpTransactions()
            val merged = linkedMapOf<String, RankingPointTransaction>()
            directory.xpTransactions.forEach { merged["${it.studentId}|${it.sourceId}"] = it }
            existingXp.forEach { tx ->
                val k = "${tx.studentId}|${tx.sourceId}"
                if (!merged.containsKey(k)) merged[k] = tx
            }
            saveXpTransactions(merged.values.sortedByDescending { it.timestampMillis })
        }
        if (directory.studentStreaks.isNotEmpty()) {
            val existingStreaks = loadLocalStudentStreaks().toMutableMap()
            directory.studentStreaks.forEach { (sid, remote) ->
                val local = existingStreaks[sid]
                existingStreaks[sid] = if (local != null) {
                    remote.copy(
                        loginDatesWib = (local.loginDatesWib + listOfNotNull(remote.lastLoginDateWib)).distinct(),
                        claimedMilestonesInCycle = local.claimedMilestonesInCycle
                    )
                } else remote
            }
            saveStudentStreaks(existingStreaks)
        }
    }

    suspend fun adminSaveClassToSupabase(
        academicClass: AcademicClass,
        studyProgramCode: String
    ): String? = withContext(Dispatchers.IO) {
        if (!isSupabaseConfigured()) return@withContext null
        val jwt = getOrRefreshActiveSupabaseJwt()
        when (val res = studentCrudService.upsertClass(jwt, academicClass, studyProgramCode)) {
            is RemoteDeleteResult.Error -> res.message
            RemoteDeleteResult.Success -> null
        }
    }

    suspend fun adminDeleteClassFromSupabase(classId: String): String? = withContext(Dispatchers.IO) {
        if (!isSupabaseConfigured()) return@withContext null
        val jwt = getOrRefreshActiveSupabaseJwt()
        when (val res = studentCrudService.deleteClass(jwt, classId)) {
            is RemoteDeleteResult.Error -> res.message
            RemoteDeleteResult.Success -> null
        }
    }

    private fun nowIso(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

    private fun buildPrimaryAdminProfile(): UserProfile = UserProfile(
        id = PRIMARY_ADMIN_ID,
        fullName = "Administrator Utama Azril Clas",
        email = PRIMARY_ADMIN_EMAIL,
        username = PRIMARY_ADMIN_USERNAME,
        role = UserRole.ADMIN,
        status = AccountStatus.ACTIVE,
        mustChangePassword = false
    )

    private suspend fun clearStoredSessionKeys() {
        store.putString(KEY_SESSION_TOKEN, null)
        store.putString(KEY_SESSION_REFRESH_TOKEN, null)
        store.putString(KEY_SESSION_USER_ID, null)
        store.putString(KEY_SESSION_ROLE, null)
        store.putString(KEY_SESSION_PROFILE_JSON, null)
        store.putLong(KEY_SESSION_EXPIRES_AT, 0L)
    }

    private suspend fun verifyAdminJwtOrFallbackProfile(
        jwt: String,
        userId: String,
        fallbackEmail: String
    ): Boolean {
        val rpcResult = verifyAdminSessionWithSupabaseDetailed(jwt)
        if (rpcResult == true) return true
        if (rpcResult == null) {
            // `verify_admin_session` RPC is not deployed (HTTP 404); verify directly via profiles.role
            val base = supabaseUrl.trimEnd('/')
            val profile = fetchSupabaseProfile(base, jwt, userId, fallbackEmail)
            return profile?.role == UserRole.ADMIN && profile.status == AccountStatus.ACTIVE
        }
        return false
    }

    suspend fun restoreSession(nowMillis: Long = System.currentTimeMillis()): RestoredSession = withContext(Dispatchers.IO) {
        val rememberMe = store.getBoolean(KEY_REMEMBER_ME, true)
        val rememberedIdentifier = store.getString(KEY_REMEMBERED_IDENTIFIER).orEmpty()
        val token = store.getString(KEY_SESSION_TOKEN)
        val savedRefreshToken = store.getString(KEY_SESSION_REFRESH_TOKEN)
        val storedRole = store.getString(KEY_SESSION_ROLE)
        val expiresAt = store.getLong(KEY_SESSION_EXPIRES_AT, 0L)
        val accounts = accountsMutex.withLock { loadLocalAccounts() }
        val registeredStudents = accounts.filter { it.role == UserRole.STUDENT }.map { it.toStudentRecord() }
        val savedClasses = loadLocalClasses()
        val savedAssignments = loadLocalAssignments()
        val savedXpTransactions = loadLocalXpTransactions()
        val savedAttendanceRecords = loadLocalAttendanceRecords()
        val savedHistoricalAttendance = loadLocalHistoricalAttendanceRecords()
        val savedStreaks = loadLocalStudentStreaks()
        val savedStreakNotifications = loadLocalStreakNotifications()

        var sessionExpired = false
        var activeExpiresAt: Long? = null
        val restoredProfile = if (rememberMe && !token.isNullOrBlank()) {
            val isWindowExpired = expiresAt <= 0L || nowMillis >= expiresAt
            if (isWindowExpired) {
                sessionExpired = true
                activeAccessToken = null
                activeRefreshToken = null
                clearStoredSessionKeys()
                null
            } else {
                val userId = store.getString(KEY_SESSION_USER_ID)
                val fromLocal = accounts.find { it.id == userId }?.toUserProfile()
                val candidate = fromLocal ?: deserializeProfile(store.getString(KEY_SESSION_PROFILE_JSON))
                val roleMismatch = storedRole != null && candidate != null && !storedRole.equals(candidate.role.name, ignoreCase = true)

                if (candidate == null || roleMismatch || candidate.status == AccountStatus.INACTIVE) {
                    sessionExpired = true
                    activeAccessToken = null
                    activeRefreshToken = null
                    clearStoredSessionKeys()
                    null
                } else if (candidate.role == UserRole.ADMIN && isSupabaseConfigured() && !isLocalSessionToken(token)) {
                    // Re-verify Admin JWT & role with Supabase backend on session restore.
                    // Supports both `verify_admin_session` RPC AND fallback profile role check (when RPC is 404),
                    // plus automatic refresh via `refresh_token` if the 1-hour access token expired.
                    activeRefreshToken = savedRefreshToken
                    var verifiedToken: String? = if (
                        verifyAdminJwtOrFallbackProfile(token, candidate.id, candidate.email)
                    ) {
                        token
                    } else {
                        null
                    }
                    if (verifiedToken == null && !savedRefreshToken.isNullOrBlank()) {
                        val refreshed = refreshSupabaseAccessTokenIfNeeded(nowMillis)
                        if (!refreshed.isNullOrBlank() &&
                            verifyAdminJwtOrFallbackProfile(refreshed, candidate.id, candidate.email)
                        ) {
                            verifiedToken = refreshed
                        }
                    }
                    if (verifiedToken != null) {
                        activeAccessToken = verifiedToken
                        activeExpiresAt = store.getLong(KEY_SESSION_EXPIRES_AT, expiresAt).takeIf { it > 0L } ?: expiresAt
                        candidate
                    } else {
                        sessionExpired = true
                        activeAccessToken = null
                        activeRefreshToken = null
                        clearStoredSessionKeys()
                        null
                    }
                } else {
                    activeAccessToken = token
                    activeRefreshToken = savedRefreshToken
                    activeExpiresAt = expiresAt
                    candidate
                }
            }
        } else {
            if (!token.isNullOrBlank() && expiresAt > 0L && nowMillis >= expiresAt) {
                sessionExpired = true
            }
            activeAccessToken = null
            activeRefreshToken = null
            clearStoredSessionKeys()
            null
        }

        RestoredSession(
            profile = restoredProfile,
            rememberedIdentifier = rememberedIdentifier,
            rememberMeEnabled = rememberMe,
            registeredStudents = registeredStudents,
            savedClasses = savedClasses,
            savedAssignments = savedAssignments,
            savedXpTransactions = savedXpTransactions,
            savedAttendanceRecords = savedAttendanceRecords,
            savedHistoricalAttendanceRecords = savedHistoricalAttendance,
            savedStreaks = savedStreaks,
            savedStreakNotifications = savedStreakNotifications,
            isSupabaseConfigured = isSupabaseConfigured(),
            sessionExpired = sessionExpired,
            sessionExpiresAt = activeExpiresAt
        )
    }

    /**
     * Admin-only student provisioning.
     * Password is NEVER stored in plaintext — strictly hashed via PBKDF2WithHmacSHA256.
     */
    suspend fun adminCreateStudentAccount(
        studentId: String = UUID.randomUUID().toString(),
        fullName: String,
        nim: String,
        email: String,
        studyProgramId: String,
        studyProgramCode: String,
        classId: String?,
        className: String?,
        semester: Int,
        cohortYear: Int,
        accountStatus: AccountStatus,
        badgeStatus: StudentBadgeStatus,
        initialPassword: String,
        mustChangePassword: Boolean = initialPassword.trim().equals(nim.trim(), ignoreCase = true),
        targetClass: AcademicClass? = null
    ): AuthResult = withContext(Dispatchers.IO) {
        val cleanName = fullName.trim()
        val cleanNim = SupabaseStudentCrudService.sanitizeNim(nim)
        val cleanEmail = email.trim().lowercase(Locale.US)

        if (cleanName.length < 3) return@withContext AuthResult.Failure("Nama lengkap minimal 3 karakter.")
        if (cleanNim.length < 5) return@withContext AuthResult.Failure("NIM minimal 5 karakter.")
        if (cleanNim.contains(" ")) return@withContext AuthResult.Failure("NIM tidak boleh mengandung spasi.")
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) return@withContext AuthResult.Failure("Format email akademik tidak valid.")
        if (initialPassword.length < 6) return@withContext AuthResult.Failure("Password awal mahasiswa minimal 6 karakter.")

        var canonicalStudent = StudentRecord(
            id = studentId,
            fullName = cleanName,
            nim = cleanNim,
            email = cleanEmail,
            studyProgramId = studyProgramId,
            studyProgramName = studyProgramCode,
            classId = classId,
            className = className,
            semester = semester,
            cohortYear = cohortYear,
            status = accountStatus,
            badgeStatus = badgeStatus,
            mustChangePassword = mustChangePassword
        )

        // When Supabase is configured, ALWAYS execute authoritative INSERT in Supabase FIRST.
        // Never fake success or leave device-only ghost records when Supabase fails.
        if (isSupabaseConfigured()) {
            val jwt = getOrRefreshActiveSupabaseJwt()
            when (
                val remoteWrite = studentCrudService.createStudent(
                    jwt = jwt,
                    student = canonicalStudent,
                    initialPassword = initialPassword,
                    targetClass = targetClass
                )
            ) {
                is RemoteStudentWriteResult.Error -> return@withContext AuthResult.Failure(remoteWrite.message)
                is RemoteStudentWriteResult.Success -> {
                    canonicalStudent = remoteWrite.student
                }
            }
        }

        val record = accountsMutex.withLock {
            val accounts = loadLocalAccounts()
            if (!isSupabaseConfigured()) {
                if (accounts.any { it.nim.equals(cleanNim, ignoreCase = true) }) {
                    return@withContext AuthResult.Failure("NIM '$cleanNim' sudah terdaftar pada akun mahasiswa lain.")
                }
                if (accounts.any { it.email.equals(cleanEmail, ignoreCase = true) }) {
                    return@withContext AuthResult.Failure("Email '$cleanEmail' sudah digunakan oleh akun lain.")
                }
            }

            val created = StoredAccountRecord(
                id = canonicalStudent.id,
                fullName = canonicalStudent.fullName,
                nim = canonicalStudent.nim,
                email = canonicalStudent.email,
                username = canonicalStudent.nim,
                studyProgramId = canonicalStudent.studyProgramId,
                studyProgramCode = canonicalStudent.studyProgramName,
                classId = canonicalStudent.classId,
                className = canonicalStudent.className,
                semester = canonicalStudent.semester,
                cohortYear = canonicalStudent.cohortYear,
                accountStatus = canonicalStudent.status,
                badgeStatus = canonicalStudent.badgeStatus,
                mustChangePassword = canonicalStudent.mustChangePassword,
                role = UserRole.STUDENT,
                passwordHash = PasswordHasher.hashPassword(initialPassword, PasswordHasher.BULK_STUDENT_ITERATIONS),
                createdAt = nowIso()
            )
            val deduped = accounts.filterNot {
                it.id == created.id || it.nim.equals(created.nim, ignoreCase = true)
            }
            saveLocalAccounts(listOf(created) + deduped)
            created
        }
        AuthResult.Success(profile = record.toUserProfile(), usingSupabase = isSupabaseConfigured())
    }

    /**
     * Atomic batch provisioning for bulk Excel student import.
     * - Username = NIM, Initial Password = NIM (hashed with PBKDF2, NEVER stored in plaintext).
     * - Sets mustChangePassword = true so student MUST change password on first login.
     * - Strictly skips existing NIMs (never overwrites existing accounts).
     * - Hashes passwords before acquiring [accountsMutex] using [PasswordHasher.BULK_STUDENT_ITERATIONS]
     *   so large imports save to DataStore in <1s without blocking login.
     * - When Supabase is configured, syncs via Edge Function `admin-bulk-import-students` first,
     *   falling back to Supabase Auth `/auth/v1/signup` when the Edge Function isn't deployed.
     */
    suspend fun adminBulkImportStudents(
        newStudents: List<StudentRecord>,
        fileName: String,
        semesterKey: String,
        importHistoricalAttendance: Boolean,
        previewRowsForSync: List<ExcelImportPreviewRow> = emptyList()
    ): List<StudentRecord> = withContext(Dispatchers.IO) {
        val createdIso = nowIso()
        // Pre-compute salted PBKDF2 hashes outside mutex so large batches save immediately
        val prepared = mutableListOf<StoredAccountRecord>()
        val seenBatch = mutableSetOf<String>()
        newStudents.forEach { stu ->
            val cleanNim = stu.nim.trim()
            val nimKey = cleanNim.lowercase(Locale.US)
            if (cleanNim.length >= 5 && !cleanNim.contains(" ") && seenBatch.add(nimKey)) {
                val safeEmail = stu.email.trim().lowercase(Locale.US)
                    .ifBlank { "${cleanNim.lowercase(Locale.US)}@mahasiswa.azrilclas.ac.id" }
                prepared.add(
                    StoredAccountRecord(
                        id = stu.id,
                        fullName = stu.fullName.trim(),
                        nim = cleanNim,
                        email = safeEmail,
                        username = cleanNim,
                        studyProgramId = stu.studyProgramId,
                        studyProgramCode = stu.studyProgramName,
                        classId = stu.classId,
                        className = stu.className,
                        semester = stu.semester,
                        cohortYear = stu.cohortYear,
                        accountStatus = stu.status,
                        badgeStatus = stu.badgeStatus,
                        mustChangePassword = true,
                        role = UserRole.STUDENT,
                        passwordHash = PasswordHasher.hashPassword(cleanNim, PasswordHasher.BULK_STUDENT_ITERATIONS),
                        createdAt = createdIso
                    )
                )
            }
        }

        val createdStudents = mutableListOf<StudentRecord>()
        val createdAny = accountsMutex.withLock {
            val existingAccounts = loadLocalAccounts()
            val existingNimSet = existingAccounts.map { it.nim.trim().lowercase(Locale.US) }.toMutableSet()
            val toInsert = prepared.filter { existingNimSet.add(it.nim.trim().lowercase(Locale.US)) }
            if (toInsert.isNotEmpty()) {
                saveLocalAccounts(toInsert + existingAccounts)
                createdStudents.addAll(toInsert.map { it.toStudentRecord() })
                true
            } else {
                false
            }
        }

        if (createdAny && isSupabaseConfigured()) {
            val edgeOk = syncBulkImportToSupabaseEdgeFunctionIfConnected(
                fileName = fileName,
                semesterKey = semesterKey,
                importHistoricalAttendance = importHistoricalAttendance,
                rows = previewRowsForSync
            )
            if (!edgeOk) {
                provisionStudentsViaSupabaseSignupFallback(createdStudents)
            }
        }
        createdStudents
    }

    /**
     * Authoritative Supabase bulk Excel importer that returns an honest [SupabaseStudentSyncReport].
     * 1. Requires a live Supabase Admin JWT (automatically attempts refresh if expired).
     * 2. Tries Edge Function `admin-bulk-import-students` (`action = "bulk_import"`) first.
     * 3. If the Edge Function is not deployed (HTTP 404), persists each student via
     *    [SupabaseStudentCrudService.createStudent] (`classes` + `auth.users` + `public.profiles`)
     *    so data is genuinely stored in Supabase and visible from all other devices.
     */
    suspend fun adminBulkImportStudentsToSupabase(
        newStudents: List<StudentRecord>,
        classes: List<AcademicClass>,
        fileName: String,
        semesterKey: String,
        importHistoricalAttendance: Boolean,
        previewRowsForSync: List<ExcelImportPreviewRow>,
        onProgress: ((processed: Int, total: Int, statusText: String) -> Unit)? = null
    ): SupabaseStudentSyncReport = withContext(Dispatchers.IO) {
        if (newStudents.isEmpty()) {
            return@withContext SupabaseStudentSyncReport(totalProcessed = 0)
        }
        if (!isSupabaseConfigured()) {
            onProgress?.invoke(0, newStudents.size, "Menyimpan ${newStudents.size} mahasiswa ke penyimpanan lokal...")
            adminBulkImportStudents(
                newStudents = newStudents,
                fileName = fileName,
                semesterKey = semesterKey,
                importHistoricalAttendance = importHistoricalAttendance,
                previewRowsForSync = previewRowsForSync
            )
            onProgress?.invoke(newStudents.size, newStudents.size, "Import selesai (${newStudents.size} mahasiswa).")
            return@withContext SupabaseStudentSyncReport(
                totalProcessed = newStudents.size,
                localRepairedCount = newStudents.size
            )
        }

        val jwt = getOrRefreshActiveSupabaseJwt()
        if (jwt.isBlank()) {
            return@withContext SupabaseStudentSyncReport(
                totalProcessed = newStudents.size,
                diagnosticMessage = "Sesi Admin belum terhubung ke Supabase Auth. Klik 'Hubungkan Supabase' di halaman Data Mahasiswa atau login menggunakan akun Admin Supabase sebelum melakukan Import Excel."
            )
        }

        onProgress?.invoke(0, newStudents.size, "Mengirim ${newStudents.size} data mahasiswa ke Supabase...")

        // Primary path: Edge Function atomic batch import
        val edgeOk = syncBulkImportToSupabaseEdgeFunctionIfConnected(
            fileName = fileName,
            semesterKey = semesterKey,
            importHistoricalAttendance = importHistoricalAttendance,
            rows = previewRowsForSync
        )
        if (edgeOk) {
            onProgress?.invoke(newStudents.size, newStudents.size, "Seluruh ${newStudents.size} mahasiswa berhasil disimpan ke Supabase.")
            return@withContext SupabaseStudentSyncReport(
                totalProcessed = newStudents.size,
                syncedToSupabaseCount = newStudents.size,
                usedEdgeFunction = true
            )
        }

        // Direct Supabase CRUD path (`classes` + `auth.users` + `public.profiles`) when Edge Function is 404
        val classById = classes.associateBy { it.id }
        var syncedCount = 0
        var alreadyCount = 0
        var rateLimited = false
        var lastError: String? = null

        newStudents.forEachIndexed { idx, stu ->
            val cleanNim = SupabaseStudentCrudService.sanitizeNim(stu.nim)
            if (cleanNim.length < 5 || cleanNim.contains(" ")) return@forEachIndexed
            onProgress?.invoke(
                idx + 1,
                newStudents.size,
                "Menyimpan ke Supabase (${idx + 1}/${newStudents.size}): ${stu.fullName.take(26)}..."
            )
            val targetClass = stu.classId?.let { classById[it] }
            when (
                val res = studentCrudService.createStudent(
                    jwt = jwt,
                    student = stu.copy(nim = cleanNim, mustChangePassword = true),
                    initialPassword = cleanNim,
                    targetClass = targetClass
                )
            ) {
                is RemoteStudentWriteResult.Success -> syncedCount++
                is RemoteStudentWriteResult.Error -> {
                    val msg = res.message
                    val lower = msg.lowercase(Locale.US)
                    when {
                        lower.contains("sudah terdaftar") || lower.contains("duplikat") -> alreadyCount++
                        lower.contains("rate limit") -> {
                            rateLimited = true
                            lastError = msg
                            return@withContext SupabaseStudentSyncReport(
                                totalProcessed = newStudents.size,
                                syncedToSupabaseCount = syncedCount,
                                alreadyInSupabaseCount = alreadyCount,
                                usedEdgeFunction = false,
                                rateLimited = true,
                                diagnosticMessage = lastError
                            )
                        }
                        lower.contains("401") || lower.contains("403") || lower.contains("42p17") -> {
                            lastError = msg
                            return@withContext SupabaseStudentSyncReport(
                                totalProcessed = newStudents.size,
                                syncedToSupabaseCount = syncedCount,
                                alreadyInSupabaseCount = alreadyCount,
                                usedEdgeFunction = false,
                                rateLimited = false,
                                diagnosticMessage = lastError
                            )
                        }
                        else -> lastError = msg
                    }
                }
            }
        }

        SupabaseStudentSyncReport(
            totalProcessed = newStudents.size,
            syncedToSupabaseCount = syncedCount,
            alreadyInSupabaseCount = alreadyCount,
            usedEdgeFunction = false,
            rateLimited = rateLimited,
            diagnosticMessage = lastError
        )
    }

    /**
     * Repairs any local missing credentials for students visible in the Admin dashboard
     * and synchronizes all active student accounts to Supabase Auth so students can log in
     * from any phone using their NIM and initial password (NIM).
     */
    suspend fun syncStudentsToSupabase(
        students: List<StudentRecord>,
        semesterKey: String
    ): SupabaseStudentSyncReport = withContext(Dispatchers.IO) {
        if (students.isEmpty()) {
            return@withContext SupabaseStudentSyncReport(
                diagnosticMessage = "Belum ada data mahasiswa untuk disinkronkan."
            )
        }

        val createdIso = nowIso()
        var repairedCount = 0
        val existingBefore = accountsMutex.withLock { loadLocalAccounts() }
        val existingNimSet = existingBefore.map { it.nim.trim().lowercase(Locale.US) }.toMutableSet()
        val missingLocally = students.mapNotNull { stu ->
            val cleanNim = stu.nim.trim()
            val nimKey = cleanNim.lowercase(Locale.US)
            if (cleanNim.length >= 5 && !cleanNim.contains(" ") && existingNimSet.add(nimKey)) {
                val safeEmail = stu.email.trim().lowercase(Locale.US)
                    .ifBlank { "${cleanNim.lowercase(Locale.US)}@mahasiswa.azrilclas.ac.id" }
                StoredAccountRecord(
                    id = stu.id,
                    fullName = stu.fullName.trim(),
                    nim = cleanNim,
                    email = safeEmail,
                    username = cleanNim,
                    studyProgramId = stu.studyProgramId,
                    studyProgramCode = stu.studyProgramName,
                    classId = stu.classId,
                    className = stu.className,
                    semester = stu.semester,
                    cohortYear = stu.cohortYear,
                    accountStatus = stu.status,
                    badgeStatus = stu.badgeStatus,
                    mustChangePassword = stu.mustChangePassword,
                    role = UserRole.STUDENT,
                    passwordHash = PasswordHasher.hashPassword(cleanNim, PasswordHasher.BULK_STUDENT_ITERATIONS),
                    createdAt = createdIso
                )
            } else null
        }

        if (missingLocally.isNotEmpty()) {
            accountsMutex.withLock {
                val fresh = loadLocalAccounts()
                val freshNims = fresh.map { it.nim.trim().lowercase(Locale.US) }.toMutableSet()
                val uniqueMissing = missingLocally.filter { freshNims.add(it.nim.trim().lowercase(Locale.US)) }
                if (uniqueMissing.isNotEmpty()) {
                    saveLocalAccounts(uniqueMissing + fresh)
                    repairedCount = uniqueMissing.size
                }
            }
        }

        if (!isSupabaseConfigured()) {
            return@withContext SupabaseStudentSyncReport(
                totalProcessed = students.size,
                localRepairedCount = repairedCount,
                diagnosticMessage = "Akun lokal tersinkron ($repairedCount diperbaiki). Supabase belum dikonfigurasi."
            )
        }

        val previewRows = students.mapIndexed { idx, stu ->
            val resolvedClassName = stu.className ?: "${stu.studyProgramName}-${stu.semester}"
            ExcelImportPreviewRow(
                sheetName = resolvedClassName,
                rowNumber = idx + 1,
                studentNumber = (idx + 1).toString(),
                nim = stu.nim,
                fullName = stu.fullName,
                studyProgramId = stu.studyProgramId,
                studyProgramCode = stu.studyProgramName,
                className = resolvedClassName,
                classLabel = resolvedClassName.substringAfter('-', stu.semester.toString()),
                semester = stu.semester,
                colorFlag = if (stu.badgeStatus == StudentBadgeStatus.AFK) {
                    com.example.ui.model.ExcelRowColorFlag.RED_AFK
                } else {
                    com.example.ui.model.ExcelRowColorFlag.NONE
                },
                validationStatus = if (stu.badgeStatus == StudentBadgeStatus.AFK) {
                    com.example.ui.model.ExcelImportValidationStatus.VALID_AFK
                } else {
                    com.example.ui.model.ExcelImportValidationStatus.VALID_NORMAL
                }
            )
        }

        val edgeOk = syncBulkImportToSupabaseEdgeFunctionIfConnected(
            fileName = "manual_admin_sync.xlsx",
            semesterKey = semesterKey,
            importHistoricalAttendance = false,
            rows = previewRows
        )
        if (edgeOk) {
            return@withContext SupabaseStudentSyncReport(
                totalProcessed = students.size,
                localRepairedCount = repairedCount,
                syncedToSupabaseCount = students.size,
                usedEdgeFunction = true
            )
        }

        val fallbackReport = provisionStudentsViaSupabaseSignupFallback(students)
        fallbackReport.copy(
            totalProcessed = students.size,
            localRepairedCount = repairedCount
        )
    }

    /**
     * Mandatory first-login password change for students whose initial password equals their NIM.
     */
    suspend fun completeFirstLoginPasswordChange(
        studentId: String,
        newPassword: String
    ): AuthResult = withContext(Dispatchers.IO) {
        val cleanPw = newPassword.trim()
        if (cleanPw.length < 6) {
            return@withContext AuthResult.Failure("Kata sandi baru minimal 6 karakter.")
        }
        val accounts = loadLocalAccounts()
        val target = accounts.find { it.id == studentId }
            ?: return@withContext AuthResult.Failure("Akun mahasiswa tidak ditemukan.")
        if (cleanPw.equals(target.nim.trim(), ignoreCase = true)) {
            return@withContext AuthResult.Failure("Kata sandi baru tidak boleh sama dengan NIM Anda.")
        }

        if (isSupabaseConfigured()) {
            val jwt = getActiveSupabaseJwt()
            if (!jwt.isNullOrBlank()) {
                val base = supabaseUrl.trimEnd('/')
                val pwUpdateErr = runCatching {
                    val payload = JSONObject().apply { put("password", cleanPw) }
                    val req = Request.Builder()
                        .url("$base/auth/v1/user")
                        .put(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                        .addHeader("apikey", supabaseAnonKey.trim())
                        .addHeader("Authorization", "Bearer $jwt")
                        .addHeader("Content-Type", "application/json")
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) "Gagal memperbarui kata sandi di Supabase Auth." else null
                    }
                }.getOrElse { it.localizedMessage }
                if (pwUpdateErr != null) {
                    return@withContext AuthResult.Failure(pwUpdateErr)
                }
                runCatching {
                    val rpcReq = Request.Builder()
                        .url("$base/rest/v1/rpc/complete_first_login_password_change")
                        .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                        .addHeader("apikey", supabaseAnonKey.trim())
                        .addHeader("Authorization", "Bearer $jwt")
                        .addHeader("Content-Type", "application/json")
                        .build()
                    httpClient.newCall(rpcReq).execute().close()
                }
            }
        }

        val updatedRecord = accountsMutex.withLock {
            val freshAccounts = loadLocalAccounts()
            val freshTarget = freshAccounts.find { it.id == target.id } ?: target
            val nextRec = freshTarget.copy(
                passwordHash = PasswordHasher.hashPassword(cleanPw),
                mustChangePassword = false
            )
            saveLocalAccounts(freshAccounts.map { if (it.id == target.id) nextRec else it })
            nextRec
        }
        val updatedProfile = updatedRecord.toUserProfile()
        if (store.getString(KEY_SESSION_USER_ID) == target.id) {
            store.putString(KEY_SESSION_PROFILE_JSON, serializeProfile(updatedProfile))
        }
        AuthResult.Success(profile = updatedProfile, usingSupabase = isSupabaseConfigured())
    }

    suspend fun adminUpdateStudentAccount(
        studentId: String,
        fullName: String,
        nim: String,
        email: String,
        studyProgramId: String,
        studyProgramCode: String,
        classId: String?,
        className: String?,
        semester: Int,
        cohortYear: Int,
        accountStatus: AccountStatus,
        badgeStatus: StudentBadgeStatus,
        previousNim: String? = null,
        targetClass: AcademicClass? = null
    ): AuthResult = withContext(Dispatchers.IO) {
        val cleanName = fullName.trim()
        val cleanNim = SupabaseStudentCrudService.sanitizeNim(nim)
        val cleanEmail = email.trim().lowercase(Locale.US)
        if (cleanName.length < 3) return@withContext AuthResult.Failure("Nama lengkap minimal 3 karakter.")
        if (cleanNim.length < 5) return@withContext AuthResult.Failure("NIM minimal 5 karakter.")
        if (cleanNim.contains(" ")) return@withContext AuthResult.Failure("NIM tidak boleh mengandung spasi.")
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) return@withContext AuthResult.Failure("Format email tidak valid.")

        var canonicalStudent = StudentRecord(
            id = studentId,
            fullName = cleanName,
            nim = cleanNim,
            email = cleanEmail,
            studyProgramId = studyProgramId,
            studyProgramName = studyProgramCode,
            classId = classId,
            className = className,
            semester = semester,
            cohortYear = cohortYear,
            status = accountStatus,
            badgeStatus = badgeStatus
        )

        // When Supabase is configured, ALWAYS execute authoritative UPDATE in Supabase FIRST.
        if (isSupabaseConfigured()) {
            val jwt = getOrRefreshActiveSupabaseJwt()
            when (
                val remoteUpdate = studentCrudService.updateStudent(
                    jwt = jwt,
                    student = canonicalStudent,
                    previousNim = previousNim,
                    targetClass = targetClass
                )
            ) {
                is RemoteStudentWriteResult.Error -> return@withContext AuthResult.Failure(remoteUpdate.message)
                is RemoteStudentWriteResult.Success -> {
                    canonicalStudent = remoteUpdate.student
                }
            }
        }

        val (_, updated) = accountsMutex.withLock {
            val accounts = loadLocalAccounts()
            if (!isSupabaseConfigured()) {
                if (accounts.any { it.id != studentId && it.nim.equals(cleanNim, ignoreCase = true) }) {
                    return@withContext AuthResult.Failure("NIM '$cleanNim' sudah digunakan oleh mahasiswa lain.")
                }
                if (accounts.any { it.id != studentId && it.email.equals(cleanEmail, ignoreCase = true) }) {
                    return@withContext AuthResult.Failure("Email '$cleanEmail' sudah digunakan oleh akun lain.")
                }
            }

            val found = accounts.find { it.id == studentId || it.id == canonicalStudent.id }
            val nextRec = if (found != null) {
                found.copy(
                    id = canonicalStudent.id,
                    fullName = canonicalStudent.fullName,
                    nim = canonicalStudent.nim,
                    email = canonicalStudent.email,
                    username = canonicalStudent.nim,
                    studyProgramId = canonicalStudent.studyProgramId,
                    studyProgramCode = canonicalStudent.studyProgramName,
                    classId = canonicalStudent.classId,
                    className = canonicalStudent.className,
                    semester = canonicalStudent.semester,
                    cohortYear = canonicalStudent.cohortYear,
                    accountStatus = canonicalStudent.status,
                    badgeStatus = canonicalStudent.badgeStatus
                )
            } else {
                StoredAccountRecord(
                    id = canonicalStudent.id,
                    fullName = canonicalStudent.fullName,
                    nim = canonicalStudent.nim,
                    email = canonicalStudent.email,
                    username = canonicalStudent.nim,
                    studyProgramId = canonicalStudent.studyProgramId,
                    studyProgramCode = canonicalStudent.studyProgramName,
                    classId = canonicalStudent.classId,
                    className = canonicalStudent.className,
                    semester = canonicalStudent.semester,
                    cohortYear = canonicalStudent.cohortYear,
                    accountStatus = canonicalStudent.status,
                    badgeStatus = canonicalStudent.badgeStatus,
                    role = UserRole.STUDENT,
                    passwordHash = PasswordHasher.hashPassword(canonicalStudent.nim, PasswordHasher.BULK_STUDENT_ITERATIONS),
                    createdAt = nowIso()
                )
            }
            val nextList = if (found != null) {
                accounts.map { if (it.id == found.id) nextRec else it }
            } else {
                listOf(nextRec) + accounts
            }
            saveLocalAccounts(nextList)
            found to nextRec
        }
        AuthResult.Success(profile = updated.toUserProfile(), usingSupabase = isSupabaseConfigured())
    }

    suspend fun adminUpdateStudentBadgeStatusResult(
        studentId: String,
        studentNim: String? = null,
        badgeStatus: StudentBadgeStatus
    ): String? = withContext(Dispatchers.IO) {
        if (isSupabaseConfigured()) {
            val jwt = getOrRefreshActiveSupabaseJwt()
            when (val res = studentCrudService.updateStudentBadge(jwt, studentId, studentNim, badgeStatus)) {
                is RemoteDeleteResult.Error -> return@withContext res.message
                RemoteDeleteResult.Success -> Unit
            }
        }
        accountsMutex.withLock {
            val accounts = loadLocalAccounts()
            val updated = accounts.map { acc ->
                if (acc.id == studentId) acc.copy(badgeStatus = badgeStatus) else acc
            }
            saveLocalAccounts(updated)
        }
        null
    }

    suspend fun adminUpdateStudentBadgeStatus(
        studentId: String,
        badgeStatus: StudentBadgeStatus
    ): Boolean = adminUpdateStudentBadgeStatusResult(studentId, null, badgeStatus) == null

    suspend fun adminResetStudentPassword(
        studentId: String,
        newPassword: String,
        requirePasswordChange: Boolean = true
    ): String? = withContext(Dispatchers.IO) {
        if (newPassword.length < 6) return@withContext "Kata sandi baru minimal 6 karakter."
        if (isSupabaseConfigured()) {
            val edgeOk = syncAdminEdgeActionIfConnected(
                JSONObject().apply {
                    put("action", "reset_password")
                    put("studentId", studentId)
                    put("newPassword", newPassword)
                    put("mustChangePassword", requirePasswordChange)
                }
            )
            if (!edgeOk) {
                return@withContext "Reset password di Supabase Auth membutuhkan Edge Function admin-bulk-import-students yang aktif dan sesi Admin Supabase yang valid."
            }
        }
        accountsMutex.withLock {
            val accounts = loadLocalAccounts()
            val target = accounts.find { it.id == studentId }
                ?: return@withContext if (isSupabaseConfigured()) null else "Akun mahasiswa tidak ditemukan."
            val newHash = PasswordHasher.hashPassword(newPassword)
            val updated = accounts.map { acc ->
                if (acc.id == target.id) acc.copy(passwordHash = newHash, mustChangePassword = requirePasswordChange) else acc
            }
            saveLocalAccounts(updated)
        }
        null
    }

    suspend fun adminDeleteStudentAccount(
        studentId: String,
        studentNim: String? = null
    ): String? = withContext(Dispatchers.IO) {
        if (isSupabaseConfigured()) {
            val jwt = getOrRefreshActiveSupabaseJwt()
            when (val res = studentCrudService.deleteStudent(jwt, studentId, studentNim)) {
                is RemoteDeleteResult.Error -> return@withContext res.message
                RemoteDeleteResult.Success -> Unit
            }
        }
        accountsMutex.withLock {
            val accounts = loadLocalAccounts().filterNot { it.id == studentId }
            saveLocalAccounts(accounts)
        }
        null
    }

    /**
     * Dedicated Administrator login (Username + Password).
     * - When Supabase is configured: authenticates strictly via Supabase Edge Function
     *   `admin-auth-login` (or `resolve_admin_login_email` + GoTrue + `verify_admin_session`),
     *   verifying `role = 'admin'` on the backend. NEVER falls back to local verifier.
     * - When Supabase is not configured: validates against salted PBKDF2-HMAC-SHA256 verifier
     *   (never plaintext in source code or storage).
     * - Student accounts are ALWAYS rejected on this portal.
     */
    suspend fun loginAdmin(
        username: String,
        password: String,
        rememberMe: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): AuthResult = withContext(Dispatchers.IO) {
        val cleanUsername = username.trim()
        if (cleanUsername.isEmpty()) return@withContext AuthResult.Failure("Username administrator wajib diisi.")
        if (password.isEmpty()) return@withContext AuthResult.Failure("Password administrator wajib diisi.")

        if (isSupabaseConfigured()) {
            when (
                val remoteOutcome = loginAdminViaSupabase(
                    username = cleanUsername,
                    password = password,
                    rememberMe = rememberMe,
                    nowMillis = nowMillis
                )
            ) {
                is SupabaseAdminLoginOutcome.Authenticated -> return@withContext remoteOutcome.result
                is SupabaseAdminLoginOutcome.Rejected -> return@withContext remoteOutcome.result
                SupabaseAdminLoginOutcome.BackendNotProvisioned -> {
                    // Supabase project is connected, but migration 006 / Edge Function `admin-auth-login`
                    // has not been deployed or primary admin has not been bootstrapped yet.
                    // Fall through to salted PBKDF2-HMAC-SHA256 verifier (still strictly rejects wrong
                    // passwords and all student accounts).
                }
            }
        }

        // Offline/local fallback: reject student accounts attempting Admin portal login
        val localAccounts = accountsMutex.withLock { loadLocalAccounts() }
        val matchingStudent = localAccounts.find {
            it.role == UserRole.STUDENT && (
                it.nim.equals(cleanUsername, ignoreCase = true) ||
                    it.username.equals(cleanUsername, ignoreCase = true) ||
                    it.email.equals(cleanUsername, ignoreCase = true)
                )
        }
        if (matchingStudent != null) {
            return@withContext AuthResult.Failure("Akses ditolak: akun mahasiswa tidak memiliki hak akses Administrator.")
        }

        // Verify primary admin username (exact case-sensitive username) & salted PBKDF2 verifier
        val isPrimaryAdmin = cleanUsername == PRIMARY_ADMIN_USERNAME &&
            PasswordHasher.verifyPassword(password, PRIMARY_ADMIN_VERIFIER_HASH)

        val localAdmin = if (!isPrimaryAdmin) {
            localAccounts.find {
                it.role == UserRole.ADMIN &&
                    it.username.equals(cleanUsername, ignoreCase = true) &&
                    PasswordHasher.verifyPassword(password, it.passwordHash)
            }
        } else null

        if (!isPrimaryAdmin && localAdmin == null) {
            return@withContext AuthResult.Failure("Username atau password administrator tidak valid.")
        }
        if (localAdmin != null && localAdmin.accountStatus == AccountStatus.INACTIVE) {
            return@withContext AuthResult.Failure("Akun administrator sedang dinonaktifkan.")
        }

        val adminProfile = localAdmin?.toUserProfile() ?: buildPrimaryAdminProfile()
        val expiresAt = nowMillis + ADMIN_SESSION_DURATION_MS
        persistSessionState(
            profile = adminProfile,
            token = "azr_admin_sess_${UUID.randomUUID()}",
            identifier = adminProfile.username ?: cleanUsername,
            rememberMe = rememberMe,
            expiresAtMillis = expiresAt
        )
        AuthResult.Success(
            profile = adminProfile,
            usingSupabase = false,
            sessionExpiresAt = expiresAt
        )
    }

    /**
     * Student login uses NIM (unique) + Password provisioned by Admin.
     * Strictly separates Student sessions from Admin accounts.
     */
    suspend fun login(
        identifier: String,
        password: String,
        rememberMe: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): AuthResult = withContext(Dispatchers.IO) {
        val cleanId = SupabaseStudentCrudService.sanitizeIdentifier(identifier)
        val cleanLower = cleanId.lowercase(Locale.US)
        if (cleanId.isEmpty()) return@withContext AuthResult.Failure("NIM wajib diisi.")
        if (password.isEmpty()) return@withContext AuthResult.Failure("Kata sandi wajib diisi.")

        // Strictly prevent Administrator accounts from logging in via the Student login screen
        if (cleanId.equals(PRIMARY_ADMIN_USERNAME, ignoreCase = true) ||
            cleanLower == PRIMARY_ADMIN_EMAIL.lowercase(Locale.US)
        ) {
            return@withContext AuthResult.Failure("Akun Administrator harus masuk melalui halaman Login Admin.")
        }

        if (isSupabaseConfigured()) {
            val remoteResult = loginViaSupabase(cleanId, password, rememberMe, nowMillis)
            if (remoteResult is AuthResult.Success) {
                if (remoteResult.profile.role == UserRole.ADMIN) {
                    logout()
                    return@withContext AuthResult.Failure("Akun Administrator harus masuk melalui halaman Login Admin.")
                }
                return@withContext remoteResult
            }
            val localAccounts = accountsMutex.withLock { loadLocalAccounts() }
            val compactId = SupabaseStudentCrudService.sanitizeNim(cleanId)
            val localMatch = localAccounts.find {
                SupabaseStudentCrudService.sanitizeNim(it.nim).equals(compactId, ignoreCase = true) ||
                    it.username.trim().equals(cleanLower, ignoreCase = true) ||
                    it.email.trim().equals(cleanLower, ignoreCase = true)
            }
            if (localMatch == null) return@withContext remoteResult
        }

        val accounts = accountsMutex.withLock { loadLocalAccounts() }
        val compactId = SupabaseStudentCrudService.sanitizeNim(cleanId)
        val matched = accounts.find {
            SupabaseStudentCrudService.sanitizeNim(it.nim).equals(compactId, ignoreCase = true) ||
                it.username.trim().equals(cleanLower, ignoreCase = true) ||
                it.email.trim().equals(cleanLower, ignoreCase = true)
        } ?: return@withContext AuthResult.Failure("NIM tidak terdaftar. Hubungi Admin untuk pembuatan akun mahasiswa.")

        if (matched.role == UserRole.ADMIN) {
            return@withContext AuthResult.Failure("Akun Administrator harus masuk melalui halaman Login Admin.")
        }

        if (matched.accountStatus == AccountStatus.INACTIVE) {
            return@withContext AuthResult.Failure("Akun mahasiswa ini sedang dinonaktifkan oleh Admin.")
        }

        if (!PasswordHasher.verifyPassword(password, matched.passwordHash)) {
            return@withContext AuthResult.Failure("Kata sandi salah. Hubungi Admin jika Anda lupa kata sandi.")
        }

        val profile = matched.toUserProfile()
        val expiresAt = nowMillis + REMEMBER_SESSION_DURATION_MS
        persistSessionState(
            profile = profile,
            token = "azr_sess_${UUID.randomUUID()}",
            identifier = matched.nim.ifBlank { cleanId },
            rememberMe = rememberMe,
            expiresAtMillis = expiresAt
        )
        AuthResult.Success(
            profile = profile,
            usingSupabase = false,
            sessionExpiresAt = expiresAt
        )
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        val token = getActiveSupabaseJwt()
        val storedRole = store.getString(KEY_SESSION_ROLE)
        activeAccessToken = null
        activeRefreshToken = null
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
        clearStoredSessionKeys()
        // Never leave admin credentials in remembered student identifier
        if (storedRole == UserRole.ADMIN.name || !store.getBoolean(KEY_REMEMBER_ME, false)) {
            store.putString(KEY_REMEMBERED_IDENTIFIER, null)
        }
    }

    suspend fun getRegisteredStudents(): List<StudentRecord> = withContext(Dispatchers.IO) {
        loadLocalAccounts().filter { it.role == UserRole.STUDENT }.map { it.toStudentRecord() }
    }

    suspend fun saveClasses(classes: List<AcademicClass>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        classes.forEach { cls ->
            arr.put(JSONObject().apply {
                put("id", cls.id)
                put("name", cls.name)
                put("study_program_id", cls.studyProgramId)
                put("study_program_name", cls.studyProgramName)
                put("semester", cls.semester)
                put("academic_year", cls.academicYear)
                put("cohort_year", cls.cohortYear)
                put("description", cls.description)
                put("is_active", cls.isActive)
                put("member_ids", JSONArray(cls.memberIds))
            })
        }
        store.putString(KEY_CLASSES_JSON, arr.toString())
    }

    suspend fun saveAssignments(assignments: List<Assignment>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        assignments.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id)
                put("title", a.title)
                put("description", a.description)
                put("instructions", a.instructions)
                put("class_id", a.classId)
                put("class_name", a.className)
                put("target_type", a.targetType.dbValue)
                put("target_class_ids", JSONArray(a.targetClassIds))
                put("target_class_names", JSONArray(a.targetClassNames))
                put("target_study_program_ids", JSONArray(a.targetStudyProgramIds))
                put("target_study_program_codes", JSONArray(a.targetStudyProgramCodes))
                put("course_category", a.courseCategory)
                put("created_at", a.createdAt)
                put("deadline", a.deadline)
                if (a.deadlineEpochMillis != null) put("deadline_epoch_millis", a.deadlineEpochMillis)
                put("allowed_formats", JSONArray(a.allowedFormats))
                put("max_file_size_mb", a.maxFileSizeMb)
                put("submission_method", a.submissionMethod.name)
                put("file1_name", a.file1Name ?: "")
                put("file1_url", a.file1Url ?: "")
                put("file2_name", a.file2Name ?: "")
                put("file2_url", a.file2Url ?: "")
                put("material_attachment", a.materialAttachment ?: "")
                put("is_active", a.isActive)
            })
        }
        store.putString(KEY_ASSIGNMENTS_JSON, arr.toString())
    }

    suspend fun saveXpTransactions(transactions: List<RankingPointTransaction>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        transactions.forEach { tx ->
            arr.put(JSONObject().apply {
                put("id", tx.id)
                put("student_id", tx.studentId)
                put("source_type", tx.sourceType.name)
                put("source_id", tx.sourceId)
                put("points", tx.points)
                put("description", tx.description)
                put("created_at", tx.createdAt)
                put("updated_at", tx.updatedAt)
                put("timestamp_millis", tx.timestampMillis)
                put("xp_category", tx.xpCategory.name)
                put("activity_title", tx.activityTitle)
                put("semester_key", tx.semesterKey)
                put("admin_id", tx.adminId ?: "")
                put("admin_name", tx.adminName ?: "")
            })
        }
        store.putString(KEY_XP_TRANSACTIONS_JSON, arr.toString())
    }

    suspend fun saveAttendanceRecords(records: List<AttendanceRecord>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("session_id", r.sessionId)
                put("meeting_title", r.meetingTitle)
                put("class_name", r.className)
                put("student_id", r.studentId)
                put("student_name", r.studentName)
                put("student_nim", r.studentNim)
                put("date_label", r.dateLabel)
                put("recorded_at", r.recordedAt)
                put("status", r.status.name)
                put("correction_reason", r.correctionReason ?: "")
                put("semester_key", r.semesterKey)
                put("timestamp_millis", r.timestampMillis)
            })
        }
        store.putString(KEY_ATTENDANCE_RECORDS_JSON, arr.toString())
    }

    suspend fun saveHistoricalAttendanceRecords(records: List<HistoricalAttendanceRecord>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("student_id", r.studentId)
                put("student_nim", r.studentNim)
                put("student_name", r.studentName)
                put("class_id", r.classId ?: "")
                put("class_name", r.className)
                put("study_program_code", r.studyProgramCode)
                put("week_number", r.weekNumber)
                put("week_label", r.weekLabel)
                put("raw_status", r.rawStatus)
                put("source_sheet", r.sourceSheet)
                put("semester_key", r.semesterKey)
                put("imported_at", r.importedAt)
                put("xp_calculated", r.xpCalculated)
            })
        }
        store.putString(KEY_HISTORICAL_ATTENDANCE_JSON, arr.toString())
    }

    private suspend fun syncBulkImportToSupabaseEdgeFunctionIfConnected(
        fileName: String,
        semesterKey: String,
        importHistoricalAttendance: Boolean,
        rows: List<ExcelImportPreviewRow>
    ): Boolean {
        if (!isSupabaseConfigured() || rows.isEmpty()) return false
        val rowsArr = JSONArray()
        rows.forEach { r ->
            rowsArr.put(JSONObject().apply {
                put("sheetName", r.sheetName)
                put("rowNumber", r.rowNumber)
                put("nim", r.nim)
                put("fullName", r.fullName)
                put("studyProgramCode", r.studyProgramCode)
                put("className", r.className)
                put("semester", r.semester)
                put("colorFlag", r.colorFlag.name)
                if (importHistoricalAttendance && r.weeklyAttendance.isNotEmpty()) {
                    val wkObj = JSONObject()
                    r.weeklyAttendance.forEach { (wk, raw) -> wkObj.put(wk.toString(), raw) }
                    put("weeklyAttendance", wkObj)
                }
            })
        }
        return syncAdminEdgeActionIfConnected(
            JSONObject().apply {
                put("action", "bulk_import")
                put("fileName", fileName)
                put("semesterKey", semesterKey)
                put("importHistoricalAttendance", importHistoricalAttendance)
                put("rows", rowsArr)
            }
        )
    }

    private suspend fun syncAdminEdgeActionIfConnected(payload: JSONObject): Boolean {
        if (!isSupabaseConfigured()) return false
        val jwt = getActiveSupabaseJwt() ?: return false
        return runCatching {
            val base = supabaseUrl.trimEnd('/')
            val req = Request.Builder()
                .url("$base/functions/v1/admin-bulk-import-students")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Authorization", "Bearer $jwt")
                .addHeader("Content-Type", "application/json")
                .build()
            httpClient.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private suspend fun provisionSingleStudentToSupabase(
        student: StudentRecord,
        initialPassword: String
    ): Boolean {
        if (!isSupabaseConfigured()) return false
        val resolvedClassName = student.className ?: "${student.studyProgramName}-${student.semester}"
        val row = ExcelImportPreviewRow(
            sheetName = resolvedClassName,
            rowNumber = 1,
            studentNumber = "1",
            nim = student.nim,
            fullName = student.fullName,
            studyProgramId = student.studyProgramId,
            studyProgramCode = student.studyProgramName,
            className = resolvedClassName,
            classLabel = resolvedClassName.substringAfter('-', student.semester.toString()),
            semester = student.semester,
            colorFlag = if (student.badgeStatus == StudentBadgeStatus.AFK) {
                com.example.ui.model.ExcelRowColorFlag.RED_AFK
            } else {
                com.example.ui.model.ExcelRowColorFlag.NONE
            },
            validationStatus = if (student.badgeStatus == StudentBadgeStatus.AFK) {
                com.example.ui.model.ExcelImportValidationStatus.VALID_AFK
            } else {
                com.example.ui.model.ExcelImportValidationStatus.VALID_NORMAL
            }
        )
        if (initialPassword.trim().equals(student.nim.trim(), ignoreCase = true)) {
            val edgeOk = syncBulkImportToSupabaseEdgeFunctionIfConnected(
                fileName = "single_student_create.xlsx",
                semesterKey = "2026/2027-Ganjil",
                importHistoricalAttendance = false,
                rows = listOf(row)
            )
            if (edgeOk) return true
        }
        val report = provisionStudentsViaSupabaseSignupFallback(listOf(student), customPassword = initialPassword)
        return report.syncedToSupabaseCount > 0 || report.alreadyInSupabaseCount > 0
    }

    private fun provisionStudentsViaSupabaseSignupFallback(
        students: List<StudentRecord>,
        customPassword: String? = null
    ): SupabaseStudentSyncReport {
        if (!isSupabaseConfigured() || students.isEmpty()) {
            return SupabaseStudentSyncReport(totalProcessed = students.size)
        }
        val base = supabaseUrl.trimEnd('/')
        var syncedCount = 0
        var alreadyRegistered = 0
        var rateLimited = false
        var emailConfirmPending = false
        var lastError: String? = null

        for (stu in students) {
            val cleanNim = stu.nim.trim()
            if (cleanNim.length < 5 || cleanNim.contains(" ")) continue
            val password = (customPassword ?: cleanNim).trim()
            if (password.length < 6) continue
            val email = stu.email.trim().lowercase(Locale.US)
                .takeIf { it.contains("@") && it.contains(".") }
                ?: "${cleanNim.lowercase(Locale.US)}@mahasiswa.azrilclas.ac.id"

            val meta = JSONObject().apply {
                put("full_name", stu.fullName.trim())
                put("nim", cleanNim)
                put("username", cleanNim)
                put("study_program_code", stu.studyProgramName)
                put("study_program_id", stu.studyProgramId)
                put("class_name", stu.className ?: "")
                put("semester", stu.semester)
                put("cohort_year", stu.cohortYear)
                put("must_change_password", password.equals(cleanNim, ignoreCase = true))
            }
            val payload = JSONObject().apply {
                put("email", email)
                put("password", password)
                put("data", meta)
            }
            val req = Request.Builder()
                .url("$base/auth/v1/signup")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Content-Type", "application/json")
                .build()

            val stopBatch = runCatching {
                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    val lowerBody = body.lowercase(Locale.US)
                    when {
                        resp.isSuccessful -> {
                            val parsed = runCatching { JSONObject(body) }.getOrNull()
                            val hasSession = parsed?.optString("access_token").orEmpty().isNotBlank()
                            val userObj = parsed?.optJSONObject("user") ?: parsed
                            val identitiesEmpty = userObj?.optJSONArray("identities")?.length() == 0
                            if (identitiesEmpty) {
                                alreadyRegistered++
                            } else {
                                syncedCount++
                                if (!hasSession && userObj?.optString("email_confirmed_at").isNullOrBlank()) {
                                    emailConfirmPending = true
                                }
                            }
                            false
                        }
                        resp.code == 429 || lowerBody.contains("rate limit") -> {
                            rateLimited = true
                            lastError = "Batas pembuatan akun Supabase Auth tercapai (rate limit). Deploy Edge Function admin-bulk-import-students untuk impor massal tanpa batas."
                            true
                        }
                        lowerBody.contains("already registered") || lowerBody.contains("user already exists") -> {
                            alreadyRegistered++
                            false
                        }
                        else -> {
                            val errMsg = runCatching {
                                JSONObject(body).let { j ->
                                    j.optString("msg").ifBlank { j.optString("error_description") }
                                        .ifBlank { j.optString("message") }
                                }
                            }.getOrNull().orEmpty()
                            lastError = errMsg.ifBlank { "HTTP ${resp.code} saat menyinkronkan akun ke Supabase Auth." }
                            // Stop early if signup is globally disabled or DB trigger fails on schema mismatch
                            resp.code == 400 || resp.code == 403 || resp.code == 500
                        }
                    }
                }
            }.getOrElse { ex ->
                lastError = SupabaseNetworkClient.formatConnectionError(ex as? Exception ?: Exception(ex), supabaseUrl)
                true
            }
            if (stopBatch) break
        }

        return SupabaseStudentSyncReport(
            totalProcessed = students.size,
            syncedToSupabaseCount = syncedCount,
            alreadyInSupabaseCount = alreadyRegistered,
            usedEdgeFunction = false,
            rateLimited = rateLimited,
            emailConfirmationPending = emailConfirmPending,
            diagnosticMessage = lastError
        )
    }

    suspend fun saveStudentStreaks(streaks: Map<String, StudentStreakState>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        streaks.values.forEach { s ->
            arr.put(JSONObject().apply {
                put("student_id", s.studentId)
                put("current_streak", s.currentStreak)
                put("longest_streak", s.longestStreak)
                put("last_login_date_wib", s.lastLoginDateWib ?: "")
                put("streak_cycle_id", s.streakCycleId)
                put("login_dates_wib", JSONArray(s.loginDatesWib))
                put("claimed_milestones", JSONArray(s.claimedMilestonesInCycle))
            })
        }
        store.putString(KEY_STUDENT_STREAKS_JSON, arr.toString())
    }

    suspend fun saveStreakNotificationLogs(logs: List<StreakNotificationLog>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        logs.forEach { l ->
            arr.put(JSONObject().apply {
                put("id", l.id)
                put("student_id", l.studentId)
                put("reminder_date_wib", l.reminderDateWib)
                put("slot", l.slot.name)
                put("message", l.message)
                put("sent_at_label", l.sentAtLabel)
                put("timestamp_millis", l.timestampMillis)
            })
        }
        store.putString(KEY_STREAK_NOTIFS_JSON, arr.toString())
    }

    suspend fun syncLoginStreakToSupabaseIfConnected() = withContext(Dispatchers.IO) {
        if (!isSupabaseConfigured()) return@withContext
        val jwt = getActiveSupabaseJwt() ?: return@withContext
        runCatching {
            val base = supabaseUrl.trimEnd('/')
            val req = Request.Builder()
                .url("$base/rest/v1/rpc/record_student_login_streak")
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Authorization", "Bearer $jwt")
                .addHeader("Content-Type", "application/json")
                .build()
            httpClient.newCall(req).execute().close()
        }
    }

    suspend fun syncParticipationXpToSupabaseIfConnected(
        studentId: String,
        category: ParticipationXpCategory,
        note: String,
        semesterKey: String
    ) = withContext(Dispatchers.IO) {
        if (!isSupabaseConfigured()) return@withContext
        val jwt = getActiveSupabaseJwt() ?: return@withContext
        runCatching {
            val base = supabaseUrl.trimEnd('/')
            val payload = JSONObject().apply {
                put("p_student_id", studentId)
                put("p_category", category.xpCategory.name)
                put("p_note", note)
                put("p_semester_key", semesterKey)
            }
            val req = Request.Builder()
                .url("$base/rest/v1/rpc/admin_award_participation_xp")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Authorization", "Bearer $jwt")
                .addHeader("Content-Type", "application/json")
                .build()
            httpClient.newCall(req).execute().close()
        }
    }

    private suspend fun syncStudentBadgeToSupabaseIfConnected(studentId: String, badgeStatus: StudentBadgeStatus) {
        if (!isSupabaseConfigured()) return
        val jwt = getActiveSupabaseJwt() ?: return
        runCatching {
            val base = supabaseUrl.trimEnd('/')
            val body = JSONObject().apply {
                put("student_badge_status", badgeStatus.dbValue)
            }
            val req = Request.Builder()
                .url("$base/rest/v1/profiles?id=eq.$studentId")
                .patch(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Authorization", "Bearer $jwt")
                .addHeader("Content-Type", "application/json")
                .build()
            httpClient.newCall(req).execute().close()
        }
    }

    private suspend fun persistSessionState(
        profile: UserProfile,
        token: String,
        identifier: String,
        rememberMe: Boolean,
        expiresAtMillis: Long = System.currentTimeMillis() +
            if (profile.role == UserRole.ADMIN) ADMIN_SESSION_DURATION_MS else REMEMBER_SESSION_DURATION_MS,
        refreshToken: String? = null
    ) {
        // Always retain the live access token & refresh token in memory for the active session
        // even when rememberMe == false (so Admin CRUD / Export / Sync have the live JWT).
        activeAccessToken = token
        activeRefreshToken = refreshToken?.takeIf { it.isNotBlank() }
        store.putBoolean(KEY_REMEMBER_ME, rememberMe)
        if (rememberMe) {
            if (profile.role == UserRole.STUDENT) {
                store.putString(KEY_REMEMBERED_IDENTIFIER, identifier)
            }
            store.putString(KEY_SESSION_TOKEN, token)
            store.putString(KEY_SESSION_REFRESH_TOKEN, refreshToken?.takeIf { it.isNotBlank() })
            store.putString(KEY_SESSION_USER_ID, profile.id)
            store.putString(KEY_SESSION_ROLE, profile.role.name)
            store.putString(KEY_SESSION_PROFILE_JSON, serializeProfile(profile))
            store.putLong(KEY_SESSION_EXPIRES_AT, expiresAtMillis)
        } else {
            store.putString(KEY_REMEMBERED_IDENTIFIER, null)
            clearStoredSessionKeys()
        }
    }

    private sealed interface SupabaseAdminLoginOutcome {
        data class Authenticated(val result: AuthResult.Success) : SupabaseAdminLoginOutcome
        data class Rejected(val result: AuthResult.Failure) : SupabaseAdminLoginOutcome
        data object BackendNotProvisioned : SupabaseAdminLoginOutcome
    }

    private sealed interface AdminEmailLookupResult {
        data class Found(val email: String) : AdminEmailLookupResult
        data object NotProvisionedOrMissing : AdminEmailLookupResult
        data class Error(val message: String) : AdminEmailLookupResult
    }

    private suspend fun loginAdminViaSupabase(
        username: String,
        password: String,
        rememberMe: Boolean,
        nowMillis: Long
    ): SupabaseAdminLoginOutcome {
        return try {
            val base = supabaseUrl.trimEnd('/')

            // 1. Primary path: dedicated backend Edge Function `admin-auth-login`
            val edgePayload = JSONObject().apply {
                put("action", "login")
                put("username", username)
                put("password", password)
            }
            val edgeReq = Request.Builder()
                .url("$base/functions/v1/admin-auth-login")
                .post(edgePayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Authorization", "Bearer ${supabaseAnonKey.trim()}")
                .addHeader("Content-Type", "application/json")
                .build()

            httpClient.newCall(edgeReq).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val json = JSONObject(rawBody)
                    val accessToken = json.optString("access_token")
                    val refreshToken = json.optString("refresh_token").ifBlank { null }
                    val profJson = json.optJSONObject("profile")
                    val roleStr = profJson?.optString("role", "")
                    if (accessToken.isBlank() || profJson == null || !roleStr.equals("admin", ignoreCase = true)) {
                        return SupabaseAdminLoginOutcome.Rejected(
                            AuthResult.Failure("Verifikasi role administrator dari backend gagal.")
                        )
                    }
                    val adminProfile = UserProfile(
                        id = profJson.optString("id"),
                        fullName = profJson.optString("full_name", "Administrator Azril Clas"),
                        email = profJson.optString("email", PRIMARY_ADMIN_EMAIL),
                        username = profJson.optString("username", username),
                        role = UserRole.ADMIN,
                        status = AccountStatus.ACTIVE,
                        mustChangePassword = false,
                        createdAt = profJson.optString("created_at").ifBlank { null }
                    )
                    val serverExpiresAtSec = json.optLong("expires_at", 0L)
                    val expiresAt = if (refreshToken != null) {
                        nowMillis + ADMIN_SESSION_DURATION_MS
                    } else if (serverExpiresAtSec > 0L) {
                        minOf(serverExpiresAtSec * 1000L, nowMillis + ADMIN_SESSION_DURATION_MS)
                    } else {
                        nowMillis + ADMIN_SESSION_DURATION_MS
                    }
                    persistSessionState(
                        profile = adminProfile,
                        token = accessToken,
                        identifier = username,
                        rememberMe = rememberMe,
                        expiresAtMillis = expiresAt,
                        refreshToken = refreshToken
                    )
                    return SupabaseAdminLoginOutcome.Authenticated(
                        AuthResult.Success(profile = adminProfile, usingSupabase = true, sessionExpiresAt = expiresAt)
                    )
                } else if (resp.code != 404) {
                    val errMsg = runCatching { JSONObject(rawBody).optString("error") }.getOrNull()
                    return SupabaseAdminLoginOutcome.Rejected(
                        AuthResult.Failure(
                            errMsg?.takeIf { it.isNotBlank() } ?: "Username atau password administrator tidak valid."
                        )
                    )
                }
            }

            // 2. Fallback path when Edge Function is not yet deployed: secure DB RPC `resolve_admin_login_email`
            //    or direct Admin email / deterministic internal admin emails, always strictly verified against profiles.role == 'admin'.
            val candidateAdminEmails = linkedSetOf<String>()
            var resolvedByAdminRpc = false
            val isDirectEmailInput = username.contains("@")
            when (val lookup = resolveAdminEmailFromSupabase(base, username)) {
                is AdminEmailLookupResult.Found -> {
                    candidateAdminEmails.add(lookup.email.lowercase(Locale.US))
                    resolvedByAdminRpc = true
                }
                AdminEmailLookupResult.NotProvisionedOrMissing -> {
                    if (isDirectEmailInput) {
                        candidateAdminEmails.add(username.trim().lowercase(Locale.US))
                    } else {
                        candidateAdminEmails.add(internalAuthEmailForAdmin(username))
                        if (username.equals(PRIMARY_ADMIN_USERNAME, ignoreCase = true)) {
                            candidateAdminEmails.add(PRIMARY_ADMIN_EMAIL.lowercase(Locale.US))
                        }
                    }
                }
                is AdminEmailLookupResult.Error ->
                    return SupabaseAdminLoginOutcome.Rejected(AuthResult.Failure(lookup.message))
            }

            var sawEmailNotConfirmed = false
            for (candidateEmail in candidateAdminEmails) {
                val authPayload = JSONObject().apply {
                    put("email", candidateEmail)
                    put("password", password)
                }
                val authReq = Request.Builder()
                    .url("$base/auth/v1/token?grant_type=password")
                    .post(authPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .addHeader("apikey", supabaseAnonKey.trim())
                    .addHeader("Content-Type", "application/json")
                    .build()

                httpClient.newCall(authReq).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val lower = body.lowercase(Locale.US)
                        if (lower.contains("email not confirmed") || lower.contains("email_not_confirmed")) {
                            sawEmailNotConfirmed = true
                        }
                        if (resolvedByAdminRpc || isDirectEmailInput) {
                            val errText = if (sawEmailNotConfirmed) {
                                "Email Admin '$candidateEmail' belum dikonfirmasi di Supabase Authentication."
                            } else {
                                "Email/username atau password administrator Supabase tidak valid."
                            }
                            return SupabaseAdminLoginOutcome.Rejected(AuthResult.Failure(errText))
                        }
                        return@use
                    }
                    val json = JSONObject(body)
                    val accessToken = json.optString("access_token")
                    val refreshToken = json.optString("refresh_token").ifBlank { null }
                    val userId = json.optJSONObject("user")?.optString("id").orEmpty()
                    if (accessToken.isBlank() || userId.isBlank()) {
                        return SupabaseAdminLoginOutcome.Rejected(
                            AuthResult.Failure("Token sesi administrator tidak valid.")
                        )
                    }

                    // Mandatory backend verification via verify_admin_session() (when deployed) + profiles.role == 'admin'
                    val verifyResult = verifyAdminSessionWithSupabaseDetailed(accessToken)
                    val profile = fetchSupabaseProfile(base, accessToken, userId, candidateEmail)
                    val backendOk = verifyResult == true || (verifyResult == null && profile?.role == UserRole.ADMIN)
                    if (!backendOk || profile == null || profile.role != UserRole.ADMIN || profile.status != AccountStatus.ACTIVE) {
                        return SupabaseAdminLoginOutcome.Rejected(
                            AuthResult.Failure("Akses ditolak: akun '$candidateEmail' berhasil login di Supabase Auth namun belum berstatus role='admin' aktif di tabel public.profiles.")
                        )
                    }

                    val expiresInSec = json.optLong("expires_in", 3600L)
                    val expiresAt = if (refreshToken != null) {
                        nowMillis + ADMIN_SESSION_DURATION_MS
                    } else {
                        minOf(nowMillis + expiresInSec * 1000L, nowMillis + ADMIN_SESSION_DURATION_MS)
                    }
                    lastAdminSupabaseDiagnostic = null
                    persistSessionState(
                        profile = profile,
                        token = accessToken,
                        identifier = username,
                        rememberMe = rememberMe,
                        expiresAtMillis = expiresAt,
                        refreshToken = refreshToken
                    )
                    return SupabaseAdminLoginOutcome.Authenticated(
                        AuthResult.Success(profile = profile, usingSupabase = true, sessionExpiresAt = expiresAt)
                    )
                }
            }
            lastAdminSupabaseDiagnostic = if (sawEmailNotConfirmed) {
                "Email Admin di Supabase Auth belum dikonfirmasi."
            } else {
                "Akun '$username' belum tertaut ke Supabase Auth (profiles.role='admin'). Masuk menggunakan Email & Password akun Admin Supabase Anda atau jalankan migrasi SQL 009 & 010."
            }
            SupabaseAdminLoginOutcome.BackendNotProvisioned
        } catch (e: Exception) {
            SupabaseAdminLoginOutcome.Rejected(
                AuthResult.Failure(SupabaseNetworkClient.formatConnectionError(e, supabaseUrl))
            )
        }
    }

    private fun resolveAdminEmailFromSupabase(baseUrl: String, username: String): AdminEmailLookupResult {
        val body = JSONObject().apply { put("p_username", username) }
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/resolve_admin_login_email")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("apikey", supabaseAnonKey.trim())
            .addHeader("Content-Type", "application/json")
            .build()
        return httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string()?.trim().orEmpty()
            when {
                resp.code == 404 -> AdminEmailLookupResult.NotProvisionedOrMissing
                !resp.isSuccessful -> AdminEmailLookupResult.Error("Gagal memverifikasi akun administrator di Supabase.")
                else -> {
                    val email = raw.removeSurrounding("\"")
                    if (email.contains("@")) {
                        AdminEmailLookupResult.Found(email)
                    } else {
                        AdminEmailLookupResult.NotProvisionedOrMissing
                    }
                }
            }
        }
    }

    private suspend fun verifyAdminSessionWithSupabaseDetailed(jwt: String): Boolean? = withContext(Dispatchers.IO) {
        if (!isSupabaseConfigured() || jwt.isBlank()) return@withContext false
        runCatching {
            val base = supabaseUrl.trimEnd('/')
            val req = Request.Builder()
                .url("$base/rest/v1/rpc/verify_admin_session")
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Authorization", "Bearer $jwt")
                .addHeader("Content-Type", "application/json")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.code == 404) return@use null // RPC not deployed yet
                if (!resp.isSuccessful) return@use false
                resp.body?.string()?.trim().equals("true", ignoreCase = true)
            }
        }.getOrDefault(false)
    }

    suspend fun verifyAdminSessionWithSupabase(jwt: String): Boolean =
        verifyAdminSessionWithSupabaseDetailed(jwt) == true

    private sealed interface StudentEmailLookupResult {
        data class Found(val email: String) : StudentEmailLookupResult
        data object NotFound : StudentEmailLookupResult
        data class Rejected(val message: String) : StudentEmailLookupResult
    }

    private suspend fun loginViaSupabase(
        identifier: String,
        password: String,
        rememberMe: Boolean,
        nowMillis: Long
    ): AuthResult {
        return try {
            val base = supabaseUrl.trimEnd('/')
            val cleanId = identifier.trim()
            val candidateEmails = linkedSetOf<String>()
            var resolvedByRpc = false

            if (cleanId.contains("@")) {
                candidateEmails.add(cleanId.lowercase(Locale.US))
                resolvedByRpc = true
            } else {
                when (val lookup = resolveEmailFromSupabaseIdentifier(base, cleanId)) {
                    is StudentEmailLookupResult.Found -> {
                        candidateEmails.add(lookup.email.lowercase(Locale.US))
                        resolvedByRpc = true
                    }
                    is StudentEmailLookupResult.Rejected -> {
                        return AuthResult.Failure(lookup.message)
                    }
                    StudentEmailLookupResult.NotFound -> Unit
                }
                // Deterministic fallback emails so login still succeeds even when
                // resolve_login_email RPC is not yet deployed or only 001 migration was applied.
                val safeNim = cleanId.lowercase(Locale.US)
                    .filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
                if (safeNim.isNotEmpty()) {
                    candidateEmails.add(internalAuthEmailForNim(cleanId))
                    candidateEmails.add("${safeNim}@mahasiswa.azrilclas.ac.id")
                }
            }

            if (candidateEmails.isEmpty()) {
                return AuthResult.Failure("NIM atau akun tidak ditemukan di database.")
            }

            var sawEmailNotConfirmed = false
            for (candidateEmail in candidateEmails) {
                val payload = JSONObject().apply {
                    put("email", candidateEmail)
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
                        val lower = body.lowercase(Locale.US)
                        if (lower.contains("email not confirmed") || lower.contains("email_not_confirmed")) {
                            sawEmailNotConfirmed = true
                        }
                        return@use
                    }
                    val json = JSONObject(body)
                    val accessToken = json.optString("access_token")
                    val refreshToken = json.optString("refresh_token").ifBlank { null }
                    val userObj = json.optJSONObject("user")
                    val userId = userObj?.optString("id").orEmpty()
                    if (accessToken.isBlank() || userId.isBlank()) return@use

                    val profile = fetchSupabaseProfile(
                        baseUrl = base,
                        jwt = accessToken,
                        userId = userId,
                        fallbackEmail = candidateEmail,
                        fallbackIdentifier = cleanId,
                        userMetadata = userObj?.optJSONObject("user_metadata")
                    ) ?: return AuthResult.Failure("Profil akun tidak ditemukan di tabel profiles.")

                    if (profile.role == UserRole.ADMIN) {
                        return AuthResult.Failure("Akun Administrator harus masuk melalui halaman Login Admin.")
                    }
                    if (profile.status == AccountStatus.INACTIVE) {
                        return AuthResult.Failure("Akun mahasiswa ini sedang dinonaktifkan oleh Admin.")
                    }

                    val expiresAt = nowMillis + REMEMBER_SESSION_DURATION_MS
                    persistSessionState(
                        profile = profile,
                        token = accessToken,
                        identifier = cleanId,
                        rememberMe = rememberMe,
                        expiresAtMillis = expiresAt,
                        refreshToken = refreshToken
                    )
                    return AuthResult.Success(profile = profile, usingSupabase = true, sessionExpiresAt = expiresAt)
                }
            }

            when {
                sawEmailNotConfirmed -> AuthResult.Failure(
                    "Akun sudah terdaftar di Supabase tetapi email belum dikonfirmasi. Nonaktifkan 'Confirm email' di Supabase Auth Settings atau gunakan Edge Function import."
                )
                resolvedByRpc -> AuthResult.Failure("NIM atau kata sandi tidak sesuai.")
                else -> AuthResult.Failure("NIM atau akun tidak ditemukan di database.")
            }
        } catch (e: Exception) {
            AuthResult.Failure(SupabaseNetworkClient.formatConnectionError(e, supabaseUrl))
        }
    }

    private fun resolveEmailFromSupabaseIdentifier(baseUrl: String, identifier: String): StudentEmailLookupResult {
        val body = JSONObject().apply { put("p_identifier", identifier) }
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/resolve_login_email")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("apikey", supabaseAnonKey.trim())
            .addHeader("Content-Type", "application/json")
            .build()
        return httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string()?.trim().orEmpty()
            if (!resp.isSuccessful) {
                val lower = raw.lowercase(Locale.US)
                if (lower.contains("administrator") || lower.contains("dinonaktifkan")) {
                    val msg = runCatching { JSONObject(raw).optString("message") }.getOrNull().orEmpty()
                    if (msg.isNotBlank()) return@use StudentEmailLookupResult.Rejected(msg)
                }
                return@use StudentEmailLookupResult.NotFound
            }
            val email = raw.removeSurrounding("\"")
            if (email.contains("@")) StudentEmailLookupResult.Found(email) else StudentEmailLookupResult.NotFound
        }
    }

    private fun fetchSupabaseProfile(
        baseUrl: String,
        jwt: String,
        userId: String,
        fallbackEmail: String,
        fallbackIdentifier: String = "",
        userMetadata: JSONObject? = null
    ): UserProfile? {
        val selectUrls = listOf(
            "$baseUrl/rest/v1/profiles?id=eq.$userId&select=*,study_programs(id,code,name),classes(id,name)",
            "$baseUrl/rest/v1/profiles?id=eq.$userId&select=*,study_programs(code,name)",
            "$baseUrl/rest/v1/profiles?id=eq.$userId&select=*"
        )
        for (url in selectUrls) {
            val req = Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Authorization", "Bearer $jwt")
                .build()
            val parsed = runCatching {
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val arr = JSONArray(resp.body?.string().orEmpty())
                    if (arr.length() == 0) return@use null
                    val row = arr.getJSONObject(0)
                    val prodiObj = row.optJSONObject("study_programs")
                    val classObj = row.optJSONObject("classes")
                    val roleStr = row.optString("role", "student")
                    val prodiCode = prodiObj?.optString("code")?.ifBlank { null }
                        ?: prodiObj?.optString("name")?.ifBlank { null }
                        ?: OfficialStudyPrograms.findById(row.optString("study_program_id"))?.code
                        ?: userMetadata?.optString("study_program_code")?.ifBlank { null }
                    val statusStr = row.optString("account_status", "active").lowercase(Locale.US)
                    val status = when (statusStr) {
                        "inactive" -> AccountStatus.INACTIVE
                        "pending_activation" -> AccountStatus.PENDING_ACTIVATION
                        else -> AccountStatus.ACTIVE
                    }
                    val joinedClassName = classObj?.optString("name")?.trim()?.ifBlank { null }
                    UserProfile(
                        id = row.optString("id", userId),
                        fullName = row.optString("full_name", userMetadata?.optString("full_name") ?: "Mahasiswa"),
                        email = row.optString("email", fallbackEmail),
                        username = row.optString("username").ifBlank { null },
                        role = if (roleStr.equals("admin", ignoreCase = true)) UserRole.ADMIN else UserRole.STUDENT,
                        nim = row.optString("nim").ifBlank { userMetadata?.optString("nim")?.ifBlank { null } },
                        studyProgramId = row.optString("study_program_id").ifBlank { null },
                        studyProgramName = prodiCode,
                        classId = row.optString("class_id").ifBlank { null },
                        className = joinedClassName
                            ?: row.optString("class_name").ifBlank {
                                userMetadata?.optString("class_name")?.ifBlank { null }
                                    ?: row.optString("class_id").ifBlank { "Terhubung ke Supabase" }
                            },
                        semester = row.optInt("semester", userMetadata?.optInt("semester", 1) ?: 1),
                        cohortYear = row.optInt("cohort_year", userMetadata?.optInt("cohort_year", 2025) ?: 2025),
                        status = status,
                        badgeStatus = StudentBadgeStatus.fromDbValue(row.optString("student_badge_status", "neutral")),
                        mustChangePassword = row.optBoolean("must_change_password", false),
                        createdAt = row.optString("created_at").ifBlank { null }
                    )
                }
            }.getOrNull()
            if (parsed != null) return parsed
        }

        // Graceful fallback strictly as STUDENT from Supabase Auth user_metadata when profiles row is not yet synced
        if (userMetadata != null) {
            val nim = userMetadata.optString("nim").ifBlank {
                fallbackIdentifier.takeIf { it.isNotBlank() && !it.contains("@") }
            }
            val prodiCode = userMetadata.optString("study_program_code").ifBlank { null }
            val matchedProdi = prodiCode?.let { OfficialStudyPrograms.findByCode(it) }
            return UserProfile(
                id = userId,
                fullName = userMetadata.optString("full_name").ifBlank { nim ?: "Mahasiswa" },
                email = fallbackEmail,
                username = userMetadata.optString("username").ifBlank { nim },
                role = UserRole.STUDENT,
                nim = nim,
                studyProgramId = matchedProdi?.id ?: userMetadata.optString("study_program_id").ifBlank { null },
                studyProgramName = matchedProdi?.code ?: prodiCode,
                classId = userMetadata.optString("class_id").ifBlank { null },
                className = userMetadata.optString("class_name").ifBlank { "Terhubung ke Supabase" },
                semester = userMetadata.optInt("semester", 1),
                cohortYear = userMetadata.optInt("cohort_year", 2025),
                status = AccountStatus.ACTIVE,
                badgeStatus = StudentBadgeStatus.NEUTRAL,
                mustChangePassword = userMetadata.optBoolean("must_change_password", false)
            )
        }
        return null
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
                    username = o.optString("username", o.getString("nim")),
                    studyProgramId = o.getString("study_program_id"),
                    studyProgramCode = o.getString("study_program_code"),
                    classId = o.optString("class_id").ifBlank { null },
                    className = o.optString("class_name").ifBlank { null },
                    semester = o.optInt("semester", 1),
                    cohortYear = o.optInt("cohort_year", 2026),
                    accountStatus = runCatching {
                        AccountStatus.valueOf(o.optString("account_status", AccountStatus.ACTIVE.name))
                    }.getOrDefault(AccountStatus.ACTIVE),
                    badgeStatus = StudentBadgeStatus.fromDbValue(o.optString("badge_status", "neutral")),
                    mustChangePassword = o.optBoolean("must_change_password", false),
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
                put("class_id", acc.classId ?: "")
                put("class_name", acc.className ?: "")
                put("semester", acc.semester)
                put("cohort_year", acc.cohortYear)
                put("account_status", acc.accountStatus.name)
                put("badge_status", acc.badgeStatus.dbValue)
                put("must_change_password", acc.mustChangePassword)
                put("role", acc.role.name)
                put("password_hash", acc.passwordHash)
                put("created_at", acc.createdAt)
            })
        }
        store.putString(KEY_ACCOUNTS_JSON, arr.toString())
    }

    private suspend fun loadLocalClasses(): List<AcademicClass> {
        val raw = store.getString(KEY_CLASSES_JSON) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AcademicClass(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    studyProgramId = o.getString("study_program_id"),
                    studyProgramName = o.getString("study_program_name"),
                    semester = o.optInt("semester", 1),
                    academicYear = o.optString("academic_year", "2026/2027"),
                    cohortYear = o.optInt("cohort_year", 2026),
                    description = o.optString("description", ""),
                    isActive = o.optBoolean("is_active", true),
                    memberIds = o.optJSONArray("member_ids").toStringList()
                )
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun loadLocalAssignments(): List<Assignment> {
        val raw = store.getString(KEY_ASSIGNMENTS_JSON) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Assignment(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    description = o.optString("description", ""),
                    instructions = o.optString("instructions", ""),
                    classId = o.optString("class_id", ""),
                    className = o.optString("class_name", ""),
                    targetType = AssignmentTargetType.fromDbValue(o.optString("target_type", "class")),
                    targetClassIds = o.optJSONArray("target_class_ids").toStringList(),
                    targetClassNames = o.optJSONArray("target_class_names").toStringList(),
                    targetStudyProgramIds = o.optJSONArray("target_study_program_ids").toStringList(),
                    targetStudyProgramCodes = o.optJSONArray("target_study_program_codes").toStringList(),
                    courseCategory = o.optString("course_category", "Umum"),
                    createdAt = o.optString("created_at", ""),
                    deadline = o.optString("deadline", ""),
                    deadlineEpochMillis = if (o.has("deadline_epoch_millis")) o.optLong("deadline_epoch_millis") else null,
                    allowedFormats = o.optJSONArray("allowed_formats").toStringList().ifEmpty { listOf("PDF", "DOCX") },
                    maxFileSizeMb = o.optInt("max_file_size_mb", 10),
                    submissionMethod = runCatching {
                        SubmissionMethod.valueOf(o.optString("submission_method", SubmissionMethod.BOTH.name))
                    }.getOrDefault(SubmissionMethod.BOTH),
                    file1Name = o.optString("file1_name").ifBlank { null },
                    file1Url = o.optString("file1_url").ifBlank { null },
                    file2Name = o.optString("file2_name").ifBlank { null },
                    file2Url = o.optString("file2_url").ifBlank { null },
                    materialAttachment = o.optString("material_attachment").ifBlank { null },
                    isActive = o.optBoolean("is_active", true)
                )
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun loadLocalXpTransactions(): List<RankingPointTransaction> {
        val raw = store.getString(KEY_XP_TRANSACTIONS_JSON) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val sourceType = runCatching {
                    PointSourceType.valueOf(o.optString("source_type", PointSourceType.ATTENDANCE.name))
                }.getOrDefault(PointSourceType.ATTENDANCE)
                val xpCategory = runCatching {
                    XpCategory.valueOf(o.optString("xp_category", XpCategory.fromSourceType(sourceType).name))
                }.getOrDefault(XpCategory.fromSourceType(sourceType))
                RankingPointTransaction(
                    id = o.getString("id"),
                    studentId = o.getString("student_id"),
                    sourceType = sourceType,
                    sourceId = o.optString("source_id", o.getString("id")),
                    points = o.optInt("points", 0),
                    description = o.optString("description", ""),
                    createdAt = o.optString("created_at", ""),
                    updatedAt = o.optString("updated_at", ""),
                    timestampMillis = o.optLong("timestamp_millis", System.currentTimeMillis()),
                    xpCategory = xpCategory,
                    activityTitle = o.optString("activity_title", o.optString("description", "")),
                    semesterKey = o.optString("semester_key", "2026/2027-Ganjil"),
                    adminId = o.optString("admin_id").ifBlank { null },
                    adminName = o.optString("admin_name").ifBlank { null }
                )
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun loadLocalAttendanceRecords(): List<AttendanceRecord> {
        val raw = store.getString(KEY_ATTENDANCE_RECORDS_JSON) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AttendanceRecord(
                    id = o.getString("id"),
                    sessionId = o.getString("session_id"),
                    meetingTitle = o.optString("meeting_title", ""),
                    className = o.optString("class_name", ""),
                    studentId = o.getString("student_id"),
                    studentName = o.optString("student_name", ""),
                    studentNim = o.optString("student_nim", "-"),
                    dateLabel = o.optString("date_label", ""),
                    recordedAt = o.optString("recorded_at", ""),
                    status = runCatching {
                        AttendanceStatus.valueOf(o.optString("status", AttendanceStatus.HADIR.name))
                    }.getOrDefault(AttendanceStatus.HADIR),
                    correctionReason = o.optString("correction_reason").ifBlank { null },
                    semesterKey = o.optString("semester_key", "2026/2027-Ganjil"),
                    timestampMillis = o.optLong("timestamp_millis", System.currentTimeMillis())
                )
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun loadLocalHistoricalAttendanceRecords(): List<HistoricalAttendanceRecord> {
        val raw = store.getString(KEY_HISTORICAL_ATTENDANCE_JSON) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                HistoricalAttendanceRecord(
                    id = o.getString("id"),
                    studentId = o.getString("student_id"),
                    studentNim = o.optString("student_nim", "-"),
                    studentName = o.optString("student_name", ""),
                    classId = o.optString("class_id").ifBlank { null },
                    className = o.optString("class_name", ""),
                    studyProgramCode = o.optString("study_program_code", ""),
                    weekNumber = o.optInt("week_number", 1),
                    weekLabel = o.optString("week_label", "Minggu ke-1"),
                    rawStatus = o.optString("raw_status", ""),
                    sourceSheet = o.optString("source_sheet", ""),
                    semesterKey = o.optString("semester_key", "2026/2027-Ganjil"),
                    importedAt = o.optString("imported_at", ""),
                    xpCalculated = o.optBoolean("xp_calculated", false)
                )
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun loadLocalStudentStreaks(): Map<String, StudentStreakState> {
        val raw = store.getString(KEY_STUDENT_STREAKS_JSON) ?: return emptyMap()
        return runCatching {
            val arr = JSONArray(raw)
            val result = mutableMapOf<String, StudentStreakState>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val studentId = o.getString("student_id")
                val milestonesArr = o.optJSONArray("claimed_milestones")
                val milestones = if (milestonesArr == null) emptyList() else {
                    (0 until milestonesArr.length()).map { idx -> milestonesArr.optInt(idx) }.filter { it > 0 }
                }
                result[studentId] = StudentStreakState(
                    studentId = studentId,
                    currentStreak = o.optInt("current_streak", 0),
                    longestStreak = o.optInt("longest_streak", 0),
                    lastLoginDateWib = o.optString("last_login_date_wib").ifBlank { null },
                    streakCycleId = o.optString("streak_cycle_id").ifBlank { UUID.randomUUID().toString() },
                    loginDatesWib = o.optJSONArray("login_dates_wib").toStringList(),
                    claimedMilestonesInCycle = milestones
                )
            }
            result
        }.getOrDefault(emptyMap())
    }

    private suspend fun loadLocalStreakNotifications(): List<StreakNotificationLog> {
        val raw = store.getString(KEY_STREAK_NOTIFS_JSON) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                StreakNotificationLog(
                    id = o.getString("id"),
                    studentId = o.getString("student_id"),
                    reminderDateWib = o.getString("reminder_date_wib"),
                    slot = runCatching {
                        StreakReminderSlot.valueOf(o.optString("slot", StreakReminderSlot.MORNING_08.name))
                    }.getOrDefault(StreakReminderSlot.MORNING_08),
                    message = o.optString("message", ""),
                    sentAtLabel = o.optString("sent_at_label", ""),
                    timestampMillis = o.optLong("timestamp_millis", System.currentTimeMillis())
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { idx -> optString(idx).takeIf { it.isNotBlank() } }
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
        put("class_id", profile.classId ?: "")
        put("class_name", profile.className ?: "")
        put("semester", profile.semester ?: 1)
        put("cohort_year", profile.cohortYear ?: 2026)
        put("badge_status", profile.badgeStatus.dbValue)
        put("must_change_password", profile.mustChangePassword)
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
                classId = o.optString("class_id").ifBlank { null },
                className = o.optString("class_name").ifBlank { "Belum terdaftar di kelas" },
                semester = o.optInt("semester", 1),
                cohortYear = o.optInt("cohort_year", 2026),
                status = AccountStatus.ACTIVE,
                badgeStatus = StudentBadgeStatus.fromDbValue(o.optString("badge_status", "neutral")),
                mustChangePassword = o.optBoolean("must_change_password", false),
                createdAt = o.optString("created_at").ifBlank { null }
            )
        }.getOrNull()
    }
}
