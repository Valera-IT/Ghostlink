package io.github.valerait.ghostlink.core.domain.usecase

import io.github.valerait.ghostlink.core.domain.repository.ChatRepository
import io.github.valerait.ghostlink.core.domain.repository.UserRepository
import io.github.valerait.ghostlink.core.model.Chat
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.ChatPrefs
import io.github.valerait.ghostlink.core.model.LastMessage
import io.github.valerait.ghostlink.core.model.User
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.core.model.expiresAt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Clock
import kotlin.time.Instant

/** Строка списка чатов: всё, что о чате видно пользователю, с учётом его личных настроек. */
data class ChatSummary(
    val chat: Chat,
    /** С кем личный чат; в группе null. */
    val partnerId: UserId?,
    /** Профиль собеседника в личном чате; в группе или пока профиль не загружен — null. */
    val partner: User?,
    val unread: Boolean,
    /** Последнее сообщение; null, если его нет, оно исчезло или удалено вместе с чатом. */
    val lastMessage: LastMessage?,
    val lastMessageIsMine: Boolean,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    /** До какого момента кто-то из собеседников «печатает…»; null — никто. */
    val typingUntil: Instant? = null,
    /** Кто печатает в группе; в личном чате null. */
    val typingName: String? = null,
    /** Автор последнего сообщения в группе, если это не я; в личном чате null. */
    val lastSenderName: String? = null,
) {
    /** Имя собеседника или название группы. */
    val title: String? get() = chat.group?.name ?: partner?.name
}

/**
 * Список чатов [me]: чаты + профили собеседников + личные настройки.
 * Закреплённые идут первыми (последний закреплённый — выше), остальные — по свежести.
 * Превью исчезающего сообщения пропадает в его срок, даже если в базе ничего не поменялось.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveChatListUseCase(
    private val chats: ChatRepository,
    private val users: UserRepository,
    private val clock: Clock,
) {
    operator fun invoke(me: UserId): Flow<List<ChatSummary>> {
        val chatList = chats.observeChats(me)
        // Подписка на собеседников пересоздаётся только при смене состава чатов, а не на каждое сообщение.
        val people =
            chatList
                .map { list ->
                    list
                        .flatMap { it.members }
                        .filter { it != me }
                        .distinct()
                        .sortedBy { it.value }
                }.distinctUntilChanged()
                .flatMapLatest { users.observeUsers(it) }
        return combine(chatList, people, chats.observeChatPrefs(me), ::Triple)
            .transformLatest { (list, people, prefs) ->
                while (true) {
                    val now = clock.now()
                    emit(summarize(me, list, people, prefs, now))
                    val next =
                        list
                            .mapNotNull { chat -> chat.lastMessage?.let { expiresAt(it.sentAt, it.ttl) } }
                            .filter { it > now }
                            .minOrNull() ?: break
                    delay(next - now)
                }
            }
    }

    private fun summarize(
        me: UserId,
        list: List<Chat>,
        people: Map<UserId, User>,
        prefs: Map<ChatId, ChatPrefs>,
        now: Instant,
    ): List<ChatSummary> =
        list
            .mapNotNull { chat ->
                val chatPrefs = prefs[chat.id] ?: ChatPrefs()
                if (!chatPrefs.isVisible(chat)) return@mapNotNull null
                val last = chat.lastMessage?.takeUnless { chatPrefs.isCleared(it.sentAt) || it.isExpired(now) }
                val typing = chat.typingOther(me)
                ChatSummary(
                    chat = chat,
                    partnerId = if (chat.isGroup) null else chat.partnerId(me),
                    partner = if (chat.isGroup) null else people[chat.partnerId(me)],
                    unread = last != null && chat.isUnreadFor(me),
                    lastMessage = last,
                    lastMessageIsMine = last?.senderId == me,
                    pinned = chatPrefs.pinned,
                    muted = chatPrefs.muted,
                    typingUntil = typing?.second,
                    typingName = if (chat.isGroup) typing?.let { people[it.first]?.name } else null,
                    lastSenderName = last?.takeIf { chat.isGroup && it.senderId != me }?.let { people[it.senderId]?.name },
                )
            }.sortedByDescending { prefs[it.chat.id]?.pinnedAt ?: Instant.DISTANT_PAST }
}
