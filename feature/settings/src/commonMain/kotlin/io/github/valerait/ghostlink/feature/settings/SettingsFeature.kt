package io.github.valerait.ghostlink.feature.settings

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

@Serializable data object SettingsRoute

@Serializable data object ThemeRoute

/** Настройки и выбор темы; [onDevices] — открыть экран устройств (другая фича). */
fun NavGraphBuilder.settingsScreens(
    navController: NavController,
    onDevices: () -> Unit,
) {
    composable<SettingsRoute> {
        SettingsScreen(
            onBack = navController::navigateUp,
            onDevices = onDevices,
            onTheme = { navController.navigate(ThemeRoute) },
        )
    }
    composable<ThemeRoute> { ThemeScreen(onBack = navController::navigateUp) }
}

fun NavController.navigateToSettings() = navigate(SettingsRoute)

val settingsModule =
    module {
        viewModelOf(::SettingsViewModel)
        viewModelOf(::ThemeViewModel)
    }
