package io.github.valerait.ghostlink.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valerait.ghostlink.R
import io.github.valerait.ghostlink.data.settings.ThemeId
import io.github.valerait.ghostlink.data.settings.ThemePreferences
import io.github.valerait.ghostlink.ui.theme.GhostlinkTheme
import io.github.valerait.ghostlink.ui.theme.LocalChatStyle
import org.koin.compose.viewmodel.koinViewModel

class ThemeViewModel(
    private val prefs: ThemePreferences,
) : ViewModel() {
    val theme = prefs.theme

    fun select(theme: ThemeId) = prefs.setTheme(theme)
}

@get:StringRes
val ThemeId.title: Int
    get() =
        when (this) {
            ThemeId.TOXIC_YELLOW -> R.string.theme_toxic_yellow
            ThemeId.NEON_SUNSET -> R.string.theme_neon_sunset
            ThemeId.ICEWIRE -> R.string.theme_icewire
            ThemeId.ACID_CIRCUIT -> R.string.theme_acid_circuit
            ThemeId.AMBER_ALERT -> R.string.theme_amber_alert
            ThemeId.CUSTOM_SPECTRUM -> R.string.theme_custom_spectrum
        }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    onBack: () -> Unit,
    viewModel: ThemeViewModel = koinViewModel(),
) {
    val current by viewModel.theme.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.theme_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            items(ThemeId.entries, key = { it.name }) { theme ->
                ThemeCard(theme, selected = theme == current, onClick = { viewModel.select(theme) })
            }
        }
    }
}

/** Карточка-превью: мини-чат, нарисованный в самой теме, как на экране выбора тем в Telegram. */
@Composable
private fun ThemeCard(
    theme: ThemeId,
    selected: Boolean,
    onClick: () -> Unit,
) {
    GhostlinkTheme(theme = theme) {
        val colors = MaterialTheme.colorScheme
        val chatStyle = LocalChatStyle.current
        Column(
            Modifier
                .clip(chatStyle.panelShape)
                .border(
                    BorderStroke(if (selected) 3.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant),
                    chatStyle.panelShape,
                ).background(colors.background)
                .clickable(onClick = onClick)
                .padding(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(colors.primary))
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(10.dp).background(colors.secondary))
                Spacer(Modifier.weight(1f))
                if (selected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.theme_selected),
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            PreviewBubble(stringResource(R.string.theme_preview_incoming), mine = false)
            Spacer(Modifier.height(6.dp))
            PreviewBubble(stringResource(R.string.theme_preview_outgoing), mine = true)
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(theme.title),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PreviewBubble(
    text: String,
    mine: Boolean,
) {
    val chatStyle = LocalChatStyle.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (mine) chatStyle.outgoing else chatStyle.incoming,
            contentColor = if (mine) chatStyle.onOutgoing else chatStyle.onIncoming,
            shape = if (mine) chatStyle.outgoingShape else chatStyle.incomingShape,
            border = if (mine) null else BorderStroke(1.dp, chatStyle.incomingBorder),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
