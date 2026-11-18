package io.github.valerait.ghostlink.core.domain.usecase

import io.github.valerait.ghostlink.core.domain.repository.AuthRepository
import io.github.valerait.ghostlink.core.domain.repository.ChatRepository
import io.github.valerait.ghostlink.core.model.ChatId
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Уведомление о чате, где появилось новое сообщение. */
data class NewMessageNotice(
    val chatId: ChatId,
    /** Имя собеседника или название группы; null — профиль не загрузился. */
    val title: String?,
    /** Автор в группе; в личном чате null. */
    val senderName: String?,
    /** Текст или подпись к фото. */
    val text: String,
    val photo: Boolean,
    val sentAt: Instant,
)

/**
 * Новые сообщения, пока приложение не на экране (фоновое обновление iOS, этап 6.12): непрочитанные чаты
 * не без звука, где последнее сообщение чужое и пришло позже [since]. Чаты берутся с сервера, а не из кэша:
 * в фоне слушатели Firestore не работали, и кэш отстал.
 */
class CheckNewMessagesUseCase(
    private val auth: AuthRepository,
    private val chats: ChatRepository,
    private val chatList: ObserveChatListUseCase,
) {
    suspend operator fun invoke(since: Instant): List<NewMessageNotice> {
        // После холодного запуска в фоне аккаунт восстанавливается из хранилища не сразу.
        val account = withTimeoutOrNull(ACCOUNT_TIMEOUT) { auth.account.filterNotNull().first() }
        if (account == null || account.isAnonymous) return emptyList()
        val me = account.id
        chats.refreshChats(me)
        // Имена собеседников могут ещё грузиться; без них уведомление всё равно лучше, чем ничего.
        val summaries =
            withTimeoutOrNull(NAMES_TIMEOUT) { chatList(me).first { list -> list.all { it.title != null } } }
                ?: chatList(me).first()
        return summaries
            .filter { it.unread && !it.muted && !it.lastMessageIsMine }
            .mapNotNull { summary ->
                val last = summary.lastMessage
                val sentAt = last?.sentAt?.takeIf { it > since } ?: return@mapNotNull null
                NewMessageNotice(summary.chat.id, summary.title, summary.lastSenderName, last.text, last.photo, sentAt)
            }
    }

    private companion object {
        val ACCOUNT_TIMEOUT = 5.seconds
        val NAMES_TIMEOUT = 5.seconds
    }
}
