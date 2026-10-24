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
import io.github.valerait.ghostlink.core.ui.resources.Res
import io.github.valerait.ghostlink.core.ui.resources.action_cancel
import io.github.valerait.ghostlink.core.ui.resources.chat_copy
import io.github.valerait.ghostlink.core.ui.resources.chat_delete
import io.github.valerait.ghostlink.core.ui.resources.chat_delete_body
import io.github.valerait.ghostlink.core.ui.resources.chat_delete_title
import io.github.valerait.ghostlink.core.ui.resources.chat_edit
import io.github.valerait.ghostlink.core.ui.resources.chat_reply
import org.jetbrains.compose.resources.stringResource

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
                text = { Text(stringResource(Res.string.chat_reply)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
                onClick = {
                    onDismiss()
                    actions.onReply()
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.chat_copy)) },
            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
            onClick = {
                onDismiss()
                actions.onCopy()
            },
        )
        if (mine && sent) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.chat_edit)) },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = {
                    onDismiss()
                    actions.onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.chat_delete), color = MaterialTheme.colorScheme.error) },
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
        title = { Text(stringResource(Res.string.chat_delete_title)) },
        text = { Text(stringResource(Res.string.chat_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.chat_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
