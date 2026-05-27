package com.example.xpense.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val XpenseDarkScheme = darkColorScheme(
    primary            = PurplePrimary,
    onPrimary          = Color.White,
    primaryContainer   = PurpleDark,
    onPrimaryContainer = Color.White,
    secondary          = PurpleLight,
    onSecondary        = Color.White,
    tertiary           = GreenPositive,
    onTertiary         = Color.White,
    background         = DarkBg,
    onBackground       = TextPrimary,
    surface            = DarkCard,
    onSurface          = TextPrimary,
    surfaceVariant     = DarkSurface,
    onSurfaceVariant   = TextSecondary,
    outline            = DarkBorder,
    error              = RedNegative,
    onError            = Color.White,
)

@Composable
fun XpenseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = XpenseDarkScheme,
        typography  = Typography,
        content     = content
    )
}
