package io.github.valerait.ghostlink

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.valerait.ghostlink.core.domain.repository.AuthRepository
import io.github.valerait.ghostlink.core.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Статус «в сети»: пока приложение на экране, раз в минуту обновляет lastSeen,
 * при уходе в фон выставляет offline. В Firestore нет onDisconnect,
 * поэтому собеседник считает статус устаревшим через ONLINE_TIMEOUT_MS.
 */
class PresenceTracker(
    private val auth: AuthRepository,
    private val users: UserRepository,
) : DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var heartbeat: Job? = null

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        heartbeat?.cancel()
        heartbeat =
            scope.launch {
                auth.account.collectLatest { account ->
                    if (account == null) return@collectLatest
                    while (true) {
                        users.setOnline(account.id, true)
                        delay(HEARTBEAT_MS)
                    }
                }
            }
    }

    override fun onStop(owner: LifecycleOwner) {
        heartbeat?.cancel()
        auth.account.value?.let { users.setOnline(it.id, false) }
    }

    private companion object {
        const val HEARTBEAT_MS = 60_000L
    }
}
