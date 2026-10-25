package io.github.valerait.ghostlink.feature.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.valerait.ghostlink.core.designsystem.LocalChatStyle
import io.github.valerait.ghostlink.core.domain.usecase.OutgoingPhoto
import io.github.valerait.ghostlink.core.domain.usecase.PhotoDraft
import io.github.valerait.ghostlink.core.model.Attachment
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.Message
import io.github.valerait.ghostlink.core.model.MessageStatus
import io.github.valerait.ghostlink.core.model.ReplyTo
import io.github.valerait.ghostlink.core.ui.resources.Res
import io.github.valerait.ghostlink.core.ui.resources.chat_deleted
import io.github.valerait.ghostlink.core.ui.resources.chat_photo
import io.github.valerait.ghostlink.core.ui.resources.chat_photo_discard
import io.github.valerait.ghostlink.core.ui.resources.chat_photo_not_sent
import io.github.valerait.ghostlink.core.ui.resources.chat_photo_open
import io.github.valerait.ghostlink.core.ui.resources.chat_photo_remove
import io.github.valerait.ghostlink.core.ui.resources.chat_photo_retry
import org.jetbrains.compose.resources.stringResource

// Содержимое сообщений: текст, фото в пузыре, загружающееся фото, выбранное фото над полем ввода, подписи цитат.

/** Превью фото в ленте: ширина постоянная, высота — по пропорциям фото в разумных пределах. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessagePhoto(
    chatId: ChatId,
    attachment: Attachment,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    val (width, height) = photoSize(attachment.width, attachment.height)
    ChatPhoto(
        chatId = chatId,
        key = attachment.thumbKey,
        modifier =
            Modifier
                .size(width, height)
                .clip(PHOTO_SHAPE)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .combinedClickable(
                    onClickLabel = stringResource(Res.string.chat_photo_open),
                    onClick = onOpen,
                    onLongClick = onLongPress,
                ),
    )
}

/** Фото, которое ещё загружается или не загрузилось: из локальных байтов, с повтором и отменой при ошибке. */
@Composable
internal fun OutgoingPhotoBubble(
    photo: OutgoingPhoto,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
) {
    val chatStyle = LocalChatStyle.current
    val (width, height) = photoSize(photo.draft.width, photo.draft.height)
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.End) {
        Surface(
            color = chatStyle.outgoing,
            contentColor = chatStyle.onOutgoing,
            shape = chatStyle.outgoingShape,
        ) {
            Column(Modifier.width(width + 22.dp).padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 6.dp)) {
                Box(Modifier.size(width, height).clip(PHOTO_SHAPE), contentAlignment = Alignment.Center) {
                    LocalPhoto(photo.draft.thumbnail, Modifier.fillMaxSize())
                    if (!photo.failed) CircularProgressIndicator(Modifier.size(32.dp), color = Color.White, strokeWidth = 3.dp)
                }
                if (photo.caption.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(photo.caption, style = MaterialTheme.typography.bodyLarge)
                }
                if (photo.failed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(Res.string.chat_photo_not_sent),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onDiscard) { Text(stringResource(Res.string.chat_photo_discard)) }
                        TextButton(onClick = onRetry) { Text(stringResource(Res.string.chat_photo_retry)) }
                    }
                } else {
                    Box(Modifier.align(Alignment.End)) { StatusIcon(MessageStatus.SENDING) }
                }
            }
        }
    }
}

private val PHOTO_SHAPE = RoundedCornerShape(10.dp)
private val PHOTO_WIDTH = 240.dp
private const val PHOTO_MIN_HEIGHT_DP = 120f
private const val PHOTO_MAX_HEIGHT_DP = 320f

private fun photoSize(
    width: Int,
    height: Int,
): Pair<Dp, Dp> {
    val ratio = if (width > 0 && height > 0) height.toFloat() / width else 1f
    return PHOTO_WIDTH to (PHOTO_WIDTH.value * ratio).coerceIn(PHOTO_MIN_HEIGHT_DP, PHOTO_MAX_HEIGHT_DP).dp
}

/** Цитата ответа: у фото — «📷 Фото» или «📷 подпись». */
@Composable
internal fun quotePreview(reply: ReplyTo): String =
    when {
        !reply.photo -> reply.preview
        reply.preview.isEmpty() -> "📷 " + stringResource(Res.string.chat_photo)
        else -> "📷 " + reply.preview
    }

/** Превью сообщения над полем ввода (ответ или правка): у фото — с отметкой «📷». */
@Composable
internal fun messagePreview(message: Message): String = quotePreview(ReplyTo.of(message))

/** Выбранное фото над полем ввода: уйдёт с текстом из поля как подписью. */
@Composable
internal fun AttachedPhotoBar(
    photo: PhotoDraft,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LocalPhoto(photo.thumbnail, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(Res.string.chat_photo), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.chat_photo_remove))
        }
    }
}

/** Фото и текст сообщения или отметка «Сообщение удалено». */
@Composable
internal fun MessageBody(
    message: Message,
    chatId: ChatId,
    onOpenPhoto: (Attachment) -> Unit,
    onLongPress: () -> Unit,
) {
    if (message.deleted) {
        DeletedText()
        return
    }
    val attachment = message.attachment
    if (attachment != null) MessagePhoto(chatId, attachment, onOpen = { onOpenPhoto(attachment) }, onLongPress = onLongPress)
    if (message.text.isNotEmpty()) {
        if (attachment != null) Spacer(Modifier.height(4.dp))
        Text(message.text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** На месте удалённого сообщения — курсивная отметка, как в мессенджерах. */
@Composable
private fun DeletedText() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Block, contentDescription = null, tint = mutedContentColor(), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(Res.string.chat_deleted),
            style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
            color = mutedContentColor(),
        )
    }
}
