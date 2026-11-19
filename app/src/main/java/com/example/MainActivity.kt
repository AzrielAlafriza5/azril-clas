package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.AzrilAdaptiveShell
import com.example.ui.components.AzrilToastBanner
import com.example.ui.model.UserRole
import com.example.ui.navigation.AdminDestination
import com.example.ui.navigation.StudentDestination
import com.example.ui.screens.admin.AdminAiAssistantScreen
import com.example.ui.screens.admin.AdminAssignmentsScreen
import com.example.ui.screens.admin.AdminAttendanceScreen
import com.example.ui.screens.admin.AdminClassesScreen
import com.example.ui.screens.admin.AdminDashboardScreen
import com.example.ui.screens.admin.AdminExportCenterScreen
import com.example.ui.screens.admin.AdminGradingScreen
import com.example.ui.screens.admin.AdminReportsScreen
import com.example.ui.screens.admin.AdminSchedulesScreen
import com.example.ui.screens.admin.AdminSettingsScreen
import com.example.ui.screens.admin.AdminStudentsScreen
import com.example.data.gamification.StreakReminderNotifier
import com.example.ui.screens.admin.AdminStudyProgramsScreen
import com.example.ui.screens.admin.AdminSubmissionsScreen
import com.example.ui.screens.admin.AdminXpParticipationScreen
import com.example.ui.screens.admin.RoleNotificationsScreen
import com.example.ui.screens.auth.AdminLoginScreen
import com.example.ui.screens.auth.ForceChangePasswordScreen
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.ranking.AdminRankingScreen
import com.example.ui.screens.ranking.StudentRankingScreen
import com.example.ui.screens.student.StudentAssignmentsScreen
import com.example.ui.screens.student.StudentAttendanceScreen
import com.example.ui.screens.student.StudentDashboardScreen
import com.example.ui.screens.student.StudentGradesScreen
import com.example.ui.screens.student.StudentProfileScreen
import com.example.ui.screens.student.StudentScheduleScreen
import com.example.ui.screens.student.StudentSubmissionsScreen
import com.example.ui.screens.student.StudentXpHistoryScreen
import com.example.ui.state.AuthPortalMode
import com.example.ui.state.AzrilViewModel
import com.example.ui.theme.AzrilClasTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val requestedRoute = intent?.data?.path?.takeIf { it.isNotBlank() }
            ?: intent?.getStringExtra("route")?.takeIf { it.isNotBlank() }
        setContent {
            AzrilClasTheme {
                AzrilClasApp(initialRoute = requestedRoute)
            }
        }
    }
}

