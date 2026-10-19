package io.github.valerait.ghostlink.ui

import io.github.valerait.ghostlink.MainDispatcherRule
import io.github.valerait.ghostlink.data.demo.InMemoryBackend
import io.github.valerait.ghostlink.di.AppConfig
import io.github.valerait.ghostlink.ui.onboarding.MAX_NAME_LENGTH
import io.github.valerait.ghostlink.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val config = AppConfig(demoMode = true, webClientId = null)

    @Test
    fun `submit creates an account with a key and a profile`() =
        runTest {
            val backend = InMemoryBackend(backgroundScope, seedDemoData = false)
            val vm = OnboardingViewModel(backend, backend, config)

            vm.onNameChange("  Валера ")
            vm.submit()

            val account = backend.account.value!!
            assertEquals(false, account.isAnonymous)
            assertNotNull(backend.accountKey.value)
            assertEquals("Валера", backend.observeUser(account.id).first()?.name)
        }

    @Test
    fun `blank name cannot be submitted`() =
        runTest {
            val backend = InMemoryBackend(backgroundScope, seedDemoData = false)
            val vm = OnboardingViewModel(backend, backend, config)

            vm.onNameChange("   ")
            assertFalse(vm.state.value.canSubmit)
            vm.submit()
            assertNull(backend.account.value)
        }

    @Test
    fun `name is limited in length`() =
        runTest {
            val backend = InMemoryBackend(backgroundScope, seedDemoData = false)
            val vm = OnboardingViewModel(backend, backend, config)

            vm.onNameChange("a".repeat(100))
            assertEquals(MAX_NAME_LENGTH, vm.state.value.name.length)
        }
}
