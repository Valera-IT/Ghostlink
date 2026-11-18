package io.github.valerait.ghostlink.core.platform

import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.InviteLink
import io.github.valerait.ghostlink.core.model.UserId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ссылки ghostlink://, которыми открыли приложение. Платформа передаёт их в [open]
 * (Android — из Intent, iOS — из onOpenURL), корневой экран забирает приглашение из [pendingInvite].
 * Приглашение может прийти до онбординга — тогда оно ждёт, пока экран его обработает.
 *
 * ghostlink://chat/<chatId> — нажатое уведомление о сообщении: корневой экран открывает чат из [pendingChat].
 */
class DeepLinks {
    private val _pendingInvite = MutableStateFlow<UserId?>(null)
    val pendingInvite: StateFlow<UserId?> = _pendingInvite.asStateFlow()

    private val _pendingChat = MutableStateFlow<ChatId?>(null)
    val pendingChat: StateFlow<ChatId?> = _pendingChat.asStateFlow()

    fun open(url: String?) {
        InviteLink.parse(url)?.let { _pendingInvite.value = it }
        parseChatLink(url)?.let { _pendingChat.value = it }
    }

    fun inviteHandled() {
        _pendingInvite.value = null
    }

    fun chatHandled() {
        _pendingChat.value = null
    }

    companion object {
        private const val CHAT_PREFIX = "ghostlink://chat/"
        private val CHAT_ID = Regex("[A-Za-z0-9_-]{1,128}")

        /** Ссылка, которую открывает уведомление о сообщении в чате [chatId]. */
        fun chatLink(chatId: ChatId): String = CHAT_PREFIX + chatId.value

        internal fun parseChatLink(url: String?): ChatId? =
            url
                ?.removePrefix(CHAT_PREFIX)
                ?.takeIf { it != url && CHAT_ID.matches(it) }
                ?.let(::ChatId)
    }
}
