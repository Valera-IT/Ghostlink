package io.github.valerait.ghostlink.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.valerait.ghostlink.core.domain.repository.AuthRepository
import io.github.valerait.ghostlink.core.domain.repository.UserRepository
import io.github.valerait.ghostlink.core.domain.repository.requireUserId
import io.github.valerait.ghostlink.core.domain.usecase.GetRecoveryCodeUseCase
import io.github.valerait.ghostlink.core.domain.usecase.RenameProfileUseCase
import io.github.valerait.ghostlink.core.domain.usecase.SignOutUseCase
import io.github.valerait.ghostlink.core.model.ThisDevice
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.core.ui.AppConfig
import io.github.valerait.ghostlink.core.ui.AppLanguage
import io.github.valerait.ghostlink.core.ui.LanguageSettings
import io.github.valerait.ghostlink.core.ui.logWarning
import io.github.valerait.ghostlink.core.ui.resources.Res
import io.github.valerait.ghostlink.core.ui.resources.error_generic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

data class SettingsUiState(
    val userId: UserId = UserId(""),
    val name: String = "",
    val demoMode: Boolean = false,
    val versionName: String = "",
    val busy: Boolean = false,
    val message: StringResource? = null,
    /** Ключ восстановления, пока открыт диалог с ним. */
    val recoveryCode: String? = null,
)

sealed interface SettingsAction {
    data class SelectLanguage(
        val language: AppLanguage,
    ) : SettingsAction

    data class Rename(
        val name: String,
    ) : SettingsAction

    data object ShowRecoveryKey : SettingsAction

    data object RecoveryKeyShown : SettingsAction

    data object SignOut : SettingsAction

    data object MessageShown : SettingsAction
}

class SettingsViewModel(
    auth: AuthRepository,
    users: UserRepository,
    private val thisDevice: ThisDevice,
    private val config: AppConfig,
    private val renameProfile: RenameProfileUseCase,
    private val getRecoveryCode: GetRecoveryCodeUseCase,
    private val signOutHere: SignOutUseCase,
    private val languagePrefs: LanguageSettings,
) : ViewModel() {
    val language: StateFlow<AppLanguage> = languagePrefs.language

    private val me = auth.requireUserId()
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<StringResource?>(null)
    private val recoveryCode = MutableStateFlow<String?>(null)

    val state: StateFlow<SettingsUiState> =
        combine(
            users.observeUser(me),
            busy,
            message,
            recoveryCode,
        ) { user, busy, message, recoveryCode ->
            SettingsUiState(
                userId = me,
                name = user?.name.orEmpty(),
                demoMode = config.demoMode,
                versionName = config.versionName,
                busy = busy,
                message = message,
                recoveryCode = recoveryCode,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SettingsUiState(userId = me, demoMode = config.demoMode, versionName = config.versionName),
        )

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.SelectLanguage -> selectLanguage(action.language)
            is SettingsAction.Rename -> rename(action.name)
            SettingsAction.ShowRecoveryKey -> showRecoveryKey()
            SettingsAction.RecoveryKeyShown -> onRecoveryKeyShown()
            SettingsAction.SignOut -> signOut()
            SettingsAction.MessageShown -> onMessageShown()
        }
    }

    private fun selectLanguage(language: AppLanguage) = languagePrefs.setLanguage(language)

    private fun rename(name: String) = launchBusy { renameProfile(name) }

    private fun showRecoveryKey() =
        launchBusy {
            recoveryCode.value = getRecoveryCode()
        }

    private fun onRecoveryKeyShown() {
        recoveryCode.value = null
    }

    private fun signOut() = launchBusy { signOutHere(thisDevice.id) }

    private fun onMessageShown() {
        message.value = null
    }

    private fun launchBusy(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                logWarning("Settings", "Action failed", e)
                message.value = Res.string.error_generic
            } finally {
                busy.value = false
            }
        }
    }
}
