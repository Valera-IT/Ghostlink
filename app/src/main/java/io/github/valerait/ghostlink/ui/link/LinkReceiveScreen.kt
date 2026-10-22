package io.github.valerait.ghostlink.ui.link

import android.annotation.SuppressLint
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valerait.ghostlink.R
import io.github.valerait.ghostlink.ui.mycode.QrCard
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkReceiveScreen(
    onBack: () -> Unit,
    viewModel: LinkReceiveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.link_receive_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
                    if (current == LinkReceiveState.SigningIn) Hint(stringResource(R.string.link_receive_signing_in))
                }

                is LinkReceiveState.ShowCode -> {
                    QrCard(
                        content = current.link,
                        contentDescription = stringResource(R.string.link_receive_qr_description),
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
                                if (current == LinkReceiveState.Expired) R.string.link_receive_expired else R.string.link_receive_failed,
                            ),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = viewModel::restart) { Text(stringResource(R.string.link_receive_new_code)) }
                }
            }
        }
    }
}

// Ложное срабатывание lint: value присваивается внутри лямбды (до обновления Compose на этапе 3.1).
@SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
private fun Countdown(expiresAt: Long) {
    val secondsLeft by produceState(initialValue = secondsUntil(expiresAt), expiresAt) {
        while (value > 0) {
            delay(1_000)
            value = secondsUntil(expiresAt)
        }
    }
    Text(
        text = stringResource(R.string.link_receive_countdown, secondsLeft / 60, secondsLeft % 60),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun secondsUntil(at: Long): Int = ((at - System.currentTimeMillis()) / 1000).coerceAtLeast(0).toInt()

@Composable
private fun Steps() {
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.link_receive_steps_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Hint(stringResource(R.string.link_receive_step_1), TextAlign.Start)
        Hint(stringResource(R.string.link_receive_step_2), TextAlign.Start)
        Hint(stringResource(R.string.link_receive_step_3), TextAlign.Start)
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
