package io.github.valerait.ghostlink.core.firebase

import com.google.firebase.firestore.Blob
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.android
import kotlinx.coroutines.tasks.await

actual suspend fun DocumentReference.setAvatar(
    data: ByteArray,
    version: Long,
) {
    android.set(mapOf("data" to Blob.fromBytes(data), "version" to version)).await()
}

actual fun DocumentSnapshot.avatarData(): ByteArray? = android.getBlob("data")?.toBytes()
