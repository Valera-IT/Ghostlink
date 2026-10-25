@file:OptIn(ExperimentalForeignApi::class)

package io.github.valerait.ghostlink.core.firebase

import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.ios
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.Foundation.numberWithLongLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual suspend fun DocumentReference.setAvatar(
    data: ByteArray,
    version: Long,
): Unit =
    suspendCancellableCoroutine { cont ->
        ios.setData(mapOf<Any?, Any?>("data" to data.toNSData(), "version" to version)) { error ->
            if (error == null) cont.resume(Unit) else cont.resumeWithException(Exception(error.localizedDescription))
        }
    }

actual suspend fun DocumentReference.setAvatarVersion(version: Long): Unit =
    suspendCancellableCoroutine { cont ->
        ios.updateData(mapOf<Any?, Any?>("avatarVersion" to NSNumber.numberWithLongLong(version))) { error ->
            if (error == null) cont.resume(Unit) else cont.resumeWithException(Exception(error.localizedDescription))
        }
    }

actual fun DocumentSnapshot.avatarData(): ByteArray? = (ios.valueForField("data") as? NSData)?.toByteArray()
