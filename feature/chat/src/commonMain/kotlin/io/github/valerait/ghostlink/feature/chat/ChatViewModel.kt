package io.github.valerait.ghostlink.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.valerait.ghostlink.core.domain.repository.AuthRepository
import io.github.valerait.ghostlink.core.domain.repository.ChatRepository
import io.github.valerait.ghostlink.core.domain.repository.UserRepository
import io.github.valerait.ghostlink.core.domain.repository.requireUserId
import io.github.valerait.ghostlink.core.domain.usecase.DeleteMessageUseCase
import io.github.valerait.ghostlink.core.domain.usecase.EditMessageUseCase
import io.github.valerait.ghostlink.core.domain.usecase.ObserveChatMessagesUseCase
import io.github.valerait.ghostlink.core.domain.usecase.OutgoingPhoto
import io.github.valerait.ghostlink.core.domain.usecase.PhotoDraft
import io.github.valerait.ghostlink.core.domain.usecase.PhotoSender
import io.github.valerait.ghostlink.core.domain.usecase.SendMessageUseCase
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.DisappearingTimer
import io.github.valerait.ghostlink.core.model.GroupInfo
import io.github.valerait.ghostlink.core.model.Message
import io.github.valerait.ghostlink.core.model.MessageId
import io.github.valerait.ghostlink.core.model.MessageStatus
import io.github.valerait.ghostlink.core.model.ReplyTo
import io.github.valerait.ghostlink.core.model.User
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.core.ui.LocalDay
import io.github.valerait.ghostlink.core.ui.localDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

data class MessageItem(
    val message: Message,
    val mine: Boolean,
    /** Статус показываем только у своих сообщений. */
    val status: MessageStatus?,
    /** Цитата в ответе — на моё сообщение (подпись «Вы» вместо имени собеседника). */
    val replyToMine: Boolean = false,
    /** Исходное сообщение цитаты удалено — вместо превью показываем «Сообщение удалено». */
    val replyToDeleted: Boolean = false,
    /** Имя автора над чужим сообщением в группе; в личном чате null. */
    val senderName: String? = null,
)

sealed interface ChatRow {
    val key: String

    data class Day(
        val time: Instant,
    ) : ChatRow {
        override val key = "day-${time.toEpochMilliseconds()}"
    }

    data class Msg(
        val item: MessageItem,
    ) : ChatRow {
        override val key = item.message.id.value
    }

    /** Фото, которое ещё загружается или не загрузилось: всегда новее сообщений чата. */
    data class Outgoing(
        val photo: OutgoingPhoto,
    ) : ChatRow {
        override val key = photo.id
    }
}

data class ChatUiState(
    val loading: Boolean = true,
    val chatId: ChatId = ChatId(""),
    /** Собеседник в личном чате; в группе null. */
    val partner: User? = null,
    /** Группа: название и админ; в личном чате null. */
    val group: GroupInfo? = null,
    val memberCount: Int = 0,
    /** userId -> имя для всех, кого видно в чате, кроме меня. */
    val names: Map<UserId, String> = emptyMap(),
    /** Кто печатает в группе (имя); в личном чате null — там достаточно [partnerTypingUntil]. */
    val typingName: String? = null,
    /** Новые сообщения в начале списка — под LazyColumn(reverseLayout = true). */
    val rows: List<ChatRow> = emptyList(),
    /** Сообщение, на которое пользователь сейчас отвечает (плашка над полем ввода). */
    val replyingTo: MessageItem? = null,
    /** Своё сообщение, которое пользователь сейчас исправляет; взаимоисключается с [replyingTo]. */
    val editing: MessageItem? = null,
    /** До какого момента кто-то из собеседников «печатает…»; null — никто. */
    val partnerTypingUntil: Instant? = null,
    /** Таймер исчезающих сообщений чата; null — выключен. */
    val disappearing: DisappearingTimer? = null,
    /** Фото, выбранное для отправки (плашка над полем ввода); уйдёт вместе с текстом как подписью. */
    val attachedPhoto: PhotoDraft? = null,
)

sealed interface ChatAction {
    /** Чат на экране или ушёл с него: «прочитано» ставим, только пока его видно. */
    data class VisibilityChanged(
        val visible: Boolean,
    ) : ChatAction

