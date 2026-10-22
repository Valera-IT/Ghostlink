package io.github.valerait.ghostlink.ui.link

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.valerait.ghostlink.core.domain.usecase.LinkState
import io.github.valerait.ghostlink.core.domain.usecase.RequestDeviceLinkUseCase
import io.github.valerait.ghostlink.data.device.ThisDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Instant

sealed interface LinkReceiveState {
    data object Preparing : LinkReceiveState

    /** QR с кодом входа; [expiresAt] — для обратного отсчёта. */
    data class ShowCode(
        val link: String,
        val expiresAt: Instant,
    ) : LinkReceiveState

    data object SigningIn : LinkReceiveState

    data object Expired : LinkReceiveState

    data object Failed : LinkReceiveState
}

/**
 * Новое устройство: показывает QR и ждёт, пока устройство с аккаунтом подтвердит вход
 * (см. [RequestDeviceLinkUseCase]). Дальше корень сам переключится на чаты.
 */
@HiltViewModel
class LinkReceiveViewModel
    @Inject
    constructor(
        private val requestLink: RequestDeviceLinkUseCase,
        private val device: ThisDevice,
    ) : ViewModel() {
        private val _state = MutableStateFlow<LinkReceiveState>(LinkReceiveState.Preparing)
        val state: StateFlow<LinkReceiveState> = _state.asStateFlow()

        private var job: Job? = null

        init {
            restart()
        }

        /** Новая заявка; прежняя удаляется, когда отменяется её сбор. */
        fun restart() {
            job?.cancel()
            _state.value = LinkReceiveState.Preparing
            job =
                viewModelScope.launch {
                    try {
                        requestLink(device.name, device.platform).collect { _state.value = it.toUiState() }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Linking failed", e)
                        _state.value = LinkReceiveState.Failed
                    }
                }
        }

        private fun LinkState.toUiState(): LinkReceiveState =
            when (this) {
                is LinkState.ShowCode -> LinkReceiveState.ShowCode(code.toUri(), expiresAt)
                LinkState.SigningIn -> LinkReceiveState.SigningIn
                LinkState.Expired -> LinkReceiveState.Expired
            }

        private companion object {
            const val TAG = "LinkReceive"
        }
    }
