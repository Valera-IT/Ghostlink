package io.github.valerait.ghostlink.feature.chat

import io.github.valerait.ghostlink.core.domain.usecase.PhotoDraft
import io.github.valerait.ghostlink.core.domain.usecase.PhotoSender
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.GhostlinkError
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.core.testing.FakeBackend
import io.github.valerait.ghostlink.core.testing.FakeMediaStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatPhotoTest {
    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    private val friend = UserId("friend")
    private val draft = PhotoDraft(photo = byteArrayOf(1, 2), thumbnail = byteArrayOf(3), width = 800, height = 600)

    private class Setup(
        val backend: FakeBackend,
        val media: FakeMediaStore,
        val chat: ChatId,
        val vm: ChatViewModel,
    )

    private suspend fun TestScope.setUp(): Setup {
        val backend = FakeBackend()
        val me = backend.createAccount().id
        val chat = backend.startChat(me, friend)
        val media = FakeMediaStore()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val vm = backend.chatViewModel(chat, photos = PhotoSender(media, backend, CoroutineScope(dispatcher)))
        backgroundScope.launch(dispatcher) { vm.state.collect {} }
        return Setup(backend, media, chat, vm)
    }

    @Test
    fun attachedPhotoGoesWithTheTextAsCaption() =
        runTest {
            val s = setUp()
            s.vm.onAction(ChatAction.AttachPhoto(draft))
            assertEquals(draft, s.vm.state.value.attachedPhoto)

            s.vm.onAction(ChatAction.Send("Вот он"))

            assertNull(s.vm.state.value.attachedPhoto)
            val message =
                s.vm.state.value.rows
                    .filterIsInstance<ChatRow.Msg>()
                    .single()
                    .item.message
            assertEquals("Вот он", message.text)
            assertEquals(800, message.attachment?.width)
        }

    @Test
    fun photoWithoutCaptionIsSentAndEmptyTextAloneIsNot() =
        runTest {
            val s = setUp()
            s.vm.onAction(ChatAction.Send("   "))
            assertTrue(
                s.vm.state.value.rows
                    .isEmpty(),
            )

            s.vm.onAction(ChatAction.AttachPhoto(draft))
            s.vm.onAction(ChatAction.Send(""))
            val message =
                s.vm.state.value.rows
                    .filterIsInstance<ChatRow.Msg>()
                    .single()
                    .item.message
            assertEquals("", message.text)
        }

    @Test
    fun uploadingPhotoIsTheNewestRowUntilItIsSent() =
        runTest {
            val s = setUp()
            s.backend.sendMessage(s.chat, friend, "Привет")
            s.media.gate = CompletableDeferred()

            s.vm.onAction(ChatAction.AttachPhoto(draft))
            s.vm.onAction(ChatAction.Send(""))
            val newest =
                s.vm.state.value.rows
                    .first()
            assertIs<ChatRow.Outgoing>(newest)
            assertEquals(false, newest.photo.failed)

            s.media.gate!!.complete(Unit)
            assertTrue(
                s.vm.state.value.rows
                    .none { it is ChatRow.Outgoing },
            )
            assertEquals(
                2,
                s.vm.state.value.rows
                    .count { it is ChatRow.Msg },
            )
        }

    @Test
    fun failedPhotoIsRetriedOrDiscarded() =
        runTest {
            val s = setUp()
            s.media.failure = GhostlinkError.Offline()
            s.vm.onAction(ChatAction.AttachPhoto(draft))
            s.vm.onAction(ChatAction.Send(""))
            val failed =
                s.vm.state.value.rows
                    .filterIsInstance<ChatRow.Outgoing>()
                    .single()
                    .photo
            assertTrue(failed.failed)

            s.media.failure = null
            s.vm.onAction(ChatAction.RetryPhoto(failed.id))
            assertTrue(
                s.vm.state.value.rows
                    .none { it is ChatRow.Outgoing },
            )
            assertEquals(
                1,
                s.vm.state.value.rows
                    .count { it is ChatRow.Msg },
            )

            s.media.failure = GhostlinkError.Offline()
            s.vm.onAction(ChatAction.AttachPhoto(draft))
            s.vm.onAction(ChatAction.Send(""))
            val again =
                s.vm.state.value.rows
                    .filterIsInstance<ChatRow.Outgoing>()
                    .single()
                    .photo
            s.vm.onAction(ChatAction.DiscardPhoto(again.id))
            assertTrue(
                s.vm.state.value.rows
                    .none { it is ChatRow.Outgoing },
            )
        }

    @Test
    fun editingDropsTheAttachedPhoto() =
        runTest {
            val s = setUp()
            s.vm.onAction(ChatAction.Send("Опечтка"))
            val mine =
                s.vm.state.value.rows
                    .filterIsInstance<ChatRow.Msg>()
                    .single()
                    .item.message
            s.vm.onAction(ChatAction.AttachPhoto(draft))
            s.vm.onAction(ChatAction.RemoveAttachedPhoto)
            assertNull(s.vm.state.value.attachedPhoto)

            s.vm.onAction(ChatAction.AttachPhoto(draft))
            s.vm.onAction(ChatAction.StartEdit(mine.id))
            assertNull(s.vm.state.value.attachedPhoto)
        }
}
