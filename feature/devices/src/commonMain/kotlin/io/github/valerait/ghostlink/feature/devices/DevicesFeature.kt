package io.github.valerait.ghostlink.feature.devices

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

@Serializable data object DevicesRoute

/** [onScan] — привязать новое устройство: открыть сканер его кода. */
fun NavGraphBuilder.devicesScreen(
    navController: NavController,
    onScan: () -> Unit,
) {
    composable<DevicesRoute> { DevicesScreen(onBack = navController::navigateUp, onScan = onScan) }
}

fun NavController.navigateToDevices() = navigate(DevicesRoute)

val devicesModule =
    module {
        viewModelOf(::DevicesViewModel)
    }
