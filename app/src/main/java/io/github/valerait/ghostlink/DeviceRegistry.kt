package io.github.valerait.ghostlink

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.valerait.ghostlink.data.device.ThisDevice
import io.github.valerait.ghostlink.data.model.Device
import io.github.valerait.ghostlink.data.repository.AuthRepository
import io.github.valerait.ghostlink.data.repository.DeviceRepository
import io.github.valerait.ghostlink.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Держит это устройство в реестре users/{uid}/devices:
 * - аккаунту без ключа выдаёт ключ (так переезжают анонимные аккаунты старой версии);
 * - записывает устройство и время активности при каждом запуске;
 * - если устройство удалили с другого устройства, выходит из аккаунта.
 *
 * Работает только для аккаунтов с профилем: временный аккаунт нового устройства в реестр не попадает.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DeviceRegistry @Inject constructor(
    private val auth: AuthRepository,
    private val users: UserRepository,
    private val devices: DeviceRepository,
    private val thisDevice: ThisDevice,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var userId: String? = null

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            auth.account
                .map { it?.id }
                .distinctUntilChanged()
                .flatMapLatest { id ->
                    if (id == null) flowOf(null) else users.observeUser(id).map { profile -> id.takeIf { profile != null } }
                }
                .distinctUntilChanged()
                .collectLatest { id ->
                    userId = id
                    if (id != null) maintain(id)
                }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        val id = userId ?: return
        scope.launch { register(id) }
    }

    private suspend fun maintain(id: String) {
        try {
            auth.ensureAccountKey()
        } catch (e: Exception) {
            // Без ключа приложение работает, просто нельзя войти на другом устройстве; повторим при следующем запуске.
            Log.w(TAG, "Failed to issue account key", e)
        }
        if (!register(id)) return
        devices.observeDevice(id, thisDevice.id).first { it == null }
        Log.i(TAG, "This device was removed from the account, signing out")
        auth.signOut()
    }

    private suspend fun register(id: String): Boolean = try {
        devices.registerDevice(id, Device(thisDevice.id, thisDevice.name, thisDevice.platform))
        true
    } catch (e: Exception) {
        Log.w(TAG, "Failed to register device", e)
        false
    }

    private companion object {
        const val TAG = "DeviceRegistry"
    }
}
