package io.github.valerait.ghostlink.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSLog
import platform.UIKit.UIPasteboard

actual fun logWarning(
    tag: String,
    message: String,
    error: Throwable?,
) {
    NSLog("%@", "W/$tag: $message${error?.let { ": $it" }.orEmpty()}")
}

@Composable
actual fun rememberCopyToClipboard(sensitive: Boolean): (String) -> Unit =
    remember { { text -> UIPasteboard.generalPasteboard.string = text } }

// Системный лист «Поделиться» подключим вместе с iOS-приложением (этап 5).
@Composable
actual fun rememberShareText(title: String): (String) -> Unit = rememberCopyToClipboard()
