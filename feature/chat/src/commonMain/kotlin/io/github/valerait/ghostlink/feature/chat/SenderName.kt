package io.github.valerait.ghostlink.feature.chat

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Имя автора над чужим сообщением в группе. */
@Composable
internal fun SenderName(name: String?) {
    if (name == null) return
    Text(
        text = name,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        modifier = Modifier.padding(bottom = 2.dp),
    )
}
