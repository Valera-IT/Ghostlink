package io.github.valerait.ghostlink.feature.chat

import androidx.lifecycle.SavedStateHandle
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.DisappearingTimer
import io.github.valerait.ghostlink.core.model.MessageStatus
import io.github.valerait.ghostlink.core.model.ReplyTo
import io.github.valerait.ghostlink.core.model.TYPING_TIMEOUT
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.core.testing.FakeBackend
import io.github.valerait.ghostlink.core.testing.TestClock
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    // ViewModel запускает корутины на Dispatchers.Main — в тестах это тестовый диспетчер.
    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    private val clock = TestClock()

    private suspend fun TestScope.setUp(): Triple<FakeBackend, ChatId, ChatViewModel> {
        val backend = FakeBackend(clock)
        val me = backend.createAccount().id
        backend.saveProfile(me, "Я")
        backend.saveProfile(FRIEND, "Друг")
        val chatId = backend.startChat(me, FRIEND)
        val vm = backend.chatViewModel(chatId, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return Triple(backend, chatId, vm)
    }

    private fun ChatUiState.messages(): List<MessageItem> = rows.filterIsInstance<ChatRow.Msg>().map { it.item }.reversed()

    @Test
    fun `own message turns read after partner reads the chat`() =
        runTest {
            val (backend, chatId, vm) = setUp()

            vm.onAction(ChatAction.Send("  привет  "))
            val sent =
                vm.state.value
                    .messages()
                    .single()
            assertEquals("привет", sent.message.text)
            assertTrue(sent.mine)
            assertEquals(MessageStatus.SENT, sent.status)

            clock.advanceBy(1.seconds)
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

            vm.onAction(ChatAction.StartReply(original.id))
            assertEquals(
                original.id,
                vm.state.value.replyingTo
                    ?.message
                    ?.id,
            )

            vm.onAction(ChatAction.Send("Да"))
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
            vm.onAction(ChatAction.Send("первое"))
            val first =
                vm.state.value
                    .messages()
                    .single()
            vm.onAction(ChatAction.StartReply(first.message.id))
            vm.onAction(ChatAction.Send("второе"))
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
            vm.onAction(
                ChatAction.StartReply(
                    vm.state.value
                        .messages()
                        .single()
                        .message.id,
                ),
            )
            vm.onAction(ChatAction.CancelReply)
            vm.onAction(ChatAction.Send("ответ"))
            assertNull(
                vm.state.value
                    .messages()
                    .last()
                    .message.replyTo,
            )
        }

    @Test
    fun `edit replaces text and marks message edited and updates chat preview`() =
        runTest {
            val (backend, chatId, vm) = setUp()
            vm.onAction(ChatAction.Send("опечатка"))
            val sent =
                vm.state.value
                    .messages()
                    .single()

            vm.onAction(ChatAction.StartEdit(sent.message.id))
            assertEquals(
                sent.message.id,
                vm.state.value.editing
                    ?.message
                    ?.id,
            )
            clock.advanceBy(1.seconds)
            vm.onAction(ChatAction.Send("  исправлено "))

            val edited =
                vm.state.value
                    .messages()
                    .single()
            assertEquals("исправлено", edited.message.text)
            assertEquals(clock.now(), edited.message.editedAt)
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
            vm.onAction(ChatAction.Send("первое"))
            vm.onAction(ChatAction.Send("второе"))
            vm.onAction(
                ChatAction.StartEdit(
                    vm.state.value
                        .messages()
                        .first()
                        .message.id,
                ),
            )
            vm.onAction(ChatAction.Send("первое!"))
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

            vm.onAction(ChatAction.StartEdit(id))
            assertNull(vm.state.value.editing)
            vm.onAction(ChatAction.Delete(id))
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
            vm.onAction(ChatAction.Send("раз"))
            val id =
                vm.state.value
                    .messages()
                    .single()
                    .message.id
            vm.onAction(ChatAction.StartReply(id))
            vm.onAction(ChatAction.StartEdit(id))
            assertNull(vm.state.value.replyingTo)
            vm.onAction(ChatAction.StartReply(id))
            assertNull(vm.state.value.editing)
        }

    @Test
    fun `deleting the latest message moves chat preview to the previous one`() =
        runTest {
            val (backend, chatId, vm) = setUp()
            backend.sendMessage(chatId, FRIEND, "привет")
            vm.onAction(ChatAction.Send("ой, не то"))
            vm.onAction(
                ChatAction.Delete(
                    vm.state.value
                        .messages()
                        .last()
                        .message.id,
                ),
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
            vm.onAction(ChatAction.Send("единственное"))
            vm.onAction(
                ChatAction.Delete(
                    vm.state.value
                        .messages()
                        .single()
                        .message.id,
                ),
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
            vm.onAction(ChatAction.Send("первое"))
            vm.onAction(ChatAction.Send("второе"))
            vm.onAction(ChatAction.Send("третье"))
            val ids =
                vm.state.value
                    .messages()
                    .map { it.message.id }
            vm.onAction(ChatAction.Delete(ids[2]))
            vm.onAction(ChatAction.Delete(ids[1]))
            assertEquals(
                "первое",
                backend
                    .observeChat(chatId)
                    .first()!!
                    .lastMessage
                    ?.text,
            )
            vm.onAction(ChatAction.Delete(ids[0]))
            assertNull(backend.observeChat(chatId).first()!!.lastMessage)
        }

    @Test
    fun `deleted message cannot be edited or replied to and its replies say so`() =
        runTest {
            val (_, _, vm) = setUp()
            vm.onAction(ChatAction.Send("исходное"))
            val id =
                vm.state.value
                    .messages()
                    .single()
                    .message.id
            vm.onAction(ChatAction.StartReply(id))
            vm.onAction(ChatAction.Send("ответ"))
            vm.onAction(ChatAction.Delete(id))

            assertTrue(
                vm.state.value
                    .messages()
                    .last()
                    .replyToDeleted,
            )
            vm.onAction(ChatAction.StartEdit(id))
            assertNull(vm.state.value.editing)
            vm.onAction(ChatAction.StartReply(id))
            assertNull(vm.state.value.replyingTo)
        }

    @Test
    fun `incoming message is marked read only while chat is visible`() =
        runTest {
            val (backend, chatId, vm) = setUp()
            val me = backend.account.value!!.id

            backend.sendMessage(chatId, FRIEND, "ты тут?")
            assertNull(backend.observeChat(chatId).first()!!.lastRead[me])

            clock.advanceBy(1.seconds)
            vm.onAction(ChatAction.VisibilityChanged(true))
            assertNotNull(backend.observeChat(chatId).first()!!.lastRead[me])
            assertEquals(false, backend.observeChat(chatId).first()!!.isUnreadFor(me))
        }

    @Test
    fun `blank messages are not sent`() =
        runTest {
            val (_, _, vm) = setUp()
            vm.onAction(ChatAction.Send("   "))
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
            vm.onAction(ChatAction.Send("вчера"))
            clock.advanceBy(1.days)
            vm.onAction(ChatAction.Send("сегодня"))
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

            vm.onAction(ChatAction.DraftChanged("при"))
            assertEquals(setOf(me), typing().keys)
            vm.onAction(ChatAction.Send("привет"))
            assertTrue(typing().isEmpty())

            vm.onAction(ChatAction.DraftChanged("ещё"))
            vm.onAction(ChatAction.DraftChanged(""))
            assertTrue(typing().isEmpty())

            vm.onAction(ChatAction.DraftChanged("и ещё"))
            vm.onAction(ChatAction.VisibilityChanged(false))
            assertTrue(typing().isEmpty())
        }

    @Test
    fun `partner typing is shown until the mark expires`() =
        runTest {
            val (backend, chatId, vm) = setUp()
            assertNull(vm.state.value.partnerTypingUntil)

            backend.setTyping(chatId, FRIEND, true)
            assertEquals(clock.now() + TYPING_TIMEOUT, vm.state.value.partnerTypingUntil)

            backend.sendMessage(chatId, FRIEND, "готово")
            assertNull(vm.state.value.partnerTypingUntil)
        }

    @Test
    fun `disappearing messages vanish on time and are erased from the backend`() =
        runTest {
            val (backend, chatId, vm) = setUp()
            vm.onAction(ChatAction.Send("навсегда"))
            vm.onAction(ChatAction.SetDisappearing(DisappearingTimer.HOUR))
            assertEquals(DisappearingTimer.HOUR, vm.state.value.disappearing)
            vm.onAction(ChatAction.Send("пуф"))
            assertEquals(
                listOf("навсегда", "пуф"),
                vm.state.value
                    .messages()
                    .map { it.message.text },
            )

            clock.advanceBy(DisappearingTimer.HOUR.duration)
            advanceTimeBy(DisappearingTimer.HOUR.duration + 1.milliseconds)
            assertEquals(
                listOf("навсегда"),
                vm.state.value
                    .messages()
                    .map { it.message.text },
            )
            assertEquals(listOf("навсегда"), backend.observeMessages(chatId).first().map { it.text })

            vm.onAction(ChatAction.SetDisappearing(null))
            vm.onAction(ChatAction.Send("опять навсегда"))
            assertNull(
                backend
                    .observeMessages(chatId)
                    .first()
                    .last()
                    .ttl,
            )
        }

    private companion object {
        val FRIEND = UserId("friend")
    }
}
