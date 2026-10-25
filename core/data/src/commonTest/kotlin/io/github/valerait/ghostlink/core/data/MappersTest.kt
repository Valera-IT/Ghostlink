package io.github.valerait.ghostlink.core.data

import dev.gitlive.firebase.firestore.Timestamp
import io.github.valerait.ghostlink.core.firebase.AttachmentDto
import io.github.valerait.ghostlink.core.firebase.ChatDto
import io.github.valerait.ghostlink.core.firebase.ChatPrefsDto
import io.github.valerait.ghostlink.core.firebase.DeviceDto
import io.github.valerait.ghostlink.core.firebase.LastMessageDto
import io.github.valerait.ghostlink.core.firebase.LinkRequestDto
import io.github.valerait.ghostlink.core.firebase.MessageDto
import io.github.valerait.ghostlink.core.firebase.ReplyToDto
import io.github.valerait.ghostlink.core.firebase.UserDto
import io.github.valerait.ghostlink.core.model.Attachment
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.DisappearingTimer
import io.github.valerait.ghostlink.core.model.GroupInfo
import io.github.valerait.ghostlink.core.model.MediaKey
import io.github.valerait.ghostlink.core.model.MessageId
import io.github.valerait.ghostlink.core.model.Platform
import io.github.valerait.ghostlink.core.model.ReplyTo
import io.github.valerait.ghostlink.core.model.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class MappersTest {
    private val at = Instant.fromEpochSeconds(1_760_000_000, 123_000_000)
    private val ts = Timestamp(at.epochSeconds, at.nanosecondsOfSecond)

    @Test
    fun timestampRoundTripKeepsNanoseconds() {
        assertEquals(at, ts.instant)
        assertEquals(at, at.toTimestamp().instant)
    }

    @Test
    fun platformIsStoredLowercaseAndUnknownFallsBack() {
        assertEquals("macos", Platform.MacOs.wireName)
        Platform.entries.forEach { assertEquals(it, platformOf(it.wireName)) }
        assertEquals(Platform.Unknown, platformOf("symbian"))
        assertEquals(Platform.Unknown, platformOf(null))
    }

    @Test
    fun userAndDevice() {
        val user = UserDto(name = "Аня", online = true, lastSeen = ts).toUser("u1")
        assertEquals(UserId("u1"), user.id)
        assertEquals("Аня", user.name)
        assertTrue(user.online)
        assertEquals(at, user.lastSeen)
        assertNull(user.avatarVersion)
        assertEquals(7L, UserDto(name = "Аня", avatarVersion = 7).toUser("u1").avatarVersion)

        val device = DeviceDto(name = "Pixel", platform = "android").toDevice("d1")
        assertEquals(Platform.Android, device.platform)
        assertNull(device.lastActiveAt)
    }

    @Test
    fun chatWithLastMessageReadsAndTyping() {
        val chat =
            ChatDto(
                members = listOf("a", "b"),
                lastMessage = LastMessageDto(text = "Привет", senderId = "a", sentAt = ts, ttl = 3_600),
                updatedAt = ts,
                lastRead = mapOf("b" to ts),
                typing = mapOf("a" to ts),
                ttl = 3_600,
            ).toChat("a_b")!!

        assertEquals(ChatId("a_b"), chat.id)
        assertEquals(listOf(UserId("a"), UserId("b")), chat.members)
        assertEquals("Привет", chat.lastMessage?.text)
        assertEquals(1.hours, chat.lastMessage?.ttl)
        assertEquals(mapOf(UserId("b") to at), chat.lastRead)
        assertEquals(mapOf(UserId("a") to at), chat.typing)
        assertEquals(DisappearingTimer.of(1.hours), chat.disappearing)
        assertNull(chat.group)
    }

    @Test
    fun groupAndBrokenChat() {
        val group = ChatDto(members = listOf("a"), type = "group", name = "Друзья", owner = "a").toChat("g1")!!
        assertEquals(GroupInfo("Друзья", UserId("a")), group.group)

        assertNull(ChatDto().toChat("empty"))
    }

    @Test
    fun messageWithReplyAndDeletedMessage() {
        val reply = ReplyTo(MessageId("m0"), UserId("b"), "Что?")
        val message =
            MessageDto(senderId = "a", text = "Ответ", sentAt = ts, replyTo = reply.toDto(), editedAt = ts)
                .toMessage("m1", pending = false)
        assertEquals(reply, message.replyTo)
        assertEquals(at, message.editedAt)

        val deleted = MessageDto(senderId = "a", deletedAt = ts).toMessage("m2", pending = true)
        assertTrue(deleted.deleted)
        assertTrue(deleted.pending)
        assertNull(deleted.sentAt)

        // replyTo без id — битая цитата: показываем сообщение без неё.
        assertNull(MessageDto(replyTo = ReplyToDto(preview = "?")).toMessage("m3", pending = false).replyTo)
    }

    @Test
    fun chatPrefs() {
        val prefs = ChatPrefsDto(pinnedAt = ts, muted = true, hidden = true).toChatPrefs()
        assertTrue(prefs.pinned)
        assertTrue(prefs.muted)
        assertTrue(prefs.hidden)
        assertNull(prefs.clearedAt)
    }

    @Test
    fun linkRequestWithoutExpiryIsExpired() {
        val request = LinkRequestDto(deviceName = "MacBook", platform = "macos").toLinkRequest("r1", payload = null)
        assertEquals(Platform.MacOs, request.platform)
        assertEquals(Instant.DISTANT_PAST, request.expiresAt)
        assertTrue(request.isExpired(at))
    }

    @Test
    fun photoMessageAndUnknownAttachments() {
        val photo = Attachment(MediaKey("p/c/a/1-x.jpg"), MediaKey("p/c/a/1-y.jpg"), width = 1280, height = 960, size = 200_000)
        val message = MessageDto(senderId = "a", sentAt = ts, attachment = photo.toDto()).toMessage("m1", pending = false)
        assertEquals(photo, message.attachment)
        assertEquals("", message.text)

        // Фото на ответе и в превью чата.
        val reply = ReplyTo(MessageId("m1"), UserId("a"), "", photo = true)
        assertEquals(reply, MessageDto(replyTo = reply.toDto()).toMessage("m2", pending = false).replyTo)
        assertTrue(ChatDto(members = listOf("a", "b"), lastMessage = LastMessageDto(photo = true)).toChat("a_b")!!.lastMessage!!.photo)

        // Вложение из будущей версии (видео) или без файлов — сообщение остаётся без него.
        assertNull(
            MessageDto(attachment = AttachmentDto(type = "video", key = "k", thumbKey = "t")).toMessage("m3", pending = false).attachment,
        )
        assertNull(MessageDto(attachment = AttachmentDto(type = "photo")).toMessage("m4", pending = false).attachment)
    }
}
