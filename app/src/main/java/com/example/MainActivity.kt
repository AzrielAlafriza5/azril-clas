package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.example.ui.screens.admin.AdminGradingScreen
import com.example.ui.screens.admin.AdminReportsScreen
import com.example.ui.screens.admin.AdminSchedulesScreen
import com.example.ui.screens.admin.AdminSettingsScreen
import com.example.ui.screens.admin.AdminStudentsScreen
import com.example.ui.screens.admin.AdminStudyProgramsScreen
import com.example.ui.screens.admin.AdminSubmissionsScreen
import com.example.ui.screens.admin.RoleNotificationsScreen
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
import com.example.ui.state.AzrilViewModel
import com.example.ui.theme.AzrilClasTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AzrilClasTheme {
                AzrilClasApp()
            }
        }
    }
}

@Composable
fun AzrilClasApp(
    viewModel: AzrilViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val user = uiState.currentUser

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
                    com.example.ui.components.AzrilBrandLogo(showTagline = true, iconSize = 48.dp)
                    CircularProgressIndicator(
                        color = com.example.ui.theme.DarkCardBg,
                        trackColor = com.example.ui.theme.LightGreenSecondary
                    )
                }
            }
        } else when (user?.role) {
            null -> {
                LoginScreen(
                    rememberedIdentifier = uiState.rememberedIdentifier,
                    rememberMeDefault = uiState.rememberMeEnabled,
                    authInProgress = uiState.authInProgress,
                    authErrorMessage = uiState.authErrorMessage,
                    isSupabaseConfigured = uiState.isSupabaseConfigured,
                    onLogin = { identifier, password, rememberMe ->
                        viewModel.loginWithCredentials(identifier, password, rememberMe)
                    },
                    onRegisterStudent = { fullName, nim, emailOrUsername, password, confirmPassword, prodiCode, rememberMe ->
                        viewModel.registerStudentAccount(
                            fullName = fullName,
                            nim = nim,
                            emailOrUsername = emailOrUsername,
                            password = password,
                            confirmPassword = confirmPassword,
                            studyProgramCode = prodiCode,
                            rememberMe = rememberMe
                        )
                    },
                    onClearAuthError = { viewModel.clearAuthError() },
                    onEnterAdminPreview = { viewModel.enterAdminPreview() },
                    onNotify = { msg, type -> viewModel.showToast(msg, type) }
                )
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
                    onSwitchRolePreview = { viewModel.enterStudentPreview() },
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
                        AdminDestination.RANKING -> AdminRankingScreen(uiState, viewModel, isExpanded)
                        AdminDestination.REPORTS -> AdminReportsScreen(uiState, viewModel, isExpanded)
                        AdminDestination.NOTIFICATIONS -> RoleNotificationsScreen(uiState, UserRole.ADMIN, viewModel, isExpanded)
                        AdminDestination.SETTINGS -> AdminSettingsScreen(uiState, viewModel, isExpanded)
                    }
                }
            }
            UserRole.STUDENT -> {
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
                    onSwitchRolePreview = { viewModel.enterAdminPreview() },
                    onLogout = { viewModel.logout() }
                ) { isExpanded ->
                    when (uiState.studentDestination) {
                        StudentDestination.DASHBOARD -> StudentDashboardScreen(uiState, isExpanded) { viewModel.navigateStudent(it) }
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
