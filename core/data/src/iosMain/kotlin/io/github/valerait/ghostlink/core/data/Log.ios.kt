package io.github.valerait.ghostlink.core.data

import platform.Foundation.NSLog

internal actual fun logWarning(
    tag: String,
    message: String,
    error: Throwable?,
) {
    NSLog("%@", "W/$tag: $message${error?.let { ": $it" }.orEmpty()}")
}
