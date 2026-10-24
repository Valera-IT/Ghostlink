package io.github.valerait.ghostlink.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.logging.Level
import java.util.logging.Logger

actual fun logWarning(
    tag: String,
    message: String,
    error: Throwable?,
) {
    Logger.getLogger(tag).log(Level.WARNING, message, error)
}

@Composable
actual fun rememberCopyToClipboard(sensitive: Boolean): (String) -> Unit =
    remember { { text -> Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) } }

// У десктопа нет общего «Поделиться»: ссылку пользователь вставит сам.
@Composable
actual fun rememberShareText(title: String): (String) -> Unit = rememberCopyToClipboard()
