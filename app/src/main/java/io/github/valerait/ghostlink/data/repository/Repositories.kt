package io.github.valerait.ghostlink.data.repository

import io.github.valerait.ghostlink.data.model.Account
import io.github.valerait.ghostlink.data.model.AccountKey
import io.github.valerait.ghostlink.data.model.Chat
import io.github.valerait.ghostlink.data.model.Device
import io.github.valerait.ghostlink.data.model.LinkRequest
import io.github.valerait.ghostlink.data.model.Message
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

    suspend fun signInWithGoogle(idToken: String): Account

    /** Превращает анонимный аккаунт в постоянный: uid и вся переписка сохраняются. */
    suspend fun linkGoogle(idToken: String)

    /**
     * Выдаёт ключ аккаунту, у которого его нет на этом устройстве:
     * анонимным аккаунтам из старой версии и аккаунтам, восстановленным через Google.
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

/** Google-аккаунт уже привязан к другому профилю Ghostlink. */
class AccountAlreadyLinkedException : Exception()

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

interface ChatRepository {
    fun observeChats(userId: String): Flow<List<Chat>>

    fun observeChat(chatId: String): Flow<Chat?>

    fun observeMessages(chatId: String): Flow<List<Message>>

    /** Не ждёт сервера: сообщение сразу появляется локально со статусом SENDING. */
    fun sendMessage(
        chatId: String,
        senderId: String,
        text: String,
    )

    fun markRead(
        chatId: String,
        userId: String,
    )

    /** Создаёт чат пары (если его ещё нет) и возвращает его id. */
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
