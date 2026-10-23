package io.github.valerait.ghostlink.core.firebase

import com.google.firebase.firestore.Blob
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.android
import kotlinx.coroutines.tasks.await

// firebase-java-sdk — это порт Android SDK, поэтому код тот же, что в androidMain.

actual suspend fun DocumentReference.approveLinkRequest(
    payload: ByteArray,
    approvedBy: String,
) {
    android.update(mapOf("payload" to Blob.fromBytes(payload), "approvedBy" to approvedBy)).await()
}

actual fun DocumentSnapshot.linkPayload(): ByteArray? = android.getBlob("payload")?.toBytes()
