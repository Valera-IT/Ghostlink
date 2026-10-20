package io.github.valerait.ghostlink.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.valerait.ghostlink.data.model.Message
import io.github.valerait.ghostlink.data.model.MessageStatus
import io.github.valerait.ghostlink.data.model.ReplyTo
import io.github.valerait.ghostlink.data.model.User
import io.github.valerait.ghostlink.data.repository.AuthRepository
import io.github.valerait.ghostlink.data.repository.ChatRepository
import io.github.valerait.ghostlink.data.repository.UserRepository
import io.github.valerait.ghostlink.data.repository.requireUserId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

const val MAX_MESSAGE_LENGTH = 4000

data class MessageItem(
    val message: Message,
    val mine: Boolean,
    /** Статус показываем только у своих сообщений. */
    val status: MessageStatus?,
    /** Цитата в ответе — на моё сообщение (подпись «Вы» вместо имени собеседника). */
    val replyToMine: Boolean = false,
    /** Исходное сообщение цитаты удалено — вместо превью показываем «Сообщение удалено». */
    val replyToDeleted: Boolean = false,
)

sealed interface ChatRow {
    val key: String

    data class Day(
        val millis: Long,
    ) : ChatRow {
        override val key = "day-$millis"
    }

    data class Msg(
        val item: MessageItem,
    ) : ChatRow {
        override val key = item.message.id
    }
}

