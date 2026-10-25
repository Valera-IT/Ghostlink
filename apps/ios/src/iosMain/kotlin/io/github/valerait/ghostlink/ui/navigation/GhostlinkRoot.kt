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
import androidx.navigation.compose.rememberNavController
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.feature.chat.chatScreens
import io.github.valerait.ghostlink.feature.chat.navigateToChat
import io.github.valerait.ghostlink.feature.chat.navigateToNewGroup
import io.github.valerait.ghostlink.feature.chats.ChatsRoute
import io.github.valerait.ghostlink.feature.chats.chatsScreen
import io.github.valerait.ghostlink.feature.devices.devicesScreen
import io.github.valerait.ghostlink.feature.devices.navigateToDevices
import io.github.valerait.ghostlink.feature.friends.friendsScreens
import io.github.valerait.ghostlink.feature.friends.navigateToConnect
import io.github.valerait.ghostlink.feature.friends.navigateToMyCode
import io.github.valerait.ghostlink.feature.friends.navigateToScan
import io.github.valerait.ghostlink.feature.link.linkApproveScreen
import io.github.valerait.ghostlink.feature.link.linkReceiveScreen
import io.github.valerait.ghostlink.feature.link.navigateToLinkApprove
import io.github.valerait.ghostlink.feature.link.navigateToLinkReceive
import io.github.valerait.ghostlink.feature.onboarding.WelcomeRoute
import io.github.valerait.ghostlink.feature.onboarding.onboardingScreen
import io.github.valerait.ghostlink.feature.recovery.navigateToRecovery
import io.github.valerait.ghostlink.feature.recovery.recoveryScreen
import io.github.valerait.ghostlink.feature.settings.navigateToSettings
import io.github.valerait.ghostlink.feature.settings.settingsScreens

// Тот же граф, что в apps/android. В общий модуль вынесем вместе с третьим приложением (десктоп, этап 5).
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
        onboardingScreen(onLinkDevice = nav::navigateToLinkReceive, onRecover = nav::navigateToRecovery)
        linkReceiveScreen(nav)
        recoveryScreen(nav)
    }
}

/** После входа. Фичи не знают друг о друге: переходы между ними связываются здесь. */
@Composable
private fun MainNavHost(
    pendingInvite: UserId?,
    onInviteHandled: () -> Unit,
) {
    val nav = rememberNavController()

    LaunchedEffect(pendingInvite) {
        if (pendingInvite != null) {
            nav.navigateToConnect(pendingInvite)
            onInviteHandled()
        }
    }

    // Новый чат открываем поверх списка: «назад» из него ведёт к чатам, а не к экрану, который его создал.
    val openChatFromList = { chatId: ChatId -> nav.navigateToChat(chatId) { popUpTo<ChatsRoute>() } }

    NavHost(navController = nav, startDestination = ChatsRoute) {
        chatsScreen(
            onOpenChat = { nav.navigateToChat(it) },
            onMyCode = nav::navigateToMyCode,
            onNewGroup = nav::navigateToNewGroup,
            onScan = nav::navigateToScan,
            onSettings = nav::navigateToSettings,
        )
        chatScreens(
            navController = nav,
            onGroupCreated = openChatFromList,
            onLeftGroup = { nav.popBackStack<ChatsRoute>(inclusive = false) },
        )
        friendsScreens(navController = nav, onChatReady = openChatFromList, onLinkCode = nav::navigateToLinkApprove)
        settingsScreens(navController = nav, onDevices = nav::navigateToDevices)
        devicesScreen(navController = nav, onScan = nav::navigateToScan)
        linkApproveScreen(nav)
    }
}
