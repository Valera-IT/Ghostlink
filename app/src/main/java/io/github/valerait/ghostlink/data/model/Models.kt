package io.github.valerait.ghostlink.data.model

/** Сколько «в сети» живёт без нового heartbeat: на случай, если приложение убили, не успев выставить offline. */
const val ONLINE_TIMEOUT_MS = 150_000L

data class Account(
    val id: String,
    val isAnonymous: Boolean,
    val email: String?,
)

data class User(
    val id: String,
    val name: String,
    val online: Boolean = false,
    val lastSeen: Long? = null,
) {
    fun isOnline(now: Long): Boolean = online && (lastSeen == null || now - lastSeen < ONLINE_TIMEOUT_MS)
}

data class LastMessage(
    val text: String,
    val senderId: String,
    val sentAt: Long?,
)

data class Chat(
    val id: String,
    val members: List<String>,
    val lastMessage: LastMessage? = null,
    val updatedAt: Long? = null,
    /** userId -> время, до которого участник прочитал чат. */
    val lastRead: Map<String, Long> = emptyMap(),
) {
    fun partnerId(me: String): String = members.firstOrNull { it != me } ?: me

    fun isUnreadFor(me: String): Boolean {
        val last = lastMessage ?: return false
        if (last.senderId == me) return false
        val sentAt = last.sentAt ?: return false
        val read = lastRead[me] ?: return true
        return sentAt > read
    }
}

data class Message(
    val id: String,
    val senderId: String,
    val text: String,
    val sentAt: Long?,
    /** Сообщение записано только локально и ещё не подтверждено сервером. */
    val pending: Boolean = false,
) {
    fun status(partnerLastRead: Long?): MessageStatus = when {
        pending || sentAt == null -> MessageStatus.SENDING
        partnerLastRead != null && partnerLastRead >= sentAt -> MessageStatus.READ
        else -> MessageStatus.SENT
    }
}

enum class MessageStatus { SENDING, SENT, READ }

object ChatIds {
    /** У пары пользователей ровно один чат: id не зависит от того, кто кого добавил. */
    fun of(a: String, b: String): String = if (a < b) "${a}_$b" else "${b}_$a"
}
