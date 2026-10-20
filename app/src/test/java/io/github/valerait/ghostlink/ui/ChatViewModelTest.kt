package io.github.valerait.ghostlink.ui

import androidx.lifecycle.SavedStateHandle
import io.github.valerait.ghostlink.MainDispatcherRule
import io.github.valerait.ghostlink.data.demo.InMemoryBackend
import io.github.valerait.ghostlink.data.model.DisappearingTimer
import io.github.valerait.ghostlink.data.model.MessageStatus
import io.github.valerait.ghostlink.data.model.ReplyTo
import io.github.valerait.ghostlink.data.model.TYPING_TIMEOUT_MS
import io.github.valerait.ghostlink.ui.chat.ChatRow
import io.github.valerait.ghostlink.ui.chat.ChatUiState
import io.github.valerait.ghostlink.ui.chat.ChatViewModel
import io.github.valerait.ghostlink.ui.chat.MessageItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
        val vm = ChatViewModel(SavedStateHandle(mapOf("chatId" to chatId)), backend, backend, backend, { now })
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
    fun `reply carries a link to the original message and clears the reply bar`() =
        runTest {
            val (backend, chatId, vm) = setUp()
            backend.sendMessage(chatId, FRIEND, "Встретимся\nв семь?")
            val original =
                vm.state.value
                    .messages()
                    .single()
                    .message

            vm.startReply(original.id)
            assertEquals(
                original.id,
                vm.state.value.replyingTo
                    ?.message
                    ?.id,
            )

            vm.send("Да")
            assertNull(vm.state.value.replyingTo)
            val reply =
                vm.state.value
                    .messages()
                    .last()
            assertEquals(ReplyTo(original.id, FRIEND, "Встретимся в семь?"), reply.message.replyTo)
            assertEquals(false, reply.replyToMine)
        }

    @Test
    fun `reply to own message is marked as mine`() =
        runTest {
            val (_, _, vm) = setUp()
            vm.send("первое")
            val first =
                vm.state.value
                    .messages()
                    .single()
            vm.startReply(first.message.id)
            vm.send("второе")
            assertTrue(
                vm.state.value
                    .messages()
                    .last()
                    .replyToMine,
            )
        }

    @Test
    fun `cancelled reply sends a plain message`() =
        runTest {
            val (backend, chatId, vm) = setUp()
            backend.sendMessage(chatId, FRIEND, "привет")
            vm.startReply(
                vm.state.value
                    .messages()
                    .single()
                    .message.id,
            )
            vm.cancelReply()
            vm.send("ответ")
            assertNull(
                vm.state.value
                    .messages()
                    .last()
                    .message.replyTo,
            )
        }

    @Test
    fun `edit replaces text, marks message edited and updates chat preview`() =
        runTest {
            val (backend, chatId, vm) = setUp()
            vm.send("опечатка")
            val sent =
                vm.state.value
                    .messages()
                    .single()

            vm.startEdit(sent.message.id)
            assertEquals(
                sent.message.id,
                vm.state.value.editing
                    ?.message
                    ?.id,
            )
            now += 1_000
            vm.send("  исправлено ")

            val edited =
                vm.state.value
                    .messages()
                    .single()
            assertEquals("исправлено", edited.message.text)
            assertEquals(now, edited.message.editedAt)
            assertNull(vm.state.value.editing)
            assertEquals(
                "исправлено",
                backend
                    .observeChat(chatId)
                    .first()!!
                    .lastMessage
                    ?.text,
            )
        }

    @Test
    fun `editing an older message keeps the chat preview`() =
        runTest {
            val (backend, chatId, vm) = setUp()
            vm.send("первое")
            vm.send("второе")
            vm.startEdit(
                vm.state.value
                    .messages()
                    .first()
                    .message.id,
            )
            vm.send("первое!")
            assertEquals(
                "второе",
                backend
                    .observeChat(chatId)
                    .first()!!
                    .lastMessage
                    ?.text,
            )
        }

    @Test
    fun `partner messages cannot be edited or deleted`() =
        runTest {
            val (backend, chatId, vm) = setUp()
            backend.sendMessage(chatId, FRIEND, "моё")
            val id =
                vm.state.value
                    .messages()
                    .single()
                    .message.id

            vm.startEdit(id)
            assertNull(vm.state.value.editing)
            vm.delete(id)
            assertEquals(
                1,
                vm.state.value
                    .messages()
                    .size,
            )
        }

    @Test
    fun `reply and edit modes replace each other`() =
        runTest {
            val (_, _, vm) = setUp()
            vm.send("раз")
            val id =
                vm.state.value
                    .messages()
                    .single()
                    .message.id
            vm.startReply(id)
            vm.startEdit(id)
            assertNull(vm.state.value.replyingTo)
            vm.startReply(id)
            assertNull(vm.state.value.editing)
        }

    @Test
    fun `deleting the latest message moves chat preview to the previous one`() =
        runTest {
            val (backend, chatId, vm) = setUp()
            backend.sendMessage(chatId, FRIEND, "привет")
            vm.send("ой, не то")
            vm.delete(
                vm.state.value
                    .messages()
                    .last()
                    .message.id,
            )

            val deleted =
                vm.state.value
                    .messages()
                    .last()
            assertTrue(deleted.message.deleted)
            assertEquals("", deleted.message.text)
            val last = backend.observeChat(chatId).first()!!.lastMessage
            assertEquals("привет", last?.text)
            assertEquals(FRIEND, last?.senderId)
        }

    @Test
    fun `deleting the only message clears chat preview`() =
        runTest {
            val (backend, chatId, vm) = setUp()
            vm.send("единственное")
            vm.delete(
                vm.state.value
                    .messages()
                    .single()
                    .message.id,
            )
            assertTrue(
                vm.state.value
                    .messages()
                    .single()
                    .message.deleted,
            )
            assertNull(backend.observeChat(chatId).first()!!.lastMessage)
        }

    @Test
    fun `chat preview skips messages that are already deleted`() =
        runTest {
            val (backend, chatId, vm) = setUp()
            vm.send("первое")
            vm.send("второе")
            vm.send("третье")
            val ids =
                vm.state.value
                    .messages()
                    .map { it.message.id }
            vm.delete(ids[2])
            vm.delete(ids[1])
            assertEquals(
                "первое",
                backend
                    .observeChat(chatId)
                    .first()!!
                    .lastMessage
                    ?.text,
            )
            vm.delete(ids[0])
            assertNull(backend.observeChat(chatId).first()!!.lastMessage)
        }

    @Test
    fun `deleted message cannot be edited or replied to and its replies say so`() =
        runTest {
            val (_, _, vm) = setUp()
            vm.send("исходное")
            val id =
                vm.state.value
                    .messages()
                    .single()
                    .message.id
            vm.startReply(id)
            vm.send("ответ")
            vm.delete(id)

            assertTrue(
                vm.state.value
                    .messages()
                    .last()
                    .replyToDeleted,
            )
            vm.startEdit(id)
            assertNull(vm.state.value.editing)
            vm.startReply(id)
            assertNull(vm.state.value.replyingTo)
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

    @Test
    fun `typing mark is set while drafting and cleared by sending or erasing`() =
        runTest {
            val (backend, chatId, vm) = setUp()
            val me = backend.account.value!!.id

            suspend fun typing() = backend.observeChat(chatId).first()!!.typing

            vm.onDraftChanged("при")
            assertEquals(setOf(me), typing().keys)
            vm.send("привет")
            assertTrue(typing().isEmpty())

            vm.onDraftChanged("ещё")
            vm.onDraftChanged("")
            assertTrue(typing().isEmpty())

            vm.onDraftChanged("и ещё")
            vm.onVisibilityChanged(false)
            assertTrue(typing().isEmpty())
        }

    @Test
    fun `partner typing is shown until the mark expires`() =
        runTest {
            val (backend, chatId, vm) = setUp()
            assertNull(vm.state.value.partnerTypingUntil)

            backend.setTyping(chatId, FRIEND, true)
            assertEquals(now + TYPING_TIMEOUT_MS, vm.state.value.partnerTypingUntil)

            backend.sendMessage(chatId, FRIEND, "готово")
            assertNull(vm.state.value.partnerTypingUntil)
        }

    @Test
    fun `disappearing messages vanish on time and are erased from the backend`() =
        runTest {
            val (backend, chatId, vm) = setUp()
            vm.send("навсегда")
            vm.setDisappearing(DisappearingTimer.HOUR)
            assertEquals(DisappearingTimer.HOUR, vm.state.value.disappearing)
            vm.send("пуф")
            assertEquals(
                listOf("навсегда", "пуф"),
                vm.state.value
                    .messages()
                    .map { it.message.text },
            )

            now += DisappearingTimer.HOUR.seconds * 1_000
            advanceTimeBy(DisappearingTimer.HOUR.seconds * 1_000 + 1)
            assertEquals(
                listOf("навсегда"),
                vm.state.value
                    .messages()
                    .map { it.message.text },
            )
            assertEquals(listOf("навсегда"), backend.observeMessages(chatId).first().map { it.text })

            vm.setDisappearing(null)
            vm.send("опять навсегда")
            assertNull(
                backend
                    .observeMessages(chatId)
                    .first()
                    .last()
                    .ttl,
            )
        }

    private companion object {
        const val FRIEND = "friend"
    }
}
