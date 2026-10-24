package io.github.valerait.ghostlink.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.valerait.ghostlink.app.AppGraph
import io.github.valerait.ghostlink.app.AppLanguage
import io.github.valerait.ghostlink.app.LanguageStore
import io.github.valerait.ghostlink.app.ThemeId
import io.github.valerait.ghostlink.app.ThemeStore
import io.github.valerait.ghostlink.app.strings
import io.github.valerait.ghostlink.app.title
import io.github.valerait.ghostlink.crypto.LinkCrypto
import io.github.valerait.ghostlink.data.requireUserId
import io.github.valerait.ghostlink.model.Device
import io.github.valerait.ghostlink.model.LinkCode
import io.github.valerait.ghostlink.platform.DeviceInfo
import io.github.valerait.ghostlink.platform.logWarning
import io.github.valerait.ghostlink.platform.nowMillis
import io.github.valerait.ghostlink.ui.common.Avatar
import io.github.valerait.ghostlink.ui.common.BackButton
import io.github.valerait.ghostlink.ui.common.formatChatListTime
import io.github.valerait.ghostlink.ui.common.rememberNow
import io.github.valerait.ghostlink.ui.theme.GhostlinkTheme
import io.github.valerait.ghostlink.ui.theme.LocalChatStyle
import io.github.valerait.ghostlink.ui.theme.LocalThemeId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val ONLINE_WINDOW_MS = 5 * 60_000L

// ---------- Настройки ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onDevices: () -> Unit, onTheme: () -> Unit) {
    val me = remember { AppGraph.auth.requireUserId() }
    val user by remember { AppGraph.users.observeUser(me) }.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var pickingLanguage by rememberSaveable { mutableStateOf(false) }
    var recoveryCode by remember { mutableStateOf<String?>(null) }
    val name = user?.name.orEmpty()

    Scaffold(topBar = { TopAppBar(title = { Text(strings.settings) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().clickable { renaming = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(name, me, size = 72.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.headlineSmall)
                    Text(strings.tapToRename, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.Edit, contentDescription = strings.rename)
            }
            HorizontalDivider()
            SectionTitle(strings.appearance)
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                headlineContent = { Text(strings.theme) },
                supportingContent = { Text(LocalThemeId.current.title) },
                modifier = Modifier.clickable(onClick = onTheme),
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Language, contentDescription = null) },
                headlineContent = { Text(strings.language) },
                supportingContent = { Text(LanguageStore.language.title) },
                modifier = Modifier.clickable { pickingLanguage = true },
            )
            SectionTitle(strings.account)
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Devices, contentDescription = null) },
                headlineContent = { Text(strings.devices) },
                supportingContent = { Text(strings.devicesHint) },
                modifier = Modifier.clickable(onClick = onDevices),
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Key, contentDescription = null) },
                headlineContent = { Text(strings.recoveryKey) },
                supportingContent = { Text(strings.recoveryKeyHint) },
                modifier = Modifier.clickable {
                    scope.launch {
                        recoveryCode = runCatching { (AppGraph.auth.accountKey.value ?: AppGraph.auth.ensureAccountKey()).toRecoveryCode() }
                            .onFailure { logWarning("Settings", "No recovery key", it) }
                            .getOrNull()
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            ListItem(
                leadingContent = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                headlineContent = { Text(strings.signOut, color = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { confirmSignOut = true },
            )
            Text(
                "Ghostlink ${DeviceInfo.appVersion}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    recoveryCode?.let { RecoveryCodeDialog(it, strings.recoveryKey) { recoveryCode = null } }
    if (renaming) {
        RenameDialog(name, onDismiss = { renaming = false }) { newName ->
            renaming = false
            scope.launch { runCatching { AppGraph.users.saveProfile(me, newName.trim().take(MAX_NAME_LENGTH)) } }
        }
    }
    if (pickingLanguage) {
        LanguageDialog(onDismiss = { pickingLanguage = false }) {
            pickingLanguage = false
            LanguageStore.set(it)
        }
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(strings.signOutTitle) },
            text = { Text(strings.signOutBody) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    AppGraph.scope.launch {
                        runCatching { AppGraph.users.setOnline(me, false) }
                        runCatching { AppGraph.devices.removeDevice(me, DeviceInfo.id) }
                        AppGraph.auth.signOut()
                    }
                }) { Text(strings.signOut, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(strings.cancel) } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun LanguageDialog(onDismiss: () -> Unit, onSelect: (AppLanguage) -> Unit) {
    val current = LanguageStore.language
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.language) },
        text = {
            Column(Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { language ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = language == current, role = Role.RadioButton, onClick = { onSelect(language) })
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = language == current, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(language.title)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.rename) },
        text = { OutlinedTextField(value, { value = it.take(MAX_NAME_LENGTH) }, singleLine = true, label = { Text(strings.nameLabel) }) },
        confirmButton = { TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text(strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

@Composable
fun RecoveryCodeDialog(code: String, title: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.recoveryCodeBody)
                SelectionContainer {
                    Text(code, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
                }
                Text(strings.recoveryCodeWarning, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.done) } },
        dismissButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(code))
                copied = true
            }) { Text(if (copied) strings.copied else strings.copy) }
        },
    )
}

