package io.github.valerait.ghostlink.core.data

import android.util.Log

internal actual fun logWarning(
    tag: String,
    message: String,
    error: Throwable?,
) {
    Log.w(tag, message, error)
}
