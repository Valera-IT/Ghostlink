package io.github.valerait.ghostlink.core.ui

import androidx.compose.runtime.Composable

// То, что каждая платформа делает по-своему. Камера и диплинки переедут в core:platform (3.10).

/** Предупреждение в системный лог: Logcat, java.util.logging, NSLog. */
expect fun logWarning(
    tag: String,
    message: String,
    error: Throwable? = null,
)

/** Копирует текст в буфер обмена. [sensitive] — секрет: система не покажет его в превью буфера. */
@Composable
expect fun rememberCopyToClipboard(sensitive: Boolean = false): (String) -> Unit

/** Отправляет текст через системное «Поделиться»; где его нет — кладёт в буфер обмена. */
@Composable
expect fun rememberShareText(title: String): (String) -> Unit
