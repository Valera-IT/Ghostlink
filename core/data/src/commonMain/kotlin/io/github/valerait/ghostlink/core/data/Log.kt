package io.github.valerait.ghostlink.core.data

/** Предупреждение в системный лог: Logcat, java.util.logging, NSLog. */
internal expect fun logWarning(
    tag: String,
    message: String,
    error: Throwable?,
)
