package io.github.valerait.ghostlink.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.valerait.ghostlink.app.AppGraph
import io.github.valerait.ghostlink.app.strings
import io.github.valerait.ghostlink.data.requireUserId
import io.github.valerait.ghostlink.model.Chat
import io.github.valerait.ghostlink.model.ChatPrefs
import io.github.valerait.ghostlink.data.leaveGroup
import io.github.valerait.ghostlink.model.DisappearingTimer
import io.github.valerait.ghostlink.model.GroupInfo
import io.github.valerait.ghostlink.model.LastMessage
import io.github.valerait.ghostlink.model.expiresAt
import io.github.valerait.ghostlink.platform.nowMillis
import io.github.valerait.ghostlink.model.Message
import io.github.valerait.ghostlink.model.MessageStatus
import io.github.valerait.ghostlink.model.ReplyTo
import io.github.valerait.ghostlink.model.User
import io.github.valerait.ghostlink.platform.formatTime
import io.github.valerait.ghostlink.platform.localEpochDay
import io.github.valerait.ghostlink.ui.common.Avatar
import io.github.valerait.ghostlink.ui.common.BackButton
import io.github.valerait.ghostlink.ui.common.GhostLogo
import io.github.valerait.ghostlink.ui.common.dayLabel
import io.github.valerait.ghostlink.ui.common.formatChatListTime
import io.github.valerait.ghostlink.ui.common.presenceText
import io.github.valerait.ghostlink.ui.common.rememberNow
import io.github.valerait.ghostlink.ui.theme.LocalChatStyle
import io.github.valerait.ghostlink.ui.theme.LogoTextStyle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

// ---------- Список чатов ----------

data class ChatListItem(
    val chat: Chat,
    /** Собеседник в личном чате; в группе — id чата (для цвета аватара). */
    val partnerId: String,
    /** Собеседник в личном чате; в группе null. */
    val partner: User?,
    val unread: Boolean,
    /** Последнее сообщение; null, если его нет или оно уже исчезло. */
    val lastMessage: LastMessage?,
    val lastMessageIsMine: Boolean,
    /** До какого момента кто-то из собеседников «печатает…»; null — никто. */
    val typingUntil: Long? = null,
    /** Кто печатает в группе; в личном чате null. */
    val typingName: String? = null,
    /** Автор последнего сообщения в группе, если это не я; в личном чате null. */
    val lastSenderName: String? = null,
    val pinned: Boolean = false,
    val muted: Boolean = false,
) {
    /** Имя собеседника или название группы. */
    val title: String? get() = chat.group?.name ?: partner?.name
}

