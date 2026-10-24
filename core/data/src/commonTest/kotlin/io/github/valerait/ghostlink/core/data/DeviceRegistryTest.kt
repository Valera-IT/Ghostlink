package io.github.valerait.ghostlink.core.data

import io.github.valerait.ghostlink.core.model.DeviceId
import io.github.valerait.ghostlink.core.model.Platform
import io.github.valerait.ghostlink.core.model.ThisDevice
import io.github.valerait.ghostlink.core.testing.FakeBackend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceRegistryTest {
    private val backend = FakeBackend()
    private val phone = ThisDevice(DeviceId("phone"), "Pixel", Platform.Android)
    private val lifecycle = TestAppLifecycle(foreground = false)

    private suspend fun TestScope.signedIn() =
        backend.createAccount().id.also {
            backend.saveProfile(it, "Valera")
            DeviceRegistry(backend, backend, backend, phone, lifecycle, backgroundScope).start()
        }

    @Test
    fun `device is registered when the app comes to screen`() =
        runTest(UnconfinedTestDispatcher()) {
            val me = signedIn()
            assertNull(backend.observeDevice(me, phone.id).first())

            lifecycle.isForeground.value = true
            assertEquals("Pixel", backend.observeDevice(me, phone.id).first()?.name)
        }

    @Test
    fun `removed device signs out`() =
        runTest(UnconfinedTestDispatcher()) {
            val me = signedIn()
            lifecycle.isForeground.value = true

            backend.removeDevice(me, phone.id)
            assertNull(backend.account.value)
        }
}
