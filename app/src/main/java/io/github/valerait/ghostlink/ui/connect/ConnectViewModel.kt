package io.github.valerait.ghostlink.ui.connect

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.valerait.ghostlink.R
import io.github.valerait.ghostlink.data.model.User
import io.github.valerait.ghostlink.data.repository.AuthRepository
import io.github.valerait.ghostlink.data.repository.ChatRepository
import io.github.valerait.ghostlink.data.repository.UserRepository
import io.github.valerait.ghostlink.data.repository.requireUserId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ConnectState {
    data object Loading : ConnectState

    data object Self : ConnectState

    data object NotFound : ConnectState

    data class Found(
        val user: User,
        val busy: Boolean = false,
        @StringRes val error: Int? = null,
    ) : ConnectState
}

@HiltViewModel
class ConnectViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        auth: AuthRepository,
        private val users: UserRepository,
        private val chats: ChatRepository,
    ) : ViewModel() {
        private val friendId: String = checkNotNull(savedStateHandle["userId"])
        private val me = auth.requireUserId()

        private val _state = MutableStateFlow<ConnectState>(ConnectState.Loading)
        val state: StateFlow<ConnectState> = _state.asStateFlow()

        private val _chatReady = Channel<String>(Channel.BUFFERED)
        val chatReady: Flow<String> = _chatReady.receiveAsFlow()

        init {
            viewModelScope.launch {
                _state.value =
                    when {
                        friendId == me -> ConnectState.Self
                        else ->
                            try {
                                users.getUser(friendId)?.let { ConnectState.Found(it) } ?: ConnectState.NotFound
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to load user", e)
                                ConnectState.NotFound
                            }
                    }
            }
        }

        fun connect() {
            val found = _state.value as? ConnectState.Found ?: return
            if (found.busy) return
            _state.value = found.copy(busy = true, error = null)
            viewModelScope.launch {
                try {
                    _chatReady.send(chats.startChat(me, friendId))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to start chat", e)
                    _state.update { (it as? ConnectState.Found)?.copy(busy = false, error = R.string.error_generic) ?: it }
                }
            }
        }

        private companion object {
            const val TAG = "Connect"
        }
    }
