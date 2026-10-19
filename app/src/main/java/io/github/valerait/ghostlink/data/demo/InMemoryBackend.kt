package io.github.valerait.ghostlink.data.demo

import io.github.valerait.ghostlink.data.model.Account
import io.github.valerait.ghostlink.data.model.AccountKey
import io.github.valerait.ghostlink.data.model.Chat
import io.github.valerait.ghostlink.data.model.ChatIds
import io.github.valerait.ghostlink.data.model.Device
import io.github.valerait.ghostlink.data.model.LINK_TTL_MS
import io.github.valerait.ghostlink.data.model.LastMessage
import io.github.valerait.ghostlink.data.model.LinkRequest
import io.github.valerait.ghostlink.data.model.Message
import io.github.valerait.ghostlink.data.model.User
import io.github.valerait.ghostlink.data.repository.AuthRepository
import io.github.valerait.ghostlink.data.repository.ChatRepository
import io.github.valerait.ghostlink.data.repository.DeviceRepository
import io.github.valerait.ghostlink.data.repository.InvalidAccountKeyException
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

/**
 * Бэкенд в памяти: на нём приложение работает без Firebase (демо-режим),
 * и он же служит фейком в unit-тестах.
 */
class InMemoryBackend(
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val seedDemoData: Boolean = true,
) : AuthRepository,
    UserRepository,
    ChatRepository,
    DeviceRepository,
    LinkRepository {
    private val _account = MutableStateFlow<Account?>(null)
    private val _accountKey = MutableStateFlow<AccountKey?>(null)

    /** «Сервер» аккаунтов: служебная почта -> (пароль, uid). Переживает выход, как настоящий Firebase. */
    private val accounts = mutableMapOf<String, Pair<String, String>>()
    private val devices = MutableStateFlow<Map<String, Map<String, Device>>>(emptyMap())
    private val linkRequests = MutableStateFlow<Map<String, LinkRequest>>(emptyMap())
    private val users = MutableStateFlow<Map<String, User>>(emptyMap())
    private val chats = MutableStateFlow<Map<String, Chat>>(emptyMap())
    private val messages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    private val nextId = AtomicInteger()
    private var botReplyIndex = 0

    // region AuthRepository

    override val account: StateFlow<Account?> = _account.asStateFlow()

    override val accountKey: StateFlow<AccountKey?> = _accountKey.asStateFlow()

    override suspend fun createAccount(): Account {
        // В демо пользователь один, в тестах можно завести несколько.
        val id = if (accounts.isEmpty()) ME else "user-${nextId.incrementAndGet()}"
        val key = AccountKey.generate()
        accounts[key.email] = key.password to id
        val account = Account(id = id, isAnonymous = false, googleEmail = null)
        if (seedDemoData) seed(account.id)
        _accountKey.value = key
        _account.value = account
        return account
    }

    override suspend fun signInWithKey(key: AccountKey): Account {
        val (password, id) = accounts[key.email] ?: throw InvalidAccountKeyException()
        if (password != key.password) throw InvalidAccountKeyException()
        val account = Account(id = id, isAnonymous = false, googleEmail = null)
        _accountKey.value = key
        _account.value = account
        return account
    }

    override suspend fun signInAnonymously(): Account {
        val account = Account(id = "temp-${nextId.incrementAndGet()}", isAnonymous = true, googleEmail = null)
        _account.value = account
        return account
    }

    override suspend fun signInWithGoogle(idToken: String): Account =
        throw UnsupportedOperationException("Google sign-in is not available in demo mode")

    override suspend fun linkGoogle(idToken: String) = throw UnsupportedOperationException("Google sign-in is not available in demo mode")

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
        devices.value = emptyMap()
    }

    // endregion

    // region UserRepository

    override fun observeUser(id: String): Flow<User?> = users.map { it[id] }.distinctUntilChanged()

    override fun observeUsers(ids: List<String>): Flow<Map<String, User>> =
        users.map { all -> ids.mapNotNull { all[it] }.associateBy { it.id } }.distinctUntilChanged()

    override suspend fun getUser(id: String): User? {
        users.value[id]?.let { return it }
        if (!seedDemoData) return null
        // В демо любой отсканированный код «существует»: заводим для него собеседника.
        val stranger = User(id, "Призрак #${id.take(4)}", online = false, lastSeen = clock() - HOUR)
        users.update { it + (id to stranger) }
        return stranger
    }

    override suspend fun saveProfile(
        id: String,
        name: String,
    ) {
        users.update {
            val user = it[id]?.copy(name = name) ?: User(id, name, online = true, lastSeen = clock())
            it + (id to user)
        }
    }

    override fun setOnline(
        id: String,
        online: Boolean,
    ) {
        users.update { all ->
            val user = all[id] ?: return@update all
            all + (id to user.copy(online = online, lastSeen = clock()))
        }
    }

    // endregion

    // region ChatRepository

    override fun observeChats(userId: String): Flow<List<Chat>> =
        chats
            .map { all ->
                all.values.filter { userId in it.members }.sortedByDescending { it.updatedAt ?: Long.MAX_VALUE }
            }.distinctUntilChanged()

    override fun observeChat(chatId: String): Flow<Chat?> = chats.map { it[chatId] }.distinctUntilChanged()

    override fun observeMessages(chatId: String): Flow<List<Message>> = messages.map { it[chatId].orEmpty() }.distinctUntilChanged()

    override fun sendMessage(
        chatId: String,
        senderId: String,
        text: String,
    ) {
        post(chatId, senderId, text, clock())
        val chat = chats.value[chatId] ?: return
        if (BOT_ID in chat.members && senderId != BOT_ID) replyAsBot(chatId)
    }

    override fun markRead(
        chatId: String,
        userId: String,
    ) {
        chats.update { all ->
            val chat = all[chatId] ?: return@update all
            all + (chatId to chat.copy(lastRead = chat.lastRead + (userId to clock())))
        }
    }

    override suspend fun startChat(
        userId: String,
        friendId: String,
    ): String {
        require(userId != friendId) { "Cannot start a chat with yourself" }
        val id = ChatIds.of(userId, friendId)
        chats.update { all ->
            if (id in all) all else all + (id to Chat(id, listOf(userId, friendId).sorted(), updatedAt = clock()))
        }
        return id
    }

    // endregion

    // region DeviceRepository

    override fun observeDevices(userId: String): Flow<List<Device>> =
        devices.map { it[userId].orEmpty().values.sortedByDescending { d -> d.lastActiveAt } }.distinctUntilChanged()

    override fun observeDevice(
        userId: String,
        deviceId: String,
    ): Flow<Device?> = devices.map { it[userId]?.get(deviceId) }.distinctUntilChanged()

    override suspend fun registerDevice(
        userId: String,
        device: Device,
    ) {
        devices.update { all -> all + (userId to all[userId].orEmpty() + (device.id to device.copy(lastActiveAt = clock()))) }
    }

    override suspend fun removeDevice(
        userId: String,
        deviceId: String,
    ) {
        devices.update { all -> all + (userId to all[userId].orEmpty() - deviceId) }
    }

    // endregion

    // region LinkRepository

    override suspend fun createLinkRequest(
        deviceName: String,
        platform: String,
    ): String {
        checkNotNull(_account.value) { "Not signed in" }
        val id = "link${nextId.incrementAndGet()}".padEnd(20, '0')
        linkRequests.update { it + (id to LinkRequest(id, deviceName, platform, expiresAt = clock() + LINK_TTL_MS)) }
        return id
    }

    override suspend fun getLinkRequest(id: String): LinkRequest? = linkRequests.value[id]

    override fun observeLinkRequest(id: String): Flow<LinkRequest?> = linkRequests.map { it[id] }.distinctUntilChanged()

    override suspend fun approveLinkRequest(
        id: String,
        payload: ByteArray,
    ) {
        linkRequests.update { all ->
            val request = checkNotNull(all[id]) { "No such link request" }
            check(request.payload == null && !request.isExpired(clock())) { "Link request is no longer valid" }
            all + (id to request.copy(payload = payload))
        }
    }

    override suspend fun deleteLinkRequest(id: String) {
        linkRequests.update { it - id }
    }

    // endregion

    private fun post(
        chatId: String,
        senderId: String,
        text: String,
        at: Long,
    ) {
        val message = Message("m${nextId.incrementAndGet()}", senderId, text, at)
        messages.update { it + (chatId to it[chatId].orEmpty() + message) }
        chats.update { all ->
            val chat = all[chatId] ?: return@update all
            all + (chatId to chat.copy(lastMessage = LastMessage(text, senderId, at), updatedAt = at))
        }
    }

    private fun replyAsBot(chatId: String) {
        scope.launch {
            delay(700)
            markRead(chatId, BOT_ID)
            delay(1_200)
            post(chatId, BOT_ID, BOT_REPLIES[botReplyIndex++ % BOT_REPLIES.size], clock())
        }
    }

    private fun seed(me: String) {
        val now = clock()
        users.value =
            mapOf(
                BOT_ID to User(BOT_ID, "Призрак", online = true),
                ANYA_ID to User(ANYA_ID, "Аня", online = false, lastSeen = now - 2 * HOUR),
            )
        val botChat = ChatIds.of(me, BOT_ID)
        val anyaChat = ChatIds.of(me, ANYA_ID)
        chats.value =
            mapOf(
                botChat to Chat(botChat, listOf(me, BOT_ID).sorted(), updatedAt = now),
                anyaChat to Chat(anyaChat, listOf(me, ANYA_ID).sorted(), updatedAt = now),
            )
        post(anyaChat, ANYA_ID, "Привет! Спасибо, что отсканировал(а) мой код на встрече 🙂", now - DAY - 3 * HOUR)
        post(anyaChat, me, "Привет! Рад(а) знакомству", now - DAY - 2 * HOUR)
        post(anyaChat, ANYA_ID, "Скинешь фотки с митапа?", now - DAY - HOUR)
        post(anyaChat, me, "Конечно, вечером пришлю", now - 3 * HOUR)
        markRead(anyaChat, ANYA_ID)
        post(anyaChat, ANYA_ID, "Жду 👀", now - 2 * HOUR)
        devices.value = mapOf(me to mapOf(DEMO_LAPTOP to Device(DEMO_LAPTOP, "MacBook Air", "macos", now - 3 * HOUR)))
        post(botChat, BOT_ID, "Бу! Я Призрак — собеседник для демо-режима. Напиши мне что-нибудь 👻", now - 10 * MINUTE)
    }

    companion object {
        const val ME = "demo-me"
        const val BOT_ID = "ghost-bot"
        const val ANYA_ID = "demo-anya"
        private const val DEMO_LAPTOP = "demo-laptop"
        private const val MINUTE = 60_000L
        private const val HOUR = 60 * MINUTE
        private const val DAY = 24 * HOUR

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
