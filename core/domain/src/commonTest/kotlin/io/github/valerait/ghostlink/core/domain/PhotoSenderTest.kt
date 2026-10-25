package io.github.valerait.ghostlink.core.domain

import io.github.valerait.ghostlink.core.domain.usecase.PhotoDraft
import io.github.valerait.ghostlink.core.domain.usecase.PhotoSender
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.DisappearingTimer
import io.github.valerait.ghostlink.core.model.GhostlinkError
import io.github.valerait.ghostlink.core.model.ReplyTo
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.core.testing.FakeBackend
import io.github.valerait.ghostlink.core.testing.FakeMediaStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoSenderTest {
    private val friend = UserId("friend")
    private val draft = PhotoDraft(photo = byteArrayOf(1, 2, 3), thumbnail = byteArrayOf(4), width = 1280, height = 960)

    private class Setup(
        val backend: FakeBackend,
        val media: FakeMediaStore,
        val sender: PhotoSender,
        val me: UserId,
        val chat: ChatId,
    )

    private suspend fun TestScope.setUp(): Setup {
        val backend = FakeBackend()
        val me = backend.createAccount().id
        val chat = backend.startChat(me, friend)
        val media = FakeMediaStore()
        return Setup(backend, media, PhotoSender(media, backend, backgroundScope), me, chat)
    }

    @Test
    fun photoIsShownWhileUploadingThenBecomesAMessage() =
        runTest {
            val s = setUp()
            s.media.gate = CompletableDeferred()

            s.sender.send(s.chat, s.me, draft, caption = "  Закат  ", timer = DisappearingTimer.DAY)
            runCurrent()
            val outgoing =
                s.sender
                    .outgoing(s.chat)
                    .first()
                    .single()
            assertEquals("Закат", outgoing.caption)
            assertTrue(
                s.backend
                    .observeMessages(s.chat)
                    .first()
                    .isEmpty(),
            )

            s.media.gate!!.complete(Unit)
            runCurrent()
            assertTrue(
                s.sender
                    .outgoing(s.chat)
                    .first()
                    .isEmpty(),
            )
            val message =
                s.backend
                    .observeMessages(s.chat)
                    .first()
                    .single()
            assertEquals("Закат", message.text)
            assertEquals(DisappearingTimer.DAY.duration, message.ttl)
            val attachment = message.attachment!!
            assertEquals(1280 to 960, attachment.width to attachment.height)
            assertEquals(3, attachment.size)
            assertContentEquals(draft.photo, s.media.download(s.chat, attachment.key))
            assertContentEquals(draft.thumbnail, s.media.download(s.chat, attachment.thumbKey))
            assertTrue(
                s.backend
                    .observeChat(s.chat)
                    .first()!!
                    .lastMessage!!
                    .photo,
            )
        }

    @Test
    fun failedUploadCanBeRetriedOrDiscarded() =
        runTest {
            val s = setUp()
            s.media.failure = GhostlinkError.Offline()

            s.sender.send(s.chat, s.me, draft, caption = "")
            s.sender.send(s.chat, s.me, draft, caption = "второе")
            runCurrent()
            val (first, second) = s.sender.outgoing(s.chat).first()
            assertTrue(first.failed && second.failed)

            s.media.failure = null
            s.sender.retry(first.id)
            s.sender.discard(second.id)
            runCurrent()
            assertTrue(
                s.sender
                    .outgoing(s.chat)
                    .first()
                    .isEmpty(),
            )
            val message =
                s.backend
                    .observeMessages(s.chat)
                    .first()
                    .single()
            assertEquals("", message.text)
            assertEquals(1, s.media.uploads)
        }

    @Test
    fun photoCanAnswerAMessageAndBeAnsweredItself() =
        runTest {
            val s = setUp()
            s.backend.sendMessage(s.chat, friend, "Покажи митап")
            val question =
                s.backend
                    .observeMessages(s.chat)
                    .first()
                    .single()

            s.sender.send(s.chat, s.me, draft, caption = "", replyTo = question)
            runCurrent()
            val photo =
                s.backend
                    .observeMessages(s.chat)
                    .first()
                    .last()
            assertEquals(ReplyTo.of(question), photo.replyTo)
            // Ответ на фото без подписи: пустое превью с отметкой «фото».
            assertEquals(ReplyTo(photo.id, s.me, "", photo = true), ReplyTo.of(photo))
        }

    @Test
    fun photosOfOtherChatsAreNotShown() =
        runTest {
            val s = setUp()
            s.media.gate = CompletableDeferred()
            s.sender.send(s.chat, s.me, draft, caption = "")
            runCurrent()
            assertTrue(
                s.sender
                    .outgoing(ChatId("other"))
                    .first()
                    .isEmpty(),
            )
        }
}
