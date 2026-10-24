package io.github.valerait.ghostlink.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.valerait.ghostlink.core.domain.usecase.CreateAccountUseCase
import io.github.valerait.ghostlink.core.model.User
import io.github.valerait.ghostlink.core.platform.logWarning
import io.github.valerait.ghostlink.core.ui.AppConfig
import io.github.valerait.ghostlink.core.ui.resources.Res
import io.github.valerait.ghostlink.core.ui.resources.error_sign_in
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

data class OnboardingUiState(
    val name: String = "",
    val busy: Boolean = false,
    val error: StringResource? = null,
    val demoMode: Boolean = false,
) {
    val canSubmit: Boolean get() = name.isNotBlank() && !busy
}

sealed interface OnboardingAction {
    data class NameChanged(
        val value: String,
    ) : OnboardingAction

    data object Submit : OnboardingAction
}

class OnboardingViewModel(
    private val createAccount: CreateAccountUseCase,
    private val config: AppConfig,
) : ViewModel() {
    private val _state =
        MutableStateFlow(OnboardingUiState(demoMode = config.demoMode))
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun onAction(action: OnboardingAction) {
        when (action) {
            is OnboardingAction.NameChanged -> onNameChange(action.value)
            OnboardingAction.Submit -> submit()
        }
    }

    private fun onNameChange(value: String) {
        _state.update { it.copy(name = value.take(User.NAME_MAX_LENGTH), error = null) }
    }

    /** Создаёт аккаунт с ключом (если его ещё нет) и профиль; дальше корень сам переключится на чаты. */
    private fun submit() {
        val name = _state.value.name.trim()
        if (name.isEmpty() || _state.value.busy) return
        runBusy { createAccount(name) }
    }

    private fun runBusy(block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                logWarning("Onboarding", "Sign in failed", e)
                _state.update { it.copy(busy = false, error = Res.string.error_sign_in) }
            }
        }
    }
}
