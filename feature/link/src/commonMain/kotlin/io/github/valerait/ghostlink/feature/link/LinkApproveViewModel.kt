package io.github.valerait.ghostlink.feature.link

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.valerait.ghostlink.core.domain.usecase.ApproveDeviceLinkUseCase
import io.github.valerait.ghostlink.core.model.LinkCode
import io.github.valerait.ghostlink.core.model.Platform
import io.github.valerait.ghostlink.core.platform.logWarning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LinkApproveUiState {
    data object Loading : LinkApproveUiState

    data class Confirm(
        val deviceName: String,
        val platform: Platform,
        val busy: Boolean = false,
        val failed: Boolean = false,
    ) : LinkApproveUiState

    data object Done : LinkApproveUiState

    /** Заявки нет, она истекла или её уже подтвердили. */
    data object Invalid : LinkApproveUiState
}

sealed interface LinkApproveAction {
    data object Approve : LinkApproveAction
}

/** Устройство с аккаунтом подтверждает вход на новом: шифрует ключ аккаунта ключом из QR. */
class LinkApproveViewModel(
    savedStateHandle: SavedStateHandle,
    private val approveLink: ApproveDeviceLinkUseCase,
) : ViewModel() {
    private val code: LinkCode? = LinkCode.parse(savedStateHandle.get<String>("code"))

    private val _state = MutableStateFlow<LinkApproveUiState>(LinkApproveUiState.Loading)
    val state: StateFlow<LinkApproveUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value =
                try {
                    val request = approveLink.load(checkNotNull(code) { "Not a link code" })
                    LinkApproveUiState.Confirm(request.deviceName, request.platform)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Просроченная, уже подтверждённая или чужая заявка — для пользователя всё равно «код не подходит».
                    logWarning(TAG, "Link request is not valid", e)
                    LinkApproveUiState.Invalid
                }
        }
    }

    fun onAction(action: LinkApproveAction) {
        when (action) {
            LinkApproveAction.Approve -> approve()
        }
    }

    private fun approve() {
        val confirm = _state.value as? LinkApproveUiState.Confirm
        val code = code
        if (confirm == null || code == null || confirm.busy) return
        _state.value = confirm.copy(busy = true, failed = false)
        viewModelScope.launch {
            _state.value =
                try {
                    approveLink(code)
                    LinkApproveUiState.Done
                } catch (e: Exception) {
                    logWarning(TAG, "Failed to approve link request", e)
                    confirm.copy(busy = false, failed = true)
                }
        }
    }

    private companion object {
        const val TAG = "LinkApprove"
    }
}
