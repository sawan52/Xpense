package com.example.xpense.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Premium Fintech Theme
val BackgroundOffWhite = Color(0xFFF5F7FF)
val PrimaryIndigo = Color(0xFF4F46E5)
val SecondaryIndigo = Color(0xFF6366F1)
val AccentBlue = Color(0xFF0EA5E9)
val PremiumBlack = Color(0xFF1E293B)
val SoftGray = Color(0xFF94A3B8)
val CardWhite = Color(0xFFFFFFFF)

val PrimaryGradient = Brush.verticalGradient(
    colors = listOf(PrimaryIndigo, SecondaryIndigo)
)

val GlassGradient = Brush.verticalGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.8f),
        Color.White.copy(alpha = 0.4f)
    )
)
