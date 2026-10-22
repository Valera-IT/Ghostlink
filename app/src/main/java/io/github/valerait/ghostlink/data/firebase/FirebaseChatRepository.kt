package io.github.valerait.ghostlink.data.firebase

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import io.github.valerait.ghostlink.core.domain.repository.ChatRepository
import io.github.valerait.ghostlink.core.model.Chat
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.ChatPrefs
import io.github.valerait.ghostlink.core.model.DisappearingTimer
import io.github.valerait.ghostlink.core.model.GroupInfo
import io.github.valerait.ghostlink.core.model.LastMessage
import io.github.valerait.ghostlink.core.model.Message
import io.github.valerait.ghostlink.core.model.MessageId
import io.github.valerait.ghostlink.core.model.ReplyTo
import io.github.valerait.ghostlink.core.model.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Схема:
 *  chats/{a_b}            members, lastMessage{text, senderId, sentAt, ttl?}, updatedAt, lastRead.{uid}, typing.{uid}, ttl?
 *  chats/{autoId}         то же + type: "group", name, owner — групповой чат
 *  chats/{id}/messages   senderId, text, sentAt, replyTo?{id, senderId, preview}, editedAt?, deletedAt?, ttl?
 *  users/{uid}/chatPrefs/{a_b}  pinnedAt?, muted?, clearedAt?, hidden? — личные настройки, видны только владельцу
 */
