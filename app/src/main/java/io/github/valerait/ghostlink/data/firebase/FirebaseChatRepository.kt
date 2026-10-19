package io.github.valerait.ghostlink.data.firebase

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import io.github.valerait.ghostlink.data.model.Chat
import io.github.valerait.ghostlink.data.model.ChatIds
import io.github.valerait.ghostlink.data.model.LastMessage
import io.github.valerait.ghostlink.data.model.Message
import io.github.valerait.ghostlink.data.model.ReplyTo
import io.github.valerait.ghostlink.data.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Схема:
 *  chats/{a_b}            members, lastMessage, updatedAt, lastRead.{uid}
 *  chats/{a_b}/messages   senderId, text, sentAt, replyTo?{id, senderId, preview}, editedAt?
 */
class FirebaseChatRepository(
    private val db: FirebaseFirestore,
) : ChatRepository {
    private val chats = db.collection("chats")

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
    ) {
        val chatRef = chats.document(chatId)
        val messageRef = chatRef.collection("messages").document()
        val now = FieldValue.serverTimestamp()
        val message = mutableMapOf<String, Any>("senderId" to senderId, "text" to text, "sentAt" to now)
        if (replyTo != null) {
            message["replyTo"] = mapOf("id" to replyTo.id, "senderId" to replyTo.senderId, "preview" to replyTo.preview)
        }
        db
            .batch()
            .set(messageRef, message)
            .update(
                chatRef,
                mapOf(
                    "lastMessage" to mapOf("text" to text, "senderId" to senderId, "sentAt" to now),
                    "updatedAt" to now,
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
        val batch = db.batch().delete(chatRef.collection("messages").document(messageId))
        if (isLatest) {
            val last: Any =
                previous?.let {
                    mapOf(
                        "text" to it.text,
                        "senderId" to it.senderId,
                        "sentAt" to (it.sentAt?.let { ms -> Timestamp(Date(ms)) } ?: FieldValue.serverTimestamp()),
                    )
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
        return id
    }

    private companion object {
        const val TAG = "Chats"
        const val MESSAGES_LIMIT = 300L
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
            )
        }
    val lastRead =
        (get("lastRead", ServerTimestampBehavior.ESTIMATE) as? Map<*, *>)
            ?.mapNotNull { (key, value) ->
                val uid = key as? String ?: return@mapNotNull null
                val ts = value as? Timestamp ?: return@mapNotNull null
                uid to ts.millis
            }?.toMap()
            .orEmpty()
    return Chat(
        id = id,
        members = members,
        lastMessage = last,
        updatedAt = getTimestamp("updatedAt", ServerTimestampBehavior.ESTIMATE)?.millis,
        lastRead = lastRead,
    )
}

private fun DocumentSnapshot.toMessage() =
    Message(
        id = id,
        senderId = getString("senderId").orEmpty(),
        text = getString("text").orEmpty(),
        sentAt = getTimestamp("sentAt", ServerTimestampBehavior.ESTIMATE)?.millis,
        pending = metadata.hasPendingWrites(),
        editedAt = getTimestamp("editedAt", ServerTimestampBehavior.ESTIMATE)?.millis,
        replyTo =
            (get("replyTo") as? Map<*, *>)?.let {
                ReplyTo(
                    id = it["id"] as? String ?: return@let null,
                    senderId = it["senderId"] as? String ?: "",
                    preview = it["preview"] as? String ?: "",
                )
            },
    )
