package io.github.valerait.ghostlink.feature.chat

import androidx.lifecycle.SavedStateHandle
import io.github.valerait.ghostlink.core.domain.usecase.ObserveChatListUseCase
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.MessageStatus
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
class GroupChatTest {
    // ViewModel запускает корутины на Dispatchers.Main — в тестах это тестовый диспетчер.
    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    private val clock = TestClock()

    private class Setup(
        val backend: FakeBackend,
        val me: UserId,
        val groupId: ChatId,
    )

    /** Я, Аня и Борис — друзья; группу создаю я через экран выбора участников. */
    private suspend fun TestScope.setUp(): Setup {
        val backend = FakeBackend(clock)
        val me = backend.createAccount().id
        backend.saveProfile(me, "Я")
        backend.saveProfile(ANYA, "Аня")
        backend.saveProfile(BORIS, "Борис")
        backend.startChat(me, ANYA)
        backend.startChat(me, BORIS)
        val pick = PickMembersViewModel(SavedStateHandle(), backend, backend, backend)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pick.state.collect {} }
        assertEquals(
            listOf("Аня", "Борис"),
            pick.state.value.friends
                .map { it.name },
        )
        pick.onAction(PickMembersAction.Toggle(ANYA))
        pick.onAction(PickMembersAction.Toggle(BORIS))
        pick.onAction(PickMembersAction.Confirm("  Митап  "))
        val groupId = (pick.effects.first() as PickMembersEffect.Done).chatId
        return Setup(backend, me, groupId)
    }

    private fun TestScope.chatVm(s: Setup) =
        s.backend.chatViewModel(s.groupId, clock).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        }

    @Test
    fun `created group has me as admin and the picked friends`() =
        runTest {
            val s = setUp()
            val chat = s.backend.observeChat(s.groupId).first()!!
            assertEquals("Митап", chat.group?.name)
            assertEquals(s.me, chat.group?.ownerId)
            assertEquals(setOf(s.me, ANYA, BORIS), chat.members.toSet())
        }

    @Test
    fun `two ticks when one member read and three when everyone did`() =
        runTest {
            val s = setUp()
            val vm = chatVm(s)
            vm.onAction(ChatAction.Send("всем привет"))

            fun status() =
                vm.state.value.rows
                    .filterIsInstance<ChatRow.Msg>()
                    .single()
                    .item.status
            assertEquals(MessageStatus.SENT, status())

            clock.advanceBy(1.seconds)
            s.backend.markRead(s.groupId, ANYA)
            assertEquals(MessageStatus.READ, status())

            s.backend.markRead(s.groupId, BORIS)
            assertEquals(MessageStatus.READ_ALL, status())
        }

    @Test
    fun `sender names and typing member are shown in the group`() =
        runTest {
            val s = setUp()
            val vm = chatVm(s)
            s.backend.sendMessage(s.groupId, ANYA, "я тут")
            val item =
                vm.state.value.rows
                    .filterIsInstance<ChatRow.Msg>()
                    .single()
                    .item
            assertEquals("Аня", item.senderName)
            assertEquals(3, vm.state.value.memberCount)
            assertNull(vm.state.value.partner)

            s.backend.setTyping(s.groupId, BORIS, true)
            assertEquals("Борис", vm.state.value.typingName)

            val row =
                ObserveChatListUseCase(s.backend, s.backend, clock)(s.me)
                    .first()
                    .first { it.chat.id == s.groupId }
            assertEquals("Митап", row.title)
            assertEquals("Аня", row.lastSenderName)
            assertEquals("Борис", row.typingName)
        }

    @Test
    fun `admin removes a member and leaving passes admin rights on`() =
        runTest {
            val s = setUp()
            val info = s.backend.groupInfoViewModel(s.groupId)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { info.state.collect {} }
            assertTrue(info.state.value.isOwner)
            assertEquals(
                s.me,
                info.state.value.members
                    .first()
                    .id,
            )

            info.onAction(GroupInfoAction.Remove(BORIS))
            assertEquals(
                setOf(s.me, ANYA),
                s.backend
                    .observeChat(s.groupId)
                    .first()!!
                    .members
                    .toSet(),
            )

            info.onAction(GroupInfoAction.Leave)
            assertTrue(info.state.value.left)
            val chat = s.backend.observeChat(s.groupId).first()!!
            assertEquals(listOf(ANYA), chat.members)
            assertEquals(ANYA, chat.group?.ownerId)
            assertFalse(s.me in chat.members)
        }

    @Test
    fun `adding members skips those already in the group`() =
        runTest {
            val s = setUp()
            s.backend.saveProfile(VERA, "Вера")
            s.backend.startChat(s.me, VERA)
            val pick = PickMembersViewModel(SavedStateHandle(mapOf("chatId" to s.groupId.value)), s.backend, s.backend, s.backend)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pick.state.collect {} }
            assertEquals(
                listOf("Вера"),
                pick.state.value.friends
                    .map { it.name },
            )
            pick.onAction(PickMembersAction.Toggle(VERA))
            pick.onAction(PickMembersAction.Confirm(""))
            assertEquals(PickMembersEffect.Done(s.groupId), pick.effects.first())
            assertTrue(
                VERA in
                    s.backend
                        .observeChat(s.groupId)
                        .first()!!
                        .members,
            )
        }

    private companion object {
        val ANYA = UserId("anya")
        val BORIS = UserId("boris")
        val VERA = UserId("vera")
    }
}
