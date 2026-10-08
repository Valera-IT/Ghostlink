package io.github.valerait.ghostlink.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// «Призрачная» палитра: почти чёрный фон и спектральная мята. Динамические цвета
// не используем — у приложения свой фирменный стиль.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CF5D3),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF173F37),
    onPrimaryContainer = Color(0xFFC9FFF0),
    secondary = Color(0xFFB9A4FF),
    onSecondary = Color(0xFF2A1770),
    secondaryContainer = Color(0xFF2E2552),
    onSecondaryContainer = Color(0xFFE6DEFF),
    background = Color(0xFF0E0F14),
    onBackground = Color(0xFFE4E6F0),
    surface = Color(0xFF0E0F14),
    onSurface = Color(0xFFE4E6F0),
    surfaceVariant = Color(0xFF1E2030),
    onSurfaceVariant = Color(0xFFA7ABC3),
    surfaceContainerLowest = Color(0xFF0A0B0F),
    surfaceContainerLow = Color(0xFF13141B),
    surfaceContainer = Color(0xFF171923),
    surfaceContainerHigh = Color(0xFF1E2030),
    surfaceContainerHighest = Color(0xFF262838),
    outline = Color(0xFF4A4E66),
    outlineVariant = Color(0xFF2A2C3C),
    error = Color(0xFFFF8A9A),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00806A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC6F3E7),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF6B4FD8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6DEFF),
    onSecondaryContainer = Color(0xFF21005E),
    background = Color(0xFFF7F8FB),
    onBackground = Color(0xFF181A22),
    surface = Color(0xFFF7F8FB),
    onSurface = Color(0xFF181A22),
    surfaceVariant = Color(0xFFE5E7F0),
    onSurfaceVariant = Color(0xFF555970),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F2F7),
    surfaceContainer = Color(0xFFEBEDF3),
    surfaceContainerHigh = Color(0xFFE5E7EE),
    surfaceContainerHighest = Color(0xFFDFE1E9),
    outline = Color(0xFF8A8EA5),
    outlineVariant = Color(0xFFCDD0DC),
)

/** Логотип-надпись GHOSTLINK. */
val LogoTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    letterSpacing = 6.sp,
)

@Composable
fun GhostlinkTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
