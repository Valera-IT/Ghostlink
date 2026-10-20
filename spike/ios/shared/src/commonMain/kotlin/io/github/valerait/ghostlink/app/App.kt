package io.github.valerait.ghostlink.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.valerait.ghostlink.model.InviteLink
import io.github.valerait.ghostlink.model.LinkCode
import io.github.valerait.ghostlink.model.ScannedCode
import io.github.valerait.ghostlink.ui.screens.ChatListScreen
import io.github.valerait.ghostlink.ui.screens.ChatScreen
import io.github.valerait.ghostlink.ui.screens.ConnectScreen
import io.github.valerait.ghostlink.ui.screens.DevicesScreen
import io.github.valerait.ghostlink.ui.screens.GroupInfoScreen
import io.github.valerait.ghostlink.ui.screens.PickMembersScreen
import io.github.valerait.ghostlink.ui.screens.LinkApproveScreen
import io.github.valerait.ghostlink.ui.screens.LinkReceiveScreen
import io.github.valerait.ghostlink.ui.screens.MyCodeScreen
import io.github.valerait.ghostlink.ui.screens.OnboardingScreen
import io.github.valerait.ghostlink.ui.screens.RecoveryScreen
import io.github.valerait.ghostlink.ui.screens.ScanScreen
import io.github.valerait.ghostlink.ui.screens.SettingsScreen
import io.github.valerait.ghostlink.ui.screens.ThemeScreen
import io.github.valerait.ghostlink.ui.theme.GhostlinkTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface RootState {
    data object Loading : RootState
    data object Onboarding : RootState
    data class Ready(val userId: String) : RootState
}

@OptIn(ExperimentalCoroutinesApi::class)
private val rootState by lazy {
    AppGraph.auth.account
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(RootState.Onboarding) else AppGraph.users.observeUser(id).map { if (it == null) RootState.Onboarding else RootState.Ready(id) }
        }
        .distinctUntilChanged()
        .stateIn(AppGraph.scope, SharingStarted.Eagerly, RootState.Loading)
}

/** Приглашение ghostlink://add/<id>, открытое из другого приложения; ждёт, пока пользователь войдёт. */
internal val pendingInvite = MutableStateFlow<String?>(null)

fun handleUrl(url: String) {
    InviteLink.parse(url)?.let { pendingInvite.value = it }
}

// Простая навигация стеком: navigation-compose для разведки не нужен.
private sealed interface Screen {
    data object Welcome : Screen
    data object LinkReceive : Screen
    data object Recovery : Screen
    data object Chats : Screen
    data class Chat(val chatId: String) : Screen
    /** Новая группа ([chatId] == null) или добавление участников в группу. */
    data class PickMembers(val chatId: String?) : Screen
    data class GroupInfo(val chatId: String) : Screen
    data object MyCode : Screen
    data object Scan : Screen
    data class Connect(val userId: String) : Screen
    data object Settings : Screen
    data object Theme : Screen
    data object Devices : Screen
    data class LinkApprove(val code: LinkCode) : Screen
}

/** Элемент стека со своими ViewModel: они очищаются, когда экран уходит из стека. */
private class Entry(val screen: Screen) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

private class BackStack(start: Screen) {
    val entries = mutableStateListOf(Entry(start))
    val top: Entry get() = entries.last()

    fun push(screen: Screen) {
        entries.add(Entry(screen))
    }

    fun pop() {
        if (entries.size > 1) entries.removeAt(entries.lastIndex).viewModelStore.clear()
    }

    /** Заменяет всё выше [keep] (включая верхний экран) на [screen]. */
    fun replaceAbove(keep: Int, screen: Screen) {
        while (entries.size > keep) entries.removeAt(entries.lastIndex).viewModelStore.clear()
        push(screen)
    }

    fun clear() = entries.forEach { it.viewModelStore.clear() }
}

