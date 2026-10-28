package io.github.valerait.ghostlink.core.data

import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.MessageId
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkerPushTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var status = HttpStatusCode.OK
    private var offline = false

    private val http =
        HttpClient(
            MockEngine { request ->
                requests += request
                if (offline) throw IOException("no network")
                if (status == HttpStatusCode.OK) respond("""{"sent":1}""") else respondError(status)
            },
        )

    private fun push(
        url: String? = "https://worker.example/",
        token: String? = "token",
    ) = WorkerPush(url, { token }, http)

    @Test
    fun `asks the worker to push the sent message`() =
        runTest {
            push().sent(ChatId("alice_bob"), MessageId("m1"))

            val request = requests.single()
            assertEquals("https://worker.example/push", request.url.toString())
            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
            assertEquals("""{"chatId":"alice_bob","messageId":"m1"}""", request.body.toByteArray().decodeToString())
        }

    @Test
    fun `worker errors and lost network do not escape`() =
        runTest {
            status = HttpStatusCode.ServiceUnavailable
            push().sent(ChatId("alice_bob"), MessageId("m1"))
            offline = true
            push().sent(ChatId("alice_bob"), MessageId("m2"))
            assertEquals(2, requests.size)
        }

    @Test
    fun `nothing is sent without a worker or a signed-in user`() =
        runTest {
            push(url = null).sent(ChatId("alice_bob"), MessageId("m1"))
            push(token = null).sent(ChatId("alice_bob"), MessageId("m1"))
            assertTrue(requests.isEmpty())
        }
}
