package io.github.valerait.ghostlink.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import io.github.valerait.ghostlink.app.AppGraph
import io.github.valerait.ghostlink.app.strings
import io.github.valerait.ghostlink.crypto.LinkCrypto
import io.github.valerait.ghostlink.data.InvalidAccountKeyException
import io.github.valerait.ghostlink.model.AccountKey
import io.github.valerait.ghostlink.model.LINK_TTL_MS
import io.github.valerait.ghostlink.model.LinkCode
import io.github.valerait.ghostlink.platform.DeviceInfo
import io.github.valerait.ghostlink.platform.logWarning
import io.github.valerait.ghostlink.platform.nowMillis
import io.github.valerait.ghostlink.ui.common.BackButton
import io.github.valerait.ghostlink.ui.common.GhostLogo
import io.github.valerait.ghostlink.ui.theme.LogoTextStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

const val MAX_NAME_LENGTH = 40

// ---------- Онбординг: имя → новый аккаунт ----------

data class OnboardingState(val name: String = "", val busy: Boolean = false, val error: String? = null) {
    val canSubmit: Boolean get() = name.isNotBlank() && !busy
}

class OnboardingViewModel : ViewModel() {
    private val auth = AppGraph.auth
    private val users = AppGraph.users
    private val _state = MutableStateFlow(OnboardingState())
    val state = _state.asStateFlow()

    fun onNameChange(value: String) = _state.update { it.copy(name = value.take(MAX_NAME_LENGTH), error = null) }

    fun submit() {
        val name = _state.value.name.trim()
        if (name.isEmpty() || _state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val account = auth.account.value ?: auth.createAccount()
                users.saveProfile(account.id, name)
            } catch (e: Exception) {
                logWarning("Onboarding", "Sign in failed", e)
                _state.update { it.copy(busy = false, error = strings.signInFailed) }
            }
        }
    }
}

@Composable
fun OnboardingScreen(onLinkDevice: () -> Unit, onRecover: () -> Unit, viewModel: OnboardingViewModel = viewModel { OnboardingViewModel() }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        GhostLogo(size = 88.dp)
        Spacer(Modifier.height(16.dp))
        Text("GHOSTLINK", style = LogoTextStyle, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(12.dp))
        Text(
            strings.tagline,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = { Text(strings.nameLabel) },
            supportingText = { Text(state.error ?: strings.nameHint) },
            isError = state.error != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = viewModel::submit, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text(strings.start)
        }
        Spacer(Modifier.height(24.dp))
        Text(strings.haveAccount, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onLinkDevice, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.QrCode2, contentDescription = null)
            Text("  " + strings.linkDevice)
        }
        TextButton(onClick = onRecover) { Text(strings.enterRecoveryKey) }
    }
}

// ---------- Вход по ключу восстановления ----------

data class RecoveryState(val code: String = "", val busy: Boolean = false, val error: String? = null) {
    val canSubmit: Boolean get() = code.isNotBlank() && !busy
}

class RecoveryViewModel : ViewModel() {
    private val _state = MutableStateFlow(RecoveryState())
    val state = _state.asStateFlow()

