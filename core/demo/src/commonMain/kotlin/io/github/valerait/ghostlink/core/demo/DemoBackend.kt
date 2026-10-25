package io.github.valerait.ghostlink.core.demo

import io.github.valerait.ghostlink.core.model.Attachment
import io.github.valerait.ghostlink.core.model.Chat
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.Device
import io.github.valerait.ghostlink.core.model.DeviceId
import io.github.valerait.ghostlink.core.model.GroupInfo
import io.github.valerait.ghostlink.core.model.Platform
import io.github.valerait.ghostlink.core.model.ReplyTo
import io.github.valerait.ghostlink.core.model.User
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.core.testing.FakeBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Бэкенд демо-режима: [FakeBackend] со стартовыми чатами и ботом-Призраком, который отвечает на сообщения.
 * Ответы бота приходят с задержкой в [scope], как от живого собеседника.
 */
class DemoBackend(
    private val scope: CoroutineScope,
    clock: Clock = Clock.System,
) : FakeBackend(clock) {
    private var botReplyIndex = 0

    override fun onAccountCreated(id: UserId) = seed(id)

    // В демо любой отсканированный код «существует»: заводим для него собеседника.
    override suspend fun getUser(id: UserId): User? =
        super.getUser(id)
            ?: User(id, "Призрак #${id.value.take(STRANGER_TAG_LENGTH)}", online = false, lastSeen = clock.now() - 1.hours).also(::putUser)

    override fun sendMessage(
        chatId: ChatId,
        senderId: UserId,
        text: String,
        replyTo: ReplyTo?,
        ttl: Duration?,
        attachment: Attachment?,
    ) {
        super.sendMessage(chatId, senderId, text, replyTo, ttl, attachment)
        val chat = chat(chatId) ?: return
        if (BOT_ID in chat.members && senderId != BOT_ID) replyAsBot(chatId, photo = attachment != null)
    }

    private fun replyAsBot(
        chatId: ChatId,
        photo: Boolean,
    ) {
        scope.launch {
            delay(BOT_READ_DELAY)
            markRead(chatId, BOT_ID)
            setTyping(chatId, BOT_ID, true)
            delay(BOT_TYPING_DELAY)
            val ttl = chat(chatId)?.disappearing?.duration
            val reply = if (photo) BOT_PHOTO_REPLY else BOT_REPLIES[botReplyIndex++ % BOT_REPLIES.size]
            post(chatId, BOT_ID, reply, clock.now()) { it.copy(ttl = ttl) }
        }
    }

    private fun seed(me: UserId) {
        val now = clock.now()
        putUser(User(BOT_ID, "Призрак", online = true))
        putUser(User(ANYA_ID, "Аня", online = false, lastSeen = now - 2.hours))
        val botChat = ChatId.of(me, BOT_ID)
        val anyaChat = ChatId.of(me, ANYA_ID)
        putChat(Chat(botChat, listOf(me, BOT_ID).sortedBy { it.value }, updatedAt = now))
        putChat(Chat(anyaChat, listOf(me, ANYA_ID).sortedBy { it.value }, updatedAt = now))
        post(anyaChat, ANYA_ID, "Привет! Спасибо, что отсканировал(а) мой код на встрече 🙂", now - 1.days - 3.hours)
        post(anyaChat, me, "Привет! Рад(а) знакомству", now - 1.days - 2.hours)
        post(anyaChat, ANYA_ID, "Скинешь фотки с митапа?", now - 1.days - 1.hours)
        post(anyaChat, me, "Конечно, вечером пришлю", now - 3.hours)
        markRead(anyaChat, ANYA_ID)
        post(anyaChat, ANYA_ID, "Жду 👀", now - 2.hours)
        putDevice(me, Device(DEMO_LAPTOP, "MacBook Air", Platform.MacOs, now - 3.hours))
        post(botChat, BOT_ID, "Бу! Я Призрак — собеседник для демо-режима. Напиши мне что-нибудь 👻", now - 10.minutes)
        val group = ChatId("g-demo")
        val members = listOf(me, ANYA_ID, BOT_ID).sortedBy { it.value }
        putChat(Chat(group, members, updatedAt = now, group = GroupInfo("Митап 👻", ANYA_ID)))
        post(group, ANYA_ID, "Собрала всех с митапа в одну группу", now - 1.hours)
        post(group, BOT_ID, "Бу! Я тоже тут", now - 30.minutes)
    }

    companion object {
        val BOT_ID = UserId("ghost-bot")
        val ANYA_ID = UserId("demo-anya")
        private val DEMO_LAPTOP = DeviceId("demo-laptop")
        private const val STRANGER_TAG_LENGTH = 4
        private val BOT_READ_DELAY = 700.milliseconds
        private val BOT_TYPING_DELAY = 1_200.milliseconds

        private const val BOT_PHOTO_REPLY = "Красивое фото! В демо-режиме оно живёт только в памяти этого телефона 👻"

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
