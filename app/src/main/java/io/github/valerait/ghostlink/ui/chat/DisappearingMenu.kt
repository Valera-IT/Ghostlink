package io.github.valerait.ghostlink.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.valerait.ghostlink.core.model.DisappearingTimer
import io.github.valerait.ghostlink.core.ui.resources.Res
import io.github.valerait.ghostlink.core.ui.resources.disappearing_day
import io.github.valerait.ghostlink.core.ui.resources.disappearing_hour
import io.github.valerait.ghostlink.core.ui.resources.disappearing_off
import io.github.valerait.ghostlink.core.ui.resources.disappearing_title
import io.github.valerait.ghostlink.core.ui.resources.disappearing_week
import org.jetbrains.compose.resources.stringResource

/** Кнопка таймера исчезающих сообщений в шапке чата; подсвечена, пока таймер включён. */
@Composable
fun DisappearingMenu(
    current: DisappearingTimer?,
    onSelect: (DisappearingTimer?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Outlined.Timer,
                contentDescription = stringResource(Res.string.disappearing_title),
                tint = if (current != null) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.disappearing_title), style = MaterialTheme.typography.labelLarge) },
                onClick = {},
                enabled = false,
            )
            (listOf(null) + DisappearingTimer.entries).forEach { timer ->
                DropdownMenuItem(
                    text = { Text(disappearingLabel(timer)) },
                    trailingIcon = { if (timer == current) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = {
                        open = false
                        if (timer != current) onSelect(timer)
                    },
                )
            }
        }
    }
}

@Composable
fun disappearingLabel(timer: DisappearingTimer?): String =
    stringResource(
        when (timer) {
            null -> Res.string.disappearing_off
            DisappearingTimer.HOUR -> Res.string.disappearing_hour
            DisappearingTimer.DAY -> Res.string.disappearing_day
            DisappearingTimer.WEEK -> Res.string.disappearing_week
        },
    )
