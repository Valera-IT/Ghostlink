@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.github.valerait.ghostlink.core.firebase

import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.ios
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual suspend fun DocumentReference.approveLinkRequest(
    payload: ByteArray,
    approvedBy: String,
): Unit =
    suspendCancellableCoroutine { cont ->
        ios.updateData(mapOf<Any?, Any?>("payload" to payload.toNSData(), "approvedBy" to approvedBy)) { error ->
            if (error == null) cont.resume(Unit) else cont.resumeWithException(Exception(error.localizedDescription))
        }
    }

actual fun DocumentSnapshot.linkPayload(): ByteArray? = (ios.valueForField("payload") as? NSData)?.toByteArray()

private fun ByteArray.toNSData(): NSData =
    usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.convert())
    }

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply { usePinned { memcpy(it.addressOf(0), bytes, length) } }
}
