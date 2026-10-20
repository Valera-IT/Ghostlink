package io.github.valerait.ghostlink

import io.github.valerait.ghostlink.model.Chat
import io.github.valerait.ghostlink.model.DisappearingTimer
import io.github.valerait.ghostlink.model.GroupInfo
import io.github.valerait.ghostlink.model.Message
import io.github.valerait.ghostlink.model.MessageStatus
import io.github.valerait.ghostlink.model.TYPING_TIMEOUT_MS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatModelTest {
    private val group = Chat("g", listOf("me", "a", "b"), group = GroupInfo("Группа", "me"))

    @Test
    fun groupTicks() {
        val message = Message("m", "me", "hi", sentAt = 100)
        val oneRead = group.copy(lastRead = mapOf("a" to 150L))
        assertEquals(MessageStatus.READ, message.status(oneRead.othersReadUpTo("me"), oneRead.allReadUpTo("me")))
        val allRead = group.copy(lastRead = mapOf("a" to 150L, "b" to 120L))
        assertEquals(MessageStatus.READ_ALL, message.status(allRead.othersReadUpTo("me"), allRead.allReadUpTo("me")))
        assertEquals(MessageStatus.SENT, message.status(group.othersReadUpTo("me"), group.allReadUpTo("me")))
    }

    @Test
    fun personalChatNeverReadAll() {
        val chat = Chat("c", listOf("me", "a"), lastRead = mapOf("a" to 150L))
        assertNull(chat.allReadUpTo("me"))
        assertEquals(MessageStatus.READ, Message("m", "me", "hi", sentAt = 100).status(chat.othersReadUpTo("me"), chat.allReadUpTo("me")))
    }

    @Test
    fun typingIgnoresMeAndFormerMembers() {
        val chat = group.copy(typing = mapOf("me" to 500L, "gone" to 900L, "a" to 300L))
        assertEquals("a" to 300L + TYPING_TIMEOUT_MS, chat.typingOther("me"))
    }

    @Test
    fun disappearingExpiry() {
        val message = Message("m", "a", "hi", sentAt = 1_000, ttl = DisappearingTimer.HOUR.seconds)
        assertFalse(message.isExpired(1_000 + 3_599_999))
        assertTrue(message.isExpired(1_000 + 3_600_000))
        assertFalse(Message("p", "a", "hi", sentAt = null, ttl = 3_600).isExpired(Long.MAX_VALUE))
    }
}