data class ChatUiState(
    val loading: Boolean = true,
    val partner: User? = null,
    /** Новые сообщения в начале списка — под LazyColumn(reverseLayout = true). */
    val rows: List<ChatRow> = emptyList(),
    /** Сообщение, на которое пользователь сейчас отвечает (плашка над полем ввода). */
    val replyingTo: MessageItem? = null,
    /** Своё сообщение, которое пользователь сейчас исправляет; взаимоисключается с [replyingTo]. */
    val editing: MessageItem? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        auth: AuthRepository,
        users: UserRepository,
        private val chats: ChatRepository,
    ) : ViewModel() {
        // Аргумент типобезопасного маршрута ChatRoute(chatId) лежит в SavedStateHandle под именем поля.
        private val chatId: String = checkNotNull(savedStateHandle["chatId"])
        private val me = auth.requireUserId()
        private val zone = ZoneId.systemDefault()
        private val visible = MutableStateFlow(false)
        private val replyingToId = MutableStateFlow<String?>(null)
        private val editingId = MutableStateFlow<String?>(null)

        private val chat =
            chats
                .observeChat(chatId)
                .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

        // Сообщения до удаления чата из списка (ChatPrefs.clearedAt) у этого пользователя больше не показываем.
        private val messages =
            combine(
                chats.observeMessages(chatId),
                chats.observeChatPrefs(me).map { it[chatId] }.distinctUntilChanged(),
            ) { messages, prefs ->
                if (prefs?.clearedAt == null) messages else messages.filterNot { prefs.isCleared(it.sentAt) }
            }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
        private val partner =
            chat
                .mapNotNull { it?.partnerId(me) }
                .distinctUntilChanged()
                .flatMapLatest { users.observeUser(it) }
                .onStart { emit(null) }

        val state: StateFlow<ChatUiState> =
            combine(chat, messages, partner, replyingToId, editingId) { chat, messages, partner, replyingToId, editingId ->
                val partnerRead = chat?.lastRead?.get(chat.partnerId(me))
                val deletedIds = messages.filter { it.deleted }.mapTo(HashSet()) { it.id }
                val items =
                    messages.map { message ->
                        val mine = message.senderId == me
                        MessageItem(
                            message = message,
                            mine = mine,
                            status = if (mine) message.status(partnerRead) else null,
                            replyToMine = message.replyTo?.senderId == me,
                            replyToDeleted = message.replyTo?.id in deletedIds,
                        )
                    }
                ChatUiState(
                    loading = false,
                    partner = partner,
                    rows = buildRows(items),
                    replyingTo = replyingToId?.let { id -> items.firstOrNull { it.message.id == id && !it.message.deleted } },
                    editing = editingId?.let { id -> items.firstOrNull { it.message.id == id && it.mine && !it.message.deleted } },
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

        init {
            // Отмечаем прочитанным, только пока чат реально на экране и пришло что-то новое.
            viewModelScope.launch {
                combine(visible, chat, messages) { visible, chat, messages ->
                    if (!visible || chat == null) return@combine null
                    val lastIncoming =
                        messages.lastOrNull { it.senderId != me && !it.pending }?.sentAt
                            ?: return@combine null
                    lastIncoming.takeIf { it > (chat.lastRead[me] ?: 0L) }
                }.filterNotNull()
                    .distinctUntilChanged()
                    .collect { chats.markRead(chatId, me) }
            }
        }

        fun onVisibilityChanged(isVisible: Boolean) {
            visible.value = isVisible
        }

        fun startReply(messageId: String) {
            editingId.value = null
            replyingToId.value = messageId
        }

        fun cancelReply() {
            replyingToId.value = null
        }

        fun startEdit(messageId: String) {
            replyingToId.value = null
            editingId.value = messageId
        }

        fun cancelEdit() {
            editingId.value = null
        }

        fun delete(messageId: String) {
            val list = messages.replayCache.firstOrNull() ?: return
            val index = list.indexOfFirst { it.id == messageId }
            if (index < 0 || list[index].senderId != me || list[index].deleted) return
            // Превью в списке чатов показывает последнее неудалённое сообщение.
            chats.deleteMessage(
                chatId,
                messageId,
                isLatest = list.isLatestVisible(index),
                previous = list.subList(0, index).lastOrNull { !it.deleted },
            )
            if (replyingToId.value == messageId) replyingToId.value = null
            if (editingId.value == messageId) editingId.value = null
        }

        /** Отправляет новое сообщение или, в режиме правки, сохраняет исправленный текст. */
        fun send(text: String) {
            val trimmed = text.trim().take(MAX_MESSAGE_LENGTH)
            if (trimmed.isEmpty()) return
            val editing = state.value.editing
            if (editing != null) {
                saveEdit(editing.message, trimmed)
            } else {
                // Отвечать можно только на сообщение, уже записанное на сервер: у pending ещё нет стабильного места в чате.
                val replyTo =
                    state.value.replyingTo
                        ?.message
                        ?.takeUnless { it.pending }
                        ?.let(ReplyTo::of)
                chats.sendMessage(chatId, me, trimmed, replyTo)
                replyingToId.value = null
            }
        }

        private fun saveEdit(
            message: Message,
            text: String,
        ) {
            editingId.value = null
            if (text == message.text) return
            val list = messages.replayCache.firstOrNull().orEmpty()
            val isLatest = list.isLatestVisible(list.indexOfFirst { it.id == message.id })
            chats.editMessage(chatId, message.id, text, isLatest)
        }

        /** После сообщения с номером [index] остались только удалённые — значит, оно сейчас в превью чата. */
        private fun List<Message>.isLatestVisible(index: Int): Boolean = index >= 0 && subList(index + 1, size).all { it.deleted }

        private fun buildRows(items: List<MessageItem>): List<ChatRow> {
            val rows = ArrayList<ChatRow>(items.size + 8)
            var lastDay: Long? = null
            for (item in items) {
                val sentAt = item.message.sentAt
                if (sentAt != null) {
                    val day =
                        Instant
                            .ofEpochMilli(sentAt)
                            .atZone(zone)
                            .toLocalDate()
                            .toEpochDay()
                    if (day != lastDay) {
                        rows += ChatRow.Day(sentAt)
                        lastDay = day
                    }
                }
                rows += ChatRow.Msg(item)
            }
            return rows.asReversed()
        }
    }
