package com.example

import com.example.data.auth.AuthKeyValueStore
import com.example.data.auth.AuthResult
import com.example.data.auth.AzrilAuthRepository
import com.example.data.auth.OfficialStudyPrograms
import com.example.ui.model.UserRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class InMemoryAuthStore : AuthKeyValueStore {
    val strings = mutableMapOf<String, String>()
    val booleans = mutableMapOf<String, Boolean>()
    val longs = mutableMapOf<String, Long>()

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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthAndRankingTest {

    private fun createRepo(store: InMemoryAuthStore): AzrilAuthRepository =
        AzrilAuthRepository(
            store = store,
            supabaseUrl = "https://YOUR_PROJECT_REF.supabase.co",
            supabaseAnonKey = "YOUR_SUPABASE_ANON_KEY"
        )

    @Test
    fun officialStudyProgramsContainExactlyHesPjkrFarmasi() {
        val codes = OfficialStudyPrograms.list.map { it.code }
        assertEquals(listOf("HES", "PJKR", "FARMASI"), codes)
    }

    @Test
    fun registerLoginRememberMeLogoutAndNoPlaintextPassword() = runTest {
        val store = InMemoryAuthStore()
        val repo = createRepo(store)
        val rawPassword = "SuperSecretPassword123!"

        // 1. Register new student with HES
        val regResult = repo.registerStudent(
            fullName = "Azril Mahasiswa",
            nim = "202601001",
            emailOrUsername = "azril.mhs@kampus.ac.id",
            password = rawPassword,
            confirmPassword = rawPassword,
            studyProgramCode = "HES",
            rememberMe = true
        )
        assertTrue(regResult is AuthResult.Success)
        val registeredProfile = (regResult as AuthResult.Success).profile
        assertEquals("Azril Mahasiswa", registeredProfile.fullName)
        assertEquals("202601001", registeredProfile.nim)
        assertEquals("HES", registeredProfile.studyProgramName)
        assertEquals(UserRole.STUDENT, registeredProfile.role)

        // 7. Ensure plaintext password is NEVER stored anywhere in key-value store
        assertTrue(store.strings.values.none { it.contains(rawPassword) })

        // Reject invalid study program outside HES/PJKR/FARMASI
        val invalidProdi = repo.registerStudent(
            fullName = "Budi Test",
            nim = "202601002",
            emailOrUsername = "budi@kampus.ac.id",
            password = rawPassword,
            confirmPassword = rawPassword,
            studyProgramCode = "TEKNIK",
            rememberMe = true
        )
        assertTrue(invalidProdi is AuthResult.Failure)

        // Reject duplicate NIM
        val duplicateNim = repo.registerStudent(
            fullName = "Budi Duplikat",
            nim = "202601001",
            emailOrUsername = "budi2@kampus.ac.id",
            password = rawPassword,
            confirmPassword = rawPassword,
            studyProgramCode = "PJKR",
            rememberMe = true
        )
        assertTrue(duplicateNim is AuthResult.Failure)

        // Reject duplicate email/username
        val duplicateEmail = repo.registerStudent(
            fullName = "Budi Email Sama",
            nim = "202601009",
            emailOrUsername = "azril.mhs@kampus.ac.id",
            password = rawPassword,
            confirmPassword = rawPassword,
            studyProgramCode = "FARMASI",
            rememberMe = true
        )
        assertTrue(duplicateEmail is AuthResult.Failure)

        // 4. Close & reopen app with Remember Me -> session persists automatically
        val restored = repo.restoreSession()
        assertNotNull(restored.profile)
        assertEquals("Azril Mahasiswa", restored.profile?.fullName)
        assertEquals("202601001", restored.profile?.nim)
        assertEquals("HES", restored.profile?.studyProgramName)

        // 5. Logout terminates session while remembering email/username for convenience
        repo.logout()
        val afterLogout = repo.restoreSession()
        assertNull(afterLogout.profile)
        assertEquals("azril.mhs@kampus.ac.id", afterLogout.rememberedIdentifier)

        // 2 & 3. Subsequent login using ONLY username + password loads identity automatically
        val loginRes = repo.login(
            identifier = "azril.mhs",
            password = rawPassword,
            rememberMe = false
        )
        assertTrue(loginRes is AuthResult.Success)
        val loggedIn = (loginRes as AuthResult.Success).profile
        assertEquals("Azril Mahasiswa", loggedIn.fullName)
        assertEquals("202601001", loggedIn.nim)
        assertEquals("HES", loggedIn.studyProgramName)

        // Remember Me = false -> reopening app does NOT auto-restore session
        val afterRestartNoRemember = repo.restoreSession()
        assertNull(afterRestartNoRemember.profile)
        assertEquals("", afterRestartNoRemember.rememberedIdentifier)

        // 6. Second student with FARMASI has isolated profile data
        val secondReg = repo.registerStudent(
            fullName = "Siti Farmasi",
            nim = "202602005",
            emailOrUsername = "siti.farmasi",
            password = "AnotherPassword456!",
            confirmPassword = "AnotherPassword456!",
            studyProgramCode = "FARMASI",
            rememberMe = true
        )
        assertTrue(secondReg is AuthResult.Success)
        val secondProfile = (secondReg as AuthResult.Success).profile
        assertEquals("Siti Farmasi", secondProfile.fullName)
        assertEquals("202602005", secondProfile.nim)
        assertEquals("FARMASI", secondProfile.studyProgramName)
        assertEquals(UserRole.STUDENT, secondProfile.role)
        assertTrue(store.strings.values.none { it.contains("AnotherPassword456!") })
    }
}
