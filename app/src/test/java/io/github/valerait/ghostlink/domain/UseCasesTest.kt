package io.github.valerait.ghostlink.domain

import io.github.valerait.ghostlink.core.domain.repository.DeviceRepository
import io.github.valerait.ghostlink.core.domain.usecase.AddFriendUseCase
import io.github.valerait.ghostlink.core.domain.usecase.ApproveDeviceLinkUseCase
import io.github.valerait.ghostlink.core.domain.usecase.CreateAccountUseCase
import io.github.valerait.ghostlink.core.domain.usecase.DeleteMessageUseCase
import io.github.valerait.ghostlink.core.domain.usecase.EditMessageUseCase
import io.github.valerait.ghostlink.core.domain.usecase.LeaveGroupUseCase
import io.github.valerait.ghostlink.core.domain.usecase.RestoreWithRecoveryCodeUseCase
import io.github.valerait.ghostlink.core.domain.usecase.SendMessageUseCase
import io.github.valerait.ghostlink.core.domain.usecase.SignOutEverywhereUseCase
import io.github.valerait.ghostlink.core.domain.usecase.SignOutUseCase
import io.github.valerait.ghostlink.core.model.AccountKey
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.Device
import io.github.valerait.ghostlink.core.model.DeviceId
import io.github.valerait.ghostlink.core.model.DisappearingTimer
import io.github.valerait.ghostlink.core.model.GhostlinkError
import io.github.valerait.ghostlink.core.model.LinkCode
import io.github.valerait.ghostlink.core.model.Message
import io.github.valerait.ghostlink.core.model.MessageId
import io.github.valerait.ghostlink.core.model.Platform
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.data.demo.InMemoryBackend
import io.github.valerait.ghostlink.data.link.JvmLinkCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Use case на InMemoryBackend. На этапе 3.4 переедут в commonTest core:domain вместе с фейками из core:testing.
class UseCasesTest {
    private fun TestScope.backend() = InMemoryBackend(backgroundScope, seedDemoData = false)

    private suspend fun InMemoryBackend.messages(chatId: ChatId): List<Message> = observeMessages(chatId).first()

    @Test
    fun `account gets a trimmed profile name and a blank name is rejected`() =
        runTest {
            val backend = backend()
            val create = CreateAccountUseCase(backend, backend)
            assertTrue(runCatching { create("   ") }.exceptionOrNull() is IllegalArgumentException)
            assertNull(backend.account.value)

            val account = create("  Валера  ")
            assertEquals("Валера", backend.getUser(account.id)?.name)
            assertNotNull(backend.accountKey.value)
        }

    @Test
    fun `temporary account left from an abandoned link becomes the real one`() =
        runTest {
            val backend = backend()
            val temp = backend.signInAnonymously()
            val account = CreateAccountUseCase(backend, backend)("Аня")
            assertEquals(temp.id, account.id)
        }

    @Test
    fun `recovery code tells a typo from a wrong key`() =
        runTest {
            val backend = backend()
            val account = backend.createAccount()
            val code = checkNotNull(backend.accountKey.value).toRecoveryCode()
            backend.signOut()
            val restore = RestoreWithRecoveryCodeUseCase(backend)

            val typo = runCatching { restore("12345") }.exceptionOrNull()
            assertTrue(typo is GhostlinkError.MalformedRecoveryCode)
            val stranger = AccountKey.generate().toRecoveryCode()
            assertTrue(runCatching { restore(stranger) }.exceptionOrNull() is GhostlinkError.InvalidAccountKey)
            assertEquals(account.id, restore(code.lowercase()).id)
        }

    @Test
    fun `sign out everywhere keeps only this device and invalidates the old recovery code`() =
        runTest {
            val backend = backend()
            val me = backend.createAccount().id
            val oldCode = checkNotNull(backend.accountKey.value).toRecoveryCode()
            backend.registerDevice(me, Device(PHONE, "Pixel", Platform.Android))
            backend.registerDevice(me, Device(LAPTOP, "MacBook", Platform.MacOs))

            val newCode = SignOutEverywhereUseCase(backend, backend)(PHONE)

            assertEquals(listOf(PHONE), backend.observeDevices(me).first().map { it.id })
            backend.signOut()
            assertTrue(runCatching { RestoreWithRecoveryCodeUseCase(backend)(oldCode) }.isFailure)
            assertEquals(me, RestoreWithRecoveryCodeUseCase(backend)(newCode).id)
        }

    @Test
    fun `sign out removes this device from the registry`() =
        runTest {
            val backend = backend()
            val me = backend.createAccount().id
            backend.registerDevice(me, Device(PHONE, "Pixel", Platform.Android))
            // Демо-бэкенд забывает реестр при выходе, поэтому удаление запоминаем сами.
            val removed = mutableListOf<DeviceId>()
            val devices =
                object : DeviceRepository by backend {
                    override suspend fun removeDevice(
                        userId: UserId,
                        deviceId: DeviceId,
                    ) {
                        removed += deviceId
                        backend.removeDevice(userId, deviceId)
                    }
                }

            SignOutUseCase(backend, backend, devices)(PHONE)

            assertEquals(listOf(PHONE), removed)
            assertNull(backend.account.value)
        }

