package io.github.valerait.ghostlink.core.testing

import io.github.valerait.ghostlink.core.domain.repository.AuthRepository
import io.github.valerait.ghostlink.core.domain.repository.ChatRepository
import io.github.valerait.ghostlink.core.domain.repository.DeviceRepository
import io.github.valerait.ghostlink.core.domain.repository.LinkRepository
import io.github.valerait.ghostlink.core.domain.repository.UserRepository
import io.github.valerait.ghostlink.core.model.Account
import io.github.valerait.ghostlink.core.model.AccountKey
import io.github.valerait.ghostlink.core.model.Attachment
import io.github.valerait.ghostlink.core.model.Chat
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.ChatPrefs
import io.github.valerait.ghostlink.core.model.Device
import io.github.valerait.ghostlink.core.model.DeviceId
import io.github.valerait.ghostlink.core.model.DisappearingTimer
import io.github.valerait.ghostlink.core.model.GhostlinkError
import io.github.valerait.ghostlink.core.model.GroupInfo
import io.github.valerait.ghostlink.core.model.LINK_TTL
import io.github.valerait.ghostlink.core.model.LastMessage
import io.github.valerait.ghostlink.core.model.LinkRequest
import io.github.valerait.ghostlink.core.model.LinkRequestId
import io.github.valerait.ghostlink.core.model.Message
import io.github.valerait.ghostlink.core.model.MessageId
import io.github.valerait.ghostlink.core.model.Platform
import io.github.valerait.ghostlink.core.model.ReplyTo
import io.github.valerait.ghostlink.core.model.User
import io.github.valerait.ghostlink.core.model.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Все репозитории в памяти: фейк для тестов ViewModel и use case. Ведёт себя как сервер —
 * аккаунты переживают выход, а заявки на привязку истекают по [clock].
 * Демо-бэкенд (`core:demo`) наследует его и добавляет стартовые данные и бота.
 */
