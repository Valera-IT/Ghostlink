package io.github.valerait.ghostlink.core.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Сколько «в сети» живёт без нового heartbeat: на случай, если приложение убили, не успев выставить offline. */
val ONLINE_TIMEOUT: Duration = 150.seconds

/** Сколько живёт отметка «печатает…» без обновления: приложение могли закрыть посреди набора. */
val TYPING_TIMEOUT: Duration = 6.seconds

/** Как часто обновлять отметку, пока пользователь набирает текст; меньше [TYPING_TIMEOUT] с запасом на задержку сети. */
val TYPING_REFRESH: Duration = 4.seconds

/** Сколько живёт заявка на вход с нового устройства. */
val LINK_TTL: Duration = 2.minutes

data class Account(
    val id: UserId,
    /** Анонимный — временный аккаунт нового устройства или аккаунт из версии до привязки устройств. */
    val isAnonymous: Boolean,
)

/** Устройство из реестра users/{uid}/devices. */
data class Device(
    val id: DeviceId,
    val name: String,
    val platform: Platform,
    val lastActiveAt: Instant? = null,
)

/** Заявка нового устройства на вход: linkRequests/{id}. */
data class LinkRequest(
    val id: LinkRequestId,
    val deviceName: String,
    val platform: Platform,
    val expiresAt: Instant,
    /** Зашифрованный ключ аккаунта; появляется, когда другое устройство подтвердило вход. */
    val payload: ByteArray? = null,
) {
    fun isExpired(now: Instant): Boolean = now >= expiresAt
}

data class User(
    val id: UserId,
    val name: String,
    val online: Boolean = false,
    val lastSeen: Instant? = null,
    /** Версия фото профиля из avatars/{uid}; null — фото нет, показываем инициалы. */
    val avatarVersion: Long? = null,
) {
    fun isOnline(now: Instant): Boolean = online && (lastSeen == null || now - lastSeen < ONLINE_TIMEOUT)

    companion object {
        /** Совпадает с ограничением в firestore.rules. */
        const val NAME_MAX_LENGTH = 40

        /** Сторона квадратного фото профиля. */
        const val AVATAR_SIZE_PX = 256

        /** Потолок JPEG фото профиля; совпадает с ограничением в firestore.rules. */
        const val AVATAR_MAX_BYTES = 64 * 1024

        /** Имя профиля без пробелов по краям и не длиннее лимита; null — пустое. */
        fun normalizeName(raw: String): String? =
            raw
                .trim()
                .take(NAME_MAX_LENGTH)
                .trimEnd()
                .takeIf { it.isNotEmpty() }
    }
}

data class LastMessage(
    /** Текст или подпись к фото; у фото без подписи пустой. */
    val text: String,
    val senderId: UserId,
    val sentAt: Instant?,
    /** Срок жизни исчезающего сообщения; null — сообщение не исчезает. */
    val ttl: Duration? = null,
    /** Последнее сообщение — фото: в списке чатов показываем «📷 Фото». */
    val photo: Boolean = false,
) {
    fun isExpired(now: Instant): Boolean = expiresAt(sentAt, ttl)?.let { now >= it } == true
}

/** Таймер исчезающих сообщений чата. Значения совпадают с разрешёнными в firestore.rules. */
enum class DisappearingTimer(
    val duration: Duration,
) {
    HOUR(1.hours),
    DAY(1.days),
    WEEK(7.days),
    ;

    companion object {
        fun of(duration: Duration?): DisappearingTimer? = entries.firstOrNull { it.duration == duration }
    }
}

/** Когда исчезнет сообщение; null — не исчезает или ещё не подтверждено сервером (отсчёт идёт от серверного времени). */
fun expiresAt(
    sentAt: Instant?,
    ttl: Duration?,
): Instant? = if (sentAt == null || ttl == null) null else sentAt + ttl

data class Chat(
    val id: ChatId,
    val members: List<UserId>,
    val lastMessage: LastMessage? = null,
    val updatedAt: Instant? = null,
    /** Участник -> время, до которого он прочитал чат. */
    val lastRead: Map<UserId, Instant> = emptyMap(),
    /** Участник -> когда он последний раз набирал текст. */
    val typing: Map<UserId, Instant> = emptyMap(),
    /** Таймер исчезающих сообщений; null — выключен. */
    val disappearing: DisappearingTimer? = null,
    /** Название и админ группы; null — личный чат пары. */
    val group: GroupInfo? = null,
) {
    val isGroup: Boolean get() = group != null

    /** Собеседник в личном чате; в группе — первый другой участник, поэтому для групп не используется. */
    fun partnerId(me: UserId): UserId = members.firstOrNull { it != me } ?: me

    /** До какого момента своё сообщение считается прочитанным: хватает одного прочитавшего, как в Telegram. */
    fun othersReadUpTo(me: UserId): Instant? = lastRead.filterKeys { it != me && it in members }.values.maxOrNull()

    /** В группе — до какого момента прочитали все остальные участники; в личном чате null. */
    fun allReadUpTo(me: UserId): Instant? {
        val others = members.filter { it != me }
        val reads = others.mapNotNull { lastRead[it] }
        return if (isGroup && others.isNotEmpty() && reads.size == others.size) reads.min() else null
    }

    /** Кто из других участников сейчас печатает, и до какого момента; null — никто. */
    fun typingOther(me: UserId): Pair<UserId, Instant>? =
        typing
            .filterKeys { it != me && it in members }
            .maxByOrNull { it.value }
            ?.let { (id, at) -> id to at + TYPING_TIMEOUT }

    fun isUnreadFor(me: UserId): Boolean {
        val sentAt = lastMessage?.takeIf { it.senderId != me }?.sentAt ?: return false
        val read = lastRead[me]
        return read == null || sentAt > read
    }

    /** До какого момента показывать, что [userId] печатает; null — не печатает. */
    fun typingUntil(userId: UserId): Instant? = typing[userId]?.plus(TYPING_TIMEOUT)

    fun isTyping(
        userId: UserId,
        now: Instant,
    ): Boolean = typingUntil(userId)?.let { now < it } == true
}

