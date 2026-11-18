package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// AZRIL CLAS — LIME GREEN MINIMALIST DESIGN SYSTEM
// ============================================================================

// Core Brand Tokens
val LimePrimary = Color(0xFFB5D45D)       // Primary: Lime Green #B5D45D
val LimeBright = Color(0xFFC5E370)        // Highlight Lime for hover/emphasis
val LimeDeep = Color(0xFF536F1A)          // Accessible Olive-Lime for links/icons on light
val LimeInk = Color(0xFF364B0F)           // High-contrast dark lime text on light green badges

val SoftMintBg = Color(0xFFF0F8DF)        // Background: Soft Mint #F0F8DF
val LightGreenSecondary = Color(0xFFDCECB8) // Secondary: Light Green #DCECB8
val MintSurfaceSoft = Color(0xFFE8F3CE)   // Subtle mint surface variant

val DarkCardBg = Color(0xFF171A17)        // Dark Card: #171A17
val DarkCardElevated = Color(0xFF232822)  // Elevated surface inside Dark Card / Sidebar
val DarkObsidian = Color(0xFF101210)      // Deepest footer/anchor dark

val PureWhite = Color(0xFFFFFFFF)         // White: #FFFFFF
val InkText = Color(0xFF20251D)           // Text: #20251D
val InkMuted = Color(0xFF4E5849)          // Secondary body text
val InkSubtle = Color(0xFF6F7B68)         // Helper/caption text
val OnDarkMuted = Color(0xFFA7B3A0)       // Subtle text on dark cards

val BorderMint = Color(0xFFD5E4B6)        // Soft organic border
val BorderStrongMint = Color(0xFFB8CE8E)  // Stronger outline for inputs

// Semantic Status Colors (Harmonized with Lime & Mint Minimalism)
val Emerald700 = Color(0xFF2B5B1E)
val Emerald600 = Color(0xFF3E7B27)
val Emerald50 = Color(0xFFE3F4D7)

val Amber700 = Color(0xFF8A5600)
val Amber600 = Color(0xFFC27C0A)
val Amber50 = Color(0xFFFEF6DC)

val Rose700 = Color(0xFF9F1D2C)
val Rose600 = Color(0xFFD13447)
val Rose50 = Color(0xFFFDECEF)

// ============================================================================
// HARMONIZED COMPATIBILITY ALIASES (Ensures 100% palette consistency across all modules)
// ============================================================================
val Navy950 = DarkObsidian
val Navy900 = InkText
val Navy800 = DarkCardElevated
val Navy700 = Color(0xFF343C31)

val RoyalBlue700 = LimeInk
val RoyalBlue600 = LimeDeep
val RoyalBlue500 = LimePrimary
val RoyalBlue100 = Color(0xFFCFE2A3)
val RoyalBlue50 = LightGreenSecondary

val CanvasWhite = SoftMintBg
val SurfaceMuted = LightGreenSecondary
val BorderLight = BorderMint
val BorderStrong = BorderStrongMint
val Slate600 = InkMuted
val Slate500 = InkSubtle
val Slate400 = OnDarkMuted

val Sky700 = LimeInk
val Sky600 = LimeDeep
val Sky50 = MintSurfaceSoft

val Indigo600 = DarkCardBg
val Indigo50 = LightGreenSecondary
