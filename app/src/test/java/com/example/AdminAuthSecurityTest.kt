package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.AuthResult
import com.example.data.auth.AzrilAuthRepository
import com.example.ui.model.AccountStatus
import com.example.ui.model.StudentBadgeStatus
import com.example.ui.model.UserRole
import com.example.ui.navigation.AdminDestination
import com.example.ui.state.AuthPortalMode
import com.example.ui.state.AzrilViewModel
import com.example.ui.state.RouteAccessResult
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdminAuthSecurityTest {

    private fun waitUntil(timeoutMs: Long = 8000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate() && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(25)
        }
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun adminLoginSuccessWrongPasswordStudentRejectionAndZeroPlaintextStorage() = runTest {
        val store = InMemoryAuthStore()
        val repo = AzrilAuthRepository(
            store = store,
            supabaseUrl = "https://YOUR_PROJECT_REF.supabase.co",
            supabaseAnonKey = "YOUR_SUPABASE_ANON_KEY"
        )

        val now = 1_795_000_000_000L

        // 1. Admin login with WRONG password -> MUST FAIL
        val wrongPw = repo.loginAdmin("Azril12v", "WrongPassword123", rememberMe = true, nowMillis = now)
        assertTrue(wrongPw is AuthResult.Failure)

        // 2. Admin login with WRONG username -> MUST FAIL
        val wrongUser = repo.loginAdmin("admin_palsu", "Azril23admin7", rememberMe = true, nowMillis = now)
        assertTrue(wrongUser is AuthResult.Failure)

        // 3. Admin login with CORRECT credentials (Azril12v / Azril23admin7) -> MUST SUCCEED
        val success = repo.loginAdmin("Azril12v", "Azril23admin7", rememberMe = true, nowMillis = now)
        assertTrue(success is AuthResult.Success)
        val adminProfile = (success as AuthResult.Success).profile
        assertEquals(UserRole.ADMIN, adminProfile.role)
        assertEquals("Azril12v", adminProfile.username)
        assertEquals(now + AzrilAuthRepository.ADMIN_SESSION_DURATION_MS, success.sessionExpiresAt)

        // Plaintext admin password MUST NEVER be stored in local storage
        assertTrue(store.strings.values.none { it.contains("Azril23admin7") })

        // 4. Restoring Admin session BEFORE expiry -> succeeds
        val restoredValid = repo.restoreSession(nowMillis = now + 60_000L)
        assertNotNull(restoredValid.profile)
        assertEquals(UserRole.ADMIN, restoredValid.profile?.role)
        assertFalse(restoredValid.sessionExpired)

        // 5. Restoring Admin session AFTER 8-hour expiry -> rejected & cleared!
        val restoredExpired = repo.restoreSession(
            nowMillis = now + AzrilAuthRepository.ADMIN_SESSION_DURATION_MS + 1_000L
        )
        assertNull(restoredExpired.profile)
        assertTrue(restoredExpired.sessionExpired)

        // 6. Provision a student account and verify strict role separation between portals
        val createdStudent = repo.adminCreateStudentAccount(
            studentId = "stu-sec-1",
            fullName = "Mahasiswa Uji Keamanan",
            nim = "250901777",
            email = "250901777@mahasiswa.azrilclas.ac.id",
            studyProgramId = "prodi-hes",
            studyProgramCode = "HES",
            classId = "cls-hes-3b",
            className = "HES-3B",
            semester = 3,
            cohortYear = 2025,
            accountStatus = AccountStatus.ACTIVE,
            badgeStatus = StudentBadgeStatus.NEUTRAL,
            initialPassword = "StudentPassword99!"
        )
        assertTrue(createdStudent is AuthResult.Success)

        // Student CANNOT log in through the Admin Login portal!
        val studentOnAdminPortal = repo.loginAdmin("250901777", "StudentPassword99!", rememberMe = false, nowMillis = now)
        assertTrue(studentOnAdminPortal is AuthResult.Failure)
        assertTrue((studentOnAdminPortal as AuthResult.Failure).message.contains("Akses ditolak", ignoreCase = true))

        // Admin CANNOT log in through the Student Login portal!
        val adminOnStudentPortal = repo.login("Azril12v", "Azril23admin7", rememberMe = false, nowMillis = now)
        assertTrue(adminOnStudentPortal is AuthResult.Failure)
        assertTrue((adminOnStudentPortal as AuthResult.Failure).message.contains("Login Admin", ignoreCase = true))

        // Student CAN log in through the Student Login portal
        val studentLogin = repo.login("250901777", "StudentPassword99!", rememberMe = true, nowMillis = now)
        assertTrue(studentLogin is AuthResult.Success)
        assertEquals(UserRole.STUDENT, (studentLogin as AuthResult.Success).profile.role)

        // Logout completely terminates session
        repo.logout()
        val afterLogout = repo.restoreSession(nowMillis = now + 1000L)
        assertNull(afterLogout.profile)
    }

    @Test
    fun routeGuardsBlockUnauthenticatedAndStudentAccessToAdminDashboardAndHandleLogoutAndExpiry() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AzrilViewModel(app)
        waitUntil { !vm.uiState.value.isRestoringSession }

        // Ensure clean unauthenticated starting state
        if (vm.uiState.value.currentUser != null) {
            vm.logout()
            waitUntil { vm.uiState.value.currentUser == null }
        }

        // 1. Unauthenticated user tries to open `/admin/dashboard` directly -> RedirectToAdminLogin (NEVER enters Admin!)
        val unauthAttempt = vm.navigateToRoute("/admin/dashboard")
        assertEquals(RouteAccessResult.RedirectToAdminLogin, unauthAttempt)
        assertNull(vm.uiState.value.currentUser)
        assertEquals(AuthPortalMode.ADMIN_LOGIN, vm.uiState.value.authPortalMode)

        // Also verify unauthenticated user trying direct AdminDestination enum navigation is blocked
        vm.navigateAdmin(AdminDestination.STUDENTS)
        assertNull(vm.uiState.value.currentUser)

        // 2. Wrong password on Admin login screen -> rejected, remains unauthenticated
        vm.loginAdminWithCredentials("Azril12v", "SalahBanget123", rememberMe = false)
        waitUntil { !vm.uiState.value.authInProgress && vm.uiState.value.authErrorMessage != null }
        assertNull(vm.uiState.value.currentUser)

        // 3. Valid Admin login (`Azril12v` / `Azril23admin7`) -> authenticated as ADMIN & can open admin routes
        val loginTime = 1_795_000_000_000L
        vm.loginAdminWithCredentials("Azril12v", "Azril23admin7", rememberMe = false, nowMillis = loginTime)
        waitUntil { vm.uiState.value.currentUser != null && !vm.uiState.value.authInProgress }
        assertEquals(UserRole.ADMIN, vm.uiState.value.currentUser?.role)

        val adminNavAllowed = vm.navigateToRoute("/admin/students", nowMillis = loginTime + 10_000L)
        assertEquals(RouteAccessResult.Allowed, adminNavAllowed)
        assertEquals(AdminDestination.STUDENTS, vm.uiState.value.adminDestination)

        // Provision a student while logged in as Admin
        val addErr = vm.addStudent(
            fullName = "Budi Mahasiswa",
            nim = "250901888",
            email = "250901888@mahasiswa.azrilclas.ac.id",
            studyProgramId = "prodi-hes",
            classId = null,
            semester = 3,
            cohortYear = 2025,
            status = AccountStatus.ACTIVE,
            badgeStatus = StudentBadgeStatus.NEUTRAL,
            initialPassword = "BudiPassword2026!"
        )
        assertNull(addErr)

        // 4. Admin session EXPIRES -> attempting `/admin/dashboard` forces re-authentication!
        val expiredTime = loginTime + AzrilAuthRepository.ADMIN_SESSION_DURATION_MS + 5_000L
        val expiredResult = vm.navigateToRoute("/admin/dashboard", nowMillis = expiredTime)
        assertTrue(expiredResult is RouteAccessResult.SessionExpired)
        assertEquals(AuthPortalMode.ADMIN_LOGIN, (expiredResult as RouteAccessResult.SessionExpired).redirectPortal)
        assertNull(vm.uiState.value.currentUser)
        assertEquals(AuthPortalMode.ADMIN_LOGIN, vm.uiState.value.authPortalMode)

        // Re-authenticate Admin and test explicit Logout
        vm.loginAdminWithCredentials("Azril12v", "Azril23admin7", rememberMe = false)
        waitUntil { vm.uiState.value.currentUser?.role == UserRole.ADMIN && !vm.uiState.value.authInProgress }
        vm.logout()
        assertNull(vm.uiState.value.currentUser)
        // After logout, opening `/admin/settings` MUST redirect to Admin Login
        assertEquals(RouteAccessResult.RedirectToAdminLogin, vm.navigateToRoute("/admin/settings"))
        assertNull(vm.uiState.value.currentUser)

        // 5. Log in as STUDENT and try to open Admin URLs directly -> STRICTLY DENIED!
        Thread.sleep(400)
        ShadowLooper.idleMainLooper()
        vm.openStudentLoginPortal()
        vm.loginWithCredentials("250901888", "BudiPassword2026!", rememberMe = false)
        waitUntil { vm.uiState.value.currentUser != null && !vm.uiState.value.authInProgress }
        assertEquals(UserRole.STUDENT, vm.uiState.value.currentUser?.role)

        val studentAdminUrlAttempt = vm.navigateToRoute("/admin/dashboard")
        assertTrue(studentAdminUrlAttempt is RouteAccessResult.AccessDenied)
        assertEquals(UserRole.STUDENT, vm.uiState.value.currentUser?.role)

        val studentAdminStudentsAttempt = vm.navigateToRoute("admin_students")
        assertTrue(studentAdminStudentsAttempt is RouteAccessResult.AccessDenied)
        assertEquals(UserRole.STUDENT, vm.uiState.value.currentUser?.role)

        val studentAdminLoginUrlAttempt = vm.navigateToRoute("/login/admin")
        assertTrue(studentAdminLoginUrlAttempt is RouteAccessResult.AccessDenied)
        assertEquals(UserRole.STUDENT, vm.uiState.value.currentUser?.role)

        // Student cannot invoke Admin mutation functions
        val unauthorizedReset = vm.resetStudentPassword("any-id", "HackerPass123")
        assertNotNull(unauthorizedReset)
        assertTrue(unauthorizedReset!!.contains("Akses ditolak", ignoreCase = true))

        // Student logout cleanly ends session
        vm.logout()
        assertNull(vm.uiState.value.currentUser)
        assertEquals(AuthPortalMode.STUDENT_LOGIN, vm.uiState.value.authPortalMode)
    }

    @Test
    fun supabaseConfiguredAdminLoginFallbackOnlyWhenBackendNotProvisioned() = runTest {
        val fakeSupabaseUrl = "https://exampleproject.supabase.co"
        val fakeAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyb2xlIjoiYW5vbiJ9.signature123456"
        val jsonMedia = "application/json; charset=utf-8".toMediaType()
        val now = 1_795_000_000_000L

        // Case 1: Supabase connected, but Edge Function `admin-auth-login` and RPC `resolve_admin_login_email` return 404
        val unprovisionedClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("""{"code":"NOT_FOUND","message":"Not found"}""".toResponseBody(jsonMedia))
                    .build()
            }
            .build()

        val storeUnprovisioned = InMemoryAuthStore()
        val repoUnprovisioned = AzrilAuthRepository(
            store = storeUnprovisioned,
            supabaseUrl = fakeSupabaseUrl,
            supabaseAnonKey = fakeAnonKey,
            httpClient = unprovisionedClient
        )
        assertTrue(repoUnprovisioned.isSupabaseConfigured())

        // Wrong password (e.g. typo with trailing 0: Azril23admin70) MUST still be rejected
        val typoResult = repoUnprovisioned.loginAdmin("Azril12v", "Azril23admin70", rememberMe = true, nowMillis = now)
        assertTrue(typoResult is AuthResult.Failure)

        // Exact admin credentials succeed cleanly via salted PBKDF2 verifier while Supabase migration 006 is pending
        val okResult = repoUnprovisioned.loginAdmin("Azril12v", "Azril23admin7", rememberMe = true, nowMillis = now)
        assertTrue(okResult is AuthResult.Success)
        assertEquals(UserRole.ADMIN, (okResult as AuthResult.Success).profile.role)

        // Session restores cleanly before expiry even when Supabase RPC is not yet deployed
        val restored = repoUnprovisioned.restoreSession(nowMillis = now + 60_000L)
        assertNotNull(restored.profile)
        assertEquals(UserRole.ADMIN, restored.profile?.role)

        // Case 2: Edge Function `admin-auth-login` IS deployed and explicitly rejects with 401 -> MUST NOT fall back!
        val provisionedRejectClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body("""{"error":"Username atau password administrator tidak valid."}""".toResponseBody(jsonMedia))
                    .build()
            }
            .build()

        val repoProvisioned = AzrilAuthRepository(
            store = InMemoryAuthStore(),
            supabaseUrl = fakeSupabaseUrl,
            supabaseAnonKey = fakeAnonKey,
            httpClient = provisionedRejectClient
        )
        val backendRejected = repoProvisioned.loginAdmin("Azril12v", "Azril23admin7", rememberMe = false, nowMillis = now)
        assertTrue(backendRejected is AuthResult.Failure)
    }
}
