package io.github.valerait.ghostlink.feature.chats

import androidx.lifecycle.SavedStateHandle
import io.github.valerait.ghostlink.core.domain.usecase.ObserveChatMessagesUseCase
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.DisappearingTimer
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.core.testing.FakeBackend
import io.github.valerait.ghostlink.core.testing.TestClock
import io.github.valerait.ghostlink.core.ui.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModelTest {
    // ViewModel запускает корутины на Dispatchers.Main — в тестах это тестовый диспетчер.
    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    private val clock = TestClock()

    private class Setup(
        val backend: FakeBackend,
        val me: UserId,
        val anya: ChatId,
        val boris: ChatId,
        val vm: ChatListViewModel,
    )

    private suspend fun TestScope.setUp(): Setup {
        val backend = FakeBackend(clock)
        val me = backend.createAccount().id
        backend.saveProfile(ANYA, "Аня")
        backend.saveProfile(BORIS, "Борис")
        val anya = backend.startChat(me, ANYA)
        backend.sendMessage(anya, ANYA, "привет от Ани")
        clock.advanceBy(1.seconds)
        val boris = backend.startChat(me, BORIS)
        backend.sendMessage(boris, BORIS, "привет от Бориса")
        val vm = backend.chatListViewModel(clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return Setup(backend, me, anya, boris, vm)
    }

    private fun ChatListViewModel.ids() = state.value.items.map { it.chat.id }

    @Test
    fun `pinned chat goes first and returns to its place when unpinned`() =
        runTest {
            val s = setUp()
            assertEquals(listOf(s.boris, s.anya), s.vm.ids())

            s.vm.onAction(ChatListAction.SetPinned(s.anya, true))
            assertEquals(listOf(s.anya, s.boris), s.vm.ids())
            assertTrue(
                s.vm.state.value.items
                    .first()
                    .pinned,
            )

            s.vm.onAction(ChatListAction.SetPinned(s.anya, false))
            assertEquals(listOf(s.boris, s.anya), s.vm.ids())
        }

    @Test
    fun `muted chat is marked but stays unread`() =
        runTest {
            val s = setUp()
            s.vm.onAction(ChatListAction.SetMuted(s.anya, true))
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
            s.vm.onAction(ChatListAction.SetPinned(s.anya, true))
            s.vm.onAction(ChatListAction.Remove(s.anya))
            assertEquals(listOf(s.boris), s.vm.ids())

            clock.advanceBy(1.seconds)
            s.backend.sendMessage(s.anya, ANYA, "снова я")
            val anya =
                s.vm.state.value.items
                    .first { it.chat.id == s.anya }
            assertFalse(anya.pinned, "удаление снимает закрепление")
            assertEquals("снова я", anya.lastMessage?.text)

            val texts =
                ObserveChatMessagesUseCase(s.backend, clock)(s.anya, s.me)
                    .first()
                    .map { it.text }
            assertEquals(listOf("снова я"), texts)
        }

    @Test
    fun `adding the friend again brings the removed chat back empty`() =
        runTest {
            val s = setUp()
            s.vm.onAction(ChatListAction.Remove(s.anya))
            clock.advanceBy(1.seconds)
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
            s.vm.onAction(ChatListAction.Remove(s.anya))
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

            clock.advanceBy(DisappearingTimer.HOUR.duration)
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
