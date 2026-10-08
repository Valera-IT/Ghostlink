package io.github.valerait.ghostlink.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valerait.ghostlink.R
import io.github.valerait.ghostlink.ui.common.Avatar
import io.github.valerait.ghostlink.ui.common.DemoModeBanner
import io.github.valerait.ghostlink.ui.common.GhostLogo
import io.github.valerait.ghostlink.ui.common.formatChatListTime
import io.github.valerait.ghostlink.ui.common.rememberNow
import io.github.valerait.ghostlink.ui.theme.LogoTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onOpenChat: (String) -> Unit,
    onMyCode: () -> Unit,
    onScan: () -> Unit,
    onSettings: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel(),
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
            state.loading -> Unit
            state.items.isEmpty() -> EmptyState(onMyCode, onScan, Modifier.padding(padding))
            else -> LazyColumn(
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.demoMode) {
                    item { DemoModeBanner(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                }
                items(state.items, key = { it.chat.id }) { item ->
                    ChatRow(item, now, onClick = { onOpenChat(item.chat.id) })
                }
            }
        }
    }
}

@Composable
private fun ChatRow(item: ChatListItem, now: Long, onClick: () -> Unit) {
    val name = item.partner?.name ?: "…"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Avatar(name = name, id = item.partnerId, size = 52.dp)
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
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                item.chat.lastMessage?.sentAt?.let {
                    Text(
                        text = formatChatListTime(it, now),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (item.unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val last = item.chat.lastMessage
                val preview = when {
                    last == null -> stringResource(R.string.chats_no_messages)
                    item.lastMessageIsMine -> stringResource(R.string.chats_you_prefix, last.text)
                    else -> last.text
                }
                Text(
                    text = preview,
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
private fun EmptyState(onMyCode: () -> Unit, onScan: () -> Unit, modifier: Modifier = Modifier) {
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