class FirebaseChatRepository(
    private val db: FirebaseFirestore,
) : ChatRepository {
    private val chats = db.collection("chats")

    private fun prefs(userId: UserId) = db.collection("users").document(userId.value).collection("chatPrefs")

    private fun updatePrefs(
        userId: UserId,
        chatId: ChatId,
        fields: Map<String, Any>,
    ) {
        prefs(userId)
            .document(chatId.value)
            .set(fields, SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "Chat prefs update failed", it) }
    }

    // Сортируем на клиенте, чтобы не требовать составной индекс members + updatedAt.
    override fun observeChats(userId: UserId): Flow<List<Chat>> =
        chats.whereArrayContains("members", userId.value).observe { qs ->
            qs.documents.mapNotNull { it.toChat() }.sortedByDescending { it.updatedAt ?: Instant.DISTANT_FUTURE }
        }

    override fun observeChat(chatId: ChatId): Flow<Chat?> = chats.document(chatId.value).observe { it.toChat() }

    // MetadataChanges.INCLUDE — чтобы увидеть, как pending-сообщение подтвердилось сервером.
    override fun observeMessages(chatId: ChatId): Flow<List<Message>> =
        chats
            .document(chatId.value)
            .collection("messages")
            .orderBy("sentAt", Query.Direction.ASCENDING)
            .limitToLast(MESSAGES_LIMIT)
            .observe(MetadataChanges.INCLUDE) { qs -> qs.documents.map { it.toMessage() } }

    override fun sendMessage(
        chatId: ChatId,
        senderId: UserId,
        text: String,
        replyTo: ReplyTo?,
        ttl: Duration?,
    ) {
        val chatRef = chats.document(chatId.value)
        val messageRef = chatRef.collection("messages").document()
        val now = FieldValue.serverTimestamp()
        val message = mutableMapOf<String, Any>("senderId" to senderId.value, "text" to text, "sentAt" to now)
        if (replyTo != null) {
            message["replyTo"] = mapOf("id" to replyTo.id.value, "senderId" to replyTo.senderId.value, "preview" to replyTo.preview)
        }
        val last = mutableMapOf<String, Any>("text" to text, "senderId" to senderId.value, "sentAt" to now)
        if (ttl != null) {
            message["ttl"] = ttl.inWholeSeconds
            last["ttl"] = ttl.inWholeSeconds
        }
        db
            .batch()
            .set(messageRef, message)
            .update(
                chatRef,
                mapOf(
                    "lastMessage" to last,
                    "updatedAt" to now,
                    // Сообщение отправлено — «печатает…» снимаем той же записью.
                    "typing.$senderId" to FieldValue.delete(),
                ),
            ).commit()
            .addOnFailureListener { Log.w(TAG, "Send failed", it) }
    }

    override fun editMessage(
        chatId: ChatId,
        messageId: MessageId,
        text: String,
        isLatest: Boolean,
    ) {
        val chatRef = chats.document(chatId.value)
        val batch =
            db
                .batch()
                .update(chatRef.collection("messages").document(messageId.value), "text", text, "editedAt", FieldValue.serverTimestamp())
        if (isLatest) batch.update(chatRef, "lastMessage.text", text)
        batch.commit().addOnFailureListener { Log.w(TAG, "Edit failed", it) }
    }

    override fun deleteMessage(
        chatId: ChatId,
        messageId: MessageId,
        isLatest: Boolean,
        previous: Message?,
    ) {
        val chatRef = chats.document(chatId.value)
        val batch =
            db.batch().update(
                chatRef.collection("messages").document(messageId.value),
                mapOf(
                    "text" to FieldValue.delete(),
                    "replyTo" to FieldValue.delete(),
                    "editedAt" to FieldValue.delete(),
                    "deletedAt" to FieldValue.serverTimestamp(),
                ),
            )
        if (isLatest) {
            val last: Any =
                previous?.let {
                    buildMap {
                        put("text", it.text)
                        put("senderId", it.senderId.value)
                        put("sentAt", it.sentAt?.toTimestamp() ?: FieldValue.serverTimestamp())
                        it.ttl?.let { ttl -> put("ttl", ttl.inWholeSeconds) }
                    }
                } ?: FieldValue.delete()
            batch.update(chatRef, "lastMessage", last)
        }
        batch.commit().addOnFailureListener { Log.w(TAG, "Delete failed", it) }
    }

    override fun markRead(
        chatId: ChatId,
        userId: UserId,
    ) {
        chats
            .document(chatId.value)
            .update("lastRead.$userId", FieldValue.serverTimestamp())
            .addOnFailureListener { Log.w(TAG, "markRead failed", it) }
    }

    override fun setTyping(
        chatId: ChatId,
        userId: UserId,
        typing: Boolean,
    ) {
        chats
            .document(chatId.value)
            .update("typing.$userId", if (typing) FieldValue.serverTimestamp() else FieldValue.delete())
            .addOnFailureListener { Log.w(TAG, "setTyping failed", it) }
    }

    override fun setDisappearing(
        chatId: ChatId,
        timer: DisappearingTimer?,
    ) {
        chats
            .document(chatId.value)
            .update("ttl", timer?.duration?.inWholeSeconds ?: FieldValue.delete())
            .addOnFailureListener { Log.w(TAG, "setDisappearing failed", it) }
    }

    override fun deleteExpired(
        chatId: ChatId,
        messageIds: List<MessageId>,
    ) {
        if (messageIds.isEmpty()) return
        val messages = chats.document(chatId.value).collection("messages")
        // Оба участника могут стирать одно и то же одновременно — повторное удаление безвредно.
        messageIds.chunked(BATCH_LIMIT).forEach { ids ->
            db
                .batch()
                .apply { ids.forEach { delete(messages.document(it.value)) } }
                .commit()
                .addOnFailureListener { Log.w(TAG, "deleteExpired failed", it) }
        }
    }

    override suspend fun createGroup(
        ownerId: UserId,
        name: String,
        memberIds: List<UserId>,
    ): ChatId {
        val ref = chats.document()
        ref
            .set(
                mapOf(
                    "type" to GROUP_TYPE,
                    "name" to name,
                    "owner" to ownerId.value,
                    "members" to (memberIds + ownerId).map { it.value }.distinct().sorted(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "lastRead" to emptyMap<String, Any>(),
                ),
            ).await()
        return ChatId(ref.id)
    }

    override fun renameGroup(
        chatId: ChatId,
        name: String,
    ) {
        chats
            .document(chatId.value)
            .update("name", name)
            .addOnFailureListener { Log.w(TAG, "renameGroup failed", it) }
    }

    @Suppress("SpreadOperator") // arrayUnion принимает только vararg; участников не больше 20.
    override fun addMembers(
        chatId: ChatId,
        userIds: List<UserId>,
    ) {
        chats
            .document(chatId.value)
            .update("members", FieldValue.arrayUnion(*userIds.map { it.value }.toTypedArray()))
            .addOnFailureListener { Log.w(TAG, "addMembers failed", it) }
    }

    override fun removeMember(
        chatId: ChatId,
        userId: UserId,
        newOwnerId: UserId?,
    ) {
        val fields = mutableMapOf<String, Any>("members" to FieldValue.arrayRemove(userId.value))
        if (newOwnerId != null) fields["owner"] = newOwnerId.value
        chats
            .document(chatId.value)
            .update(fields)
            .addOnFailureListener { Log.w(TAG, "removeMember failed", it) }
    }

    override fun observeChatPrefs(userId: UserId): Flow<Map<ChatId, ChatPrefs>> =
        prefs(userId).observe { qs -> qs.documents.associate { ChatId(it.id) to it.toChatPrefs() } }

    override fun setPinned(
        userId: UserId,
        chatId: ChatId,
        pinned: Boolean,
    ) = updatePrefs(userId, chatId, mapOf("pinnedAt" to if (pinned) FieldValue.serverTimestamp() else FieldValue.delete()))

    override fun setMuted(
        userId: UserId,
        chatId: ChatId,
        muted: Boolean,
    ) = updatePrefs(userId, chatId, mapOf("muted" to muted))

    override fun removeChat(
        userId: UserId,
        chatId: ChatId,
    ) = updatePrefs(
        userId,
        chatId,
        mapOf("hidden" to true, "clearedAt" to FieldValue.serverTimestamp(), "pinnedAt" to FieldValue.delete()),
    )

    override suspend fun startChat(
        userId: UserId,
        friendId: UserId,
    ): ChatId {
        require(userId != friendId) { "Cannot start a chat with yourself" }
        val id = ChatId.of(userId, friendId)
        val ref = chats.document(id.value)
        if (!ref.get().await().exists()) {
            ref
                .set(
                    mapOf(
                        "members" to listOf(userId.value, friendId.value).sorted(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "lastRead" to emptyMap<String, Any>(),
                    ),
                ).await()
        }
        val prefsRef = prefs(userId).document(id.value)
        if (prefsRef.get().await().getBoolean("hidden") == true) prefsRef.update("hidden", false).await()
        return id
    }

    private companion object {
        const val TAG = "Chats"
        const val MESSAGES_LIMIT = 300L
        const val BATCH_LIMIT = 500
        const val GROUP_TYPE = "group"
    }
}

private fun DocumentSnapshot.toChat(): Chat? {
    if (!exists()) return null
    val members = (get("members") as? List<*>)?.filterIsInstance<String>()?.map(::UserId) ?: return null
    val last =
        (get("lastMessage", ServerTimestampBehavior.ESTIMATE) as? Map<*, *>)?.let {
            LastMessage(
                text = it["text"] as? String ?: "",
                senderId = UserId(it["senderId"] as? String ?: ""),
                sentAt = (it["sentAt"] as? Timestamp)?.instant,
                ttl = (it["ttl"] as? Number)?.toLong()?.seconds,
            )
        }
    val lastRead = get("lastRead", ServerTimestampBehavior.ESTIMATE).toInstantMap()
    return Chat(
        id = ChatId(id),
        members = members,
        lastMessage = last,
        updatedAt = getTimestamp("updatedAt", ServerTimestampBehavior.ESTIMATE)?.instant,
        lastRead = lastRead,
        typing = get("typing", ServerTimestampBehavior.ESTIMATE).toInstantMap(),
        disappearing = DisappearingTimer.of(getLong("ttl")?.seconds),
        group =
            if (getString("type") == "group") {
                GroupInfo(name = getString("name").orEmpty(), ownerId = UserId(getString("owner").orEmpty()))
            } else {
                null
            },
    )
}

private fun Any?.toInstantMap(): Map<UserId, Instant> =
    (this as? Map<*, *>)
        ?.mapNotNull { (key, value) ->
            val uid = key as? String ?: return@mapNotNull null
            val ts = value as? Timestamp ?: return@mapNotNull null
            UserId(uid) to ts.instant
        }?.toMap()
        .orEmpty()

private fun DocumentSnapshot.toChatPrefs() =
    ChatPrefs(
        pinnedAt = getTimestamp("pinnedAt", ServerTimestampBehavior.ESTIMATE)?.instant,
        muted = getBoolean("muted") == true,
        clearedAt = getTimestamp("clearedAt", ServerTimestampBehavior.ESTIMATE)?.instant,
        hidden = getBoolean("hidden") == true,
    )

private fun DocumentSnapshot.toMessage() =
    Message(
        id = MessageId(id),
        senderId = UserId(getString("senderId").orEmpty()),
        text = getString("text").orEmpty(),
        sentAt = getTimestamp("sentAt", ServerTimestampBehavior.ESTIMATE)?.instant,
        pending = metadata.hasPendingWrites(),
        editedAt = getTimestamp("editedAt", ServerTimestampBehavior.ESTIMATE)?.instant,
        deletedAt = getTimestamp("deletedAt", ServerTimestampBehavior.ESTIMATE)?.instant,
        ttl = getLong("ttl")?.seconds,
        replyTo =
            (get("replyTo") as? Map<*, *>)?.let {
                ReplyTo(
                    id = MessageId(it["id"] as? String ?: return@let null),
                    senderId = UserId(it["senderId"] as? String ?: ""),
                    preview = it["preview"] as? String ?: "",
                )
            },
    )
