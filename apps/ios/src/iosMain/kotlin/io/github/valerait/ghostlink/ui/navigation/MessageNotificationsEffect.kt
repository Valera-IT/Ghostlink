package io.github.valerait.ghostlink.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import io.github.valerait.ghostlink.core.ui.AppConfig
import io.github.valerait.ghostlink.feature.chat.ChatRoute
import org.koin.compose.koinInject
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * Уведомления фонового обновления на главном экране: один раз спрашивает разрешение
 * (дальше iOS сама не показывает запрос) и убирает уведомление чата, когда его открыли.
 * В демо фоновой проверки нет, и запрос только мешал бы сценариям Maestro.
 */
@Composable
internal fun MessageNotificationsEffect(nav: NavController) {
    val demoMode = koinInject<AppConfig>().demoMode
    LaunchedEffect(Unit) {
        if (demoMode) return@LaunchedEffect
        UNUserNotificationCenter
            .currentNotificationCenter()
            .requestAuthorizationWithOptions(
                UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
            ) { _, _ -> }
    }
    LaunchedEffect(nav) {
        nav.currentBackStackEntryFlow.collect { entry ->
            if (entry.destination.hasRoute<ChatRoute>()) {
                val chatId = entry.toRoute<ChatRoute>().chatId
                UNUserNotificationCenter.currentNotificationCenter().removeDeliveredNotificationsWithIdentifiers(listOf(chatId))
            }
        }
    }
}
