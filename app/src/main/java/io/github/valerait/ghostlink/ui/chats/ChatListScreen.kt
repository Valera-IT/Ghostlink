package io.github.valerait.ghostlink.ui.chats

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valerait.ghostlink.R
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.ui.common.Avatar
import io.github.valerait.ghostlink.ui.common.DemoModeBanner
import io.github.valerait.ghostlink.ui.common.GhostLogo
import io.github.valerait.ghostlink.ui.common.formatChatListTime
import io.github.valerait.ghostlink.ui.common.rememberIsTyping
import io.github.valerait.ghostlink.ui.common.rememberNow
import io.github.valerait.ghostlink.ui.theme.LogoTextStyle
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onOpenChat: (ChatId) -> Unit,
    onMyCode: () -> Unit,
    onNewGroup: () -> Unit,
    onScan: () -> Unit,
    onSettings: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now by rememberNow()
    var removing by rememberSaveable { mutableStateOf<String?>(null) }

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
                    IconButton(onClick = onNewGroup) {
                        Icon(Icons.Outlined.GroupAdd, contentDescription = stringResource(R.string.group_new))
                    }
                    IconButton(onClick = onScan) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = stringResource(R.string.action_scan))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.items.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onMyCode,
                    icon = { Icon(Icons.Filled.QrCode2, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_my_code)) },
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> {
                Unit
            }

            state.items.isEmpty() -> {
                EmptyState(onMyCode, onScan, Modifier.padding(padding))
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.demoMode) {
                        item { DemoModeBanner(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                    }
                    items(state.items, key = { it.chat.id.value }) { item ->
                        ChatRow(
                            item = item,
                            now = now,
                            onClick = { onOpenChat(item.chat.id) },
                            onPin = { viewModel.setPinned(item.chat.id, !item.pinned) },
                            onMute = { viewModel.setMuted(item.chat.id, !item.muted) },
                            onRemove = { removing = item.chat.id.value },
                        )
                    }
                }
            }
        }
    }

    removing?.let { ChatId(it) }?.let { chatId ->
        val item = state.items.firstOrNull { it.chat.id == chatId }
        if (item == null) {
            removing = null
        } else {
            RemoveChatDialog(
                name = item.title ?: "…",
                isGroup = item.chat.isGroup,
                onConfirm = {
                    viewModel.remove(chatId)
                    removing = null
                },
                onDismiss = { removing = null },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(
    item: ChatListItem,
    now: Instant,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onMute: () -> Unit,
    onRemove: () -> Unit,
) {
    val name = item.title ?: "…"
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menu = true
                        },
                    ).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Avatar(name = name, id = item.avatarId, size = 52.dp)
                if (item.partner?.isOnline(now) == true) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .background(MaterialTheme.colorScheme.background, CircleShape)
                            .padding(2.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (item.muted) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Outlined.NotificationsOff,
                                contentDescription = stringResource(R.string.chats_muted),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    item.lastMessage?.sentAt?.let {
                        Text(
                            text = formatChatListTime(it, now),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (item.unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val typing = rememberIsTyping(item.typingUntil)
                    Text(
                        text =
                            when {
                                !typing -> previewText(item)
                                item.typingName != null -> stringResource(R.string.chat_typing_named, item.typingName)
                                else -> stringResource(R.string.chat_typing)
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (typing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (item.unread) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.unread) {
                        Spacer(Modifier.width(8.dp))
                        // У чата без звука непрочитанное — серым, как в Telegram.
                        val dot = if (item.muted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                        Box(Modifier.size(10.dp).background(dot, CircleShape))
                    } else if (item.pinned) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Outlined.PushPin,
                            contentDescription = stringResource(R.string.chats_pinned),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        ChatMenu(menu, item, onDismiss = { menu = false }, onPin = onPin, onMute = onMute, onRemove = onRemove)
    }
}

/** Меню по долгому нажатию на чат, как в Telegram. */
@Composable
private fun ChatMenu(
    expanded: Boolean,
    item: ChatListItem,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onMute: () -> Unit,
    onRemove: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(if (item.pinned) R.string.chats_unpin else R.string.chats_pin)) },
            leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
            onClick = {
                onDismiss()
                onPin()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(if (item.muted) R.string.chats_unmute else R.string.chats_mute)) },
            leadingIcon = {
                Icon(if (item.muted) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff, contentDescription = null)
            },
            onClick = {
                onDismiss()
                onMute()
            },
        )
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(if (item.chat.isGroup) R.string.group_leave else R.string.chats_remove),
                    color = MaterialTheme.colorScheme.error,
                )
            },
            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            onClick = {
                onDismiss()
                onRemove()
            },
        )
    }
}

@Composable
private fun RemoveChatDialog(
    name: String,
    isGroup: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isGroup) R.string.group_leave_title else R.string.chats_remove_title)) },
        text = { Text(stringResource(if (isGroup) R.string.group_leave_body else R.string.chats_remove_body, name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(if (isGroup) R.string.group_leave else R.string.chats_remove), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun EmptyState(
    onMyCode: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GhostLogo(size = 88.dp, tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.chats_empty_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.chats_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onMyCode, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.QrCode2, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_my_code))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_scan))
        }
    }
}

@Composable
private fun previewText(item: ChatListItem): String {
    val last = item.lastMessage
    return when {
        last == null -> stringResource(R.string.chats_no_messages)
        item.lastMessageIsMine -> stringResource(R.string.chats_you_prefix, last.text)
        item.lastSenderName != null -> stringResource(R.string.chats_sender_prefix, item.lastSenderName, last.text)
        else -> last.text
    }
}
