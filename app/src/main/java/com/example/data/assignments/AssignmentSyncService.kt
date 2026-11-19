package com.example.data.assignments

import com.example.BuildConfig
import com.example.data.auth.AuthKeyValueStore
import com.example.data.gamification.XpAndStreakEngine
import com.example.ui.model.AcademicClass
import com.example.ui.model.Assignment
import com.example.ui.model.AssignmentChecklistRecord
import com.example.ui.model.AssignmentTargetType
import com.example.ui.model.GradeAuditEntry
import com.example.ui.model.StudentRecord
import com.example.ui.model.StudyProgram
import com.example.ui.model.SubmissionMethod
import com.example.ui.model.SubmissionRecord
import com.example.ui.model.SubmissionStatus
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class RemoteAssignmentSyncSnapshot(
    val assignments: List<Assignment>? = null,
    val submissions: List<SubmissionRecord>? = null,
    val checklists: List<AssignmentChecklistRecord>? = null,
    val serverTimeMillis: Long? = null,
    val syncedFromSupabase: Boolean = false
)

sealed interface RemoteMutationResult {
    data class Success(val syncedRemote: Boolean, val uploadedFileUrl: String? = null) : RemoteMutationResult
    data class Rejected(val message: String) : RemoteMutationResult
}

/**
 * Dedicated repository/sync service for Assignments, Student Submissions,
 * Individual Admin Verification Checklists, and Backend Server-Time Deadline Validation.
 *
 * Security guarantees:
 * - Uses only the caller's authenticated Supabase JWT (never service_role key).
 * - All deadline checks and role checks are enforced BOTH locally (WIB + server clock offset)
 *   and on Supabase via RLS policies, BEFORE triggers, and SECURITY DEFINER/INVOKER RPCs.
 */
