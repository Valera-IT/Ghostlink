package io.github.valerait.ghostlink.ui.mycode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.valerait.ghostlink.data.model.InviteLink
import io.github.valerait.ghostlink.data.repository.AuthRepository
import io.github.valerait.ghostlink.data.repository.UserRepository
import io.github.valerait.ghostlink.data.repository.requireUserId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MyCodeState(val userId: String, val name: String, val link: String)

@HiltViewModel
class MyCodeViewModel @Inject constructor(
    auth: AuthRepository,
    users: UserRepository,
) : ViewModel() {

    private val me = auth.requireUserId()

    val state: StateFlow<MyCodeState> = users.observeUser(me)
        .map { MyCodeState(me, it?.name.orEmpty(), InviteLink.create(me)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyCodeState(me, "", InviteLink.create(me)))
}
