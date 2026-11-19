package com.example.ui.screens.student

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.gamification.XpAndStreakEngine
import com.example.ui.components.AzrilCard
import com.example.ui.components.AzrilDarkCard
import com.example.ui.components.AzrilEmptyState
import com.example.ui.components.AzrilMintCard
import com.example.ui.components.AzrilPageHeader
import com.example.ui.components.AzrilStatusBadge
import com.example.ui.model.PointSourceType
import com.example.ui.model.RankingPointTransaction
import com.example.ui.model.StreakReminderSlot
import com.example.ui.model.XpHistoryTimeRange
import com.example.ui.state.AzrilUiState
import com.example.ui.theme.Amber50
import com.example.ui.theme.Amber700
import com.example.ui.theme.BorderMint
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.DarkCardElevated
import com.example.ui.theme.Emerald50
import com.example.ui.theme.Emerald700
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSubtle
import com.example.ui.theme.InkText
import com.example.ui.theme.LightGreenSecondary
import com.example.ui.theme.LimeDeep
import com.example.ui.theme.LimeInk
import com.example.ui.theme.LimePrimary
import com.example.ui.theme.OnDarkMuted
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Rose50
import com.example.ui.theme.Rose700
import com.example.ui.theme.SoftMintBg
import com.example.ui.theme.SurfaceMuted

private val FlameOrange = Color(0xFFFF7A1A)
private val FlameBgDark = Color(0xFF2D2118)

