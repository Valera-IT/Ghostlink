package io.github.valerait.ghostlink.ui

import io.github.valerait.ghostlink.MainDispatcherRule
import io.github.valerait.ghostlink.core.model.AccountKey
import io.github.valerait.ghostlink.core.model.DeviceId
import io.github.valerait.ghostlink.core.model.LINK_TTL
import io.github.valerait.ghostlink.core.model.LinkCode
import io.github.valerait.ghostlink.core.model.LinkRequestId
import io.github.valerait.ghostlink.core.model.Platform
import io.github.valerait.ghostlink.core.testing.FakeBackend
import io.github.valerait.ghostlink.core.testing.FakeLinkCipher
import io.github.valerait.ghostlink.data.device.ThisDevice
import io.github.valerait.ghostlink.ui.link.LinkApproveState
import io.github.valerait.ghostlink.ui.link.LinkApproveViewModel
import io.github.valerait.ghostlink.ui.link.LinkReceiveState
import io.github.valerait.ghostlink.ui.link.LinkReceiveViewModel
import io.github.valerait.ghostlink.ui.recovery.RecoveryViewModel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class DeviceLinkingTest {
    @get:Rule val mainRule = MainDispatcherRule()

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
            val code = LinkCode.parse((receiver.state.value as LinkReceiveState.ShowCode).link)!!
            assertEquals("MacBook Air", backend.getLinkRequest(code.requestId)?.deviceName)

            // Так делает устройство с аккаунтом после сканирования.
            backend.approveLinkRequest(code.requestId, FakeLinkCipher.encrypt(code.publicKey, code.requestId, key.bytes))
            runCurrent()

            assertEquals(owner.id, backend.account.value?.id)
            assertEquals(key, backend.accountKey.value)
            assertNull("Request is cleaned up", backend.getLinkRequest(code.requestId))
        }

    @Test
    fun `code expires if nobody approves it`() =
        runTest {
            val backend = backend()
            val receiver = backend.linkReceiveViewModel(laptop)
            val code = LinkCode.parse((receiver.state.value as LinkReceiveState.ShowCode).link)!!

            advanceTimeBy(LINK_TTL + 1.milliseconds)

            assertEquals(LinkReceiveState.Expired, receiver.state.value)
            assertNull(backend.getLinkRequest(code.requestId))

            receiver.restart()
            assertTrue(receiver.state.value is LinkReceiveState.ShowCode)
        }

    @Test
    fun `payload for another key fails instead of signing in`() =
        runTest {
            val backend = backend()
            val receiver = backend.linkReceiveViewModel(laptop)
            val code = LinkCode.parse((receiver.state.value as LinkReceiveState.ShowCode).link)!!
            val stranger = FakeLinkCipher.newKeyPair().publicKey

            backend.approveLinkRequest(code.requestId, FakeLinkCipher.encrypt(stranger, code.requestId, ByteArray(AccountKey.SIZE)))
            runCurrent()

            assertEquals(LinkReceiveState.Failed, receiver.state.value)
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
            assertEquals(LinkApproveState.Confirm("Pixel 9", Platform.Android), vm.state.value)
            vm.approve()

            assertEquals(LinkApproveState.Done, vm.state.value)
            val payload = assertNotNull(backend.getLinkRequest(requestId)?.payload)
            assertArrayEquals(key.bytes, newDevice.decrypt(requestId, payload))
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

            assertEquals(LinkApproveState.Invalid, used.state.value)
            assertEquals(LinkApproveState.Invalid, unknown.state.value)
        }

    @Test
    fun `recovery key signs in and a rotated key stops working`() =
        runTest {
            val backend = backend()
            val owner = backend.createAccount()
            val oldCode = backend.accountKey.value!!.toRecoveryCode()
            val newCode = backend.rotateKey().toRecoveryCode()
            backend.signOut()

            val vm = backend.recoveryViewModel()
            vm.onCodeChange(oldCode)
            vm.submit()
            assertNull(backend.account.value)
            assertNotNull(vm.state.value.error)

            vm.onCodeChange(newCode.lowercase())
            vm.submit()
            assertEquals(owner.id, backend.account.value?.id)
        }

    private fun <T : Any> assertNotNull(value: T?): T {
        assertNotNull(null, value)
        return value!!
    }
}
