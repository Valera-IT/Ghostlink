package io.github.valerait.ghostlink.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.ui.AppConfig
import io.github.valerait.ghostlink.feature.chat.ChatRoute
import io.github.valerait.ghostlink.push.MessageNotifications
import org.koin.compose.koinInject

/**
 * Уведомления о сообщениях на главном экране: один раз спрашивает разрешение (Android 13+)
 * и убирает уведомление чата, когда его открыли. В демо push нет, и запрос только мешал бы сценариям Maestro.
 */
@Composable
internal fun MessageNotificationsEffect(nav: NavController) {
    val context = LocalContext.current
    val demoMode = koinInject<AppConfig>().demoMode
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (demoMode || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(nav) {
        val notifications = MessageNotifications(context.applicationContext)
        nav.currentBackStackEntryFlow.collect { entry ->
            if (entry.destination.hasRoute<ChatRoute>()) notifications.cancel(ChatId(entry.toRoute<ChatRoute>().chatId))
        }
    }
}
