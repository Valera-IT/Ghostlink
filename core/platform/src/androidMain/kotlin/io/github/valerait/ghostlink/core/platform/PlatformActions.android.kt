package io.github.valerait.ghostlink.core.platform

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

actual fun logWarning(
    tag: String,
    message: String,
    error: Throwable?,
) {
    Log.w(tag, message, error)
}

@Composable
actual fun rememberCopyToClipboard(sensitive: Boolean): (String) -> Unit {
    val context = LocalContext.current
    return remember(context, sensitive) {
        { text ->
            val clip = ClipData.newPlainText("Ghostlink", text)
            // Android 13+ не покажет секрет во всплывающем превью буфера.
            if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
            }
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        }
    }
}

@Composable
actual fun rememberShareText(title: String): (String) -> Unit {
    val context = LocalContext.current
    return remember(context, title) {
        { text ->
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(send, title))
        }
    }
}
