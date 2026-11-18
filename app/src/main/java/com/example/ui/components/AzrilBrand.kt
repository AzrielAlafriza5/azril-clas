package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.InkSubtle
import com.example.ui.theme.InkText
import com.example.ui.theme.LimeDeep
import com.example.ui.theme.LimePrimary
import com.example.ui.theme.OnDarkMuted
import com.example.ui.theme.PureWhite

@Composable
fun AzrilBrandLogo(
    modifier: Modifier = Modifier,
    darkSurface: Boolean = false,
    showTagline: Boolean = false,
    iconSize: Dp = 42.dp
) {
    val badgeShape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(badgeShape)
                .background(if (darkSurface) LimePrimary else DarkCardBg)
                .border(
                    width = 1.dp,
                    color = if (darkSurface) PureWhite.copy(alpha = 0.25f) else LimePrimary.copy(alpha = 0.45f),
                    shape = badgeShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.School,
                contentDescription = stringResource(id = R.string.cd_app_logo),
                tint = if (darkSurface) DarkCardBg else LimePrimary,
                modifier = Modifier.size(iconSize * 0.56f)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "AZRIL",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    ),
                    color = if (darkSurface) PureWhite else InkText
                )
                Text(
                    text = "CLAS",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    ),
                    color = if (darkSurface) LimePrimary else LimeDeep
                )
            }
            if (showTagline) {
                Text(
                    text = stringResource(id = R.string.app_tagline),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (darkSurface) OnDarkMuted else InkSubtle
                )
            }
        }
    }
}
