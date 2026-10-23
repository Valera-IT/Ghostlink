package io.github.valerait.ghostlink.core.data

import dev.gitlive.firebase.firestore.Timestamp
import io.github.valerait.ghostlink.core.firebase.ChatDto
import io.github.valerait.ghostlink.core.firebase.ChatPrefsDto
import io.github.valerait.ghostlink.core.firebase.DeviceDto
import io.github.valerait.ghostlink.core.firebase.GROUP_TYPE
import io.github.valerait.ghostlink.core.firebase.LinkRequestDto
import io.github.valerait.ghostlink.core.firebase.MessageDto
import io.github.valerait.ghostlink.core.firebase.ReplyToDto
import io.github.valerait.ghostlink.core.firebase.UserDto
import io.github.valerait.ghostlink.core.model.Chat
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.ChatPrefs
import io.github.valerait.ghostlink.core.model.Device
import io.github.valerait.ghostlink.core.model.DeviceId
import io.github.valerait.ghostlink.core.model.DisappearingTimer
import io.github.valerait.ghostlink.core.model.GroupInfo
import io.github.valerait.ghostlink.core.model.LastMessage
import io.github.valerait.ghostlink.core.model.LinkRequest
import io.github.valerait.ghostlink.core.model.LinkRequestId
import io.github.valerait.ghostlink.core.model.Message
import io.github.valerait.ghostlink.core.model.MessageId
import io.github.valerait.ghostlink.core.model.Platform
import io.github.valerait.ghostlink.core.model.ReplyTo
import io.github.valerait.ghostlink.core.model.User
import io.github.valerait.ghostlink.core.model.UserId
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// DTO → domain. Чистые функции: тестируются без Firestore, а снимки документов разбирают репозитории.

internal val Timestamp.instant: Instant get() = Instant.fromEpochSeconds(seconds, nanoseconds)

internal fun Instant.toTimestamp(): Timestamp = Timestamp(epochSeconds, nanosecondsOfSecond)

// Платформа хранится строкой в нижнем регистре: так её пишут и читают все клиенты.
internal val Platform.wireName: String get() = name.lowercase()

internal fun platformOf(wireName: String?): Platform = Platform.entries.firstOrNull { it.wireName == wireName } ?: Platform.Unknown

internal fun UserDto.toUser(id: String) = User(id = UserId(id), name = name, online = online, lastSeen = lastSeen?.instant)

internal fun DeviceDto.toDevice(id: String) =
    Device(id = DeviceId(id), name = name, platform = platformOf(platform), lastActiveAt = lastActiveAt?.instant)

/** Документ без участников — битый или чужой формат: такой чат не показываем. */
internal fun ChatDto.toChat(id: String): Chat? {
    if (members.isEmpty()) return null
    return Chat(
        id = ChatId(id),
        members = members.map(::UserId),
        lastMessage =
            lastMessage?.let {
                LastMessage(text = it.text, senderId = UserId(it.senderId), sentAt = it.sentAt?.instant, ttl = it.ttl?.seconds)
            },
        updatedAt = updatedAt?.instant,
        lastRead = lastRead.toInstantMap(),
        typing = typing.toInstantMap(),
        disappearing = DisappearingTimer.of(ttl?.seconds),
        group = if (type == GROUP_TYPE) GroupInfo(name = name.orEmpty(), ownerId = UserId(owner.orEmpty())) else null,
    )
}

private fun Map<String, Timestamp>.toInstantMap(): Map<UserId, Instant> = entries.associate { (uid, ts) -> UserId(uid) to ts.instant }

internal fun ChatPrefsDto.toChatPrefs() =
    ChatPrefs(pinnedAt = pinnedAt?.instant, muted = muted, clearedAt = clearedAt?.instant, hidden = hidden)

internal fun MessageDto.toMessage(
    id: String,
    pending: Boolean,
) = Message(
    id = MessageId(id),
    senderId = UserId(senderId),
    text = text,
    sentAt = sentAt?.instant,
    pending = pending,
    replyTo = replyTo?.takeIf { it.id.isNotEmpty() }?.let { ReplyTo(MessageId(it.id), UserId(it.senderId), it.preview) },
    editedAt = editedAt?.instant,
    deletedAt = deletedAt?.instant,
    ttl = ttl?.seconds,
)

internal fun ReplyTo.toDto() = ReplyToDto(id = id.value, senderId = senderId.value, preview = preview)

/** Заявка без срока считается истёкшей: подтвердить её всё равно не дадут правила. */
internal fun LinkRequestDto.toLinkRequest(
    id: String,
    payload: ByteArray?,
) = LinkRequest(
    id = LinkRequestId(id),
    deviceName = deviceName,
    platform = platformOf(platform),
    expiresAt = expiresAt?.instant ?: Instant.DISTANT_PAST,
    payload = payload,
)
