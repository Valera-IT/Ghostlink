package io.github.valerait.ghostlink.core.data

import io.github.valerait.ghostlink.core.domain.repository.AuthRepository
import io.github.valerait.ghostlink.core.domain.repository.UserRepository
import io.github.valerait.ghostlink.core.platform.AppLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

/**
 * Статус «в сети»: пока приложение на экране, раз в минуту обновляет lastSeen,
 * при уходе в фон выставляет offline. В Firestore нет onDisconnect,
 * поэтому собеседник считает статус устаревшим через ONLINE_TIMEOUT_MS.
 */
class PresenceTracker(
    private val auth: AuthRepository,
    private val users: UserRepository,
    private val lifecycle: AppLifecycle,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            lifecycle.isForeground
                // «Не в сети» пишем только после того, как приложение побывало на экране.
                .dropWhile { !it }
                .collectLatest { foreground ->
                    if (foreground) {
                        auth.account.collectLatest { account ->
                            if (account == null) return@collectLatest
                            while (true) {
                                users.setOnline(account.id, true)
                                delay(HEARTBEAT)
                            }
                        }
                    } else {
                        auth.account.value?.let { users.setOnline(it.id, false) }
                    }
                }
        }
    }

    private companion object {
        val HEARTBEAT = 1.minutes
    }
}
