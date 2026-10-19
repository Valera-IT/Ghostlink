package io.github.valerait.ghostlink.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.valerait.ghostlink.data.model.Chat
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
    val lastMessageIsMine: Boolean,
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
        chats: ChatRepository,
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

        val state: StateFlow<ChatListState> =
            combine(chatList, partners) { list, people ->
                ChatListState(
                    loading = false,
                    items =
                        list.map { chat ->
                            ChatListItem(
                                chat = chat,
                                partnerId = chat.partnerId(me),
                                partner = people[chat.partnerId(me)],
                                unread = chat.isUnreadFor(me),
                                lastMessageIsMine = chat.lastMessage?.senderId == me,
                            )
                        },
                    demoMode = config.demoMode,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatListState(demoMode = config.demoMode))
    }
