package io.github.valerait.ghostlink.feature.recovery

import io.github.valerait.ghostlink.core.domain.usecase.RestoreWithRecoveryCodeUseCase
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
class RecoveryViewModelTest {
    // ViewModel запускает корутины на Dispatchers.Main — в тестах это тестовый диспетчер.
    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun `recovery key signs in and a rotated key stops working`() =
        runTest {
            val backend = FakeBackend()
            val owner = backend.createAccount()
            val oldCode = backend.accountKey.value!!.toRecoveryCode()
            val newCode = backend.rotateKey().toRecoveryCode()
            backend.signOut()

            val vm = RecoveryViewModel(RestoreWithRecoveryCodeUseCase(backend))
            vm.onAction(RecoveryAction.CodeChanged(oldCode))
            vm.onAction(RecoveryAction.Submit)
            assertNull(backend.account.value)
            assertNotNull(vm.state.value.error)

            vm.onAction(RecoveryAction.CodeChanged(newCode.lowercase()))
            vm.onAction(RecoveryAction.Submit)
            assertEquals(owner.id, backend.account.value?.id)
        }
}
