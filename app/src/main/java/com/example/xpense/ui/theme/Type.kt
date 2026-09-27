package com.example.xpense.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.xpense.R

// Both families ship as single variable fonts; each weight is an instance of the wght axis.
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Sora = FontFamily(
    variable(R.font.sora, 300),
    variable(R.font.sora, 400),
    variable(R.font.sora, 500),
    variable(R.font.sora, 600),
    variable(R.font.sora, 700)
)

val Mono = FontFamily(
    variable(R.font.jetbrains_mono, 400),
    variable(R.font.jetbrains_mono, 500),
    variable(R.font.jetbrains_mono, 600)
)

/** The design's type scale. Amounts and other figures always use a Mono style. */
object XType {
    val display = TextStyle(fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, letterSpacing = (-0.03).em)
    val displayS = TextStyle(fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = (-0.03).em)
    val h1 = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = (-0.03).em)
    val h2 = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.02).em)
    val h3 = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.02).em)
    val section = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    val bodyStrong = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    val body = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp)
    val small = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp)
    val smallStrong = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    val caption = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Normal, fontSize = 12.sp)
    val captionStrong = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    val micro = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Normal, fontSize = 11.sp)
    val nav = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
    /** Uppercase section label; callers pass already-uppercased text. */
    val overline = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.1.em)
    val overlineS = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Normal, fontSize = 11.sp, letterSpacing = 0.08.em)

    val monoL = TextStyle(fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    val mono = TextStyle(fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    val monoM = TextStyle(fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    val monoS = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 12.sp)
    val monoXS = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 10.sp)
}

private val base = Typography()

/** Material defaults re-based on Sora so stock M3 widgets match the design. */
val Typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Sora),
    displayMedium = base.displayMedium.copy(fontFamily = Sora),
    displaySmall = base.displaySmall.copy(fontFamily = Sora),
    headlineLarge = base.headlineLarge.copy(fontFamily = Sora),
    headlineMedium = base.headlineMedium.copy(fontFamily = Sora),
    headlineSmall = base.headlineSmall.copy(fontFamily = Sora),
    titleLarge = base.titleLarge.copy(fontFamily = Sora),
    titleMedium = base.titleMedium.copy(fontFamily = Sora),
    titleSmall = base.titleSmall.copy(fontFamily = Sora),
    bodyLarge = base.bodyLarge.copy(fontFamily = Sora),
    bodyMedium = base.bodyMedium.copy(fontFamily = Sora),
    bodySmall = base.bodySmall.copy(fontFamily = Sora),
    labelLarge = base.labelLarge.copy(fontFamily = Sora),
    labelMedium = base.labelMedium.copy(fontFamily = Sora),
    labelSmall = base.labelSmall.copy(fontFamily = Sora)
)
