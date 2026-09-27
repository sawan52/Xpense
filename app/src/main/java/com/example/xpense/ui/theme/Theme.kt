package com.example.xpense.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

@Composable
fun XpenseTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val c = if (dark) DarkXpenseColors else LightXpenseColors
    // Map the tokens onto Material's scheme too, so stock M3 pieces (text fields, sheets,
    // progress indicators) pick up the same palette.
    val scheme = if (dark) darkColorScheme(
        primary = c.ac, onPrimary = Color.White, secondary = c.ac2, onSecondary = Color.White,
        background = c.bg, onBackground = c.tx, surface = c.bg2, onSurface = c.tx,
        surfaceVariant = c.card2, onSurfaceVariant = c.tx2, surfaceContainer = c.bg2,
        surfaceContainerHigh = c.bg2, surfaceContainerLow = c.bg2, outline = c.line,
        outlineVariant = c.line, error = c.neg, onError = Color.White
    ) else lightColorScheme(
        primary = c.ac, onPrimary = Color.White, secondary = c.ac2, onSecondary = Color.White,
        background = c.bg, onBackground = c.tx, surface = c.bg2, onSurface = c.tx,
        surfaceVariant = c.card2, onSurfaceVariant = c.tx2, surfaceContainer = c.bg2,
        surfaceContainerHigh = c.bg2, surfaceContainerLow = c.bg2, outline = c.line,
        outlineVariant = c.line, error = c.neg, onError = Color.White
    )
    CompositionLocalProvider(LocalXpenseColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = Typography) {
            CompositionLocalProvider(
                LocalContentColor provides c.tx,
                LocalTextStyle provides XType.body,
                content = content
            )
        }
    }
}

object XpenseTheme {
    val colors: XpenseColors
        @Composable @ReadOnlyComposable get() = LocalXpenseColors.current
}
