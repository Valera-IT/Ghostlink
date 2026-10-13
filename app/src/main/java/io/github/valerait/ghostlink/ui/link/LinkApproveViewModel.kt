package io.github.valerait.ghostlink.ui.link

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.valerait.ghostlink.data.link.LinkCrypto
import io.github.valerait.ghostlink.data.model.LinkCode
import io.github.valerait.ghostlink.data.repository.AuthRepository
import io.github.valerait.ghostlink.data.repository.LinkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LinkApproveState {
    data object Loading : LinkApproveState
    data class Confirm(val deviceName: String, val platform: String, val busy: Boolean = false, val failed: Boolean = false) : LinkApproveState
    data object Done : LinkApproveState
    /** Заявки нет, она истекла или её уже подтвердили. */
    data object Invalid : LinkApproveState
}

/** Устройство с аккаунтом подтверждает вход на новом: шифрует ключ аккаунта ключом из QR. */
@HiltViewModel
class LinkApproveViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val auth: AuthRepository,
    private val links: LinkRepository,
) : ViewModel() {

    private val code: LinkCode? = LinkCode.parse(savedStateHandle.get<String>("code"))

    private val _state = MutableStateFlow<LinkApproveState>(LinkApproveState.Loading)
    val state: StateFlow<LinkApproveState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val request = try {
                code?.let { links.getLinkRequest(it.requestId) }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load link request", e)
                null
            }
            _state.value = if (request == null || request.payload != null || request.isExpired(System.currentTimeMillis())) {
                LinkApproveState.Invalid
            } else {
                LinkApproveState.Confirm(request.deviceName, request.platform)
            }
        }
    }

    fun approve() {
        val confirm = _state.value as? LinkApproveState.Confirm ?: return
        val code = code ?: return
        if (confirm.busy) return
        _state.value = confirm.copy(busy = true, failed = false)
        viewModelScope.launch {
            _state.value = try {
                val key = auth.accountKey.value ?: auth.ensureAccountKey()
                links.approveLinkRequest(code.requestId, LinkCrypto.encrypt(code.publicKey, code.requestId, key.bytes))
                LinkApproveState.Done
            } catch (e: Exception) {
                Log.w(TAG, "Failed to approve link request", e)
                confirm.copy(busy = false, failed = true)
            }
        }
    }

    private companion object {
        const val TAG = "LinkApprove"
    }
}
