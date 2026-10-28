package io.github.valerait.ghostlink.core.data

import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.MessageId
import io.github.valerait.ghostlink.core.platform.logWarning
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Просьба разбудить получателей нового сообщения; зовётся, когда сервер уже принял сообщение. */
internal fun interface MessagePush {
    suspend fun sent(
        chatId: ChatId,
        messageId: MessageId,
    )
}

/**
 * Push через Worker (`POST /push`, см. worker/README.md): Worker сам находит устройства получателей и шлёт FCM.
 * Ошибки только пишутся в лог: сообщение уже отправлено, а без push собеседник увидит его, открыв приложение.
 *
 * @param workerUrl адрес Worker; null — push не шлём (эмулятор, Worker не развёрнут).
 */
internal class WorkerPush(
    private val workerUrl: String?,
    private val idToken: suspend () -> String?,
    private val http: HttpClient,
) : MessagePush {
    @Suppress("TooGenericExceptionCaught") // push — необязательная часть отправки, его ошибка не должна её ронять
    override suspend fun sent(
        chatId: ChatId,
        messageId: MessageId,
    ) {
        val base = workerUrl?.trimEnd('/') ?: return
        try {
            val token = idToken() ?: return
            val response =
                http.post("$base/push") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(JSON.encodeToString(PushRequest(chatId.value, messageId.value)))
                }
            if (!response.status.isSuccess()) {
                logWarning(TAG, "Push for $messageId: HTTP ${response.status.value} ${response.bodyAsText().take(ERROR_BODY_LIMIT)}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            logWarning(TAG, "Push for $messageId: offline", e)
        } catch (e: Exception) {
            logWarning(TAG, "Push for $messageId failed", e)
        }
    }

    @Serializable
    private class PushRequest(
        val chatId: String,
        val messageId: String,
    )

    private companion object {
        const val TAG = "Push"
        val JSON = Json
        const val ERROR_BODY_LIMIT = 200
    }
}
