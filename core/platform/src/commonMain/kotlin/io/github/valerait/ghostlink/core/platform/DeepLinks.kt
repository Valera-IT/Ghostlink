package io.github.valerait.ghostlink.core.platform

import io.github.valerait.ghostlink.core.model.InviteLink
import io.github.valerait.ghostlink.core.model.UserId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ссылки ghostlink://, которыми открыли приложение. Платформа передаёт их в [open]
 * (Android — из Intent, iOS — из onOpenURL), корневой экран забирает приглашение из [pendingInvite].
 * Приглашение может прийти до онбординга — тогда оно ждёт, пока экран его обработает.
 */
class DeepLinks {
    private val _pendingInvite = MutableStateFlow<UserId?>(null)
    val pendingInvite: StateFlow<UserId?> = _pendingInvite.asStateFlow()

    fun open(url: String?) {
        InviteLink.parse(url)?.let { _pendingInvite.value = it }
    }

    fun inviteHandled() {
        _pendingInvite.value = null
    }
}
