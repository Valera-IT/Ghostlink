package io.github.valerait.ghostlink.core.data.emulator

import io.github.valerait.ghostlink.core.domain.usecase.LeaveGroupUseCase
import io.github.valerait.ghostlink.core.domain.usecase.LinkState
import io.github.valerait.ghostlink.core.model.AccountKey
import io.github.valerait.ghostlink.core.model.Device
import io.github.valerait.ghostlink.core.model.DeviceId
import io.github.valerait.ghostlink.core.model.DisappearingTimer
import io.github.valerait.ghostlink.core.model.GhostlinkError
import io.github.valerait.ghostlink.core.model.MessageStatus
import io.github.valerait.ghostlink.core.model.Platform
import io.github.valerait.ghostlink.core.model.ReplyTo
import io.github.valerait.ghostlink.core.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/**
 * Репозитории core:data против Firebase Emulator с боевыми firestore.rules: каждая запись проходит правила,
 * каждое чтение приходит слушателем. Запуск — `./gradlew :core:data:jvmEmulatorTest` внутри `firebase emulators:exec`.
 */
class RepositoriesEmulatorTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun client() = EmulatorClient(scope)

    @AfterTest
    fun tearDown() = scope.cancel()

    /** Вход на новом устройстве целиком через use case: заявка, подтверждение, payload в поле bytes, вход по ключу. */
    @Suppress("LongMethod") // сценарий целиком: каждый шаг опирается на предыдущий
    @Test
    fun newDeviceLinksToAccountAndChatsInRealTime() =
        emulatorTest {
            val phone = client()
            val friend = client()
            val laptop = client()
            val phoneId =
                phone.auth
                    .createAccount()
                    .id
                    .also { phone.users.saveProfile(it, "Phone") }
            val friendId =
                friend.auth
                    .createAccount()
                    .id
                    .also { friend.users.saveProfile(it, "Friend") }

            val states = MutableStateFlow<LinkState?>(null)
            val linking = scope.launch { laptop.requestLink("MacBook", Platform.MacOs).collect { states.value = it } }
            val code = withTimeout(TIMEOUT) { states.filterIsInstance<LinkState.ShowCode>().first().code }

            assertEquals("MacBook", phone.approveLink.load(code).deviceName)
            phone.approveLink(code)
            withTimeout(TIMEOUT) { linking.join() }
            assertEquals(LinkState.SigningIn, states.value)
            // Второй раз ту же заявку не подтвердить: в ней уже лежит payload или её уже удалили.
            assertFailsWith<GhostlinkError> { phone.approveLink.load(code) }
            assertEquals(
                phoneId,
                laptop.auth.account.value
                    ?.id,
            )
            assertEquals(phone.auth.accountKey.value, laptop.auth.accountKey.value)
            assertNull(phone.links.getLinkRequest(code.requestId), "заявка удаляется после входа")

            // Реестр устройств: телефон видит ноутбук слушателем.
            laptop.devices.registerDevice(phoneId, Device(DeviceId("laptop"), "MacBook", Platform.MacOs))
            val registered =
                withTimeout(TIMEOUT) {
                    phone.devices
                        .observeDevices(phoneId)
                        .map { list ->
                            list.find { it.id == DeviceId("laptop") }
                        }.filterNotNull()
                        .first()
                }
            assertEquals(Platform.MacOs, registered.platform)

            // Push-токен: регистрация без токена не стирает записанный раньше.
            laptop.devices.registerDevice(phoneId, Device(DeviceId("laptop"), "MacBook", Platform.MacOs, pushToken = "fcm-token"))
            laptop.devices.registerDevice(phoneId, Device(DeviceId("laptop"), "MacBook", Platform.MacOs))
            assertEquals(
                "fcm-token",
                laptop.devices
                    .observeDevice(phoneId, DeviceId("laptop"))
                    .filterNotNull()
                    .first()
                    .pushToken,
            )

            // Чат: друг пишет — ноутбук получает, и обратно; сообщения ждём уже с серверным временем.
            val chatId = friend.chats.startChat(friendId, phoneId)
            friend.chats.sendMessage(chatId, friendId, "Привет, ноутбук")
            val received =
                withTimeout(TIMEOUT) {
                    laptop.chats
                        .observeMessages(chatId)
                        .map { list ->
                            list.find { it.text == "Привет, ноутбук" && it.sentAt != null }
                        }.filterNotNull()
                        .first()
                }
            assertEquals(friendId, received.senderId)

            laptop.chats.sendMessage(chatId, phoneId, "Привет с JVM")
            val sent =
                withTimeout(TIMEOUT) {
                    friend.chats
                        .observeMessages(chatId)
                        .map { list ->
                            list.find { it.text == "Привет с JVM" && it.sentAt != null }
                        }.filterNotNull()
                        .first()
                }
            friend.chats.markRead(chatId, friendId)
            withTimeout(TIMEOUT) {
                phone.chats.observeChat(chatId).first { chat ->
                    chat != null &&
                        sent.status(chat.othersReadUpTo(phoneId)) == MessageStatus.READ
                }
            }
        }

    @Test
    fun errorsArriveAsGhostlinkErrors() =
        emulatorTest {
            val phone = client()
            phone.auth.createAccount()

            // Подтвердить заявку может только настоящий аккаунт: анонимный правила не пускают.
            val stranger = client()
            val laptop = client()
            val states = MutableStateFlow<LinkState?>(null)
            val linking = scope.launch { laptop.requestLink("MacBook", Platform.MacOs).collect { states.value = it } }
            val code = withTimeout(TIMEOUT) { states.filterIsInstance<LinkState.ShowCode>().first().code }
            stranger.auth.signInAnonymously()
            assertFailsWith<GhostlinkError.PermissionDenied> { stranger.links.approveLinkRequest(code.requestId, ByteArray(1)) }
            linking.cancel()

            // Чужой ключ не подходит. Смену ключа (rotateKey) здесь не проверить: в firebase-java-sdk
            // не реализован EmailAuthProvider.credential, см. ADR 0006.
            assertFailsWith<GhostlinkError.InvalidAccountKey> { client().auth.signInWithKey(AccountKey.generate()) }
            assertEquals(
                phone.auth.account.value
                    ?.id,
                client().auth.signInWithKey(phone.auth.accountKey.value!!).id,
            )
        }

    /** Группа, «печатает…», таймер и исчезающие сообщения. */
    @Suppress("LongMethod") // сценарий целиком: каждый шаг опирается на предыдущий
    @Test
    fun groupWithTypingAndDisappearingMessages() =
        emulatorTest {
            val admin = client()
            val anna = client()
            val boris = client()
            val adminId =
                admin.auth
                    .createAccount()
                    .id
                    .also { admin.users.saveProfile(it, "Admin") }
            val annaId =
                anna.auth
                    .createAccount()
                    .id
                    .also { anna.users.saveProfile(it, "Anna") }
            val borisId =
                boris.auth
                    .createAccount()
                    .id
                    .also { boris.users.saveProfile(it, "Boris") }

            val groupId = admin.chats.createGroup(adminId, "Друзья", listOf(annaId))
            withTimeout(TIMEOUT) { anna.chats.observeChat(groupId).first { it?.group?.name == "Друзья" } }

            admin.chats.renameGroup(groupId, "Друзья 2")
            admin.chats.addMembers(groupId, listOf(borisId))
            // Читать группу может только участник: Борис подписывается, когда сервер уже записал его в состав.
            withTimeout(TIMEOUT) { anna.chats.observeChat(groupId).first { it?.group?.name == "Друзья 2" && borisId in it.members } }
            withTimeout(TIMEOUT) { boris.chats.observeChat(groupId).first { it?.group?.name == "Друзья 2" } }

            anna.chats.setTyping(groupId, annaId, true)
            withTimeout(TIMEOUT) { admin.chats.observeChat(groupId).first { it?.typingOther(adminId)?.first == annaId } }

            anna.chats.setDisappearing(groupId, DisappearingTimer.entries.first())
            withTimeout(TIMEOUT) { admin.chats.observeChat(groupId).first { it?.disappearing == DisappearingTimer.entries.first() } }

            // Отправка снимает «печатает…» и несёт ttl в сообщении и превью.
            val ttl = DisappearingTimer.entries.first().duration
            anna.chats.sendMessage(groupId, annaId, "Исчезну", ttl = ttl)
            val message =
                withTimeout(TIMEOUT) {
                    boris.chats
                        .observeMessages(groupId)
                        .map { list ->
                            list.find { it.text == "Исчезну" && it.sentAt != null }
                        }.filterNotNull()
                        .first()
                }
            assertEquals(ttl, message.ttl)
            withTimeout(TIMEOUT) { admin.chats.observeChat(groupId).first { it?.typing?.isEmpty() == true && it.lastMessage?.ttl == ttl } }

            // Ответ с цитатой; replyTo и ttl пишутся, только когда заданы.
            boris.chats.sendMessage(groupId, borisId, "Ответ", ReplyTo.of(message))
            withTimeout(TIMEOUT) {
                admin.chats.observeMessages(groupId).first { list ->
                    list.any {
                        it.replyTo?.id == message.id &&
                            it.sentAt != null
                    }
                }
            }

            // Все прочитали — у автора три галочки.
            admin.chats.markRead(groupId, adminId)
            boris.chats.markRead(groupId, borisId)
            withTimeout(TIMEOUT) {
                anna.chats.observeChat(groupId).first { chat ->
                    chat != null && message.status(chat.othersReadUpTo(annaId), chat.allReadUpTo(annaId)) == MessageStatus.READ_ALL
                }
            }

            // Правка и удаление своего сообщения: превью в списке чатов следует за ними.
            val answer =
                boris.chats
                    .observeMessages(groupId)
                    .first()
                    .last()
            boris.chats.editMessage(groupId, answer.id, "Ответ!", isLatest = true)
            withTimeout(TIMEOUT) { anna.chats.observeChat(groupId).first { it?.lastMessage?.text == "Ответ!" } }
            boris.chats.deleteMessage(groupId, answer.id, isLatest = true, previous = message)
            withTimeout(TIMEOUT) { anna.chats.observeChat(groupId).first { it?.lastMessage?.text == "Исчезну" } }
            withTimeout(TIMEOUT) { anna.chats.observeMessages(groupId).first { list -> list.any { it.id == answer.id && it.deleted } } }
            anna.chats.deleteExpired(groupId, listOf(message.id))
            withTimeout(TIMEOUT) { boris.chats.observeMessages(groupId).first { list -> list.none { it.id == message.id } } }

            // Админ выходит — права переходят следующему участнику.
            LeaveGroupUseCase(admin.chats)(
                admin.chats
                    .observeChat(groupId)
                    .filterNotNull()
                    .first(),
                adminId,
            )
            withTimeout(TIMEOUT) {
                anna.chats.observeChat(groupId).first {
                    it != null && adminId !in it.members &&
                        it.group?.ownerId != adminId
                }
            }
        }

    /** Меню чата: закрепить, выключить уведомления, удалить и вернуть чат новым сообщением. */
    @Test
    fun pinMuteAndRemoveChat() =
        emulatorTest {
            val me = client()
            val friend = client()
            val meId =
                me.auth
                    .createAccount()
                    .id
                    .also { me.users.saveProfile(it, "Me") }
            val friendId =
                friend.auth
                    .createAccount()
                    .id
                    .also { friend.users.saveProfile(it, "Pal") }
            val chatId = me.chats.startChat(meId, friendId)

            me.chats.setPinned(meId, chatId, true)
            me.chats.setMuted(meId, chatId, true)
            withTimeout(TIMEOUT) { me.chats.observeChatPrefs(meId).first { it[chatId]?.let { p -> p.pinned && p.muted } == true } }
            assertEquals(
                mapOf(friendId to "Pal"),
                me.users
                    .observeUsers(listOf(friendId))
                    .first()
                    .mapValues { it.value.name },
            )

            friend.chats.sendMessage(chatId, friendId, "Старое")
            withTimeout(TIMEOUT) { me.chats.observeChat(chatId).first { it?.lastMessage?.sentAt != null } }

            me.chats.removeChat(meId, chatId)
            val removed =
                withTimeout(TIMEOUT) {
                    me.chats.observeChatPrefs(meId).map { it[chatId] }.first {
                        it != null && it.hidden &&
                            it.clearedAt != null
                    }
                }!!
            assertFalse(removed.pinned)
            assertFalse(
                removed.isVisible(
                    me.chats
                        .observeChat(chatId)
                        .filterNotNull()
                        .first(),
                ),
            )

            // Собеседник пишет снова — чат возвращается, старое сообщение остаётся скрытым.
            friend.chats.sendMessage(chatId, friendId, "Новое")
            withTimeout(TIMEOUT) {
                me.chats.observeChat(chatId).filterNotNull().first {
                    it.lastMessage?.let { m -> m.text == "Новое" && m.sentAt != null } ==
                        true &&
                        removed.isVisible(it)
                }
            }

            // Повторное добавление друга снимает hidden.
            me.chats.startChat(meId, friendId)
            withTimeout(TIMEOUT) { me.chats.observeChatPrefs(meId).first { it[chatId]?.hidden == false } }
        }

    /** Фото профиля: bytes в avatars/{uid} проходит правила, друг видит новую версию и скачивает именно её. */
    @Test
    fun profilePhotoReachesFriendAndCanBeRemoved() =
        emulatorTest {
            val me = client()
            val friend = client()
            val meId =
                me.auth
                    .createAccount()
                    .id
                    .also { me.users.saveProfile(it, "Me") }
            friend.auth.createAccount().also { friend.users.saveProfile(it.id, "Friend") }
            val jpeg = ByteArray(User.AVATAR_MAX_BYTES) { it.toByte() }

            me.users.setAvatar(meId, jpeg)
            val version =
                withTimeout(TIMEOUT) {
                    friend.users
                        .observeUser(meId)
                        .mapNotNull { it?.avatarVersion }
                        .first()
                }
            assertContentEquals(jpeg, friend.users.getAvatar(meId, version))
            assertNull(friend.users.getAvatar(meId, version - 1), "устаревшая версия не отдаётся")
            // Правила не пускают больше 64 КБ — клиент отказывает ещё до записи.
            assertFailsWith<IllegalArgumentException> { me.users.setAvatar(meId, ByteArray(User.AVATAR_MAX_BYTES + 1)) }

            me.users.removeAvatar(meId)
            withTimeout(TIMEOUT) { friend.users.observeUser(meId).first { it?.avatarVersion == null } }
            // У друга JPEG той версии остаётся в кэше, но без версии в профиле его больше не спросят; на сервере фото нет.
            val stranger = client().also { it.auth.createAccount() }
            assertNull(stranger.users.getAvatar(meId, version))
        }

    /**
     * Сценарий целиком под общим таймаутом: если запись или вход так и не дождутся сервера,
     * тест упадёт с местом зависания, а не повесит всю задачу до таймаута Gradle.
     */
    private fun emulatorTest(block: suspend CoroutineScope.() -> Unit) = runBlocking<Unit> { withTimeout(SCENARIO_TIMEOUT, block) }

    private companion object {
        val TIMEOUT = 15.seconds
        val SCENARIO_TIMEOUT = 60.seconds
    }
}
