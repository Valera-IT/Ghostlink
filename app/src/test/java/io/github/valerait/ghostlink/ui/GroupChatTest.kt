package io.github.valerait.ghostlink.ui

import androidx.lifecycle.SavedStateHandle
import io.github.valerait.ghostlink.MainDispatcherRule
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.MessageStatus
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.data.demo.InMemoryBackend
import io.github.valerait.ghostlink.di.AppConfig
import io.github.valerait.ghostlink.ui.chat.ChatRow
import io.github.valerait.ghostlink.ui.chat.ChatViewModel
import io.github.valerait.ghostlink.ui.chats.ChatListViewModel
import io.github.valerait.ghostlink.ui.group.GroupInfoViewModel
import io.github.valerait.ghostlink.ui.group.PickMembersViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

class GroupChatTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private var now = Instant.fromEpochMilliseconds(1_000_000)
    private val clock =
        object : Clock {
            override fun now() = now
        }

    private class Setup(
        val backend: InMemoryBackend,
        val me: UserId,
        val groupId: ChatId,
    )

    /** Я, Аня и Борис — друзья; группу создаю я через экран выбора участников. */
    private suspend fun TestScope.setUp(): Setup {
        val backend = InMemoryBackend(backgroundScope, clock = clock, seedDemoData = false)
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
        pick.toggle(ANYA)
        pick.toggle(BORIS)
        pick.create("  Митап  ")
        val groupId = checkNotNull(pick.state.value.doneChatId)
        return Setup(backend, me, groupId)
    }

    private fun TestScope.chatVm(s: Setup) =
        ChatViewModel(SavedStateHandle(mapOf("chatId" to s.groupId.value)), s.backend, s.backend, s.backend, clock).also { vm ->
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
    fun `two ticks when one member read, three when everyone did`() =
        runTest {
            val s = setUp()
            val vm = chatVm(s)
            vm.send("всем привет")

            fun status() =
                vm.state.value.rows
                    .filterIsInstance<ChatRow.Msg>()
                    .single()
                    .item.status
            assertEquals(MessageStatus.SENT, status())

            now += 1.seconds
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

            val list = ChatListViewModel(s.backend, s.backend, s.backend, AppConfig(demoMode = false), clock)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { list.state.collect {} }
            val row =
                list.state.value.items
                    .first { it.chat.id == s.groupId }
            assertEquals("Митап", row.title)
            assertEquals("Аня", row.lastSenderName)
            assertEquals("Борис", row.typingName)
        }

    @Test
    fun `admin removes a member and leaving passes admin rights on`() =
        runTest {
            val s = setUp()
            val info = GroupInfoViewModel(SavedStateHandle(mapOf("chatId" to s.groupId.value)), s.backend, s.backend, s.backend)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { info.state.collect {} }
            assertTrue(info.state.value.isOwner)
            assertEquals(
                s.me,
                info.state.value.members
                    .first()
                    .id,
            )

            info.remove(BORIS)
            assertEquals(
                setOf(s.me, ANYA),
                s.backend
                    .observeChat(s.groupId)
                    .first()!!
                    .members
                    .toSet(),
            )

            info.leave()
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
            pick.toggle(VERA)
            pick.addSelected()
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
