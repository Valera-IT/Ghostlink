package io.github.valerait.ghostlink.feature.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.valerait.ghostlink.core.domain.usecase.RestoreWithRecoveryCodeUseCase
import io.github.valerait.ghostlink.core.model.GhostlinkError
import io.github.valerait.ghostlink.core.platform.logWarning
import io.github.valerait.ghostlink.core.ui.resources.Res
import io.github.valerait.ghostlink.core.ui.resources.error_sign_in
import io.github.valerait.ghostlink.core.ui.resources.recovery_error_format
import io.github.valerait.ghostlink.core.ui.resources.recovery_error_wrong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

data class RecoveryUiState(
    val code: String = "",
    val busy: Boolean = false,
    val error: StringResource? = null,
) {
    val canSubmit: Boolean get() = code.isNotBlank() && !busy
}

sealed interface RecoveryAction {
    data class CodeChanged(
        val value: String,
    ) : RecoveryAction

    data object Submit : RecoveryAction
}

/** Вход по ключу восстановления, когда других устройств с аккаунтом под рукой нет. */
class RecoveryViewModel(
    private val restore: RestoreWithRecoveryCodeUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(RecoveryUiState())
    val state: StateFlow<RecoveryUiState> = _state.asStateFlow()

    fun onAction(action: RecoveryAction) {
        when (action) {
            is RecoveryAction.CodeChanged -> onCodeChange(action.value)
            RecoveryAction.Submit -> submit()
        }
    }

    private fun onCodeChange(value: String) {
        _state.update { it.copy(code = value.take(MAX_INPUT), error = null) }
    }

    private fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                restore(current.code)
                // Дальше корень сам переключится на чаты.
            } catch (e: GhostlinkError.MalformedRecoveryCode) {
                _state.update { it.copy(busy = false, error = Res.string.recovery_error_format) }
            } catch (e: GhostlinkError.InvalidAccountKey) {
                _state.update { it.copy(busy = false, error = Res.string.recovery_error_wrong) }
            } catch (e: Exception) {
                logWarning("Recovery", "Sign in failed", e)
                _state.update { it.copy(busy = false, error = Res.string.error_sign_in) }
            }
        }
    }

    private companion object {
        const val MAX_INPUT = 80
    }
}
