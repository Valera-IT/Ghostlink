package io.github.valerait.ghostlink.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valerait.ghostlink.BuildConfig
import io.github.valerait.ghostlink.R
import io.github.valerait.ghostlink.ui.common.Avatar
import io.github.valerait.ghostlink.ui.common.DemoModeBanner
import io.github.valerait.ghostlink.ui.common.requestGoogleIdToken
import io.github.valerait.ghostlink.ui.onboarding.MAX_NAME_LENGTH
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    val messageText = state.message?.let { stringResource(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.onMessageShown()
        }
    }

    val linkGoogle: () -> Unit = {
        viewModel.webClientId?.let { clientId ->
            scope.launch {
                val token = runCatching { requestGoogleIdToken(context, clientId) }.getOrNull()
                if (token != null) viewModel.linkGoogle(token)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.demoMode) DemoModeBanner(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { renaming = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(state.name, state.userId, size = 72.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = stringResource(R.string.settings_tap_to_rename),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.settings_rename))
            }
            HorizontalDivider()

            Text(
                text = stringResource(R.string.settings_account),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
            )
            if (state.isAnonymous) {
                AnonymousAccountCard(
                    googleAvailable = state.googleAvailable,
                    busy = state.busy,
                    onLinkGoogle = linkGoogle,
                )
            } else {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text(stringResource(R.string.settings_google_linked_title)) },
                    supportingContent = { state.email?.let { Text(it) } },
                )
            }

            Spacer(Modifier.height(8.dp))
            ListItem(
                leadingContent = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                headlineContent = { Text(stringResource(R.string.settings_sign_out), color = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable(enabled = !state.busy) { confirmSignOut = true },
            )

            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (renaming) {
        RenameDialog(
            initial = state.name,
            onDismiss = { renaming = false },
            onConfirm = {
                viewModel.rename(it)
                renaming = false
            },
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.settings_sign_out_title)) },
            text = {
                Text(
                    stringResource(
                        if (state.isAnonymous) R.string.settings_sign_out_anonymous_warning else R.string.settings_sign_out_body,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.signOut()
                }) { Text(stringResource(R.string.settings_sign_out), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun AnonymousAccountCard(googleAvailable: Boolean, busy: Boolean, onLinkGoogle: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_anonymous_title), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_anonymous_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onLinkGoogle, enabled = googleAvailable && !busy) {
                Text(stringResource(R.string.settings_link_google))
            }
            if (!googleAvailable) {
                Text(
                    text = stringResource(R.string.settings_google_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_rename)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(MAX_NAME_LENGTH) },
                singleLine = true,
                label = { Text(stringResource(R.string.onboarding_name_label)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
