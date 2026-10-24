package io.github.valerait.ghostlink.core.data

import java.util.logging.Level
import java.util.logging.Logger

internal actual fun logWarning(
    tag: String,
    message: String,
    error: Throwable?,
) {
    Logger.getLogger(tag).log(Level.WARNING, message, error)
}
