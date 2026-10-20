package io.github.valerait.ghostlink.ui.chat

import io.github.valerait.ghostlink.data.model.TYPING_REFRESH_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Ставит отметку «печатает…», пока идёт набор, — не чаще раза в [TYPING_REFRESH_MS]. */
class TypingNotifier(
    private val scope: CoroutineScope,
    private val setTyping: (Boolean) -> Unit,
) {
    private var refresh: Job? = null

    fun onDraftChanged(text: String) {
        if (text.isBlank()) {
            stop()
            return
        }
        if (refresh?.isActive == true) return
        setTyping(true)
        refresh = scope.launch { delay(TYPING_REFRESH_MS) }
    }

    /** Снимает отметку, если она стоит. */
    fun stop() {
        if (refresh == null) return
        onSent()
        setTyping(false)
    }

    /** Сообщение отправлено: отправка сама снимает отметку на сервере. */
    fun onSent() {
        refresh?.cancel()
        refresh = null
    }
}
