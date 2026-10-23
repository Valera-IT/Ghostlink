package io.github.valerait.ghostlink.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.valerait.ghostlink.core.domain.repository.AuthRepository
import io.github.valerait.ghostlink.core.domain.repository.ChatRepository
import io.github.valerait.ghostlink.core.domain.repository.requireUserId
import io.github.valerait.ghostlink.core.domain.usecase.ChatSummary
import io.github.valerait.ghostlink.core.domain.usecase.LeaveGroupUseCase
import io.github.valerait.ghostlink.core.domain.usecase.ObserveChatListUseCase
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.di.AppConfig
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ChatListState(
    val loading: Boolean = true,
    val items: List<ChatSummary> = emptyList(),
    val demoMode: Boolean = false,
)

@HiltViewModel
class ChatListViewModel
    @Inject
    constructor(
        auth: AuthRepository,
        observeChatList: ObserveChatListUseCase,
        private val chats: ChatRepository,
        private val leaveGroup: LeaveGroupUseCase,
        private val config: AppConfig,
    ) : ViewModel() {
        private val me = auth.requireUserId()

        val state: StateFlow<ChatListState> =
            observeChatList(me)
                .map { ChatListState(loading = false, items = it, demoMode = config.demoMode) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatListState(demoMode = config.demoMode))

        fun setPinned(
            chatId: ChatId,
            pinned: Boolean,
        ) = chats.setPinned(me, chatId, pinned)

        fun setMuted(
            chatId: ChatId,
            muted: Boolean,
        ) = chats.setMuted(me, chatId, muted)

        /** Личный чат прячется только у меня; из группы я выхожу (админ передаёт права следующему участнику). */
        fun remove(chatId: ChatId) {
            val chat =
                state.value.items
                    .firstOrNull { it.chat.id == chatId }
                    ?.chat
            if (chat?.isGroup == true) leaveGroup(chat, me) else chats.removeChat(me, chatId)
        }
    }
