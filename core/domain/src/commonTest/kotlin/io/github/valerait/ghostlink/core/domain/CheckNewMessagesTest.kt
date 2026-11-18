package io.github.valerait.ghostlink.core.domain

import io.github.valerait.ghostlink.core.domain.usecase.CheckNewMessagesUseCase
import io.github.valerait.ghostlink.core.domain.usecase.ObserveChatListUseCase
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.core.testing.FakeBackend
import io.github.valerait.ghostlink.core.testing.TestClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class CheckNewMessagesTest {
    private val clock = TestClock()
    private val backend = FakeBackend(clock)
    private val check = CheckNewMessagesUseCase(backend, backend, ObserveChatListUseCase(backend, backend, clock))
    private val bob = UserId("bob")

    private suspend fun meWithChat(): Pair<UserId, ChatId> {
        val me = backend.createAccount().id
        backend.saveProfile(me, "Валера")
        backend.saveProfile(bob, "Боб")
        return me to backend.startChat(me, bob)
    }

    @Test
    fun `unread message from a friend after the last check is reported`() =
        runTest {
            val (_, chat) = meWithChat()
            val since = clock.now()
            clock.advanceBy(1.minutes)
            backend.sendMessage(chat, bob, "Привет!")

            val notice = check(since).single()
            assertEquals(chat, notice.chatId)
            assertEquals("Боб", notice.title)
            assertEquals("Привет!", notice.text)
        }

    @Test
    fun `old, own, read and muted messages are not reported`() =
        runTest {
            val (me, chat) = meWithChat()
            backend.sendMessage(chat, bob, "давно")
            clock.advanceBy(1.minutes)
            assertTrue(check(clock.now()).isEmpty(), "старше прошлой проверки")

            val since = clock.now()
            clock.advanceBy(1.minutes)
            backend.sendMessage(chat, me, "моё")
            assertTrue(check(since).isEmpty(), "своё")

            backend.sendMessage(chat, bob, "прочитано")
            backend.markRead(chat, me)
            assertTrue(check(since).isEmpty(), "прочитано")

            clock.advanceBy(1.minutes)
            backend.sendMessage(chat, bob, "без звука")
            backend.setMuted(me, chat, muted = true)
            assertTrue(check(since).isEmpty(), "без звука")
        }

    @Test
    fun `nothing without an account`() =
        runTest {
            assertTrue(check(clock.now()).isEmpty())
        }
}
