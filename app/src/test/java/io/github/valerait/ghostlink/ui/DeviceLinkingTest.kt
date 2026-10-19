package io.github.valerait.ghostlink.ui

import androidx.lifecycle.SavedStateHandle
import io.github.valerait.ghostlink.MainDispatcherRule
import io.github.valerait.ghostlink.data.demo.InMemoryBackend
import io.github.valerait.ghostlink.data.device.ThisDevice
import io.github.valerait.ghostlink.data.link.LinkCrypto
import io.github.valerait.ghostlink.data.model.AccountKey
import io.github.valerait.ghostlink.data.model.LINK_TTL_MS
import io.github.valerait.ghostlink.data.model.LinkCode
import io.github.valerait.ghostlink.ui.link.LinkApproveState
import io.github.valerait.ghostlink.ui.link.LinkApproveViewModel
import io.github.valerait.ghostlink.ui.link.LinkReceiveState
import io.github.valerait.ghostlink.ui.link.LinkReceiveViewModel
import io.github.valerait.ghostlink.ui.recovery.RecoveryViewModel
import kotlinx.coroutines.test.TestScope
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

class DeviceLinkingTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val laptop = ThisDevice("laptop", "MacBook Air", "macos")

    private fun TestScope.backend() = InMemoryBackend(backgroundScope, seedDemoData = false)

    @Test
    fun `new device signs into the same account once the code is approved`() =
        runTest {
            val backend = backend()
            val owner = backend.createAccount()
            val key = backend.accountKey.value!!
            backend.signOut()

            val receiver = LinkReceiveViewModel(backend, backend, laptop)
            val code = LinkCode.parse((receiver.state.value as LinkReceiveState.ShowCode).link)!!
            assertEquals("MacBook Air", backend.getLinkRequest(code.requestId)?.deviceName)

            // Так делает устройство с аккаунтом после сканирования.
            backend.approveLinkRequest(code.requestId, LinkCrypto.encrypt(code.publicKey, code.requestId, key.bytes))
            runCurrent()

            assertEquals(owner.id, backend.account.value?.id)
            assertEquals(key, backend.accountKey.value)
            assertNull("Request is cleaned up", backend.getLinkRequest(code.requestId))
        }

    @Test
    fun `code expires if nobody approves it`() =
        runTest {
            val backend = backend()
            val receiver = LinkReceiveViewModel(backend, backend, laptop)
            val code = LinkCode.parse((receiver.state.value as LinkReceiveState.ShowCode).link)!!

            advanceTimeBy(LINK_TTL_MS + 1)

            assertEquals(LinkReceiveState.Expired, receiver.state.value)
            assertNull(backend.getLinkRequest(code.requestId))

            receiver.restart()
            assertTrue(receiver.state.value is LinkReceiveState.ShowCode)
        }

    @Test
    fun `payload for another key fails instead of signing in`() =
        runTest {
            val backend = backend()
            val receiver = LinkReceiveViewModel(backend, backend, laptop)
            val code = LinkCode.parse((receiver.state.value as LinkReceiveState.ShowCode).link)!!
            val stranger = LinkCrypto.encodePublicKey(LinkCrypto.generateKeyPair().public)

            backend.approveLinkRequest(code.requestId, LinkCrypto.encrypt(stranger, code.requestId, ByteArray(AccountKey.SIZE)))
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
            val newDevice = LinkCrypto.generateKeyPair()
            val requestId = backend.createLinkRequest("Pixel 9", "android")
            val code = LinkCode(requestId, LinkCrypto.encodePublicKey(newDevice.public))

            val vm = LinkApproveViewModel(SavedStateHandle(mapOf("code" to code.toUri())), backend, backend)
            assertEquals(LinkApproveState.Confirm("Pixel 9", "android"), vm.state.value)
            vm.approve()

            assertEquals(LinkApproveState.Done, vm.state.value)
            val payload = assertNotNull(backend.getLinkRequest(requestId)?.payload)
            assertArrayEquals(key.bytes, LinkCrypto.decrypt(newDevice.private, requestId, payload))
        }

    @Test
    fun `used or unknown codes cannot be approved`() =
        runTest {
            val backend = backend()
            backend.createAccount()
            val publicKey = LinkCrypto.encodePublicKey(LinkCrypto.generateKeyPair().public)
            val requestId = backend.createLinkRequest("Pixel 9", "android")
            backend.approveLinkRequest(requestId, ByteArray(100))

            val used = LinkApproveViewModel(SavedStateHandle(mapOf("code" to LinkCode(requestId, publicKey).toUri())), backend, backend)
            val unknown =
                LinkApproveViewModel(
                    SavedStateHandle(mapOf("code" to LinkCode("NoSuchRequest0000000", publicKey).toUri())),
                    backend,
                    backend,
                )

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

            val vm = RecoveryViewModel(backend)
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