    fun onCodeChange(value: String) = _state.update { it.copy(code = value.take(80), error = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        val key = AccountKey.parseRecoveryCode(current.code)
        if (key == null) {
            _state.update { it.copy(error = strings.recoveryErrorFormat) }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                AppGraph.auth.signInWithKey(key)
            } catch (e: InvalidAccountKeyException) {
                _state.update { it.copy(busy = false, error = strings.recoveryErrorWrong) }
            } catch (e: Exception) {
                logWarning("Recovery", "Sign in failed", e)
                _state.update { it.copy(busy = false, error = strings.signInFailed) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryScreen(onBack: () -> Unit, viewModel: RecoveryViewModel = viewModel { RecoveryViewModel() }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(strings.recoveryTitle) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(24.dp)) {
            Text(
                strings.recoveryBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.code,
                onValueChange = viewModel::onCodeChange,
                label = { Text(strings.recoveryLabel) },
                isError = state.error != null,
                supportingText = state.error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = viewModel::submit, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text(strings.signIn)
            }
        }
    }
}

// ---------- Вход с другого устройства: показать QR ----------

sealed interface LinkReceiveState {
    data object Preparing : LinkReceiveState
    data class ShowCode(val link: String, val expiresAt: Long) : LinkReceiveState
    data object SigningIn : LinkReceiveState
    data object Expired : LinkReceiveState
    data object Failed : LinkReceiveState
}

/** Создаёт заявку, показывает QR и ждёт, пока устройство с аккаунтом положит в неё зашифрованный ключ. */
class LinkReceiveViewModel : ViewModel() {
    private val auth = AppGraph.auth
    private val links = AppGraph.links
    private val _state = MutableStateFlow<LinkReceiveState>(LinkReceiveState.Preparing)
    val state = _state.asStateFlow()
    private var job: Job? = null
    private var requestId: String? = null

    init {
        restart()
    }

    fun restart() {
        job?.cancel()
        job = viewModelScope.launch { run() }
    }

    private suspend fun run() {
        _state.value = LinkReceiveState.Preparing
        try {
            dropRequest()
            if (auth.account.value == null) auth.signInAnonymously()
            val keyPair = LinkCrypto.generateKeyPair()
            val id = links.createLinkRequest(DeviceInfo.name, DeviceInfo.platform).also { requestId = it }
            val code = LinkCode(id, LinkCrypto.encodePublicKey(keyPair.publicKey))
            _state.value = LinkReceiveState.ShowCode(code.toUri(), nowMillis() + LINK_TTL_MS)

            val payload = withTimeoutOrNull(LINK_TTL_MS) {
                links.observeLinkRequest(id).first { it?.payload != null }?.payload
            }
            if (payload == null) {
                dropRequest()
                _state.value = LinkReceiveState.Expired
                return
            }
            _state.value = LinkReceiveState.SigningIn
            val key = AccountKey(LinkCrypto.decrypt(keyPair.privateKey, id, payload))
            dropRequest()
            auth.signInWithKey(key)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logWarning("LinkReceive", "Linking failed", e)
            _state.value = LinkReceiveState.Failed
        }
    }

    private suspend fun dropRequest() {
        val id = requestId ?: return
        requestId = null
        withContext(NonCancellable) { runCatching { links.deleteLinkRequest(id) } }
    }

    override fun onCleared() {
        val id = requestId ?: return
        AppGraph.scope.launch { runCatching { links.deleteLinkRequest(id) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkReceiveScreen(onBack: () -> Unit, viewModel: LinkReceiveViewModel = viewModel { LinkReceiveViewModel() }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(strings.linkReceiveTitle) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val s = state) {
                LinkReceiveState.Preparing, LinkReceiveState.SigningIn -> {
                    Spacer(Modifier.height(80.dp))
                    CircularProgressIndicator()
                    if (s == LinkReceiveState.SigningIn) Text(strings.signingIn)
                }
                is LinkReceiveState.ShowCode -> {
                    QrImage(s.link, Modifier.size(260.dp))
                    Countdown(s.expiresAt)
                    Text(strings.howToSignIn, style = MaterialTheme.typography.titleMedium)
                    Text(
                        strings.linkReceiveSteps,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinkReceiveState.Expired, LinkReceiveState.Failed -> {
                    Spacer(Modifier.height(80.dp))
                    Text(
                        if (s == LinkReceiveState.Expired) strings.codeExpired else strings.signInFailed,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = viewModel::restart) { Text(strings.showNewCode) }
                }
            }
        }
    }
}

@Composable
private fun Countdown(expiresAt: Long) {
    val left by produceState(expiresAt - nowMillis(), expiresAt) {
        while (value > 0) {
            delay(1_000)
            value = expiresAt - nowMillis()
        }
    }
    val seconds = (left / 1000).coerceAtLeast(0)
    Text(
        strings.codeValidFor("${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** QR на белой подложке: тёмные темы не должны мешать камере. */
@Composable
fun QrImage(data: String, modifier: Modifier = Modifier) {
    val painter = rememberQrCodePainter(data)
    Box(modifier.background(Color.White).padding(14.dp)) {
        androidx.compose.foundation.Image(painter, contentDescription = strings.qrCode, modifier = Modifier.fillMaxSize())
    }
}
