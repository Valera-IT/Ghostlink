package io.github.valerait.ghostlink.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ModelsTest {
    private val a = UserId("a")
    private val b = UserId("b")

    private fun at(millis: Long) = Instant.fromEpochMilliseconds(millis)

    @Test
    fun chatIdDoesNotDependOnWhoAddsWhom() {
        val alice = UserId("alice")
        val bob = UserId("bob")
        assertEquals(ChatId.of(alice, bob), ChatId.of(bob, alice))
        assertEquals(ChatId("alice_bob"), ChatId.of(bob, alice))
    }

    @Test
    fun messageStatusFollowsServerAckAndPartnerReadTime() {
        val pending = Message(MessageId("1"), UserId("me"), "hi", sentAt = at(100), pending = true)
        val sent = pending.copy(pending = false)
        assertEquals(MessageStatus.SENDING, pending.status(readUpTo = at(500)))
        assertEquals(MessageStatus.SENT, sent.status(readUpTo = null))
        assertEquals(MessageStatus.SENT, sent.status(readUpTo = at(99)))
        assertEquals(MessageStatus.READ, sent.status(readUpTo = at(100)))
        assertEquals(MessageStatus.READ_ALL, sent.status(readUpTo = at(100), allReadUpTo = at(100)))
    }

    @Test
    fun chatIsUnreadOnlyForIncomingMessagesNewerThanLastRead() {
        val chat = Chat(ChatId("a_b"), listOf(a, b), LastMessage("hey", senderId = b, sentAt = at(200)))
        assertTrue(chat.isUnreadFor(a))
        assertFalse(chat.isUnreadFor(b))
        assertFalse(chat.copy(lastRead = mapOf(a to at(200))).isUnreadFor(a))
        assertTrue(chat.copy(lastRead = mapOf(a to at(150))).isUnreadFor(a))
    }

    @Test
    fun groupIsReadByAllOnlyWhenEveryoneElseRead() {
        val c = UserId("c")
        val group = Chat(ChatId("g"), listOf(a, b, c), group = GroupInfo("G", ownerId = a))
        assertNull(group.copy(lastRead = mapOf(b to at(10))).allReadUpTo(a))
        assertEquals(at(10), group.copy(lastRead = mapOf(b to at(10), c to at(20))).allReadUpTo(a))
        assertEquals(at(20), group.copy(lastRead = mapOf(b to at(10), c to at(20))).othersReadUpTo(a))
    }

    @Test
    fun typingMarkExpires() {
        val chat = Chat(ChatId("a_b"), listOf(a, b), typing = mapOf(b to at(1_000)))
        assertTrue(chat.isTyping(b, now = at(1_000) + TYPING_TIMEOUT - 1.milliseconds))
        assertFalse(chat.isTyping(b, now = at(1_000) + TYPING_TIMEOUT))
        assertEquals(b to at(1_000) + TYPING_TIMEOUT, chat.typingOther(a))
        assertNull(chat.typingOther(b))
    }

    @Test
    fun onlineStatusExpiresWithoutHeartbeat() {
        val user = User(UserId("u"), "U", online = true, lastSeen = at(1_000))
        assertTrue(user.isOnline(now = at(1_000) + ONLINE_TIMEOUT - 1.milliseconds))
        assertFalse(user.isOnline(now = at(1_000) + ONLINE_TIMEOUT))
        assertFalse(user.copy(online = false).isOnline(now = at(1_000)))
    }

    @Test
    fun disappearingMessageExpiresAfterTtlFromServerTime() {
        val message = Message(MessageId("1"), a, "x", sentAt = at(0), ttl = 1.hours)
        assertEquals(at(0) + 1.hours, message.expiresAt)
        assertFalse(message.isExpired(at(0) + 59.minutes))
        assertTrue(message.isExpired(at(0) + 1.hours))
        assertNull(message.copy(sentAt = null).expiresAt)
        assertEquals(DisappearingTimer.HOUR, DisappearingTimer.of(1.hours))
        assertNull(DisappearingTimer.of(2.hours))
    }

    @Test
    fun clearedChatHidesOnlyOlderMessages() {
        val prefs = ChatPrefs(clearedAt = at(100), hidden = true)
        val chat = Chat(ChatId("a_b"), listOf(a, b), LastMessage("old", b, at(100)))
        assertFalse(prefs.isVisible(chat))
        assertTrue(prefs.isVisible(chat.copy(lastMessage = LastMessage("new", b, at(101)))))
        assertFalse(prefs.isCleared(null))
    }

    @Test
    fun replyPreviewIsOneLine() {
        assertEquals("раз два три", ReplyTo.previewOf("  раз\n\nдва\tтри  "))
    }

    @Test
    fun longReplyPreviewIsCutToTheRulesLimitWithAnEllipsis() {
        val preview = ReplyTo.previewOf("слово ".repeat(50))
        assertTrue(preview.length <= ReplyTo.PREVIEW_MAX_LENGTH)
        assertTrue(preview.endsWith("…"))
    }

    @Test
    fun shortReplyPreviewIsKeptAsIs() {
        assertEquals("x".repeat(100), ReplyTo.previewOf("x".repeat(100)))
    }
}