    /** Текст в поле ввода изменился. Правка своего сообщения — тоже набор. */
    data class DraftChanged(
        val text: String,
    ) : ChatAction

    /** Отправить новое сообщение или, в режиме правки, сохранить исправленный текст. */
    data class Send(
        val text: String,
    ) : ChatAction

    /** Таймер общий для чата: собеседник увидит его и его сообщения тоже начнут исчезать. */
    data class SetDisappearing(
        val timer: DisappearingTimer?,
    ) : ChatAction

    data class StartReply(
        val messageId: MessageId,
    ) : ChatAction

    data object CancelReply : ChatAction

    data class StartEdit(
        val messageId: MessageId,
    ) : ChatAction

    data object CancelEdit : ChatAction

    data class Delete(
        val messageId: MessageId,
    ) : ChatAction

    /** Выбрано фото: оно ждёт над полем ввода и уйдёт по кнопке «Отправить». */
    data class AttachPhoto(
        val draft: PhotoDraft,
    ) : ChatAction

    data object RemoveAttachedPhoto : ChatAction

    data class RetryPhoto(
        val id: String,
    ) : ChatAction

    data class DiscardPhoto(
        val id: String,
    ) : ChatAction
}

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList") // экран переписки собирает все сценарии чата: текст, правку, удаление, фото
class ChatViewModel(
    savedStateHandle: SavedStateHandle,
    auth: AuthRepository,
    users: UserRepository,
    observeMessages: ObserveChatMessagesUseCase,
    private val chats: ChatRepository,
    private val sendMessage: SendMessageUseCase,
    private val editMessage: EditMessageUseCase,
    private val deleteMessage: DeleteMessageUseCase,
    private val photos: PhotoSender,
) : ViewModel() {
    // Аргумент типобезопасного маршрута ChatRoute(chatId) лежит в SavedStateHandle под именем поля.
    private val chatId = ChatId(checkNotNull(savedStateHandle["chatId"]))
    private val me = auth.requireUserId()
    private val visible = MutableStateFlow(false)
    private val replyingToId = MutableStateFlow<MessageId?>(null)
    private val editingId = MutableStateFlow<MessageId?>(null)
    private val attachedPhoto = MutableStateFlow<PhotoDraft?>(null)
    private val typing = TypingNotifier(viewModelScope) { chats.setTyping(chatId, me, it) }

    private val chat =
        chats
            .observeChat(chatId)
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val messages =
        observeMessages(chatId, me).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    // Собеседник в личном чате или все участники группы (и бывшие — их имена нужны у старых сообщений).
    private val people =
        combine(chat.mapNotNull { it?.members }, messages) { members, messages ->
            (members + messages.map { it.senderId }).filter { it != me }.distinct().sortedBy { it.value }
        }.distinctUntilChanged()
            .flatMapLatest { users.observeUsers(it) }
            .onStart { emit(emptyMap()) }

    // Всё, что ещё не стало сообщением: выбранное фото и фото в загрузке.
    private val drafts = combine(attachedPhoto, photos.outgoing(chatId)) { attached, outgoing -> attached to outgoing }

    private val selection = combine(replyingToId, editingId) { replyingTo, editing -> replyingTo to editing }

    val state: StateFlow<ChatUiState> =
        combine(chat, messages, people, selection, drafts) { chat, messages, people, (replyingToId, editingId), (attached, outgoing) ->
            val readUpTo = chat?.othersReadUpTo(me)
            val allReadUpTo = chat?.allReadUpTo(me)
            val isGroup = chat?.isGroup == true
            val deletedIds = messages.filter { it.deleted }.mapTo(HashSet()) { it.id }
            val items =
                messages.map { message ->
                    val mine = message.senderId == me
                    MessageItem(
                        message = message,
                        mine = mine,
                        status = if (mine) message.status(readUpTo, allReadUpTo) else null,
                        replyToMine = message.replyTo?.senderId == me,
                        replyToDeleted = message.replyTo?.id in deletedIds,
                        senderName = if (isGroup && !mine) people[message.senderId]?.name else null,
                    )
                }
            val typing = chat?.typingOther(me)
            ChatUiState(
                loading = false,
                chatId = chatId,
                partner = if (isGroup) null else chat?.let { people[it.partnerId(me)] },
                group = chat?.group,
                memberCount = chat?.members?.size ?: 0,
                names = people.mapValues { it.value.name },
                typingName = if (isGroup) typing?.let { people[it.first]?.name } else null,
                rows = outgoing.asReversed().map { ChatRow.Outgoing(it) } + buildRows(items),
                replyingTo = replyingToId?.let { id -> items.firstOrNull { it.message.id == id && !it.message.deleted } },
                editing = editingId?.let { id -> items.firstOrNull { it.message.id == id && it.mine && !it.message.deleted } },
                partnerTypingUntil = typing?.second,
                disappearing = chat?.disappearing,
                attachedPhoto = attached,
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
                lastIncoming.takeIf { it > (chat.lastRead[me] ?: Instant.DISTANT_PAST) }
            }.filterNotNull()
                .distinctUntilChanged()
                .collect { chats.markRead(chatId, me) }
        }
    }

    fun onAction(action: ChatAction) {
        when (action) {
            is ChatAction.VisibilityChanged -> {
                onVisibilityChanged(action.visible)
            }

            is ChatAction.DraftChanged -> {
                typing.onDraftChanged(action.text)
            }

            is ChatAction.Send -> {
                send(action.text)
            }

            is ChatAction.SetDisappearing -> {
                chats.setDisappearing(chatId, action.timer)
            }

            is ChatAction.StartReply -> {
                editingId.value = null
                replyingToId.value = action.messageId
            }

            ChatAction.CancelReply -> {
                replyingToId.value = null
            }

            is ChatAction.StartEdit -> {
                replyingToId.value = null
                attachedPhoto.value = null
                editingId.value = action.messageId
            }

            ChatAction.CancelEdit -> {
                editingId.value = null
            }

            is ChatAction.Delete -> {
                delete(action.messageId)
            }

            is ChatAction.AttachPhoto -> {
                editingId.value = null
                attachedPhoto.value = action.draft
            }

            ChatAction.RemoveAttachedPhoto -> {
                attachedPhoto.value = null
            }

            is ChatAction.RetryPhoto -> {
                photos.retry(action.id)
            }

            is ChatAction.DiscardPhoto -> {
                photos.discard(action.id)
            }
        }
    }

    private fun onVisibilityChanged(isVisible: Boolean) {
        visible.value = isVisible
        if (!isVisible) typing.stop()
    }

    private fun delete(messageId: MessageId) {
        val list = messages.replayCache.firstOrNull() ?: return
        if (!deleteMessage(chatId, me, list, messageId)) return
        if (replyingToId.value == messageId) replyingToId.value = null
        if (editingId.value == messageId) editingId.value = null
    }

    private fun send(text: String) {
        val editing = state.value.editing
        if (editing != null) {
            if (text.isBlank()) return
            editingId.value = null
            typing.stop()
            editMessage(chatId, messages.replayCache.firstOrNull().orEmpty(), editing.message, text)
        } else {
            val timer = chat.replayCache.firstOrNull()?.disappearing
            val replyTo = state.value.replyingTo?.message
            val photo = attachedPhoto.value
            if (photo != null) {
                photos.send(chatId, me, photo, caption = text, replyTo = replyTo, timer = timer)
                attachedPhoto.value = null
            } else if (!sendMessage(chatId, me, text, replyTo, timer)) {
                return
            }
            typing.onSent()
            replyingToId.value = null
        }
    }
}

/** Сообщения с разделителями дней, новые первыми. */
private fun buildRows(items: List<MessageItem>): List<ChatRow> {
    val rows = ArrayList<ChatRow>(items.size)
    var lastDay: LocalDay? = null
    for (item in items) {
        val sentAt = item.message.sentAt
        if (sentAt != null) {
            val day = sentAt.localDay()
            if (day != lastDay) {
                rows += ChatRow.Day(sentAt)
                lastDay = day
            }
        }
        rows += ChatRow.Msg(item)
    }
    return rows.asReversed()
}