// ---------- Выбор темы ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(onBack: () -> Unit) {
    val current by ThemeStore.theme.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(strings.themeTitle) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            items(ThemeId.entries, key = { it.name }) { theme ->
                ThemeCard(theme, selected = theme == current, onClick = { ThemeStore.set(theme) })
            }
        }
    }
}

/** Карточка-превью: мини-чат, нарисованный в самой теме. */
@Composable
private fun ThemeCard(theme: ThemeId, selected: Boolean, onClick: () -> Unit) {
    GhostlinkTheme(theme = theme) {
        val colors = MaterialTheme.colorScheme
        val chatStyle = LocalChatStyle.current
        Column(
            Modifier
                .clip(chatStyle.panelShape)
                .border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant), chatStyle.panelShape)
                .background(colors.background)
                .clickable(onClick = onClick)
                .padding(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(colors.primary))
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(10.dp).background(colors.secondary))
                Spacer(Modifier.weight(1f))
                if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = strings.themeSelected, tint = colors.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(8.dp))
            PreviewBubble(strings.previewIncoming, mine = false)
            Spacer(Modifier.height(6.dp))
            PreviewBubble(strings.previewOutgoing, mine = true)
            Spacer(Modifier.height(10.dp))
            Text(theme.title, style = MaterialTheme.typography.titleSmall, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PreviewBubble(text: String, mine: Boolean) {
    val chatStyle = LocalChatStyle.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (mine) chatStyle.outgoing else chatStyle.incoming,
            contentColor = if (mine) chatStyle.onOutgoing else chatStyle.onIncoming,
            shape = if (mine) chatStyle.outgoingShape else chatStyle.incomingShape,
            border = if (mine) null else BorderStroke(1.dp, chatStyle.incomingBorder),
        ) {
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
        }
    }
}

// ---------- Устройства ----------

class DevicesViewModel : ViewModel() {
    private val me = AppGraph.auth.requireUserId()
    val devices = AppGraph.devices.observeDevices(me)
    val busy = MutableStateFlow(false)
    val newRecoveryCode = MutableStateFlow<String?>(null)

    fun remove(deviceId: String) = launchBusy { AppGraph.devices.removeDevice(me, deviceId) }

    /** Новый секрет ключа: Firebase отзывает сессии остальных устройств, а реестр заставляет их выйти сразу. */
    fun signOutOthers() = launchBusy {
        val key = AppGraph.auth.rotateKey()
        newRecoveryCode.value = key.toRecoveryCode()
        AppGraph.devices.observeDevices(me).first()
            .filter { it.id != DeviceInfo.id }
            .forEach { runCatching { AppGraph.devices.removeDevice(me, it.id) } }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                logWarning("Devices", "Action failed", e)
            } finally {
                busy.value = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(onBack: () -> Unit, onScan: () -> Unit, viewModel: DevicesViewModel = viewModel { DevicesViewModel() }) {
    val list by viewModel.devices.collectAsStateWithLifecycle(emptyList())
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val newCode by viewModel.newRecoveryCode.collectAsStateWithLifecycle()
    val now by rememberNow()
    var toRemove by remember { mutableStateOf<Device?>(null) }
    var confirmSignOutOthers by remember { mutableStateOf(false) }
    val thisDevice = list.firstOrNull { it.id == DeviceInfo.id } ?: Device(DeviceInfo.id, DeviceInfo.name, DeviceInfo.platform)
    val others = list.filter { it.id != DeviceInfo.id }

    Scaffold(topBar = { TopAppBar(title = { Text(strings.devices) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    strings.devicesAddBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                    Text("  " + strings.linkDeviceAction)
                }
            }
            SectionTitle(strings.thisDevice)
            DeviceRow(thisDevice, now, onRemove = null)
            if (others.isNotEmpty()) {
                SectionTitle(strings.otherDevices)
                others.forEach { DeviceRow(it, now, onRemove = { toRemove = it }) }
                Spacer(Modifier.height(8.dp))
                ListItem(
                    headlineContent = { Text(strings.signOutOthers, color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text(strings.signOutOthersHint) },
                    modifier = Modifier.clickable(enabled = !busy) { confirmSignOutOthers = true },
                )
            }
            if (busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
        }
    }

    toRemove?.let { device ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text(strings.removeDeviceTitle(device.name)) },
            text = { Text(strings.removeDeviceBody) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(device.id)
                    toRemove = null
                }) { Text(strings.delete, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text(strings.cancel) } },
        )
    }
    if (confirmSignOutOthers) {
        AlertDialog(
            onDismissRequest = { confirmSignOutOthers = false },
            title = { Text(strings.signOutOthersTitle) },
            text = { Text(strings.signOutOthersBody) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOutOthers = false
                    viewModel.signOutOthers()
                }) { Text(strings.signOutEverywhere, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOutOthers = false }) { Text(strings.cancel) } },
        )
    }
    newCode?.let { RecoveryCodeDialog(it, strings.newRecoveryKey) { viewModel.newRecoveryCode.value = null } }
}

