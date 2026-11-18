package io.github.valerait.ghostlink.core.data

import dev.gitlive.firebase.FirebaseNetworkException
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import dev.gitlive.firebase.firestore.ServerTimestampBehavior
import dev.gitlive.firebase.firestore.code
import io.github.valerait.ghostlink.core.platform.logWarning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

private const val LISTENER_RETRY_INITIAL_DELAY_MS = 1_000L
private const val LISTENER_RETRY_MAX_DOUBLINGS = 5L

// Ещё не подтверждённое сервером время (pending-запись) читаем как оценку, а не как null.
internal val ESTIMATE = ServerTimestampBehavior.ESTIMATE

// Ошибку слушателя (например, отказ правил доступа) только логируем:
// падение потока уронило бы весь экран, а пустое состояние UI переживёт.
internal fun <T> Flow<T>.logErrors(what: String): Flow<T> = catch { logWarning("Firestore", "Listen failed: $what", it) }

internal fun <T> Flow<T>.retryChatListener(what: String): Flow<T> =
    retryWhen { cause, attempt ->
        if (!cause.canRetryListener()) return@retryWhen false
        logWarning("Firestore", "Retrying listener: $what", cause)
        val doublings = attempt.coerceAtMost(LISTENER_RETRY_MAX_DOUBLINGS).toInt()
        delay(LISTENER_RETRY_INITIAL_DELAY_MS shl doublings)
        true
    }.logErrors(what)

private fun Throwable.canRetryListener(): Boolean =
    when (this) {
        is FirebaseNetworkException -> {
            true
        }

        is FirebaseFirestoreException -> {
            when (code) {
                FirestoreExceptionCode.UNAVAILABLE,
                FirestoreExceptionCode.DEADLINE_EXCEEDED,
                FirestoreExceptionCode.ABORTED,
                FirestoreExceptionCode.INTERNAL,
                FirestoreExceptionCode.RESOURCE_EXHAUSTED,
                -> true

                else -> false
            }
        }

        else -> {
            false
        }
    }

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
