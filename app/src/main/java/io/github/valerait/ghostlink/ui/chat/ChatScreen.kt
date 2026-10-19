package io.github.valerait.ghostlink.ui.chat

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valerait.ghostlink.R
import io.github.valerait.ghostlink.data.model.MessageStatus
import io.github.valerait.ghostlink.data.model.ReplyTo
import io.github.valerait.ghostlink.ui.common.Avatar
import io.github.valerait.ghostlink.ui.common.dayLabel
import io.github.valerait.ghostlink.ui.common.formatTime
import io.github.valerait.ghostlink.ui.common.presenceText
import io.github.valerait.ghostlink.ui.common.rememberNow
import io.github.valerait.ghostlink.ui.theme.LocalChatStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now by rememberNow()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var highlightedId by remember { mutableStateOf<String?>(null) }
    val partnerName = state.partner?.name.orEmpty()
    val you = stringResource(R.string.chat_reply_you)
    val originalUnavailable = stringResource(R.string.chat_reply_original_unavailable)

    // Нажатие на цитату — прокрутка к исходному сообщению и короткая подсветка.
    val showOriginal: (String) -> Unit = { id ->
        val index = state.rows.indexOfFirst { it.key == id }
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

    LifecycleResumeEffect(Unit) {
        viewModel.onVisibilityChanged(true)
        onPauseOrDispose { viewModel.onVisibilityChanged(false) }
    }

    // Новое сообщение — прокручиваем вниз, если пользователь и так был внизу.
    val newestKey = state.rows.firstOrNull()?.key
    LaunchedEffect(newestKey) {
        if (listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = {
                    val partner = state.partner
                    if (partner != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(partner.name, partner.id, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(partner.name, style = MaterialTheme.typography.titleMedium)
                                val online = partner.isOnline(now)
                                Text(
                                    text = presenceText(partner, now),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            MessageInput(
                replyingTo = state.replyingTo,
                replyAuthor = { if (it.mine) you else partnerName },
                onCancelReply = viewModel::cancelReply,
                onSend = viewModel::send,
            )
        },
    ) { padding ->
        if (!state.loading && state.rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.chat_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                    is ChatRow.Msg ->
                        SwipeToReply(
                            enabled = !row.item.message.pending,
                            onReply = { viewModel.startReply(row.item.message.id) },
                        ) {
                            MessageBubble(
                                item = row.item,
                                highlighted = row.item.message.id == highlightedId,
                                quoteAuthor = if (row.item.replyToMine) you else partnerName,
                                onQuoteClick = showOriginal,
                            )
                        }
                }
            }
        }
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

/** Свайп вправо по сообщению — ответить. Для TalkBack то же самое доступно как действие «Ответить». */
@Composable
private fun SwipeToReply(
    enabled: Boolean,
    onReply: () -> Unit,
    content: @Composable () -> Unit,
) {
    val currentOnReply by rememberUpdatedState(onReply)
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    val replyLabel = stringResource(R.string.chat_reply)
    Box(
        modifier =
            Modifier
                .semantics {
                    if (enabled) customActions = listOf(CustomAccessibilityAction(replyLabel) { currentOnReply().let { true } })
                }.pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    var armed = false
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (armed) currentOnReply()
                            armed = false
                            scope.launch { offset.animateTo(0f) }
                        },
                        onDragCancel = {
                            armed = false
                            scope.launch { offset.animateTo(0f) }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        val next = (offset.value + dragAmount).coerceIn(0f, threshold * 1.4f)
                        scope.launch { offset.snapTo(next) }
                        if (!armed && next >= threshold) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        armed = next >= threshold
                    }
                },
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Reply,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .graphicsLayer { alpha = (offset.value / threshold).coerceIn(0f, 1f) },
        )
        Box(Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }) { content() }
    }
}

@Composable
private fun MessageBubble(
    item: MessageItem,
    highlighted: Boolean,
    quoteAuthor: String,
    onQuoteClick: (String) -> Unit,
) {
    val mine = item.mine
    val chatStyle = LocalChatStyle.current
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
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item.message.sentAt?.let {
                        Text(
                            text = formatTime(it),
                            style = MaterialTheme.typography.labelSmall.mono(),
                            color = mutedContentColor(),
                        )
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
private fun mutedContentColor(): Color = LocalContentColor.current.copy(alpha = 0.6f)

private fun TextStyle.mono(): TextStyle = copy(fontFamily = FontFamily.Monospace)

@Composable
private fun StatusIcon(status: MessageStatus) {
    val (icon, description) =
        when (status) {
            MessageStatus.SENDING -> Icons.Filled.Schedule to R.string.status_sending
            MessageStatus.SENT -> Icons.Filled.Done to R.string.status_sent
            MessageStatus.READ -> Icons.Filled.DoneAll to R.string.status_read
        }
    Icon(
        imageVector = icon,
        contentDescription = stringResource(description),
        tint = if (status == MessageStatus.READ) LocalChatStyle.current.readTick else mutedContentColor(),
        modifier = Modifier.size(16.dp),
    )
}

@Composable
private fun ReplyQuote(
    author: String,
    preview: String,
    accent: Color,
    modifier: Modifier = Modifier,
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    ReplyQuote(
                        author = replyAuthor(replyingTo),
                        preview = ReplyTo.previewOf(replyingTo.message.text),
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onCancelReply) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_reply_cancel))
                    }
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
                    onValueChange = { text = it.take(MAX_MESSAGE_LENGTH) },
                    placeholder = { Text(stringResource(R.string.chat_input_hint)) },
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
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.action_send))
                }
            }
        }
    }
}
