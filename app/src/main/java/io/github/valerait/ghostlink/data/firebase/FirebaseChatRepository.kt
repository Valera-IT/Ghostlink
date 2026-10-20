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
import io.github.valerait.ghostlink.data.model.Chat
import io.github.valerait.ghostlink.data.model.ChatIds
import io.github.valerait.ghostlink.data.model.ChatPrefs
import io.github.valerait.ghostlink.data.model.DisappearingTimer
import io.github.valerait.ghostlink.data.model.GroupInfo
import io.github.valerait.ghostlink.data.model.LastMessage
import io.github.valerait.ghostlink.data.model.Message
import io.github.valerait.ghostlink.data.model.ReplyTo
import io.github.valerait.ghostlink.data.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.Date

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

    private fun prefs(userId: String) = db.collection("users").document(userId).collection("chatPrefs")

    private fun updatePrefs(
        userId: String,
        chatId: String,
        fields: Map<String, Any>,
    ) {
        prefs(userId)
            .document(chatId)
            .set(fields, SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "Chat prefs update failed", it) }
    }

    // Сортируем на клиенте, чтобы не требовать составной индекс members + updatedAt.
    override fun observeChats(userId: String): Flow<List<Chat>> =
        chats.whereArrayContains("members", userId).observe { qs ->
            qs.documents.mapNotNull { it.toChat() }.sortedByDescending { it.updatedAt ?: Long.MAX_VALUE }
        }

    override fun observeChat(chatId: String): Flow<Chat?> = chats.document(chatId).observe { it.toChat() }

    // MetadataChanges.INCLUDE — чтобы увидеть, как pending-сообщение подтвердилось сервером.
    override fun observeMessages(chatId: String): Flow<List<Message>> =
        chats
            .document(chatId)
            .collection("messages")
            .orderBy("sentAt", Query.Direction.ASCENDING)
            .limitToLast(MESSAGES_LIMIT)
            .observe(MetadataChanges.INCLUDE) { qs -> qs.documents.map { it.toMessage() } }

    override fun sendMessage(
        chatId: String,
        senderId: String,
        text: String,
        replyTo: ReplyTo?,
        ttl: Long?,
    ) {
        val chatRef = chats.document(chatId)
        val messageRef = chatRef.collection("messages").document()
        val now = FieldValue.serverTimestamp()
        val message = mutableMapOf<String, Any>("senderId" to senderId, "text" to text, "sentAt" to now)
        if (replyTo != null) {
            message["replyTo"] = mapOf("id" to replyTo.id, "senderId" to replyTo.senderId, "preview" to replyTo.preview)
        }
        val last = mutableMapOf<String, Any>("text" to text, "senderId" to senderId, "sentAt" to now)
        if (ttl != null) {
            message["ttl"] = ttl
            last["ttl"] = ttl
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
        chatId: String,
        messageId: String,
        text: String,
        isLatest: Boolean,
    ) {
        val chatRef = chats.document(chatId)
        val batch =
            db
                .batch()
                .update(chatRef.collection("messages").document(messageId), "text", text, "editedAt", FieldValue.serverTimestamp())
        if (isLatest) batch.update(chatRef, "lastMessage.text", text)
        batch.commit().addOnFailureListener { Log.w(TAG, "Edit failed", it) }
    }

    override fun deleteMessage(
        chatId: String,
        messageId: String,
        isLatest: Boolean,
        previous: Message?,
    ) {
        val chatRef = chats.document(chatId)
        val batch =
            db.batch().update(
                chatRef.collection("messages").document(messageId),
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
                        put("senderId", it.senderId)
                        put("sentAt", it.sentAt?.let { ms -> Timestamp(Date(ms)) } ?: FieldValue.serverTimestamp())
                        it.ttl?.let { ttl -> put("ttl", ttl) }
                    }
                } ?: FieldValue.delete()
            batch.update(chatRef, "lastMessage", last)
        }
        batch.commit().addOnFailureListener { Log.w(TAG, "Delete failed", it) }
    }

    override fun markRead(
        chatId: String,
        userId: String,
    ) {
        chats
            .document(chatId)
            .update("lastRead.$userId", FieldValue.serverTimestamp())
            .addOnFailureListener { Log.w(TAG, "markRead failed", it) }
    }

    override fun setTyping(
        chatId: String,
        userId: String,
        typing: Boolean,
    ) {
        chats
            .document(chatId)
            .update("typing.$userId", if (typing) FieldValue.serverTimestamp() else FieldValue.delete())
            .addOnFailureListener { Log.w(TAG, "setTyping failed", it) }
    }

    override fun setDisappearing(
        chatId: String,
        timer: DisappearingTimer?,
    ) {
        chats
            .document(chatId)
            .update("ttl", timer?.seconds ?: FieldValue.delete())
            .addOnFailureListener { Log.w(TAG, "setDisappearing failed", it) }
    }

    override fun deleteExpired(
        chatId: String,
        messageIds: List<String>,
    ) {
        if (messageIds.isEmpty()) return
        val messages = chats.document(chatId).collection("messages")
        // Оба участника могут стирать одно и то же одновременно — повторное удаление безвредно.
        messageIds.chunked(BATCH_LIMIT).forEach { ids ->
            db
                .batch()
                .apply { ids.forEach { delete(messages.document(it)) } }
                .commit()
                .addOnFailureListener { Log.w(TAG, "deleteExpired failed", it) }
        }
    }

    override suspend fun createGroup(
        ownerId: String,
        name: String,
        memberIds: List<String>,
    ): String {
        val ref = chats.document()
        ref
            .set(
                mapOf(
                    "type" to GROUP_TYPE,
                    "name" to name,
                    "owner" to ownerId,
                    "members" to (memberIds + ownerId).distinct().sorted(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "lastRead" to emptyMap<String, Any>(),
                ),
            ).await()
        return ref.id
    }

    override fun renameGroup(
        chatId: String,
        name: String,
    ) {
        chats
            .document(chatId)
            .update("name", name)
            .addOnFailureListener { Log.w(TAG, "renameGroup failed", it) }
    }

    @Suppress("SpreadOperator") // arrayUnion принимает только vararg; участников не больше 20.
    override fun addMembers(
        chatId: String,
        userIds: List<String>,
    ) {
        chats
            .document(chatId)
            .update("members", FieldValue.arrayUnion(*userIds.toTypedArray()))
            .addOnFailureListener { Log.w(TAG, "addMembers failed", it) }
    }

    override fun removeMember(
        chatId: String,
        userId: String,
        newOwnerId: String?,
    ) {
        val fields = mutableMapOf<String, Any>("members" to FieldValue.arrayRemove(userId))
        if (newOwnerId != null) fields["owner"] = newOwnerId
        chats
            .document(chatId)
            .update(fields)
            .addOnFailureListener { Log.w(TAG, "removeMember failed", it) }
    }

    override fun observeChatPrefs(userId: String): Flow<Map<String, ChatPrefs>> =
        prefs(userId).observe { qs -> qs.documents.associate { it.id to it.toChatPrefs() } }

    override fun setPinned(
        userId: String,
        chatId: String,
        pinned: Boolean,
    ) = updatePrefs(userId, chatId, mapOf("pinnedAt" to if (pinned) FieldValue.serverTimestamp() else FieldValue.delete()))

    override fun setMuted(
        userId: String,
        chatId: String,
        muted: Boolean,
    ) = updatePrefs(userId, chatId, mapOf("muted" to muted))

    override fun removeChat(
        userId: String,
        chatId: String,
    ) = updatePrefs(
        userId,
        chatId,
        mapOf("hidden" to true, "clearedAt" to FieldValue.serverTimestamp(), "pinnedAt" to FieldValue.delete()),
    )

    override suspend fun startChat(
        userId: String,
        friendId: String,
    ): String {
        require(userId != friendId) { "Cannot start a chat with yourself" }
        val id = ChatIds.of(userId, friendId)
        val ref = chats.document(id)
        if (!ref.get().await().exists()) {
            ref
                .set(
                    mapOf(
                        "members" to listOf(userId, friendId).sorted(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "lastRead" to emptyMap<String, Any>(),
                    ),
                ).await()
        }
        val prefsRef = prefs(userId).document(id)
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
    val members = (get("members") as? List<*>)?.filterIsInstance<String>() ?: return null
    val last =
        (get("lastMessage", ServerTimestampBehavior.ESTIMATE) as? Map<*, *>)?.let {
            LastMessage(
                text = it["text"] as? String ?: "",
                senderId = it["senderId"] as? String ?: "",
                sentAt = (it["sentAt"] as? Timestamp)?.millis,
                ttl = (it["ttl"] as? Number)?.toLong(),
            )
        }
    val lastRead = get("lastRead", ServerTimestampBehavior.ESTIMATE).toMillisMap()
    return Chat(
        id = id,
        members = members,
        lastMessage = last,
        updatedAt = getTimestamp("updatedAt", ServerTimestampBehavior.ESTIMATE)?.millis,
        lastRead = lastRead,
        typing = get("typing", ServerTimestampBehavior.ESTIMATE).toMillisMap(),
        disappearing = DisappearingTimer.of(getLong("ttl")),
        group =
            if (getString("type") == "group") {
                GroupInfo(name = getString("name").orEmpty(), ownerId = getString("owner").orEmpty())
            } else {
                null
            },
    )
}

private fun Any?.toMillisMap(): Map<String, Long> =
    (this as? Map<*, *>)
        ?.mapNotNull { (key, value) ->
            val uid = key as? String ?: return@mapNotNull null
            val ts = value as? Timestamp ?: return@mapNotNull null
            uid to ts.millis
        }?.toMap()
        .orEmpty()

private fun DocumentSnapshot.toChatPrefs() =
    ChatPrefs(
        pinnedAt = getTimestamp("pinnedAt", ServerTimestampBehavior.ESTIMATE)?.millis,
        muted = getBoolean("muted") == true,
        clearedAt = getTimestamp("clearedAt", ServerTimestampBehavior.ESTIMATE)?.millis,
        hidden = getBoolean("hidden") == true,
    )

private fun DocumentSnapshot.toMessage() =
    Message(
        id = id,
        senderId = getString("senderId").orEmpty(),
        text = getString("text").orEmpty(),
        sentAt = getTimestamp("sentAt", ServerTimestampBehavior.ESTIMATE)?.millis,
        pending = metadata.hasPendingWrites(),
        editedAt = getTimestamp("editedAt", ServerTimestampBehavior.ESTIMATE)?.millis,
        deletedAt = getTimestamp("deletedAt", ServerTimestampBehavior.ESTIMATE)?.millis,
        ttl = getLong("ttl"),
        replyTo =
            (get("replyTo") as? Map<*, *>)?.let {
                ReplyTo(
                    id = it["id"] as? String ?: return@let null,
                    senderId = it["senderId"] as? String ?: "",
                    preview = it["preview"] as? String ?: "",
                )
            },
    )
