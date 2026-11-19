package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.model.UserProfile
import com.example.ui.model.UserRole
import com.example.ui.navigation.NavDestination
import com.example.ui.theme.BorderMint
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.DarkCardElevated
import com.example.ui.theme.DarkObsidian
import com.example.ui.theme.InkText
import com.example.ui.theme.LightGreenSecondary
import com.example.ui.theme.LimeInk
import com.example.ui.theme.LimePrimary
import com.example.ui.theme.OnDarkMuted
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftMintBg
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : NavDestination> AzrilAdaptiveShell(
    user: UserProfile,
    destinations: List<T>,
    bottomBarDestinations: List<T>,
    selectedDestination: T,
    unreadNotifications: Int,
    onSelectDestination: (T) -> Unit,
    onOpenNotifications: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    content: @Composable (isExpandedWidth: Boolean) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(SoftMintBg)) {
        val isExpanded = maxWidth >= 840.dp
        val isMedium = maxWidth in 600.dp..839.dp

        if (isExpanded) {
            // Desktop / Laptop: Sleek Compact Dark Sidebar (240dp) + Soft Mint Workspace
            Row(modifier = Modifier.fillMaxSize()) {
                AzrilSidebarContent(
                    user = user,
                    destinations = destinations,
                    selectedDestination = selectedDestination,
                    onSelectDestination = onSelectDestination,
                    onLogout = onLogout,
                    modifier = Modifier
                        .width(240.dp)
                        .fillMaxHeight()
                )
                Scaffold(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    containerColor = SoftMintBg,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    topBar = {
                        AzrilShellTopBar(
                            title = selectedDestination.label,
                            role = user.role,
                            unreadNotifications = unreadNotifications,
                            showMenuButton = false,
                            showBrandLogo = false,
                            onMenuClick = {},
                            onNotificationsClick = onOpenNotifications,
                            onRefreshClick = onRefresh
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        content(true)
                    }
                }
            }
        } else {
            // Compact Smartphone & Medium Tablet/Foldable
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = DarkCardBg,
                        drawerContentColor = PureWhite,
                        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                        modifier = Modifier.width(276.dp)
                    ) {
                        AzrilSidebarContent(
                            user = user,
                            destinations = destinations,
                            selectedDestination = selectedDestination,
                            onSelectDestination = { dest ->
                                onSelectDestination(dest)
                                scope.launch { drawerState.close() }
                            },
                            onLogout = {
                                scope.launch { drawerState.close() }
                                onLogout()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            ) {
                if (isMedium) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        NavigationRail(
                            containerColor = DarkCardBg,
                            contentColor = PureWhite,
                            header = {
                                Column(
                                    modifier = Modifier.statusBarsPadding().padding(top = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AzrilOfficialLogoMark(size = 34.dp)
                                    IconButton(
                                        onClick = { scope.launch { drawerState.open() } },
                                        modifier = Modifier.testTag("open_drawer_button")
                                    ) {
                                        Icon(
                                            Icons.Rounded.Menu,
                                            contentDescription = stringResource(id = R.string.cd_open_drawer),
                                            tint = LimePrimary
                                        )
                                    }
                                }
                            }
                        ) {
                            bottomBarDestinations.forEach { dest ->
                                val selected = dest == selectedDestination
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { onSelectDestination(dest) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.shortLabel, maxLines = 1) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = DarkCardBg,
                                        selectedTextColor = LimePrimary,
                                        indicatorColor = LimePrimary,
                                        unselectedIconColor = OnDarkMuted,
                                        unselectedTextColor = OnDarkMuted
                                    )
                                )
                            }
                        }
                        Scaffold(
                            modifier = Modifier.weight(1f),
                            containerColor = SoftMintBg,
                            contentWindowInsets = WindowInsets.safeDrawing,
                            topBar = {
                                AzrilShellTopBar(
                                    title = selectedDestination.label,
                                    role = user.role,
                                    unreadNotifications = unreadNotifications,
                                    showMenuButton = false,
                                    showBrandLogo = true,
                                    onMenuClick = {},
                                    onNotificationsClick = onOpenNotifications,
                                    onRefreshClick = onRefresh
                                )
                            }
                        ) { innerPadding ->
                            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                                content(false)
                            }
                        }
                    }
                } else {
                    Scaffold(
                        containerColor = SoftMintBg,
                        contentWindowInsets = WindowInsets.safeDrawing,
                        topBar = {
                            AzrilShellTopBar(
                                title = selectedDestination.label,
                                role = user.role,
                                unreadNotifications = unreadNotifications,
                                showMenuButton = true,
                                showBrandLogo = true,
                                onMenuClick = { scope.launch { drawerState.open() } },
                                onNotificationsClick = onOpenNotifications,
                                onRefreshClick = onRefresh
                            )
                        },
                        bottomBar = {
                            AzrilFloatingBottomBar(
                                destinations = bottomBarDestinations,
                                selectedDestination = selectedDestination,
                                onSelectDestination = onSelectDestination
                            )
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                            content(false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T : NavDestination> AzrilFloatingBottomBar(
    destinations: List<T>,
    selectedDestination: T,
    onSelectDestination: (T) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkCardBg,
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, LimePrimary.copy(alpha = 0.22f)),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEach { dest ->
                    val selected = dest == selectedDestination
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) LimePrimary else Color.Transparent)
                            .clickable { onSelectDestination(dest) }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                            .testTag("bottom_nav_${dest.route}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                            contentDescription = dest.label,
                            tint = if (selected) DarkCardBg else OnDarkMuted,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = dest.shortLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (selected) DarkCardBg else OnDarkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AzrilShellTopBar(
    title: String,
    role: UserRole,
    unreadNotifications: Int,
    showMenuButton: Boolean,
    showBrandLogo: Boolean = true,
    onMenuClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = SoftMintBg,
            titleContentColor = InkText
        ),
        navigationIcon = {
            if (showMenuButton) {
                Surface(
                    shape = CircleShape,
                    color = PureWhite,
                    border = BorderStroke(1.dp, BorderMint),
                    modifier = Modifier.padding(start = 12.dp, end = 6.dp)
                ) {
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.size(42.dp).testTag("open_drawer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = stringResource(id = R.string.cd_open_drawer),
                            tint = DarkCardBg
                        )
                    }
                }
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showBrandLogo) {
                    AzrilOfficialLogoMark(size = 30.dp)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = InkText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                AzrilStatusBadge(
                    text = role.label,
                    containerColor = if (role == UserRole.ADMIN) DarkCardBg else LightGreenSecondary,
                    contentColor = if (role == UserRole.ADMIN) LimePrimary else LimeInk
                )
            }
        },
        actions = {
            Surface(
                shape = CircleShape,
                color = PureWhite,
                border = BorderStroke(1.dp, BorderMint),
                modifier = Modifier.padding(end = 6.dp)
            ) {
                IconButton(
                    onClick = onRefreshClick,
                    modifier = Modifier.size(40.dp).testTag("topbar_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(id = R.string.action_refresh),
                        tint = InkText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = PureWhite,
                border = BorderStroke(1.dp, BorderMint),
                modifier = Modifier.padding(end = 12.dp)
            ) {
                IconButton(
                    onClick = onNotificationsClick,
                    modifier = Modifier.size(40.dp).testTag("topbar_notifications_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (unreadNotifications > 0) {
                                Badge(containerColor = LimePrimary, contentColor = DarkCardBg) {
                                    Text(if (unreadNotifications > 9) "9+" else "$unreadNotifications")
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Notifications,
                            contentDescription = stringResource(id = R.string.cd_notifications),
                            tint = InkText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun <T : NavDestination> AzrilSidebarContent(
    user: UserProfile,
    destinations: List<T>,
    selectedDestination: T,
    onSelectDestination: (T) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(DarkCardBg)
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // Brand Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AzrilBrandLogo(darkSurface = true, showTagline = false, iconSize = 38.dp)
            Surface(
                color = DarkCardElevated,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, LimePrimary.copy(alpha = 0.25f))
            ) {
                Text(
                    text = if (user.role == UserRole.ADMIN) "Admin Academic Panel" else "Student Academic Portal",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = LimePrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
        HorizontalDivider(color = DarkCardElevated)

        // Grouped Navigation Items
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val grouped = destinations.groupBy { it.sectionGroup }
            grouped.forEach { (groupTitle, items) ->
                Text(
                    text = groupTitle.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = OnDarkMuted.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp)
                )
                items.forEach { dest ->
                    val selected = dest == selectedDestination
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) LimePrimary else Color.Transparent)
                            .clickable { onSelectDestination(dest) }
                            .padding(horizontal = 14.dp, vertical = 11.dp)
                            .testTag("sidebar_${dest.route}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                            contentDescription = dest.label,
                            tint = if (selected) DarkCardBg else OnDarkMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = dest.label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (selected) DarkCardBg else PureWhite.copy(alpha = 0.88f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = DarkCardElevated)

        // Footer User Card + Switch Preview + Logout
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkObsidian)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(LimePrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.fullName.take(2).uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = DarkCardBg
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.fullName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = user.email,
                        style = MaterialTheme.typography.labelSmall,
                        color = OnDarkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Surface(
                onClick = onLogout,
                color = Color(0xFF331B1E),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("sidebar_logout")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = stringResource(id = R.string.logout_label),
                        tint = Color(0xFFFCA5A5),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (user.role == UserRole.ADMIN) "Keluar dari Admin" else "Keluar Akun",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFFFCA5A5)
                    )
                }
            }
        }
    }
}
