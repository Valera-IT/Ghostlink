package io.github.valerait.ghostlink.data.model

import java.time.Duration

/** Сколько «в сети» живёт без нового heartbeat: на случай, если приложение убили, не успев выставить offline. */
const val ONLINE_TIMEOUT_MS = 150_000L

/** Сколько живёт отметка «печатает…» без обновления: приложение могли закрыть посреди набора. */
const val TYPING_TIMEOUT_MS = 6_000L

/** Как часто обновлять отметку, пока пользователь набирает текст; меньше [TYPING_TIMEOUT_MS] с запасом на задержку сети. */
const val TYPING_REFRESH_MS = 4_000L

data class Account(
    val id: String,
    /** Анонимный — временный аккаунт нового устройства или аккаунт из версии до привязки устройств. */
    val isAnonymous: Boolean,
)

/** Сколько живёт заявка на вход с нового устройства. */
const val LINK_TTL_MS = 120_000L

/** Устройство из реестра users/{uid}/devices. */
data class Device(
    val id: String,
    val name: String,
    val platform: String,
    val lastActiveAt: Long? = null,
)

/** Заявка нового устройства на вход: linkRequests/{id}. */
data class LinkRequest(
    val id: String,
    val deviceName: String,
    val platform: String,
    val expiresAt: Long,
    /** Зашифрованный ключ аккаунта; появляется, когда другое устройство подтвердило вход. */
    val payload: ByteArray? = null,
) {
    fun isExpired(now: Long): Boolean = now >= expiresAt
}

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
    /** Срок жизни исчезающего сообщения, секунды; null — сообщение не исчезает. */
    val ttl: Long? = null,
) {
    fun isExpired(now: Long): Boolean = expiresAt(sentAt, ttl)?.let { now >= it } == true
}

/** Таймер исчезающих сообщений чата. Значения совпадают с разрешёнными в firestore.rules. */
enum class DisappearingTimer(
    val seconds: Long,
) {
    HOUR(Duration.ofHours(1).seconds),
    DAY(Duration.ofDays(1).seconds),
    WEEK(Duration.ofDays(DAYS_IN_WEEK).seconds),
    ;

    companion object {
        fun of(seconds: Long?): DisappearingTimer? = entries.firstOrNull { it.seconds == seconds }
    }
}

/** Когда исчезнет сообщение; null — не исчезает или ещё не подтверждено сервером (отсчёт идёт от серверного времени). */
fun expiresAt(
    sentAt: Long?,
    ttl: Long?,
): Long? = if (sentAt == null || ttl == null) null else sentAt + Duration.ofSeconds(ttl).toMillis()

private const val DAYS_IN_WEEK = 7L

data class Chat(
    val id: String,
    val members: List<String>,
    val lastMessage: LastMessage? = null,
    val updatedAt: Long? = null,
    /** userId -> время, до которого участник прочитал чат. */
    val lastRead: Map<String, Long> = emptyMap(),
    /** userId -> когда участник последний раз набирал текст. */
    val typing: Map<String, Long> = emptyMap(),
    /** Таймер исчезающих сообщений; null — выключен. */
    val disappearing: DisappearingTimer? = null,
    /** Название и админ группы; null — личный чат пары. */
    val group: GroupInfo? = null,
) {
    val isGroup: Boolean get() = group != null

    /** Собеседник в личном чате; в группе — первый другой участник, поэтому для групп не используется. */
    fun partnerId(me: String): String = members.firstOrNull { it != me } ?: me

    /** До какого момента своё сообщение считается прочитанным: хватает одного прочитавшего, как в Telegram. */
    fun othersReadUpTo(me: String): Long? = lastRead.filterKeys { it != me && it in members }.values.maxOrNull()

    /** В группе — до какого момента прочитали все остальные участники; в личном чате null. */
    fun allReadUpTo(me: String): Long? {
        val others = members.filter { it != me }
        val reads = others.mapNotNull { lastRead[it] }
        return if (isGroup && others.isNotEmpty() && reads.size == others.size) reads.min() else null
    }

    /** Кто из других участников сейчас печатает, и до какого момента; null — никто. */
    fun typingOther(me: String): Pair<String, Long>? =
        typing
            .filterKeys { it != me && it in members }
            .maxByOrNull { it.value }
            ?.let { (id, at) -> id to at + TYPING_TIMEOUT_MS }

    fun isUnreadFor(me: String): Boolean {
        val last = lastMessage ?: return false
        if (last.senderId == me) return false
        val sentAt = last.sentAt ?: return false
        val read = lastRead[me] ?: return true
        return sentAt > read
    }

    /** До какого момента показывать, что [userId] печатает; null — не печатает. */
    fun typingUntil(userId: String): Long? = typing[userId]?.plus(TYPING_TIMEOUT_MS)

    fun isTyping(
        userId: String,
        now: Long,
    ): Boolean = typingUntil(userId)?.let { now < it } == true
}

