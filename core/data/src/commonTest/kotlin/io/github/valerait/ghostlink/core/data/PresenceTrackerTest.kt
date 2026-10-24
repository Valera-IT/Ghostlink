package io.github.valerait.ghostlink.core.data

import io.github.valerait.ghostlink.core.testing.FakeBackend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PresenceTrackerTest {
    @Test
    fun `online while on screen and offline in background`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeBackend()
            val me = backend.createAccount().id
            backend.saveProfile(me, "Valera")
            backend.setOnline(me, false)
            val lifecycle = TestAppLifecycle(foreground = false)
            PresenceTracker(backend, backend, lifecycle, backgroundScope).start()
            assertEquals(false, backend.getUser(me)?.online)

            lifecycle.isForeground.value = true
            assertEquals(true, backend.getUser(me)?.online)

            lifecycle.isForeground.value = false
            assertEquals(false, backend.getUser(me)?.online)
        }
}
