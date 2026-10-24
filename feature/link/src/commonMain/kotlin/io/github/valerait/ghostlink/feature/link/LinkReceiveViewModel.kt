package io.github.valerait.ghostlink.feature.link

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.valerait.ghostlink.core.domain.usecase.LinkState
import io.github.valerait.ghostlink.core.domain.usecase.RequestDeviceLinkUseCase
import io.github.valerait.ghostlink.core.model.ThisDevice
import io.github.valerait.ghostlink.core.ui.logWarning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Instant

sealed interface LinkReceiveUiState {
    data object Preparing : LinkReceiveUiState

    /** QR с кодом входа; [expiresAt] — для обратного отсчёта. */
    data class ShowCode(
        val link: String,
        val expiresAt: Instant,
    ) : LinkReceiveUiState

    data object SigningIn : LinkReceiveUiState

    data object Expired : LinkReceiveUiState

    data object Failed : LinkReceiveUiState
}

sealed interface LinkReceiveAction {
    /** Код истёк или не удался — выдать новый. */
    data object Restart : LinkReceiveAction
}

/**
 * Новое устройство: показывает QR и ждёт, пока устройство с аккаунтом подтвердит вход
 * (см. [RequestDeviceLinkUseCase]). Дальше корень сам переключится на чаты.
 */
class LinkReceiveViewModel(
    private val requestLink: RequestDeviceLinkUseCase,
    private val device: ThisDevice,
) : ViewModel() {
    private val _state = MutableStateFlow<LinkReceiveUiState>(LinkReceiveUiState.Preparing)
    val state: StateFlow<LinkReceiveUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        restart()
    }

    fun onAction(action: LinkReceiveAction) {
        when (action) {
            LinkReceiveAction.Restart -> restart()
        }
    }

    /** Новая заявка; прежняя удаляется, когда отменяется её сбор. */
    private fun restart() {
        job?.cancel()
        _state.value = LinkReceiveUiState.Preparing
        job =
            viewModelScope.launch {
                try {
                    requestLink(device.name, device.platform).collect { _state.value = it.toUiState() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logWarning(TAG, "Linking failed", e)
                    _state.value = LinkReceiveUiState.Failed
                }
            }
    }

    private fun LinkState.toUiState(): LinkReceiveUiState =
        when (this) {
            is LinkState.ShowCode -> LinkReceiveUiState.ShowCode(code.toUri(), expiresAt)
            LinkState.SigningIn -> LinkReceiveUiState.SigningIn
            LinkState.Expired -> LinkReceiveUiState.Expired
        }

    private companion object {
        const val TAG = "LinkReceive"
    }
}
