package io.github.valerait.ghostlink.core.firebase

import com.google.firebase.firestore.Blob
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.android
import kotlinx.coroutines.tasks.await

// Firebase Android SDK.

actual suspend fun DocumentReference.approveLinkRequest(
    payload: ByteArray,
    approvedBy: String,
) {
    android.update(mapOf("payload" to Blob.fromBytes(payload), "approvedBy" to approvedBy)).await()
}

actual fun DocumentSnapshot.linkPayload(): ByteArray? = android.getBlob("payload")?.toBytes()
