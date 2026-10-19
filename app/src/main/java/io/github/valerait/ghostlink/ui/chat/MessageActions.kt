package io.github.valerait.ghostlink.ui.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.valerait.ghostlink.R

/** Действия из меню сообщения (долгое нажатие), как в Telegram. */
internal data class MessageActions(
    val onReply: () -> Unit,
    val onEdit: () -> Unit,
    val onDelete: () -> Unit,
    val onCopy: () -> Unit = {},
)

/** Пока сообщение не дошло до сервера, его можно только скопировать. Править и удалять можно только свои. */
@Composable
internal fun MessageMenu(
    expanded: Boolean,
    mine: Boolean,
    sent: Boolean,
    onDismiss: () -> Unit,
    actions: MessageActions,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (sent) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_reply)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
                onClick = {
                    onDismiss()
                    actions.onReply()
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.chat_copy)) },
            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
            onClick = {
                onDismiss()
                actions.onCopy()
            },
        )
        if (mine && sent) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_edit)) },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = {
                    onDismiss()
                    actions.onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    onDismiss()
                    actions.onDelete()
                },
            )
        }
    }
}

@Composable
internal fun DeleteMessageDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_delete_title)) },
        text = { Text(stringResource(R.string.chat_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.chat_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