data class GroupInfo(
    val name: String,
    /** Админ: добавляет и удаляет участников, меняет название. */
    val ownerId: String,
) {
    companion object {
        /** Совпадают с ограничениями в firestore.rules. */
        const val NAME_MAX_LENGTH = 64
        const val MAX_MEMBERS = 20
    }
}

/**
 * Личные настройки чата у одного пользователя: users/{uid}/chatPrefs/{chatId}. Собеседник их не видит.
 */
data class ChatPrefs(
    /** Когда чат закрепили; закреплённые идут первыми, последний закреплённый — выше. */
    val pinnedAt: Long? = null,
    val muted: Boolean = false,
    /** Когда чат удалили из списка: сообщения до этого времени больше не показываем. */
    val clearedAt: Long? = null,
    /** Чат убран из списка и вернётся, когда придёт новое сообщение или друга добавят снова. */
    val hidden: Boolean = false,
) {
    val pinned: Boolean get() = pinnedAt != null

    /** Сообщение отправлено до удаления чата. Неподтверждённые (без времени) — всегда новые. */
    fun isCleared(sentAt: Long?): Boolean = clearedAt != null && sentAt != null && sentAt <= clearedAt

    fun isVisible(chat: Chat): Boolean = !hidden || chat.lastMessage?.let { !isCleared(it.sentAt) } == true
}

data class Message(
    val id: String,
    val senderId: String,
    val text: String,
    val sentAt: Long?,
    /** Сообщение записано только локально и ещё не подтверждено сервером. */
    val pending: Boolean = false,
    val replyTo: ReplyTo? = null,
    /** Когда автор последний раз исправил текст; null — не исправлялось. */
    val editedAt: Long? = null,
    /** Когда автор удалил сообщение у обоих; от него остаётся только отметка «Сообщение удалено». */
    val deletedAt: Long? = null,
    /** Срок жизни исчезающего сообщения, секунды; null — сообщение не исчезает. */
    val ttl: Long? = null,
) {
    val deleted: Boolean get() = deletedAt != null

    val expiresAt: Long? get() = expiresAt(sentAt, ttl)

    fun isExpired(now: Long): Boolean = expiresAt?.let { now >= it } == true

    /**
     * [readUpTo] — до какого момента прочитал хоть один получатель;
     * [allReadUpTo] — до какого прочитали все (только в группах, в личном чате null).
     */
    fun status(
        readUpTo: Long?,
        allReadUpTo: Long? = null,
    ): MessageStatus =
        when {
            pending || sentAt == null -> MessageStatus.SENDING
            allReadUpTo != null && allReadUpTo >= sentAt -> MessageStatus.READ_ALL
            readUpTo != null && readUpTo >= sentAt -> MessageStatus.READ
            else -> MessageStatus.SENT
        }
}

/** READ — прочитал хоть один (две галочки), READ_ALL — в группе прочитали все (три галочки). */
enum class MessageStatus { SENDING, SENT, READ, READ_ALL }

/**
 * Ссылка на сообщение, на которое отвечают. Превью хранится в самом ответе, чтобы цитату
 * можно было показать без чтения исходного сообщения (оно могло выйти за лимит загрузки).
 */
data class ReplyTo(
    val id: String,
    val senderId: String,
    val preview: String,
) {
    companion object {
        /** Совпадает с ограничением в firestore.rules. */
        const val PREVIEW_MAX_LENGTH = 100

        fun of(message: Message): ReplyTo = ReplyTo(message.id, message.senderId, previewOf(message.text))

        /** Одна строка без лишних пробелов, не длиннее [PREVIEW_MAX_LENGTH] вместе с многоточием. */
        fun previewOf(text: String): String {
            val line = text.trim().replace(Regex("\\s+"), " ")
            return if (line.length <= PREVIEW_MAX_LENGTH) line else line.take(PREVIEW_MAX_LENGTH - 1).trimEnd() + "…"
        }
    }
}

object ChatIds {
    /** У пары пользователей ровно один чат: id не зависит от того, кто кого добавил. */
    fun of(
        a: String,
        b: String,
    ): String = if (a < b) "${a}_$b" else "${b}_$a"
}
