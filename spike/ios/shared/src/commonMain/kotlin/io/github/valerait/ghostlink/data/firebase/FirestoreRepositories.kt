package io.github.valerait.ghostlink.data.firebase

import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.BaseTimestamp
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FieldPath
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.ServerTimestampBehavior
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.fromMilliseconds
import dev.gitlive.firebase.firestore.toMilliseconds
import io.github.valerait.ghostlink.data.ChatRepository
import io.github.valerait.ghostlink.data.DeviceRepository
import io.github.valerait.ghostlink.data.LinkRepository
import io.github.valerait.ghostlink.data.UserRepository
import io.github.valerait.ghostlink.model.Chat
import io.github.valerait.ghostlink.model.ChatIds
import io.github.valerait.ghostlink.model.Device
import io.github.valerait.ghostlink.model.LINK_TTL_MS
import io.github.valerait.ghostlink.model.LastMessage
import io.github.valerait.ghostlink.model.LinkRequest
import io.github.valerait.ghostlink.model.Message
import io.github.valerait.ghostlink.model.ReplyTo
import io.github.valerait.ghostlink.model.User
import io.github.valerait.ghostlink.platform.approveWithPayload
import io.github.valerait.ghostlink.platform.logWarning
import io.github.valerait.ghostlink.platform.nowMillis
import io.github.valerait.ghostlink.platform.payloadBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// Схема Firestore та же, что у Android (см. firestore.rules). Документы читаются через DTO,
// серверное время пишется сентинелом Timestamp.ServerTimestamp / FieldValue.serverTimestamp.

private val ESTIMATE = ServerTimestampBehavior.ESTIMATE

private fun Timestamp.millis(): Long = toMilliseconds().toLong()

// Ошибку слушателя (например, отказ правил) только логируем: упавший поток уронил бы экран.
private fun <T> Flow<T>.logErrors(what: String): Flow<T> = catch { logWarning("Firestore", "Listen failed: $what", it) }

// ---------- users ----------

@Serializable
private class UserDto(val name: String = "", val online: Boolean = false, val lastSeen: Timestamp? = null)

@Serializable
private class ProfileWrite(val name: String, val online: Boolean, val lastSeen: BaseTimestamp)

private fun DocumentSnapshot.toUser(): User? {
    if (!exists) return null
    val dto = data<UserDto>(ESTIMATE)
    return User(id = id, name = dto.name, online = dto.online, lastSeen = dto.lastSeen?.millis())
}

class FirestoreUserRepository(db: FirebaseFirestore) : UserRepository {
    private val users = db.collection("users")

    override fun observeUser(id: String): Flow<User?> =
        users.document(id).snapshots.map { it.toUser() }.logErrors("users/$id")

    override fun observeUsers(ids: List<String>): Flow<Map<String, User>> {
        if (ids.isEmpty()) return flowOf(emptyMap())
        // whereIn принимает не больше 30 значений.
        val parts = ids.chunked(30).map { chunk ->
            users.where { FieldPath.documentId inArray chunk }.snapshots
                .map { qs -> qs.documents.mapNotNull { it.toUser() } }
                .logErrors("users in $chunk")
        }
        return combine(parts) { lists -> lists.flatMap { it }.associateBy { it.id } }
    }

    override suspend fun getUser(id: String): User? = users.document(id).get().toUser()

    override suspend fun saveProfile(id: String, name: String) {
        users.document(id).set(ProfileWrite(name, online = true, lastSeen = Timestamp.ServerTimestamp), merge = true)
    }

    // update, а не set: до онбординга профиля ещё нет, и создавать его без имени нельзя.
    override suspend fun setOnline(id: String, online: Boolean) {
        users.document(id).update("online" to online, "lastSeen" to FieldValue.serverTimestamp)
    }
}

// ---------- chats ----------

@Serializable
private class LastMessageDto(val text: String = "", val senderId: String = "", val sentAt: Timestamp? = null)

@Serializable
private class ChatDto(
    val members: List<String> = emptyList(),
    val lastMessage: LastMessageDto? = null,
    val updatedAt: Timestamp? = null,
    val lastRead: Map<String, Timestamp> = emptyMap(),
)

@Serializable
private class ChatCreate(val members: List<String>, val updatedAt: BaseTimestamp, val lastRead: Map<String, String>)

@Serializable
private class ReplyToDto(val id: String = "", val senderId: String = "", val preview: String = "")

@Serializable
private class MessageDto(
    val senderId: String = "",
    val text: String = "",
    val sentAt: Timestamp? = null,
    val replyTo: ReplyToDto? = null,
    val editedAt: Timestamp? = null,
    val deletedAt: Timestamp? = null,
)

@Serializable
private class MessageWrite(val senderId: String, val text: String, val sentAt: BaseTimestamp)

