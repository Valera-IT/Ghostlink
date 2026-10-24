package io.github.valerait.ghostlink.feature.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.valerait.ghostlink.core.domain.repository.AuthRepository
import io.github.valerait.ghostlink.core.domain.repository.ChatRepository
import io.github.valerait.ghostlink.core.domain.repository.requireUserId
import io.github.valerait.ghostlink.core.domain.usecase.ChatSummary
import io.github.valerait.ghostlink.core.domain.usecase.LeaveGroupUseCase
import io.github.valerait.ghostlink.core.domain.usecase.ObserveChatListUseCase
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.ui.AppConfig
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ChatListUiState(
    val loading: Boolean = true,
    val items: List<ChatSummary> = emptyList(),
    val demoMode: Boolean = false,
)

sealed interface ChatListAction {
    data class SetPinned(
        val chatId: ChatId,
        val pinned: Boolean,
    ) : ChatListAction

    data class SetMuted(
        val chatId: ChatId,
        val muted: Boolean,
    ) : ChatListAction

    /** Личный чат прячется только у меня; из группы я выхожу (админ передаёт права следующему участнику). */
    data class Remove(
        val chatId: ChatId,
    ) : ChatListAction
}

class ChatListViewModel(
    auth: AuthRepository,
    observeChatList: ObserveChatListUseCase,
    private val chats: ChatRepository,
    private val leaveGroup: LeaveGroupUseCase,
    private val config: AppConfig,
) : ViewModel() {
    private val me = auth.requireUserId()

    val state: StateFlow<ChatListUiState> =
        observeChatList(me)
            .map { ChatListUiState(loading = false, items = it, demoMode = config.demoMode) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatListUiState(demoMode = config.demoMode))

    fun onAction(action: ChatListAction) {
        when (action) {
            is ChatListAction.SetPinned -> setPinned(action.chatId, action.pinned)
            is ChatListAction.SetMuted -> setMuted(action.chatId, action.muted)
            is ChatListAction.Remove -> remove(action.chatId)
        }
    }

    private fun setPinned(
        chatId: ChatId,
        pinned: Boolean,
    ) = chats.setPinned(me, chatId, pinned)

    private fun setMuted(
        chatId: ChatId,
        muted: Boolean,
    ) = chats.setMuted(me, chatId, muted)

    /** Личный чат прячется только у меня; из группы я выхожу (админ передаёт права следующему участнику). */
    private fun remove(chatId: ChatId) {
        val chat =
            state.value.items
                .firstOrNull { it.chat.id == chatId }
                ?.chat
        if (chat?.isGroup == true) leaveGroup(chat, me) else chats.removeChat(me, chatId)
    }
}
