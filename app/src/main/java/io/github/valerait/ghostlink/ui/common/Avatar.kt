package io.github.valerait.ghostlink.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.absoluteValue

private val AvatarColors = listOf(
    Color(0xFF3FB9A0), Color(0xFF8C6CF0), Color(0xFFE0708F), Color(0xFF4F8EF7),
    Color(0xFFE59A4B), Color(0xFF5DB85F), Color(0xFFB46CD9), Color(0xFF3AA6C9),
)

/** Аватар из инициалов; цвет стабилен для пользователя и зависит от его id. */
@Composable
fun Avatar(name: String, id: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val color = AvatarColors[id.hashCode().absoluteValue % AvatarColors.size]
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials(name),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.38f).sp,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

internal fun initials(name: String): String =
    name.split(' ', '-', '_')
        .mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
        .take(2)
        .joinToString("") { it.uppercase() }
        .ifEmpty { "?" }
