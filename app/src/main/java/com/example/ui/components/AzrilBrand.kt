package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.InkSubtle
import com.example.ui.theme.InkText
import com.example.ui.theme.LimeDeep
import com.example.ui.theme.LimePrimary
import com.example.ui.theme.OnDarkMuted
import com.example.ui.theme.PureWhite

/**
 * Single-source official Azril Clas logo mark.
 * Preserves the logo's exact original colors, proportions, and silhouette via ContentScale.Fit.
 */
@Composable
fun AzrilOfficialLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 42.dp
) {
    Image(
        painter = painterResource(id = R.drawable.ic_azril_logo_mark),
        contentDescription = stringResource(id = R.string.cd_app_logo),
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size)
    )
}

@Composable
fun AzrilBrandLogo(
    modifier: Modifier = Modifier,
    darkSurface: Boolean = false,
    showTagline: Boolean = false,
    iconSize: Dp = 44.dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AzrilOfficialLogoMark(size = iconSize)
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