class AssignmentSyncService(
    private val store: AuthKeyValueStore,
    private val supabaseUrl: String = BuildConfig.SUPABASE_URL,
    private val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    companion object {
        private const val KEY_SUBMISSIONS_JSON = "azril_submissions_v2"
        private const val KEY_CHECKLISTS_JSON = "azril_assignment_checklists_v1"
        private const val KEY_SESSION_TOKEN = "session_token"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val WIB_TZ: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")

        fun parseIsoTimestampMillis(raw: String?): Long? {
            val clean = raw?.trim().orEmpty()
            if (clean.isEmpty()) return null
            // Primary: java.time OffsetDateTime / Instant (handles fractional seconds & +07:00 / +00:00 / Z)
            runCatching { return OffsetDateTime.parse(clean).toInstant().toEpochMilli() }
            runCatching { return Instant.parse(clean).toEpochMilli() }
            // Fallback for PostgreSQL space-separated timestamps ("2026-12-15 23:59:00+07")
            val normalized = clean.replace(' ', 'T')
            runCatching { return OffsetDateTime.parse(normalized).toInstant().toEpochMilli() }
            val patterns = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd HH:mm:ss"
            )
            for (pattern in patterns) {
                val parsed = runCatching {
                    SimpleDateFormat(pattern, Locale.US).apply {
                        timeZone = if (pattern.endsWith("'Z'")) TimeZone.getTimeZone("UTC") else WIB_TZ
                    }.parse(clean)?.time
                }.getOrNull()
                if (parsed != null) return parsed
            }
            return null
        }

        fun formatEpochIsoUtc(epochMillis: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return sdf.format(Date(epochMillis))
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

    private suspend fun activeSupabaseJwt(): String? {
        val jwt = store.getString(KEY_SESSION_TOKEN) ?: return null
        if (jwt.isBlank() || jwt.startsWith("azr_sess_") || jwt.startsWith("azr_admin_sess_")) return null
        return jwt
    }

    // --- LOCAL PERSISTENCE (SUBMISSIONS & CHECKLISTS) ---

    suspend fun saveLocalSubmissions(submissions: List<SubmissionRecord>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        submissions.forEach { s ->
            val historyArr = JSONArray()
            s.gradeHistory.forEach { h ->
                historyArr.put(JSONObject().apply {
                    put("changed_at", h.changedAt)
                    if (h.previousScore != null) put("previous_score", h.previousScore)
                    put("new_score", h.newScore)
                    put("note", h.note)
                })
            }
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("assignment_id", s.assignmentId)
                put("assignment_title", s.assignmentTitle)
                put("class_name", s.className)
                put("student_id", s.studentId)
                put("student_name", s.studentName)
                put("student_nim", s.studentNim)
                put("answer_text", s.answerText ?: "")
                put("drive_link", s.driveLink ?: "")
                put("uploaded_file_name", s.uploadedFileName ?: "")
                put("uploaded_file_url", s.uploadedFileUrl ?: "")
                put("uploaded_file_size_label", s.uploadedFileSizeLabel ?: "")
                put("submitted_at", s.submittedAt)
                put("first_submitted_at", s.firstSubmittedAt)
                put("last_updated_at", s.lastUpdatedAt)
                put("first_submitted_at_millis", s.firstSubmittedAtMillis)
                put("last_updated_at_millis", s.lastUpdatedAtMillis)
                put("status", s.status.name)
                put("is_late", s.isLate)
                put("revision_count", s.revisionCount)
                if (s.finalScore != null) put("final_score", s.finalScore)
                put("admin_feedback", s.adminFeedback ?: "")
                put("grade_history", historyArr)
            })
        }
        store.putString(KEY_SUBMISSIONS_JSON, arr.toString())
    }

    suspend fun loadLocalSubmissions(): List<SubmissionRecord> = withContext(Dispatchers.IO) {
        val raw = store.getString(KEY_SUBMISSIONS_JSON) ?: return@withContext emptyList()
        runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val submittedAt = o.optString("submitted_at", "")
                val historyArr = o.optJSONArray("grade_history")
                val history = if (historyArr == null) emptyList() else {
                    (0 until historyArr.length()).map { idx ->
                        val h = historyArr.getJSONObject(idx)
                        GradeAuditEntry(
                            changedAt = h.optString("changed_at", ""),
                            previousScore = if (h.has("previous_score")) h.optInt("previous_score") else null,
                            newScore = h.optInt("new_score", 0),
                            note = h.optString("note", "")
                        )
                    }
                }
                SubmissionRecord(
                    id = o.getString("id"),
                    assignmentId = o.getString("assignment_id"),
                    assignmentTitle = o.optString("assignment_title", ""),
                    className = o.optString("class_name", ""),
                    studentId = o.getString("student_id"),
                    studentName = o.optString("student_name", ""),
                    studentNim = o.optString("student_nim", "-"),
                    answerText = o.optString("answer_text").ifBlank { null },
                    driveLink = o.optString("drive_link").ifBlank { null },
                    uploadedFileName = o.optString("uploaded_file_name").ifBlank { null },
                    uploadedFileUrl = o.optString("uploaded_file_url").ifBlank { null },
                    uploadedFileSizeLabel = o.optString("uploaded_file_size_label").ifBlank { null },
                    submittedAt = submittedAt,
                    firstSubmittedAt = o.optString("first_submitted_at", submittedAt).ifBlank { submittedAt },
                    lastUpdatedAt = o.optString("last_updated_at", submittedAt).ifBlank { submittedAt },
                    firstSubmittedAtMillis = o.optLong("first_submitted_at_millis", 0L),
                    lastUpdatedAtMillis = o.optLong("last_updated_at_millis", 0L),
                    status = runCatching {
                        SubmissionStatus.valueOf(o.optString("status", SubmissionStatus.AWAITING_ADMIN.name))
                    }.getOrDefault(SubmissionStatus.AWAITING_ADMIN),
                    isLate = o.optBoolean("is_late", false),
                    revisionCount = o.optInt("revision_count", 0),
                    finalScore = if (o.has("final_score")) o.optInt("final_score") else null,
                    adminFeedback = o.optString("admin_feedback").ifBlank { null },
                    gradeHistory = history
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun saveLocalChecklists(checklists: List<AssignmentChecklistRecord>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        checklists.forEach { c ->
            arr.put(JSONObject().apply {
                put("assignment_id", c.assignmentId)
                put("student_id", c.studentId)
                put("is_completed", c.isCompleted)
                put("verified_by_id", c.verifiedById ?: "")
                put("verified_by_name", c.verifiedByName ?: "")
                put("verified_at", c.verifiedAt ?: "")
                put("verification_note", c.verificationNote ?: "")
                put("updated_at_millis", c.updatedAtMillis)
            })
        }
        store.putString(KEY_CHECKLISTS_JSON, arr.toString())
    }

    suspend fun loadLocalChecklists(): List<AssignmentChecklistRecord> = withContext(Dispatchers.IO) {
        val raw = store.getString(KEY_CHECKLISTS_JSON) ?: return@withContext emptyList()
        runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AssignmentChecklistRecord(
                    assignmentId = o.getString("assignment_id"),
                    studentId = o.getString("student_id"),
                    isCompleted = o.optBoolean("is_completed", false),
                    verifiedById = o.optString("verified_by_id").ifBlank { null },
                    verifiedByName = o.optString("verified_by_name").ifBlank { null },
                    verifiedAt = o.optString("verified_at").ifBlank { null },
                    verificationNote = o.optString("verification_note").ifBlank { null },
                    updatedAtMillis = o.optLong("updated_at_millis", System.currentTimeMillis())
                )
            }
        }.getOrDefault(emptyList())
    }

    // --- SUPABASE SYNC: FETCH EXISTING ASSIGNMENTS, SUBMISSIONS, CHECKLISTS & SERVER TIME ---

    suspend fun fetchRemoteSnapshot(
        knownClasses: List<AcademicClass>,
        knownStudyPrograms: List<StudyProgram>,
        knownStudents: List<StudentRecord>,
        knownAssignments: List<Assignment>
    ): RemoteAssignmentSyncSnapshot = withContext(Dispatchers.IO) {
        if (!isSupabaseConfigured()) return@withContext RemoteAssignmentSyncSnapshot()
        val jwt = activeSupabaseJwt() ?: return@withContext RemoteAssignmentSyncSnapshot()
        val base = supabaseUrl.trimEnd('/')

        val serverTime = fetchServerTimeMillis(base, jwt)
        val remoteAssignments = fetchAssignmentsFromSupabase(base, jwt, knownClasses, knownStudyPrograms)
        val mergedAssignmentsForLookup = buildList {
            val seen = mutableSetOf<String>()
            remoteAssignments?.forEach { if (seen.add(it.id)) add(it) }
            knownAssignments.forEach { if (seen.add(it.id)) add(it) }
        }
        val remoteSubmissions = fetchSubmissionsFromSupabase(base, jwt, knownStudents, mergedAssignmentsForLookup)
        val remoteChecklists = fetchChecklistsFromSupabase(base, jwt)

        RemoteAssignmentSyncSnapshot(
            assignments = remoteAssignments,
            submissions = remoteSubmissions,
            checklists = remoteChecklists,
            serverTimeMillis = serverTime,
            syncedFromSupabase = remoteAssignments != null || remoteSubmissions != null || remoteChecklists != null
        )
    }

    private fun fetchServerTimeMillis(base: String, jwt: String): Long? = runCatching {
        val req = Request.Builder()
            .url("$base/rest/v1/rpc/get_server_time_utc")
            .post("{}".toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("apikey", supabaseAnonKey.trim())
            .addHeader("Authorization", "Bearer $jwt")
            .addHeader("Content-Type", "application/json")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val raw = resp.body?.string()?.trim()?.removeSurrounding("\"")
            parseIsoTimestampMillis(raw)
        }
    }.getOrNull()

    private fun fetchAssignmentsFromSupabase(
        base: String,
        jwt: String,
        knownClasses: List<AcademicClass>,
        knownStudyPrograms: List<StudyProgram>
    ): List<Assignment>? = runCatching {
        val req = Request.Builder()
            .url("$base/rest/v1/assignments?select=*,assignment_targets(*)&order=created_at.desc")
            .get()
            .addHeader("apikey", supabaseAnonKey.trim())
            .addHeader("Authorization", "Bearer $jwt")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val arr = JSONArray(resp.body?.string().orEmpty())
            val classById = knownClasses.associateBy { it.id }
            val prodiById = knownStudyPrograms.associateBy { it.id }
            val prodiByCode = knownStudyPrograms.associateBy { it.code.uppercase(Locale.US) }

            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val targetArr = o.optJSONArray("assignment_targets")
                val targetClassIds = mutableListOf<String>()
                val targetClassNames = mutableListOf<String>()
                val targetProdiIds = mutableListOf<String>()
                val targetProdiCodes = mutableListOf<String>()

                if (targetArr != null) {
                    for (tIdx in 0 until targetArr.length()) {
                        val t = targetArr.getJSONObject(tIdx)
                        val cId = t.optString("class_id").takeIf { it.isNotBlank() && it != "null" }
                        val pId = t.optString("study_program_id").takeIf { it.isNotBlank() && it != "null" }
                        val pCode = t.optString("study_program_code").takeIf { it.isNotBlank() && it != "null" }
                        if (cId != null && cId !in targetClassIds) {
                            targetClassIds += cId
                            targetClassNames += (classById[cId]?.name ?: cId)
                        }
                        if (pId != null && pId !in targetProdiIds) {
                            targetProdiIds += pId
                        }
                        val resolvedCode = pCode ?: pId?.let { prodiById[it]?.code }
                        if (resolvedCode != null && resolvedCode !in targetProdiCodes) {
                            targetProdiCodes += resolvedCode.uppercase(Locale.US)
                        }
                        if (pId == null && resolvedCode != null) {
                            prodiByCode[resolvedCode.uppercase(Locale.US)]?.id?.let { mappedId ->
                                if (mappedId !in targetProdiIds) targetProdiIds += mappedId
                            }
                        }
                    }
                }

                // Graceful fallback if legacy row used direct class_id column
                val legacyClassId = o.optString("class_id").takeIf { it.isNotBlank() && it != "null" }
                if (targetClassIds.isEmpty() && legacyClassId != null) {
                    targetClassIds += legacyClassId
                    targetClassNames += (classById[legacyClassId]?.name ?: legacyClassId)
                }

                val targetType = AssignmentTargetType.fromDbValue(o.optString("target_type", "class"))
                val deadlineRaw = o.optString("deadline", "")
                val deadlineMillis = parseIsoTimestampMillis(deadlineRaw)
                val deadlineLabel = if (deadlineMillis != null) {
                    XpAndStreakEngine.formatWibDateTimeLabel(deadlineMillis)
                } else {
                    deadlineRaw.ifBlank { "-" }
                }
                val createdAtRaw = o.optString("created_at", "")
                val createdAtMillis = parseIsoTimestampMillis(createdAtRaw)
                val createdAtLabel = if (createdAtMillis != null) {
                    XpAndStreakEngine.formatWibDateTimeLabel(createdAtMillis)
                } else {
                    createdAtRaw
                }

                val formatsArr = o.optJSONArray("allowed_formats")
                val formats = if (formatsArr == null || formatsArr.length() == 0) {
                    listOf("PDF", "DOCX")
                } else {
                    (0 until formatsArr.length()).mapNotNull { idx ->
                        formatsArr.optString(idx).takeIf { it.isNotBlank() }
                    }.ifEmpty { listOf("PDF", "DOCX") }
                }

                val f1Name = o.optString("file1_name").takeIf { it.isNotBlank() && it != "null" }
                val f1Url = o.optString("file1_url").takeIf { it.isNotBlank() && it != "null" }
                val f2Name = o.optString("file2_name").takeIf { it.isNotBlank() && it != "null" }
                val f2Url = o.optString("file2_url").takeIf { it.isNotBlank() && it != "null" }

                Assignment(
                    id = o.getString("id"),
                    title = o.optString("title", "Tugas"),
                    description = o.optString("description", ""),
                    instructions = o.optString("instructions", o.optString("description", "")),
                    classId = targetClassIds.firstOrNull().orEmpty(),
                    className = when (targetType) {
                        AssignmentTargetType.CLASS -> targetClassNames.joinToString(", ")
                        AssignmentTargetType.STUDY_PROGRAM ->
                            if (targetProdiCodes.isEmpty()) "Program Studi" else "Prodi ${targetProdiCodes.joinToString(", ")}"
                    },
                    targetType = targetType,
                    targetClassIds = targetClassIds,
                    targetClassNames = targetClassNames,
                    targetStudyProgramIds = targetProdiIds,
                    targetStudyProgramCodes = targetProdiCodes,
                    courseCategory = o.optString("course_category", "Umum").ifBlank { "Umum" },
                    createdAt = createdAtLabel,
                    deadline = deadlineLabel,
                    deadlineEpochMillis = deadlineMillis,
                    allowedFormats = formats,
                    maxFileSizeMb = o.optInt("max_file_size_mb", 10),
                    submissionMethod = runCatching {
                        SubmissionMethod.valueOf(o.optString("submission_method", SubmissionMethod.BOTH.name))
                    }.getOrDefault(SubmissionMethod.BOTH),
                    file1Name = f1Name,
                    file1Url = f1Url,
                    file2Name = f2Name,
                    file2Url = f2Url,
                    materialAttachment = listOfNotNull(f1Name, f2Name).joinToString(" • ").ifBlank { null },
                    isActive = o.optBoolean("is_active", true)
                )
            }
        }
    }.getOrNull()

    private fun fetchSubmissionsFromSupabase(
        base: String,
        jwt: String,
        knownStudents: List<StudentRecord>,
        knownAssignments: List<Assignment>
    ): List<SubmissionRecord>? = runCatching {
        val req = Request.Builder()
            .url("$base/rest/v1/assignment_submissions?select=*&order=last_updated_at.desc")
            .get()
            .addHeader("apikey", supabaseAnonKey.trim())
            .addHeader("Authorization", "Bearer $jwt")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val arr = JSONArray(resp.body?.string().orEmpty())
            val studentById = knownStudents.associateBy { it.id }
            val assignmentById = knownAssignments.associateBy { it.id }

            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val assignmentId = o.getString("assignment_id")
                val studentId = o.getString("student_id")
                val assignment = assignmentById[assignmentId]
                val student = studentById[studentId]

                val firstMillis = parseIsoTimestampMillis(o.optString("first_submitted_at")) ?: System.currentTimeMillis()
                val updatedMillis = parseIsoTimestampMillis(o.optString("last_updated_at")) ?: firstMillis
                val firstLabel = XpAndStreakEngine.formatWibDateTimeLabel(firstMillis)
                val updatedLabel = XpAndStreakEngine.formatWibDateTimeLabel(updatedMillis)

                val statusStr = o.optString("status", "awaiting_admin").lowercase(Locale.US)
                val mappedStatus = when (statusStr) {
                    "submitted" -> SubmissionStatus.SUBMITTED
                    "needs_revision" -> SubmissionStatus.NEEDS_REVISION
                    "graded" -> SubmissionStatus.GRADED
                    else -> SubmissionStatus.AWAITING_ADMIN
                }

                SubmissionRecord(
                    id = o.getString("id"),
                    assignmentId = assignmentId,
                    assignmentTitle = assignment?.title ?: "Tugas",
                    className = student?.className ?: assignment?.targetSummaryLabel().orEmpty(),
                    studentId = studentId,
                    studentName = student?.fullName ?: "Mahasiswa",
                    studentNim = student?.nim ?: "-",
                    answerText = o.optString("answer_text").takeIf { it.isNotBlank() && it != "null" },
                    driveLink = o.optString("submission_url").takeIf { it.isNotBlank() && it != "null" },
                    uploadedFileName = o.optString("file_name").takeIf { it.isNotBlank() && it != "null" },
                    uploadedFileUrl = o.optString("file_url").takeIf { it.isNotBlank() && it != "null" },
                    uploadedFileSizeLabel = o.optString("file_size_label").takeIf { it.isNotBlank() && it != "null" },
                    submittedAt = updatedLabel,
                    firstSubmittedAt = firstLabel,
                    lastUpdatedAt = updatedLabel,
                    firstSubmittedAtMillis = firstMillis,
                    lastUpdatedAtMillis = updatedMillis,
                    status = mappedStatus,
                    isLate = false,
                    revisionCount = o.optInt("revision_count", 0),
                    finalScore = if (!o.isNull("final_score")) o.optInt("final_score") else null,
                    adminFeedback = o.optString("admin_feedback").takeIf { it.isNotBlank() && it != "null" }
                )
            }
        }
    }.getOrNull()

    private fun fetchChecklistsFromSupabase(
        base: String,
        jwt: String
    ): List<AssignmentChecklistRecord>? = runCatching {
        val req = Request.Builder()
            .url("$base/rest/v1/assignment_student_checklists?select=*")
            .get()
            .addHeader("apikey", supabaseAnonKey.trim())
            .addHeader("Authorization", "Bearer $jwt")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val arr = JSONArray(resp.body?.string().orEmpty())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val verifiedAtMillis = parseIsoTimestampMillis(o.optString("verified_at"))
                val updatedAtMillis = parseIsoTimestampMillis(o.optString("updated_at")) ?: System.currentTimeMillis()
                AssignmentChecklistRecord(
                    assignmentId = o.getString("assignment_id"),
                    studentId = o.getString("student_id"),
                    isCompleted = o.optBoolean("is_completed", false),
                    verifiedById = o.optString("verified_by").takeIf { it.isNotBlank() && it != "null" },
                    verifiedByName = o.optString("verified_by_name").takeIf { it.isNotBlank() && it != "null" },
                    verifiedAt = verifiedAtMillis?.let { XpAndStreakEngine.formatWibDateTimeLabel(it) },
                    verificationNote = o.optString("verification_note").takeIf { it.isNotBlank() && it != "null" },
                    updatedAtMillis = updatedAtMillis
                )
            }
        }
    }.getOrNull()

    // --- SUPABASE WRITES: ASSIGNMENTS, SUBMISSIONS (WITH BACKEND DEADLINE CHECK), & ADMIN CHECKLISTS ---

    suspend fun syncCreateAssignmentToSupabase(assignment: Assignment): RemoteMutationResult = withContext(Dispatchers.IO) {
        if (!isSupabaseConfigured()) return@withContext RemoteMutationResult.Success(syncedRemote = false)
        val jwt = activeSupabaseJwt() ?: return@withContext RemoteMutationResult.Success(syncedRemote = false)
        val base = supabaseUrl.trimEnd('/')
        val deadlineIso = formatEpochIsoUtc(assignment.deadlineEpochMillis ?: System.currentTimeMillis())

        val row = JSONObject().apply {
            put("id", assignment.id)
            put("title", assignment.title)
            put("description", assignment.description)
            put("instructions", assignment.instructions)
            put("target_type", assignment.targetType.dbValue)
            put("course_category", assignment.courseCategory)
            put("file1_name", assignment.file1Name ?: JSONObject.NULL)
            put("file1_url", assignment.file1Url ?: JSONObject.NULL)
            put("file2_name", assignment.file2Name ?: JSONObject.NULL)
            put("file2_url", assignment.file2Url ?: JSONObject.NULL)
            put("deadline", deadlineIso)
            put("allowed_formats", JSONArray(assignment.allowedFormats))
            put("max_file_size_mb", assignment.maxFileSizeMb)
            put("submission_method", assignment.submissionMethod.name)
            put("is_active", assignment.isActive)
        }

        val created = runCatching {
            val req = Request.Builder()
                .url("$base/rest/v1/assignments")
                .post(row.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Authorization", "Bearer $jwt")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .addHeader("Content-Type", "application/json")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful && resp.code == 403) {
                    return@withContext RemoteMutationResult.Rejected("Akses ditolak oleh Supabase RLS: hanya Admin yang dapat membuat tugas.")
                }
                resp.isSuccessful
            }
        }.getOrDefault(false)

        if (created) {
            val targets = JSONArray()
            when (assignment.targetType) {
                AssignmentTargetType.CLASS -> {
                    assignment.targetClassIds.forEach { classId ->
                        targets.put(JSONObject().apply {
                            put("assignment_id", assignment.id)
                            put("class_id", classId)
                        })
                    }
                }
                AssignmentTargetType.STUDY_PROGRAM -> {
                    assignment.targetStudyProgramCodes.forEach { code ->
                        targets.put(JSONObject().apply {
                            put("assignment_id", assignment.id)
                            put("study_program_code", code)
                        })
                    }
                }
            }
            if (targets.length() > 0) {
                runCatching {
                    val targetReq = Request.Builder()
                        .url("$base/rest/v1/assignment_targets")
                        .post(targets.toString().toRequestBody(JSON_MEDIA_TYPE))
                        .addHeader("apikey", supabaseAnonKey.trim())
                        .addHeader("Authorization", "Bearer $jwt")
                        .addHeader("Prefer", "return=minimal")
                        .addHeader("Content-Type", "application/json")
                        .build()
                    httpClient.newCall(targetReq).execute().close()
                }
            }
        }
        RemoteMutationResult.Success(syncedRemote = created)
    }

    suspend fun syncToggleAssignmentActiveToSupabase(assignmentId: String, isActive: Boolean) = withContext(Dispatchers.IO) {
        if (!isSupabaseConfigured()) return@withContext
        val jwt = activeSupabaseJwt() ?: return@withContext
        runCatching {
            val base = supabaseUrl.trimEnd('/')
            val req = Request.Builder()
                .url("$base/rest/v1/assignments?id=eq.$assignmentId")
                .patch(JSONObject().put("is_active", isActive).toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Authorization", "Bearer $jwt")
                .addHeader("Content-Type", "application/json")
                .build()
            httpClient.newCall(req).execute().close()
        }
    }

    suspend fun syncDeleteAssignmentToSupabase(assignmentId: String) = withContext(Dispatchers.IO) {
        if (!isSupabaseConfigured()) return@withContext
        val jwt = activeSupabaseJwt() ?: return@withContext
        runCatching {
            val base = supabaseUrl.trimEnd('/')
            val req = Request.Builder()
                .url("$base/rest/v1/assignments?id=eq.$assignmentId")
                .delete()
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Authorization", "Bearer $jwt")
                .build()
            httpClient.newCall(req).execute().close()
        }
    }

    /**
     * Submits or updates a student's submission via Supabase Storage + RPC `upsert_student_assignment_submission`.
     * Backend strictly enforces server-time deadline (`NOW() <= deadline`) and targeting.
     * If backend explicitly rejects (400/403/deadline error), returns [RemoteMutationResult.Rejected].
     */
    suspend fun syncStudentSubmissionToSupabase(
        submission: SubmissionRecord,
        fileBytes: ByteArray? = null,
        mimeType: String? = null
    ): RemoteMutationResult = withContext(Dispatchers.IO) {
        if (!isSupabaseConfigured()) return@withContext RemoteMutationResult.Success(syncedRemote = false)
        val jwt = activeSupabaseJwt() ?: return@withContext RemoteMutationResult.Success(syncedRemote = false)
        val base = supabaseUrl.trimEnd('/')

        var effectiveFileUrl = submission.uploadedFileUrl

        // Optional file upload to private Supabase Storage bucket `assignment-submissions`
        if (fileBytes != null && !submission.uploadedFileName.isNullOrBlank()) {
            val safeName = submission.uploadedFileName
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .take(120)
            val objectPath = "${submission.assignmentId}/${submission.studentId}/$safeName"
            val uploadOutcome = runCatching {
                val media = (mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream").toMediaType()
                val req = Request.Builder()
                    .url("$base/storage/v1/object/assignment-submissions/$objectPath")
                    .post(fileBytes.toRequestBody(media))
                    .addHeader("apikey", supabaseAnonKey.trim())
                    .addHeader("Authorization", "Bearer $jwt")
                    .addHeader("x-upsert", "true")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        effectiveFileUrl = "$base/storage/v1/object/authenticated/assignment-submissions/$objectPath"
                        null
                    } else if (resp.code == 400 || resp.code == 403) {
                        val err = extractSupabaseErrorMessage(resp.body?.string().orEmpty())
                        if (err.contains("deadline", ignoreCase = true) || err.contains("row-level security", ignoreCase = true)) {
                            "Upload ditolak oleh server: deadline tugas telah berakhir atau akses tidak diizinkan."
                        } else null
                    } else null
                }
            }.getOrNull()
            if (uploadOutcome != null) {
                return@withContext RemoteMutationResult.Rejected(uploadOutcome)
            }
        }

        val payload = JSONObject().apply {
            put("p_assignment_id", submission.assignmentId)
            put("p_answer_text", submission.answerText ?: "")
            put("p_submission_url", submission.driveLink ?: JSONObject.NULL)
            put("p_file_name", submission.uploadedFileName ?: JSONObject.NULL)
            put("p_file_url", effectiveFileUrl ?: JSONObject.NULL)
            put("p_file_size_label", submission.uploadedFileSizeLabel ?: JSONObject.NULL)
        }

        return@withContext runCatching {
            val req = Request.Builder()
                .url("$base/rest/v1/rpc/upsert_student_assignment_submission")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Authorization", "Bearer $jwt")
                .addHeader("Content-Type", "application/json")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                when {
                    resp.isSuccessful -> RemoteMutationResult.Success(syncedRemote = true, uploadedFileUrl = effectiveFileUrl)
                    resp.code == 404 -> RemoteMutationResult.Success(syncedRemote = false, uploadedFileUrl = effectiveFileUrl)
                    else -> {
                        val msg = extractSupabaseErrorMessage(rawBody)
                            .ifBlank { "Pengumpulan ditolak oleh server Supabase (kode ${resp.code})." }
                        RemoteMutationResult.Rejected(msg)
                    }
                }
            }
        }.getOrElse {
            RemoteMutationResult.Success(syncedRemote = false, uploadedFileUrl = effectiveFileUrl)
        }
    }

    /**
     * Admin-only checklist toggle via Supabase RPC `admin_set_assignment_checklist`.
     * Non-admins are strictly rejected by backend RLS & SECURITY DEFINER check.
     */
    suspend fun syncAdminChecklistToSupabase(
        assignmentId: String,
        studentId: String,
        isCompleted: Boolean,
        note: String? = null
    ): RemoteMutationResult = withContext(Dispatchers.IO) {
        if (!isSupabaseConfigured()) return@withContext RemoteMutationResult.Success(syncedRemote = false)
        val jwt = activeSupabaseJwt() ?: return@withContext RemoteMutationResult.Success(syncedRemote = false)
        val base = supabaseUrl.trimEnd('/')

        val payload = JSONObject().apply {
            put("p_assignment_id", assignmentId)
            put("p_student_id", studentId)
            put("p_is_completed", isCompleted)
            put("p_note", note ?: JSONObject.NULL)
        }

        return@withContext runCatching {
            val req = Request.Builder()
                .url("$base/rest/v1/rpc/admin_set_assignment_checklist")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", supabaseAnonKey.trim())
                .addHeader("Authorization", "Bearer $jwt")
                .addHeader("Content-Type", "application/json")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                when {
                    resp.isSuccessful -> RemoteMutationResult.Success(syncedRemote = true)
                    resp.code == 404 -> RemoteMutationResult.Success(syncedRemote = false)
                    else -> {
                        val msg = extractSupabaseErrorMessage(rawBody)
                            .ifBlank { "Akses ditolak: hanya Administrator yang dapat mengubah checklist." }
                        RemoteMutationResult.Rejected(msg)
                    }
                }
            }
        }.getOrElse {
            RemoteMutationResult.Success(syncedRemote = false)
        }
    }

    private fun extractSupabaseErrorMessage(rawBody: String): String {
        if (rawBody.isBlank()) return ""
        return runCatching {
            val json = JSONObject(rawBody)
            json.optString("message").ifBlank {
                json.optString("error").ifBlank { json.optString("details") }
            }
        }.getOrDefault("")
    }
}
