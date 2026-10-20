package io.github.valerait.ghostlink.data.repository

import io.github.valerait.ghostlink.data.model.Account
import io.github.valerait.ghostlink.data.model.AccountKey
import io.github.valerait.ghostlink.data.model.Chat
import io.github.valerait.ghostlink.data.model.ChatPrefs
import io.github.valerait.ghostlink.data.model.Device
import io.github.valerait.ghostlink.data.model.DisappearingTimer
import io.github.valerait.ghostlink.data.model.LinkRequest
import io.github.valerait.ghostlink.data.model.Message
import io.github.valerait.ghostlink.data.model.ReplyTo
import io.github.valerait.ghostlink.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val account: StateFlow<Account?>

    /** Ключ текущего аккаунта, сохранённый на этом устройстве; null — не вошли или ключа ещё нет. */
    val accountKey: StateFlow<AccountKey?>

    /** Новый аккаунт со свежим ключом. */
    suspend fun createAccount(): Account

    /** Вход по ключу: с другого устройства через QR или по ключу восстановления. */
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

/** Ключ не подошёл: аккаунта нет или секрет уже сменили. */
class InvalidAccountKeyException(
    cause: Throwable? = null,
) : Exception(cause)

fun AuthRepository.requireUserId(): String = checkNotNull(account.value) { "Not signed in" }.id

interface UserRepository {
    fun observeUser(id: String): Flow<User?>

    fun observeUsers(ids: List<String>): Flow<Map<String, User>>

    suspend fun getUser(id: String): User?

    suspend fun saveProfile(
        id: String,
        name: String,
    )

    fun setOnline(
        id: String,
        online: Boolean,
    )
}

/** Личные настройки чатов: видны и меняются только у самого пользователя, собеседник о них не знает. */
interface ChatPrefsRepository {
    /** chatId -> [ChatPrefs]. */
    fun observeChatPrefs(userId: String): Flow<Map<String, ChatPrefs>>

    fun setPinned(
        userId: String,
        chatId: String,
        pinned: Boolean,
    )

    fun setMuted(
        userId: String,
        chatId: String,
        muted: Boolean,
    )

    /**
     * Убирает чат из списка только у [userId]: переписка до этого момента у него больше не показывается,
     * у собеседника ничего не меняется. Чат вернётся с новым сообщением или при повторном добавлении друга.
     */
    fun removeChat(
        userId: String,
        chatId: String,
    )
}

/** Исчезающие сообщения: таймер чата общий для обоих участников. */
interface DisappearingRepository {
    /** Включает или выключает исчезающие сообщения в чате — для обоих участников. */
    fun setDisappearing(
        chatId: String,
        timer: DisappearingTimer?,
    )

    /** Стирает из базы исчезнувшие сообщения: на Spark нет TTL, поэтому за этим следят сами клиенты. */
    fun deleteExpired(
        chatId: String,
        messageIds: List<String>,
    )
}

/** Групповые чаты. Управляет составом и названием только админ; выйти может любой участник. */
interface GroupRepository {
    /** Создаёт группу с админом [ownerId] и возвращает её id. */
    suspend fun createGroup(
        ownerId: String,
        name: String,
        memberIds: List<String>,
    ): String

    fun renameGroup(
        chatId: String,
        name: String,
    )

    fun addMembers(
        chatId: String,
        userIds: List<String>,
    )

    /**
     * Убирает участника: админ — любого, остальные — только себя (выход из группы).
     * Если выходит админ, права переходят к [newOwnerId].
     */
    fun removeMember(
        chatId: String,
        userId: String,
        newOwnerId: String? = null,
    )
}

interface ChatRepository :
    ChatPrefsRepository,
    DisappearingRepository,
    GroupRepository {
    fun observeChats(userId: String): Flow<List<Chat>>

    fun observeChat(chatId: String): Flow<Chat?>

    fun observeMessages(chatId: String): Flow<List<Message>>

    /**
     * Не ждёт сервера: сообщение сразу появляется локально со статусом SENDING.
     * [ttl] — срок жизни исчезающего сообщения в секундах (таймер чата в момент отправки).
     */
    fun sendMessage(
        chatId: String,
        senderId: String,
        text: String,
        replyTo: ReplyTo? = null,
        ttl: Long? = null,
    )

    /**
     * Исправляет текст своего сообщения. [isLatest] — сообщение последнее в чате:
     * тогда вместе с ним правится и превью в списке чатов.
     */
    fun editMessage(
        chatId: String,
        messageId: String,
        text: String,
        isLatest: Boolean,
    )

    /**
     * Удаляет своё сообщение у обоих участников: вместо него остаётся отметка «Сообщение удалено».
     * Если удалено последнее ([isLatest]), превью в списке чатов переходит на [previous]
     * или пропадает, если сообщений не осталось.
     */
    fun deleteMessage(
        chatId: String,
        messageId: String,
        isLatest: Boolean,
        previous: Message?,
    )

    fun markRead(
        chatId: String,
        userId: String,
    )

    /** Отметка «печатает…» для собеседника; с [typing] = false снимается сразу, иначе гаснет сама. */
    fun setTyping(
        chatId: String,
        userId: String,
        typing: Boolean,
    )

    /** Создаёт чат пары (если его ещё нет), возвращает его в список, если он был удалён, и возвращает его id. */
    suspend fun startChat(
        userId: String,
        friendId: String,
    ): String
}

interface DeviceRepository {
    fun observeDevices(userId: String): Flow<List<Device>>

    /** Испускает null, когда устройство удалили из реестра. */
    fun observeDevice(
        userId: String,
        deviceId: String,
    ): Flow<Device?>

    /** Создаёт или обновляет запись и время последней активности. */
    suspend fun registerDevice(
        userId: String,
        device: Device,
    )

    suspend fun removeDevice(
        userId: String,
        deviceId: String,
    )
}

interface LinkRepository {
    /** Создаёт заявку от имени текущего (временного) аккаунта и возвращает её id. */
    suspend fun createLinkRequest(
        deviceName: String,
        platform: String,
    ): String

    suspend fun getLinkRequest(id: String): LinkRequest?

    fun observeLinkRequest(id: String): Flow<LinkRequest?>

    /** Кладёт в заявку зашифрованный ключ аккаунта. */
    suspend fun approveLinkRequest(
        id: String,
        payload: ByteArray,
    )

    suspend fun deleteLinkRequest(id: String)
}
