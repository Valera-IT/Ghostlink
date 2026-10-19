package io.github.valerait.ghostlink.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.valerait.ghostlink.data.model.ScannedCode
import io.github.valerait.ghostlink.ui.chat.ChatScreen
import io.github.valerait.ghostlink.ui.chats.ChatListScreen
import io.github.valerait.ghostlink.ui.connect.ConnectScreen
import io.github.valerait.ghostlink.ui.devices.DevicesScreen
import io.github.valerait.ghostlink.ui.link.LinkApproveScreen
import io.github.valerait.ghostlink.ui.link.LinkReceiveScreen
import io.github.valerait.ghostlink.ui.mycode.MyCodeScreen
import io.github.valerait.ghostlink.ui.onboarding.OnboardingScreen
import io.github.valerait.ghostlink.ui.recovery.RecoveryScreen
import io.github.valerait.ghostlink.ui.scan.ScanScreen
import io.github.valerait.ghostlink.ui.settings.SettingsScreen
import io.github.valerait.ghostlink.ui.settings.ThemeScreen
import kotlinx.serialization.Serializable

@Serializable data object ChatsRoute

@Serializable data class ChatRoute(
    val chatId: String,
)

@Serializable data object MyCodeRoute

@Serializable data object ScanRoute

@Serializable data class ConnectRoute(
    val userId: String,
)

@Serializable data object SettingsRoute

@Serializable data object DevicesRoute

@Serializable data object ThemeRoute

/** code — отсканированная ссылка ghostlink://link/…; разбирает её LinkApproveViewModel. */
@Serializable data class LinkApproveRoute(
    val code: String,
)

@Serializable data object WelcomeRoute

@Serializable data object LinkReceiveRoute

@Serializable data object RecoveryRoute

@Composable
fun GhostlinkRoot(viewModel: RootViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingInvite by viewModel.pendingInvite.collectAsStateWithLifecycle()

    Crossfade(targetState = state, label = "root") { current ->
        when (current) {
            RootState.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            RootState.Onboarding -> OnboardingNavHost()
            is RootState.Ready -> MainNavHost(pendingInvite, viewModel::onInviteHandled)
        }
    }
}

/** До входа: новый аккаунт, вход с другого устройства по QR или по ключу восстановления. */
@Composable
private fun OnboardingNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = WelcomeRoute) {
        composable<WelcomeRoute> {
            OnboardingScreen(
                onLinkDevice = { nav.navigate(LinkReceiveRoute) },
                onRecover = { nav.navigate(RecoveryRoute) },
            )
        }
        composable<LinkReceiveRoute> {
            LinkReceiveScreen(onBack = { nav.navigateUp() })
        }
        composable<RecoveryRoute> {
            RecoveryScreen(onBack = { nav.navigateUp() })
        }
    }
}

@Composable
private fun MainNavHost(
    pendingInvite: String?,
    onInviteHandled: () -> Unit,
) {
    val nav = rememberNavController()

    LaunchedEffect(pendingInvite) {
        if (pendingInvite != null) {
            nav.navigate(ConnectRoute(pendingInvite))
            onInviteHandled()
        }
    }

    NavHost(navController = nav, startDestination = ChatsRoute) {
        composable<ChatsRoute> {
            ChatListScreen(
                onOpenChat = { nav.navigate(ChatRoute(it)) },
                onMyCode = { nav.navigate(MyCodeRoute) },
                onScan = { nav.navigate(ScanRoute) },
                onSettings = { nav.navigate(SettingsRoute) },
            )
        }
        composable<ChatRoute> {
            ChatScreen(onBack = { nav.navigateUp() })
        }
        composable<MyCodeRoute> {
            MyCodeScreen(onBack = { nav.navigateUp() }, onScan = { nav.navigate(ScanRoute) })
        }
        composable<ScanRoute> {
            ScanScreen(
                onBack = { nav.navigateUp() },
                onScanned = { code ->
                    val route: Any =
                        when (code) {
                            is ScannedCode.Invite -> ConnectRoute(code.userId)
                            is ScannedCode.Link -> LinkApproveRoute(code.code.toUri())
                        }
                    nav.navigate(route) { popUpTo<ScanRoute> { inclusive = true } }
                },
            )
        }
        composable<ConnectRoute> {
            ConnectScreen(
                onBack = { nav.navigateUp() },
                onChatReady = { chatId ->
                    nav.navigate(ChatRoute(chatId)) { popUpTo<ChatsRoute>() }
                },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { nav.navigateUp() },
                onDevices = { nav.navigate(DevicesRoute) },
                onTheme = { nav.navigate(ThemeRoute) },
            )
        }
        composable<ThemeRoute> {
            ThemeScreen(onBack = { nav.navigateUp() })
        }
        composable<DevicesRoute> {
            DevicesScreen(onBack = { nav.navigateUp() }, onScan = { nav.navigate(ScanRoute) })
        }
        composable<LinkApproveRoute> {
            LinkApproveScreen(onClose = { nav.navigateUp() })
        }
    }
}
