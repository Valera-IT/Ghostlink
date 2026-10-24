package io.github.valerait.ghostlink.ui.link

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.valerait.ghostlink.core.domain.usecase.ApproveDeviceLinkUseCase
import io.github.valerait.ghostlink.core.model.LinkCode
import io.github.valerait.ghostlink.core.model.Platform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LinkApproveState {
    data object Loading : LinkApproveState

    data class Confirm(
        val deviceName: String,
        val platform: Platform,
        val busy: Boolean = false,
        val failed: Boolean = false,
    ) : LinkApproveState

    data object Done : LinkApproveState

    /** Заявки нет, она истекла или её уже подтвердили. */
    data object Invalid : LinkApproveState
}

/** Устройство с аккаунтом подтверждает вход на новом: шифрует ключ аккаунта ключом из QR. */
class LinkApproveViewModel(
    savedStateHandle: SavedStateHandle,
    private val approveLink: ApproveDeviceLinkUseCase,
) : ViewModel() {
    private val code: LinkCode? = LinkCode.parse(savedStateHandle.get<String>("code"))

    private val _state = MutableStateFlow<LinkApproveState>(LinkApproveState.Loading)
    val state: StateFlow<LinkApproveState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value =
                try {
                    val request = approveLink.load(checkNotNull(code) { "Not a link code" })
                    LinkApproveState.Confirm(request.deviceName, request.platform)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Просроченная, уже подтверждённая или чужая заявка — для пользователя всё равно «код не подходит».
                    Log.w(TAG, "Link request is not valid", e)
                    LinkApproveState.Invalid
                }
        }
    }

    fun approve() {
        val confirm = _state.value as? LinkApproveState.Confirm ?: return
        val code = code ?: return
        if (confirm.busy) return
        _state.value = confirm.copy(busy = true, failed = false)
        viewModelScope.launch {
            _state.value =
                try {
                    approveLink(code)
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
