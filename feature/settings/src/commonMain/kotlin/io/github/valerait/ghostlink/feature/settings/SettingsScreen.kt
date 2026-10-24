package io.github.valerait.ghostlink.feature.settings

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valerait.ghostlink.core.designsystem.LocalThemeId
import io.github.valerait.ghostlink.core.model.User
import io.github.valerait.ghostlink.core.ui.AppLanguage
import io.github.valerait.ghostlink.core.ui.Avatar
import io.github.valerait.ghostlink.core.ui.DemoModeBanner
import io.github.valerait.ghostlink.core.ui.RecoveryCodeDialog
import io.github.valerait.ghostlink.core.ui.resources.Res
import io.github.valerait.ghostlink.core.ui.resources.action_back
import io.github.valerait.ghostlink.core.ui.resources.action_cancel
import io.github.valerait.ghostlink.core.ui.resources.action_save
import io.github.valerait.ghostlink.core.ui.resources.language_english
import io.github.valerait.ghostlink.core.ui.resources.language_russian
import io.github.valerait.ghostlink.core.ui.resources.language_system
import io.github.valerait.ghostlink.core.ui.resources.onboarding_name_label
import io.github.valerait.ghostlink.core.ui.resources.settings_account
import io.github.valerait.ghostlink.core.ui.resources.settings_appearance
import io.github.valerait.ghostlink.core.ui.resources.settings_devices
import io.github.valerait.ghostlink.core.ui.resources.settings_devices_hint
import io.github.valerait.ghostlink.core.ui.resources.settings_language
import io.github.valerait.ghostlink.core.ui.resources.settings_recovery_key
import io.github.valerait.ghostlink.core.ui.resources.settings_recovery_key_hint
import io.github.valerait.ghostlink.core.ui.resources.settings_rename
import io.github.valerait.ghostlink.core.ui.resources.settings_sign_out
import io.github.valerait.ghostlink.core.ui.resources.settings_sign_out_body
import io.github.valerait.ghostlink.core.ui.resources.settings_sign_out_title
import io.github.valerait.ghostlink.core.ui.resources.settings_tap_to_rename
import io.github.valerait.ghostlink.core.ui.resources.settings_theme
import io.github.valerait.ghostlink.core.ui.resources.settings_title
import io.github.valerait.ghostlink.core.ui.resources.settings_version
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onDevices: () -> Unit,
    onTheme: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var pickingLanguage by rememberSaveable { mutableStateOf(false) }
    val language by viewModel.language.collectAsStateWithLifecycle()

    val messageText = state.message?.let { stringResource(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.onAction(SettingsAction.MessageShown)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_title)) },
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
            if (state.demoMode) DemoModeBanner(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { renaming = true }
                        .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(state.name, state.userId.value, size = 72.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = stringResource(Res.string.settings_tap_to_rename),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(Res.string.settings_rename))
            }
            HorizontalDivider()

            Text(
                text = stringResource(Res.string.settings_appearance),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                headlineContent = { Text(stringResource(Res.string.settings_theme)) },
                supportingContent = { Text(stringResource(LocalThemeId.current.title)) },
                modifier = Modifier.clickable(onClick = onTheme),
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Language, contentDescription = null) },
                headlineContent = { Text(stringResource(Res.string.settings_language)) },
                supportingContent = { Text(stringResource(language.title)) },
                modifier = Modifier.clickable { pickingLanguage = true },
            )

            Text(
                text = stringResource(Res.string.settings_account),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Devices, contentDescription = null) },
                headlineContent = { Text(stringResource(Res.string.settings_devices)) },
                supportingContent = { Text(stringResource(Res.string.settings_devices_hint)) },
                modifier = Modifier.clickable(onClick = onDevices),
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Key, contentDescription = null) },
                headlineContent = { Text(stringResource(Res.string.settings_recovery_key)) },
                supportingContent = { Text(stringResource(Res.string.settings_recovery_key_hint)) },
                modifier = Modifier.clickable(enabled = !state.busy, onClick = { viewModel.onAction(SettingsAction.ShowRecoveryKey) }),
            )

            Spacer(Modifier.height(8.dp))
            ListItem(
                leadingContent = {
                    Icon(
                        Icons.AutoMirrored.Outlined.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                headlineContent = { Text(stringResource(Res.string.settings_sign_out), color = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable(enabled = !state.busy) { confirmSignOut = true },
            )

            Text(
                text = stringResource(Res.string.settings_version, state.versionName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    state.recoveryCode?.let { code ->
        RecoveryCodeDialog(
            code = code,
            title = stringResource(Res.string.settings_recovery_key),
            onDismiss = { viewModel.onAction(SettingsAction.RecoveryKeyShown) },
        )
    }
    if (renaming) {
        RenameDialog(
            initial = state.name,
            onDismiss = { renaming = false },
            onConfirm = {
                viewModel.onAction(SettingsAction.Rename(it))
                renaming = false
            },
        )
    }
    if (pickingLanguage) {
        LanguageDialog(
            current = language,
            onDismiss = { pickingLanguage = false },
            onSelect = {
                pickingLanguage = false
                viewModel.onAction(SettingsAction.SelectLanguage(it))
            },
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(Res.string.settings_sign_out_title)) },
            text = {
                Text(stringResource(Res.string.settings_sign_out_body))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.onAction(SettingsAction.SignOut)
                }) { Text(stringResource(Res.string.settings_sign_out), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }
}

private val AppLanguage.title: StringResource
    get() =
        when (this) {
            AppLanguage.SYSTEM -> Res.string.language_system
            AppLanguage.RUSSIAN -> Res.string.language_russian
            AppLanguage.ENGLISH -> Res.string.language_english
        }

@Composable
private fun LanguageDialog(
    current: AppLanguage,
    onDismiss: () -> Unit,
    onSelect: (AppLanguage) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_language)) },
        text = {
            Column(Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { language ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = language == current,
                                    role = Role.RadioButton,
                                    onClick = { onSelect(language) },
                                ).padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = language == current, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(language.title))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_rename)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(User.NAME_MAX_LENGTH) },
                singleLine = true,
                label = { Text(stringResource(Res.string.onboarding_name_label)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
