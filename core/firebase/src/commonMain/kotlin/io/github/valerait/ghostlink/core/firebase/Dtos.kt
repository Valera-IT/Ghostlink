package io.github.valerait.ghostlink.core.firebase

import dev.gitlive.firebase.firestore.BaseTimestamp
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.serialization.Serializable

// Документы Firestore один в один. Схема записана ещё и в firestore.rules: меняется одно — меняй и второе.
// *Dto читают документ (у полей значения по умолчанию: старые документы могут быть неполными),
// *Write пишут его, и серверное время в них — Timestamp.ServerTimestamp.
//
//  users/{uid}                    UserDto
//  avatars/{uid}                  фото профиля: data — поле bytes, см. Avatars.kt
//  users/{uid}/devices/{id}       DeviceDto
//  users/{uid}/chatPrefs/{chatId} ChatPrefsDto — личные настройки, видны только владельцу
//  chats/{a_b}                    ChatDto; группа — chats/{autoId} с type = "group", name, owner
//  chats/{id}/messages/{id}       MessageDto
//  linkRequests/{id}              LinkRequestDto; payload — поле bytes, см. LinkPayload.kt

object Collections {
    const val USERS = "users"
    const val AVATARS = "avatars"
    const val DEVICES = "devices"
    const val CHAT_PREFS = "chatPrefs"
    const val CHATS = "chats"
    const val MESSAGES = "messages"
    const val LINK_REQUESTS = "linkRequests"
}

const val GROUP_TYPE = "group"

@Serializable
class UserDto(
    val name: String = "",
    val online: Boolean = false,
    val lastSeen: Timestamp? = null,
    val avatarVersion: Long? = null,
)

@Serializable
class ProfileWrite(
    val name: String,
    val online: Boolean,
    val lastSeen: BaseTimestamp,
)

@Serializable
class DeviceDto(
    val name: String = "",
    val platform: String = "",
    val lastActiveAt: Timestamp? = null,
)

@Serializable
class DeviceWrite(
    val name: String,
    val platform: String,
    val lastActiveAt: BaseTimestamp,
)

@Serializable
class LastMessageDto(
    val text: String = "",
    val senderId: String = "",
    val sentAt: Timestamp? = null,
    val ttl: Long? = null,
    val photo: Boolean = false,
)

// Поле на каждое поле документа, поэтому параметров много.
@Suppress("LongParameterList")
@Serializable
class ChatDto(
    val members: List<String> = emptyList(),
    val lastMessage: LastMessageDto? = null,
    val updatedAt: Timestamp? = null,
    val lastRead: Map<String, Timestamp> = emptyMap(),
    val typing: Map<String, Timestamp> = emptyMap(),
    val ttl: Long? = null,
    val type: String? = null,
    val name: String? = null,
    val owner: String? = null,
)

@Serializable
class ChatCreate(
    val members: List<String>,
    val updatedAt: BaseTimestamp,
    val lastRead: Map<String, String> = emptyMap(),
)

@Serializable
class GroupCreate(
    val name: String,
    val owner: String,
    val members: List<String>,
    val updatedAt: BaseTimestamp,
    val lastRead: Map<String, String> = emptyMap(),
    val type: String = GROUP_TYPE,
)

@Serializable
class ReplyToDto(
    val id: String = "",
    val senderId: String = "",
    val preview: String = "",
    val photo: Boolean = false,
)

/**
 * Фото в сообщении: ключи файлов в хранилище медиа и размеры. Числа — Long: GitLive на iOS пишет их дробными,
 * поэтому правила проверяют `is number`, а не `is int`.
 */
@Serializable
class AttachmentDto(
    val type: String = "",
    val key: String = "",
    val thumbKey: String = "",
    val size: Long = 0,
    val width: Long = 0,
    val height: Long = 0,
)

const val PHOTO_TYPE = "photo"

@Suppress("LongParameterList")
@Serializable
class MessageDto(
    val senderId: String = "",
    val text: String = "",
    val sentAt: Timestamp? = null,
    val replyTo: ReplyToDto? = null,
    val editedAt: Timestamp? = null,
    val deletedAt: Timestamp? = null,
    val ttl: Long? = null,
    val attachment: AttachmentDto? = null,
)

/**
 * Пишется с `encodeDefaults = false`: правила не пускают поля replyTo, ttl и attachment со значением null,
 * а пустой text у фото без подписи просто не пишется.
 */
@Serializable
class MessageWrite(
    val senderId: String,
    val text: String = "",
    val sentAt: BaseTimestamp,
    val replyTo: ReplyToDto? = null,
    val ttl: Long? = null,
    val attachment: AttachmentDto? = null,
)

@Serializable
class ChatPrefsDto(
    val pinnedAt: Timestamp? = null,
    val muted: Boolean = false,
    val clearedAt: Timestamp? = null,
    val hidden: Boolean = false,
)

@Serializable
class LinkRequestDto(
    val owner: String = "",
    val deviceName: String = "",
    val platform: String = "",
    val createdAt: Timestamp? = null,
    val expiresAt: Timestamp? = null,
    val approvedBy: String? = null,
)

@Serializable
class LinkRequestWrite(
    val owner: String,
    val deviceName: String,
    val platform: String,
    val createdAt: BaseTimestamp,
    val expiresAt: Timestamp,
)
