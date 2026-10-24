package io.github.valerait.ghostlink.core.designsystem

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Неоновая «слэб»-тема: тёмный фон с оттенком акцента, свои сообщения — плашки цвета акцента
 * со срезанными углами, чужие — тёмные с неоновой рамкой.
 */
@Immutable
data class NeonPalette(
    val accent: Color,
    val secondary: Color,
    val background: Color,
    /** Цвет своих сообщений; по умолчанию акцент. */
    val outgoing: Color = accent,
    val onOutgoing: Color = Color(0xFF0B0A10),
    val text: Color = Color(0xFFF1F0F7),
)

val ThemeId.palette: NeonPalette
    get() =
        when (this) {
            ThemeId.TOXIC_YELLOW -> {
                NeonPalette(
                    accent = Color(0xFFFFE600),
                    secondary = Color(0xFFFF2E4C),
                    background = Color(0xFF0E0C12),
                )
            }

            ThemeId.NEON_SUNSET -> {
                NeonPalette(
                    accent = Color(0xFFFF5CA8),
                    secondary = Color(0xFFFFB45E),
                    background = Color(0xFF120911),
                )
            }

            ThemeId.ICEWIRE -> {
                NeonPalette(
                    accent = Color(0xFF8ED8F8),
                    secondary = Color(0xFFC690FF),
                    background = Color(0xFF080C14),
                )
            }

            ThemeId.ACID_CIRCUIT -> {
                NeonPalette(
                    accent = Color(0xFFB8FF3C),
                    secondary = Color(0xFFFF5CD6),
                    background = Color(0xFF0A0E09),
                )
            }

            ThemeId.AMBER_ALERT -> {
                NeonPalette(
                    accent = Color(0xFFFFB443),
                    secondary = Color(0xFFFF5A3C),
                    background = Color(0xFF110C07),
                )
            }

            ThemeId.CUSTOM_SPECTRUM -> {
                NeonPalette(
                    accent = Color(0xFF3DFF86),
                    secondary = Color(0xFFE245FF),
                    background = Color(0xFF0D0717),
                    outgoing = Color(0xFF4B2290),
                    onOutgoing = Color(0xFFF4EEFF),
                )
            }
        }

/** Как выглядят сообщения в чате. Отдельно от ColorScheme: у плашек свои цвета и формы. */
@Immutable
data class ChatStyle(
    val outgoing: Color,
    val onOutgoing: Color,
    val incoming: Color,
    val onIncoming: Color,
    /** Рамка чужих сообщений. */
    val incomingBorder: Color,
    /** Галочки «прочитано» на своём сообщении. */
    val readTick: Color,
    val outgoingShape: Shape,
    val incomingShape: Shape,
    val chipShape: Shape,
    /** Карточки и панели (например, превью темы). */
    val panelShape: Shape,
)

val LocalChatStyle = staticCompositionLocalOf<ChatStyle> { error("ChatStyle is not provided") }
val LocalThemeId = staticCompositionLocalOf { ThemeId.DEFAULT }

/** Логотип-надпись GHOSTLINK. */
val LogoTextStyle =
    TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 6.sp,
    )

@Composable
fun GhostlinkTheme(
    theme: ThemeId = ThemeId.DEFAULT,
    content: @Composable () -> Unit,
) {
    val palette = theme.palette
    CompositionLocalProvider(LocalChatStyle provides palette.chatStyle(), LocalThemeId provides theme) {
        MaterialTheme(
            colorScheme = palette.colorScheme(),
            typography = NeonTypography,
            content = content,
        )
    }
}

private fun NeonPalette.tint(fraction: Float): Color = accent.copy(alpha = fraction).compositeOver(background)

private fun NeonPalette.chatStyle() =
    ChatStyle(
        outgoing = outgoing,
        onOutgoing = onOutgoing,
        incoming = tint(0.12f),
        onIncoming = text,
        incomingBorder = accent.copy(alpha = 0.55f),
        readTick = if (outgoing == accent) onOutgoing else accent,
        outgoingShape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
        incomingShape = CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp),
        chipShape = CutCornerShape(6.dp),
        panelShape = CutCornerShape(10.dp),
    )

private fun NeonPalette.colorScheme(): ColorScheme {
    val muted = text.copy(alpha = 0.68f).compositeOver(background)
    return darkColorScheme(
        primary = accent,
        onPrimary = onOutgoing.takeIf { outgoing == accent } ?: Color(0xFF0B0A10),
        primaryContainer = tint(0.22f),
        onPrimaryContainer = text,
        inversePrimary = accent,
        secondary = secondary,
        onSecondary = Color(0xFF0B0A10),
        secondaryContainer = secondary.copy(alpha = 0.22f).compositeOver(background),
        onSecondaryContainer = text,
        tertiary = secondary,
        onTertiary = Color(0xFF0B0A10),
        background = background,
        onBackground = text,
        surface = background,
        onSurface = text,
        surfaceVariant = tint(0.12f),
        onSurfaceVariant = muted,
        surfaceTint = accent,
        surfaceBright = tint(0.18f),
        surfaceDim = background,
        surfaceContainerLowest = Color.Black.copy(alpha = 0.4f).compositeOver(background),
        surfaceContainerLow = tint(0.04f),
        surfaceContainer = tint(0.07f),
        surfaceContainerHigh = tint(0.11f),
        surfaceContainerHighest = tint(0.15f),
        outline = tint(0.5f),
        outlineVariant = tint(0.22f),
        error = Color(0xFFFF6B7D),
        onError = Color(0xFF0B0A10),
    )
}

// Заголовки и подписи — моноширинным жирным, как надписи на плашках в макете.
private val NeonTypography =
    Typography().run {
        fun TextStyle.mono() = copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        copy(
            headlineSmall = headlineSmall.mono(),
            titleLarge = titleLarge.mono(),
            titleMedium = titleMedium.mono(),
            titleSmall = titleSmall.mono(),
            labelLarge = labelLarge.mono(),
        )
    }
