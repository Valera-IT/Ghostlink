package io.github.valerait.ghostlink.ui

import androidx.lifecycle.SavedStateHandle
import io.github.valerait.ghostlink.MainDispatcherRule
import io.github.valerait.ghostlink.data.demo.InMemoryBackend
import io.github.valerait.ghostlink.data.model.MessageStatus
import io.github.valerait.ghostlink.ui.chat.ChatRow
import io.github.valerait.ghostlink.ui.chat.ChatUiState
import io.github.valerait.ghostlink.ui.chat.ChatViewModel
import io.github.valerait.ghostlink.ui.chat.MessageItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private var now = 1_000_000L

    private suspend fun TestScope.setUp(): Triple<InMemoryBackend, String, ChatViewModel> {
        val backend = InMemoryBackend(backgroundScope, clock = { now }, seedDemoData = false)
        val me = backend.createAccount().id
        backend.saveProfile(me, "Я")
        backend.saveProfile(FRIEND, "Друг")
        val chatId = backend.startChat(me, FRIEND)
        val vm = ChatViewModel(SavedStateHandle(mapOf("chatId" to chatId)), backend, backend, backend)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return Triple(backend, chatId, vm)
    }

    private fun ChatUiState.messages(): List<MessageItem> = rows.filterIsInstance<ChatRow.Msg>().map { it.item }.reversed()

    @Test
    fun `own message turns read after partner reads the chat`() =
        runTest {
            val (backend, chatId, vm) = setUp()

            vm.send("  привет  ")
            val sent =
                vm.state.value
                    .messages()
                    .single()
            assertEquals("привет", sent.message.text)
            assertTrue(sent.mine)
            assertEquals(MessageStatus.SENT, sent.status)

            now += 1_000
            backend.markRead(chatId, FRIEND)
            assertEquals(
                MessageStatus.READ,
                vm.state.value
                    .messages()
                    .single()
                    .status,
            )
        }

    @Test
    fun `incoming message is marked read only while chat is visible`() =
        runTest {
            val (backend, chatId, vm) = setUp()
            val me = backend.account.value!!.id

            backend.sendMessage(chatId, FRIEND, "ты тут?")
            assertNull(backend.observeChat(chatId).first()!!.lastRead[me])

            now += 1_000
            vm.onVisibilityChanged(true)
            assertNotNull(backend.observeChat(chatId).first()!!.lastRead[me])
            assertEquals(false, backend.observeChat(chatId).first()!!.isUnreadFor(me))
        }

    @Test
    fun `blank messages are not sent`() =
        runTest {
            val (_, _, vm) = setUp()
            vm.send("   ")
            assertTrue(
                vm.state.value
                    .messages()
                    .isEmpty(),
            )
        }

    @Test
    fun `messages from different days get a day separator`() =
        runTest {
            val (_, _, vm) = setUp()
            vm.send("вчера")
            now += 24 * 60 * 60 * 1000L
            vm.send("сегодня")
            assertEquals(
                2,
                vm.state.value.rows
                    .count { it is ChatRow.Day },
            )
        }

    private companion object {
        const val FRIEND = "friend"
    }
}
