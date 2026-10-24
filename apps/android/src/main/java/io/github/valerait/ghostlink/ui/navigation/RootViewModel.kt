package io.github.valerait.ghostlink.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.valerait.ghostlink.core.domain.repository.AuthRepository
import io.github.valerait.ghostlink.core.domain.repository.UserRepository
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.core.platform.DeepLinks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface RootState {
    data object Loading : RootState

    /** Нет аккаунта или у аккаунта ещё нет профиля. */
    data object Onboarding : RootState

    data class Ready(
        val userId: UserId,
    ) : RootState
}

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModel(
    auth: AuthRepository,
    users: UserRepository,
    private val deepLinks: DeepLinks,
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
    val pendingInvite: StateFlow<UserId?> = deepLinks.pendingInvite

    fun onInviteHandled() = deepLinks.inviteHandled()
}
