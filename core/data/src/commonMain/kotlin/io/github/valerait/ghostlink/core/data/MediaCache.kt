package io.github.valerait.ghostlink.core.data

import io.github.valerait.ghostlink.core.model.MediaKey
import io.github.valerait.ghostlink.core.platform.logWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/**
 * Файлы медиа на диске: лимит B2 на скачивания в сутки теснее лимита трафика, поэтому однажды скачанное не качаем снова.
 * Когда кэш больше [maxBytes], первыми уходят самые старые фото: имя файла в ключе начинается со времени загрузки.
 * Ошибки диска не роняют приложение: файл просто скачается заново.
 */
internal class MediaCache(
    private val dir: Path,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    private val mutex = Mutex()

    /** Размер кэша; считается при первом обращении. */
    private var totalBytes: Long? = null

    suspend fun get(key: MediaKey): ByteArray? =
        io("read $key") {
            val path = pathOf(key)
            if (SystemFileSystem.exists(path)) SystemFileSystem.source(path).buffered().use { it.readByteArray() } else null
        }

    suspend fun put(
        key: MediaKey,
        bytes: ByteArray,
    ) {
        io("write $key") {
            SystemFileSystem.createDirectories(dir)
            val path = pathOf(key)
            if (SystemFileSystem.exists(path)) return@io
            val before = totalBytes ?: measure()
            // Сначала во временный файл: оборванная запись не оставит битое фото.
            val temp = Path(dir, "${path.name}.tmp")
            SystemFileSystem.sink(temp).buffered().use { it.write(bytes) }
            SystemFileSystem.atomicMove(temp, path)
            val total = before + bytes.size
            totalBytes = if (total > maxBytes) trim() else total
        }
    }

    private fun measure(): Long = files().sumOf { size(it) }

    /** Удаляет старые файлы, пока кэш не станет вдвое меньше лимита: не чистить его на каждой записи. */
    private fun trim(): Long {
        val files = files().sortedBy { uploadTime(it.name) }
        var total = files.sumOf { size(it) }
        for (file in files) {
            if (total <= maxBytes / 2) break
            total -= size(file)
            SystemFileSystem.delete(file, mustExist = false)
        }
        return total
    }

    private fun files(): List<Path> = if (SystemFileSystem.exists(dir)) SystemFileSystem.list(dir).toList() else emptyList()

    private fun size(path: Path): Long = SystemFileSystem.metadataOrNull(path)?.size ?: 0

    // Ключ состоит из латиницы, цифр, «-», «_», «.» и «/»; «~» в нём нет, поэтому имя файла однозначно.
    private fun pathOf(key: MediaKey) = Path(dir, key.value.replace('/', '~'))

    private suspend fun <T> io(
        what: String,
        block: () -> T,
    ): T? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    block()
                } catch (e: IOException) {
                    logWarning(TAG, "Can't $what", e)
                    totalBytes = null
                    null
                }
            }
        }

    private companion object {
        const val TAG = "MediaCache"
        const val DEFAULT_MAX_BYTES = 200L * 1024 * 1024

        /** Время из имени `{время в base36}-{случайное}.jpg`; файлы другого вида считаются самыми старыми. */
        fun uploadTime(name: String): Long = name.substringAfterLast('~').substringBefore('-').toLongOrNull(radix = 36) ?: 0
    }
}
