package io.github.valerait.ghostlink.feature.chat

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valerait.ghostlink.core.model.GroupInfo
import io.github.valerait.ghostlink.core.model.User
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.core.ui.Avatar
import io.github.valerait.ghostlink.core.ui.resources.Res
import io.github.valerait.ghostlink.core.ui.resources.action_back
import io.github.valerait.ghostlink.core.ui.resources.action_cancel
import io.github.valerait.ghostlink.core.ui.resources.action_save
import io.github.valerait.ghostlink.core.ui.resources.group_add_members
import io.github.valerait.ghostlink.core.ui.resources.group_admin
import io.github.valerait.ghostlink.core.ui.resources.group_info
import io.github.valerait.ghostlink.core.ui.resources.group_leave
import io.github.valerait.ghostlink.core.ui.resources.group_leave_body
import io.github.valerait.ghostlink.core.ui.resources.group_leave_title
import io.github.valerait.ghostlink.core.ui.resources.group_members
import io.github.valerait.ghostlink.core.ui.resources.group_name
import io.github.valerait.ghostlink.core.ui.resources.group_remove
import io.github.valerait.ghostlink.core.ui.resources.group_remove_title
import io.github.valerait.ghostlink.core.ui.resources.group_rename
import io.github.valerait.ghostlink.core.ui.resources.group_you
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Карточка группы: участники, название; админ добавляет и удаляет участников, любой может выйти. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    onBack: () -> Unit,
    onAddMembers: () -> Unit,
    onLeft: () -> Unit,
    viewModel: GroupInfoViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var removingId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.left, state.loading) {
        if (!state.loading && state.left) onLeft()
    }
    val chat = state.chat
    val group = chat?.group ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.group_info)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(group.name, chat.id.value, size = 88.dp)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(group.name, style = MaterialTheme.typography.headlineSmall)
                        if (state.isOwner) {
                            IconButton(onClick = { renaming = true }) {
                                Icon(Icons.Outlined.Edit, contentDescription = stringResource(Res.string.group_rename))
                            }
                        }
                    }
                    Text(
                        pluralStringResource(Res.plurals.group_members, chat.members.size, chat.members.size),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.isOwner && chat.members.size < GroupInfo.MAX_MEMBERS) {
                item { ActionRow(Icons.Outlined.PersonAdd, stringResource(Res.string.group_add_members), onClick = onAddMembers) }
            }
            items(state.members, key = { it.id.value }) { member ->
                MemberRow(
                    member = member,
                    isMe = member.id == state.me,
                    isOwner = member.id == group.ownerId,
                    onRemove = if (state.isOwner && member.id != state.me) ({ removingId = member.id.value }) else null,
                )
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                ActionRow(
                    Icons.AutoMirrored.Outlined.Logout,
                    stringResource(Res.string.group_leave),
                    color = MaterialTheme.colorScheme.error,
                    onClick = { confirmLeave = true },
                )
            }
        }
    }

    if (renaming) {
        RenameDialog(group.name, onConfirm = {
            viewModel.onAction(GroupInfoAction.Rename(it))
            renaming = false
        }, onDismiss = { renaming = false })
    }
    removingId?.let { UserId(it) }?.let { id ->
        val name =
            state.members
                .firstOrNull { it.id == id }
                ?.name
                .orEmpty()
        ConfirmDialog(
            title = stringResource(Res.string.group_remove_title, name),
            confirm = stringResource(Res.string.group_remove),
            onConfirm = {
                viewModel.onAction(GroupInfoAction.Remove(id))
                removingId = null
            },
            onDismiss = { removingId = null },
        )
    }
    if (confirmLeave) {
        ConfirmDialog(
            title = stringResource(Res.string.group_leave_title),
            body = stringResource(Res.string.group_leave_body, group.name),
            confirm = stringResource(Res.string.group_leave),
            onConfirm = {
                confirmLeave = false
                viewModel.onAction(GroupInfoAction.Leave)
            },
            onDismiss = { confirmLeave = false },
        )
    }
}

@Composable
private fun MemberRow(
    member: User,
    isMe: Boolean,
    isOwner: Boolean,
    onRemove: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Avatar(member.name, member.id.value, size = 44.dp, photoVersion = member.avatarVersion)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (isMe) stringResource(Res.string.group_you, member.name) else member.name, style = MaterialTheme.typography.titleMedium)
            if (isOwner) {
                Text(
                    stringResource(Res.string.group_admin),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.PersonRemove, contentDescription = stringResource(Res.string.group_remove))
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    text: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(24.dp))
        Text(text, color = color, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun RenameDialog(
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.group_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(GroupInfo.NAME_MAX_LENGTH) },
                singleLine = true,
                label = { Text(stringResource(Res.string.group_name)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    body: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = body?.let { { Text(it) } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
