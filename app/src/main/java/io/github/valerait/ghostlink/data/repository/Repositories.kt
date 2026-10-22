package io.github.valerait.ghostlink.data.repository

import io.github.valerait.ghostlink.core.model.Account
import io.github.valerait.ghostlink.core.model.Chat
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.ChatPrefs
import io.github.valerait.ghostlink.core.model.Device
import io.github.valerait.ghostlink.core.model.DeviceId
import io.github.valerait.ghostlink.core.model.DisappearingTimer
import io.github.valerait.ghostlink.core.model.GhostlinkError
import io.github.valerait.ghostlink.core.model.LinkRequest
import io.github.valerait.ghostlink.core.model.LinkRequestId
import io.github.valerait.ghostlink.core.model.Message
import io.github.valerait.ghostlink.core.model.MessageId
import io.github.valerait.ghostlink.core.model.Platform
import io.github.valerait.ghostlink.core.model.ReplyTo
import io.github.valerait.ghostlink.core.model.User
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.data.model.AccountKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

interface AuthRepository {
    val account: StateFlow<Account?>

    /** Ключ текущего аккаунта, сохранённый на этом устройстве; null — не вошли или ключа ещё нет. */
    val accountKey: StateFlow<AccountKey?>

    /** Новый аккаунт со свежим ключом. */
    suspend fun createAccount(): Account

    /** Вход по ключу: с другого устройства через QR или по ключу восстановления. Неверный ключ — [GhostlinkError.InvalidAccountKey]. */
    suspend fun signInWithKey(key: AccountKey): Account

    /** Временный аккаунт нового устройства: нужен только чтобы создать заявку на вход. */
    suspend fun signInAnonymously(): Account

    /**
     * Выдаёт ключ аккаунту, у которого его нет на этом устройстве:
     * анонимным аккаунтам из старой версии и аккаунтам, чей ключ на устройстве потерян.
     */
    suspend fun ensureAccountKey(): AccountKey

    /** Меняет секрет ключа: Firebase отзывает сессии всех остальных устройств. */
    suspend fun rotateKey(): AccountKey

    /** Выходит и забывает ключ на этом устройстве. */
    suspend fun signOut()
}

fun AuthRepository.requireUserId(): UserId = checkNotNull(account.value) { "Not signed in" }.id

interface UserRepository {
    fun observeUser(id: UserId): Flow<User?>

    fun observeUsers(ids: List<UserId>): Flow<Map<UserId, User>>

    suspend fun getUser(id: UserId): User?

    suspend fun saveProfile(
        id: UserId,
        name: String,
    )

    fun setOnline(
        id: UserId,
        online: Boolean,
    )
}

/** Личные настройки чатов: видны и меняются только у самого пользователя, собеседник о них не знает. */
interface ChatPrefsRepository {
    fun observeChatPrefs(userId: UserId): Flow<Map<ChatId, ChatPrefs>>

    fun setPinned(
        userId: UserId,
        chatId: ChatId,
        pinned: Boolean,
    )

    fun setMuted(
        userId: UserId,
        chatId: ChatId,
        muted: Boolean,
    )

    /**
     * Убирает чат из списка только у [userId]: переписка до этого момента у него больше не показывается,
     * у собеседника ничего не меняется. Чат вернётся с новым сообщением или при повторном добавлении друга.
     */
    fun removeChat(
        userId: UserId,
        chatId: ChatId,
    )
}

/** Исчезающие сообщения: таймер чата общий для обоих участников. */
interface DisappearingRepository {
    /** Включает или выключает исчезающие сообщения в чате — для обоих участников. */
    fun setDisappearing(
        chatId: ChatId,
        timer: DisappearingTimer?,
    )

    /** Стирает из базы исчезнувшие сообщения: на Spark нет TTL, поэтому за этим следят сами клиенты. */
    fun deleteExpired(
        chatId: ChatId,
        messageIds: List<MessageId>,
    )
}

/** Групповые чаты. Управляет составом и названием только админ; выйти может любой участник. */
interface GroupRepository {
    /** Создаёт группу с админом [ownerId] и возвращает её id. */
    suspend fun createGroup(
        ownerId: UserId,
        name: String,
        memberIds: List<UserId>,
    ): ChatId

    fun renameGroup(
        chatId: ChatId,
        name: String,
    )

    fun addMembers(
        chatId: ChatId,
        userIds: List<UserId>,
    )

    /**
     * Убирает участника: админ — любого, остальные — только себя (выход из группы).
     * Если выходит админ, права переходят к [newOwnerId].
     */
    fun removeMember(
        chatId: ChatId,
        userId: UserId,
        newOwnerId: UserId? = null,
    )
}

interface ChatRepository :
    ChatPrefsRepository,
    DisappearingRepository,
    GroupRepository {
    fun observeChats(userId: UserId): Flow<List<Chat>>

    fun observeChat(chatId: ChatId): Flow<Chat?>

    fun observeMessages(chatId: ChatId): Flow<List<Message>>

    /**
     * Не ждёт сервера: сообщение сразу появляется локально со статусом SENDING.
     * [ttl] — срок жизни исчезающего сообщения (таймер чата в момент отправки).
     */
    fun sendMessage(
        chatId: ChatId,
        senderId: UserId,
        text: String,
        replyTo: ReplyTo? = null,
        ttl: Duration? = null,
    )

    /**
     * Исправляет текст своего сообщения. [isLatest] — сообщение последнее в чате:
     * тогда вместе с ним правится и превью в списке чатов.
     */
    fun editMessage(
        chatId: ChatId,
        messageId: MessageId,
        text: String,
        isLatest: Boolean,
    )

    /**
     * Удаляет своё сообщение у обоих участников: вместо него остаётся отметка «Сообщение удалено».
     * Если удалено последнее ([isLatest]), превью в списке чатов переходит на [previous]
     * или пропадает, если сообщений не осталось.
     */
    fun deleteMessage(
        chatId: ChatId,
        messageId: MessageId,
        isLatest: Boolean,
        previous: Message?,
    )

    fun markRead(
        chatId: ChatId,
        userId: UserId,
    )

    /** Отметка «печатает…» для собеседника; с [typing] = false снимается сразу, иначе гаснет сама. */
    fun setTyping(
        chatId: ChatId,
        userId: UserId,
        typing: Boolean,
    )

    /** Создаёт чат пары (если его ещё нет), возвращает его в список, если он был удалён, и возвращает его id. */
    suspend fun startChat(
        userId: UserId,
        friendId: UserId,
    ): ChatId
}

interface DeviceRepository {
    fun observeDevices(userId: UserId): Flow<List<Device>>

    /** Испускает null, когда устройство удалили из реестра. */
    fun observeDevice(
        userId: UserId,
        deviceId: DeviceId,
    ): Flow<Device?>

    /** Создаёт или обновляет запись и время последней активности. */
    suspend fun registerDevice(
        userId: UserId,
        device: Device,
    )

    suspend fun removeDevice(
        userId: UserId,
        deviceId: DeviceId,
    )
}

interface LinkRepository {
    /** Создаёт заявку от имени текущего (временного) аккаунта и возвращает её id. */
    suspend fun createLinkRequest(
        deviceName: String,
        platform: Platform,
    ): LinkRequestId

    suspend fun getLinkRequest(id: LinkRequestId): LinkRequest?

    fun observeLinkRequest(id: LinkRequestId): Flow<LinkRequest?>

    /** Кладёт в заявку зашифрованный ключ аккаунта. */
    suspend fun approveLinkRequest(
        id: LinkRequestId,
        payload: ByteArray,
    )

    suspend fun deleteLinkRequest(id: LinkRequestId)
}
