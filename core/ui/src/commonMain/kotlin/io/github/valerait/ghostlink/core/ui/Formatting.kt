package io.github.valerait.ghostlink.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.valerait.ghostlink.core.model.User
import io.github.valerait.ghostlink.core.ui.resources.Res
import io.github.valerait.ghostlink.core.ui.resources.day_today
import io.github.valerait.ghostlink.core.ui.resources.day_yesterday
import io.github.valerait.ghostlink.core.ui.resources.presence_last_seen_date
import io.github.valerait.ghostlink.core.ui.resources.presence_last_seen_today
import io.github.valerait.ghostlink.core.ui.resources.presence_long_ago
import io.github.valerait.ghostlink.core.ui.resources.presence_online
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// Время и даты — в часовом поясе устройства и на языке интерфейса (см. LocalDay.*.kt).

fun formatTime(time: Instant): String = time.formatLocal("HH:mm")

/** Время в списке чатов: сегодня — часы, на этой неделе — день недели, иначе — дата. */
fun formatChatListTime(
    time: Instant,
    now: Instant,
): String {
    val days = now.localDay().epochDay - time.localDay().epochDay
    return when (days) {
        0L -> formatTime(time)
        in 1..6 -> time.formatLocal("EEE")
        else -> time.formatLocal("dd.MM.yy")
    }
}

@Composable
fun dayLabel(
    time: Instant,
    now: Instant,
): String {
    val date = time.localDay()
    val today = now.localDay()
    return when (date.epochDay) {
        today.epochDay -> stringResource(Res.string.day_today)
        today.epochDay - 1 -> stringResource(Res.string.day_yesterday)
        else -> time.formatLocal(if (date.year == today.year) "d MMMM" else "d MMMM yyyy")
    }
}

@Composable
fun presenceText(
    user: User?,
    now: Instant,
): String =
    when {
        user == null -> ""
        user.isOnline(now) -> stringResource(Res.string.presence_online)
        else -> lastSeenText(user.lastSeen, now)
    }

@Composable
private fun lastSeenText(
    lastSeen: Instant?,
    now: Instant,
): String =
    when {
        lastSeen == null -> {
            stringResource(Res.string.presence_long_ago)
        }

        lastSeen.localDay() == now.localDay() -> {
            stringResource(Res.string.presence_last_seen_today, formatTime(lastSeen))
        }

        else -> {
            stringResource(Res.string.presence_last_seen_date, formatChatListTime(lastSeen, now))
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