@Composable
fun AzrilClasApp(
    initialRoute: String? = null,
    viewModel: AzrilViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val user = uiState.currentUser
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Granted or denied gracefully; in-app notifications always stay active */ }

    LaunchedEffect(user?.id) {
        if (user?.role == UserRole.STUDENT &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !StreakReminderNotifier.hasNotificationPermission(context)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(uiState.isRestoringSession, initialRoute) {
        if (!uiState.isRestoringSession && !initialRoute.isNullOrBlank()) {
            viewModel.navigateToRoute(initialRoute)
        }
    }

    // Active session expiration watcher: terminates expired sessions and requires re-authentication
    LaunchedEffect(user?.id, uiState.sessionExpiresAtMillis) {
        val expiresAt = uiState.sessionExpiresAtMillis
        if (user != null && expiresAt != null && expiresAt > 0L) {
            val remaining = expiresAt - System.currentTimeMillis()
            if (remaining <= 0L) {
                viewModel.enforceSessionValidity()
            } else {
                delay(remaining.coerceAtMost(60_000L))
                viewModel.enforceSessionValidity()
            }
        }
    }

    LaunchedEffect(uiState.toastMessage?.id) {
        if (uiState.toastMessage != null) {
            delay(3800)
            viewModel.dismissToast()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isRestoringSession) {
            Box(
                modifier = Modifier.fillMaxSize().background(com.example.ui.theme.SoftMintBg),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    com.example.ui.components.AzrilBrandLogo(showTagline = true, iconSize = 56.dp)
                    CircularProgressIndicator(
                        color = com.example.ui.theme.DarkCardBg,
                        trackColor = com.example.ui.theme.LightGreenSecondary
                    )
                }
            }
        } else when (user?.role) {
            null -> {
                when (uiState.authPortalMode) {
                    AuthPortalMode.STUDENT_LOGIN -> LoginScreen(
                        rememberedIdentifier = uiState.rememberedIdentifier,
                        rememberMeDefault = uiState.rememberMeEnabled,
                        authInProgress = uiState.authInProgress,
                        authErrorMessage = uiState.authErrorMessage,
                        isSupabaseConfigured = uiState.isSupabaseConfigured,
                        onLogin = { identifier, password, rememberMe ->
                            viewModel.loginWithCredentials(identifier, password, rememberMe)
                        },
                        onClearAuthError = { viewModel.clearAuthError() },
                        onOpenAdminLogin = { viewModel.openAdminLoginPortal() }
                    )
                    AuthPortalMode.ADMIN_LOGIN -> AdminLoginScreen(
                        authInProgress = uiState.authInProgress,
                        authErrorMessage = uiState.authErrorMessage,
                        isSupabaseConfigured = uiState.isSupabaseConfigured,
                        onSubmitAdminLogin = { username, password, rememberMe ->
                            viewModel.loginAdminWithCredentials(username, password, rememberMe)
                        },
                        onClearAuthError = { viewModel.clearAuthError() },
                        onBackToStudentLogin = { viewModel.openStudentLoginPortal() }
                    )
                }
            }
            UserRole.ADMIN -> {
                BackHandler(enabled = uiState.adminDestination != AdminDestination.DASHBOARD) {
                    viewModel.navigateAdmin(AdminDestination.DASHBOARD)
                }
                AzrilAdaptiveShell(
                    user = user,
                    destinations = AdminDestination.entries,
                    bottomBarDestinations = AdminDestination.bottomBarItems,
                    selectedDestination = uiState.adminDestination,
                    unreadNotifications = uiState.unreadNotificationCount,
                    onSelectDestination = { viewModel.navigateAdmin(it) },
                    onOpenNotifications = { viewModel.navigateAdmin(AdminDestination.NOTIFICATIONS) },
                    onRefresh = { viewModel.triggerRefresh() },
                    onLogout = { viewModel.logout() }
                ) { isExpanded ->
                    when (uiState.adminDestination) {
                        AdminDestination.DASHBOARD -> AdminDashboardScreen(uiState, isExpanded) { viewModel.navigateAdmin(it) }
                        AdminDestination.STUDY_PROGRAMS -> AdminStudyProgramsScreen(uiState, viewModel, isExpanded)
                        AdminDestination.CLASSES -> AdminClassesScreen(uiState, viewModel, isExpanded)
                        AdminDestination.STUDENTS -> AdminStudentsScreen(uiState, viewModel, isExpanded)
                        AdminDestination.SCHEDULES -> AdminSchedulesScreen(uiState, viewModel, isExpanded)
                        AdminDestination.ATTENDANCE -> AdminAttendanceScreen(uiState, viewModel, isExpanded)
                        AdminDestination.ASSIGNMENTS -> AdminAssignmentsScreen(uiState, viewModel, isExpanded)
                        AdminDestination.SUBMISSIONS -> AdminSubmissionsScreen(uiState, viewModel, isExpanded)
                        AdminDestination.AI_ASSISTANT -> AdminAiAssistantScreen(uiState, viewModel, isExpanded)
                        AdminDestination.GRADING -> AdminGradingScreen(uiState, viewModel, isExpanded)
                        AdminDestination.XP_KEAKTIFAN -> AdminXpParticipationScreen(uiState, viewModel, isExpanded)
                        AdminDestination.RANKING -> AdminRankingScreen(uiState, viewModel, isExpanded)
                        AdminDestination.REPORTS -> AdminReportsScreen(uiState, viewModel, isExpanded) { viewModel.navigateAdmin(it) }
                        AdminDestination.EXPORT_CENTER -> AdminExportCenterScreen(uiState, viewModel, isExpanded)
                        AdminDestination.NOTIFICATIONS -> RoleNotificationsScreen(uiState, UserRole.ADMIN, viewModel, isExpanded)
                        AdminDestination.SETTINGS -> AdminSettingsScreen(uiState, viewModel, isExpanded)
                    }
                }
            }
            UserRole.STUDENT -> {
                if (user.mustChangePassword) {
                    ForceChangePasswordScreen(
                        user = user,
                        authInProgress = uiState.authInProgress,
                        authErrorMessage = uiState.authErrorMessage,
                        onClearError = { viewModel.clearAuthError() },
                        onSubmitNewPassword = { newPw, confirmPw ->
                            viewModel.completeFirstLoginPasswordChange(newPw, confirmPw)
                        },
                        onLogout = { viewModel.logout() }
                    )
                } else {
                    BackHandler(enabled = uiState.studentDestination != StudentDestination.DASHBOARD) {
                        viewModel.navigateStudent(StudentDestination.DASHBOARD)
                    }
                    AzrilAdaptiveShell(
                        user = user,
                        destinations = StudentDestination.entries,
                        bottomBarDestinations = StudentDestination.bottomBarItems,
                        selectedDestination = uiState.studentDestination,
                        unreadNotifications = uiState.unreadNotificationCount,
                        onSelectDestination = { viewModel.navigateStudent(it) },
                        onOpenNotifications = { viewModel.navigateStudent(StudentDestination.NOTIFICATIONS) },
                        onRefresh = { viewModel.triggerRefresh() },
                        onLogout = { viewModel.logout() }
                    ) { isExpanded ->
                        when (uiState.studentDestination) {
                            StudentDestination.DASHBOARD -> StudentDashboardScreen(uiState, isExpanded) { viewModel.navigateStudent(it) }
                            StudentDestination.XP_HISTORY -> StudentXpHistoryScreen(uiState, isExpanded)
                            StudentDestination.MY_SCHEDULE -> StudentScheduleScreen(uiState, viewModel, isExpanded) { viewModel.navigateStudent(it) }
                            StudentDestination.ATTENDANCE -> StudentAttendanceScreen(uiState, viewModel, isExpanded)
                            StudentDestination.ASSIGNMENTS -> StudentAssignmentsScreen(uiState, viewModel, isExpanded)
                            StudentDestination.MY_SUBMISSIONS -> StudentSubmissionsScreen(uiState, isExpanded)
                            StudentDestination.GRADES_FEEDBACK -> StudentGradesScreen(uiState, isExpanded)
                            StudentDestination.RANKING -> StudentRankingScreen(uiState, isExpanded)
                            StudentDestination.NOTIFICATIONS -> RoleNotificationsScreen(uiState, UserRole.STUDENT, viewModel, isExpanded)
                            StudentDestination.PROFILE -> StudentProfileScreen(uiState, viewModel, isExpanded) { viewModel.logout() }
                        }
                    }
                }
            }
        }

        // Floating Toast / Notification Banner
        AnimatedVisibility(
            visible = uiState.toastMessage != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 72.dp)
        ) {
            uiState.toastMessage?.let { toast ->
                AzrilToastBanner(
                    toast = toast,
                    onDismiss = { viewModel.dismissToast() }
                )
            }
        }
    }
}
