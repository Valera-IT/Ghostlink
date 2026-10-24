package io.github.valerait.ghostlink.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.valerait.ghostlink.core.domain.repository.AuthRepository
import io.github.valerait.ghostlink.core.domain.repository.DeviceRepository
import io.github.valerait.ghostlink.core.domain.repository.requireUserId
import io.github.valerait.ghostlink.core.domain.usecase.SignOutEverywhereUseCase
import io.github.valerait.ghostlink.core.model.Device
import io.github.valerait.ghostlink.core.model.DeviceId
import io.github.valerait.ghostlink.core.model.ThisDevice
import io.github.valerait.ghostlink.core.ui.logWarning
import io.github.valerait.ghostlink.core.ui.resources.Res
import io.github.valerait.ghostlink.core.ui.resources.devices_removed
import io.github.valerait.ghostlink.core.ui.resources.error_generic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

data class DevicesUiState(
    val thisDevice: Device? = null,
    val others: List<Device> = emptyList(),
    val busy: Boolean = false,
    val message: StringResource? = null,
    /** Новый ключ восстановления после «Выйти на всех других устройствах» — старый больше не работает. */
    val newRecoveryCode: String? = null,
)

sealed interface DevicesAction {
    /** Мягкое удаление: устройство само выйдет, увидев, что его нет в реестре. */
    data class Remove(
        val deviceId: DeviceId,
    ) : DevicesAction

    /** Выйти на всех остальных устройствах. */
    data object SignOutOthers : DevicesAction

    data object MessageShown : DevicesAction

    data object RecoveryCodeShown : DevicesAction
}

class DevicesViewModel(
    auth: AuthRepository,
    private val devices: DeviceRepository,
    private val thisDevice: ThisDevice,
    private val signOutEverywhere: SignOutEverywhereUseCase,
) : ViewModel() {
    private val me = auth.requireUserId()
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<StringResource?>(null)
    private val newRecoveryCode = MutableStateFlow<String?>(null)

    val state: StateFlow<DevicesUiState> =
        combine(devices.observeDevices(me), busy, message, newRecoveryCode) { list, busy, message, code ->
            DevicesUiState(
                thisDevice =
                    list.firstOrNull { it.id == thisDevice.id }
                        ?: Device(thisDevice.id, thisDevice.name, thisDevice.platform),
                others = list.filter { it.id != thisDevice.id },
                busy = busy,
                message = message,
                newRecoveryCode = code,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DevicesUiState())

    fun onAction(action: DevicesAction) {
        when (action) {
            is DevicesAction.Remove -> remove(action.deviceId)
            DevicesAction.SignOutOthers -> signOutOthers()
            DevicesAction.MessageShown -> onMessageShown()
            DevicesAction.RecoveryCodeShown -> onRecoveryCodeShown()
        }
    }

    /** Мягкое удаление: устройство само выйдет, увидев, что его нет в реестре. */
    private fun remove(deviceId: DeviceId) =
        launchBusy {
            devices.removeDevice(me, deviceId)
            message.value = Res.string.devices_removed
        }

    /** Жёсткая мера: новый секрет ключа (см. [SignOutEverywhereUseCase]). */
    private fun signOutOthers() =
        launchBusy {
            newRecoveryCode.value = signOutEverywhere(thisDevice.id)
        }

    private fun onMessageShown() {
        message.value = null
    }

    private fun onRecoveryCodeShown() {
        newRecoveryCode.value = null
    }

    private fun launchBusy(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                logWarning("Devices", "Action failed", e)
                message.value = Res.string.error_generic
            } finally {
                busy.value = false
            }
        }
    }
}
