package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.navigation.StudentDestination
import com.example.ui.theme.BorderMint
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.DarkCardElevated
import com.example.ui.theme.LightGreenSecondary
import com.example.ui.theme.LimePrimary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftMintBg

/**
 * Lightweight, non-blocking top-to-bottom loading indicator aligned with Azril Clas branding
 * (Lime Green #B5D45D, Dark Card #171A17, Soft Mint #F4F7EE).
 *
 * Renders:
 * 1. A crisp 3.5dp top progress bar with an animated lime highlight sweep.
 * 2. A subtle, pointer-passthrough vertical top-to-bottom sweep curtain (900ms cycle)
 *    that gives clear directional loading feedback without blocking taps or scrolling.
 * Stops and fades out immediately when [visible] becomes false.
 */
@Composable
fun AzrilTopToBottomLoadingIndicator(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(140)),
        exit = fadeOut(animationSpec = tween(180)),
        modifier = modifier
    ) {
        val transition = rememberInfiniteTransition(label = "azril_top_bottom_loader")
        val verticalProgress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 920, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "vertical_sweep"
        )
        val barSweep by transition.animateFloat(
            initialValue = -0.35f,
            targetValue = 1.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 820, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "top_bar_sweep"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("azril_route_loading_indicator")
        ) {
            // Subtle top-to-bottom directional lime sheen (non-blocking pure Canvas)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bandHeight = (size.height * 0.22f).coerceAtLeast(110f)
                val topY = (verticalProgress * (size.height + bandHeight)) - bandHeight
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            LimePrimary.copy(alpha = 0.10f),
                            LightGreenSecondary.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        startY = topY,
                        endY = topY + bandHeight
                    ),
                    topLeft = Offset(0f, topY.coerceAtLeast(0f)),
                    size = Size(size.width, bandHeight.coerceAtMost(size.height))
                )
            }

            // Crisp top horizontal progress track (DarkCardBg + LimePrimary runner)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .align(Alignment.TopCenter)
            ) {
                drawRect(
                    color = DarkCardBg.copy(alpha = 0.14f),
                    size = size
                )
                val runnerWidth = size.width * 0.42f
                val left = (barSweep * size.width) - (runnerWidth * 0.5f)
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            DarkCardBg.copy(alpha = 0.25f),
                            LimePrimary,
                            DarkCardBg
                        ),
                        startX = left,
                        endX = left + runnerWidth
                    ),
                    topLeft = Offset(left.coerceAtLeast(0f), 0f),
                    size = Size(
                        width = (left + runnerWidth).coerceAtMost(size.width) - left.coerceAtLeast(0f),
                        height = size.height
                    ).let { s -> if (s.width < 0f) Size(0f, size.height) else s },
                    cornerRadius = CornerRadius(size.height, size.height)
                )
            }
        }
    }
}

/**
 * Smooth navigation transition host combining:
 * - Crisp fade + subtle top-to-bottom slide (`AnimatedContent`) on route switch
 * - Stale-while-revalidate: shows cached content immediately + top-to-bottom loading bar when
 *   data already exists; shows tailored content-structured skeleton when initial data is empty
 * - Instant dismissal of loading indicator once data loads or fails.
 */
@Composable
fun <T> AzrilRouteTransitionHost(
    targetRoute: T,
    isRouteLoading: Boolean,
    showSkeletonWhenLoading: Boolean,
    skeletonContent: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
    screenContent: @Composable (T) -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = targetRoute,
            transitionSpec = {
                (fadeIn(animationSpec = tween(210, easing = FastOutSlowInEasing)) +
                    slideInVertically(
                        animationSpec = tween(230, easing = FastOutSlowInEasing),
                        initialOffsetY = { fullHeight -> -(fullHeight / 28).coerceIn(10, 28) }
                    )) togetherWith fadeOut(animationSpec = tween(140, easing = LinearEasing))
            },
            label = "azril_route_animated_content"
        ) { route ->
            if (isRouteLoading && showSkeletonWhenLoading) {
                skeletonContent(route)
            } else {
                screenContent(route)
            }
        }

        AzrilTopToBottomLoadingIndicator(
            visible = isRouteLoading,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Shared vertical shimmer brush moving from top to bottom for content-structured skeletons.
 */
@Composable
private fun rememberVerticalShimmerAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "azril_skeleton_pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )
    return alpha
}

@Composable
private fun SkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
    cornerRadius: Dp = 10.dp,
    color: Color = LightGreenSecondary,
    shimmerAlpha: Float
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(color.copy(alpha = shimmerAlpha))
    )
}

