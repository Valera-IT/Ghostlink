package io.github.valerait.ghostlink.data.firebase

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

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

internal val Timestamp.millis: Long get() = toDate().time