/**
 * Top-of-dashboard Login Streak & XP Hero Card for Students.
 * Displays flame icon, current streak days (WIB), today's login status,
 * 7-day login history, Total XP, Monthly XP, and direct access to Riwayat XP.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentStreakAndXpHeroCard(
    uiState: AzrilUiState,
    onOpenXpHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val user = uiState.currentUser ?: return
    val streak = uiState.studentStreaks[user.id]
    val effectiveStreak = remember(streak) { XpAndStreakEngine.effectiveCurrentStreak(streak) }
    val loggedInToday = remember(streak) { XpAndStreakEngine.hasLoggedInTodayWib(streak) }
    val last7Days = remember(streak) { XpAndStreakEngine.buildLast7DaysVisual(streak) }
    val totalXp = remember(uiState.rankingPoints, user.id) {
        XpAndStreakEngine.computeTotalXp(user.id, uiState.rankingPoints)
    }
    val monthlyXp = remember(uiState.rankingPoints, user.id) {
        XpAndStreakEngine.computeMonthlyXp(user.id, uiState.rankingPoints)
    }

    AzrilDarkCard(
        modifier = modifier.testTag("student_login_streak_card"),
        contentPadding = PaddingValues(20.dp)
    ) {
        // Header Row: Flame + Streak Days + Today Login Status Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (loggedInToday) FlameBgDark else DarkCardElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocalFireDepartment,
                        contentDescription = "Ikon Api Login Streak",
                        tint = if (effectiveStreak > 0) FlameOrange else OnDarkMuted,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "$effectiveStreak Hari Streak",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = PureWhite
                    )
                    Text(
                        text = "Rekor terbaik: ${streak?.longestStreak ?: effectiveStreak} hari • Zona WIB",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnDarkMuted
                    )
                }
            }
            AzrilStatusBadge(
                text = if (loggedInToday) "Sudah Login Hari Ini" else "Belum Login Hari Ini",
                containerColor = if (loggedInToday) LimePrimary else Rose50,
                contentColor = if (loggedInToday) DarkCardBg else Rose700
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 7-Day WIB Login History Strip
        Text(
            text = "Riwayat Login 7 Hari Terakhir (WIB)",
            style = MaterialTheme.typography.labelMedium,
            color = OnDarkMuted
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            last7Days.forEach { day ->
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = when {
                        day.loggedIn -> LimePrimary.copy(alpha = 0.16f)
                        day.isToday -> DarkCardElevated
                        else -> DarkCardElevated.copy(alpha = 0.65f)
                    },
                    border = BorderStroke(
                        width = if (day.isToday) 1.5.dp else 1.dp,
                        color = when {
                            day.loggedIn -> LimePrimary
                            day.isToday -> FlameOrange
                            else -> Color.Transparent
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = day.dayShortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (day.isToday) PureWhite else OnDarkMuted,
                            maxLines = 1
                        )
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (day.loggedIn) LimePrimary else DarkCardBg),
                            contentAlignment = Alignment.Center
                        ) {
                            if (day.loggedIn) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Login pada ${day.dateWib}",
                                    tint = DarkCardBg,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Text(
                                    text = day.dayOfMonth,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnDarkMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = DarkCardElevated)
        Spacer(modifier = Modifier.height(14.dp))

        // XP Metrics + Riwayat XP CTA
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text("Total XP", style = MaterialTheme.typography.labelSmall, color = OnDarkMuted)
                    Text(
                        text = "$totalXp XP",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = LimePrimary
                    )
                }
                Column {
                    Text("XP Bulan Ini", style = MaterialTheme.typography.labelSmall, color = OnDarkMuted)
                    Text(
                        text = "+$monthlyXp XP",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = PureWhite
                    )
                }
            }
            Button(
                onClick = onOpenXpHistory,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LimePrimary, contentColor = DarkCardBg),
                modifier = Modifier.testTag("student_open_xp_history_button")
            ) {
                Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Riwayat XP", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            XpAndStreakEngine.STREAK_MILESTONE_BONUSES.forEach { (days, bonus) ->
                val achieved = effectiveStreak >= days
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (achieved) LimePrimary.copy(alpha = 0.22f) else DarkCardElevated
                ) {
                    Text(
                        text = "${days}h: +$bonus XP",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (achieved) LimePrimary else OnDarkMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Mandatory Student "Riwayat XP" Screen:
 * - Shows Total XP, Monthly XP, and Individual Semester Izin Quota
 * - Filterable by Activity Category & Time Range
 * - Paginated ("Muat Lebih Banyak")
 * - Strictly read-only & scoped strictly to the logged-in student's own XP transactions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentXpHistoryScreen(
    uiState: AzrilUiState,
    isExpanded: Boolean
) {
    val user = uiState.currentUser ?: return
    var selectedCategory by rememberSaveable { mutableStateOf<PointSourceType?>(null) }
    var selectedTimeRange by rememberSaveable { mutableStateOf(XpHistoryTimeRange.ALL) }
    var visibleCount by rememberSaveable { mutableIntStateOf(10) }

    val totalXp = remember(uiState.rankingPoints, user.id) {
        XpAndStreakEngine.computeTotalXp(user.id, uiState.rankingPoints)
    }
    val monthlyXp = remember(uiState.rankingPoints, user.id) {
        XpAndStreakEngine.computeMonthlyXp(user.id, uiState.rankingPoints)
    }
    val izinQuota = remember(uiState.attendanceRecords, uiState.settings, user.id) {
        XpAndStreakEngine.computeStudentIzinQuota(
            studentId = user.id,
            semesterKey = uiState.settings.activeSemesterKey,
            attendanceRecords = uiState.attendanceRecords,
            maxPaidIzin = uiState.settings.maxPaidIzinPerSemester
        )
    }
    val filteredHistory = remember(
        user,
        uiState.rankingPoints,
        selectedCategory,
        selectedTimeRange,
        uiState.settings.activeSemesterKey
    ) {
        XpAndStreakEngine.getAuthorizedXpHistory(
            requester = user,
            targetStudentId = user.id,
            allTransactions = uiState.rankingPoints,
            categoryFilter = selectedCategory,
            timeRange = selectedTimeRange,
            activeSemesterKey = uiState.settings.activeSemesterKey
        )
    }
    val pagedHistory = remember(filteredHistory, visibleCount) {
        filteredHistory.take(visibleCount)
    }

    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = DarkCardBg,
        selectedLabelColor = LimePrimary,
        containerColor = SoftMintBg,
        labelColor = InkText
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AzrilPageHeader(
            title = "Riwayat XP",
            subtitle = "Pantau seluruh perolehan XP dari absensi, tugas, keaktifan kelas, dan bonus login streak secara transparan."
        )

        // Summary Hero Card: Total XP + Monthly XP + Semester Izin Quota
        AzrilDarkCard(contentPadding = PaddingValues(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AzrilStatusBadge("AKUMULASI XP AKTUAL", LimePrimary, DarkCardBg)
                AzrilStatusBadge("Hanya Baca", DarkCardElevated, OnDarkMuted)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    color = DarkCardElevated,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, LimePrimary.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Total XP Dimiliki", style = MaterialTheme.typography.labelSmall, color = OnDarkMuted)
                        Text(
                            text = "$totalXp XP",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = LimePrimary,
                            modifier = Modifier.testTag("xp_history_total_value")
                        )
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    color = DarkCardElevated,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("XP Bulan Ini (WIB)", style = MaterialTheme.typography.labelSmall, color = OnDarkMuted)
                        Text(
                            text = "+$monthlyXp XP",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = PureWhite,
                            modifier = Modifier.testTag("xp_history_monthly_value")
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = DarkCardElevated,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Kuota XP Izin (${uiState.settings.activeSemesterKey})",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnDarkMuted
                    )
                    Text(
                        text = "Terpakai ${izinQuota.usedIzinCount} • Sisa ${izinQuota.remainingPaidIzin}/${izinQuota.maxPaidIzin}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (izinQuota.remainingPaidIzin > 0) LimePrimary else Amber50
                    )
                }
            }
        }

        // Streak Reminder Schedule Info Card (08:00, 12:00, 17:00 WIB)
        AzrilMintCard(contentPadding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = DarkCardBg, modifier = Modifier.size(18.dp))
                Text("Pengingat Login Streak Otomatis (WIB)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = InkText)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = StreakReminderSlot.entries.joinToString(" • ") { "${it.timeLabel} (${it.title.removePrefix("Pengingat Streak ")})" } +
                    ". Pengingat otomatis berhenti setelah Anda login hari ini.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted
            )
        }

        // Filter Panel: Category + Time Range
        AzrilCard {
            Text("Filter Kategori Aktivitas", style = MaterialTheme.typography.titleSmall, color = InkText)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null; visibleCount = 10 },
                    label = { Text("Semua") },
                    shape = RoundedCornerShape(999.dp),
                    colors = chipColors,
                    modifier = Modifier.testTag("xp_filter_category_all")
                )
                PointSourceType.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat; visibleCount = 10 },
                        label = { Text(cat.label) },
                        shape = RoundedCornerShape(999.dp),
                        colors = chipColors,
                        modifier = Modifier.testTag("xp_filter_category_${cat.name.lowercase()}")
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Rentang Waktu", style = MaterialTheme.typography.titleSmall, color = InkText)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                XpHistoryTimeRange.entries.forEach { range ->
                    FilterChip(
                        selected = selectedTimeRange == range,
                        onClick = { selectedTimeRange = range; visibleCount = 10 },
                        label = { Text(range.label) },
                        shape = RoundedCornerShape(999.dp),
                        colors = chipColors,
                        modifier = Modifier.testTag("xp_filter_range_${range.name.lowercase()}")
                    )
                }
            }
        }

        // Transaction List Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Daftar Transaksi XP (${filteredHistory.size})",
                style = MaterialTheme.typography.titleMedium,
                color = InkText
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = LimeDeep, modifier = Modifier.size(15.dp))
                Text("Terproteksi", style = MaterialTheme.typography.labelSmall, color = LimeDeep)
            }
        }

        if (pagedHistory.isEmpty()) {
            AzrilEmptyState(
                title = "Belum Ada Transaksi XP",
                description = "Transaksi XP Anda dari kehadiran (+20 XP), tugas (+35/+30/+10 XP), keaktifan diskusi (+10/+20 XP), dan bonus login streak akan muncul otomatis di sini.",
                icon = Icons.Rounded.History,
                showIllustration = true
            )
        } else {
            pagedHistory.forEach { tx ->
                XpTransactionLedgerCard(tx = tx)
            }

            if (filteredHistory.size > visibleCount) {
                val remaining = filteredHistory.size - visibleCount
                OutlinedButton(
                    onClick = { visibleCount += 10 },
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("xp_history_load_more_button")
                ) {
                    Text("Muat Lebih Banyak ($remaining transaksi tersisa)", color = DarkCardBg, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun XpTransactionLedgerCard(
    tx: RankingPointTransaction,
    modifier: Modifier = Modifier
) {
    val icon = when (tx.sourceType) {
        PointSourceType.ATTENDANCE -> Icons.Rounded.FactCheck
        PointSourceType.ASSIGNMENT -> Icons.AutoMirrored.Rounded.Assignment
        PointSourceType.PARTICIPATION -> Icons.Rounded.Bolt
        PointSourceType.STREAK_BONUS -> Icons.Rounded.LocalFireDepartment
        PointSourceType.MANUAL_ADJUSTMENT -> Icons.Rounded.Tune
    }
    val iconBg = when (tx.sourceType) {
        PointSourceType.STREAK_BONUS -> Amber50
        PointSourceType.PARTICIPATION -> DarkCardBg
        else -> LightGreenSecondary
    }
    val iconTint = when (tx.sourceType) {
        PointSourceType.STREAK_BONUS -> FlameOrange
        PointSourceType.PARTICIPATION -> LimePrimary
        else -> DarkCardBg
    }
    val xpBadgeBg = when {
        tx.points > 0 -> Emerald50
        tx.points == 0 -> SurfaceMuted
        else -> Rose50
    }
    val xpBadgeColor = when {
        tx.points > 0 -> Emerald700
        tx.points == 0 -> InkMuted
        else -> Rose700
    }
    val xpText = when {
        tx.points > 0 -> "+${tx.points} XP"
        tx.points == 0 -> "0 XP"
        else -> "${tx.points} XP"
    }

    AzrilCard(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = tx.sourceType.label, tint = iconTint, modifier = Modifier.size(20.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = tx.activityTitle.ifBlank { tx.description },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = InkText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = tx.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AzrilStatusBadge(
                            text = tx.xpCategory.groupLabel,
                            containerColor = SoftMintBg,
                            contentColor = LimeInk
                        )
                        Text(
                            text = tx.updatedAt.ifBlank { tx.createdAt },
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSubtle
                        )
                    }
                    if (!tx.adminName.isNullOrBlank()) {
                        Text(
                            text = "Diberikan oleh Admin: ${tx.adminName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = LimeDeep
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = xpBadgeBg,
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = xpText,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = xpBadgeColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