// Отдельный класс, а не replyTo = null в MessageWrite: правила не пускают поле replyTo со значением null.
@Serializable
private class ReplyWrite(val senderId: String, val text: String, val sentAt: BaseTimestamp, val replyTo: ReplyToDto)

private fun DocumentSnapshot.toChat(): Chat? {
    if (!exists) return null
    val dto = data<ChatDto>(ESTIMATE)
    if (dto.members.isEmpty()) return null
    return Chat(
        id = id,
        members = dto.members,
        lastMessage = dto.lastMessage?.let { LastMessage(it.text, it.senderId, it.sentAt?.millis()) },
        updatedAt = dto.updatedAt?.millis(),
        lastRead = dto.lastRead.mapValues { it.value.millis() },
    )
}

private fun DocumentSnapshot.toMessage(): Message {
    val dto = data<MessageDto>(ESTIMATE)
    return Message(
        id = id,
        senderId = dto.senderId,
        text = dto.text,
        sentAt = dto.sentAt?.millis(),
        pending = metadata.hasPendingWrites,
        replyTo = dto.replyTo?.let { ReplyTo(it.id, it.senderId, it.preview) },
        editedAt = dto.editedAt?.millis(),
        deletedAt = dto.deletedAt?.millis(),
    )
}

class FirestoreChatRepository(
    private val db: FirebaseFirestore,
    private val scope: CoroutineScope,
) : ChatRepository {
    private val chats = db.collection("chats")

    // Сортируем на клиенте, чтобы не требовать составной индекс members + updatedAt.
    override fun observeChats(userId: String): Flow<List<Chat>> =
        chats.where { "members" contains userId }.snapshots
            .map { qs -> qs.documents.mapNotNull { it.toChat() }.sortedByDescending { it.updatedAt ?: Long.MAX_VALUE } }
            .logErrors("chats of $userId")

    override fun observeChat(chatId: String): Flow<Chat?> =
        chats.document(chatId).snapshots.map { it.toChat() }.logErrors("chats/$chatId")

    // includeMetadataChanges — чтобы увидеть, как pending-сообщение подтвердилось сервером.
    override fun observeMessages(chatId: String): Flow<List<Message>> =
        chats.document(chatId).collection("messages")
            .orderBy("sentAt", Direction.ASCENDING)
            .limitToLast(MESSAGES_LIMIT)
            .snapshots(includeMetadataChanges = true)
            .map { qs -> qs.documents.map { it.toMessage() } }
            .logErrors("messages of $chatId")

    override fun sendMessage(chatId: String, senderId: String, text: String, replyTo: ReplyTo?) {
        val chatRef = chats.document(chatId)
        val messageRef = chatRef.collection("messages").document(newId())
        scope.launch {
            runCatching {
                val batch = db.batch()
                if (replyTo == null) {
                    batch.set(messageRef, MessageWrite(senderId, text, Timestamp.ServerTimestamp))
                } else {
                    val reply = ReplyToDto(replyTo.id, replyTo.senderId, replyTo.preview)
                    batch.set(messageRef, ReplyWrite(senderId, text, Timestamp.ServerTimestamp, reply))
                }
                batch
                    .update(
                        chatRef,
                        "lastMessage" to mapOf("text" to text, "senderId" to senderId, "sentAt" to FieldValue.serverTimestamp),
                        "updatedAt" to FieldValue.serverTimestamp,
                    )
                    .commit()
            }.onFailure { logWarning("Chats", "Send failed", it) }
        }
    }

    override fun editMessage(chatId: String, messageId: String, text: String, isLatest: Boolean) {
        val chatRef = chats.document(chatId)
        scope.launch {
            runCatching {
                val batch = db.batch()
                    .update(chatRef.collection("messages").document(messageId), "text" to text, "editedAt" to FieldValue.serverTimestamp)
                if (isLatest) batch.update(chatRef, FieldPath("lastMessage", "text") to text)
                batch.commit()
            }.onFailure { logWarning("Chats", "Edit failed", it) }
        }
    }

    override fun deleteMessage(chatId: String, messageId: String, isLatest: Boolean, previous: Message?) {
        val chatRef = chats.document(chatId)
        scope.launch {
            runCatching {
                // Сообщение не стирается целиком: остаётся отметка «Сообщение удалено».
                val batch = db.batch().update(
                    chatRef.collection("messages").document(messageId),
                    "text" to FieldValue.delete,
                    "replyTo" to FieldValue.delete,
                    "editedAt" to FieldValue.delete,
                    "deletedAt" to FieldValue.serverTimestamp,
                )
                if (isLatest) {
                    val last: Any = previous?.let {
                        mapOf(
                            "text" to it.text,
                            "senderId" to it.senderId,
                            "sentAt" to (it.sentAt?.let { ms -> Timestamp.fromMilliseconds(ms.toDouble()) } ?: FieldValue.serverTimestamp),
                        )
                    } ?: FieldValue.delete
                    batch.update(chatRef, "lastMessage" to last)
                }
                batch.commit()
            }.onFailure { logWarning("Chats", "Delete failed", it) }
        }
    }

    override fun markRead(chatId: String, userId: String) {
        scope.launch {
            runCatching { chats.document(chatId).update(FieldPath("lastRead", userId) to FieldValue.serverTimestamp) }
                .onFailure { logWarning("Chats", "markRead failed", it) }
        }
    }

    override suspend fun startChat(userId: String, friendId: String): String {
        require(userId != friendId) { "Cannot start a chat with yourself" }
        val id = ChatIds.of(userId, friendId)
        val ref = chats.document(id)
        if (!ref.get().exists) {
            ref.set(ChatCreate(listOf(userId, friendId).sorted(), Timestamp.ServerTimestamp, emptyMap()))
        }
        return id
    }

    private companion object {
        const val MESSAGES_LIMIT = 300L
    }
}

