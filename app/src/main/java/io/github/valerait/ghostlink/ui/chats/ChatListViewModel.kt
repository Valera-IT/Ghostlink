package io.github.valerait.ghostlink.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.valerait.ghostlink.data.model.Chat
import io.github.valerait.ghostlink.data.model.ChatPrefs
import io.github.valerait.ghostlink.data.model.Clock
import io.github.valerait.ghostlink.data.model.LastMessage
import io.github.valerait.ghostlink.data.model.User
import io.github.valerait.ghostlink.data.model.expiresAt
import io.github.valerait.ghostlink.data.repository.AuthRepository
import io.github.valerait.ghostlink.data.repository.ChatRepository
import io.github.valerait.ghostlink.data.repository.UserRepository
import io.github.valerait.ghostlink.data.repository.requireUserId
import io.github.valerait.ghostlink.di.AppConfig
import io.github.valerait.ghostlink.ui.group.leaveGroup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject

data class ChatListItem(
    val chat: Chat,
    /** Собеседник в личном чате; в группе — id чата (для цвета аватара). */
    val partnerId: String,
    /** Собеседник в личном чате; в группе null. */
    val partner: User?,
    val unread: Boolean,
    /** Последнее сообщение; null, если его нет или оно удалено вместе с чатом. */
    val lastMessage: LastMessage?,
    val lastMessageIsMine: Boolean,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    /** До какого момента кто-то из собеседников «печатает…»; null — никто. */
    val typingUntil: Long? = null,
    /** Кто печатает в группе; в личном чате null. */
    val typingName: String? = null,
    /** Автор последнего сообщения в группе, если это не я; в личном чате null. */
    val lastSenderName: String? = null,
) {
    /** Имя собеседника или название группы. */
    val title: String? get() = chat.group?.name ?: partner?.name
}

data class ChatListState(
    val loading: Boolean = true,
    val items: List<ChatListItem> = emptyList(),
    val demoMode: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatListViewModel
    @Inject
    constructor(
        auth: AuthRepository,
        users: UserRepository,
        private val chats: ChatRepository,
        private val config: AppConfig,
        private val clock: Clock,
    ) : ViewModel() {
        private val me = auth.requireUserId()

        private val chatList =
            chats
                .observeChats(me)
                .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

        // Подписка на собеседников пересоздаётся только при смене состава чатов, а не на каждое сообщение.
        private val partners =
            chatList
                .map { list ->
                    list
                        .flatMap { it.members }
                        .filter { it != me }
                        .distinct()
                        .sorted()
                }.distinctUntilChanged()
                .flatMapLatest { users.observeUsers(it) }

        private val prefs = chats.observeChatPrefs(me)

        // Превью исчезающего сообщения пропадает в его срок, даже если в базе ничего не поменялось.
        val state: StateFlow<ChatListState> =
            combine(chatList, partners, prefs, ::Triple)
                .transformLatest { (list, people, prefs) ->
                    while (true) {
                        val now = clock.now()
                        emit(buildState(list, people, prefs, now))
                        val next =
                            list
                                .mapNotNull { chat -> chat.lastMessage?.let { expiresAt(it.sentAt, it.ttl) } }
                                .filter { it > now }
                                .minOrNull() ?: break
                        delay(next - now)
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatListState(demoMode = config.demoMode))

        private fun buildState(
            list: List<Chat>,
            people: Map<String, User>,
            prefs: Map<String, ChatPrefs>,
            now: Long,
        ) = ChatListState(
            loading = false,
            items =
                list
                    .mapNotNull { chat ->
                        val chatPrefs = prefs[chat.id] ?: ChatPrefs()
                        if (!chatPrefs.isVisible(chat)) return@mapNotNull null
                        val last = chat.lastMessage?.takeUnless { chatPrefs.isCleared(it.sentAt) || it.isExpired(now) }
                        val typing = chat.typingOther(me)
                        ChatListItem(
                            chat = chat,
                            partnerId = if (chat.isGroup) chat.id else chat.partnerId(me),
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
                    }
                    // Закреплённые сверху (последний закреплённый первым), остальные — по свежести, как пришли.
                    .sortedByDescending { prefs[it.chat.id]?.pinnedAt ?: Long.MIN_VALUE },
            demoMode = config.demoMode,
        )

        fun setPinned(
            chatId: String,
            pinned: Boolean,
        ) = chats.setPinned(me, chatId, pinned)

        fun setMuted(
            chatId: String,
            muted: Boolean,
        ) = chats.setMuted(me, chatId, muted)

        /** Личный чат прячется только у меня; из группы я выхожу (админ передаёт права следующему участнику). */
        fun remove(chatId: String) {
            val chat =
                state.value.items
                    .firstOrNull { it.chat.id == chatId }
                    ?.chat
            if (chat?.isGroup == true) leaveGroup(chats, chat, me)
            chats.removeChat(me, chatId)
        }
    }
