package io.github.valerait.ghostlink.feature.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.valerait.ghostlink.core.domain.repository.AuthRepository
import io.github.valerait.ghostlink.core.domain.repository.UserRepository
import io.github.valerait.ghostlink.core.domain.repository.requireUserId
import io.github.valerait.ghostlink.core.model.InviteLink
import io.github.valerait.ghostlink.core.model.UserId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class MyCodeUiState(
    val userId: UserId,
    val name: String,
    val link: String,
)

class MyCodeViewModel(
    auth: AuthRepository,
    users: UserRepository,
) : ViewModel() {
    private val me = auth.requireUserId()

    val state: StateFlow<MyCodeUiState> =
        users
            .observeUser(me)
            .map { MyCodeUiState(me, it?.name.orEmpty(), InviteLink.create(me)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyCodeUiState(me, "", InviteLink.create(me)))
}
