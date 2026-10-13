package io.github.valerait.ghostlink.ui.settings

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.valerait.ghostlink.R
import io.github.valerait.ghostlink.data.device.ThisDevice
import io.github.valerait.ghostlink.data.repository.AccountAlreadyLinkedException
import io.github.valerait.ghostlink.data.repository.AuthRepository
import io.github.valerait.ghostlink.data.repository.DeviceRepository
import io.github.valerait.ghostlink.data.repository.UserRepository
import io.github.valerait.ghostlink.data.repository.requireUserId
import io.github.valerait.ghostlink.di.AppConfig
import io.github.valerait.ghostlink.ui.onboarding.MAX_NAME_LENGTH
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val userId: String = "",
    val name: String = "",
    /** Почта привязанного Google; null — Google не привязан. */
    val googleEmail: String? = null,
    val demoMode: Boolean = false,
    val googleAvailable: Boolean = false,
    val busy: Boolean = false,
    @StringRes val message: Int? = null,
    /** Ключ восстановления, пока открыт диалог с ним. */
    val recoveryCode: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val users: UserRepository,
    private val devices: DeviceRepository,
    private val thisDevice: ThisDevice,
    private val config: AppConfig,
) : ViewModel() {

    private val me = auth.requireUserId()
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<Int?>(null)
    private val recoveryCode = MutableStateFlow<String?>(null)

    val webClientId: String? get() = config.webClientId

    val state: StateFlow<SettingsState> = combine(
        auth.account,
        users.observeUser(me),
        busy,
        message,
        recoveryCode,
    ) { account, user, busy, message, recoveryCode ->
        SettingsState(
            userId = me,
            name = user?.name.orEmpty(),
            googleEmail = account?.googleEmail,
            demoMode = config.demoMode,
            googleAvailable = config.webClientId != null,
            busy = busy,
            message = message,
            recoveryCode = recoveryCode,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState(userId = me, demoMode = config.demoMode))

    fun rename(name: String) {
        val trimmed = name.trim().take(MAX_NAME_LENGTH)
        if (trimmed.isEmpty()) return
        launchBusy { users.saveProfile(me, trimmed) }
    }

    fun linkGoogle(idToken: String) = launchBusy {
        auth.linkGoogle(idToken)
        message.value = R.string.settings_google_linked
    }

    fun showRecoveryKey() = launchBusy {
        val key = auth.accountKey.value ?: auth.ensureAccountKey()
        recoveryCode.value = key.toRecoveryCode()
    }

    fun onRecoveryKeyShown() {
        recoveryCode.value = null
    }

    fun signOut() = launchBusy {
        users.setOnline(me, false)
        // Без интернета запись устройства останется в списке — её можно удалить с другого устройства.
        runCatching { devices.removeDevice(me, thisDevice.id) }
        auth.signOut()
    }

    fun onMessageShown() {
        message.value = null
    }

    private fun launchBusy(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: AccountAlreadyLinkedException) {
                message.value = R.string.error_google_already_linked
            } catch (e: Exception) {
                Log.w("Settings", "Action failed", e)
                message.value = R.string.error_generic
            } finally {
                busy.value = false
            }
        }
    }
}