@Composable
private fun SkeletonCardContainer(
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = if (dark) DarkCardBg else PureWhite,
        border = BorderStroke(1.dp, if (dark) LimePrimary.copy(alpha = 0.22f) else BorderMint.copy(alpha = 0.7f))
    ) {
        Box(modifier = Modifier.padding(18.dp)) {
            content()
        }
    }
}

@Composable
fun StudentDestinationSkeleton(
    destination: StudentDestination,
    isExpanded: Boolean
) {
    when (destination) {
        StudentDestination.DASHBOARD -> StudentDashboardSkeleton(isExpanded)
        StudentDestination.RANKING -> StudentRankingSkeleton(isExpanded)
        StudentDestination.PROFILE -> StudentProfileSkeleton(isExpanded)
        StudentDestination.XP_HISTORY -> StudentXpHistorySkeleton(isExpanded)
        StudentDestination.ASSIGNMENTS -> StudentAssignmentsSkeleton(isExpanded)
        else -> GenericAzrilScreenSkeleton(isExpanded)
    }
}

/** Skeleton mirroring Beranda (StudentDashboardScreen) */
@Composable
fun StudentDashboardSkeleton(isExpanded: Boolean) {
    val alpha = rememberVerticalShimmerAlpha()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 18.dp, vertical = 16.dp)
            .testTag("skeleton_student_dashboard"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Page Header placeholder
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.52f), height = 24.dp, color = DarkCardBg.copy(alpha = 0.14f), shimmerAlpha = alpha)
            SkeletonBlock(Modifier.fillMaxWidth(0.82f), height = 13.dp, color = LightGreenSecondary, shimmerAlpha = alpha)
        }
        // Dark Hero Card (Streak + XP + Rank)
        SkeletonCardContainer(dark = true) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBlock(Modifier.width(120.dp), height = 22.dp, cornerRadius = 999.dp, color = LimePrimary.copy(alpha = 0.55f), shimmerAlpha = alpha)
                SkeletonBlock(Modifier.fillMaxWidth(0.75f), height = 24.dp, color = DarkCardElevated, shimmerAlpha = alpha)
                SkeletonBlock(Modifier.fillMaxWidth(0.60f), height = 14.dp, color = DarkCardElevated, shimmerAlpha = alpha)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SkeletonBlock(Modifier.weight(1f), height = 42.dp, cornerRadius = 16.dp, color = LimePrimary.copy(alpha = 0.35f), shimmerAlpha = alpha)
                    SkeletonBlock(Modifier.weight(1f), height = 42.dp, cornerRadius = 16.dp, color = DarkCardElevated, shimmerAlpha = alpha)
                }
            }
        }
        // 2x2 Stat Cards
        repeat(2) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) {
                    SkeletonCardContainer(modifier = Modifier.weight(1f)) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SkeletonBlock(Modifier.fillMaxWidth(0.65f), height = 12.dp, shimmerAlpha = alpha)
                            SkeletonBlock(Modifier.fillMaxWidth(0.45f), height = 26.dp, color = DarkCardBg.copy(alpha = 0.14f), shimmerAlpha = alpha)
                            SkeletonBlock(Modifier.fillMaxWidth(0.8f), height = 11.dp, shimmerAlpha = alpha)
                        }
                    }
                }
            }
        }
        // Activity section
        SkeletonCardContainer {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBlock(Modifier.fillMaxWidth(0.45f), height = 16.dp, color = DarkCardBg.copy(alpha = 0.14f), shimmerAlpha = alpha)
                repeat(3) {
                    SkeletonBlock(Modifier.fillMaxWidth(), height = 48.dp, cornerRadius = 16.dp, color = SoftMintBg, shimmerAlpha = alpha)
                }
            }
        }
    }
}

