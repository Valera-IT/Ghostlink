package io.github.valerait.ghostlink.feature.chat

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valerait.ghostlink.core.designsystem.LocalChatStyle
import io.github.valerait.ghostlink.core.model.Message
import io.github.valerait.ghostlink.core.model.MessageId
import io.github.valerait.ghostlink.core.model.MessageStatus
import io.github.valerait.ghostlink.core.model.ReplyTo
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.core.platform.rememberCopyToClipboard
import io.github.valerait.ghostlink.core.ui.dayLabel
import io.github.valerait.ghostlink.core.ui.formatTime
import io.github.valerait.ghostlink.core.ui.rememberNow
import io.github.valerait.ghostlink.core.ui.resources.Res
import io.github.valerait.ghostlink.core.ui.resources.action_back
import io.github.valerait.ghostlink.core.ui.resources.action_save
import io.github.valerait.ghostlink.core.ui.resources.action_send
import io.github.valerait.ghostlink.core.ui.resources.chat_actions
import io.github.valerait.ghostlink.core.ui.resources.chat_deleted
import io.github.valerait.ghostlink.core.ui.resources.chat_edit_cancel
import io.github.valerait.ghostlink.core.ui.resources.chat_edited
import io.github.valerait.ghostlink.core.ui.resources.chat_editing
import io.github.valerait.ghostlink.core.ui.resources.chat_empty
import io.github.valerait.ghostlink.core.ui.resources.chat_input_hint
import io.github.valerait.ghostlink.core.ui.resources.chat_reply_cancel
import io.github.valerait.ghostlink.core.ui.resources.chat_reply_original_unavailable
import io.github.valerait.ghostlink.core.ui.resources.chat_reply_you
import io.github.valerait.ghostlink.core.ui.resources.status_read
import io.github.valerait.ghostlink.core.ui.resources.status_read_all
import io.github.valerait.ghostlink.core.ui.resources.status_sending
import io.github.valerait.ghostlink.core.ui.resources.status_sent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onGroupInfo: () -> Unit,
    viewModel: ChatViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now by rememberNow()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var highlightedId by remember { mutableStateOf<MessageId?>(null) }
    var confirmDeleteId by rememberSaveable { mutableStateOf<String?>(null) }

    val nameOf: (UserId?) -> String = { state.names[it].orEmpty() }
    val you = stringResource(Res.string.chat_reply_you)
    val originalUnavailable = stringResource(Res.string.chat_reply_original_unavailable)

    // Нажатие на цитату — прокрутка к исходному сообщению и короткая подсветка.
    val showOriginal: (MessageId) -> Unit = { id ->
        val index = state.rows.indexOfFirst { it.key == id.value }
        scope.launch {
            if (index < 0) {
                snackbar.showSnackbar(originalUnavailable)
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
    val hideKeyboardOnDrag =
        remember(keyboard, focusManager) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source == NestedScrollSource.UserInput && available.y > 0f) {
                        keyboard?.hide()
                        focusManager.clearFocus()
                    }
                    return Offset.Zero
                }
            }
        }

    LifecycleResumeEffect(Unit) {
        viewModel.onAction(ChatAction.VisibilityChanged(true))
        onPauseOrDispose { viewModel.onAction(ChatAction.VisibilityChanged(false)) }
    }

    // Новое сообщение — прокручиваем вниз, если пользователь и так был внизу.
    val newestKey = state.rows.firstOrNull()?.key
    LaunchedEffect(newestKey) {
        if (listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                actions = { DisappearingMenu(state.disappearing) { viewModel.onAction(ChatAction.SetDisappearing(it)) } },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
                title = { ChatTitle(state, now, onGroupInfo) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            MessageInput(
                replyingTo = state.replyingTo,
                editing = state.editing,
                replyAuthor = { if (it.mine) you else nameOf(it.message.senderId) },
                onCancelReply = { viewModel.onAction(ChatAction.CancelReply) },
                onCancelEdit = { viewModel.onAction(ChatAction.CancelEdit) },
                onDraftChanged = { viewModel.onAction(ChatAction.DraftChanged(it)) },
                onSend = { viewModel.onAction(ChatAction.Send(it)) },
            )
        },
    ) { padding ->
        if (!state.loading && state.rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.chat_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                    is ChatRow.Day -> {
                        DayHeader(dayLabel(row.time, now))
                    }

                    is ChatRow.Msg -> {
                        val message = row.item.message
                        MessageBubble(
                            item = row.item,
                            highlighted = message.id == highlightedId,
                            quoteAuthor = if (row.item.replyToMine) you else nameOf(message.replyTo?.senderId),
                            onQuoteClick = showOriginal,
                            actions =
                                MessageActions(
                                    onReply = { viewModel.onAction(ChatAction.StartReply(message.id)) },
                                    onEdit = { viewModel.onAction(ChatAction.StartEdit(message.id)) },
                                    onDelete = { confirmDeleteId = message.id.value },
                                ),
                        )
                    }
                }
            }
        }
    }

    confirmDeleteId?.let { id ->
        DeleteMessageDialog(
            onConfirm = {
                confirmDeleteId = null
                viewModel.onAction(ChatAction.Delete(MessageId(id)))
            },
            onDismiss = { confirmDeleteId = null },
        )
    }
}

