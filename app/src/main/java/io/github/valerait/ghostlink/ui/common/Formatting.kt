package io.github.valerait.ghostlink.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import io.github.valerait.ghostlink.R
import io.github.valerait.ghostlink.core.model.User
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.toJavaInstant

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

private fun Instant.toLocalDate(zone: ZoneId): LocalDate = toJavaInstant().atZone(zone).toLocalDate()

fun formatTime(
    time: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): String = time.toJavaInstant().atZone(zone).format(TimeFormat)

/** Время в списке чатов: сегодня — часы, на этой неделе — день недели, иначе — дата. */
fun formatChatListTime(
    time: Instant,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val date = time.toLocalDate(zone)
    val days = ChronoUnit.DAYS.between(date, now.toLocalDate(zone))
    return when {
        days == 0L -> formatTime(time, zone)
        days in 1..6 -> date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
        else -> date.format(DateTimeFormatter.ofPattern("dd.MM.yy"))
    }
}

@Composable
fun dayLabel(
    time: Instant,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val locale = LocalLocale.current.platformLocale
    val date = time.toLocalDate(zone)
    val today = now.toLocalDate(zone)
    return when (date) {
        today -> {
            stringResource(R.string.day_today)
        }

        today.minusDays(1) -> {
            stringResource(R.string.day_yesterday)
        }

        else -> {
            val pattern = if (date.year == today.year) "d MMMM" else "d MMMM yyyy"
            date.format(DateTimeFormatter.ofPattern(pattern, locale))
        }
    }
}

@Composable
fun presenceText(
    user: User?,
    now: Instant,
): String =
    when {
        user == null -> {
            ""
        }

        user.isOnline(now) -> {
            stringResource(R.string.presence_online)
        }

        else -> {
            lastSeenText(user.lastSeen, now)
        }
    }

@Composable
private fun lastSeenText(
    lastSeen: Instant?,
    now: Instant,
): String =
    when {
        lastSeen == null -> {
            stringResource(R.string.presence_long_ago)
        }

        lastSeen.toLocalDate(ZoneId.systemDefault()) == now.toLocalDate(ZoneId.systemDefault()) -> {
            stringResource(R.string.presence_last_seen_today, formatTime(lastSeen))
        }

        else -> {
            stringResource(R.string.presence_last_seen_date, formatChatListTime(lastSeen, now))
        }
    }

/** Текущее время, которое раз в 30 секунд обновляется — для «в сети» и меток времени. */
@Composable
fun rememberNow(): State<Instant> =
    produceState(Clock.System.now()) {
        while (true) {
            delay(30.seconds)
            value = Clock.System.now()
        }
    }

/** Отметка «печатает…» видна, пока не наступил [until]; гаснет сама, даже если новых данных не пришло. */
@Composable
fun rememberIsTyping(until: Instant?): Boolean {
    var typing by remember(until) { mutableStateOf(until != null && Clock.System.now() < until) }
    LaunchedEffect(until) {
        if (until != null) {
            delay(until - Clock.System.now())
            typing = false
        }
    }
    return typing
}
