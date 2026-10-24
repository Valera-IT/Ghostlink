package io.github.valerait.ghostlink.feature.link

import io.github.valerait.ghostlink.core.model.AccountKey
import io.github.valerait.ghostlink.core.model.DeviceId
import io.github.valerait.ghostlink.core.model.LINK_TTL
import io.github.valerait.ghostlink.core.model.LinkCode
import io.github.valerait.ghostlink.core.model.LinkRequestId
import io.github.valerait.ghostlink.core.model.Platform
import io.github.valerait.ghostlink.core.model.ThisDevice
import io.github.valerait.ghostlink.core.testing.FakeBackend
import io.github.valerait.ghostlink.core.testing.FakeLinkCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceLinkingTest {
    // ViewModel запускает корутины на Dispatchers.Main — в тестах это тестовый диспетчер.
    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    private val laptop = ThisDevice(DeviceId("laptop"), "MacBook Air", Platform.MacOs)

    private fun backend() = FakeBackend()

    @Test
    fun `new device signs into the same account once the code is approved`() =
        runTest {
            val backend = backend()
            val owner = backend.createAccount()
            val key = backend.accountKey.value!!
            backend.signOut()

            val receiver = backend.linkReceiveViewModel(laptop)
            val code = LinkCode.parse((receiver.state.value as LinkReceiveUiState.ShowCode).link)!!
            assertEquals("MacBook Air", backend.getLinkRequest(code.requestId)?.deviceName)

            // Так делает устройство с аккаунтом после сканирования.
            backend.approveLinkRequest(code.requestId, FakeLinkCipher.encrypt(code.publicKey, code.requestId, key.bytes))
            runCurrent()

            assertEquals(owner.id, backend.account.value?.id)
            assertEquals(key, backend.accountKey.value)
            assertNull(backend.getLinkRequest(code.requestId), "Request is cleaned up")
        }

    @Test
    fun `code expires if nobody approves it`() =
        runTest {
            val backend = backend()
            val receiver = backend.linkReceiveViewModel(laptop)
            val code = LinkCode.parse((receiver.state.value as LinkReceiveUiState.ShowCode).link)!!

            advanceTimeBy(LINK_TTL + 1.milliseconds)

            assertEquals(LinkReceiveUiState.Expired, receiver.state.value)
            assertNull(backend.getLinkRequest(code.requestId))

            receiver.onAction(LinkReceiveAction.Restart)
            assertTrue(receiver.state.value is LinkReceiveUiState.ShowCode)
        }

    @Test
    fun `payload for another key fails instead of signing in`() =
        runTest {
            val backend = backend()
            val receiver = backend.linkReceiveViewModel(laptop)
            val code = LinkCode.parse((receiver.state.value as LinkReceiveUiState.ShowCode).link)!!
            val stranger = FakeLinkCipher.newKeyPair().publicKey

            backend.approveLinkRequest(code.requestId, FakeLinkCipher.encrypt(stranger, code.requestId, ByteArray(AccountKey.SIZE)))
            runCurrent()

            assertEquals(LinkReceiveUiState.Failed, receiver.state.value)
            assertTrue(backend.account.value?.isAnonymous == true)
        }

    @Test
    fun `approving encrypts this account's key for the scanned device`() =
        runTest {
            val backend = backend()
            backend.createAccount()
            val key = backend.accountKey.value!!
            val newDevice = FakeLinkCipher.newKeyPair()
            val requestId = backend.createLinkRequest("Pixel 9", Platform.Android)
            val code = LinkCode(requestId, newDevice.publicKey)

            val vm = backend.linkApproveViewModel(code.toUri())
            assertEquals(LinkApproveUiState.Confirm("Pixel 9", Platform.Android), vm.state.value)
            vm.onAction(LinkApproveAction.Approve)

            assertEquals(LinkApproveUiState.Done, vm.state.value)
            val payload = assertNotNull(backend.getLinkRequest(requestId)?.payload)
            assertContentEquals(key.bytes, newDevice.decrypt(requestId, payload))
        }

    @Test
    fun `used or unknown codes cannot be approved`() =
        runTest {
            val backend = backend()
            backend.createAccount()
            val publicKey = FakeLinkCipher.newKeyPair().publicKey
            val requestId = backend.createLinkRequest("Pixel 9", Platform.Android)
            backend.approveLinkRequest(requestId, ByteArray(100))

            val used = backend.linkApproveViewModel(LinkCode(requestId, publicKey).toUri())
            val unknown = backend.linkApproveViewModel(LinkCode(LinkRequestId("NoSuchRequest0000000"), publicKey).toUri())

            assertEquals(LinkApproveUiState.Invalid, used.state.value)
            assertEquals(LinkApproveUiState.Invalid, unknown.state.value)
        }
}
