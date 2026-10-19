package io.github.valerait.ghostlink.app

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import io.github.valerait.ghostlink.data.AuthRepository
import io.github.valerait.ghostlink.data.ChatRepository
import io.github.valerait.ghostlink.data.DeviceRepository
import io.github.valerait.ghostlink.data.LinkRepository
import io.github.valerait.ghostlink.data.UserRepository
import io.github.valerait.ghostlink.data.firebase.FirebaseAuthRepository
import io.github.valerait.ghostlink.data.firebase.FirestoreChatRepository
import io.github.valerait.ghostlink.data.firebase.FirestoreDeviceRepository
import io.github.valerait.ghostlink.data.firebase.FirestoreLinkRepository
import io.github.valerait.ghostlink.data.firebase.FirestoreUserRepository
import io.github.valerait.ghostlink.model.Device
import io.github.valerait.ghostlink.platform.DeviceInfo
import io.github.valerait.ghostlink.platform.logWarning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Ручной DI разведки (на этапе 3 его заменит Koin). Firebase уже сконфигурирован из Swift
 * (FirebaseApp.configure() в iOSApp) до первого обращения сюда.
 */
object AppGraph {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val auth: AuthRepository by lazy { FirebaseAuthRepository(Firebase.auth, scope) }
    val users: UserRepository by lazy { FirestoreUserRepository(Firebase.firestore) }
    val chats: ChatRepository by lazy { FirestoreChatRepository(Firebase.firestore, scope) }
    val devices: DeviceRepository by lazy { FirestoreDeviceRepository(Firebase.firestore) }
    val links: LinkRepository by lazy { FirestoreLinkRepository(Firebase.firestore, Firebase.auth) }

    private var started = false
    private var heartbeat: Job? = null
    private var registryUser: String? = null

    /** Реестр устройств — как DeviceRegistry на Android. Запускается один раз. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            auth.account
                .map { it?.id }
                .distinctUntilChanged()
                .flatMapLatest { id ->
                    if (id == null) flowOf(null) else users.observeUser(id).map { profile -> id.takeIf { profile != null } }
                }
                .distinctUntilChanged()
                .collectLatest { id ->
                    registryUser = id
                    if (id != null) maintainDevice(id)
                }
        }
    }

    /** Приложение на экране: heartbeat «в сети» и отметка активности устройства. */
    fun onForeground() {
        heartbeat?.cancel()
        heartbeat = scope.launch {
            registryUser?.let { registerDevice(it) }
            auth.account.collectLatest { account ->
                if (account == null) return@collectLatest
                while (true) {
                    runCatching { users.setOnline(account.id, true) }
                    delay(HEARTBEAT_MS)
                }
            }
        }
    }

    fun onBackground() {
        heartbeat?.cancel()
        val id = auth.account.value?.id ?: return
        scope.launch { runCatching { users.setOnline(id, false) } }
    }

    private suspend fun maintainDevice(id: String) {
        try {
            auth.ensureAccountKey()
        } catch (e: Exception) {
            logWarning("DeviceRegistry", "Failed to issue account key", e)
        }
        if (!registerDevice(id)) return
        devices.observeDevice(id, DeviceInfo.id).first { it == null }
        logWarning("DeviceRegistry", "This device was removed from the account, signing out")
        auth.signOut()
    }

    private suspend fun registerDevice(id: String): Boolean = try {
        devices.registerDevice(id, Device(DeviceInfo.id, DeviceInfo.name, DeviceInfo.platform))
        true
    } catch (e: Exception) {
        logWarning("DeviceRegistry", "Failed to register device", e)
        false
    }

    private const val HEARTBEAT_MS = 60_000L
}
