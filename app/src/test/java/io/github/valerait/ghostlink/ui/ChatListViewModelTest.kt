package io.github.valerait.ghostlink.ui

import androidx.lifecycle.SavedStateHandle
import io.github.valerait.ghostlink.MainDispatcherRule
import io.github.valerait.ghostlink.data.demo.InMemoryBackend
import io.github.valerait.ghostlink.data.model.DisappearingTimer
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

class ChatListViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private var now = 1_000_000L

    private class Setup(
        val backend: InMemoryBackend,
        val me: String,
        val anya: String,
        val boris: String,
        val vm: ChatListViewModel,
    )

    private suspend fun TestScope.setUp(): Setup {
        val backend = InMemoryBackend(backgroundScope, clock = { now }, seedDemoData = false)
        val me = backend.createAccount().id
        backend.saveProfile(ANYA, "Аня")
        backend.saveProfile(BORIS, "Борис")
        val anya = backend.startChat(me, ANYA)
        backend.sendMessage(anya, ANYA, "привет от Ани")
        now += 1_000
        val boris = backend.startChat(me, BORIS)
        backend.sendMessage(boris, BORIS, "привет от Бориса")
        val vm = ChatListViewModel(backend, backend, backend, AppConfig(demoMode = false), { now })
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

            now += 1_000
            s.backend.sendMessage(s.anya, ANYA, "снова я")
            val anya =
                s.vm.state.value.items
                    .first { it.chat.id == s.anya }
            assertFalse("удаление снимает закрепление", anya.pinned)
            assertEquals("снова я", anya.lastMessage?.text)

            val chat = ChatViewModel(SavedStateHandle(mapOf("chatId" to s.anya)), s.backend, s.backend, s.backend, { now })
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
            now += 1_000
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
            s.backend.sendMessage(s.anya, ANYA, "пуф", ttl = DisappearingTimer.HOUR.seconds)
            assertEquals(
                "пуф",
                s.vm.state.value.items
                    .first { it.chat.id == s.anya }
                    .lastMessage
                    ?.text,
            )

            now += DisappearingTimer.HOUR.seconds * 1_000
            advanceTimeBy(DisappearingTimer.HOUR.seconds * 1_000 + 1)
            assertNull(
                s.vm.state.value.items
                    .first { it.chat.id == s.anya }
                    .lastMessage,
            )
        }

    private companion object {
        const val ANYA = "anya"
        const val BORIS = "boris"
    }
}