@Composable
private fun DeviceRow(device: Device, now: Long, onRemove: (() -> Unit)?) {
    val lastActive = device.lastActiveAt
    val active = lastActive != null && now - lastActive < ONLINE_WINDOW_MS
    ListItem(
        leadingContent = { Icon(if (device.platform == "ios") Icons.Outlined.PhoneIphone else Icons.Outlined.Devices, contentDescription = null) },
        headlineContent = { Text(device.name) },
        supportingContent = {
            Text(
                when {
                    onRemove == null || active -> strings.activeNow
                    lastActive != null -> strings.activeAt(formatChatListTime(lastActive, now))
                    else -> device.platform
                },
            )
        },
        trailingContent = onRemove?.let { { IconButton(onClick = it) { Icon(Icons.Outlined.Delete, contentDescription = strings.removeDevice) } } },
    )
}

// ---------- Подтверждение входа на новом устройстве ----------

sealed interface LinkApproveState {
    data object Loading : LinkApproveState
    data class Confirm(val deviceName: String, val platform: String, val busy: Boolean = false, val failed: Boolean = false) : LinkApproveState
    data object Done : LinkApproveState
    data object Invalid : LinkApproveState
}

/** Шифрует ключ аккаунта публичным ключом из QR нового устройства и кладёт его в заявку. */
class LinkApproveViewModel(private val code: LinkCode) : ViewModel() {
    private val _state = MutableStateFlow<LinkApproveState>(LinkApproveState.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val request = runCatching { AppGraph.links.getLinkRequest(code.requestId) }
                .onFailure { logWarning("LinkApprove", "Failed to load link request", it) }
                .getOrNull()
            _state.value = if (request == null || request.payload != null || request.isExpired(nowMillis())) {
                LinkApproveState.Invalid
            } else {
                LinkApproveState.Confirm(request.deviceName, request.platform)
            }
        }
    }

    fun approve() {
        val confirm = _state.value as? LinkApproveState.Confirm ?: return
        if (confirm.busy) return
        _state.value = confirm.copy(busy = true, failed = false)
        viewModelScope.launch {
            _state.value = try {
                val key = AppGraph.auth.accountKey.value ?: AppGraph.auth.ensureAccountKey()
                AppGraph.links.approveLinkRequest(code.requestId, LinkCrypto.encrypt(code.publicKey, code.requestId, key.bytes))
                LinkApproveState.Done
            } catch (e: Exception) {
                logWarning("LinkApprove", "Failed to approve link request", e)
                confirm.copy(busy = false, failed = true)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkApproveScreen(code: LinkCode, onClose: () -> Unit, viewModel: LinkApproveViewModel = viewModel(key = code.requestId) { LinkApproveViewModel(code) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(strings.linkApproveTitle) }, navigationIcon = { BackButton(onClose) }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (val s = state) {
                LinkApproveState.Loading -> CircularProgressIndicator()
                is LinkApproveState.Confirm -> {
                    Text(strings.linkApproveQuestion, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("${s.deviceName} (${s.platform})", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        strings.linkApproveWarning,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    if (s.failed) {
                        Spacer(Modifier.height(8.dp))
                        Text(strings.linkApproveFailed, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = viewModel::approve, enabled = !s.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        if (s.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text(strings.allowSignIn)
                    }
                    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(strings.cancel) }
                }
                LinkApproveState.Done -> {
                    Message(strings.done, strings.linkApproveDoneBody)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onClose) { Text(strings.done) }
                }
                LinkApproveState.Invalid -> {
                    Message(strings.linkApproveInvalidTitle, strings.linkApproveInvalidBody)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onClose) { Text(strings.close) }
                }
            }
        }
    }
}
