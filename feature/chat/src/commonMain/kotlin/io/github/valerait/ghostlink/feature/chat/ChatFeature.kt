package io.github.valerait.ghostlink.feature.chat

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.github.valerait.ghostlink.core.model.ChatId
import kotlinx.serialization.Serializable
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

// Аргументы маршрутов попадают в SavedStateHandle под именами полей: их читают ViewModel.

@Serializable data class ChatRoute(
    val chatId: String,
)

/** chatId == null — создать новую группу, иначе добавить участников в эту. */
@Serializable data class PickMembersRoute(
    val chatId: String? = null,
)

@Serializable data class GroupInfoRoute(
    val chatId: String,
)

/**
 * Переписка и экраны группы. Переходы внутри фичи делает сама, наружу — через лямбды:
 * [onGroupCreated] — новая группа готова, [onLeftGroup] — я вышел из группы.
 */
fun NavGraphBuilder.chatScreens(
    navController: NavController,
    onGroupCreated: (ChatId) -> Unit,
    onLeftGroup: () -> Unit,
) {
    composable<ChatRoute> { entry ->
        val chatId = entry.toRoute<ChatRoute>().chatId
        ChatScreen(onBack = navController::navigateUp, onGroupInfo = { navController.navigate(GroupInfoRoute(chatId)) })
    }
    composable<PickMembersRoute> { entry ->
        val adding = entry.toRoute<PickMembersRoute>().chatId != null
        PickMembersScreen(
            onBack = navController::navigateUp,
            onDone = { chatId -> if (adding) navController.navigateUp() else onGroupCreated(chatId) },
        )
    }
    composable<GroupInfoRoute> { entry ->
        val chatId = entry.toRoute<GroupInfoRoute>().chatId
        GroupInfoScreen(
            onBack = navController::navigateUp,
            onAddMembers = { navController.navigate(PickMembersRoute(chatId)) },
            onLeft = onLeftGroup,
        )
    }
}

fun NavController.navigateToChat(
    chatId: ChatId,
    builder: NavOptionsBuilder.() -> Unit = {},
) = navigate(ChatRoute(chatId.value), builder)

fun NavController.navigateToNewGroup() = navigate(PickMembersRoute())

val chatModule =
    module {
        viewModelOf(::ChatViewModel)
        singleOf(::ChatPhotos)
        viewModelOf(::PickMembersViewModel)
        viewModelOf(::GroupInfoViewModel)
    }
