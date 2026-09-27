package com.example.xpense.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The design's token set (see the Claude Design export: `.xp` CSS variables). Screens read these
 * through `XpenseTheme.colors` instead of hardcoding, so the dark/light toggle restyles everything.
 */
@Immutable
data class XpenseColors(
    val isDark: Boolean,
    val bg: Color,        // app background
    val bg2: Color,       // sheets, dialogs, donut holes
    val card: Color,      // translucent card fill
    val card2: Color,     // nested fills: tracks, inputs, secondary buttons
    val line: Color,      // 1px hairline borders and dividers
    val tx: Color,        // primary text
    val tx2: Color,       // secondary text
    val tx3: Color,       // muted text / chevrons
    val neg: Color,       // spend amounts, destructive
    val pos: Color,       // savings, success
    val nav: Color,       // floating bottom bar
    val ac: Color,        // accent (violet)
    val ac2: Color,       // second accent (cyan)
    val acSoft: Color,    // accent at 16%
    val hero: List<Color>,// hero card gradient stops
    val heroLine: Color,
    val glow: Float       // opacity of the background glow blobs
)

private val Violet = Color(0xFF8B5CF6)
private val Cyan = Color(0xFF22D3EE)

val DarkXpenseColors = XpenseColors(
    isDark = true,
    bg = Color(0xFF06060C),
    bg2 = Color(0xFF0D0D18),
    card = Color.White.copy(alpha = 0.035f),
    card2 = Color.White.copy(alpha = 0.06f),
    line = Color.White.copy(alpha = 0.08f),
    tx = Color(0xFFF3F2FA),
    tx2 = Color(0xFF9896AD),
    tx3 = Color(0xFF5E5C72),
    neg = Color(0xFFFF6B86),
    pos = Color(0xFF34E0A1),
    // No backdrop blur in Compose, so the bar is solid rather than the design's 78% glass.
    nav = Color(0xFF0E0E1A),
    ac = Violet,
    ac2 = Cyan,
    acSoft = Violet.copy(alpha = 0.16f),
    hero = listOf(Color(0xFF1B1335), Color(0xFF0C0A1C)),
    heroLine = Color.White.copy(alpha = 0.10f),
    glow = 0.55f
)

val LightXpenseColors = XpenseColors(
    isDark = false,
    bg = Color(0xFFF3F2F9),
    bg2 = Color.White,
    card = Color.White,
    card2 = Color(0xFFEEECF7),
    line = Color(0xFF18123C).copy(alpha = 0.08f),
    tx = Color(0xFF14121F),
    tx2 = Color(0xFF65627A),
    tx3 = Color(0xFFA3A1B5),
    neg = Color(0xFFE11D48),
    pos = Color(0xFF059669),
    nav = Color.White,
    ac = Violet,
    ac2 = Cyan,
    acSoft = Violet.copy(alpha = 0.16f),
    hero = listOf(Violet, Color(0xFF5B3FD6), Color(0xFF3730A3)),
    heroLine = Color.White.copy(alpha = 0.25f),
    glow = 0.22f
)

val LocalXpenseColors = staticCompositionLocalOf { DarkXpenseColors }

// ── Category palette (from the design) ──────────────────────────────────────
val CategoryFoodColor          = Color(0xFFF87171)
val CategoryShoppingColor      = Color(0xFFA78BFA)
val CategoryTravelColor        = Color(0xFF22D3EE)
val CategoryBillsColor         = Color(0xFF60A5FA)
val CategoryHealthColor        = Color(0xFF34D399)
val CategoryEntertainmentColor = Color(0xFFFBBF24)
val CategoryOthersColor        = Color(0xFF94A3B8)

// Distinct palette for non-default categories so every donut slice (and its icon/legend) is
// visually separable. Assigned by category id.
val CategoryPalette = listOf(
    Color(0xFFFB923C), // orange
    Color(0xFFC084FC), // purple
    Color(0xFF4ADE80), // green
    Color(0xFF818CF8), // indigo
    Color(0xFF38BDF8), // sky
    Color(0xFFF472B6), // pink
    Color(0xFFFACC15), // yellow
    Color(0xFF2DD4BF), // teal
    Color(0xFFE879F9), // fuchsia
    Color(0xFFA3E635), // lime
    Color(0xFFFB7185), // rose
    Color(0xFF93C5FD), // light blue
    Color(0xFFFDBA74), // peach
    Color(0xFF86EFAC), // mint
    Color(0xFFD8B4FE), // lavender
    Color(0xFF67E8F9)  // aqua
)
