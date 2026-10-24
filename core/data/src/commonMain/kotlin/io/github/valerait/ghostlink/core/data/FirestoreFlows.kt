package io.github.valerait.ghostlink.core.data

import dev.gitlive.firebase.firestore.ServerTimestampBehavior
import io.github.valerait.ghostlink.core.platform.logWarning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

// Ещё не подтверждённое сервером время (pending-запись) читаем как оценку, а не как null.
internal val ESTIMATE = ServerTimestampBehavior.ESTIMATE

// Ошибку слушателя (например, отказ правил доступа) только логируем:
// падение потока уронило бы весь экран, а пустое состояние UI переживёт.
internal fun <T> Flow<T>.logErrors(what: String): Flow<T> = catch { logWarning("Firestore", "Listen failed: $what", it) }

/**
 * Запись без ожидания: интерфейс репозитория не suspend, потому что Firestore сразу применяет её локально,
 * а подтверждение сервера приходит позже (или после восстановления сети). Ошибку только логируем.
 */
@Suppress("TooGenericExceptionCaught") // любая ошибка записи только логируется
internal fun CoroutineScope.launchWrite(
    tag: String,
    what: String,
    block: suspend () -> Unit,
) {
    launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logWarning(tag, "$what failed", e.toGhostlinkError())
        }
    }
}
