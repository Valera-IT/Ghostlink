package io.github.valerait.ghostlink.feature.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.valerait.ghostlink.core.domain.repository.MediaStore
import io.github.valerait.ghostlink.core.domain.usecase.PhotoDraft
import io.github.valerait.ghostlink.core.model.Attachment
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.GhostlinkError
import io.github.valerait.ghostlink.core.model.MediaKey
import io.github.valerait.ghostlink.core.platform.ImageRequest
import io.github.valerait.ghostlink.core.platform.PickedImage
import io.github.valerait.ghostlink.core.platform.fullScreenDialogProperties
import io.github.valerait.ghostlink.core.platform.logWarning
import io.github.valerait.ghostlink.core.platform.rememberImagePicker
import io.github.valerait.ghostlink.core.platform.rememberPhotoCamera
import io.github.valerait.ghostlink.core.ui.resources.Res
import io.github.valerait.ghostlink.core.ui.resources.action_cancel
import io.github.valerait.ghostlink.core.ui.resources.chat_photo_camera
import io.github.valerait.ghostlink.core.ui.resources.chat_photo_close
import io.github.valerait.ghostlink.core.ui.resources.chat_photo_gallery
import io.github.valerait.ghostlink.core.ui.resources.chat_photo_source
import io.github.valerait.ghostlink.core.ui.resources.chat_photo_unavailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Картинки фото чатов для экрана. Файлы скачивает и кэширует на диске [MediaStore], здесь — последние
 * декодированные картинки в памяти, чтобы лента не декодировала их заново при каждой прокрутке.
 * Вызывается из композиции, то есть на главном потоке, поэтому кэш без блокировок.
 */
class ChatPhotos(
    private val media: MediaStore,
) {
    private val images = LinkedHashMap<MediaKey, ImageBitmap>()

    fun cached(key: MediaKey): ImageBitmap? = images.remove(key)?.also { images[key] = it }

    /** null — файл не скачался (нет сети, исчерпан лимит хранилища) или битый. */
    suspend fun load(
        chatId: ChatId,
        key: MediaKey,
    ): ImageBitmap? =
        cached(key) ?: decode(chatId, key)?.also { image ->
            images[key] = image
            // Превью ленты и пара открытых фото; давно не нужные уходят первыми.
            while (images.size > MAX_IMAGES) images.remove(images.keys.first())
        }

    @Suppress("TooGenericExceptionCaught") // битая картинка — та же «недоступна»
    private suspend fun decode(
        chatId: ChatId,
        key: MediaKey,
    ): ImageBitmap? =
        try {
            val jpeg = media.download(chatId, key)
            withContext(Dispatchers.Default) { jpeg.decodeToImageBitmap() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GhostlinkError) {
            logWarning(TAG, "Can't load $key", e)
            null
        } catch (e: Exception) {
            logWarning(TAG, "Can't decode $key", e)
            null
        }

    private companion object {
        const val TAG = "ChatPhotos"
        const val MAX_IMAGES = 60
    }
}

/** Выбор фото для чата: полный размер и превью из одного снимка. */
@Composable
internal fun rememberPhotoPicker(
    onPicked: (PhotoDraft) -> Unit,
    onFailed: () -> Unit,
): () -> Unit {
    var choosingSource by remember { mutableStateOf(false) }
    val picked: (List<PickedImage>) -> Unit = { (photo, thumbnail) ->
        onPicked(PhotoDraft(photo.jpeg, thumbnail.jpeg, photo.width, photo.height))
    }
    val gallery = rememberImagePicker(PHOTO_REQUESTS, picked, onFailed)
    val camera = rememberPhotoCamera(PHOTO_REQUESTS, picked, onFailed)
    if (choosingSource) {
        AlertDialog(
            onDismissRequest = { choosingSource = false },
            title = { Text(stringResource(Res.string.chat_photo_source)) },
            text = {
                Column {
                    TextButton(onClick = {
                        choosingSource = false
                        gallery()
                    }) {
                        Text(stringResource(Res.string.chat_photo_gallery))
                    }
                    if (camera != null) {
                        TextButton(onClick = {
                            choosingSource = false
                            camera()
                        }) {
                            Text(stringResource(Res.string.chat_photo_camera))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { choosingSource = false }) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }
    return { if (camera == null) gallery() else choosingSource = true }
}

private val PHOTO_REQUESTS =
    listOf(
        ImageRequest(maxSidePx = Attachment.PHOTO_SIDE_PX, maxBytes = Attachment.PHOTO_MAX_BYTES),
        ImageRequest(maxSidePx = Attachment.THUMB_SIDE_PX, maxBytes = Attachment.THUMB_MAX_BYTES),
    )

/** Картинка из хранилища медиа; пока грузится — индикатор, не загрузилась — значок битой картинки. */
@Composable
internal fun ChatPhoto(
    chatId: ChatId,
    key: MediaKey,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    /** Что показать, пока [key] грузится: например, уже загруженное превью при открытии полного фото. */
    placeholder: MediaKey? = null,
) {
    val photos = koinInject<ChatPhotos>()
    var image by remember(key) { mutableStateOf(photos.cached(key)) }
    var failed by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        if (image == null) {
            image = photos.load(chatId, key)
            failed = image == null
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val shown = image ?: placeholder?.let { photos.cached(it) }
        when {
            shown != null -> Image(shown, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
            failed -> BrokenPhoto()
            else -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        if (image == null && shown != null && !failed) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

/** Картинка из байтов, которые ещё не в хранилище: выбранное или загружающееся фото. */
@Composable
internal fun LocalPhoto(
    jpeg: ByteArray,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var image by remember(jpeg) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(jpeg) {
        image = runCatching { withContext(Dispatchers.Default) { jpeg.decodeToImageBitmap() } }.getOrNull()
    }
    Box(modifier) {
        image?.let { Image(it, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
private fun BrokenPhoto() {
    Icon(
        Icons.Outlined.BrokenImage,
        contentDescription = stringResource(Res.string.chat_photo_unavailable),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Фото на весь экран: щипок увеличивает, перетаскивание двигает увеличенное. */
@Composable
internal fun PhotoViewer(
    chatId: ChatId,
    attachment: Attachment,
    onClose: () -> Unit,
) {
    ZoomablePhotoViewer(onClose) { modifier ->
        ChatPhoto(
            chatId = chatId,
            key = attachment.key,
            placeholder = attachment.thumbKey,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    }
}

@Composable
internal fun LocalPhotoViewer(
    jpeg: ByteArray,
    onClose: () -> Unit,
) {
    ZoomablePhotoViewer(onClose) { modifier -> LocalPhoto(jpeg, modifier, contentScale = ContentScale.Fit) }
}

@Composable
private fun ZoomablePhotoViewer(
    onClose: () -> Unit,
    photo: @Composable (Modifier) -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = fullScreenDialogProperties(),
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            photo(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val relative = centroid - center
                            val ratio = nextScale / scale
                            val moved = relative - (relative - offset) * ratio + pan
                            val maxX = size.width * (nextScale - 1f) / 2f
                            val maxY = size.height * (nextScale - 1f) / 2f
                            offset = Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
                            scale = nextScale
                        }
                    }.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                modifier = Modifier.safeDrawingPadding().padding(8.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.chat_photo_close))
            }
        }
    }
}

private const val MAX_ZOOM = 5f
