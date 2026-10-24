package io.github.valerait.ghostlink.ui.recovery

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.valerait.ghostlink.R
import io.github.valerait.ghostlink.core.domain.usecase.RestoreWithRecoveryCodeUseCase
import io.github.valerait.ghostlink.core.model.GhostlinkError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecoveryState(
    val code: String = "",
    val busy: Boolean = false,
    @StringRes val error: Int? = null,
) {
    val canSubmit: Boolean get() = code.isNotBlank() && !busy
}

/** Вход по ключу восстановления, когда других устройств с аккаунтом под рукой нет. */
class RecoveryViewModel(
    private val restore: RestoreWithRecoveryCodeUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(RecoveryState())
    val state: StateFlow<RecoveryState> = _state.asStateFlow()

    fun onCodeChange(value: String) {
        _state.update { it.copy(code = value.take(MAX_INPUT), error = null) }
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                restore(current.code)
                // Дальше корень сам переключится на чаты.
            } catch (e: GhostlinkError.MalformedRecoveryCode) {
                _state.update { it.copy(busy = false, error = R.string.recovery_error_format) }
            } catch (e: GhostlinkError.InvalidAccountKey) {
                _state.update { it.copy(busy = false, error = R.string.recovery_error_wrong) }
            } catch (e: Exception) {
                Log.w("Recovery", "Sign in failed", e)
                _state.update { it.copy(busy = false, error = R.string.error_sign_in) }
            }
        }
    }

    private companion object {
        const val MAX_INPUT = 80
    }
}