@Composable
fun App() {
    val theme by ThemeStore.theme.collectAsStateWithLifecycle()
    val state by rootState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { AppGraph.start() }
    LifecycleStartEffect(Unit) {
        AppGraph.onForeground()
        onStopOrDispose { AppGraph.onBackground() }
    }

    GhostlinkTheme(theme = theme) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Crossfade(targetState = state, label = "root") { current ->
                when (current) {
                    RootState.Loading -> Unit
                    RootState.Onboarding -> Navigator(Screen.Welcome)
                    is RootState.Ready -> Navigator(Screen.Chats)
                }
            }
        }
    }
}

@Composable
private fun Navigator(start: Screen) {
    val stack = remember(start) { BackStack(start) }
    DisposableEffect(stack) { onDispose { stack.clear() } }

    val invite by pendingInvite.collectAsStateWithLifecycle()
    LaunchedEffect(invite, start) {
        val id = invite ?: return@LaunchedEffect
        if (start == Screen.Chats) {
            stack.push(Screen.Connect(id))
            pendingInvite.value = null
        }
    }

    AnimatedContent(
        targetState = stack.top,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "nav",
    ) { entry ->
        CompositionLocalProvider(LocalViewModelStoreOwner provides entry) {
            Content(entry.screen, stack)
        }
    }
}

@Composable
private fun Content(screen: Screen, stack: BackStack) {
    val back = { stack.pop() }
    when (screen) {
        Screen.Welcome -> OnboardingScreen(onLinkDevice = { stack.push(Screen.LinkReceive) }, onRecover = { stack.push(Screen.Recovery) })
        Screen.LinkReceive -> LinkReceiveScreen(onBack = back)
        Screen.Recovery -> RecoveryScreen(onBack = back)
        Screen.Chats -> ChatListScreen(
            onOpenChat = { stack.push(Screen.Chat(it)) },
            onMyCode = { stack.push(Screen.MyCode) },
            onScan = { stack.push(Screen.Scan) },
            onSettings = { stack.push(Screen.Settings) },
            onNewGroup = { stack.push(Screen.PickMembers(null)) },
        )
        is Screen.Chat -> ChatScreen(screen.chatId, onBack = back, onGroupInfo = { stack.push(Screen.GroupInfo(screen.chatId)) })
        is Screen.PickMembers -> PickMembersScreen(screen.chatId, onBack = back, onDone = { chatId ->
            // Новая группа открывается вместо экрана выбора; после добавления возвращаемся в карточку.
            if (screen.chatId == null) stack.replaceAbove(1, Screen.Chat(chatId)) else stack.pop()
        })
        is Screen.GroupInfo -> GroupInfoScreen(
            screen.chatId,
            onBack = back,
            onAddMembers = { stack.push(Screen.PickMembers(screen.chatId)) },
            onLeft = { stack.replaceAbove(0, Screen.Chats) },
        )
        Screen.MyCode -> MyCodeScreen(onBack = back, onScan = { stack.push(Screen.Scan) })
        Screen.Scan -> ScanScreen(onBack = back, onScanned = { code ->
            val next = when (code) {
                is ScannedCode.Invite -> Screen.Connect(code.userId)
                is ScannedCode.Link -> Screen.LinkApprove(code.code)
            }
            stack.replaceAbove(stack.entries.size - 1, next)
        })
        is Screen.Connect -> ConnectScreen(screen.userId, onBack = back, onChatReady = { chatId -> stack.replaceAbove(1, Screen.Chat(chatId)) })
        Screen.Settings -> SettingsScreen(onBack = back, onDevices = { stack.push(Screen.Devices) }, onTheme = { stack.push(Screen.Theme) })
        Screen.Theme -> ThemeScreen(onBack = back)
        Screen.Devices -> DevicesScreen(onBack = back, onScan = { stack.push(Screen.Scan) })
        is Screen.LinkApprove -> LinkApproveScreen(screen.code, onClose = back)
    }
}
