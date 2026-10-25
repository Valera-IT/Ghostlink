package io.github.valerait.ghostlink.core.data

import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.GhostlinkError
import io.github.valerait.ghostlink.core.model.MediaKey
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorkerMediaStoreTest {
    private val chat = ChatId("alice_bob")
    private val dir = Path(SystemTemporaryDirectory, "media-test-${Random.nextLong().toULong()}")

    /** Что лежит в «B2»: url → байты. */
    private val bucket = mutableMapOf<String, ByteArray>()
    private val requests = mutableListOf<HttpRequestData>()
    private var worker: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { fakeWorker(it) }

    private val http =
        HttpClient(
            MockEngine { request ->
                requests += request
                worker(request)
            },
        )

    private fun store(
        url: String? = "https://worker.example/",
        token: String? = "token",
    ) = WorkerMediaStore(url, { token }, http, MediaCache(dir))

    @AfterTest
    fun cleanUp() {
        if (SystemFileSystem.exists(dir)) {
            SystemFileSystem.list(dir).forEach { SystemFileSystem.delete(it) }
            SystemFileSystem.delete(dir)
        }
    }

    private suspend fun MockRequestHandleScope.fakeWorker(request: HttpRequestData): HttpResponseData {
        val url = request.url.toString()
        return when {
            url == "https://worker.example/media/upload" -> {
                assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                val body = request.body.toByteArray().decodeToString()
                assertTrue("\"chatId\":\"alice_bob\"" in body, body)
                val count =
                    Regex("\"sizes\":\\[([^\\]]*)]")
                        .find(body)!!
                        .groupValues[1]
                        .split(',')
                        .size
                val files =
                    (1..count).joinToString(",") {
                        """{"key":"p/alice_bob/me/a-$it.jpg","url":"https://b2/$it","headers":{"content-type":"image/jpeg"}}"""
                    }
                json("""{"files":[$files]}""")
            }

            url == "https://worker.example/media/download" -> {
                val key = Regex("\"keys\":\\[\"([^\"]+)\"]").find(request.body.toByteArray().decodeToString())!!.groupValues[1]
                json("""{"urls":{"$key":"https://b2/${key.substringAfterLast('-').removeSuffix(".jpg")}"}}""")
            }

            url.startsWith("https://b2/") && request.method == HttpMethod.Put -> {
                bucket[url] = request.body.toByteArray()
                respond("")
            }

            url.startsWith("https://b2/") -> {
                bucket[url]?.let { respond(it) } ?: respondError(HttpStatusCode.NotFound)
            }

            else -> {
                error("Unexpected request $url")
            }
        }
    }

    private fun MockRequestHandleScope.json(body: String) = respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun uploadPutsBytesToSignedUrlsAndReturnsKeysInOrder() =
        runTest {
            val photo = byteArrayOf(1, 2, 3)
            val thumb = byteArrayOf(4)

            val keys = store().upload(chat, listOf(photo, thumb))

            assertEquals(listOf(MediaKey("p/alice_bob/me/a-1.jpg"), MediaKey("p/alice_bob/me/a-2.jpg")), keys)
            assertContentEquals(photo, bucket["https://b2/1"])
            assertContentEquals(thumb, bucket["https://b2/2"])
            assertEquals(
                "image/jpeg",
                requests
                    .first { it.url.toString() == "https://b2/1" }
                    .body.contentType
                    .toString(),
            )
        }

    @Test
    fun ownUploadIsServedFromCacheWithoutNetwork() =
        runTest {
            val store = store()
            val keys = store.upload(chat, listOf(byteArrayOf(7, 7)))
            requests.clear()

            assertContentEquals(byteArrayOf(7, 7), store.download(chat, keys[0]))
            assertTrue(requests.isEmpty())
        }

    @Test
    fun downloadAsksWorkerOnceThenUsesDiskCache() =
        runTest {
            bucket["https://b2/9"] = byteArrayOf(9)
            val key = MediaKey("p/alice_bob/bob/b-9.jpg")

            assertContentEquals(byteArrayOf(9), store().download(chat, key))
            // Новый экземпляр — как после перезапуска приложения: файл уже на диске.
            assertContentEquals(byteArrayOf(9), store().download(chat, key))
            assertEquals(2, requests.size)
        }

    @Test
    fun errorsBecomeGhostlinkErrors() =
        runTest {
            worker = { respondError(HttpStatusCode.Forbidden, """{"error":"Not a member of this chat"}""") }
            assertFailsWith<GhostlinkError.PermissionDenied> { store().upload(chat, listOf(byteArrayOf(1))) }

            worker = { respondError(HttpStatusCode.TooManyRequests) }
            assertFailsWith<GhostlinkError.Unexpected> { store().download(chat, MediaKey("p/alice_bob/bob/b-1.jpg")) }

            worker = { throw IOException("No route to host") }
            assertFailsWith<GhostlinkError.Offline> { store().download(chat, MediaKey("p/alice_bob/bob/b-1.jpg")) }

            assertFailsWith<GhostlinkError.PermissionDenied> { store(token = null).upload(chat, listOf(byteArrayOf(1))) }
            assertFailsWith<GhostlinkError.Unexpected> { store(url = null).upload(chat, listOf(byteArrayOf(1))) }
        }

    @Test
    fun cacheDropsOldestPhotosOverLimit() =
        runTest {
            val cache = MediaCache(dir, maxBytes = 10)
            cache.put(MediaKey("p/c/u/1-a.jpg"), ByteArray(4))
            cache.put(MediaKey("p/c/u/3-c.jpg"), ByteArray(4))
            cache.put(MediaKey("p/c/u/2-b.jpg"), ByteArray(4))

            // Больше 10 байт — чистим до половины лимита, начиная со старых по времени в имени.
            assertEquals(null, cache.get(MediaKey("p/c/u/1-a.jpg")))
            assertEquals(null, cache.get(MediaKey("p/c/u/2-b.jpg")))
            assertContentEquals(ByteArray(4), cache.get(MediaKey("p/c/u/3-c.jpg")))
        }
}
