package io.github.valerait.ghostlink.core.domain.usecase

import io.github.valerait.ghostlink.core.domain.repository.AuthRepository
import io.github.valerait.ghostlink.core.domain.repository.ChatRepository
import io.github.valerait.ghostlink.core.domain.repository.requireUserId
import io.github.valerait.ghostlink.core.model.Chat
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.DisappearingTimer
import io.github.valerait.ghostlink.core.model.Message
import io.github.valerait.ghostlink.core.model.MessageId
import io.github.valerait.ghostlink.core.model.ReplyTo
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.core.model.normalizeMessageText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Clock

/** Друг из QR или ссылки: личный чат с ним создаётся (или возвращается в список) сразу. */
class AddFriendUseCase(
    private val auth: AuthRepository,
    private val chats: ChatRepository,
) {
    suspend operator fun invoke(friendId: UserId): ChatId {
        val me = auth.requireUserId()
        require(friendId != me) { "Cannot add yourself" }
        return chats.startChat(me, friendId)
    }
}

/**
 * Сообщения чата, которые видит [me]: без удалённых вместе с чатом (ChatPrefs.clearedAt) и без исчезнувших.
 * Исчезающие пропадают ровно в свой срок, даже если из базы ничего нового не пришло. На Spark нет TTL,
 * поэтому исчезнувшие стирает из базы любой участник, увидевший их первым.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveChatMessagesUseCase(
    private val chats: ChatRepository,
    private val clock: Clock,
) {
    operator fun invoke(
        chatId: ChatId,
        me: UserId,
    ): Flow<List<Message>> {
        // Исчезнувшие сообщения, которые этот поток уже попросил стереть.
        val requested = HashSet<MessageId>()
        return combine(
            chats.observeMessages(chatId),
            chats.observeChatPrefs(me).map { it[chatId] }.distinctUntilChanged(),
        ) { messages, prefs ->
            if (prefs?.clearedAt == null) messages else messages.filterNot { prefs.isCleared(it.sentAt) }
        }.transformLatest { list ->
            while (true) {
                val now = clock.now()
                val (expired, alive) = list.partition { it.isExpired(now) }
                val erase = expired.filter { !it.pending && requested.add(it.id) }.map { it.id }
                if (erase.isNotEmpty()) chats.deleteExpired(chatId, erase)
                emit(alive)
                val next = alive.mapNotNull { it.expiresAt }.minOrNull() ?: break
                delay(next - now)
            }
        }
    }
}

/** Новое сообщение. Таймер чата в момент отправки становится сроком жизни сообщения. */
class SendMessageUseCase(
    private val chats: ChatRepository,
) {
    /** false — текст пустой, ничего не отправлено. */
    operator fun invoke(
        chatId: ChatId,
        sender: UserId,
        text: String,
        replyTo: Message? = null,
        timer: DisappearingTimer? = null,
    ): Boolean {
        val normalized = normalizeMessageText(text) ?: return false
        // Отвечать можно только на сообщение, уже записанное на сервер: у pending ещё нет стабильного места в чате.
        val reply = replyTo?.takeUnless { it.pending }?.let(ReplyTo::of)
        chats.sendMessage(chatId, sender, normalized, reply, timer?.duration)
        return true
    }
}

/** Исправление своего сообщения; если оно последнее, правится и превью в списке чатов. */
class EditMessageUseCase(
    private val chats: ChatRepository,
) {
    /** [visible] — сообщения чата в том виде, в каком их видит пользователь, по порядку. */
    operator fun invoke(
        chatId: ChatId,
        visible: List<Message>,
        message: Message,
        text: String,
    ) {
        val normalized = normalizeMessageText(text) ?: return
        if (normalized == message.text || message.deleted) return
        chats.editMessage(chatId, message.id, normalized, isLatest = visible.isLatestVisible(visible.indexOfFirst { it.id == message.id }))
    }
}

/** Удаление своего сообщения у всех; превью в списке чатов переходит на предыдущее неудалённое. */
class DeleteMessageUseCase(
    private val chats: ChatRepository,
) {
    /** false — сообщение не найдено, чужое или уже удалено. */
    operator fun invoke(
        chatId: ChatId,
        me: UserId,
        visible: List<Message>,
        messageId: MessageId,
    ): Boolean {
        val index = visible.indexOfFirst { it.id == messageId }
        if (index < 0 || visible[index].senderId != me || visible[index].deleted) return false
        chats.deleteMessage(
            chatId,
            messageId,
            isLatest = visible.isLatestVisible(index),
            previous = visible.subList(0, index).lastOrNull { !it.deleted },
        )
        return true
    }
}

/** После сообщения с номером [index] остались только удалённые — значит, оно сейчас в превью чата. */
private fun List<Message>.isLatestVisible(index: Int): Boolean = index >= 0 && subList(index + 1, size).all { it.deleted }

/**
 * Выход из группы с удалением её из своего списка. Админ передаёт права следующему участнику;
 * последний участник просто прячет чат у себя.
 */
class LeaveGroupUseCase(
    private val chats: ChatRepository,
) {
    operator fun invoke(
        chat: Chat,
        me: UserId,
    ) {
        require(chat.isGroup) { "Not a group" }
        val others = chat.members.filter { it != me }
        if (others.isNotEmpty()) {
            val newOwner = if (chat.group?.ownerId == me) others.first() else null
            chats.removeMember(chat.id, me, newOwner)
        }
        chats.removeChat(me, chat.id)
    }
}
