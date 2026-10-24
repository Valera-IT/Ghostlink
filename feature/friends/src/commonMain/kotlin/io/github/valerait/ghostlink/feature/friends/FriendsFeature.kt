package io.github.valerait.ghostlink.feature.friends

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.ScannedCode
import io.github.valerait.ghostlink.core.model.UserId
import kotlinx.serialization.Serializable
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

@Serializable data object MyCodeRoute

@Serializable data object ScanRoute

@Serializable data class ConnectRoute(
    val userId: String,
)

/**
 * Мой код, сканер и «добавить в друзья». [onChatReady] — чат с другом готов;
 * [onLinkCode] — отсканирован код входа нового устройства (ghostlink://link/…), сканер его уже закрыл.
 */
fun NavGraphBuilder.friendsScreens(
    navController: NavController,
    onChatReady: (ChatId) -> Unit,
    onLinkCode: (String) -> Unit,
) {
    composable<MyCodeRoute> {
        MyCodeScreen(onBack = navController::navigateUp, onScan = navController::navigateToScan)
    }
    composable<ScanRoute> {
        ScanScreen(
            onBack = navController::navigateUp,
            onScanned = { code ->
                when (code) {
                    is ScannedCode.Invite -> {
                        navController.navigate(ConnectRoute(code.userId.value)) { popUpTo<ScanRoute> { inclusive = true } }
                    }

                    is ScannedCode.Link -> {
                        navController.popBackStack<ScanRoute>(inclusive = true)
                        onLinkCode(code.code.toUri())
                    }
                }
            },
        )
    }
    composable<ConnectRoute> {
        ConnectScreen(onBack = navController::navigateUp, onChatReady = onChatReady)
    }
}

fun NavController.navigateToMyCode() = navigate(MyCodeRoute)

fun NavController.navigateToScan() = navigate(ScanRoute)

/** Приглашение ghostlink://add/… пришло извне (например, присланной ссылкой). */
fun NavController.navigateToConnect(userId: UserId) = navigate(ConnectRoute(userId.value))

val friendsModule =
    module {
        viewModelOf(::MyCodeViewModel)
        viewModelOf(::ConnectViewModel)
    }
