package io.github.valerait.ghostlink.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.valerait.ghostlink.core.ui.Avatar
import io.github.valerait.ghostlink.core.ui.presenceText
import io.github.valerait.ghostlink.core.ui.rememberIsTyping
import io.github.valerait.ghostlink.core.ui.resources.Res
import io.github.valerait.ghostlink.core.ui.resources.chat_typing
import io.github.valerait.ghostlink.core.ui.resources.chat_typing_named
import io.github.valerait.ghostlink.core.ui.resources.group_members
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/** Шапка чата: собеседник и его статус, или группа и число участников. Нажатие на группу открывает её карточку. */
@Composable
fun ChatTitle(
    state: ChatUiState,
    now: Instant,
    onGroupInfo: () -> Unit,
) {
    val group = state.group
    val partner = state.partner
    val typing = rememberIsTyping(state.partnerTypingUntil)
    val (title, avatarId) =
        when {
            group != null -> group.name to state.chatId.value
            partner != null -> partner.name to partner.id.value
            else -> return
        }
    val subtitle =
        when {
            typing && state.typingName != null -> stringResource(Res.string.chat_typing_named, state.typingName)
            typing -> stringResource(Res.string.chat_typing)
            group != null -> pluralStringResource(Res.plurals.group_members, state.memberCount, state.memberCount)
            else -> presenceText(partner!!, now)
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
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