@Composable
private fun DayHeader(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        val chatStyle = LocalChatStyle.current
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = chatStyle.chipShape) {
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelMedium.mono(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    item: MessageItem,
    highlighted: Boolean,
    quoteAuthor: String,
    onQuoteClick: (MessageId) -> Unit,
    actions: MessageActions,
) {
    val mine = item.mine
    val copy = rememberCopyToClipboard()
    val scope = rememberCoroutineScope()
    val chatStyle = LocalChatStyle.current
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    val highlight by animateColorAsState(
        targetValue = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
        label = "highlight",
    )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(highlight)
                .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        // Долгое нажатие — меню действий, как в Telegram. Пока сообщение не дошло до сервера, его можно только скопировать.
        Box {
            Surface(
                color = if (mine) chatStyle.outgoing else chatStyle.incoming,
                contentColor = if (mine) chatStyle.onOutgoing else chatStyle.onIncoming,
                shape = if (mine) chatStyle.outgoingShape else chatStyle.incomingShape,
                border = if (mine) null else BorderStroke(1.dp, chatStyle.incomingBorder),
                modifier =
                    Modifier
                        .widthIn(max = 300.dp)
                        .clip(if (mine) chatStyle.outgoingShape else chatStyle.incomingShape)
                        .combinedClickable(
                            enabled = !item.message.deleted,
                            onLongClickLabel = stringResource(Res.string.chat_actions),
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuOpen = true
                            },
                            onClick = {},
                        ),
            ) {
                Column(Modifier.width(IntrinsicSize.Max).padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 6.dp)) {
                    SenderName(item.senderName)
                    item.message.replyTo?.let { reply ->
                        ReplyQuote(
                            author = quoteAuthor,
                            preview = if (item.replyToDeleted) stringResource(Res.string.chat_deleted) else reply.preview,
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
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (item.message.editedAt != null) {
                            Text(
                                text = stringResource(Res.string.chat_edited),
                                style = MaterialTheme.typography.labelSmall,
                                color = mutedContentColor(),
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        item.message.sentAt?.let {
                            Text(
                                text = formatTime(it),
                                style = MaterialTheme.typography.labelSmall.mono(),
                                color = mutedContentColor(),
                            )
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
                actions =
                    actions.copy(
                        onCopy = {
                            copy(item.message.text)
                        },
                    ),
            )
        }
    }
}

/** На месте удалённого сообщения — курсивная отметка, как в мессенджерах. */
@Composable
private fun DeletedText() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Block, contentDescription = null, tint = mutedContentColor(), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(Res.string.chat_deleted),
            style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
            color = mutedContentColor(),
        )
    }
}

@Composable
private fun mutedContentColor(): Color = LocalContentColor.current.copy(alpha = 0.6f)

private fun TextStyle.mono(): TextStyle = copy(fontFamily = FontFamily.Monospace)

@Composable
private fun StatusIcon(status: MessageStatus) {
    val read = status == MessageStatus.READ || status == MessageStatus.READ_ALL
    val tint = if (read) LocalChatStyle.current.readTick else mutedContentColor()
    if (status == MessageStatus.READ_ALL) {
        // Три галочки: двойная поверх одинарной со сдвигом влево.
        Box(Modifier.width(22.dp).height(16.dp)) {
            Icon(Icons.Filled.Done, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Icon(
                Icons.Filled.DoneAll,
                contentDescription = stringResource(Res.string.status_read_all),
                tint = tint,
                modifier = Modifier.size(16.dp).align(Alignment.CenterEnd),
            )
        }
        return
    }
    val (icon, description) =
        when (status) {
            MessageStatus.SENDING -> Icons.Filled.Schedule to Res.string.status_sending
            MessageStatus.SENT -> Icons.Filled.Done to Res.string.status_sent
            else -> Icons.Filled.DoneAll to Res.string.status_read
        }
    Icon(
        imageVector = icon,
        contentDescription = stringResource(description),
        tint = tint,
        modifier = Modifier.size(16.dp),
    )
}

@Composable
private fun ReplyQuote(
    author: String,
    preview: String,
    accent: Color,
    modifier: Modifier = Modifier,
    previewDeleted: Boolean = false,
) {
    Row(
        modifier =
            modifier
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.1f)),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(author, style = MaterialTheme.typography.labelMedium, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = preview,
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
    onDraftChanged: (String) -> Unit,
    onSend: (String) -> Unit,
) {
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    // Черновик, отложенный на время правки: после неё он возвращается в поле, как в Telegram.
    var draft by rememberSaveable { mutableStateOf("") }
    var wasEditing by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    val editingId = editing?.message?.id
    LaunchedEffect(editingId) {
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
                editing != null -> {
                    InputContextBar(
                        icon = Icons.Outlined.Edit,
                        title = stringResource(Res.string.chat_editing),
                        preview = ReplyTo.previewOf(editing.message.text),
                        cancelLabel = stringResource(Res.string.chat_edit_cancel),
                        onCancel = onCancelEdit,
                    )
                }

                replyingTo != null -> {
                    InputContextBar(
                        icon = Icons.AutoMirrored.Filled.Reply,
                        title = replyAuthor(replyingTo),
                        preview = ReplyTo.previewOf(replyingTo.message.text),
                        cancelLabel = stringResource(Res.string.chat_reply_cancel),
                        onCancel = onCancelReply,
                    )
                }
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                TextField(
                    value = text,
                    onValueChange = {
                        text =
                            if (it.text.length > Message.TEXT_MAX_LENGTH) it.copy(text = it.text.take(Message.TEXT_MAX_LENGTH)) else it
                        onDraftChanged(text.text)
                    },
                    placeholder = { Text(stringResource(Res.string.chat_input_hint)) },
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        TextFieldDefaults.colors(
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
                        Icon(Icons.Filled.Check, contentDescription = stringResource(Res.string.action_save))
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(Res.string.action_send))
                    }
                }
            }
        }
    }
}

/** Плашка над полем ввода: на что отвечаем или что исправляем. */
@Composable
private fun InputContextBar(
    icon: ImageVector,
    title: String,
    preview: String,
    cancelLabel: String,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        ReplyQuote(
            author = title,
            preview = preview,
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onCancel) {
            Icon(Icons.Filled.Close, contentDescription = cancelLabel)
        }
    }
}
