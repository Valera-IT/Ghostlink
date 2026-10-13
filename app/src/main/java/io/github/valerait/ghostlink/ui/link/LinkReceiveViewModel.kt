package io.github.valerait.ghostlink.ui.link

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.valerait.ghostlink.data.device.ThisDevice
import io.github.valerait.ghostlink.data.link.LinkCrypto
import io.github.valerait.ghostlink.data.model.AccountKey
import io.github.valerait.ghostlink.data.model.LINK_TTL_MS
import io.github.valerait.ghostlink.data.model.LinkCode
import io.github.valerait.ghostlink.data.repository.AuthRepository
import io.github.valerait.ghostlink.data.repository.LinkRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

sealed interface LinkReceiveState {
    data object Preparing : LinkReceiveState
    /** QR с кодом входа; [expiresAt] — для обратного отсчёта. */
    data class ShowCode(val link: String, val expiresAt: Long) : LinkReceiveState
    data object SigningIn : LinkReceiveState
    data object Expired : LinkReceiveState
    data object Failed : LinkReceiveState
}

/**
 * Новое устройство: создаёт заявку, показывает QR и ждёт, пока устройство с аккаунтом
 * положит в заявку зашифрованный ключ. Дальше корень сам переключится на чаты.
 */
@HiltViewModel
class LinkReceiveViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val links: LinkRepository,
    private val device: ThisDevice,
) : ViewModel() {

    private val _state = MutableStateFlow<LinkReceiveState>(LinkReceiveState.Preparing)
    val state: StateFlow<LinkReceiveState> = _state.asStateFlow()

    private var job: Job? = null
    private var requestId: String? = null

    init {
        restart()
    }

    fun restart() {
        job?.cancel()
        job = viewModelScope.launch { run() }
    }

    private suspend fun run() {
        _state.value = LinkReceiveState.Preparing
        try {
            dropRequest()
            // Заявку нельзя создать без входа: временный анонимный аккаунт удалится после входа по ключу.
            if (auth.account.value == null) auth.signInAnonymously()
            val keyPair = LinkCrypto.generateKeyPair()
            val id = links.createLinkRequest(device.name, device.platform).also { requestId = it }
            val code = LinkCode(id, LinkCrypto.encodePublicKey(keyPair.public))
            _state.value = LinkReceiveState.ShowCode(code.toUri(), System.currentTimeMillis() + LINK_TTL_MS)

            val payload = withTimeoutOrNull(LINK_TTL_MS) {
                links.observeLinkRequest(id).first { it?.payload != null }?.payload
            }
            if (payload == null) {
                dropRequest()
                _state.value = LinkReceiveState.Expired
                return
            }
            _state.value = LinkReceiveState.SigningIn
            val key = AccountKey(LinkCrypto.decrypt(keyPair.private, id, payload))
            dropRequest()
            auth.signInWithKey(key)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Linking failed", e)
            _state.value = LinkReceiveState.Failed
        }
    }

    private suspend fun dropRequest() {
        val id = requestId ?: return
        requestId = null
        withContext(NonCancellable) {
            runCatching { links.deleteLinkRequest(id) }.onFailure { Log.w(TAG, "Failed to delete link request", it) }
        }
    }

    override fun onCleared() {
        // viewModelScope уже отменён; заявка истечёт сама, но уберём её, если успеем.
        val id = requestId ?: return
        MainScope().launch { runCatching { links.deleteLinkRequest(id) } }
    }

    private companion object {
        const val TAG = "LinkReceive"
    }
}
