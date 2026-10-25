package io.github.valerait.ghostlink.core.data

import io.github.valerait.ghostlink.core.domain.repository.MediaStore
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.GhostlinkError
import io.github.valerait.ghostlink.core.model.MediaKey
import io.github.valerait.ghostlink.core.platform.logWarning
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Файлы чатов в Backblaze B2. Ключей B2 у приложения нет: Worker (см. worker/README.md) по ID-токену
 * Firebase проверяет членство в чате и выдаёт подписанные ссылки, а сами байты идут напрямую в B2 и из него.
 *
 * @param workerUrl адрес Worker; null — Worker ещё не развёрнут, фото не загружаются и не скачиваются.
 * @param idToken ID-токен текущего пользователя Firebase; null — не вошли.
 */
internal class WorkerMediaStore(
    private val workerUrl: String?,
    private val idToken: suspend () -> String?,
    private val http: HttpClient,
    private val cache: MediaCache,
) : MediaStore {
    override suspend fun upload(
        chatId: ChatId,
        files: List<ByteArray>,
    ): List<MediaKey> =
        mediaCall {
            require(files.size in 1..MediaStore.MAX_FILES) { "1..${MediaStore.MAX_FILES} files expected" }
            require(files.all { it.size in 1..MediaStore.MAX_FILE_BYTES }) { "File too large" }
            val targets = callWorker<UploadRequest, UploadResponse>("upload", UploadRequest(chatId.value, files.map { it.size })).files
            check(targets.size == files.size) { "Worker returned ${targets.size} upload URLs for ${files.size} files" }
            targets.zip(files).map { (target, bytes) ->
                // Тип и размер входят в подпись ссылки; Content-Length движок ставит сам по телу.
                val type = target.headers[HttpHeaders.ContentType.lowercase()]?.let(ContentType::parse) ?: ContentType.Image.JPEG
                http.put(target.url) { setBody(ByteArrayContent(bytes, type)) }.requireSuccess("B2 upload")
                MediaKey(target.key).also { cache.put(it, bytes) }
            }
        }

    override suspend fun download(
        chatId: ChatId,
        key: MediaKey,
    ): ByteArray =
        cache.get(key) ?: mediaCall {
            val url =
                callWorker<DownloadRequest, DownloadResponse>("download", DownloadRequest(chatId.value, listOf(key.value))).urls[key.value]
                    ?: error("Worker returned no URL for $key")
            http
                .get(url)
                .requireSuccess("B2 download")
                .bodyAsBytes()
                .also { cache.put(key, it) }
        }

    private suspend inline fun <reified Request, reified Response> callWorker(
        path: String,
        body: Request,
    ): Response {
        val base = checkNotNull(workerUrl) { "Media worker URL is not set" }.trimEnd('/')
        val token = idToken() ?: throw GhostlinkError.PermissionDenied()
        val response =
            http.post("$base/media/$path") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(JSON.encodeToString(body))
            }
        return JSON.decodeFromString<Response>(response.requireSuccess("Worker $path").bodyAsText())
    }

    private suspend fun HttpResponse.requireSuccess(what: String): HttpResponse {
        if (status.isSuccess()) return this
        val detail = runCatching { bodyAsText().take(ERROR_BODY_LIMIT) }.getOrDefault("")
        val cause = IllegalStateException("$what: HTTP ${status.value} $detail")
        throw when (status) {
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> GhostlinkError.PermissionDenied(cause)
            else -> GhostlinkError.Unexpected(cause)
        }
    }

    @Suppress("TooGenericExceptionCaught") // наружу выходят только GhostlinkError, как из репозиториев Firebase
    private suspend inline fun <T> mediaCall(block: () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error =
                when (e) {
                    is GhostlinkError -> e
                    is IOException -> GhostlinkError.Offline(e)
                    else -> GhostlinkError.Unexpected(e)
                }
            if (error !is GhostlinkError.Offline) logWarning(TAG, "Media request failed", e)
            throw error
        }

    @Serializable
    private class UploadRequest(
        val chatId: String,
        val sizes: List<Int>,
    )

    @Serializable
    private class UploadTarget(
        val key: String,
        val url: String,
        val headers: Map<String, String> = emptyMap(),
    )

    @Serializable
    private class UploadResponse(
        val files: List<UploadTarget>,
    )

    @Serializable
    private class DownloadRequest(
        val chatId: String,
        val keys: List<String>,
    )

    @Serializable
    private class DownloadResponse(
        val urls: Map<String, String>,
    )

    private companion object {
        const val TAG = "Media"
        val JSON = Json { ignoreUnknownKeys = true }
        const val ERROR_BODY_LIMIT = 200
    }
}