@OptIn(ExperimentalAtomicApi::class)
open class FakeBackend(
    protected val clock: Clock = Clock.System,
) : AuthRepository,
    UserRepository,
    ChatRepository,
    DeviceRepository,
    LinkRepository {
    private val _account = MutableStateFlow<Account?>(null)
    private val _accountKey = MutableStateFlow<AccountKey?>(null)

    /** «Сервер» аккаунтов: служебная почта -> (пароль, uid). Переживает выход, как настоящий Firebase. */
    private val accounts = mutableMapOf<String, Pair<String, UserId>>()
    private val devices = MutableStateFlow<Map<UserId, Map<DeviceId, Device>>>(emptyMap())
    private val linkRequests = MutableStateFlow<Map<LinkRequestId, LinkRequest>>(emptyMap())
    private val users = MutableStateFlow<Map<UserId, User>>(emptyMap())
    private val avatars = MutableStateFlow<Map<UserId, Pair<Long, ByteArray>>>(emptyMap())
    private val chats = MutableStateFlow<Map<ChatId, Chat>>(emptyMap())
    private val messages = MutableStateFlow<Map<ChatId, List<Message>>>(emptyMap())
    private val chatPrefs = MutableStateFlow<Map<UserId, Map<ChatId, ChatPrefs>>>(emptyMap())
    private val idCounter = AtomicInt(0)

    // region AuthRepository

    override val account: StateFlow<Account?> = _account.asStateFlow()

    override val accountKey: StateFlow<AccountKey?> = _accountKey.asStateFlow()

    override suspend fun createAccount(): Account {
        val id = UserId(nextId("user-"))
        val key = AccountKey.generate()
        accounts[key.email] = key.password to id
        val account = Account(id = id, isAnonymous = false)
        onAccountCreated(id)
        _accountKey.value = key
        _account.value = account
        return account
    }

    override suspend fun signInWithKey(key: AccountKey): Account {
        val (password, id) = accounts[key.email] ?: throw GhostlinkError.InvalidAccountKey()
        if (password != key.password) throw GhostlinkError.InvalidAccountKey()
        val account = Account(id = id, isAnonymous = false)
        _accountKey.value = key
        _account.value = account
        return account
    }

    override suspend fun signInAnonymously(): Account {
        val account = Account(id = UserId(nextId("temp-")), isAnonymous = true)
        _account.value = account
        return account
    }

    override suspend fun ensureAccountKey(): AccountKey {
        val account = checkNotNull(_account.value) { "Not signed in" }
        _accountKey.value?.let { return it }
        val key = AccountKey.generate()
        accounts[key.email] = key.password to account.id
        _accountKey.value = key
        _account.value = account.copy(isAnonymous = false)
        return key
    }

    override suspend fun rotateKey(): AccountKey {
        val current = checkNotNull(_accountKey.value) { "No account key" }
        val next = current.rotated()
        accounts[next.email] = next.password to accounts.getValue(current.email).second
        _accountKey.value = next
        return next
    }

    // Выход забывает только сессию этого устройства: профиль, чаты и устройства живут на «сервере»,
    // как в Firebase, и возвращаются после входа по ключу или с другого устройства.
    override suspend fun signOut() {
        _account.value = null
        _accountKey.value = null
    }

    // endregion

    // region UserRepository

    override fun observeUser(id: UserId): Flow<User?> = users.map { it[id] }.distinctUntilChanged()

    override fun observeUsers(ids: List<UserId>): Flow<Map<UserId, User>> =
        users.map { all -> ids.mapNotNull { all[it] }.associateBy { it.id } }.distinctUntilChanged()

    override suspend fun getUser(id: UserId): User? = users.value[id]

    override suspend fun saveProfile(
        id: UserId,
        name: String,
    ) {
        users.update {
            val user = it[id]?.copy(name = name) ?: User(id, name, online = true, lastSeen = clock.now())
            it + (id to user)
        }
    }

    override fun setOnline(
        id: UserId,
        online: Boolean,
    ) {
        users.update { all ->
            val user = all[id] ?: return@update all
            all + (id to user.copy(online = online, lastSeen = clock.now()))
        }
    }

    override suspend fun getAvatar(
        id: UserId,
        version: Long,
    ): ByteArray? = avatars.value[id]?.takeIf { it.first == version }?.second

    override suspend fun setAvatar(
        id: UserId,
        jpeg: ByteArray,
    ) {
        require(jpeg.isNotEmpty() && jpeg.size <= User.AVATAR_MAX_BYTES) { "Avatar is ${jpeg.size} bytes" }
        // Версия растёт, даже если два фото поставили в одну миллисекунду.
        val version = maxOf(clock.now().toEpochMilliseconds(), (avatars.value[id]?.first ?: 0) + 1)
        avatars.update { it + (id to (version to jpeg)) }
        users.update { all -> all[id]?.let { all + (id to it.copy(avatarVersion = version)) } ?: all }
    }

    override suspend fun removeAvatar(id: UserId) {
        users.update { all -> all[id]?.let { all + (id to it.copy(avatarVersion = null)) } ?: all }
        avatars.update { it - id }
    }

    // endregion

    // region ChatRepository

    override fun observeChats(userId: UserId): Flow<List<Chat>> =
        chats
            .map { all ->
                all.values.filter { userId in it.members }.sortedByDescending { it.updatedAt ?: Instant.DISTANT_FUTURE }
            }.distinctUntilChanged()

    override fun observeChat(chatId: ChatId): Flow<Chat?> = chats.map { it[chatId] }.distinctUntilChanged()

    override fun observeMessages(chatId: ChatId): Flow<List<Message>> = messages.map { it[chatId].orEmpty() }.distinctUntilChanged()

    override fun sendMessage(
        chatId: ChatId,
        senderId: UserId,
        text: String,
        replyTo: ReplyTo?,
        ttl: Duration?,
        attachment: Attachment?,
    ) {
        post(chatId, senderId, text, clock.now()) { it.copy(replyTo = replyTo, ttl = ttl, attachment = attachment) }
    }

    override fun editMessage(
        chatId: ChatId,
        messageId: MessageId,
        text: String,
        isLatest: Boolean,
    ) {
        messages.update { all ->
            val list = all[chatId] ?: return@update all
            all + (chatId to list.map { if (it.id == messageId) it.copy(text = text, editedAt = clock.now()) else it })
        }
        if (isLatest) {
            chats.update { all ->
                val chat = all[chatId] ?: return@update all
                all + (chatId to chat.copy(lastMessage = chat.lastMessage?.copy(text = text)))
            }
        }
    }

    override fun deleteMessage(
        chatId: ChatId,
        messageId: MessageId,
        isLatest: Boolean,
        previous: Message?,
    ) {
        messages.update { all ->
            val list = all[chatId] ?: return@update all
            all +
                (
                    chatId to
                        list.map {
                            if (it.id == messageId) {
                                it.copy(text = "", replyTo = null, editedAt = null, attachment = null, deletedAt = clock.now())
                            } else {
                                it
                            }
                        }
                )
        }
        if (isLatest) {
            chats.update { all ->
                val chat = all[chatId] ?: return@update all
                all + (chatId to chat.copy(lastMessage = previous?.let(::lastMessageOf)))
            }
        }
    }

    override fun markRead(
        chatId: ChatId,
        userId: UserId,
    ) {
        chats.update { all ->
            val chat = all[chatId] ?: return@update all
            all + (chatId to chat.copy(lastRead = chat.lastRead + (userId to clock.now())))
        }
    }

    override fun setTyping(
        chatId: ChatId,
        userId: UserId,
        typing: Boolean,
    ) {
        chats.update { all ->
            val chat = all[chatId] ?: return@update all
            all + (chatId to chat.copy(typing = if (typing) chat.typing + (userId to clock.now()) else chat.typing - userId))
        }
    }

    override fun setDisappearing(
        chatId: ChatId,
        timer: DisappearingTimer?,
    ) {
        chats.update { all ->
            val chat = all[chatId] ?: return@update all
            all + (chatId to chat.copy(disappearing = timer))
        }
    }

    override fun deleteExpired(
        chatId: ChatId,
        messageIds: List<MessageId>,
    ) {
        val ids = messageIds.toSet()
        messages.update { all ->
            val list = all[chatId] ?: return@update all
            all + (chatId to list.filterNot { it.id in ids })
        }
    }

    override suspend fun createGroup(
        ownerId: UserId,
        name: String,
        memberIds: List<UserId>,
    ): ChatId {
        val id = ChatId(nextId("g"))
        val chat =
            Chat(
                id = id,
                members = (memberIds + ownerId).distinct().sortedBy { it.value },
                updatedAt = clock.now(),
                group = GroupInfo(name, ownerId),
            )
        chats.update { it + (id to chat) }
        return id
    }

    override fun renameGroup(
        chatId: ChatId,
        name: String,
    ) = updateGroup(chatId) { it.copy(group = it.group?.copy(name = name)) }

    override fun addMembers(
        chatId: ChatId,
        userIds: List<UserId>,
    ) = updateGroup(chatId) { it.copy(members = (it.members + userIds).distinct()) }

    override fun removeMember(
        chatId: ChatId,
        userId: UserId,
        newOwnerId: UserId?,
    ) = updateGroup(chatId) { chat ->
        chat.copy(
            members = chat.members - userId,
            group = chat.group?.let { if (newOwnerId != null) it.copy(ownerId = newOwnerId) else it },
        )
    }

    private fun updateGroup(
        chatId: ChatId,
        change: (Chat) -> Chat,
    ) {
        chats.update { all ->
            val chat = all[chatId]?.takeIf { it.isGroup } ?: return@update all
            all + (chatId to change(chat))
        }
    }

    override fun observeChatPrefs(userId: UserId): Flow<Map<ChatId, ChatPrefs>> =
        chatPrefs.map { it[userId].orEmpty() }.distinctUntilChanged()

    override fun setPinned(
        userId: UserId,
        chatId: ChatId,
        pinned: Boolean,
    ) = updatePrefs(userId, chatId) { it.copy(pinnedAt = if (pinned) clock.now() else null) }

    override fun setMuted(
        userId: UserId,
        chatId: ChatId,
        muted: Boolean,
    ) = updatePrefs(userId, chatId) { it.copy(muted = muted) }

    override fun removeChat(
        userId: UserId,
        chatId: ChatId,
    ) = updatePrefs(userId, chatId) { it.copy(hidden = true, clearedAt = clock.now(), pinnedAt = null) }

    override suspend fun startChat(
        userId: UserId,
        friendId: UserId,
    ): ChatId {
        require(userId != friendId) { "Cannot start a chat with yourself" }
        val id = ChatId.of(userId, friendId)
        chats.update { all ->
            if (id in all) all else all + (id to Chat(id, listOf(userId, friendId).sortedBy { it.value }, updatedAt = clock.now()))
        }
        if (chatPrefs.value[userId]?.get(id)?.hidden == true) updatePrefs(userId, id) { it.copy(hidden = false) }
        return id
    }

    private fun updatePrefs(
        userId: UserId,
        chatId: ChatId,
        change: (ChatPrefs) -> ChatPrefs,
    ) {
        chatPrefs.update { all ->
            val mine = all[userId].orEmpty()
            all + (userId to mine + (chatId to change(mine[chatId] ?: ChatPrefs())))
        }
    }

    // endregion

    // region DeviceRepository

    override fun observeDevices(userId: UserId): Flow<List<Device>> =
        devices.map { it[userId].orEmpty().values.sortedByDescending { d -> d.lastActiveAt } }.distinctUntilChanged()

    override fun observeDevice(
        userId: UserId,
        deviceId: DeviceId,
    ): Flow<Device?> = devices.map { it[userId]?.get(deviceId) }.distinctUntilChanged()

    override suspend fun registerDevice(
        userId: UserId,
        device: Device,
    ) {
        devices.update { all ->
            val mine = all[userId].orEmpty()
            val token = device.pushToken ?: mine[device.id]?.pushToken
            all + (userId to mine + (device.id to device.copy(lastActiveAt = clock.now(), pushToken = token)))
        }
    }

    override suspend fun removeDevice(
        userId: UserId,
        deviceId: DeviceId,
    ) {
        devices.update { all -> all + (userId to all[userId].orEmpty() - deviceId) }
    }

    // endregion

    // region LinkRepository

    override suspend fun createLinkRequest(
        deviceName: String,
        platform: Platform,
    ): LinkRequestId {
        checkNotNull(_account.value) { "Not signed in" }
        val id = LinkRequestId(nextId("link").padEnd(LINK_REQUEST_ID_LENGTH, '0'))
        linkRequests.update { it + (id to LinkRequest(id, deviceName, platform, expiresAt = clock.now() + LINK_TTL)) }
        return id
    }

    override suspend fun getLinkRequest(id: LinkRequestId): LinkRequest? = linkRequests.value[id]

    override fun observeLinkRequest(id: LinkRequestId): Flow<LinkRequest?> = linkRequests.map { it[id] }.distinctUntilChanged()

    override suspend fun approveLinkRequest(
        id: LinkRequestId,
        payload: ByteArray,
    ) {
        linkRequests.update { all ->
            val request = checkNotNull(all[id]) { "No such link request" }
            check(request.payload == null && !request.isExpired(clock.now())) { "Link request is no longer valid" }
            all + (id to request.copy(payload = payload))
        }
    }

    override suspend fun deleteLinkRequest(id: LinkRequestId) {
        linkRequests.update { it - id }
    }

    // endregion

    // region Для наследников: начальные данные демо-режима

    /** Аккаунт создан, но ещё не опубликован в [account]. */
    protected open fun onAccountCreated(id: UserId) {}

    protected fun nextId(prefix: String): String = "$prefix${idCounter.incrementAndFetch()}"

    protected fun chat(chatId: ChatId): Chat? = chats.value[chatId]

    protected fun putUser(user: User) {
        users.update { it + (user.id to user) }
    }

    protected fun putChat(chat: Chat) {
        chats.update { it + (chat.id to chat) }
    }

    protected fun putDevice(
        userId: UserId,
        device: Device,
    ) {
        devices.update { all -> all + (userId to all[userId].orEmpty() + (device.id to device)) }
    }

    /** Сообщение с заданным временем отправки: [sendMessage] всегда ставит «сейчас». */
    protected fun post(
        chatId: ChatId,
        senderId: UserId,
        text: String,
        at: Instant,
        extra: (Message) -> Message = { it },
    ) {
        val message = extra(Message(MessageId(nextId("m")), senderId, text, at))
        messages.update { it + (chatId to it[chatId].orEmpty() + message) }
        chats.update { all ->
            val chat = all[chatId] ?: return@update all
            all + (chatId to chat.copy(lastMessage = lastMessageOf(message), updatedAt = at, typing = chat.typing - senderId))
        }
    }

    private fun lastMessageOf(message: Message) =
        LastMessage(message.text, message.senderId, message.sentAt, message.ttl, photo = message.attachment != null)

    // endregion

    private companion object {
        /** Как у автоматических id Firestore. */
        const val LINK_REQUEST_ID_LENGTH = 20
    }
}
