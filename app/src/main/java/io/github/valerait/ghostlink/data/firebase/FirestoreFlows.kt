package io.github.valerait.ghostlink.data.firebase

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import io.github.valerait.ghostlink.core.model.Platform
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.time.Instant

private const val TAG = "Firestore"

// Ошибку слушателя (например, отказ правил доступа) только логируем:
// падение потока уронило бы весь экран, а пустое состояние UI переживёт.
internal fun <T> DocumentReference.observe(map: (DocumentSnapshot) -> T): Flow<T> =
    callbackFlow {
        val registration =
            addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) Log.w(TAG, "Listen failed: $path", error)
                if (snapshot != null) trySend(map(snapshot))
            }
        awaitClose { registration.remove() }
    }

internal fun <T> Query.observe(
    metadataChanges: MetadataChanges = MetadataChanges.EXCLUDE,
    map: (QuerySnapshot) -> T,
): Flow<T> =
    callbackFlow {
        val registration =
            addSnapshotListener(metadataChanges) { snapshot, error ->
                if (error != null) Log.w(TAG, "Query listen failed", error)
                if (snapshot != null) trySend(map(snapshot))
            }
        awaitClose { registration.remove() }
    }

internal val Timestamp.instant: Instant get() = Instant.fromEpochSeconds(seconds, nanoseconds)

internal fun Instant.toTimestamp(): Timestamp = Timestamp(epochSeconds, nanosecondsOfSecond)

// Платформа хранится строкой в нижнем регистре: так её пишут и читают все клиенты.
internal val Platform.wireName: String get() = name.lowercase()

internal fun platformOf(wireName: String?): Platform = Platform.entries.firstOrNull { it.wireName == wireName } ?: Platform.Unknown
