package io.github.valerait.ghostlink.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.data.repository.AuthRepository
import io.github.valerait.ghostlink.data.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface RootState {
    data object Loading : RootState

    /** Нет аккаунта или у аккаунта ещё нет профиля. */
    data object Onboarding : RootState

    data class Ready(
        val userId: UserId,
    ) : RootState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RootViewModel
    @Inject
    constructor(
        auth: AuthRepository,
        users: UserRepository,
    ) : ViewModel() {
        val state: StateFlow<RootState> =
            auth.account
                .map { it?.id }
                .distinctUntilChanged()
                .flatMapLatest { id ->
                    if (id == null) {
                        flowOf(RootState.Onboarding)
                    } else {
                        users.observeUser(id).map { if (it == null) RootState.Onboarding else RootState.Ready(id) }
                    }
                }.distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.Eagerly, RootState.Loading)

        // Приглашение может прийти до онбординга — тогда откроем его сразу после.
        private val _pendingInvite = MutableStateFlow<UserId?>(null)
        val pendingInvite: StateFlow<UserId?> = _pendingInvite.asStateFlow()

        fun onInvite(userId: UserId) {
            _pendingInvite.value = userId
        }

        fun onInviteHandled() {
            _pendingInvite.value = null
        }
    }