    @Test
    fun `link request can be approved only once and only before it expires`() =
        runTest {
            var now = Instant.fromEpochMilliseconds(1_000_000)
            val clock =
                object : Clock {
                    override fun now() = now
                }
            val backend = InMemoryBackend(backgroundScope, clock = clock, seedDemoData = false)
            backend.signInAnonymously()
            val requestId = backend.createLinkRequest("Pixel 9", Platform.Android)
            val code = LinkCode(requestId, JvmLinkCipher.newKeyPair().publicKey)
            backend.createAccount()
            val approve = ApproveDeviceLinkUseCase(backend, backend, JvmLinkCipher, clock)

            assertEquals("Pixel 9", approve.load(code).deviceName)
            approve(code)
            assertTrue(runCatching { approve.load(code) }.exceptionOrNull() is GhostlinkError.LinkAlreadyApproved)

            backend.signOut()
            backend.signInAnonymously()
            val late = LinkCode(backend.createLinkRequest("iPhone", Platform.Ios), code.publicKey)
            now += 3.minutes
            assertTrue(runCatching { approve.load(late) }.exceptionOrNull() is GhostlinkError.LinkExpired)
            backend.deleteLinkRequest(late.requestId)
            assertTrue(runCatching { approve.load(late) }.exceptionOrNull() is GhostlinkError.LinkExpired)
        }

    @Test
    fun `adding yourself is refused, adding a friend opens the pair chat`() =
        runTest {
            val backend = backend()
            val me = backend.createAccount().id
            val add = AddFriendUseCase(backend, backend)
            assertTrue(runCatching { add(me) }.isFailure)
            val chatId = add(FRIEND)
            assertEquals(listOf(me, FRIEND).sortedBy { it.value }, backend.observeChat(chatId).first()?.members)
        }

    @Test
    fun `message text is trimmed, blank is not sent and the chat timer becomes its ttl`() =
        runTest {
            val backend = backend()
            val me = backend.createAccount().id
            val chatId = backend.startChat(me, FRIEND)
            val send = SendMessageUseCase(backend)

            assertFalse(send(chatId, me, " \n "))
            assertTrue(send(chatId, me, "  привет  ", timer = DisappearingTimer.DAY))
            assertTrue(send(chatId, me, "x".repeat(Message.TEXT_MAX_LENGTH + 10)))

            val (first, long) = backend.messages(chatId)
            assertEquals("привет", first.text)
            assertEquals(DisappearingTimer.DAY.duration, first.ttl)
            assertEquals(Message.TEXT_MAX_LENGTH, long.text.length)
        }

    @Test
    fun `reply to an unconfirmed message is sent as a plain message`() =
        runTest {
            val backend = backend()
            val me = backend.createAccount().id
            val chatId = backend.startChat(me, FRIEND)
            val pending = Message(MessageId("p"), me, "ещё летит", sentAt = null, pending = true)

            SendMessageUseCase(backend)(chatId, me, "ответ", replyTo = pending)

            assertNull(backend.messages(chatId).single().replyTo)
        }

    @Test
    fun `editing and deleting the latest message updates the chat preview`() =
        runTest {
            val backend = backend()
            val me = backend.createAccount().id
            val chatId = backend.startChat(me, FRIEND)
            backend.sendMessage(chatId, me, "первое")
            backend.sendMessage(chatId, me, "второе")
            val (first, second) = backend.messages(chatId)

            EditMessageUseCase(backend)(chatId, backend.messages(chatId), second, "  второе!  ")
            assertEquals(
                "второе!",
                backend
                    .observeChat(chatId)
                    .first()
                    ?.lastMessage
                    ?.text,
            )

            assertTrue(DeleteMessageUseCase(backend)(chatId, me, backend.messages(chatId), second.id))
            assertEquals(
                first.text,
                backend
                    .observeChat(chatId)
                    .first()
                    ?.lastMessage
                    ?.text,
            )
        }

    @Test
    fun `only own and not yet deleted messages can be deleted`() =
        runTest {
            val backend = backend()
            val me = backend.createAccount().id
            val chatId = backend.startChat(me, FRIEND)
            backend.sendMessage(chatId, FRIEND, "чужое")
            val foreign = backend.messages(chatId).single()
            val delete = DeleteMessageUseCase(backend)

            assertFalse(delete(chatId, me, backend.messages(chatId), foreign.id))
            assertFalse(backend.messages(chatId).single().deleted)
        }

    @Test
    fun `admin leaving the group hands it over to the next member`() =
        runTest {
            val backend = backend()
            val me = backend.createAccount().id
            val groupId = backend.createGroup(me, "Митап", listOf(FRIEND, OTHER))
            val group = checkNotNull(backend.observeChat(groupId).first())

            LeaveGroupUseCase(backend)(group, me)

            val after = checkNotNull(backend.observeChat(groupId).first())
            assertFalse(me in after.members)
            assertEquals(after.members.first(), after.group?.ownerId)
            assertTrue(
                backend
                    .observeChatPrefs(me)
                    .first()[groupId]
                    ?.hidden == true,
            )
        }

    private companion object {
        val FRIEND = UserId("friend")
        val OTHER = UserId("other")
        val PHONE = DeviceId("phone")
        val LAPTOP = DeviceId("laptop")
    }
}
