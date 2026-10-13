package io.github.valerait.ghostlink.ui.devices

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.valerait.ghostlink.R
import io.github.valerait.ghostlink.data.device.ThisDevice
import io.github.valerait.ghostlink.data.model.Device
import io.github.valerait.ghostlink.data.repository.AuthRepository
import io.github.valerait.ghostlink.data.repository.DeviceRepository
import io.github.valerait.ghostlink.data.repository.requireUserId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DevicesState(
    val thisDevice: Device? = null,
    val others: List<Device> = emptyList(),
    val busy: Boolean = false,
    @StringRes val message: Int? = null,
    /** Новый ключ восстановления после «Выйти на всех других устройствах» — старый больше не работает. */
    val newRecoveryCode: String? = null,
)

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val devices: DeviceRepository,
    private val thisDevice: ThisDevice,
) : ViewModel() {

    private val me = auth.requireUserId()
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<Int?>(null)
    private val newRecoveryCode = MutableStateFlow<String?>(null)

    val state: StateFlow<DevicesState> =
        combine(devices.observeDevices(me), busy, message, newRecoveryCode) { list, busy, message, code ->
            DevicesState(
                thisDevice = list.firstOrNull { it.id == thisDevice.id }
                    ?: Device(thisDevice.id, thisDevice.name, thisDevice.platform),
                others = list.filter { it.id != thisDevice.id },
                busy = busy,
                message = message,
                newRecoveryCode = code,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DevicesState())

    /** Мягкое удаление: устройство само выйдет, увидев, что его нет в реестре. */
    fun remove(deviceId: String) = launchBusy {
        devices.removeDevice(me, deviceId)
        message.value = R.string.devices_removed
    }

    /**
     * Жёсткая мера: новый секрет ключа. Firebase отзывает сессии остальных устройств,
     * а удаление из реестра заставляет их выйти сразу, не дожидаясь истечения токена.
     */
    fun signOutOthers() = launchBusy {
        val key = auth.rotateKey()
        newRecoveryCode.value = key.toRecoveryCode()
        devices.observeDevices(me).first()
            .filter { it.id != thisDevice.id }
            .forEach { runCatching { devices.removeDevice(me, it.id) } }
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