/** Skeleton mirroring Ranking (StudentRankingScreen: Hero + Top 3 Podium + Leaderboard List) */
@Composable
fun StudentRankingSkeleton(isExpanded: Boolean) {
    val alpha = rememberVerticalShimmerAlpha()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 18.dp, vertical = 16.dp)
            .testTag("skeleton_student_ranking"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.55f), height = 24.dp, color = DarkCardBg.copy(alpha = 0.14f), shimmerAlpha = alpha)
            SkeletonBlock(Modifier.fillMaxWidth(0.84f), height = 13.dp, shimmerAlpha = alpha)
        }
        // Dark Rank Position Summary
        SkeletonCardContainer(dark = true) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBlock(Modifier.width(110.dp), height = 20.dp, cornerRadius = 999.dp, color = LimePrimary.copy(alpha = 0.55f), shimmerAlpha = alpha)
                    SkeletonBlock(Modifier.fillMaxWidth(0.75f), height = 24.dp, color = DarkCardElevated, shimmerAlpha = alpha)
                    SkeletonBlock(Modifier.fillMaxWidth(0.55f), height = 12.dp, color = DarkCardElevated, shimmerAlpha = alpha)
                }
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(LimePrimary.copy(alpha = alpha * 0.45f))
                )
            }
        }
        // Top 3 Podium Skeleton
        SkeletonCardContainer {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Rank 2
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(LightGreenSecondary.copy(alpha = alpha)))
                    SkeletonBlock(Modifier.fillMaxWidth(0.8f), height = 12.dp, shimmerAlpha = alpha)
                    SkeletonBlock(Modifier.fillMaxWidth(), height = 74.dp, cornerRadius = 16.dp, color = SoftMintBg, shimmerAlpha = alpha)
                }
                // Rank 1 (taller lime podium)
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(LimePrimary.copy(alpha = alpha * 0.7f)))
                    SkeletonBlock(Modifier.fillMaxWidth(0.85f), height = 13.dp, color = DarkCardBg.copy(alpha = 0.16f), shimmerAlpha = alpha)
                    SkeletonBlock(Modifier.fillMaxWidth(), height = 102.dp, cornerRadius = 18.dp, color = LimePrimary.copy(alpha = 0.45f), shimmerAlpha = alpha)
                }
                // Rank 3
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(LightGreenSecondary.copy(alpha = alpha)))
                    SkeletonBlock(Modifier.fillMaxWidth(0.8f), height = 12.dp, shimmerAlpha = alpha)
                    SkeletonBlock(Modifier.fillMaxWidth(), height = 62.dp, cornerRadius = 16.dp, color = SoftMintBg, shimmerAlpha = alpha)
                }
            }
        }
        // Leaderboard Rows
        repeat(4) {
            SkeletonCardContainer {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(LightGreenSecondary.copy(alpha = alpha)))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SkeletonBlock(Modifier.fillMaxWidth(0.62f), height = 15.dp, color = DarkCardBg.copy(alpha = 0.14f), shimmerAlpha = alpha)
                        SkeletonBlock(Modifier.fillMaxWidth(0.42f), height = 11.dp, shimmerAlpha = alpha)
                    }
                    SkeletonBlock(Modifier.width(64.dp), height = 24.dp, cornerRadius = 999.dp, color = LimePrimary.copy(alpha = 0.5f), shimmerAlpha = alpha)
                }
            }
        }
    }
}

/** Skeleton mirroring Akun / Profil (StudentProfileScreen) */
@Composable
fun StudentProfileSkeleton(isExpanded: Boolean) {
    val alpha = rememberVerticalShimmerAlpha()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 18.dp, vertical = 16.dp)
            .testTag("skeleton_student_profile"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SkeletonBlock(Modifier.fillMaxWidth(0.45f), height = 24.dp, color = DarkCardBg.copy(alpha = 0.14f), shimmerAlpha = alpha)
        // Avatar & identity dark hero
        SkeletonCardContainer(dark = true) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(60.dp).clip(CircleShape).background(LimePrimary.copy(alpha = alpha * 0.6f)))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBlock(Modifier.fillMaxWidth(0.7f), height = 18.dp, color = DarkCardElevated, shimmerAlpha = alpha)
                    SkeletonBlock(Modifier.fillMaxWidth(0.48f), height = 13.dp, color = DarkCardElevated, shimmerAlpha = alpha)
                    SkeletonBlock(Modifier.width(96.dp), height = 20.dp, cornerRadius = 999.dp, color = LimePrimary.copy(alpha = 0.45f), shimmerAlpha = alpha)
                }
            }
        }
        // Info fields card
        SkeletonCardContainer {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SkeletonBlock(Modifier.fillMaxWidth(0.42f), height = 16.dp, color = DarkCardBg.copy(alpha = 0.14f), shimmerAlpha = alpha)
                repeat(4) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SkeletonBlock(Modifier.width(90.dp), height = 11.dp, shimmerAlpha = alpha)
                        SkeletonBlock(Modifier.fillMaxWidth(0.75f), height = 15.dp, color = SoftMintBg, shimmerAlpha = alpha)
                    }
                }
            }
        }
        // Security card
        SkeletonCardContainer {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBlock(Modifier.fillMaxWidth(0.5f), height = 16.dp, color = DarkCardBg.copy(alpha = 0.14f), shimmerAlpha = alpha)
                SkeletonBlock(Modifier.fillMaxWidth(), height = 46.dp, cornerRadius = 999.dp, color = LightGreenSecondary, shimmerAlpha = alpha)
            }
        }
    }
}

