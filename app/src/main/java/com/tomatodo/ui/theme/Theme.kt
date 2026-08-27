package com.tomatodo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Cinnabar,
    onPrimary = PaperCard,
    primaryContainer = Color(0xFFF0D9CF),
    onPrimaryContainer = Ink,
    secondary = PineGreen,
    onSecondary = PaperCard,
    secondaryContainer = Color(0xFFDCE8E0),
    onSecondaryContainer = Ink,
    tertiary = Ochre,
    onTertiary = PaperCard,
    background = PaperWhite,
    onBackground = Ink,
    surface = PaperCard,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFE9DD),
    onSurfaceVariant = InkMuted,
    outline = Line,
    outlineVariant = Color(0xFFE3DDD1),
    error = Color(0xFFB3261E),
    onError = PaperCard
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD78A6B),
    onPrimary = Color(0xFF3A1C12),
    primaryContainer = Color(0xFF5A2E1D),
    onPrimaryContainer = Color(0xFFF2D8CC),
    secondary = Color(0xFF8FB3A4),
    onSecondary = Color(0xFF10231B),
    secondaryContainer = Color(0xFF2A4036),
    onSecondaryContainer = Color(0xFFD5E7DE),
    tertiary = Color(0xFFC7A85C),
    onTertiary = Color(0xFF2C2407),
    background = WarmDark,
    onBackground = WarmIvory,
    surface = WarmDarkSurface,
    onSurface = WarmIvory,
    surfaceVariant = Color(0xFF35322D),
    onSurfaceVariant = Color(0xFFB5AFA2),
    outline = Color(0xFF4A463E),
    outlineVariant = Color(0xFF3A3630),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

@Composable
fun TomaTodoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
