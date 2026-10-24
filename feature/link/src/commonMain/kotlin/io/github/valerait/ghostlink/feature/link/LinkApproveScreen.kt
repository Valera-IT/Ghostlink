package io.github.valerait.ghostlink.feature.link

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valerait.ghostlink.core.ui.platformIcon
import io.github.valerait.ghostlink.core.ui.platformLabel
import io.github.valerait.ghostlink.core.ui.resources.Res
import io.github.valerait.ghostlink.core.ui.resources.action_back
import io.github.valerait.ghostlink.core.ui.resources.action_cancel
import io.github.valerait.ghostlink.core.ui.resources.action_done
import io.github.valerait.ghostlink.core.ui.resources.error_generic
import io.github.valerait.ghostlink.core.ui.resources.link_approve_action
import io.github.valerait.ghostlink.core.ui.resources.link_approve_done_body
import io.github.valerait.ghostlink.core.ui.resources.link_approve_done_title
import io.github.valerait.ghostlink.core.ui.resources.link_approve_invalid_body
import io.github.valerait.ghostlink.core.ui.resources.link_approve_invalid_title
import io.github.valerait.ghostlink.core.ui.resources.link_approve_question
import io.github.valerait.ghostlink.core.ui.resources.link_approve_title
import io.github.valerait.ghostlink.core.ui.resources.link_approve_warning
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkApproveScreen(
    onClose: () -> Unit,
    viewModel: LinkApproveViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.link_approve_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
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
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val current = state) {
                LinkApproveUiState.Loading -> {
                    Spacer(Modifier.height(96.dp))
                    CircularProgressIndicator()
                }

                is LinkApproveUiState.Confirm -> {
                    Confirm(current, onApprove = { viewModel.onAction(LinkApproveAction.Approve) }, onCancel = onClose)
                }

                LinkApproveUiState.Done -> {
                    Outcome(
                        icon = Icons.Outlined.CheckCircle,
                        title = stringResource(Res.string.link_approve_done_title),
                        body = stringResource(Res.string.link_approve_done_body),
                        onClose = onClose,
                    )
                }

                LinkApproveUiState.Invalid -> {
                    Outcome(
                        icon = Icons.Outlined.ErrorOutline,
                        title = stringResource(Res.string.link_approve_invalid_title),
                        body = stringResource(Res.string.link_approve_invalid_body),
                        onClose = onClose,
                    )
                }
            }
        }
    }
}

@Composable
private fun Confirm(
    state: LinkApproveUiState.Confirm,
    onApprove: () -> Unit,
    onCancel: () -> Unit,
) {
    Spacer(Modifier.height(24.dp))
    Icon(platformIcon(state.platform), contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(Res.string.link_approve_question),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(4.dp))
    Text(state.deviceName, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(
        text = platformLabel(state.platform),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(Res.string.link_approve_warning), style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (state.failed) {
        Text(
            text = stringResource(Res.string.error_generic),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
    Spacer(Modifier.height(24.dp))
    Button(onClick = onApprove, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        if (state.busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(Res.string.link_approve_action))
        }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onCancel, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.action_cancel))
    }
}

@Composable
private fun Outcome(
    icon: ImageVector,
    title: String,
    body: String,
    onClose: () -> Unit,
) {
    Spacer(Modifier.height(48.dp))
    Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onClose) { Text(stringResource(Res.string.action_done)) }
}
