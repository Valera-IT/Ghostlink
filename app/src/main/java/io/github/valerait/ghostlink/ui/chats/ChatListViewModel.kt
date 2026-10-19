package io.github.valerait.ghostlink.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.valerait.ghostlink.data.model.Chat
import io.github.valerait.ghostlink.data.model.ChatPrefs
import io.github.valerait.ghostlink.data.model.LastMessage
import io.github.valerait.ghostlink.data.model.User
import io.github.valerait.ghostlink.data.repository.AuthRepository
import io.github.valerait.ghostlink.data.repository.ChatRepository
import io.github.valerait.ghostlink.data.repository.UserRepository
import io.github.valerait.ghostlink.data.repository.requireUserId
import io.github.valerait.ghostlink.di.AppConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ChatListItem(
    val chat: Chat,
    val partnerId: String,
    val partner: User?,
    val unread: Boolean,
    /** Последнее сообщение; null, если его нет или оно удалено вместе с чатом. */
    val lastMessage: LastMessage?,
    val lastMessageIsMine: Boolean,
    val pinned: Boolean = false,
    val muted: Boolean = false,
)

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
        config: AppConfig,
    ) : ViewModel() {
        private val me = auth.requireUserId()

        private val chatList =
            chats
                .observeChats(me)
                .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

        // Подписка на собеседников пересоздаётся только при смене состава чатов, а не на каждое сообщение.
        private val partners =
            chatList
                .map { list -> list.map { it.partnerId(me) }.distinct().sorted() }
                .distinctUntilChanged()
                .flatMapLatest { users.observeUsers(it) }

        private val prefs = chats.observeChatPrefs(me)

        val state: StateFlow<ChatListState> =
            combine(chatList, partners, prefs) { list, people, prefs ->
                ChatListState(
                    loading = false,
                    items =
                        list
                            .mapNotNull { chat ->
                                val chatPrefs = prefs[chat.id] ?: ChatPrefs()
                                if (!chatPrefs.isVisible(chat)) return@mapNotNull null
                                val last = chat.lastMessage?.takeUnless { chatPrefs.isCleared(it.sentAt) }
                                ChatListItem(
                                    chat = chat,
                                    partnerId = chat.partnerId(me),
                                    partner = people[chat.partnerId(me)],
                                    unread = last != null && chat.isUnreadFor(me),
                                    lastMessage = last,
                                    lastMessageIsMine = last?.senderId == me,
                                    pinned = chatPrefs.pinned,
                                    muted = chatPrefs.muted,
                                )
                            }
                            // Закреплённые сверху (последний закреплённый первым), остальные — по свежести, как пришли.
                            .sortedByDescending { prefs[it.chat.id]?.pinnedAt ?: Long.MIN_VALUE },
                    demoMode = config.demoMode,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatListState(demoMode = config.demoMode))

        fun setPinned(
            chatId: String,
            pinned: Boolean,
        ) = chats.setPinned(me, chatId, pinned)

        fun setMuted(
            chatId: String,
            muted: Boolean,
        ) = chats.setMuted(me, chatId, muted)

        fun remove(chatId: String) = chats.removeChat(me, chatId)
    }
