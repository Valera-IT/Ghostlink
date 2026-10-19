package io.github.valerait.ghostlink.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.valerait.ghostlink.app.AppGraph
import io.github.valerait.ghostlink.data.requireUserId
import io.github.valerait.ghostlink.model.Chat
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
import kotlin.math.roundToInt
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
import kotlinx.coroutines.launch

// ---------- Список чатов ----------

data class ChatListItem(val chat: Chat, val partnerId: String, val partner: User?, val unread: Boolean, val lastMessageIsMine: Boolean)

data class ChatListState(val loading: Boolean = true, val items: List<ChatListItem> = emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModel : ViewModel() {
    private val me = AppGraph.auth.requireUserId()
    private val chatList = AppGraph.chats.observeChats(me).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    // Подписка на собеседников пересоздаётся только при смене состава чатов.
    private val partners = chatList
        .map { list -> list.map { it.partnerId(me) }.distinct().sorted() }
        .distinctUntilChanged()
        .flatMapLatest { AppGraph.users.observeUsers(it) }

    val state: StateFlow<ChatListState> = combine(chatList, partners) { list, people ->
        ChatListState(
            loading = false,
            items = list.map { chat ->
                ChatListItem(chat, chat.partnerId(me), people[chat.partnerId(me)], chat.isUnreadFor(me), chat.lastMessage?.senderId == me)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatListState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onOpenChat: (String) -> Unit,
    onMyCode: () -> Unit,
    onScan: () -> Unit,
    onSettings: () -> Unit,
    viewModel: ChatListViewModel = viewModel { ChatListViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now by rememberNow()
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
                    IconButton(onClick = onScan) { Icon(Icons.Filled.QrCodeScanner, contentDescription = "Сканировать код") }
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Настройки") }
                },
            )
        },
        floatingActionButton = {
            if (state.items.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onMyCode,
                    icon = { Icon(Icons.Filled.QrCode2, contentDescription = null) },
                    text = { Text("Мой код") },
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> Unit
            state.items.isEmpty() -> EmptyChats(onMyCode, onScan, Modifier.padding(padding))
            else -> LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
                items(state.items, key = { it.chat.id }) { item -> ChatRowItem(item, now) { onOpenChat(item.chat.id) } }
            }
        }
    }
}

@Composable
private fun ChatRowItem(item: ChatListItem, now: Long, onClick: () -> Unit) {
    val name = item.partner?.name ?: "…"
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
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
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                item.chat.lastMessage?.sentAt?.let {
                    Text(
                        formatChatListTime(it, now),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (item.unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val last = item.chat.lastMessage
                val preview = when {
                    last == null -> "Нет сообщений — напишите первым"
                    item.lastMessageIsMine -> "Вы: ${last.text}"
                    else -> last.text
                }
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (item.unread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (item.unread) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun EmptyChats(onMyCode: () -> Unit, onScan: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        GhostLogo(size = 88.dp, tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(20.dp))
        Text("Пока здесь пусто", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Встретьтесь с другом и отсканируйте его код — или покажите ему свой.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onMyCode, modifier = Modifier.fillMaxWidth()) { Text("Мой код") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text("Сканировать код") }
    }
}

// ---------- Переписка ----------

const val MAX_MESSAGE_LENGTH = 4000

data class MessageItem(val message: Message, val mine: Boolean, val status: MessageStatus?, val replyToMine: Boolean = false)

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
    val partner: User? = null,
    val rows: List<ChatRow> = emptyList(),
    val replyingTo: MessageItem? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(private val chatId: String) : ViewModel() {
    private val me = AppGraph.auth.requireUserId()
    private val chats = AppGraph.chats
    private val visible = MutableStateFlow(false)
    private val replyingToId = MutableStateFlow<String?>(null)

    private val chat = chats.observeChat(chatId).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    private val messages = chats.observeMessages(chatId).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    private val partner = chat.mapNotNull { it?.partnerId(me) }.distinctUntilChanged()
        .flatMapLatest { AppGraph.users.observeUser(it) }
        .onStart { emit(null) }

    val state: StateFlow<ChatUiState> = combine(chat, messages, partner, replyingToId) { chat, messages, partner, replyingToId ->
        val partnerRead = chat?.lastRead?.get(chat.partnerId(me))
        val items = messages.map { m ->
            val mine = m.senderId == me
            MessageItem(m, mine, if (mine) m.status(partnerRead) else null, replyToMine = m.replyTo?.senderId == me)
        }
        ChatUiState(
            loading = false,
            partner = partner,
            rows = buildRows(items),
            replyingTo = replyingToId?.let { id -> items.firstOrNull { it.message.id == id } },
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
    }

    fun startReply(messageId: String) {
        replyingToId.value = messageId
    }

    fun cancelReply() {
        replyingToId.value = null
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        // Отвечать можно только на сообщение, уже записанное на сервер: у pending ещё нет стабильного места в чате.
        val replyTo = state.value.replyingTo?.message?.takeUnless { it.pending }?.let(ReplyTo::of)
        chats.sendMessage(chatId, me, trimmed.take(MAX_MESSAGE_LENGTH), replyTo)
        replyingToId.value = null
    }

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
fun ChatScreen(chatId: String, onBack: () -> Unit, viewModel: ChatViewModel = viewModel(key = chatId) { ChatViewModel(chatId) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now by rememberNow()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var highlightedId by remember { mutableStateOf<String?>(null) }
    val partnerName = state.partner?.name.orEmpty()

    // Нажатие на цитату — прокрутка к исходному сообщению и короткая подсветка.
    val showOriginal: (String) -> Unit = { id ->
        val index = state.rows.indexOfFirst { it.key == id }
        scope.launch {
            if (index < 0) {
                snackbar.showSnackbar("Исходное сообщение слишком старое")
            } else {
                listState.animateScrollToItem(index)
                highlightedId = id
                delay(1_500)
                highlightedId = null
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
                title = {
                    val partner = state.partner
                    if (partner != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(partner.name, partner.id, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(partner.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    presenceText(partner, now),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (partner.isOnline(now)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            MessageInput(
                replyingTo = state.replyingTo,
                replyAuthor = { if (it.mine) "Вы" else partnerName },
                onCancelReply = viewModel::cancelReply,
                onSend = viewModel::send,
            )
        },
    ) { padding ->
        if (!state.loading && state.rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Скажите привет 👋", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            items(state.rows, key = { it.key }) { row ->
                when (row) {
                    is ChatRow.Day -> DayHeader(dayLabel(row.millis, now))
                    is ChatRow.Msg -> SwipeToReply(
                        enabled = !row.item.message.pending,
                        onReply = { viewModel.startReply(row.item.message.id) },
                    ) {
                        MessageBubble(
                            item = row.item,
                            highlighted = row.item.message.id == highlightedId,
                            quoteAuthor = if (row.item.replyToMine) "Вы" else partnerName,
                            onQuoteClick = showOriginal,
                        )
                    }
                }
            }
        }
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

/** Свайп вправо по сообщению — ответить. Для VoiceOver то же самое доступно как действие «Ответить». */
@Composable
private fun SwipeToReply(enabled: Boolean, onReply: () -> Unit, content: @Composable () -> Unit) {
    val currentOnReply by rememberUpdatedState(onReply)
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    Box(
        Modifier
            .semantics {
                if (enabled) customActions = listOf(CustomAccessibilityAction("Ответить") { currentOnReply().let { true } })
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                // Смещение копим здесь: offset обновляется асинхронно и отстаёт от жеста.
                var dragged = 0f
                val reset = {
                    dragged = 0f
                    scope.launch { offset.animateTo(0f) }
                }
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragged >= threshold) currentOnReply()
                        reset()
                    },
                    onDragCancel = { reset() },
                ) { change, dragAmount ->
                    change.consume()
                    val wasArmed = dragged >= threshold
                    dragged = (dragged + dragAmount).coerceIn(0f, threshold * 1.4f)
                    if (!wasArmed && dragged >= threshold) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch { offset.snapTo(dragged) }
                }
            },
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Reply,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
                .graphicsLayer { alpha = (offset.value / threshold).coerceIn(0f, 1f) },
        )
        Box(Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }) { content() }
    }
}

@Composable
private fun MessageBubble(item: MessageItem, highlighted: Boolean, quoteAuthor: String, onQuoteClick: (String) -> Unit) {
    val mine = item.mine
    val chatStyle = LocalChatStyle.current
    val highlight by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
        label = "highlight",
    )
    Row(
        Modifier.fillMaxWidth().background(highlight).padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (mine) chatStyle.outgoing else chatStyle.incoming,
            contentColor = if (mine) chatStyle.onOutgoing else chatStyle.onIncoming,
            shape = if (mine) chatStyle.outgoingShape else chatStyle.incomingShape,
            border = if (mine) null else BorderStroke(1.dp, chatStyle.incomingBorder),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.width(IntrinsicSize.Max).padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 6.dp)) {
                item.message.replyTo?.let { reply ->
                    ReplyQuote(
                        author = quoteAuthor,
                        preview = reply.preview,
                        accent = LocalContentColor.current,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp).clickable { onQuoteClick(reply.id) },
                    )
                }
                Text(item.message.text, style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    item.message.sentAt?.let {
                        Text(formatTime(it), style = MaterialTheme.typography.labelSmall.mono(), color = LocalContentColor.current.copy(alpha = 0.6f))
                    }
                    item.status?.let {
                        Spacer(Modifier.width(4.dp))
                        StatusIcon(it)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(status: MessageStatus) {
    val (icon, description) = when (status) {
        MessageStatus.SENDING -> Icons.Filled.Schedule to "Отправляется"
        MessageStatus.SENT -> Icons.Filled.Done to "Отправлено"
        MessageStatus.READ -> Icons.Filled.DoneAll to "Прочитано"
    }
    Icon(
        icon,
        contentDescription = description,
        tint = if (status == MessageStatus.READ) LocalChatStyle.current.readTick else LocalContentColor.current.copy(alpha = 0.6f),
        modifier = Modifier.size(16.dp),
    )
}

@Composable
private fun ReplyQuote(author: String, preview: String, accent: Color, modifier: Modifier = Modifier) {
    Row(modifier.height(IntrinsicSize.Min).clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.1f))) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(author, style = MaterialTheme.typography.labelMedium, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                preview,
                style = MaterialTheme.typography.bodySmall,
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
    replyAuthor: (MessageItem) -> String,
    onCancelReply: () -> Unit,
    onSend: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
            if (replyingTo != null) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    ReplyQuote(
                        author = replyAuthor(replyingTo),
                        preview = ReplyTo.previewOf(replyingTo.message.text),
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onCancelReply) { Icon(Icons.Filled.Close, contentDescription = "Отменить ответ") }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                TextField(
                    value = text,
                    onValueChange = { text = it.take(MAX_MESSAGE_LENGTH) },
                    placeholder = { Text("Сообщение") },
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        onSend(text)
                        text = ""
                    },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.size(56.dp),
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить") }
            }
        }
    }
}
