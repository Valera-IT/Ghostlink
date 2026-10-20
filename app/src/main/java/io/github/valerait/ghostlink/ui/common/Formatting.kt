package io.github.valerait.ghostlink.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.valerait.ghostlink.R
import io.github.valerait.ghostlink.data.model.User
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

private fun Long.toLocalDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

fun formatTime(
    millis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String = Instant.ofEpochMilli(millis).atZone(zone).format(TimeFormat)

/** Время в списке чатов: сегодня — часы, на этой неделе — день недели, иначе — дата. */
fun formatChatListTime(
    millis: Long,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val date = millis.toLocalDate(zone)
    val days = ChronoUnit.DAYS.between(date, now.toLocalDate(zone))
    return when {
        days == 0L -> formatTime(millis, zone)
        days in 1..6 -> date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
        else -> date.format(DateTimeFormatter.ofPattern("dd.MM.yy"))
    }
}

@Composable
fun dayLabel(
    millis: Long,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val date = millis.toLocalDate(zone)
    val today = now.toLocalDate(zone)
    return when (date) {
        today -> stringResource(R.string.day_today)
        today.minusDays(1) -> stringResource(R.string.day_yesterday)
        else -> {
            val pattern = if (date.year == today.year) "d MMMM" else "d MMMM yyyy"
            date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
        }
    }
}

@Composable
fun presenceText(
    user: User?,
    now: Long,
): String =
    when {
        user == null -> ""
        user.isOnline(now) -> stringResource(R.string.presence_online)
        user.lastSeen == null -> stringResource(R.string.presence_long_ago)
        user.lastSeen.toLocalDate(ZoneId.systemDefault()) == now.toLocalDate(ZoneId.systemDefault()) ->
            stringResource(R.string.presence_last_seen_today, formatTime(user.lastSeen))
        else -> stringResource(R.string.presence_last_seen_date, formatChatListTime(user.lastSeen, now))
    }

/** Текущее время, которое раз в 30 секунд обновляется — для «в сети» и меток времени. */
@Composable
fun rememberNow(): State<Long> =
    produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }

/** Отметка «печатает…» видна, пока не наступил [until]; гаснет сама, даже если новых данных не пришло. */
@Composable
fun rememberIsTyping(until: Long?): Boolean {
    var typing by remember(until) { mutableStateOf(until != null && System.currentTimeMillis() < until) }
    LaunchedEffect(until) {
        if (until != null) {
            delay(until - System.currentTimeMillis())
            typing = false
        }
    }
    return typing
}