/** Skeleton mirroring Riwayat XP (StudentXpHistoryScreen) */
@Composable
fun StudentXpHistorySkeleton(isExpanded: Boolean) {
    val alpha = rememberVerticalShimmerAlpha()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 18.dp, vertical = 16.dp)
            .testTag("skeleton_student_xp_history"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.46f), height = 24.dp, color = DarkCardBg.copy(alpha = 0.14f), shimmerAlpha = alpha)
            SkeletonBlock(Modifier.fillMaxWidth(0.78f), height = 13.dp, shimmerAlpha = alpha)
        }
        // Dark Total XP Hero
        SkeletonCardContainer(dark = true) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SkeletonBlock(Modifier.width(104.dp), height = 20.dp, cornerRadius = 999.dp, color = LimePrimary.copy(alpha = 0.55f), shimmerAlpha = alpha)
                SkeletonBlock(Modifier.width(160.dp), height = 30.dp, color = DarkCardElevated, shimmerAlpha = alpha)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    repeat(3) {
                        SkeletonBlock(Modifier.weight(1f), height = 44.dp, cornerRadius = 14.dp, color = DarkCardElevated, shimmerAlpha = alpha)
                    }
                }
            }
        }
        // Filter Chips row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) {
                SkeletonBlock(Modifier.width(86.dp), height = 32.dp, cornerRadius = 999.dp, color = LightGreenSecondary, shimmerAlpha = alpha)
            }
        }
        // XP Timeline items
        repeat(4) {
            SkeletonCardContainer {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(LightGreenSecondary.copy(alpha = alpha)))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SkeletonBlock(Modifier.fillMaxWidth(0.68f), height = 14.dp, color = DarkCardBg.copy(alpha = 0.14f), shimmerAlpha = alpha)
                        SkeletonBlock(Modifier.fillMaxWidth(0.48f), height = 11.dp, shimmerAlpha = alpha)
                    }
                    SkeletonBlock(Modifier.width(60.dp), height = 26.dp, cornerRadius = 999.dp, color = LimePrimary.copy(alpha = 0.55f), shimmerAlpha = alpha)
                }
            }
        }
    }
}

/** Skeleton mirroring Tugas (StudentAssignmentsScreen) */
@Composable
fun StudentAssignmentsSkeleton(isExpanded: Boolean) {
    val alpha = rememberVerticalShimmerAlpha()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 18.dp, vertical = 16.dp)
            .testTag("skeleton_student_assignments"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.48f), height = 24.dp, color = DarkCardBg.copy(alpha = 0.14f), shimmerAlpha = alpha)
            SkeletonBlock(Modifier.fillMaxWidth(0.82f), height = 13.dp, shimmerAlpha = alpha)
        }
        // Filter strip
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) {
                SkeletonBlock(Modifier.width(92.dp), height = 32.dp, cornerRadius = 999.dp, color = LightGreenSecondary, shimmerAlpha = alpha)
            }
        }
        // Assignment Cards
        repeat(3) {
            SkeletonCardContainer {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SkeletonBlock(Modifier.width(96.dp), height = 22.dp, cornerRadius = 999.dp, color = LightGreenSecondary, shimmerAlpha = alpha)
                        SkeletonBlock(Modifier.width(78.dp), height = 22.dp, cornerRadius = 999.dp, color = SoftMintBg, shimmerAlpha = alpha)
                    }
                    SkeletonBlock(Modifier.fillMaxWidth(0.75f), height = 18.dp, color = DarkCardBg.copy(alpha = 0.15f), shimmerAlpha = alpha)
                    SkeletonBlock(Modifier.fillMaxWidth(0.95f), height = 12.dp, shimmerAlpha = alpha)
                    SkeletonBlock(Modifier.fillMaxWidth(0.62f), height = 12.dp, shimmerAlpha = alpha)
                    Spacer(modifier = Modifier.height(2.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(), height = 44.dp, cornerRadius = 999.dp, color = DarkCardBg.copy(alpha = 0.16f), shimmerAlpha = alpha)
                }
            }
        }
    }
}

@Composable
fun GenericAzrilScreenSkeleton(isExpanded: Boolean) {
    val alpha = rememberVerticalShimmerAlpha()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpanded) 28.dp else 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SkeletonBlock(Modifier.fillMaxWidth(0.5f), height = 24.dp, color = DarkCardBg.copy(alpha = 0.14f), shimmerAlpha = alpha)
        SkeletonBlock(Modifier.fillMaxWidth(0.8f), height = 13.dp, shimmerAlpha = alpha)
        repeat(3) {
            SkeletonCardContainer {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonBlock(Modifier.fillMaxWidth(0.6f), height = 16.dp, color = DarkCardBg.copy(alpha = 0.14f), shimmerAlpha = alpha)
                    SkeletonBlock(Modifier.fillMaxWidth(), height = 12.dp, shimmerAlpha = alpha)
                    SkeletonBlock(Modifier.fillMaxWidth(0.78f), height = 12.dp, shimmerAlpha = alpha)
                }
            }
        }
    }
}
