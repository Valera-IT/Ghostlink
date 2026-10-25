package io.github.valerait.ghostlink.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.valerait.ghostlink.app.AppGraph
import io.github.valerait.ghostlink.app.strings
import io.github.valerait.ghostlink.model.User
import io.github.valerait.ghostlink.platform.decodeImage
import io.github.valerait.ghostlink.platform.formatDate
import io.github.valerait.ghostlink.platform.formatShortDate
import io.github.valerait.ghostlink.platform.formatTime
import io.github.valerait.ghostlink.platform.formatWeekday
import io.github.valerait.ghostlink.platform.localEpochDay
import io.github.valerait.ghostlink.platform.logWarning
import io.github.valerait.ghostlink.platform.nowMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

private val AvatarColors = listOf(
    Color(0xFF3FB9A0), Color(0xFF8C6CF0), Color(0xFFE0708F), Color(0xFF4F8EF7),
    Color(0xFFE59A4B), Color(0xFF5DB85F), Color(0xFFB46CD9), Color(0xFF3AA6C9),
)

/**
 * Фото профиля, а пока его нет или оно грузится — инициалы; цвет стабилен для пользователя и зависит от его id.
 * [photoVersion] — [User.avatarVersion]; null — фото нет (и у групп).
 */
@Composable
fun Avatar(name: String, id: String, modifier: Modifier = Modifier, size: Dp = 48.dp, photoVersion: Long? = null) {
    // Пока грузится новая версия, видна прежняя: produceState не сбрасывает значение при смене ключей.
    val photo by produceState(photoVersion?.let { AvatarPhotos.cached(id, it) }, id, photoVersion) {
        value = photoVersion?.let { AvatarPhotos.photo(id, it) }
    }
    photo?.let {
        Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.size(size).clip(CircleShape))
        return
    }
    val color = AvatarColors[id.hashCode().absoluteValue % AvatarColors.size]
    Box(modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(
            text = initials(name),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.38f).sp,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/**
 * Фото профилей: готовые картинки в памяти до конца процесса, JPEG на диске кэширует сам Firestore.
 * Вызывается из композиции, то есть на главном потоке, поэтому кэш без блокировок.
 */
private object AvatarPhotos {
    private const val MAX_IMAGES = 200
    private val images = LinkedHashMap<String, ImageBitmap>()

    fun cached(id: String, version: Long): ImageBitmap? = images["$id@$version"]

    suspend fun photo(id: String, version: Long): ImageBitmap? {
        cached(id, version)?.let { return it }
        val image = try {
            AppGraph.users.getAvatar(id, version)?.let { withContext(Dispatchers.Default) { decodeImage(it) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logWarning("Avatars", "Can't load avatar of $id", e)
            null
        } ?: return null
        images["$id@$version"] = image
        while (images.size > MAX_IMAGES) images.remove(images.keys.first())
        return image
    }
}

fun initials(name: String): String =
    name.split(' ', '-', '_')
        .mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
        .take(2)
        .joinToString("") { it.uppercase() }
        .ifEmpty { "?" }

// Тот же контур, что ic_ghost.xml на Android.
private val GhostIcon: ImageVector = ImageVector.Builder("ghost", 24.dp, 24.dp, 24f, 24f).addPath(
    pathData = PathParser().parsePathString(
        "M4,11 A8,8 0 0,1 20,11 L20,21 Q18.67,22.8 17.33,21 Q16,19.2 14.67,21 Q13.33,22.8 12,21 " +
            "Q10.67,19.2 9.33,21 Q8,22.8 6.67,21 Q5.33,19.2 4,21 Z M8,11.5 a1.5,1.75 0 1,0 3,0 a1.5,1.75 0 1,0 -3,0 Z " +
            "M13,11.5 a1.5,1.75 0 1,0 3,0 a1.5,1.75 0 1,0 -3,0 Z",
    ).toNodes(),
    pathFillType = PathFillType.EvenOdd,
    fill = SolidColor(Color.Black),
).build()

@Composable
fun GhostLogo(modifier: Modifier = Modifier, size: Dp = 72.dp, tint: Color = MaterialTheme.colorScheme.primary) {
    Icon(GhostIcon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back) }
}

/** Время в списке чатов: сегодня — часы, на этой неделе — день недели, иначе — дата. */
fun formatChatListTime(millis: Long, now: Long): String {
    val days = localEpochDay(now) - localEpochDay(millis)
    return when {
        days == 0L -> formatTime(millis)
        days in 1..6 -> formatWeekday(millis)
        else -> formatShortDate(millis)
    }
}

fun dayLabel(millis: Long, now: Long): String {
    val days = localEpochDay(now) - localEpochDay(millis)
    return when (days) {
        0L -> strings.today
        1L -> strings.yesterday
        else -> formatDate(millis, withYear = days > 300)
    }
}

fun presenceText(user: User?, now: Long): String {
    val lastSeen = user?.lastSeen
    return when {
        user == null -> ""
        user.isOnline(now) -> strings.online
        lastSeen == null -> strings.lastSeenLongAgo
        localEpochDay(lastSeen) == localEpochDay(now) -> strings.lastSeenAt(formatTime(lastSeen))
        else -> strings.lastSeenOn(formatChatListTime(lastSeen, now))
    }
}

/** Текущее время, которое раз в 30 секунд обновляется — для «в сети» и меток времени. */
@Composable
fun rememberNow(): State<Long> = produceState(nowMillis()) {
    while (true) {
        delay(30_000)
        value = nowMillis()
    }
}
