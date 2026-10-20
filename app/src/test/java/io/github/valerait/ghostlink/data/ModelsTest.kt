package io.github.valerait.ghostlink.data

import io.github.valerait.ghostlink.data.model.Chat
import io.github.valerait.ghostlink.data.model.ChatIds
import io.github.valerait.ghostlink.data.model.LastMessage
import io.github.valerait.ghostlink.data.model.Message
import io.github.valerait.ghostlink.data.model.MessageStatus
import io.github.valerait.ghostlink.data.model.ONLINE_TIMEOUT_MS
import io.github.valerait.ghostlink.data.model.ReplyTo
import io.github.valerait.ghostlink.data.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    @Test
    fun `chat id does not depend on who adds whom`() {
        assertEquals(ChatIds.of("alice", "bob"), ChatIds.of("bob", "alice"))
        assertEquals("alice_bob", ChatIds.of("bob", "alice"))
    }

    @Test
    fun `message status follows server ack and partner read time`() {
        val pending = Message("1", "me", "hi", sentAt = 100, pending = true)
        val sent = pending.copy(pending = false)
        assertEquals(MessageStatus.SENDING, pending.status(readUpTo = 500))
        assertEquals(MessageStatus.SENT, sent.status(readUpTo = null))
        assertEquals(MessageStatus.SENT, sent.status(readUpTo = 99))
        assertEquals(MessageStatus.READ, sent.status(readUpTo = 100))
    }

    @Test
    fun `chat is unread only for incoming messages newer than last read`() {
        val chat = Chat("a_b", listOf("a", "b"), LastMessage("hey", senderId = "b", sentAt = 200))
        assertTrue(chat.isUnreadFor("a"))
        assertFalse(chat.isUnreadFor("b"))
        assertFalse(chat.copy(lastRead = mapOf("a" to 200L)).isUnreadFor("a"))
        assertTrue(chat.copy(lastRead = mapOf("a" to 150L)).isUnreadFor("a"))
    }

    @Test
    fun `online status expires without heartbeat`() {
        val user = User("u", "U", online = true, lastSeen = 1_000)
        assertTrue(user.isOnline(now = 1_000 + ONLINE_TIMEOUT_MS - 1))
        assertFalse(user.isOnline(now = 1_000 + ONLINE_TIMEOUT_MS))
        assertFalse(user.copy(online = false).isOnline(now = 1_000))
    }

    @Test
    fun `reply preview is one line`() {
        assertEquals("раз два три", ReplyTo.previewOf("  раз\n\nдва\tтри  "))
    }

    @Test
    fun `long reply preview is cut to the rules limit with an ellipsis`() {
        val preview = ReplyTo.previewOf("слово ".repeat(50))
        assertTrue(preview.length <= ReplyTo.PREVIEW_MAX_LENGTH)
        assertTrue(preview.endsWith("…"))
    }

    @Test
    fun `short reply preview is kept as is`() {
        assertEquals("x".repeat(100), ReplyTo.previewOf("x".repeat(100)))
    }
}
