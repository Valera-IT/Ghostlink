package io.github.valerait.ghostlink.feature.onboarding

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Первый экран без аккаунта; с него начинается граф до входа. */
@Serializable data object WelcomeRoute

fun NavGraphBuilder.onboardingScreen(
    onLinkDevice: () -> Unit,
    onRecover: () -> Unit,
) {
    composable<WelcomeRoute> { OnboardingScreen(onLinkDevice = onLinkDevice, onRecover = onRecover) }
}

val onboardingModule =
    module {
        viewModelOf(::OnboardingViewModel)
    }
