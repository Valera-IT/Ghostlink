package io.github.valerait.ghostlink.ui

import androidx.lifecycle.SavedStateHandle
import io.github.valerait.ghostlink.MainDispatcherRule
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.DisappearingTimer
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.data.demo.InMemoryBackend
import io.github.valerait.ghostlink.di.AppConfig
import io.github.valerait.ghostlink.ui.chat.ChatRow
import io.github.valerait.ghostlink.ui.chat.ChatViewModel
import io.github.valerait.ghostlink.ui.chats.ChatListViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ChatListViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private var now = Instant.fromEpochMilliseconds(1_000_000)
    private val clock =
        object : Clock {
            override fun now() = now
        }

    private class Setup(
        val backend: InMemoryBackend,
        val me: UserId,
        val anya: ChatId,
        val boris: ChatId,
        val vm: ChatListViewModel,
    )

    private suspend fun TestScope.setUp(): Setup {
        val backend = InMemoryBackend(backgroundScope, clock = clock, seedDemoData = false)
        val me = backend.createAccount().id
        backend.saveProfile(ANYA, "Аня")
        backend.saveProfile(BORIS, "Борис")
        val anya = backend.startChat(me, ANYA)
        backend.sendMessage(anya, ANYA, "привет от Ани")
        now += 1.seconds
        val boris = backend.startChat(me, BORIS)
        backend.sendMessage(boris, BORIS, "привет от Бориса")
        val vm = ChatListViewModel(backend, backend, backend, AppConfig(demoMode = false), clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return Setup(backend, me, anya, boris, vm)
    }

    private fun ChatListViewModel.ids() = state.value.items.map { it.chat.id }

    @Test
    fun `pinned chat goes first and returns to its place when unpinned`() =
        runTest {
            val s = setUp()
            assertEquals(listOf(s.boris, s.anya), s.vm.ids())

            s.vm.setPinned(s.anya, true)
            assertEquals(listOf(s.anya, s.boris), s.vm.ids())
            assertTrue(
                s.vm.state.value.items
                    .first()
                    .pinned,
            )

            s.vm.setPinned(s.anya, false)
            assertEquals(listOf(s.boris, s.anya), s.vm.ids())
        }

    @Test
    fun `muted chat is marked but stays unread`() =
        runTest {
            val s = setUp()
            s.vm.setMuted(s.anya, true)
            val anya =
                s.vm.state.value.items
                    .single { it.chat.id == s.anya }
            assertTrue(anya.muted)
            assertTrue(anya.unread)
        }

    @Test
    fun `removed chat disappears with its history and comes back with a new message`() =
        runTest {
            val s = setUp()
            s.vm.setPinned(s.anya, true)
            s.vm.remove(s.anya)
            assertEquals(listOf(s.boris), s.vm.ids())

            now += 1.seconds
            s.backend.sendMessage(s.anya, ANYA, "снова я")
            val anya =
                s.vm.state.value.items
                    .first { it.chat.id == s.anya }
            assertFalse("удаление снимает закрепление", anya.pinned)
            assertEquals("снова я", anya.lastMessage?.text)

            val chat = ChatViewModel(SavedStateHandle(mapOf("chatId" to s.anya.value)), s.backend, s.backend, s.backend, clock)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { chat.state.collect {} }
            val texts =
                chat.state.value.rows
                    .filterIsInstance<ChatRow.Msg>()
                    .map { it.item.message.text }
            assertEquals(listOf("снова я"), texts)
        }

    @Test
    fun `adding the friend again brings the removed chat back empty`() =
        runTest {
            val s = setUp()
            s.vm.remove(s.anya)
            now += 1.seconds
            assertEquals(s.anya, s.backend.startChat(s.me, ANYA))

            val anya =
                s.vm.state.value.items
                    .single { it.chat.id == s.anya }
            assertNull(anya.lastMessage)
            assertFalse(anya.unread)
        }

    @Test
    fun `removing a chat does not affect the partner`() =
        runTest {
            val s = setUp()
            s.vm.remove(s.anya)
            assertTrue(
                s.backend
                    .observeChatPrefs(ANYA)
                    .first()
                    .isEmpty(),
            )
            assertEquals(
                1,
                s.backend
                    .observeMessages(s.anya)
                    .first()
                    .size,
            )
        }

    @Test
    fun `expired disappearing message leaves the preview`() =
        runTest {
            val s = setUp()
            s.backend.sendMessage(s.anya, ANYA, "пуф", ttl = DisappearingTimer.HOUR.duration)
            assertEquals(
                "пуф",
                s.vm.state.value.items
                    .first { it.chat.id == s.anya }
                    .lastMessage
                    ?.text,
            )

            now += DisappearingTimer.HOUR.duration
            advanceTimeBy(DisappearingTimer.HOUR.duration + 1.milliseconds)
            assertNull(
                s.vm.state.value.items
                    .first { it.chat.id == s.anya }
                    .lastMessage,
            )
        }

    private companion object {
        val ANYA = UserId("anya")
        val BORIS = UserId("boris")
    }
}
