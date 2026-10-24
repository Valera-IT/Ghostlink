package io.github.valerait.ghostlink.ui.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valerait.ghostlink.core.model.Device
import io.github.valerait.ghostlink.core.ui.formatChatListTime
import io.github.valerait.ghostlink.core.ui.rememberNow
import io.github.valerait.ghostlink.core.ui.resources.Res
import io.github.valerait.ghostlink.core.ui.resources.action_back
import io.github.valerait.ghostlink.core.ui.resources.action_cancel
import io.github.valerait.ghostlink.core.ui.resources.devices_active_now
import io.github.valerait.ghostlink.core.ui.resources.devices_add
import io.github.valerait.ghostlink.core.ui.resources.devices_add_body
import io.github.valerait.ghostlink.core.ui.resources.devices_last_active
import io.github.valerait.ghostlink.core.ui.resources.devices_new_key_title
import io.github.valerait.ghostlink.core.ui.resources.devices_others
import io.github.valerait.ghostlink.core.ui.resources.devices_remove
import io.github.valerait.ghostlink.core.ui.resources.devices_remove_body
import io.github.valerait.ghostlink.core.ui.resources.devices_remove_title
import io.github.valerait.ghostlink.core.ui.resources.devices_sign_out_others
import io.github.valerait.ghostlink.core.ui.resources.devices_sign_out_others_body
import io.github.valerait.ghostlink.core.ui.resources.devices_sign_out_others_confirm
import io.github.valerait.ghostlink.core.ui.resources.devices_sign_out_others_hint
import io.github.valerait.ghostlink.core.ui.resources.devices_sign_out_others_title
import io.github.valerait.ghostlink.core.ui.resources.devices_this
import io.github.valerait.ghostlink.core.ui.resources.devices_title
import io.github.valerait.ghostlink.ui.recovery.RecoveryCodeDialog
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onBack: () -> Unit,
    onScan: () -> Unit,
    viewModel: DevicesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now by rememberNow()
    val snackbar = remember { SnackbarHostState() }
    var removing by remember { mutableStateOf<Device?>(null) }
    var confirmSignOutOthers by rememberSaveable { mutableStateOf(false) }

    val messageText = state.message?.let { stringResource(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.onMessageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.devices_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
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
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(Res.string.devices_add_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.devices_add))
                }
            }
            HorizontalDivider()

            SectionTitle(stringResource(Res.string.devices_this))
            state.thisDevice?.let { DeviceRow(it, subtitle = stringResource(Res.string.devices_active_now)) }

            if (state.others.isNotEmpty()) {
                SectionTitle(stringResource(Res.string.devices_others))
                state.others.forEach { device ->
                    DeviceRow(
                        device = device,
                        subtitle =
                            device.lastActiveAt
                                ?.let { stringResource(Res.string.devices_last_active, formatChatListTime(it, now)) }
                                .orEmpty(),
                        onRemove = { removing = device }.takeUnless { state.busy },
                    )
                }
                Spacer(Modifier.height(8.dp))
                ListItem(
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    headlineContent = { Text(stringResource(Res.string.devices_sign_out_others), color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text(stringResource(Res.string.devices_sign_out_others_hint)) },
                    modifier = Modifier.clickable(enabled = !state.busy) { confirmSignOutOthers = true },
                )
            }
        }
    }

    removing?.let { device ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(Res.string.devices_remove_title, device.name)) },
            text = { Text(stringResource(Res.string.devices_remove_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(device.id)
                    removing = null
                }) { Text(stringResource(Res.string.devices_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    if (confirmSignOutOthers) {
        AlertDialog(
            onDismissRequest = { confirmSignOutOthers = false },
            title = { Text(stringResource(Res.string.devices_sign_out_others_title)) },
            text = { Text(stringResource(Res.string.devices_sign_out_others_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.signOutOthers()
                    confirmSignOutOthers = false
                }) { Text(stringResource(Res.string.devices_sign_out_others_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOutOthers = false }) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }
    state.newRecoveryCode?.let { code ->
        RecoveryCodeDialog(
            code = code,
            title = stringResource(Res.string.devices_new_key_title),
            onDismiss = viewModel::onRecoveryCodeShown,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun DeviceRow(
    device: Device,
    subtitle: String,
    onRemove: (() -> Unit)? = null,
) {
    ListItem(
        leadingContent = { Icon(platformIcon(device.platform), contentDescription = null) },
        headlineContent = { Text(device.name) },
        supportingContent = { Text("${platformLabel(device.platform)} · $subtitle".trimEnd(' ', '·')) },
        trailingContent =
            onRemove?.let {
                {
                    IconButton(onClick = it) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.devices_remove))
                    }
                }
            },
    )
}
