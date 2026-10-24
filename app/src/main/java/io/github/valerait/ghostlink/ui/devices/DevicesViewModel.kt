package io.github.valerait.ghostlink.ui.devices

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.valerait.ghostlink.R
import io.github.valerait.ghostlink.core.domain.repository.AuthRepository
import io.github.valerait.ghostlink.core.domain.repository.DeviceRepository
import io.github.valerait.ghostlink.core.domain.repository.requireUserId
import io.github.valerait.ghostlink.core.domain.usecase.SignOutEverywhereUseCase
import io.github.valerait.ghostlink.core.model.Device
import io.github.valerait.ghostlink.core.model.DeviceId
import io.github.valerait.ghostlink.data.device.ThisDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DevicesState(
    val thisDevice: Device? = null,
    val others: List<Device> = emptyList(),
    val busy: Boolean = false,
    @StringRes val message: Int? = null,
    /** Новый ключ восстановления после «Выйти на всех других устройствах» — старый больше не работает. */
    val newRecoveryCode: String? = null,
)

class DevicesViewModel(
    auth: AuthRepository,
    private val devices: DeviceRepository,
    private val thisDevice: ThisDevice,
    private val signOutEverywhere: SignOutEverywhereUseCase,
) : ViewModel() {
    private val me = auth.requireUserId()
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<Int?>(null)
    private val newRecoveryCode = MutableStateFlow<String?>(null)

    val state: StateFlow<DevicesState> =
        combine(devices.observeDevices(me), busy, message, newRecoveryCode) { list, busy, message, code ->
            DevicesState(
                thisDevice =
                    list.firstOrNull { it.id == thisDevice.id }
                        ?: Device(thisDevice.id, thisDevice.name, thisDevice.platform),
                others = list.filter { it.id != thisDevice.id },
                busy = busy,
                message = message,
                newRecoveryCode = code,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DevicesState())

    /** Мягкое удаление: устройство само выйдет, увидев, что его нет в реестре. */
    fun remove(deviceId: DeviceId) =
        launchBusy {
            devices.removeDevice(me, deviceId)
            message.value = R.string.devices_removed
        }

    /** Жёсткая мера: новый секрет ключа (см. [SignOutEverywhereUseCase]). */
    fun signOutOthers() =
        launchBusy {
            newRecoveryCode.value = signOutEverywhere(thisDevice.id)
        }

    fun onMessageShown() {
        message.value = null
    }

    fun onRecoveryCodeShown() {
        newRecoveryCode.value = null
    }

    private fun launchBusy(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Log.w("Devices", "Action failed", e)
                message.value = R.string.error_generic
            } finally {
                busy.value = false
            }
        }
    }
}
