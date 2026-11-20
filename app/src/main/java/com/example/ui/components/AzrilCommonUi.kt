package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.model.ToastMessage
import com.example.ui.model.ToastType
import com.example.ui.theme.Amber50
import com.example.ui.theme.Amber600
import com.example.ui.theme.BorderMint
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.DarkCardElevated
import com.example.ui.theme.Emerald50
import com.example.ui.theme.Emerald600
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
import com.example.ui.theme.Rose600

@Composable
fun AzrilCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    containerColor: Color = PureWhite,
    borderColor: Color = BorderMint.copy(alpha = 0.65f),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun AzrilDarkCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(22.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        border = BorderStroke(1.dp, LimePrimary.copy(alpha = 0.22f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun AzrilMintCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = LightGreenSecondary),
        border = BorderStroke(1.dp, LimePrimary.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun AzrilPageHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isCompactMobile = maxWidth < 600.dp
        if (isCompactMobile) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = InkText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSubtle,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (action != null) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        action()
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = InkText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSubtle,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (action != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    action()
                }
            }
        }
    }
}

@Composable
fun AzrilStatCard(
    label: String,
    value: String,
    helper: String,
    icon: ImageVector,
    accentColor: Color = LimeInk,
    accentBg: Color = LightGreenSecondary,
    darkVariant: Boolean = false,
    mintVariant: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        darkVariant -> DarkCardBg
        mintVariant -> LightGreenSecondary
        else -> PureWhite
    }
    val borderColor = when {
        darkVariant -> LimePrimary.copy(alpha = 0.2f)
        mintVariant -> LimePrimary.copy(alpha = 0.35f)
        else -> BorderMint.copy(alpha = 0.65f)
    }
    val titleColor = if (darkVariant) OnDarkMuted else InkSubtle
    val valueColor = if (darkVariant) LimePrimary else InkText
    val helperColor = if (darkVariant) OnDarkMuted.copy(alpha = 0.85f) else InkMuted
    val iconContainer = when {
        darkVariant -> DarkCardElevated
        mintVariant -> PureWhite
        else -> accentBg
    }
    val iconTint = when {
        darkVariant -> LimePrimary
        mintVariant -> DarkCardBg
        else -> accentColor
    }

    AzrilCard(
        modifier = modifier,
        contentPadding = PaddingValues(18.dp),
        containerColor = bgColor,
        borderColor = borderColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineLarge,
                    color = valueColor
                )
            }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = helper,
            style = MaterialTheme.typography.bodySmall,
            color = helperColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AzrilStatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AzrilSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    testTag: String = "search_input"
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.testTag(testTag),
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        placeholder = { Text(placeholder, color = InkSubtle, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = stringResource(id = R.string.cd_search),
                tint = InkMuted
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(id = R.string.cd_clear_search),
                        tint = InkMuted
                    )
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = PureWhite,
            unfocusedContainerColor = PureWhite,
            focusedBorderColor = DarkCardBg,
            unfocusedBorderColor = BorderMint
        )
    )
}

@Composable
fun AzrilEmptyState(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    showIllustration: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    AzrilCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showIllustration) {
                Image(
                    painter = painterResource(id = R.drawable.img_lime_empty_state),
                    contentDescription = stringResource(id = R.string.cd_empty_illustration),
                    modifier = Modifier
                        .height(148.dp)
                        .clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(LightGreenSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = DarkCardBg,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = InkText,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = InkSubtle,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp)
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LimePrimary, contentColor = InkText),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                    modifier = Modifier.testTag("empty_state_action")
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun AzrilErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    AzrilCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Rose50),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = "Terjadi kesalahan",
                    tint = Rose600,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text("Gagal Memuat Data", style = MaterialTheme.typography.titleMedium, color = InkText)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = InkMuted, textAlign = TextAlign.Center)
            OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, DarkCardBg)
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp), tint = DarkCardBg)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(id = R.string.action_retry), color = InkText)
            }
        }
    }
}

@Composable
fun AzrilSkeletonList(
    count: Int = 4,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(count) {
            AzrilCard(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.42f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(LightGreenSecondary.copy(alpha = alpha))
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(LightGreenSecondary.copy(alpha = alpha))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.58f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(LightGreenSecondary.copy(alpha = alpha))
                )
            }
        }
    }
}

@Composable
fun AzrilPaginationBar(
    currentPage: Int,
    totalPages: Int,
    totalItems: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeTotalPages = totalPages.coerceAtLeast(1)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Total $totalItems data • Halaman $currentPage dari $safeTotalPages",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (currentPage > 1) PureWhite else LightGreenSecondary.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, BorderMint)
            ) {
                IconButton(
                    onClick = { if (currentPage > 1) onPageChange(currentPage - 1) },
                    enabled = currentPage > 1,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Halaman sebelumnya", tint = InkText)
                }
            }
            Surface(
                shape = CircleShape,
                color = if (currentPage < safeTotalPages) PureWhite else LightGreenSecondary.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, BorderMint)
            ) {
                IconButton(
                    onClick = { if (currentPage < safeTotalPages) onPageChange(currentPage + 1) },
                    enabled = currentPage < safeTotalPages,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Halaman berikutnya", tint = InkText)
                }
            }
        }
    }
}

@Composable
fun AzrilConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        containerColor = PureWhite,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = InkText) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium, color = InkMuted) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Rose600, contentColor = PureWhite),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.testTag("confirm_delete_button")
            ) {
                Text(stringResource(id = R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.action_cancel), color = InkMuted)
            }
        }
    )
}

@Composable
fun AzrilToastBanner(
    toast: ToastMessage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (accentColor, icon) = when (toast.type) {
        ToastType.SUCCESS -> LimePrimary to Icons.Rounded.CheckCircle
        ToastType.WARNING -> Amber600 to Icons.Rounded.WarningAmber
        ToastType.ERROR -> Rose600 to Icons.Rounded.ErrorOutline
        ToastType.INFO -> LimePrimary to Icons.Rounded.Info
    }
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        color = DarkCardBg,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }
            Text(
                text = toast.message,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = PureWhite,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Tutup notifikasi", tint = OnDarkMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun Stage1BannerStrip(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = LightGreenSecondary,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, LimePrimary.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(PureWhite),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = LimeDeep,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = stringResource(id = R.string.stage_1_preview_notice),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = LimeInk
            )
        }
    }
}
