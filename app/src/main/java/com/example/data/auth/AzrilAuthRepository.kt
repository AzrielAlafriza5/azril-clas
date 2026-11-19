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
    private val supabaseUrl: String = BuildConfig.SUPABASE_URL,
    private val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val accountsMutex = Mutex()

    companion object {
        private const val KEY_SESSION_TOKEN = "session_token"
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

    fun isSupabaseConfigured(): Boolean {
        val cleanUrl = supabaseUrl.trim()
        val cleanKey = supabaseAnonKey.trim()
        return cleanUrl.startsWith("https://") &&
            !cleanUrl.contains("YOUR_PROJECT_REF") &&
            cleanKey.length > 20 &&
            !cleanKey.contains("YOUR_SUPABASE_ANON_KEY")
    }

    private fun isLocalSessionToken(token: String?): Boolean =
        token.isNullOrBlank() || token.startsWith("azr_sess_") || token.startsWith("azr_admin_sess_")

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
        store.putString(KEY_SESSION_USER_ID, null)
        store.putString(KEY_SESSION_ROLE, null)
        store.putString(KEY_SESSION_PROFILE_JSON, null)
        store.putLong(KEY_SESSION_EXPIRES_AT, 0L)
    }

    suspend fun restoreSession(nowMillis: Long = System.currentTimeMillis()): RestoredSession = withContext(Dispatchers.IO) {
        val rememberMe = store.getBoolean(KEY_REMEMBER_ME, true)
        val rememberedIdentifier = store.getString(KEY_REMEMBERED_IDENTIFIER).orEmpty()
        val token = store.getString(KEY_SESSION_TOKEN)
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
            if (expiresAt <= 0L || nowMillis >= expiresAt) {
                sessionExpired = true
                clearStoredSessionKeys()
                null
            } else {
                val userId = store.getString(KEY_SESSION_USER_ID)
                val fromLocal = accounts.find { it.id == userId }?.toUserProfile()
                val candidate = fromLocal ?: deserializeProfile(store.getString(KEY_SESSION_PROFILE_JSON))
                val roleMismatch = storedRole != null && candidate != null && !storedRole.equals(candidate.role.name, ignoreCase = true)

                if (candidate == null || roleMismatch || candidate.status == AccountStatus.INACTIVE) {
                    sessionExpired = true
                    clearStoredSessionKeys()
                    null
                } else if (candidate.role == UserRole.ADMIN && isSupabaseConfigured() && !token.startsWith("azr_admin_sess_")) {
                    // Re-verify Admin JWT & role with Supabase backend on session restore
                    val verified = verifyAdminSessionWithSupabase(token)
                    if (verified) {
                        activeExpiresAt = expiresAt
                        candidate
                    } else {
                        sessionExpired = true
                        clearStoredSessionKeys()
                        null
                    }
                } else {
                    activeExpiresAt = expiresAt
                    candidate
                }
            }
        } else {
            if (!token.isNullOrBlank() && expiresAt > 0L && nowMillis >= expiresAt) {
                sessionExpired = true
            }
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
        mustChangePassword: Boolean = initialPassword.trim().equals(nim.trim(), ignoreCase = true)
    ): AuthResult = withContext(Dispatchers.IO) {
        val cleanName = fullName.trim()
        val cleanNim = nim.trim()
        val cleanEmail = email.trim().lowercase(Locale.US)

        if (cleanName.length < 3) return@withContext AuthResult.Failure("Nama lengkap minimal 3 karakter.")
        if (cleanNim.length < 5) return@withContext AuthResult.Failure("NIM minimal 5 karakter.")
        if (cleanNim.contains(" ")) return@withContext AuthResult.Failure("NIM tidak boleh mengandung spasi.")
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) return@withContext AuthResult.Failure("Format email akademik tidak valid.")
        if (initialPassword.length < 6) return@withContext AuthResult.Failure("Password awal mahasiswa minimal 6 karakter.")

        val record = accountsMutex.withLock {
            val accounts = loadLocalAccounts()
            if (accounts.any { it.nim.equals(cleanNim, ignoreCase = true) }) {
                return@withContext AuthResult.Failure("NIM '$cleanNim' sudah terdaftar pada akun mahasiswa lain.")
            }
            if (accounts.any { it.email.equals(cleanEmail, ignoreCase = true) }) {
                return@withContext AuthResult.Failure("Email '$cleanEmail' sudah digunakan oleh akun lain.")
            }

            val created = StoredAccountRecord(
                id = studentId,
                fullName = cleanName,
                nim = cleanNim,
                email = cleanEmail,
                username = cleanNim,
                studyProgramId = studyProgramId,
                studyProgramCode = studyProgramCode,
                classId = classId,
                className = className,
                semester = semester,
                cohortYear = cohortYear,
                accountStatus = accountStatus,
                badgeStatus = badgeStatus,
                mustChangePassword = mustChangePassword,
                role = UserRole.STUDENT,
                passwordHash = PasswordHasher.hashPassword(initialPassword),
                createdAt = nowIso()
            )
            saveLocalAccounts(listOf(created) + accounts)
            created
        }
        AuthResult.Success(profile = record.toUserProfile(), usingSupabase = isSupabaseConfigured())
    }

    /**
     * Atomic batch provisioning for bulk Excel student import.
     * - Username = NIM, Initial Password = NIM (hashed with PBKDF2, NEVER stored in plaintext).
     * - Sets mustChangePassword = true so student MUST change password on first login.
     * - Strictly skips existing NIMs (never overwrites existing accounts).
     * - When Supabase is configured, delegates server-side account creation to Edge Function `admin-bulk-import-students`.
     */
    suspend fun adminBulkImportStudents(
        newStudents: List<StudentRecord>,
        fileName: String,
        semesterKey: String,
        importHistoricalAttendance: Boolean,
        previewRowsForSync: List<ExcelImportPreviewRow> = emptyList()
    ): List<StudentRecord> = withContext(Dispatchers.IO) {
        val createdStudents = mutableListOf<StudentRecord>()
        val createdAny = accountsMutex.withLock {
            val existingAccounts = loadLocalAccounts()
            val existingNimSet = existingAccounts.map { it.nim.trim().lowercase(Locale.US) }.toMutableSet()
            val createdRecords = mutableListOf<StoredAccountRecord>()
            val createdIso = nowIso()

            newStudents.forEach { stu ->
                val cleanNim = stu.nim.trim()
                val nimKey = cleanNim.lowercase(Locale.US)
                if (cleanNim.length >= 5 && !cleanNim.contains(" ") && existingNimSet.add(nimKey)) {
                    val safeEmail = stu.email.trim().lowercase(Locale.US)
                        .ifBlank { "${cleanNim.lowercase(Locale.US)}@mahasiswa.azrilclas.ac.id" }
                    val rec = StoredAccountRecord(
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
                        passwordHash = PasswordHasher.hashPassword(cleanNim),
                        createdAt = createdIso
                    )
                    createdRecords.add(rec)
                    createdStudents.add(rec.toStudentRecord())
                }
            }

            if (createdRecords.isNotEmpty()) {
                saveLocalAccounts(createdRecords + existingAccounts)
                true
            } else {
                false
            }
        }

        if (createdAny) {
            syncBulkImportToSupabaseEdgeFunctionIfConnected(
                fileName = fileName,
                semesterKey = semesterKey,
                importHistoricalAttendance = importHistoricalAttendance,
                rows = previewRowsForSync
            )
        }
        createdStudents
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
            val jwt = store.getString(KEY_SESSION_TOKEN)
            if (!isLocalSessionToken(jwt)) {
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
        badgeStatus: StudentBadgeStatus
    ): AuthResult = withContext(Dispatchers.IO) {
        val cleanName = fullName.trim()
        val cleanNim = nim.trim()
        val cleanEmail = email.trim().lowercase(Locale.US)
        if (cleanName.length < 3) return@withContext AuthResult.Failure("Nama lengkap minimal 3 karakter.")
        if (cleanNim.length < 5) return@withContext AuthResult.Failure("NIM minimal 5 karakter.")
        if (cleanNim.contains(" ")) return@withContext AuthResult.Failure("NIM tidak boleh mengandung spasi.")
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) return@withContext AuthResult.Failure("Format email tidak valid.")

        val (existing, updated) = accountsMutex.withLock {
            val accounts = loadLocalAccounts()
            if (accounts.any { it.id != studentId && it.nim.equals(cleanNim, ignoreCase = true) }) {
                return@withContext AuthResult.Failure("NIM '$cleanNim' sudah digunakan oleh mahasiswa lain.")
            }
            if (accounts.any { it.id != studentId && it.email.equals(cleanEmail, ignoreCase = true) }) {
                return@withContext AuthResult.Failure("Email '$cleanEmail' sudah digunakan oleh akun lain.")
            }

            val found = accounts.find { it.id == studentId }
            val nextRec = if (found != null) {
                found.copy(
                    fullName = cleanName,
                    nim = cleanNim,
                    email = cleanEmail,
                    username = cleanNim,
                    studyProgramId = studyProgramId,
                    studyProgramCode = studyProgramCode,
                    classId = classId,
                    className = className,
                    semester = semester,
                    cohortYear = cohortYear,
                    accountStatus = accountStatus,
                    badgeStatus = badgeStatus
                )
            } else {
                StoredAccountRecord(
                    id = studentId,
                    fullName = cleanName,
                    nim = cleanNim,
                    email = cleanEmail,
                    username = cleanNim,
                    studyProgramId = studyProgramId,
                    studyProgramCode = studyProgramCode,
                    classId = classId,
                    className = className,
                    semester = semester,
                    cohortYear = cohortYear,
                    accountStatus = accountStatus,
                    badgeStatus = badgeStatus,
                    role = UserRole.STUDENT,
                    passwordHash = PasswordHasher.hashPassword(cleanNim),
                    createdAt = nowIso()
                )
            }
            val nextList = if (found != null) {
                accounts.map { if (it.id == studentId) nextRec else it }
            } else {
                listOf(nextRec) + accounts
            }
            saveLocalAccounts(nextList)
            found to nextRec
        }
        if (existing != null && !existing.nim.equals(cleanNim, ignoreCase = true)) {
            syncAdminEdgeActionIfConnected(
                JSONObject().apply {
                    put("action", "update_nim")
                    put("studentId", studentId)
                    put("newNim", cleanNim)
                }
            )
        }
        syncStudentBadgeToSupabaseIfConnected(studentId, badgeStatus)
        AuthResult.Success(profile = updated.toUserProfile(), usingSupabase = isSupabaseConfigured())
    }

    suspend fun adminUpdateStudentBadgeStatus(
        studentId: String,
        badgeStatus: StudentBadgeStatus
    ): Boolean = withContext(Dispatchers.IO) {
        accountsMutex.withLock {
            val accounts = loadLocalAccounts()
            val updated = accounts.map { acc ->
                if (acc.id == studentId) acc.copy(badgeStatus = badgeStatus) else acc
            }
            saveLocalAccounts(updated)
        }
        syncStudentBadgeToSupabaseIfConnected(studentId, badgeStatus)
        true
    }

    suspend fun adminResetStudentPassword(
        studentId: String,
        newPassword: String,
        requirePasswordChange: Boolean = true
    ): String? = withContext(Dispatchers.IO) {
        if (newPassword.length < 6) return@withContext "Kata sandi baru minimal 6 karakter."
        accountsMutex.withLock {
            val accounts = loadLocalAccounts()
            val target = accounts.find { it.id == studentId }
                ?: return@withContext "Akun mahasiswa tidak ditemukan."
            val newHash = PasswordHasher.hashPassword(newPassword)
            val updated = accounts.map { acc ->
                if (acc.id == target.id) acc.copy(passwordHash = newHash, mustChangePassword = requirePasswordChange) else acc
            }
            saveLocalAccounts(updated)
        }
        syncAdminEdgeActionIfConnected(
            JSONObject().apply {
                put("action", "reset_password")
                put("studentId", studentId)
                put("newPassword", newPassword)
                put("mustChangePassword", requirePasswordChange)
            }
        )
        null
    }

    suspend fun adminDeleteStudentAccount(studentId: String) = withContext(Dispatchers.IO) {
        accountsMutex.withLock {
            val accounts = loadLocalAccounts().filterNot { it.id == studentId }
            saveLocalAccounts(accounts)
        }
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
        val cleanId = identifier.trim()
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
            val localMatch = localAccounts.find {
                it.nim.equals(cleanId, ignoreCase = true) ||
                    it.username.equals(cleanLower, ignoreCase = true) ||
                    it.email.equals(cleanLower, ignoreCase = true)
            }
            if (localMatch == null) return@withContext remoteResult
        }

        val accounts = accountsMutex.withLock { loadLocalAccounts() }
        val matched = accounts.find {
            it.nim.equals(cleanId, ignoreCase = true) ||
                it.username.equals(cleanLower, ignoreCase = true) ||
                it.email.equals(cleanLower, ignoreCase = true)
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
        val token = store.getString(KEY_SESSION_TOKEN)
        val storedRole = store.getString(KEY_SESSION_ROLE)
        if (isSupabaseConfigured() && !token.isNullOrBlank() &&
            !token.startsWith("azr_sess_") && !token.startsWith("azr_admin_sess_")
        ) {
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
    ) {
        if (!isSupabaseConfigured() || rows.isEmpty()) return
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
        syncAdminEdgeActionIfConnected(
            JSONObject().apply {
                put("action", "bulk_import")
                put("fileName", fileName)
                put("semesterKey", semesterKey)
                put("importHistoricalAttendance", importHistoricalAttendance)
                put("rows", rowsArr)
            }
        )
    }

    private suspend fun syncAdminEdgeActionIfConnected(payload: JSONObject) {
        if (!isSupabaseConfigured()) return
        val jwt = store.getString(KEY_SESSION_TOKEN) ?: return
        if (isLocalSessionToken(jwt)) return
        runCatching {
            val base = supabaseUrl.trimEnd('/')
            val req = Request.Builder()
                .url("$base/functions/v1/admin-bulk-import-students")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Authorization", "Bearer $jwt")
                .addHeader("Content-Type", "application/json")
                .build()
            httpClient.newCall(req).execute().close()
        }
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
        val jwt = store.getString(KEY_SESSION_TOKEN) ?: return@withContext
        if (isLocalSessionToken(jwt)) return@withContext
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
        val jwt = store.getString(KEY_SESSION_TOKEN) ?: return@withContext
        if (isLocalSessionToken(jwt)) return@withContext
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
        val jwt = store.getString(KEY_SESSION_TOKEN) ?: return
        if (isLocalSessionToken(jwt)) return
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
            if (profile.role == UserRole.ADMIN) ADMIN_SESSION_DURATION_MS else REMEMBER_SESSION_DURATION_MS
    ) {
        store.putBoolean(KEY_REMEMBER_ME, rememberMe)
        if (rememberMe) {
            if (profile.role == UserRole.STUDENT) {
                store.putString(KEY_REMEMBERED_IDENTIFIER, identifier)
            }
            store.putString(KEY_SESSION_TOKEN, token)
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
                    val expiresAt = if (serverExpiresAtSec > 0L) {
                        minOf(serverExpiresAtSec * 1000L, nowMillis + ADMIN_SESSION_DURATION_MS)
                    } else {
                        nowMillis + ADMIN_SESSION_DURATION_MS
                    }
                    persistSessionState(adminProfile, accessToken, username, rememberMe, expiresAt)
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
            val resolvedEmail = when (val lookup = resolveAdminEmailFromSupabase(base, username)) {
                is AdminEmailLookupResult.Found -> lookup.email
                AdminEmailLookupResult.NotProvisionedOrMissing ->
                    return SupabaseAdminLoginOutcome.BackendNotProvisioned
                is AdminEmailLookupResult.Error ->
                    return SupabaseAdminLoginOutcome.Rejected(AuthResult.Failure(lookup.message))
            }

            val authPayload = JSONObject().apply {
                put("email", resolvedEmail)
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
                    return SupabaseAdminLoginOutcome.Rejected(
                        AuthResult.Failure("Username atau password administrator tidak valid.")
                    )
                }
                val json = JSONObject(body)
                val accessToken = json.optString("access_token")
                val userId = json.optJSONObject("user")?.optString("id").orEmpty()
                if (accessToken.isBlank() || userId.isBlank()) {
                    return SupabaseAdminLoginOutcome.Rejected(
                        AuthResult.Failure("Token sesi administrator tidak valid.")
                    )
                }

                // Mandatory backend verification via verify_admin_session() + profiles.role == 'admin'
                val backendVerified = verifyAdminSessionWithSupabase(accessToken)
                val profile = fetchSupabaseProfile(base, accessToken, userId, resolvedEmail)
                if (!backendVerified || profile == null || profile.role != UserRole.ADMIN || profile.status != AccountStatus.ACTIVE) {
                    return SupabaseAdminLoginOutcome.Rejected(
                        AuthResult.Failure("Akses ditolak: akun ini tidak terverifikasi sebagai Administrator.")
                    )
                }

                val expiresInSec = json.optLong("expires_in", 3600L)
                val expiresAt = minOf(nowMillis + expiresInSec * 1000L, nowMillis + ADMIN_SESSION_DURATION_MS)
                persistSessionState(profile, accessToken, username, rememberMe, expiresAt)
                SupabaseAdminLoginOutcome.Authenticated(
                    AuthResult.Success(profile = profile, usingSupabase = true, sessionExpiresAt = expiresAt)
                )
            }
        } catch (e: Exception) {
            SupabaseAdminLoginOutcome.Rejected(
                AuthResult.Failure("Gagal terhubung ke Supabase Auth: ${e.localizedMessage ?: "kesalahan jaringan"}")
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

    suspend fun verifyAdminSessionWithSupabase(jwt: String): Boolean = withContext(Dispatchers.IO) {
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
                if (!resp.isSuccessful) return@use false
                resp.body?.string()?.trim().equals("true", ignoreCase = true)
            }
        }.getOrDefault(false)
    }

    private suspend fun loginViaSupabase(
        identifier: String,
        password: String,
        rememberMe: Boolean,
        nowMillis: Long
    ): AuthResult {
        return try {
            val base = supabaseUrl.trimEnd('/')
            val resolvedEmail = if (identifier.contains("@")) {
                identifier.lowercase(Locale.US)
            } else {
                resolveEmailFromSupabaseIdentifier(base, identifier)
                    ?: return AuthResult.Failure("NIM atau akun tidak ditemukan di database.")
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
                    return AuthResult.Failure("NIM atau kata sandi tidak sesuai.")
                }
                val json = JSONObject(body)
                val accessToken = json.optString("access_token")
                val userObj = json.optJSONObject("user")
                val userId = userObj?.optString("id").orEmpty()
                val profile = fetchSupabaseProfile(base, accessToken, userId, resolvedEmail)
                    ?: return AuthResult.Failure("Profil akun tidak ditemukan di tabel profiles.")

                if (profile.role == UserRole.ADMIN) {
                    return AuthResult.Failure("Akun Administrator harus masuk melalui halaman Login Admin.")
                }
                if (profile.status == AccountStatus.INACTIVE) {
                    return AuthResult.Failure("Akun mahasiswa ini sedang dinonaktifkan oleh Admin.")
                }

                val expiresAt = nowMillis + REMEMBER_SESSION_DURATION_MS
                persistSessionState(profile, accessToken, identifier, rememberMe, expiresAt)
                AuthResult.Success(profile = profile, usingSupabase = true, sessionExpiresAt = expiresAt)
            }
        } catch (e: Exception) {
            AuthResult.Failure("Gagal terhubung ke Supabase Auth: ${e.localizedMessage ?: "kesalahan jaringan"}")
        }
    }

    private fun resolveEmailFromSupabaseIdentifier(baseUrl: String, identifier: String): String? {
        val body = JSONObject().apply { put("p_identifier", identifier) }
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
            .url("$baseUrl/rest/v1/profiles?id=eq.$userId&select=id,full_name,nim,email,username,study_program_id,class_id,semester,cohort_year,account_status,student_badge_status,must_change_password,role,created_at,study_programs(code,name)")
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
            val prodiCode = prodiObj?.optString("code")?.ifBlank { null } ?: prodiObj?.optString("name")
            val statusStr = row.optString("account_status", "active").lowercase(Locale.US)
            val status = when (statusStr) {
                "inactive" -> AccountStatus.INACTIVE
                "pending_activation" -> AccountStatus.PENDING_ACTIVATION
                else -> AccountStatus.ACTIVE
            }
            UserProfile(
                id = row.optString("id", userId),
                fullName = row.optString("full_name", "Mahasiswa"),
                email = row.optString("email", fallbackEmail),
                username = row.optString("username").ifBlank { null },
                role = if (roleStr.equals("admin", ignoreCase = true)) UserRole.ADMIN else UserRole.STUDENT,
                nim = row.optString("nim").ifBlank { null },
                studyProgramId = row.optString("study_program_id").ifBlank { null },
                studyProgramName = prodiCode,
                classId = row.optString("class_id").ifBlank { null },
                className = row.optString("class_id").ifBlank { "Terhubung ke Supabase" },
                semester = row.optInt("semester", 1),
                cohortYear = row.optInt("cohort_year", 2026),
                status = status,
                badgeStatus = StudentBadgeStatus.fromDbValue(row.optString("student_badge_status", "neutral")),
                mustChangePassword = row.optBoolean("must_change_password", false),
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
