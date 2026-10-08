package io.github.valerait.ghostlink.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valerait.ghostlink.R
import io.github.valerait.ghostlink.data.model.MessageStatus
import io.github.valerait.ghostlink.ui.common.Avatar
import io.github.valerait.ghostlink.ui.common.dayLabel
import io.github.valerait.ghostlink.ui.common.formatTime
import io.github.valerait.ghostlink.ui.common.presenceText
import io.github.valerait.ghostlink.ui.common.rememberNow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onBack: () -> Unit, viewModel: ChatViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now by rememberNow()
    val listState = rememberLazyListState()

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
        bottomBar = {
            MessageInput(onSend = viewModel::send)
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
                    is ChatRow.Msg -> MessageBubble(row.item)
                }
            }
        }
    }
}

@Composable
private fun DayHeader(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(50)) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun MessageBubble(item: MessageItem) {
    val mine = item.mine
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomEnd = if (mine) 4.dp else 18.dp,
        bottomStart = if (mine) 18.dp else 4.dp,
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            shape = bubbleShape,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 6.dp)) {
                Text(item.message.text, style = MaterialTheme.typography.bodyLarge)
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item.message.sentAt?.let {
                        Text(
                            text = formatTime(it),
                            style = MaterialTheme.typography.labelSmall,
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
private fun mutedContentColor(): Color =
    LocalContentColor.current.copy(alpha = 0.6f)

@Composable
private fun StatusIcon(status: MessageStatus) {
    val (icon, description) = when (status) {
        MessageStatus.SENDING -> Icons.Filled.Schedule to R.string.status_sending
        MessageStatus.SENT -> Icons.Filled.Done to R.string.status_sent
        MessageStatus.READ -> Icons.Filled.DoneAll to R.string.status_read
    }
    Icon(
        imageVector = icon,
        contentDescription = stringResource(description),
        tint = if (status == MessageStatus.READ) MaterialTheme.colorScheme.primary else mutedContentColor(),
        modifier = Modifier.size(16.dp),
    )
}

@Composable
private fun MessageInput(onSend: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            TextField(
                value = text,
                onValueChange = { text = it.take(MAX_MESSAGE_LENGTH) },
                placeholder = { Text(stringResource(R.string.chat_input_hint)) },
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
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.action_send))
            }
        }
    }
}
