package io.github.valerait.ghostlink.core.domain.usecase

import io.github.valerait.ghostlink.core.domain.repository.ChatRepository
import io.github.valerait.ghostlink.core.domain.repository.MediaStore
import io.github.valerait.ghostlink.core.model.Attachment
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.DisappearingTimer
import io.github.valerait.ghostlink.core.model.GhostlinkError
import io.github.valerait.ghostlink.core.model.Message
import io.github.valerait.ghostlink.core.model.ReplyTo
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.core.model.normalizeMessageText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Выбранное фото: полный размер и превью (оба JPEG, см. [Attachment]) и размер полного в пикселях. */
class PhotoDraft(
    val photo: ByteArray,
    val thumbnail: ByteArray,
    val width: Int,
    val height: Int,
)

/** Фото, которое ещё загружается ([failed] = false) или не загрузилось. Сообщение с ним появится после загрузки. */
data class OutgoingPhoto(
    val id: String,
    val chatId: ChatId,
    val draft: PhotoDraft,
    val caption: String,
    val failed: Boolean = false,
)

/**
 * Отправка фото: сначала файлы в [MediaStore], потом сообщение с их ключами. Загрузка идёт в [scope] процесса,
 * поэтому не обрывается, если уйти из чата. Пока она идёт или если не удалась, фото видно в [outgoing]:
 * неудачное можно отправить снова или убрать.
 */
class PhotoSender(
    private val media: MediaStore,
    private val chats: ChatRepository,
    private val scope: CoroutineScope,
) {
    private class Pending(
        val photo: OutgoingPhoto,
        val sender: UserId,
        val replyTo: ReplyTo?,
        val timer: DisappearingTimer?,
    )

    private val pending = MutableStateFlow<List<Pending>>(emptyList())

    // Фото отправляют с экрана, то есть с главного потока: счётчику блокировки не нужны.
    private var nextId = 0

    /** Неотправленные фото чата в порядке отправки. */
    fun outgoing(chatId: ChatId): Flow<List<OutgoingPhoto>> =
        pending.map { list -> list.filter { it.photo.chatId == chatId }.map { it.photo } }.distinctUntilChanged()

    /**
     * [caption] — подпись (может быть пустой), [replyTo] и [timer] — как у текстового сообщения в [SendMessageUseCase].
     */
    @Suppress("LongParameterList") // по параметру на поле сообщения
    fun send(
        chatId: ChatId,
        sender: UserId,
        draft: PhotoDraft,
        caption: String,
        replyTo: Message? = null,
        timer: DisappearingTimer? = null,
    ) {
        val photo = OutgoingPhoto("photo-${nextId++}", chatId, draft, normalizeMessageText(caption).orEmpty())
        val reply = replyTo?.takeUnless { it.pending }?.let(ReplyTo::of)
        val item = Pending(photo, sender, reply, timer)
        pending.update { it + item }
        upload(item)
    }

    fun retry(id: String) {
        val item = pending.value.firstOrNull { it.photo.id == id && it.photo.failed } ?: return
        setFailed(id, false)
        upload(item)
    }

    fun discard(id: String) {
        pending.update { list -> list.filterNot { it.photo.id == id && it.photo.failed } }
    }

    private fun upload(item: Pending) {
        val photo = item.photo
        scope.launch {
            val keys =
                try {
                    media.upload(photo.chatId, listOf(photo.draft.photo, photo.draft.thumbnail))
                } catch (_: GhostlinkError) {
                    // Причину уже записал MediaStore; пользователь видит «Не отправлено» и кнопку повтора.
                    setFailed(photo.id, true)
                    return@launch
                }
            val attachment = Attachment(keys[0], keys[1], photo.draft.width, photo.draft.height, photo.draft.photo.size)
            chats.sendMessage(photo.chatId, item.sender, photo.caption, item.replyTo, item.timer?.duration, attachment)
            pending.update { list -> list.filterNot { it.photo.id == photo.id } }
        }
    }

    private fun setFailed(
        id: String,
        failed: Boolean,
    ) {
        pending.update { list ->
            list.map { if (it.photo.id == id) Pending(it.photo.copy(failed = failed), it.sender, it.replyTo, it.timer) else it }
        }
    }
}
