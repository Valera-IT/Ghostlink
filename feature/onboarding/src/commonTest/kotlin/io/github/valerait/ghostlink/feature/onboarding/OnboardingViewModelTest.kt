package io.github.valerait.ghostlink.feature.onboarding

import io.github.valerait.ghostlink.core.domain.usecase.CreateAccountUseCase
import io.github.valerait.ghostlink.core.model.User
import io.github.valerait.ghostlink.core.testing.FakeBackend
import io.github.valerait.ghostlink.core.ui.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    // ViewModel запускает корутины на Dispatchers.Main — в тестах это тестовый диспетчер.
    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    private val config = AppConfig(demoMode = true)

    @Test
    fun `submit creates an account with a key and a profile`() =
        runTest {
            val backend = FakeBackend()
            val vm = backend.onboardingViewModel(config)

            vm.onAction(OnboardingAction.NameChanged("  Валера "))
            vm.onAction(OnboardingAction.Submit)

            val account = backend.account.value!!
            assertEquals(false, account.isAnonymous)
            assertNotNull(backend.accountKey.value)
            assertEquals("Валера", backend.observeUser(account.id).first()?.name)
        }

    @Test
    fun `blank name cannot be submitted`() =
        runTest {
            val backend = FakeBackend()
            val vm = backend.onboardingViewModel(config)

            vm.onAction(OnboardingAction.NameChanged("   "))
            assertFalse(vm.state.value.canSubmit)
            vm.onAction(OnboardingAction.Submit)
            assertNull(backend.account.value)
        }

    @Test
    fun `name is limited in length`() =
        runTest {
            val backend = FakeBackend()
            val vm = backend.onboardingViewModel(config)

            vm.onAction(OnboardingAction.NameChanged("a".repeat(100)))
            assertEquals(User.NAME_MAX_LENGTH, vm.state.value.name.length)
        }

    private fun FakeBackend.onboardingViewModel(config: AppConfig) = OnboardingViewModel(CreateAccountUseCase(this, this), config)
}
