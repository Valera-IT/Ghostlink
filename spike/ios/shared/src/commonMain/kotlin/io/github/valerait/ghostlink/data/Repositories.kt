package io.github.valerait.ghostlink.data

import io.github.valerait.ghostlink.model.Account
import io.github.valerait.ghostlink.model.AccountKey
import io.github.valerait.ghostlink.model.Chat
import io.github.valerait.ghostlink.model.ChatPrefs
import io.github.valerait.ghostlink.model.Device
import io.github.valerait.ghostlink.model.DisappearingTimer
import io.github.valerait.ghostlink.model.LinkRequest
import io.github.valerait.ghostlink.model.Message
import io.github.valerait.ghostlink.model.ReplyTo
import io.github.valerait.ghostlink.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

// Те же контракты, что в android-модуле, без входа через Google (на iOS он в разведку не входит).

interface AuthRepository {
    val account: StateFlow<Account?>
    val accountKey: StateFlow<AccountKey?>

    suspend fun createAccount(): Account
    suspend fun signInWithKey(key: AccountKey): Account
    suspend fun signInAnonymously(): Account
    suspend fun ensureAccountKey(): AccountKey
    suspend fun rotateKey(): AccountKey
    suspend fun signOut()
}

/** Ключ не подошёл: аккаунта нет или секрет уже сменили. */
class InvalidAccountKeyException(cause: Throwable? = null) : Exception(cause)

fun AuthRepository.requireUserId(): String = checkNotNull(account.value) { "Not signed in" }.id

interface UserRepository {
    fun observeUser(id: String): Flow<User?>
    fun observeUsers(ids: List<String>): Flow<Map<String, User>>
    suspend fun getUser(id: String): User?
    suspend fun saveProfile(id: String, name: String)
    suspend fun setOnline(id: String, online: Boolean)
}

interface ChatRepository {
    fun observeChats(userId: String): Flow<List<Chat>>
    fun observeChat(chatId: String): Flow<Chat?>
    fun observeMessages(chatId: String): Flow<List<Message>>
    /** [ttl] — срок жизни исчезающего сообщения в секундах; null — не исчезает. */
    fun sendMessage(chatId: String, senderId: String, text: String, replyTo: ReplyTo? = null, ttl: Long? = null)
    /** Исправляет своё сообщение; [isLatest] — оно последнее, и превью в списке чатов правится тоже. */
    fun editMessage(chatId: String, messageId: String, text: String, isLatest: Boolean)

    /** Удаляет своё сообщение у обоих; если оно последнее, превью переходит на [previous] или пропадает. */
    fun deleteMessage(chatId: String, messageId: String, isLatest: Boolean, previous: Message?)
    fun markRead(chatId: String, userId: String)
    /** Отметка «печатает…»: true ставит серверное время, false снимает. */
    fun setTyping(chatId: String, userId: String, typing: Boolean)
    /** Таймер общий для чата; null выключает. */
    fun setDisappearing(chatId: String, timer: DisappearingTimer?)
    /** Стирает из базы сообщения, срок которых вышел; это может сделать любой участник. */
    fun deleteExpired(chatId: String, messageIds: List<String>)
    /** Создаёт чат пары, если его нет, и возвращает его в список, если пользователь его удалял. */
    suspend fun startChat(userId: String, friendId: String): String

    /** chatId -> личные настройки чатов пользователя. */
    fun observeChatPrefs(userId: String): Flow<Map<String, ChatPrefs>>
    fun setPinned(userId: String, chatId: String, pinned: Boolean)
    fun setMuted(userId: String, chatId: String, muted: Boolean)
    /** Убирает чат из списка только у [userId] и скрывает его прежние сообщения. */
    fun removeChat(userId: String, chatId: String)

    suspend fun createGroup(ownerId: String, name: String, memberIds: List<String>): String
    fun renameGroup(chatId: String, name: String)
    fun addMembers(chatId: String, userIds: List<String>)
    /** Убирает участника; при выходе админа [newOwnerId] — кому передать права. */
    fun removeMember(chatId: String, userId: String, newOwnerId: String? = null)
}

/** Выход из группы. Админ передаёт права следующему участнику; последний участник остаётся как есть. */
fun ChatRepository.leaveGroup(chat: Chat, me: String) {
    val others = chat.members.filter { it != me }
    if (others.isEmpty()) return
    removeMember(chat.id, me, if (chat.group?.ownerId == me) others.first() else null)
}

interface DeviceRepository {
    fun observeDevices(userId: String): Flow<List<Device>>
    fun observeDevice(userId: String, deviceId: String): Flow<Device?>
    suspend fun registerDevice(userId: String, device: Device)
    suspend fun removeDevice(userId: String, deviceId: String)
}

interface LinkRepository {
    suspend fun createLinkRequest(deviceName: String, platform: String): String
    suspend fun getLinkRequest(id: String): LinkRequest?
    fun observeLinkRequest(id: String): Flow<LinkRequest?>
    suspend fun approveLinkRequest(id: String, payload: ByteArray)
    suspend fun deleteLinkRequest(id: String)
}
