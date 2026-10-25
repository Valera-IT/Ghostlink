package io.github.valerait.ghostlink.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSDate
import platform.Foundation.NSLog
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.UIKit.UIPasteboard
import platform.UIKit.UIPasteboardOptionExpirationDate
import platform.UIKit.UIPasteboardOptionLocalOnly

actual fun logWarning(
    tag: String,
    message: String,
    error: Throwable?,
) {
    // Строки Kotlin в variadic-аргументах NSLog роняют приложение, поэтому текст идёт форматом, с экранированным %.
    NSLog("W/$tag: $message${error?.let { ": $it" }.orEmpty()}".replace("%", "%%"))
}

@Composable
actual fun rememberCopyToClipboard(sensitive: Boolean): (String) -> Unit =
    remember(sensitive) {
        { text ->
            val pasteboard = UIPasteboard.generalPasteboard
            if (sensitive) {
                // Секрет не уезжает через Universal Clipboard на Mac и iPad и сам стирается из буфера.
                val options =
                    mapOf<Any?, Any>(
                        UIPasteboardOptionLocalOnly to true,
                        UIPasteboardOptionExpirationDate to NSDate.dateWithTimeIntervalSinceNow(SENSITIVE_CLIP_SECONDS),
                    )
                pasteboard.setItems(listOf(mapOf(PLAIN_TEXT to text)), options)
            } else {
                pasteboard.string = text
            }
        }
    }

/** UTType.utf8PlainText. */
private const val PLAIN_TEXT = "public.utf8-plain-text"

/** Ключ восстановления успеют вставить в менеджер паролей, но в буфере он не останется. */
private const val SENSITIVE_CLIP_SECONDS = 120.0

// Системный лист «Поделиться» подключим вместе с первым экраном, которому он нужен: пока его никто не вызывает.
@Composable
actual fun rememberShareText(title: String): (String) -> Unit = rememberCopyToClipboard()
