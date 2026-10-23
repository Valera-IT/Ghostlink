package io.github.valerait.ghostlink.core.demo

import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.UserId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DemoBackendTest {
    @Test
    fun newAccountStartsWithDemoChatsAndALaptop() =
        runTest {
            val backend = DemoBackend(backgroundScope)
            val me = backend.createAccount().id

            val chats = backend.observeChats(me).first()
            assertEquals(3, chats.size)
            assertEquals(1, chats.count { it.isGroup })
            assertEquals(1, backend.observeDevices(me).first().size)
        }

    @Test
    fun botReadsAndRepliesInItsChat() =
        runTest {
            val backend = DemoBackend(backgroundScope)
            val me = backend.createAccount().id
            val chatId = ChatId.of(me, DemoBackend.BOT_ID)

            backend.sendMessage(chatId, me, "привет")
            // advanceUntilIdle не ждёт backgroundScope, где отвечает бот.
            advanceTimeBy(5.seconds)

            val messages = backend.observeMessages(chatId).first()
            assertEquals(DemoBackend.BOT_ID, messages.last().senderId)
            assertTrue(
                backend
                    .observeChat(chatId)
                    .first()!!
                    .lastRead
                    .containsKey(DemoBackend.BOT_ID),
            )
        }

    @Test
    fun anyScannedCodeBelongsToSomeone() =
        runTest {
            val backend = DemoBackend(backgroundScope)
            backend.createAccount()

            val stranger = UserId("abcdef")
            assertEquals("Призрак #abcd", backend.getUser(stranger)?.name)
            assertEquals(backend.getUser(stranger), backend.observeUser(stranger).first())
        }
}
