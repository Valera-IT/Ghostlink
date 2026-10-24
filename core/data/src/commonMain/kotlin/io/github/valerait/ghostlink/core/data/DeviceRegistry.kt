package io.github.valerait.ghostlink.core.data

import io.github.valerait.ghostlink.core.domain.repository.AuthRepository
import io.github.valerait.ghostlink.core.domain.repository.DeviceRepository
import io.github.valerait.ghostlink.core.domain.repository.UserRepository
import io.github.valerait.ghostlink.core.model.Device
import io.github.valerait.ghostlink.core.model.ThisDevice
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.core.platform.AppLifecycle
import io.github.valerait.ghostlink.core.platform.logWarning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Держит это устройство в реестре users/{uid}/devices:
 * - аккаунту без ключа выдаёт ключ (так переезжают анонимные аккаунты старой версии);
 * - записывает устройство и время активности каждый раз, когда приложение выходит на экран;
 * - если устройство удалили с другого устройства, выходит из аккаунта.
 *
 * Работает только для аккаунтов с профилем: временный аккаунт нового устройства в реестр не попадает.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceRegistry(
    private val auth: AuthRepository,
    private val users: UserRepository,
    private val devices: DeviceRepository,
    private val thisDevice: ThisDevice,
    private val lifecycle: AppLifecycle,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            auth.account
                .map { it?.id }
                .distinctUntilChanged()
                .flatMapLatest { id ->
                    if (id == null) flowOf(null) else users.observeUser(id).map { profile -> id.takeIf { profile != null } }
                }.distinctUntilChanged()
                .collectLatest { id -> if (id != null) maintain(id) }
        }
    }

    private suspend fun maintain(id: UserId) =
        coroutineScope {
            try {
                auth.ensureAccountKey()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Без ключа приложение работает, просто нельзя войти на другом устройстве; повторим при следующем запуске.
                logWarning(TAG, "Failed to issue account key", e)
            }
            val registered = CompletableDeferred<Unit>()
            launch {
                lifecycle.isForeground.filter { it }.collect {
                    if (register(id)) registered.complete(Unit)
                }
            }
            // Пока устройство не записано, его отсутствие в реестре ничего не значит.
            registered.await()
            devices.observeDevice(id, thisDevice.id).first { it == null }
            logWarning(TAG, "This device was removed from the account, signing out")
            coroutineContext.cancelChildren()
            auth.signOut()
        }

    private suspend fun register(id: UserId): Boolean =
        try {
            devices.registerDevice(id, Device(thisDevice.id, thisDevice.name, thisDevice.platform))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logWarning(TAG, "Failed to register device", e)
            false
        }

    private companion object {
        const val TAG = "DeviceRegistry"
    }
}
