package io.github.valerait.ghostlink.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.valerait.ghostlink.model.DisappearingTimer
import io.github.valerait.ghostlink.model.TYPING_REFRESH_MS
import io.github.valerait.ghostlink.ui.common.Avatar
import io.github.valerait.ghostlink.ui.common.presenceText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Ставит отметку «печатает…», пока идёт набор, — не чаще раза в [TYPING_REFRESH_MS]. */
class TypingNotifier(private val scope: CoroutineScope, private val setTyping: (Boolean) -> Unit) {
    private var refresh: Job? = null

    fun onDraftChanged(text: String) {
        if (text.isBlank()) {
            stop()
            return
        }
        if (refresh?.isActive == true) return
        setTyping(true)
        refresh = scope.launch { delay(TYPING_REFRESH_MS) }
    }

    /** Снимает отметку, если она стоит. */
    fun stop() {
        if (refresh == null) return
        onSent()
        setTyping(false)
    }

    /** Сообщение отправлено: отправка сама снимает отметку на сервере. */
    fun onSent() {
        refresh?.cancel()
        refresh = null
    }
}

/** «3 участника», «5 участников». */
fun membersLabel(count: Int): String {
    val word = when {
        count % 100 in 11..14 -> "участников"
        count % 10 == 1 -> "участник"
        count % 10 in 2..4 -> "участника"
        else -> "участников"
    }
    return "$count $word"
}

/** Шапка чата: собеседник и его статус, или группа и число участников. Нажатие на группу открывает её карточку. */
@Composable
fun ChatTitle(state: ChatUiState, now: Long, onGroupInfo: () -> Unit) {
    val group = state.group
    val partner = state.partner
    val typing = rememberIsTyping(state.typingUntil)
    val (title, avatarId) = when {
        group != null -> group.name to state.chatId
        partner != null -> partner.name to partner.id
        else -> return
    }
    val subtitle = when {
        typing && state.typingName != null -> "${state.typingName} печатает…"
        typing -> "печатает…"
        group != null -> membersLabel(state.memberCount)
        else -> presenceText(partner, now)
    }
    val highlighted = typing || (group == null && partner?.isOnline(now) == true)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (group != null) Modifier.clickable(onClick = onGroupInfo) else Modifier,
    ) {
        Avatar(title, avatarId, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

fun disappearingLabel(timer: DisappearingTimer?): String = when (timer) {
    null -> "Выключены"
    DisappearingTimer.HOUR -> "Через 1 час"
    DisappearingTimer.DAY -> "Через 1 день"
    DisappearingTimer.WEEK -> "Через 1 неделю"
}

/** Кнопка таймера исчезающих сообщений в шапке чата; подсвечена, пока таймер включён. */
@Composable
fun DisappearingMenu(current: DisappearingTimer?, onSelect: (DisappearingTimer?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Outlined.Timer,
                contentDescription = "Исчезающие сообщения",
                tint = if (current != null) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Исчезающие сообщения", style = MaterialTheme.typography.labelLarge) }, onClick = {}, enabled = false)
            (listOf(null) + DisappearingTimer.entries).forEach { timer ->
                DropdownMenuItem(
                    text = { Text(disappearingLabel(timer)) },
                    trailingIcon = { if (timer == current) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = {
                        open = false
                        if (timer != current) onSelect(timer)
                    },
                )
            }
        }
    }
}
