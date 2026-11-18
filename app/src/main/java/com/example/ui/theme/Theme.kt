package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val AzrilLimeMinimalistColorScheme = lightColorScheme(
    primary = LimePrimary,
    onPrimary = InkText,
    primaryContainer = LightGreenSecondary,
    onPrimaryContainer = InkText,
    secondary = DarkCardBg,
    onSecondary = PureWhite,
    secondaryContainer = LightGreenSecondary,
    onSecondaryContainer = InkText,
    tertiary = LimeDeep,
    onTertiary = PureWhite,
    tertiaryContainer = MintSurfaceSoft,
    onTertiaryContainer = LimeInk,
    background = SoftMintBg,
    onBackground = InkText,
    surface = PureWhite,
    onSurface = InkText,
    surfaceVariant = LightGreenSecondary,
    onSurfaceVariant = InkMuted,
    outline = BorderMint,
    outlineVariant = BorderStrongMint,
    error = Rose600,
    onError = PureWhite,
    errorContainer = Rose50,
    onErrorContainer = Rose700
)

val AzrilShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun AzrilClasTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AzrilLimeMinimalistColorScheme,
        typography = AzrilTypography,
        shapes = AzrilShapes,
        content = content
    )
}
