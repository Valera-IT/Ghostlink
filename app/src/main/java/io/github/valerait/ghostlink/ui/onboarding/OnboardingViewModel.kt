package io.github.valerait.ghostlink.ui.onboarding

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.valerait.ghostlink.R
import io.github.valerait.ghostlink.core.domain.usecase.CreateAccountUseCase
import io.github.valerait.ghostlink.core.model.User
import io.github.valerait.ghostlink.di.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingState(
    val name: String = "",
    val busy: Boolean = false,
    @StringRes val error: Int? = null,
    val demoMode: Boolean = false,
) {
    val canSubmit: Boolean get() = name.isNotBlank() && !busy
}

class OnboardingViewModel(
    private val createAccount: CreateAccountUseCase,
    private val config: AppConfig,
) : ViewModel() {
    private val _state =
        MutableStateFlow(OnboardingState(demoMode = config.demoMode))
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun onNameChange(value: String) {
        _state.update { it.copy(name = value.take(User.NAME_MAX_LENGTH), error = null) }
    }

    /** Создаёт аккаунт с ключом (если его ещё нет) и профиль; дальше корень сам переключится на чаты. */
    fun submit() {
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
                Log.w("Onboarding", "Sign in failed", e)
                _state.update { it.copy(busy = false, error = R.string.error_sign_in) }
            }
        }
    }
}
