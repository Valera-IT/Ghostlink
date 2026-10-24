package io.github.valerait.ghostlink.feature.recovery

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

@Serializable data object RecoveryRoute

fun NavGraphBuilder.recoveryScreen(navController: NavController) {
    composable<RecoveryRoute> { RecoveryScreen(onBack = navController::navigateUp) }
}

fun NavController.navigateToRecovery() = navigate(RecoveryRoute)

val recoveryModule =
    module {
        viewModelOf(::RecoveryViewModel)
    }
