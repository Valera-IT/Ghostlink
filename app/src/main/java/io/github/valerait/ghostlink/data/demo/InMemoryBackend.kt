package io.github.valerait.ghostlink.data.demo

import io.github.valerait.ghostlink.core.model.Account
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
import io.github.valerait.ghostlink.data.model.AccountKey
import io.github.valerait.ghostlink.data.repository.AuthRepository
import io.github.valerait.ghostlink.data.repository.ChatRepository
import io.github.valerait.ghostlink.data.repository.DeviceRepository
import io.github.valerait.ghostlink.data.repository.LinkRepository
import io.github.valerait.ghostlink.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Бэкенд в памяти: на нём приложение работает без Firebase (демо-режим),
 * и он же служит фейком в unit-тестах.
 */
class InMemoryBackend(
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val seedDemoData: Boolean = true,
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
    private val chats = MutableStateFlow<Map<ChatId, Chat>>(emptyMap())
    private val messages = MutableStateFlow<Map<ChatId, List<Message>>>(emptyMap())
    private val chatPrefs = MutableStateFlow<Map<UserId, Map<ChatId, ChatPrefs>>>(emptyMap())
    private val nextId = AtomicInteger()
    private var botReplyIndex = 0

    // region AuthRepository

    override val account: StateFlow<Account?> = _account.asStateFlow()

    override val accountKey: StateFlow<AccountKey?> = _accountKey.asStateFlow()

    override suspend fun createAccount(): Account {
        // В демо пользователь один, в тестах можно завести несколько.
        val id = if (accounts.isEmpty()) ME else UserId("user-${nextId.incrementAndGet()}")
        val key = AccountKey.generate()
        accounts[key.email] = key.password to id
        val account = Account(id = id, isAnonymous = false)
        if (seedDemoData) seed(account.id)
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
        val account = Account(id = UserId("temp-${nextId.incrementAndGet()}"), isAnonymous = true)
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

    override suspend fun signOut() {
        _account.value = null
        _accountKey.value = null
        users.value = emptyMap()
        chats.value = emptyMap()
        messages.value = emptyMap()
        chatPrefs.value = emptyMap()
        devices.value = emptyMap()
    }

    // endregion

    // region UserRepository

    override fun observeUser(id: UserId): Flow<User?> = users.map { it[id] }.distinctUntilChanged()

    override fun observeUsers(ids: List<UserId>): Flow<Map<UserId, User>> =
        users.map { all -> ids.mapNotNull { all[it] }.associateBy { it.id } }.distinctUntilChanged()

    override suspend fun getUser(id: UserId): User? {
        users.value[id]?.let { return it }
        if (!seedDemoData) return null
        // В демо любой отсканированный код «существует»: заводим для него собеседника.
        val stranger = User(id, "Призрак #${id.value.take(4)}", online = false, lastSeen = clock.now() - 1.hours)
        users.update { it + (id to stranger) }
        return stranger
    }

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
    ) {
        post(chatId, senderId, text, clock.now()) { it.copy(replyTo = replyTo, ttl = ttl) }
        val chat = chats.value[chatId] ?: return
        if (BOT_ID in chat.members && senderId != BOT_ID) replyAsBot(chatId)
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
                            if (it.id == messageId) it.copy(text = "", replyTo = null, editedAt = null, deletedAt = clock.now()) else it
                        }
                )
        }
        if (isLatest) {
            chats.update { all ->
                val chat = all[chatId] ?: return@update all
                all + (chatId to chat.copy(lastMessage = previous?.let { LastMessage(it.text, it.senderId, it.sentAt, it.ttl) }))
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
        val id = ChatId("g${nextId.incrementAndGet()}")
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
        devices.update { all -> all + (userId to all[userId].orEmpty() + (device.id to device.copy(lastActiveAt = clock.now()))) }
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
        val id = LinkRequestId("link${nextId.incrementAndGet()}".padEnd(20, '0'))
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

    private fun post(
        chatId: ChatId,
        senderId: UserId,
        text: String,
        at: Instant,
        extra: (Message) -> Message = { it },
    ) {
        val message = extra(Message(MessageId("m${nextId.incrementAndGet()}"), senderId, text, at))
        val ttl = message.ttl
        messages.update { it + (chatId to it[chatId].orEmpty() + message) }
        chats.update { all ->
            val chat = all[chatId] ?: return@update all
            all + (chatId to chat.copy(lastMessage = LastMessage(text, senderId, at, ttl), updatedAt = at, typing = chat.typing - senderId))
        }
    }

    private fun replyAsBot(chatId: ChatId) {
        scope.launch {
            delay(700)
            markRead(chatId, BOT_ID)
            setTyping(chatId, BOT_ID, true)
            delay(1_200)
            val ttl = chats.value[chatId]?.disappearing?.duration
            post(chatId, BOT_ID, BOT_REPLIES[botReplyIndex++ % BOT_REPLIES.size], clock.now()) { it.copy(ttl = ttl) }
        }
    }

    private fun seed(me: UserId) {
        val now = clock.now()
        users.value =
            mapOf(
                BOT_ID to User(BOT_ID, "Призрак", online = true),
                ANYA_ID to User(ANYA_ID, "Аня", online = false, lastSeen = now - 2.hours),
            )
        val botChat = ChatId.of(me, BOT_ID)
        val anyaChat = ChatId.of(me, ANYA_ID)
        chats.value =
            mapOf(
                botChat to Chat(botChat, listOf(me, BOT_ID).sortedBy { it.value }, updatedAt = now),
                anyaChat to Chat(anyaChat, listOf(me, ANYA_ID).sortedBy { it.value }, updatedAt = now),
            )
        post(anyaChat, ANYA_ID, "Привет! Спасибо, что отсканировал(а) мой код на встрече 🙂", now - 1.days - 3.hours)
        post(anyaChat, me, "Привет! Рад(а) знакомству", now - 1.days - 2.hours)
        post(anyaChat, ANYA_ID, "Скинешь фотки с митапа?", now - 1.days - 1.hours)
        post(anyaChat, me, "Конечно, вечером пришлю", now - 3.hours)
        markRead(anyaChat, ANYA_ID)
        post(anyaChat, ANYA_ID, "Жду 👀", now - 2.hours)
        devices.value = mapOf(me to mapOf(DEMO_LAPTOP to Device(DEMO_LAPTOP, "MacBook Air", Platform.MacOs, now - 3.hours)))
        post(botChat, BOT_ID, "Бу! Я Призрак — собеседник для демо-режима. Напиши мне что-нибудь 👻", now - 10.minutes)
        val group = ChatId("g-demo")
        val members = listOf(me, ANYA_ID, BOT_ID).sortedBy { it.value }
        chats.update { it + (group to Chat(group, members, updatedAt = now, group = GroupInfo("Митап 👻", ANYA_ID))) }
        post(group, ANYA_ID, "Собрала всех с митапа в одну группу", now - 1.hours)
        post(group, BOT_ID, "Бу! Я тоже тут", now - 30.minutes)
    }

    companion object {
        val ME = UserId("demo-me")
        val BOT_ID = UserId("ghost-bot")
        val ANYA_ID = UserId("demo-anya")
        private val DEMO_LAPTOP = DeviceId("demo-laptop")

        private val BOT_REPLIES =
            listOf(
                "Я тебя слышу, даже если ты меня не видишь 👻",
                "В Ghostlink нельзя найти человека по номеру — только встретиться и отсканировать код.",
                "Открой «Мой код», покажи QR другу — и вы сразу окажетесь в общем чате.",
                "Видишь две галочки? Значит, я прочитал. Призраки всё читают.",
                "Я живу только в демо-режиме. Подключи Firebase — и появятся настоящие люди.",
            )
    }
}
