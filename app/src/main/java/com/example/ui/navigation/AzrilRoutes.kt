package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Class
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Class
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.History
import androidx.compose.ui.graphics.vector.ImageVector

interface NavDestination {
    val route: String
    val label: String
    val shortLabel: String
    val selectedIcon: ImageVector
    val unselectedIcon: ImageVector
    val sectionGroup: String
}

enum class AdminDestination(
    override val route: String,
    override val label: String,
    override val shortLabel: String,
    override val selectedIcon: ImageVector,
    override val unselectedIcon: ImageVector,
    override val sectionGroup: String
) : NavDestination {
    DASHBOARD("admin_dashboard", "Dashboard", "Beranda", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard, "Utama"),
    STUDY_PROGRAMS("admin_study_programs", "Program Studi", "Prodi", Icons.Rounded.School, Icons.Outlined.School, "Akademik"),
    CLASSES("admin_classes", "Kelas", "Kelas", Icons.Rounded.Class, Icons.Outlined.Class, "Akademik"),
    STUDENTS("admin_students", "Mahasiswa", "Mhs", Icons.Rounded.Groups, Icons.Outlined.Groups, "Akademik"),
    SCHEDULES("admin_schedules", "Jadwal", "Jadwal", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth, "Perkuliahan"),
    ATTENDANCE("admin_attendance", "Absensi", "Absensi", Icons.Rounded.FactCheck, Icons.Outlined.FactCheck, "Perkuliahan"),
    ASSIGNMENTS("admin_assignments", "Penugasan", "Tugas", Icons.Rounded.Assignment, Icons.Outlined.Assignment, "Evaluasi"),
    SUBMISSIONS("admin_submissions", "Pengumpulan", "Kumpul", Icons.Rounded.UploadFile, Icons.Outlined.UploadFile, "Evaluasi"),
    AI_ASSISTANT("admin_ai_assistant", "AI Assistant", "AI", Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome, "Evaluasi"),
    GRADING("admin_grading", "Penilaian", "Nilai", Icons.Rounded.Grade, Icons.Outlined.Grade, "Evaluasi"),
    XP_KEAKTIFAN("admin_xp_keaktifan", "XP Keaktifan", "XP Aktif", Icons.Rounded.Bolt, Icons.Outlined.Bolt, "Evaluasi"),
    RANKING("admin_ranking", "Ranking", "Ranking", Icons.Rounded.EmojiEvents, Icons.Outlined.EmojiEvents, "Evaluasi"),
    REPORTS("admin_reports", "Laporan", "Laporan", Icons.Rounded.Analytics, Icons.Outlined.Analytics, "Sistem"),
    NOTIFICATIONS("admin_notifications", "Notifikasi", "Notif", Icons.Rounded.Notifications, Icons.Outlined.Notifications, "Sistem"),
    SETTINGS("admin_settings", "Pengaturan", "Atur", Icons.Rounded.Settings, Icons.Outlined.Settings, "Sistem");

    companion object {
        val bottomBarItems = listOf(DASHBOARD, ATTENDANCE, ASSIGNMENTS, XP_KEAKTIFAN, RANKING)
    }
}

enum class StudentDestination(
    override val route: String,
    override val label: String,
    override val shortLabel: String,
    override val selectedIcon: ImageVector,
    override val unselectedIcon: ImageVector,
    override val sectionGroup: String
) : NavDestination {
    DASHBOARD("student_dashboard", "Dashboard", "Beranda", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard, "Utama"),
    XP_HISTORY("student_xp_history", "Riwayat XP", "Riwayat XP", Icons.Rounded.History, Icons.Outlined.History, "Utama"),
    MY_SCHEDULE("student_schedule", "Jadwal Saya", "Jadwal", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth, "Perkuliahan"),
    ATTENDANCE("student_attendance", "Absensi", "Absensi", Icons.Rounded.FactCheck, Icons.Outlined.FactCheck, "Perkuliahan"),
    ASSIGNMENTS("student_assignments", "Daftar Tugas", "Tugas", Icons.Rounded.Assignment, Icons.Outlined.Assignment, "Tugas & Nilai"),
    MY_SUBMISSIONS("student_submissions", "Pengumpulan Saya", "Kumpul", Icons.Rounded.UploadFile, Icons.Outlined.UploadFile, "Tugas & Nilai"),
    GRADES_FEEDBACK("student_grades", "Nilai dan Feedback", "Nilai", Icons.Rounded.Grade, Icons.Outlined.Grade, "Tugas & Nilai"),
    RANKING("student_ranking", "Ranking", "Ranking", Icons.Rounded.EmojiEvents, Icons.Outlined.EmojiEvents, "Tugas & Nilai"),
    NOTIFICATIONS("student_notifications", "Notifikasi", "Notif", Icons.Rounded.Notifications, Icons.Outlined.Notifications, "Akun"),
    PROFILE("student_profile", "Profil Saya", "Profil", Icons.Rounded.Person, Icons.Outlined.Person, "Akun");

    companion object {
        val bottomBarItems = listOf(DASHBOARD, XP_HISTORY, ASSIGNMENTS, RANKING, PROFILE)
    }
}
