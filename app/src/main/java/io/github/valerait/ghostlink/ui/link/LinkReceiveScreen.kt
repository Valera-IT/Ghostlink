package io.github.valerait.ghostlink.ui.link

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valerait.ghostlink.core.ui.resources.Res
import io.github.valerait.ghostlink.core.ui.resources.action_back
import io.github.valerait.ghostlink.core.ui.resources.link_receive_countdown
import io.github.valerait.ghostlink.core.ui.resources.link_receive_expired
import io.github.valerait.ghostlink.core.ui.resources.link_receive_failed
import io.github.valerait.ghostlink.core.ui.resources.link_receive_new_code
import io.github.valerait.ghostlink.core.ui.resources.link_receive_qr_description
import io.github.valerait.ghostlink.core.ui.resources.link_receive_signing_in
import io.github.valerait.ghostlink.core.ui.resources.link_receive_step_1
import io.github.valerait.ghostlink.core.ui.resources.link_receive_step_2
import io.github.valerait.ghostlink.core.ui.resources.link_receive_step_3
import io.github.valerait.ghostlink.core.ui.resources.link_receive_steps_title
import io.github.valerait.ghostlink.core.ui.resources.link_receive_title
import io.github.valerait.ghostlink.ui.mycode.QrCard
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkReceiveScreen(
    onBack: () -> Unit,
    viewModel: LinkReceiveViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.link_receive_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val current = state) {
                LinkReceiveState.Preparing, LinkReceiveState.SigningIn -> {
                    Spacer(Modifier.height(96.dp))
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    if (current == LinkReceiveState.SigningIn) Hint(stringResource(Res.string.link_receive_signing_in))
                }

                is LinkReceiveState.ShowCode -> {
                    QrCard(
                        content = current.link,
                        contentDescription = stringResource(Res.string.link_receive_qr_description),
                        modifier = Modifier.widthIn(max = 360.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Countdown(current.expiresAt)
                    Spacer(Modifier.height(20.dp))
                    Steps()
                }

                LinkReceiveState.Expired, LinkReceiveState.Failed -> {
                    Spacer(Modifier.height(96.dp))
                    Text(
                        text =
                            stringResource(
                                if (current ==
                                    LinkReceiveState.Expired
                                ) {
                                    Res.string.link_receive_expired
                                } else {
                                    Res.string.link_receive_failed
                                },
                            ),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = viewModel::restart) { Text(stringResource(Res.string.link_receive_new_code)) }
                }
            }
        }
    }
}

@Composable
private fun Countdown(expiresAt: Instant) {
    val secondsLeft by produceState(initialValue = secondsUntil(expiresAt), expiresAt) {
        while (value > 0) {
            delay(1.seconds)
            value = secondsUntil(expiresAt)
        }
    }
    Text(
        text = stringResource(Res.string.link_receive_countdown, secondsLeft / 60, (secondsLeft % 60).toString().padStart(2, '0')),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun secondsUntil(at: Instant): Int = (at - Clock.System.now()).inWholeSeconds.coerceAtLeast(0).toInt()

@Composable
private fun Steps() {
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.link_receive_steps_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Hint(stringResource(Res.string.link_receive_step_1), TextAlign.Start)
        Hint(stringResource(Res.string.link_receive_step_2), TextAlign.Start)
        Hint(stringResource(Res.string.link_receive_step_3), TextAlign.Start)
    }
}

@Composable
private fun Hint(
    text: String,
    align: TextAlign = TextAlign.Center,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}