// Как автоматический id Firestore: 20 символов [A-Za-z0-9].
private fun newId(): String {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    val random = dev.whyoleg.cryptography.random.CryptographyRandom
    return CharArray(20) { alphabet[random.nextInt(alphabet.length)] }.concatToString()
}

// ---------- devices ----------

@Serializable
private class DeviceDto(val name: String = "", val platform: String = "", val lastActiveAt: Timestamp? = null)

@Serializable
private class DeviceWrite(val name: String, val platform: String, val lastActiveAt: BaseTimestamp)

private fun DocumentSnapshot.toDevice(): Device? {
    if (!exists) return null
    val dto = data<DeviceDto>(ESTIMATE)
    return Device(id = id, name = dto.name, platform = dto.platform, lastActiveAt = dto.lastActiveAt?.millis())
}

class FirestoreDeviceRepository(private val db: FirebaseFirestore) : DeviceRepository {
    private fun devices(userId: String) = db.collection("users").document(userId).collection("devices")

    override fun observeDevices(userId: String): Flow<List<Device>> =
        devices(userId).snapshots
            .map { qs -> qs.documents.mapNotNull { it.toDevice() }.sortedByDescending { it.lastActiveAt } }
            .logErrors("devices of $userId")

    override fun observeDevice(userId: String, deviceId: String): Flow<Device?> =
        devices(userId).document(deviceId).snapshots.map { it.toDevice() }.logErrors("device $deviceId")

    override suspend fun registerDevice(userId: String, device: Device) {
        devices(userId).document(device.id)
            .set(DeviceWrite(device.name.take(60), device.platform, Timestamp.ServerTimestamp), merge = true)
    }

    override suspend fun removeDevice(userId: String, deviceId: String) {
        devices(userId).document(deviceId).delete()
    }
}

// ---------- linkRequests ----------

@Serializable
private class LinkRequestDto(val deviceName: String = "", val platform: String = "", val expiresAt: Timestamp? = null)

@Serializable
private class LinkRequestWrite(
    val owner: String,
    val deviceName: String,
    val platform: String,
    val createdAt: BaseTimestamp,
    val expiresAt: Timestamp,
)

private fun DocumentSnapshot.toLinkRequest(): LinkRequest? {
    if (!exists) return null
    val dto = data<LinkRequestDto>()
    return LinkRequest(
        id = id,
        deviceName = dto.deviceName,
        platform = dto.platform,
        expiresAt = dto.expiresAt?.millis() ?: 0L,
        payload = payloadBytes(),
    )
}

class FirestoreLinkRepository(db: FirebaseFirestore, private val auth: FirebaseAuth) : LinkRepository {
    private val requests = db.collection("linkRequests")

    override suspend fun createLinkRequest(deviceName: String, platform: String): String {
        val owner = checkNotNull(auth.currentUser) { "Not signed in" }.uid
        val doc = requests.document(newId())
        // expiresAt считает клиент, но правила проверяют его по серверному времени.
        doc.set(
            LinkRequestWrite(
                owner = owner,
                deviceName = deviceName.take(60),
                platform = platform,
                createdAt = Timestamp.ServerTimestamp,
                expiresAt = Timestamp.fromMilliseconds((nowMillis() + LINK_TTL_MS).toDouble()),
            ),
        )
        return doc.id
    }

    override suspend fun getLinkRequest(id: String): LinkRequest? = requests.document(id).get().toLinkRequest()

    override fun observeLinkRequest(id: String): Flow<LinkRequest?> =
        requests.document(id).snapshots.map { it.toLinkRequest() }.logErrors("linkRequests/$id")

    override suspend fun approveLinkRequest(id: String, payload: ByteArray) {
        val approver = checkNotNull(auth.currentUser) { "Not signed in" }.uid
        requests.document(id).approveWithPayload(payload, approver)
    }

    override suspend fun deleteLinkRequest(id: String) {
        requests.document(id).delete()
    }
}