data class ChatListState(val loading: Boolean = true, val items: List<ChatListItem> = emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModel : ViewModel() {
    private val me = AppGraph.auth.requireUserId()
    private val chatList = AppGraph.chats.observeChats(me).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    // Подписка на людей пересоздаётся только при смене состава чатов.
    private val people = chatList
        .map { list -> list.flatMap { it.members }.filter { it != me }.distinct().sorted() }
        .distinctUntilChanged()
        .flatMapLatest { AppGraph.users.observeUsers(it) }

    // Превью исчезающего сообщения пропадает в его срок, даже если в базе ничего не поменялось.
    private val prefs = AppGraph.chats.observeChatPrefs(me)

    val state: StateFlow<ChatListState> = combine(chatList, people, prefs, ::Triple)
        .transformLatest { (list, people, prefs) ->
            while (true) {
                val now = nowMillis()
                val items = list
                    .mapNotNull { chat ->
                        val chatPrefs = prefs[chat.id] ?: ChatPrefs()
                        if (chatPrefs.isVisible(chat)) item(chat, chatPrefs, people, now) else null
                    }
                    // Закреплённые сверху (последний закреплённый первым), остальные — по свежести, как пришли.
                    .sortedByDescending { prefs[it.chat.id]?.pinnedAt ?: Long.MIN_VALUE }
                emit(ChatListState(loading = false, items = items))
                val next = list.mapNotNull { chat -> chat.lastMessage?.let { expiresAt(it.sentAt, it.ttl) } }.filter { it > now }.minOrNull() ?: break
                delay(next - now)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatListState())

    fun setPinned(chatId: String, pinned: Boolean) = AppGraph.chats.setPinned(me, chatId, pinned)

    fun setMuted(chatId: String, muted: Boolean) = AppGraph.chats.setMuted(me, chatId, muted)

    /** Личный чат прячется только у меня; из группы я выхожу (админ передаёт права следующему участнику). */
    fun remove(chatId: String) {
        val chat = state.value.items.firstOrNull { it.chat.id == chatId }?.chat ?: return
        if (chat.isGroup) AppGraph.chats.leaveGroup(chat, me)
        AppGraph.chats.removeChat(me, chatId)
    }

    private fun item(chat: Chat, prefs: ChatPrefs, people: Map<String, User>, now: Long): ChatListItem {
        val last = chat.lastMessage?.takeUnless { prefs.isCleared(it.sentAt) || it.isExpired(now) }
        val typing = chat.typingOther(me)
        return ChatListItem(
            chat = chat,
            partnerId = if (chat.isGroup) chat.id else chat.partnerId(me),
            partner = if (chat.isGroup) null else people[chat.partnerId(me)],
            unread = last != null && chat.isUnreadFor(me),
            lastMessage = last,
            lastMessageIsMine = last?.senderId == me,
            typingUntil = typing?.second,
            typingName = if (chat.isGroup) typing?.let { people[it.first]?.name } else null,
            lastSenderName = last?.takeIf { chat.isGroup && it.senderId != me }?.let { people[it.senderId]?.name },
            pinned = prefs.pinned,
            muted = prefs.muted,
        )
    }
}

/** Пока не наступил [until], возвращает true и сама сбрасывается в срок — для «печатает…». */
@Composable
fun rememberIsTyping(until: Long?): Boolean {
    var typing by remember { mutableStateOf(false) }
    LaunchedEffect(until) {
        val left = (until ?: 0L) - nowMillis()
        typing = left > 0
        if (left > 0) {
            delay(left)
            typing = false
        }
    }
    return typing
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onOpenChat: (String) -> Unit,
    onMyCode: () -> Unit,
    onScan: () -> Unit,
    onSettings: () -> Unit,
    onNewGroup: () -> Unit,
    viewModel: ChatListViewModel = viewModel { ChatListViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now by rememberNow()
    var removingId by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GhostLogo(size = 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("GHOSTLINK", style = LogoTextStyle.copy(fontSize = 18.sp, letterSpacing = 3.sp))
                    }
                },
                actions = {
                    if (state.items.any { !it.chat.isGroup }) {
                        IconButton(onClick = onNewGroup) { Icon(Icons.Outlined.GroupAdd, contentDescription = strings.newGroup) }
                    }
                    IconButton(onClick = onScan) { Icon(Icons.Filled.QrCodeScanner, contentDescription = strings.scanCode) }
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = strings.settings) }
                },
            )
        },
        floatingActionButton = {
            if (state.items.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onMyCode,
                    icon = { Icon(Icons.Filled.QrCode2, contentDescription = null) },
                    text = { Text(strings.myCode) },
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> Unit
            state.items.isEmpty() -> EmptyChats(onMyCode, onScan, Modifier.padding(padding))
            else -> LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
                items(state.items, key = { it.chat.id }) { item ->
                    ChatRowItem(
                        item,
                        now,
                        onClick = { onOpenChat(item.chat.id) },
                        onPin = { viewModel.setPinned(item.chat.id, !item.pinned) },
                        onMute = { viewModel.setMuted(item.chat.id, !item.muted) },
                        onRemove = { removingId = item.chat.id },
                    )
                }
            }
        }
    }

    removingId?.let { id ->
        val item = state.items.firstOrNull { it.chat.id == id }
        val isGroup = item?.chat?.isGroup == true
        val name = item?.title.orEmpty()
        val action = if (isGroup) strings.leaveGroup else strings.delete
        AlertDialog(
            onDismissRequest = { removingId = null },
            title = { Text(if (isGroup) strings.leaveGroupTitle else strings.removeContactTitle) },
            text = {
                Text(
                    if (isGroup) {
                        strings.leaveGroupBody(name)
                    } else {
                        strings.removeContactBody(name)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    removingId = null
                    viewModel.remove(id)
                }) { Text(action, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removingId = null }) { Text(strings.cancel) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRowItem(item: ChatListItem, now: Long, onClick: () -> Unit, onPin: () -> Unit, onMute: () -> Unit, onRemove: () -> Unit) {
    val name = item.title ?: "…"
    val typing = rememberIsTyping(item.typingUntil)
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    Box {
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClickLabel = strings.chatActions,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menu = true
                },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Avatar(name, item.partnerId, size = 52.dp)
            if (item.partner?.isOnline(now) == true) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(14.dp)
                        .background(MaterialTheme.colorScheme.background, CircleShape).padding(2.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Имя занимает всё место до времени; отдельный Spacer с весом отнимал у него половину строки.
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (item.muted) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Outlined.NotificationsOff, contentDescription = strings.muted, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                item.lastMessage?.sentAt?.let {
                    Text(
                        formatChatListTime(it, now),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (item.unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val last = item.lastMessage
                val preview = when {
                    typing && item.typingName != null -> strings.typingNamed(item.typingName)
                    typing -> strings.typing
                    last == null -> strings.noMessages
                    item.lastMessageIsMine -> strings.youPrefix(last.text)
                    item.lastSenderName != null -> "${item.lastSenderName}: ${last.text}"
                    else -> last.text
                }
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (typing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (item.unread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (item.unread) {
                    Spacer(Modifier.width(8.dp))
                    val dot = if (item.muted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                    Box(Modifier.size(10.dp).background(dot, CircleShape))
                } else if (item.pinned) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.PushPin, contentDescription = strings.pinned, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        DropdownMenuItem(
            text = { Text(if (item.pinned) strings.unpin else strings.pin) },
            leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
            onClick = { menu = false; onPin() },
        )
        DropdownMenuItem(
            text = { Text(if (item.muted) strings.unmute else strings.mute) },
            leadingIcon = { Icon(if (item.muted) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff, contentDescription = null) },
            onClick = { menu = false; onMute() },
        )
        DropdownMenuItem(
            text = { Text(if (item.chat.isGroup) strings.leaveGroup else strings.delete, color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            onClick = { menu = false; onRemove() },
        )
    }
    }
}

@Composable
private fun EmptyChats(onMyCode: () -> Unit, onScan: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        GhostLogo(size = 88.dp, tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(20.dp))
        Text(strings.emptyTitle, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            strings.emptyBody,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onMyCode, modifier = Modifier.fillMaxWidth()) { Text(strings.myCode) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text(strings.scanCode) }
    }
}

// ---------- Переписка ----------

const val MAX_MESSAGE_LENGTH = 4000

data class MessageItem(
    val message: Message,
    val mine: Boolean,
    val status: MessageStatus?,
    val replyToMine: Boolean = false,
    /** Исходное сообщение цитаты удалено — вместо превью показываем «Сообщение удалено». */
    val replyToDeleted: Boolean = false,
    /** Имя автора над чужим сообщением в группе; в личном чате null. */
    val senderName: String? = null,
)

sealed interface ChatRow {
    val key: String

    data class Day(val millis: Long) : ChatRow {
        override val key = "day-$millis"
    }

    data class Msg(val item: MessageItem) : ChatRow {
        override val key = item.message.id
    }
}

data class ChatUiState(
    val loading: Boolean = true,
    val chatId: String = "",
    /** Собеседник в личном чате; в группе null. */
    val partner: User? = null,
    /** Группа: название и админ; в личном чате null. */
    val group: GroupInfo? = null,
    val memberCount: Int = 0,
    /** userId -> имя для всех, кого видно в чате, кроме меня. */
    val names: Map<String, String> = emptyMap(),
    /** До какого момента кто-то из собеседников «печатает…»; null — никто. */
    val typingUntil: Long? = null,
    /** Кто печатает в группе; в личном чате null. */
    val typingName: String? = null,
    /** Таймер исчезающих сообщений чата; null — выключен. */
    val disappearing: DisappearingTimer? = null,
    val rows: List<ChatRow> = emptyList(),
    val replyingTo: MessageItem? = null,
    /** Своё сообщение, которое сейчас исправляют; взаимоисключается с [replyingTo]. */
    val editing: MessageItem? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(private val chatId: String) : ViewModel() {
    private val me = AppGraph.auth.requireUserId()
    private val chats = AppGraph.chats
    private val visible = MutableStateFlow(false)
    private val replyingToId = MutableStateFlow<String?>(null)
    private val editingId = MutableStateFlow<String?>(null)

    private val typing = TypingNotifier(viewModelScope) { chats.setTyping(chatId, me, it) }

    private val chat = chats.observeChat(chatId).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    // Исчезнувшие сообщения, которые этот клиент уже попросил стереть из базы.
    private val expiredRequested = HashSet<String>()

    // Исчезающие пропадают ровно в свой срок, даже если из базы ничего нового не пришло.
    // Сообщения до удаления чата из списка (ChatPrefs.clearedAt) у этого пользователя больше не показываем.
    private val messages = combine(chats.observeMessages(chatId), chats.observeChatPrefs(me).map { it[chatId] }.distinctUntilChanged()) { messages, prefs ->
        if (prefs?.clearedAt == null) messages else messages.filterNot { prefs.isCleared(it.sentAt) }
    }
        .transformLatest { list ->
            while (true) {
                val now = nowMillis()
                val (expired, alive) = list.partition { it.isExpired(now) }
                // Стирать из базы может любой участник; локально сообщение уже скрыто.
                val erase = expired.filter { !it.pending && expiredRequested.add(it.id) }.map { it.id }
                if (erase.isNotEmpty()) chats.deleteExpired(chatId, erase)
                emit(alive)
                val next = alive.mapNotNull { it.expiresAt }.minOrNull() ?: break
                delay(next - now)
            }
        }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    // Собеседник в личном чате или все участники группы (и бывшие — их имена нужны у старых сообщений).
    private val people = combine(chat.mapNotNull { it?.members }, messages) { members, messages ->
        (members + messages.map { it.senderId }).filter { it != me }.distinct().sorted()
    }
        .distinctUntilChanged()
        .flatMapLatest { AppGraph.users.observeUsers(it) }
        .onStart { emit(emptyMap()) }

    val state: StateFlow<ChatUiState> = combine(chat, messages, people, replyingToId, editingId) { chat, messages, people, replyingToId, editingId ->
        val readUpTo = chat?.othersReadUpTo(me)
        val allReadUpTo = chat?.allReadUpTo(me)
        val isGroup = chat?.isGroup == true
        val deletedIds = messages.filter { it.deleted }.mapTo(HashSet()) { it.id }
        val items = messages.map { m ->
            val mine = m.senderId == me
            MessageItem(
                m,
                mine,
                if (mine) m.status(readUpTo, allReadUpTo) else null,
                replyToMine = m.replyTo?.senderId == me,
                replyToDeleted = m.replyTo?.id in deletedIds,
                senderName = if (isGroup && !mine) people[m.senderId]?.name else null,
            )
        }
        val typingOther = chat?.typingOther(me)
        ChatUiState(
            loading = false,
            chatId = chatId,
            partner = if (isGroup) null else chat?.let { people[it.partnerId(me)] },
            group = chat?.group,
            memberCount = chat?.members?.size ?: 0,
            names = people.mapValues { it.value.name },
            typingUntil = typingOther?.second,
            typingName = if (isGroup) typingOther?.let { people[it.first]?.name } else null,
            disappearing = chat?.disappearing,
            rows = buildRows(items),
            replyingTo = replyingToId?.let { id -> items.firstOrNull { it.message.id == id && !it.message.deleted } },
            editing = editingId?.let { id -> items.firstOrNull { it.message.id == id && it.mine && !it.message.deleted } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        // Отмечаем прочитанным, только пока чат реально на экране и пришло что-то новое.
        viewModelScope.launch {
            combine(visible, chat, messages) { visible, chat, messages ->
                if (!visible || chat == null) return@combine null
                val lastIncoming = messages.lastOrNull { it.senderId != me && !it.pending }?.sentAt ?: return@combine null
                lastIncoming.takeIf { it > (chat.lastRead[me] ?: 0L) }
            }.filterNotNull().distinctUntilChanged().collect { chats.markRead(chatId, me) }
        }
    }

    fun onVisibilityChanged(isVisible: Boolean) {
        visible.value = isVisible
        if (!isVisible) typing.stop()
    }

    /** Текст в поле ввода изменился. Правка своего сообщения — тоже набор. */
    fun onDraftChanged(text: String) = typing.onDraftChanged(text)

    /** Таймер общий для чата: собеседники увидят его, и их сообщения тоже начнут исчезать. */
    fun setDisappearing(timer: DisappearingTimer?) = chats.setDisappearing(chatId, timer)

    fun startReply(messageId: String) {
        editingId.value = null
        replyingToId.value = messageId
    }

    fun cancelReply() {
        replyingToId.value = null
    }

    fun startEdit(messageId: String) {
        replyingToId.value = null
        editingId.value = messageId
    }

    fun cancelEdit() {
        editingId.value = null
    }

    fun delete(messageId: String) {
        val list = messages.replayCache.firstOrNull() ?: return
        val index = list.indexOfFirst { it.id == messageId }
        if (index < 0 || list[index].senderId != me || list[index].deleted) return
        // Превью в списке чатов показывает последнее неудалённое сообщение.
        chats.deleteMessage(
            chatId,
            messageId,
            isLatest = list.isLatestVisible(index),
            previous = list.subList(0, index).lastOrNull { !it.deleted },
        )
        if (replyingToId.value == messageId) replyingToId.value = null
        if (editingId.value == messageId) editingId.value = null
    }

    /** Отправляет новое сообщение или, в режиме правки, сохраняет исправленный текст. */
    fun send(text: String) {
        val trimmed = text.trim().take(MAX_MESSAGE_LENGTH)
        if (trimmed.isEmpty()) return
        val editing = state.value.editing
        if (editing != null) {
            editingId.value = null
            typing.stop()
            if (trimmed == editing.message.text) return
            val list = messages.replayCache.firstOrNull().orEmpty()
            val isLatest = list.isLatestVisible(list.indexOfFirst { it.id == editing.message.id })
            chats.editMessage(chatId, editing.message.id, trimmed, isLatest)
        } else {
            // Отвечать можно только на сообщение, уже записанное на сервер: у pending ещё нет стабильного места в чате.
            val replyTo = state.value.replyingTo?.message?.takeUnless { it.pending }?.let(ReplyTo::of)
            typing.onSent()
            chats.sendMessage(chatId, me, trimmed, replyTo, chat.replayCache.firstOrNull()?.disappearing?.seconds)
            replyingToId.value = null
        }
    }

    /** После сообщения с номером [index] остались только удалённые — значит, оно сейчас в превью чата. */
    private fun List<Message>.isLatestVisible(index: Int): Boolean = index >= 0 && subList(index + 1, size).all { it.deleted }

    private fun buildRows(items: List<MessageItem>): List<ChatRow> {
        val rows = ArrayList<ChatRow>(items.size + 8)
        var lastDay: Long? = null
        for (item in items) {
            val sentAt = item.message.sentAt
            if (sentAt != null) {
                val day = localEpochDay(sentAt)
                if (day != lastDay) {
                    rows += ChatRow.Day(sentAt)
                    lastDay = day
                }
            }
            rows += ChatRow.Msg(item)
        }
        return rows.asReversed()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatId: String, onBack: () -> Unit, onGroupInfo: () -> Unit, viewModel: ChatViewModel = viewModel(key = chatId) { ChatViewModel(chatId) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now by rememberNow()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var highlightedId by remember { mutableStateOf<String?>(null) }
    var confirmDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val partnerName = state.partner?.name.orEmpty()
    val authorName: (String, Boolean) -> String = { senderId, mine -> if (mine) strings.you else state.names[senderId] ?: partnerName }

    // Нажатие на цитату — прокрутка к исходному сообщению и короткая подсветка.
    val showOriginal: (String) -> Unit = { id ->
        val index = state.rows.indexOfFirst { it.key == id }
        scope.launch {
            if (index < 0) {
                snackbar.showSnackbar(strings.replyTooOld)
            } else {
                listState.animateScrollToItem(index)
                highlightedId = id
                delay(1_500)
                highlightedId = null
            }
        }
    }

    // Протяжка ленты пальцем вниз прячет клавиатуру, как в Telegram.
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val hideKeyboardOnDrag = remember(keyboard, focusManager) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    keyboard?.hide()
                    focusManager.clearFocus()
                }
                return Offset.Zero
            }
        }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.onVisibilityChanged(true)
        onPauseOrDispose { viewModel.onVisibilityChanged(false) }
    }

    val newestKey = state.rows.firstOrNull()?.key
    LaunchedEffect(newestKey) {
        if (listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = { ChatTitle(state, now, onGroupInfo) },
                actions = { DisappearingMenu(state.disappearing, viewModel::setDisappearing) },
            )
        },
        bottomBar = {
            MessageInput(
                replyingTo = state.replyingTo,
                editing = state.editing,
                replyAuthor = { authorName(it.message.senderId, it.mine) },
                onCancelReply = viewModel::cancelReply,
                onCancelEdit = viewModel::cancelEdit,
                onSend = viewModel::send,
                onDraftChanged = viewModel::onDraftChanged,
            )
        },
    ) { padding ->
        if (!state.loading && state.rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(strings.sayHi, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxSize().padding(padding).nestedScroll(hideKeyboardOnDrag),
        ) {
            items(state.rows, key = { it.key }) { row ->
                when (row) {
                    is ChatRow.Day -> DayHeader(dayLabel(row.millis, now))
                    is ChatRow.Msg -> {
                        val id = row.item.message.id
                        MessageBubble(
                            item = row.item,
                            highlighted = id == highlightedId,
                            quoteAuthor = row.item.message.replyTo?.let { authorName(it.senderId, row.item.replyToMine) }.orEmpty(),
                            onQuoteClick = showOriginal,
                            actions = MessageActions(
                                onReply = { viewModel.startReply(id) },
                                onEdit = { viewModel.startEdit(id) },
                                onDelete = { confirmDeleteId = id },
                            ),
                        )
                    }
                }
            }
        }
    }

    confirmDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text(strings.deleteMessageTitle) },
            text = { Text(strings.deleteMessageBody) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteId = null
                    viewModel.delete(id)
                }) { Text(strings.delete, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteId = null }) { Text(strings.cancel) } },
        )
    }
}

private fun TextStyle.mono(): TextStyle = copy(fontFamily = FontFamily.Monospace)

@Composable
private fun DayHeader(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = LocalChatStyle.current.chipShape) {
            Text(
                text.uppercase(),
                style = MaterialTheme.typography.labelMedium.mono(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

/** Действия из меню сообщения (долгое нажатие), как в Telegram. */
data class MessageActions(val onReply: () -> Unit, val onEdit: () -> Unit, val onDelete: () -> Unit)

/** Пока сообщение не дошло до сервера, его можно только скопировать. Править и удалять можно только свои. */
@Composable
private fun MessageMenu(expanded: Boolean, mine: Boolean, sent: Boolean, onDismiss: () -> Unit, onCopy: () -> Unit, actions: MessageActions) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (sent) {
            DropdownMenuItem(
                text = { Text(strings.reply) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
                onClick = { onDismiss(); actions.onReply() },
            )
        }
        DropdownMenuItem(
            text = { Text(strings.copy) },
            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
            onClick = { onDismiss(); onCopy() },
        )
        if (mine && sent) {
            DropdownMenuItem(
                text = { Text(strings.edit) },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = { onDismiss(); actions.onEdit() },
            )
            DropdownMenuItem(
                text = { Text(strings.delete, color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { onDismiss(); actions.onDelete() },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(item: MessageItem, highlighted: Boolean, quoteAuthor: String, onQuoteClick: (String) -> Unit, actions: MessageActions) {
    val mine = item.mine
    val chatStyle = LocalChatStyle.current
    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember { mutableStateOf(false) }
    val shape = if (mine) chatStyle.outgoingShape else chatStyle.incomingShape
    val highlight by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
        label = "highlight",
    )
    Row(
        Modifier.fillMaxWidth().background(highlight).padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Box {
            Surface(
                color = if (mine) chatStyle.outgoing else chatStyle.incoming,
                contentColor = if (mine) chatStyle.onOutgoing else chatStyle.onIncoming,
                shape = shape,
                border = if (mine) null else BorderStroke(1.dp, chatStyle.incomingBorder),
                modifier = Modifier.widthIn(max = 300.dp).clip(shape).combinedClickable(
                    enabled = !item.message.deleted,
                    onLongClickLabel = strings.messageActions,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                    onClick = {},
                ),
            ) {
                Column(Modifier.width(IntrinsicSize.Max).padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 6.dp)) {
                    item.senderName?.let {
                        Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1, modifier = Modifier.padding(bottom = 2.dp))
                    }
                    item.message.replyTo?.let { reply ->
                        ReplyQuote(
                            author = quoteAuthor,
                            preview = if (item.replyToDeleted) strings.messageDeleted else reply.preview,
                            previewDeleted = item.replyToDeleted,
                            accent = LocalContentColor.current,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp).clickable { onQuoteClick(reply.id) },
                        )
                    }
                    if (item.message.deleted) {
                        DeletedText()
                    } else {
                        Text(item.message.text, style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        if (item.message.editedAt != null) {
                            Text(strings.edited, style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = 0.6f))
                            Spacer(Modifier.width(4.dp))
                        }
                        item.message.sentAt?.let {
                            Text(formatTime(it), style = MaterialTheme.typography.labelSmall.mono(), color = LocalContentColor.current.copy(alpha = 0.6f))
                        }
                        item.status?.takeUnless { item.message.deleted }?.let {
                            Spacer(Modifier.width(4.dp))
                            StatusIcon(it)
                        }
                    }
                }
            }
            MessageMenu(
                expanded = menuOpen,
                mine = mine,
                sent = !item.message.pending,
                onDismiss = { menuOpen = false },
                onCopy = { clipboard.setText(AnnotatedString(item.message.text)) },
                actions = actions,
            )
        }
    }
}


/** На месте удалённого сообщения — курсивная отметка, как в мессенджерах. */
@Composable
private fun DeletedText() {
    val muted = LocalContentColor.current.copy(alpha = 0.6f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Block, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(strings.messageDeleted, style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic), color = muted)
    }
}

@Composable
private fun StatusIcon(status: MessageStatus) {
    val read = status == MessageStatus.READ || status == MessageStatus.READ_ALL
    val tint = if (read) LocalChatStyle.current.readTick else LocalContentColor.current.copy(alpha = 0.6f)
    if (status == MessageStatus.READ_ALL) {
        // Три галочки: двойная поверх одинарной со сдвигом влево.
        Box(Modifier.width(22.dp).height(16.dp)) {
            Icon(Icons.Filled.Done, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Icon(Icons.Filled.DoneAll, contentDescription = strings.readByAll, tint = tint, modifier = Modifier.size(16.dp).align(Alignment.CenterEnd))
        }
        return
    }
    val (icon, description) = when (status) {
        MessageStatus.SENDING -> Icons.Filled.Schedule to strings.sending
        MessageStatus.SENT -> Icons.Filled.Done to strings.sent
        else -> Icons.Filled.DoneAll to strings.read
    }
    Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(16.dp))
}

@Composable
private fun ReplyQuote(author: String, preview: String, accent: Color, modifier: Modifier = Modifier, previewDeleted: Boolean = false) {
    Row(modifier.height(IntrinsicSize.Min).clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.1f))) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(author, style = MaterialTheme.typography.labelMedium, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                preview,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = if (previewDeleted) FontStyle.Italic else null,
                color = LocalContentColor.current.copy(alpha = 0.75f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MessageInput(
    replyingTo: MessageItem?,
    editing: MessageItem?,
    replyAuthor: (MessageItem) -> String,
    onCancelReply: () -> Unit,
    onCancelEdit: () -> Unit,
    onSend: (String) -> Unit,
    onDraftChanged: (String) -> Unit,
) {
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    // Черновик, отложенный на время правки: после неё он возвращается в поле, как в Telegram.
    var draft by rememberSaveable { mutableStateOf("") }
    var wasEditing by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(editing?.message?.id) {
        if (editing != null) {
            if (!wasEditing) draft = text.text
            text = TextFieldValue(editing.message.text, TextRange(editing.message.text.length))
            focus.requestFocus()
        } else if (wasEditing) {
            text = TextFieldValue(draft, TextRange(draft.length))
        }
        wasEditing = editing != null
    }
    LaunchedEffect(replyingTo?.message?.id) {
        if (replyingTo != null) focus.requestFocus()
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
            when {
                editing != null -> InputContextBar(Icons.Outlined.Edit, strings.editing, ReplyTo.previewOf(editing.message.text), strings.cancelEdit, onCancelEdit)
                replyingTo != null -> InputContextBar(
                    Icons.AutoMirrored.Filled.Reply,
                    replyAuthor(replyingTo),
                    ReplyTo.previewOf(replyingTo.message.text),
                    strings.cancelReply,
                    onCancelReply,
                )
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
                TextField(
                    value = text,
                    onValueChange = {
                        val changed = it.text != text.text
                        text = if (it.text.length > MAX_MESSAGE_LENGTH) it.copy(text = it.text.take(MAX_MESSAGE_LENGTH)) else it
                        if (changed) onDraftChanged(text.text)
                    },
                    placeholder = { Text(strings.messageHint) },
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        onSend(text.text)
                        text = TextFieldValue()
                    },
                    enabled = text.text.isNotBlank(),
                    modifier = Modifier.size(56.dp),
                ) {
                    if (editing != null) {
                        Icon(Icons.Filled.Check, contentDescription = strings.save)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = strings.send)
                    }
                }
            }
        }
    }
}

/** Плашка над полем ввода: на что отвечаем или что исправляем. */
@Composable
private fun InputContextBar(icon: ImageVector, title: String, preview: String, cancelLabel: String, onCancel: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        ReplyQuote(author = title, preview = preview, accent = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = cancelLabel) }
    }
}
