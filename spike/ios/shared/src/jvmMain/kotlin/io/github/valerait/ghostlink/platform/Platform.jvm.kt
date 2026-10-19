package io.github.valerait.ghostlink.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.Blob
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.android
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.InetAddress
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger
import java.util.prefs.Preferences
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Десктоп в разведке: SecureStore на Preferences — только для проверки. На этапе 4 — java-keyring
// (Keychain на macOS, Credential Manager на Windows).

private val prefs: Preferences by lazy {
    Preferences.userRoot().node(System.getProperty("ghostlink.prefs", "io/github/valerait/ghostlink"))
}

actual object DeviceInfo {
    actual val id: String by lazy {
        prefs.get("deviceId", null) ?: UUID.randomUUID().toString().replace("-", "").also { prefs.put("deviceId", it) }
    }

    actual val name: String by lazy {
        runCatching { InetAddress.getLocalHost().hostName.removeSuffix(".local") }.getOrDefault("Desktop")
    }

    actual val platform: String = if (System.getProperty("os.name").startsWith("Mac")) "macos" else "windows"
}

actual object SecureStore {
    private val node get() = prefs.node("accountKey")

    actual fun get(account: String): ByteArray? = node.getByteArray(account, null)

    actual fun put(account: String, value: ByteArray) {
        clear()
        node.putByteArray(account, value)
    }

    actual fun clear() = node.clear()
}

actual object LocalSettings {
    actual fun getString(key: String): String? = prefs.get(key, null)
    actual fun putString(key: String, value: String) = prefs.put(key, value)
}

actual fun logWarning(tag: String, message: String, error: Throwable?) {
    Logger.getLogger(tag).log(Level.WARNING, message, error)
}

private fun format(pattern: String, millis: Long) = SimpleDateFormat(pattern).format(Date(millis))

actual fun formatTime(millis: Long): String = format("HH:mm", millis)
actual fun formatWeekday(millis: Long): String = format("EEE", millis)
actual fun formatDate(millis: Long, withYear: Boolean): String =
    if (withYear) DateFormat.getDateInstance(DateFormat.LONG).format(Date(millis)) else format("d MMMM", millis)
actual fun formatShortDate(millis: Long): String = format("dd.MM.yy", millis)

actual fun localEpochDay(millis: Long): Long = (millis + TimeZone.getDefault().getOffset(millis)).floorDiv(86_400_000L)

// firebase-java-sdk — это Android-SDK Firestore, поэтому Blob пишется так же, как на Android.
actual suspend fun DocumentReference.approveWithPayload(payload: ByteArray, approvedBy: String) {
    android.update(mapOf("payload" to Blob.fromBytes(payload), "approvedBy" to approvedBy)).await()
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        val error = task.exception
        if (error == null) cont.resume(task.result) else cont.resumeWithException(error)
    }
}

actual fun DocumentSnapshot.payloadBytes(): ByteArray? = android.getBlob("payload")?.toBytes()

/** На десктопе камеры нет: вход только по QR, который показывает сам десктоп (этап 4.2). */
@Composable
actual fun QrScanner(onScanned: (String) -> Unit, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) { Text("Камера недоступна на десктопе") }
}
