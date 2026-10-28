package io.github.valerait.ghostlink.core.data

import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FieldPath
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Source
import dev.gitlive.firebase.firestore.Timestamp
import io.github.valerait.ghostlink.core.domain.repository.ChatRepository
import io.github.valerait.ghostlink.core.firebase.ChatCreate
import io.github.valerait.ghostlink.core.firebase.ChatDto
import io.github.valerait.ghostlink.core.firebase.ChatPrefsDto
import io.github.valerait.ghostlink.core.firebase.Collections
import io.github.valerait.ghostlink.core.firebase.GroupCreate
import io.github.valerait.ghostlink.core.firebase.MessageDto
import io.github.valerait.ghostlink.core.firebase.MessageWrite
import io.github.valerait.ghostlink.core.model.Attachment
import io.github.valerait.ghostlink.core.model.Chat
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.ChatPrefs
import io.github.valerait.ghostlink.core.model.DisappearingTimer
import io.github.valerait.ghostlink.core.model.Message
import io.github.valerait.ghostlink.core.model.MessageId
import io.github.valerait.ghostlink.core.model.ReplyTo
import io.github.valerait.ghostlink.core.model.UserId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration
import kotlin.time.Instant

/** Схема документов — в core:firebase (Dtos.kt) и в firestore.rules. */
@Suppress("TooManyFunctions") // по функции на метод ChatRepository
class FirebaseChatRepository internal constructor(
    private val db: FirebaseFirestore,
    private val scope: CoroutineScope,
    private val push: MessagePush = MessagePush { _, _ -> },
) : ChatRepository {
    private val chats = db.collection(Collections.CHATS)

    private fun messages(chatId: ChatId) = chats.document(chatId.value).collection(Collections.MESSAGES)

    private fun prefs(userId: UserId) = db.collection(Collections.USERS).document(userId.value).collection(Collections.CHAT_PREFS)

    private fun write(
        what: String,
        block: suspend () -> Unit,
    ) = scope.launchWrite(TAG, what, block)

    // Личные настройки пишутся слиянием: документа может ещё не быть.
    private fun updatePrefs(
        userId: UserId,
        chatId: ChatId,
        vararg fields: Pair<String, Any>,
    ) = write("Chat prefs update") { prefs(userId).document(chatId.value).set(mapOf(*fields), merge = true) }

    // Сортируем на клиенте, чтобы не требовать составной индекс members + updatedAt.
    override fun observeChats(userId: UserId): Flow<List<Chat>> =
        chats
            .where { "members" contains userId.value }
            .snapshots
            .map { qs -> qs.documents.mapNotNull { it.toChat() }.sortedByDescending { it.updatedAt ?: Instant.DISTANT_FUTURE } }
            .logErrors("chats of $userId")

    // Ответ сервера Firestore кладёт в кэш, и слушатель observeChats сразу видит свежие чаты.
    override suspend fun refreshChats(userId: UserId) =
        firebaseCall {
            chats.where { "members" contains userId.value }.get(Source.SERVER)
            Unit
        }

    override fun observeChat(chatId: ChatId): Flow<Chat?> =
        chats
            .document(chatId.value)
            .snapshots
            .map { it.toChat() }
            .logErrors("chats/$chatId")

    // includeMetadataChanges — чтобы увидеть, как pending-сообщение подтвердилось сервером.
    override fun observeMessages(chatId: ChatId): Flow<List<Message>> =
        messages(chatId)
            .orderBy("sentAt", Direction.ASCENDING)
            .limitToLast(MESSAGES_LIMIT)
            .snapshots(includeMetadataChanges = true)
            .map { qs -> qs.documents.map { it.toMessage() } }
            .logErrors("messages of $chatId")

    override fun sendMessage(
        chatId: ChatId,
        senderId: UserId,
        text: String,
        replyTo: ReplyTo?,
        ttl: Duration?,
        attachment: Attachment?,
    ) {
        val chatRef = chats.document(chatId.value)
        val messageRef = messages(chatId).document
        val message =
            MessageWrite(senderId.value, text, Timestamp.ServerTimestamp, replyTo?.toDto(), ttl?.inWholeSeconds, attachment?.toDto())
        val last = lastMessageFields(text, senderId, FieldValue.serverTimestamp, ttl, photo = attachment != null)
        write("Send") {
            db
                .batch()
                .set(messageRef, message) { encodeDefaults = false }
                .updateFields(chatRef) {
                    "lastMessage" to last
                    "updatedAt" to FieldValue.serverTimestamp
                    // Сообщение отправлено — «печатает…» снимаем той же записью.
                    FieldPath("typing", senderId.value) to FieldValue.delete
                }.commit()
            // Worker читает сообщение с сервера, поэтому зовём его только после записи.
            push.sent(chatId, MessageId(messageRef.id))
        }
    }

    override fun editMessage(
        chatId: ChatId,
        messageId: MessageId,
        text: String,
        isLatest: Boolean,
    ) = write("Edit") {
        val batch =
            db
                .batch()
                .updateFields(messages(chatId).document(messageId.value)) {
                    "text" to text
                    "editedAt" to FieldValue.serverTimestamp
                }
        if (isLatest) batch.updateFields(chats.document(chatId.value)) { FieldPath("lastMessage", "text") to text }
        batch.commit()
    }

    override fun deleteMessage(
        chatId: ChatId,
        messageId: MessageId,
        isLatest: Boolean,
        previous: Message?,
    ) = write("Delete") {
        // Сообщение не стирается целиком: остаётся отметка «Сообщение удалено».
        val batch =
            db.batch().updateFields(messages(chatId).document(messageId.value)) {
                "text" to FieldValue.delete
                "replyTo" to FieldValue.delete
                "editedAt" to FieldValue.delete
                "attachment" to FieldValue.delete
                "deletedAt" to FieldValue.serverTimestamp
            }
        if (isLatest) {
            val last: Any =
                previous?.let {
                    lastMessageFields(
                        it.text,
                        it.senderId,
                        it.sentAt?.toTimestamp() ?: FieldValue.serverTimestamp,
                        it.ttl,
                        photo = it.attachment != null,
                    )
                } ?: FieldValue.delete
            batch.updateFields(chats.document(chatId.value)) { "lastMessage" to last }
        }
        batch.commit()
    }

    override fun markRead(
        chatId: ChatId,
        userId: UserId,
    ) = write(
        "markRead",
    ) { chats.document(chatId.value).updateFields { FieldPath("lastRead", userId.value) to FieldValue.serverTimestamp } }

    override fun setTyping(
        chatId: ChatId,
        userId: UserId,
        typing: Boolean,
    ) = write("setTyping") {
        chats
            .document(chatId.value)
            .updateFields { FieldPath("typing", userId.value) to if (typing) FieldValue.serverTimestamp else FieldValue.delete }
    }

    override fun setDisappearing(
        chatId: ChatId,
        timer: DisappearingTimer?,
    ) = write("setDisappearing") {
        chats.document(chatId.value).updateFields { "ttl" to (timer?.duration?.inWholeSeconds ?: FieldValue.delete) }
    }

    override fun deleteExpired(
        chatId: ChatId,
        messageIds: List<MessageId>,
    ) {
        if (messageIds.isEmpty()) return
        // Оба участника могут стирать одно и то же одновременно — повторное удаление безвредно.
        messageIds.chunked(BATCH_LIMIT).forEach { ids ->
            write("deleteExpired") {
                val batch = db.batch()
                ids.forEach { batch.delete(messages(chatId).document(it.value)) }
                batch.commit()
            }
        }
    }

    override suspend fun createGroup(
        ownerId: UserId,
        name: String,
        memberIds: List<UserId>,
    ): ChatId =
        firebaseCall {
            val ref = chats.document
            val members = (memberIds + ownerId).map { it.value }.distinct().sorted()
            ref.set(GroupCreate(name = name, owner = ownerId.value, members = members, updatedAt = Timestamp.ServerTimestamp))
            ChatId(ref.id)
        }

    override fun renameGroup(
        chatId: ChatId,
        name: String,
    ) = write("renameGroup") { chats.document(chatId.value).updateFields { "name" to name } }

    @Suppress("SpreadOperator") // arrayUnion принимает только vararg; участников не больше 20.
    override fun addMembers(
        chatId: ChatId,
        userIds: List<UserId>,
    ) = write("addMembers") {
        chats.document(chatId.value).updateFields { "members" to FieldValue.arrayUnion(*userIds.map { it.value }.toTypedArray()) }
    }

    override fun removeMember(
        chatId: ChatId,
        userId: UserId,
        newOwnerId: UserId?,
    ) = write("removeMember") {
        chats.document(chatId.value).updateFields {
            "members" to FieldValue.arrayRemove(userId.value)
            if (newOwnerId != null) "owner" to newOwnerId.value
        }
    }

    override fun observeChatPrefs(userId: UserId): Flow<Map<ChatId, ChatPrefs>> =
        prefs(userId)
            .snapshots
            .map { qs -> qs.documents.associate { ChatId(it.id) to it.toChatPrefs() } }
            .logErrors("chatPrefs of $userId")

    override fun setPinned(
        userId: UserId,
        chatId: ChatId,
        pinned: Boolean,
    ) = updatePrefs(userId, chatId, "pinnedAt" to if (pinned) FieldValue.serverTimestamp else FieldValue.delete)

    override fun setMuted(
        userId: UserId,
        chatId: ChatId,
        muted: Boolean,
    ) = updatePrefs(userId, chatId, "muted" to muted)

    override fun removeChat(
        userId: UserId,
        chatId: ChatId,
    ) = updatePrefs(userId, chatId, "hidden" to true, "clearedAt" to FieldValue.serverTimestamp, "pinnedAt" to FieldValue.delete)

    override suspend fun startChat(
        userId: UserId,
        friendId: UserId,
    ): ChatId {
        require(userId != friendId) { "Cannot start a chat with yourself" }
        val id = ChatId.of(userId, friendId)
        firebaseCall {
            val ref = chats.document(id.value)
            if (!ref.get().exists) {
                ref.set(ChatCreate(listOf(userId.value, friendId.value).sorted(), Timestamp.ServerTimestamp))
            }
            val prefsRef = prefs(userId).document(id.value)
            val prefsDoc = prefsRef.get()
            if (prefsDoc.exists && prefsDoc.toChatPrefs().hidden) prefsRef.updateFields { "hidden" to false }
        }
        return id
    }

    private companion object {
        const val TAG = "Chats"
        const val MESSAGES_LIMIT = 300L
        const val BATCH_LIMIT = 500
    }
}

/** Превью чата в списке: lastMessage пишется словарём, чтобы sentAt мог быть серверным временем. */
private fun lastMessageFields(
    text: String,
    senderId: UserId,
    sentAt: Any,
    ttl: Duration?,
    photo: Boolean,
): Map<String, Any> =
    buildMap {
        put("text", text)
        put("senderId", senderId.value)
        put("sentAt", sentAt)
        ttl?.let { put("ttl", it.inWholeSeconds) }
        if (photo) put("photo", true)
    }

private fun DocumentSnapshot.toChat(): Chat? = if (exists) data<ChatDto>(ESTIMATE).toChat(id) else null

private fun DocumentSnapshot.toChatPrefs(): ChatPrefs = data<ChatPrefsDto>(ESTIMATE).toChatPrefs()

private fun DocumentSnapshot.toMessage(): Message = data<MessageDto>(ESTIMATE).toMessage(id, pending = metadata.hasPendingWrites)
