package io.github.valerait.ghostlink.ui.settings

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.valerait.ghostlink.R
import io.github.valerait.ghostlink.core.domain.repository.AuthRepository
import io.github.valerait.ghostlink.core.domain.repository.UserRepository
import io.github.valerait.ghostlink.core.domain.repository.requireUserId
import io.github.valerait.ghostlink.core.domain.usecase.GetRecoveryCodeUseCase
import io.github.valerait.ghostlink.core.domain.usecase.RenameProfileUseCase
import io.github.valerait.ghostlink.core.domain.usecase.SignOutUseCase
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.data.device.ThisDevice
import io.github.valerait.ghostlink.di.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val userId: UserId = UserId(""),
    val name: String = "",
    val demoMode: Boolean = false,
    val busy: Boolean = false,
    @StringRes val message: Int? = null,
    /** Ключ восстановления, пока открыт диалог с ним. */
    val recoveryCode: String? = null,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        auth: AuthRepository,
        users: UserRepository,
        private val thisDevice: ThisDevice,
        private val config: AppConfig,
        private val renameProfile: RenameProfileUseCase,
        private val getRecoveryCode: GetRecoveryCodeUseCase,
        private val signOutHere: SignOutUseCase,
    ) : ViewModel() {
        private val me = auth.requireUserId()
        private val busy = MutableStateFlow(false)
        private val message = MutableStateFlow<Int?>(null)
        private val recoveryCode = MutableStateFlow<String?>(null)

        val state: StateFlow<SettingsState> =
            combine(
                users.observeUser(me),
                busy,
                message,
                recoveryCode,
            ) { user, busy, message, recoveryCode ->
                SettingsState(
                    userId = me,
                    name = user?.name.orEmpty(),
                    demoMode = config.demoMode,
                    busy = busy,
                    message = message,
                    recoveryCode = recoveryCode,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState(userId = me, demoMode = config.demoMode))

        fun rename(name: String) = launchBusy { renameProfile(name) }

        fun showRecoveryKey() =
            launchBusy {
                recoveryCode.value = getRecoveryCode()
            }

        fun onRecoveryKeyShown() {
            recoveryCode.value = null
        }

        fun signOut() = launchBusy { signOutHere(thisDevice.id) }

        fun onMessageShown() {
            message.value = null
        }

        private fun launchBusy(block: suspend () -> Unit) {
            if (busy.value) return
            busy.value = true
            viewModelScope.launch {
                try {
                    block()
                } catch (e: Exception) {
                    Log.w("Settings", "Action failed", e)
                    message.value = R.string.error_generic
                } finally {
                    busy.value = false
                }
            }
        }
    }
