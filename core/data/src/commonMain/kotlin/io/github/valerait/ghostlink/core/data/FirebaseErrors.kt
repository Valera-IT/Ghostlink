package io.github.valerait.ghostlink.core.data

import dev.gitlive.firebase.FirebaseNetworkException
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import dev.gitlive.firebase.firestore.code
import io.github.valerait.ghostlink.core.model.GhostlinkError
import kotlinx.coroutines.CancellationException

/** Исключение Firebase → [GhostlinkError]. Выше core:data типов Firebase нет. */
internal fun Exception.toGhostlinkError(): Exception =
    when (this) {
        is GhostlinkError, is CancellationException -> {
            this
        }

        is FirebaseNetworkException -> {
            GhostlinkError.Offline(this)
        }

        is FirebaseFirestoreException -> {
            when (code) {
                FirestoreExceptionCode.UNAVAILABLE, FirestoreExceptionCode.DEADLINE_EXCEEDED -> GhostlinkError.Offline(this)
                FirestoreExceptionCode.PERMISSION_DENIED, FirestoreExceptionCode.UNAUTHENTICATED -> GhostlinkError.PermissionDenied(this)
                else -> GhostlinkError.Unexpected(this)
            }
        }

        else -> {
            GhostlinkError.Unexpected(this)
        }
    }

/** Обращение к Firebase, которое бросает только [GhostlinkError] (и отмену корутины). */
@Suppress("TooGenericExceptionCaught") // ловим всё, чтобы наружу выходили только GhostlinkError
internal suspend inline fun <T> firebaseCall(block: () -> T): T =
    try {
        block()
    } catch (e: Exception) {
        throw e.toGhostlinkError()
    }
