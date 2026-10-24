package io.github.valerait.ghostlink.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.valerait.ghostlink.core.platform.rememberCopyToClipboard
import io.github.valerait.ghostlink.core.ui.resources.Res
import io.github.valerait.ghostlink.core.ui.resources.action_done
import io.github.valerait.ghostlink.core.ui.resources.recovery_code_body
import io.github.valerait.ghostlink.core.ui.resources.recovery_code_copied
import io.github.valerait.ghostlink.core.ui.resources.recovery_code_copy
import io.github.valerait.ghostlink.core.ui.resources.recovery_code_warning
import org.jetbrains.compose.resources.stringResource

/** Показывает ключ восстановления: 8 групп по 5 символов, по 4 группы в строке. */
@Composable
fun RecoveryCodeDialog(
    code: String,
    title: String,
    onDismiss: () -> Unit,
) {
    val copy = rememberCopyToClipboard(sensitive = true)
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(stringResource(Res.string.recovery_code_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SelectionContainer {
                        Text(
                            text = code.split('-').chunked(4).joinToString("\n") { it.joinToString("-") },
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.recovery_code_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_done)) } },
        dismissButton = {
            TextButton(onClick = {
                copy(code)
                copied = true
            }) { Text(stringResource(if (copied) Res.string.recovery_code_copied else Res.string.recovery_code_copy)) }
        },
    )
}