data class GroupInfo(
    val name: String,
    /** Админ: добавляет и удаляет участников, меняет название. */
    val ownerId: UserId,
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
    val pinnedAt: Instant? = null,
    val muted: Boolean = false,
    /** Когда чат удалили из списка: сообщения до этого времени больше не показываем. */
    val clearedAt: Instant? = null,
    /** Чат убран из списка и вернётся, когда придёт новое сообщение или друга добавят снова. */
    val hidden: Boolean = false,
) {
    val pinned: Boolean get() = pinnedAt != null

    /** Сообщение отправлено до удаления чата. Неподтверждённые (без времени) — всегда новые. */
    fun isCleared(sentAt: Instant?): Boolean = clearedAt != null && sentAt != null && sentAt <= clearedAt

    fun isVisible(chat: Chat): Boolean = !hidden || chat.lastMessage?.let { !isCleared(it.sentAt) } == true
}

data class Message(
    val id: MessageId,
    val senderId: UserId,
    /** Текст или подпись к фото ([attachment]); у фото без подписи пустой. */
    val text: String,
    val sentAt: Instant?,
    /** Сообщение записано только локально и ещё не подтверждено сервером. */
    val pending: Boolean = false,
    val replyTo: ReplyTo? = null,
    /** Когда автор последний раз исправил текст; null — не исправлялось. */
    val editedAt: Instant? = null,
    /** Когда автор удалил сообщение у обоих; от него остаётся только отметка «Сообщение удалено». */
    val deletedAt: Instant? = null,
    /** Срок жизни исчезающего сообщения; null — сообщение не исчезает. */
    val ttl: Duration? = null,
    /** Фото в сообщении; у удалённого сообщения пропадает вместе с текстом. */
    val attachment: Attachment? = null,
) {
    val deleted: Boolean get() = deletedAt != null

    val expiresAt: Instant? get() = expiresAt(sentAt, ttl)

    fun isExpired(now: Instant): Boolean = expiresAt?.let { now >= it } == true

    companion object {
        /** Совпадает с ограничением в firestore.rules. */
        const val TEXT_MAX_LENGTH = 4000
    }

    /**
     * [readUpTo] — до какого момента прочитал хоть один получатель;
     * [allReadUpTo] — до какого прочитали все (только в группах, в личном чате null).
     */
    fun status(
        readUpTo: Instant?,
        allReadUpTo: Instant? = null,
    ): MessageStatus =
        when {
            pending || sentAt == null -> MessageStatus.SENDING
            allReadUpTo != null && allReadUpTo >= sentAt -> MessageStatus.READ_ALL
            readUpTo != null && readUpTo >= sentAt -> MessageStatus.READ
            else -> MessageStatus.SENT
        }
}

/** Текст сообщения без пробелов по краям и не длиннее лимита; null — отправлять нечего. */
fun normalizeMessageText(raw: String): String? = raw.trim().take(Message.TEXT_MAX_LENGTH).takeIf { it.isNotEmpty() }

/**
 * Фото в сообщении. Сами файлы — полный размер [key] и превью [thumbKey] — лежат в хранилище медиа (MediaStore),
 * в сообщении только их ключи и размеры: по [width] и [height] лента резервирует место до загрузки превью.
 */
data class Attachment(
    val key: MediaKey,
    val thumbKey: MediaKey,
    val width: Int,
    val height: Int,
    /** Размер полного фото в байтах. */
    val size: Int,
) {
    companion object {
        /** Полное фото: длинная сторона и потолок JPEG. Потолок ниже лимита Worker с запасом. */
        const val PHOTO_SIDE_PX = 1280
        const val PHOTO_MAX_BYTES = 512 * 1024

        /** Превью для ленты: качается вместо полного фото, пока его не откроют. */
        const val THUMB_SIDE_PX = 320
        const val THUMB_MAX_BYTES = 48 * 1024

        /** Совпадает с ограничением в firestore.rules. */
        const val MAX_SIDE_PX = 4096
    }
}

/** READ — прочитал хоть один (две галочки), READ_ALL — в группе прочитали все (три галочки). */
enum class MessageStatus { SENDING, SENT, READ, READ_ALL }

/**
 * Ссылка на сообщение, на которое отвечают. Превью хранится в самом ответе, чтобы цитату
 * можно было показать без чтения исходного сообщения (оно могло выйти за лимит загрузки).
 */
data class ReplyTo(
    val id: MessageId,
    val senderId: UserId,
    /** Начало текста или подписи; у фото без подписи пустое. */
    val preview: String,
    /** Отвечают на фото: перед превью показываем «📷 Фото». */
    val photo: Boolean = false,
) {
    companion object {
        /** Совпадает с ограничением в firestore.rules. */
        const val PREVIEW_MAX_LENGTH = 100

        fun of(message: Message): ReplyTo =
            ReplyTo(message.id, message.senderId, previewOf(message.text), photo = message.attachment != null)

        /** Одна строка без лишних пробелов, не длиннее [PREVIEW_MAX_LENGTH] вместе с многоточием. */
        fun previewOf(text: String): String {
            val line = text.trim().replace(Regex("\\s+"), " ")
            return if (line.length <= PREVIEW_MAX_LENGTH) line else line.take(PREVIEW_MAX_LENGTH - 1).trimEnd() + "…"
        }
    }
}
