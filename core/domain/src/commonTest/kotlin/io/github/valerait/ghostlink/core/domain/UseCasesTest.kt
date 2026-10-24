package io.github.valerait.ghostlink.core.domain

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
import io.github.valerait.ghostlink.core.testing.FakeBackend
import io.github.valerait.ghostlink.core.testing.FakeLinkCipher
import io.github.valerait.ghostlink.core.testing.TestClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class UseCasesTest {
    private fun backend() = FakeBackend()

    private suspend fun FakeBackend.messages(chatId: ChatId): List<Message> = observeMessages(chatId).first()

    @Test
    fun accountGetsATrimmedProfileNameAndABlankNameIsRejected() =
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
    fun temporaryAccountLeftFromAnAbandonedLinkBecomesTheRealOne() =
        runTest {
            val backend = backend()
            val temp = backend.signInAnonymously()
            val account = CreateAccountUseCase(backend, backend)("Аня")
            assertEquals(temp.id, account.id)
        }

    @Test
    fun recoveryCodeTellsATypoFromAWrongKey() =
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
    fun signOutEverywhereKeepsOnlyThisDeviceAndInvalidatesTheOldRecoveryCode() =
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
    fun signOutRemovesThisDeviceFromTheRegistry() =
        runTest {
            val backend = backend()
            val me = backend.createAccount().id
            backend.registerDevice(me, Device(PHONE, "Pixel", Platform.Android))

            SignOutUseCase(backend, backend, backend)(PHONE)

            assertEquals(emptyList(), backend.observeDevices(me).first())
            assertNull(backend.account.value)
        }

    @Test
    fun linkRequestCanBeApprovedOnlyOnceAndOnlyBeforeItExpires() =
        runTest {
            val clock = TestClock()
            val backend = FakeBackend(clock)
            backend.signInAnonymously()
            val requestId = backend.createLinkRequest("Pixel 9", Platform.Android)
            val code = LinkCode(requestId, FakeLinkCipher.newKeyPair().publicKey)
            backend.createAccount()
            val approve = ApproveDeviceLinkUseCase(backend, backend, FakeLinkCipher, clock)

            assertEquals("Pixel 9", approve.load(code).deviceName)
            approve(code)
            assertTrue(runCatching { approve.load(code) }.exceptionOrNull() is GhostlinkError.LinkAlreadyApproved)

            backend.signOut()
            backend.signInAnonymously()
            val late = LinkCode(backend.createLinkRequest("iPhone", Platform.Ios), code.publicKey)
            clock.advanceBy(3.minutes)
            assertTrue(runCatching { approve.load(late) }.exceptionOrNull() is GhostlinkError.LinkExpired)
            backend.deleteLinkRequest(late.requestId)
            assertTrue(runCatching { approve.load(late) }.exceptionOrNull() is GhostlinkError.LinkExpired)
        }

    @Test
    fun addingYourselfIsRefusedAddingAFriendOpensThePairChat() =
        runTest {
            val backend = backend()
            val me = backend.createAccount().id
            val add = AddFriendUseCase(backend, backend)
            assertTrue(runCatching { add(me) }.isFailure)
            val chatId = add(FRIEND)
            assertEquals(listOf(me, FRIEND).sortedBy { it.value }, backend.observeChat(chatId).first()?.members)
        }

    @Test
    fun messageTextIsTrimmedBlankIsNotSentAndTheChatTimerBecomesItsTtl() =
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
    fun replyToAnUnconfirmedMessageIsSentAsAPlainMessage() =
        runTest {
            val backend = backend()
            val me = backend.createAccount().id
            val chatId = backend.startChat(me, FRIEND)
            val pending = Message(MessageId("p"), me, "ещё летит", sentAt = null, pending = true)

            SendMessageUseCase(backend)(chatId, me, "ответ", replyTo = pending)

            assertNull(backend.messages(chatId).single().replyTo)
        }

    @Test
    fun editingAndDeletingTheLatestMessageUpdatesTheChatPreview() =
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
    fun onlyOwnAndNotYetDeletedMessagesCanBeDeleted() =
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
    fun adminLeavingTheGroupHandsItOverToTheNextMember() =
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
