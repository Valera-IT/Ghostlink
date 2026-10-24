package io.github.valerait.ghostlink.feature.friends

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.valerait.ghostlink.core.domain.repository.AuthRepository
import io.github.valerait.ghostlink.core.domain.repository.UserRepository
import io.github.valerait.ghostlink.core.domain.repository.requireUserId
import io.github.valerait.ghostlink.core.domain.usecase.AddFriendUseCase
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.User
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.core.platform.logWarning
import io.github.valerait.ghostlink.core.ui.resources.Res
import io.github.valerait.ghostlink.core.ui.resources.error_generic
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

sealed interface ConnectUiState {
    data object Loading : ConnectUiState

    data object Self : ConnectUiState

    data object NotFound : ConnectUiState

    data class Found(
        val user: User,
        val busy: Boolean = false,
        val error: StringResource? = null,
    ) : ConnectUiState
}

sealed interface ConnectAction {
    /** Начать чат с найденным человеком. */
    data object Connect : ConnectAction
}

sealed interface ConnectEffect {
    /** Чат с этим человеком есть (создан или уже был) — открываем его. */
    data class ChatReady(
        val chatId: ChatId,
    ) : ConnectEffect
}

class ConnectViewModel(
    savedStateHandle: SavedStateHandle,
    auth: AuthRepository,
    private val users: UserRepository,
    private val addFriend: AddFriendUseCase,
) : ViewModel() {
    private val friendId = UserId(checkNotNull(savedStateHandle["userId"]))
    private val me = auth.requireUserId()

    private val _state = MutableStateFlow<ConnectUiState>(ConnectUiState.Loading)
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    private val _effects = Channel<ConnectEffect>(Channel.BUFFERED)
    val effects: Flow<ConnectEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            _state.value =
                when {
                    friendId == me -> {
                        ConnectUiState.Self
                    }

                    else -> {
                        try {
                            users.getUser(friendId)?.let { ConnectUiState.Found(it) } ?: ConnectUiState.NotFound
                        } catch (e: Exception) {
                            logWarning(TAG, "Failed to load user", e)
                            ConnectUiState.NotFound
                        }
                    }
                }
        }
    }

    fun onAction(action: ConnectAction) {
        when (action) {
            ConnectAction.Connect -> connect()
        }
    }

    private fun connect() {
        val found = _state.value as? ConnectUiState.Found ?: return
        if (found.busy) return
        _state.value = found.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                _effects.send(ConnectEffect.ChatReady(addFriend(friendId)))
            } catch (e: Exception) {
                logWarning(TAG, "Failed to start chat", e)
                _state.update { (it as? ConnectUiState.Found)?.copy(busy = false, error = Res.string.error_generic) ?: it }
            }
        }
    }

    private companion object {
        const val TAG = "Connect"
    }
}
