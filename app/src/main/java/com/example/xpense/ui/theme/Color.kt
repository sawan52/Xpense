package com.example.xpense.ui.theme

import androidx.compose.ui.graphics.Color

// ── Dark background layers ──────────────────────────────────────────────────
val DarkBg      = Color(0xFF0D0D1A)
val DarkCard    = Color(0xFF1A1A2E)
val DarkSurface = Color(0xFF252545)
val DarkBorder  = Color(0xFF2D2D4E)

// ── Brand purple ────────────────────────────────────────────────────────────
val PurplePrimary = Color(0xFF7C3AED)
val PurpleLight   = Color(0xFF9D6CF6)
val PurpleDark    = Color(0xFF5B21B6)

// ── Status ──────────────────────────────────────────────────────────────────
val GreenPositive = Color(0xFF10B981)
val RedNegative   = Color(0xFFEF4444)

// ── Text ────────────────────────────────────────────────────────────────────
val TextPrimary   = Color.White
val TextSecondary = Color(0xFF94A3B8)
val TextMuted     = Color(0xFF64748B)

// ── Category palette ────────────────────────────────────────────────────────
val CategoryFoodColor          = Color(0xFFFF6B35)
val CategoryShoppingColor      = Color(0xFF8B5CF6)
val CategoryTravelColor        = Color(0xFF0EA5E9)
val CategoryBillsColor         = Color(0xFF3B82F6)
val CategoryHealthColor        = Color(0xFF10B981)
val CategoryEntertainmentColor = Color(0xFFF59E0B)
val CategoryOthersColor        = Color(0xFF6B7280)

// Distinct palette for non-default categories so every pie-chart slice (and its icon/legend)
// is visually separable instead of all sharing the grey "Others" colour. Assigned by category id.
val CategoryPalette = listOf(
    Color(0xFFEC4899), // pink
    Color(0xFF14B8A6), // teal
    Color(0xFFC026D3), // magenta
    Color(0xFFEAB308), // gold
    Color(0xFFEF4444), // red
    Color(0xFF84CC16), // lime
    Color(0xFF06B6D4), // cyan
    Color(0xFFF97316), // orange
    Color(0xFF6366F1), // indigo
    Color(0xFFDB2777), // deep pink
    Color(0xFF65A30D), // olive
    Color(0xFF0891B2), // dark cyan
    Color(0xFF9333EA), // violet
    Color(0xFFE11D48), // rose
    Color(0xFFCA8A04), // dark gold
    Color(0xFF2DD4BF)  // aqua
)

// ── Legacy names kept so nothing else breaks ────────────────────────────────
val Purple80         = Color(0xFFD0BCFF)
val PurpleGrey80     = Color(0xFFCCC2DC)
val Pink80           = Color(0xFFEFB8C8)
val Purple40         = Color(0xFF6650a4)
val PurpleGrey40     = Color(0xFF625b71)
val Pink40           = Color(0xFF7D5260)
val BackgroundOffWhite = Color(0xFFF5F7FF)
val PrimaryIndigo    = Color(0xFF4F46E5)
val SecondaryIndigo  = Color(0xFF6366F1)
val AccentBlue       = Color(0xFF0EA5E9)
val PremiumBlack     = Color(0xFF1E293B)
val SoftGray         = Color(0xFF94A3B8)
val CardWhite        = Color.White
