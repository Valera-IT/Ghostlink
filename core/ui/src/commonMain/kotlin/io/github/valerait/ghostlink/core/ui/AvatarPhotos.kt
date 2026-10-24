package io.github.valerait.ghostlink.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import io.github.valerait.ghostlink.core.platform.logWarning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Фото профилей для [Avatar]. Приложение передаёт загрузку JPEG из UserRepository: фичи и core:ui не видят domain.
 * Готовые картинки живут в памяти до конца процесса, а сами JPEG на диске кэширует Firestore.
 * Вызывается из композиции, то есть на главном потоке, поэтому кэш без блокировок.
 */
class AvatarPhotos(
    private val load: suspend (userId: String, version: Long) -> ByteArray?,
) {
    private val images = LinkedHashMap<String, ImageBitmap>()

    fun cached(
        userId: String,
        version: Long,
    ): ImageBitmap? = images[key(userId, version)]

    /** Фото версии [version]; null — его нет, сеть недоступна или картинка битая: тогда остаются инициалы. */
    suspend fun photo(
        userId: String,
        version: Long,
    ): ImageBitmap? =
        cached(userId, version) ?: download(userId, version)?.also { image ->
            images[key(userId, version)] = image
            // Хватит на список чатов с запасом; старые версии и давно не нужные фото уходят первыми.
            while (images.size > MAX_IMAGES) images.remove(images.keys.first())
        }

    @Suppress("TooGenericExceptionCaught") // любая ошибка — просто нет фото, остаются инициалы
    private suspend fun download(
        userId: String,
        version: Long,
    ): ImageBitmap? =
        try {
            load(userId, version)?.let { jpeg -> withContext(Dispatchers.Default) { jpeg.decodeToImageBitmap() } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logWarning("Avatars", "Can't load avatar of $userId", e)
            null
        }

    private fun key(
        userId: String,
        version: Long,
    ) = "$userId@$version"

    private companion object {
        const val MAX_IMAGES = 200
    }
}

/** Без него [Avatar] показывает только инициалы: превью, тесты, экраны до входа. */
val LocalAvatarPhotos = staticCompositionLocalOf<AvatarPhotos?> { null }
