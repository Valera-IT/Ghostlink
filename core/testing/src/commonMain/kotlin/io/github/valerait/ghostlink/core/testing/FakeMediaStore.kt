package io.github.valerait.ghostlink.core.testing

import io.github.valerait.ghostlink.core.domain.repository.MediaStore
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.GhostlinkError
import io.github.valerait.ghostlink.core.model.MediaKey
import kotlinx.coroutines.CompletableDeferred

/**
 * Хранилище медиа в памяти. [failure] — чем падать вместо загрузки и скачивания (нет сети и т. п.);
 * [gate] — если задан, загрузка ждёт его: так тест видит фото «в процессе отправки».
 */
class FakeMediaStore : MediaStore {
    private val files = mutableMapOf<MediaKey, ByteArray>()
    private var counter = 0

    var failure: GhostlinkError? = null
    var gate: CompletableDeferred<Unit>? = null

    /** Сколько раз файлы скачивали и загружали: проверка кэша и повторов. */
    var downloads = 0
        private set
    var uploads = 0
        private set

    override suspend fun upload(
        chatId: ChatId,
        files: List<ByteArray>,
    ): List<MediaKey> {
        require(files.size in 1..MediaStore.MAX_FILES && files.all { it.size in 1..MediaStore.MAX_FILE_BYTES })
        gate?.await()
        failure?.let { throw it }
        uploads++
        return files.map { bytes -> MediaKey("fake/${chatId.value}/${++counter}.jpg").also { this.files[it] = bytes } }
    }

    override suspend fun download(
        chatId: ChatId,
        key: MediaKey,
    ): ByteArray {
        failure?.let { throw it }
        downloads++
        if (!key.value.startsWith("fake/${chatId.value}/")) throw GhostlinkError.PermissionDenied()
        return files[key] ?: throw GhostlinkError.Unexpected(NoSuchElementException("No file $key"))
    }

    /** Кладёт файл, как будто его загрузил другой участник чата. */
    fun put(
        chatId: ChatId,
        bytes: ByteArray,
    ): MediaKey = MediaKey("fake/${chatId.value}/${++counter}.jpg").also { files[it] = bytes }
}
