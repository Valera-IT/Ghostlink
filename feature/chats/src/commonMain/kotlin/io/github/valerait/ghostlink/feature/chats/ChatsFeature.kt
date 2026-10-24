package io.github.valerait.ghostlink.feature.chats

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.valerait.ghostlink.core.model.ChatId
import kotlinx.serialization.Serializable
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Список чатов — стартовый экран после входа. */
@Serializable data object ChatsRoute

@Suppress("LongParameterList") // по лямбде на каждый переход из списка
fun NavGraphBuilder.chatsScreen(
    onOpenChat: (ChatId) -> Unit,
    onMyCode: () -> Unit,
    onNewGroup: () -> Unit,
    onScan: () -> Unit,
    onSettings: () -> Unit,
) {
    composable<ChatsRoute> {
        ChatListScreen(
            onOpenChat = onOpenChat,
            onMyCode = onMyCode,
            onNewGroup = onNewGroup,
            onScan = onScan,
            onSettings = onSettings,
        )
    }
}

val chatsModule =
    module {
        viewModelOf(::ChatListViewModel)
    }
