package io.github.valerait.ghostlink.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.valerait.ghostlink.app.AppGraph
import io.github.valerait.ghostlink.data.requireUserId
import io.github.valerait.ghostlink.model.InviteLink
import io.github.valerait.ghostlink.model.ScannedCode
import io.github.valerait.ghostlink.model.User
import io.github.valerait.ghostlink.platform.QrScanner
import io.github.valerait.ghostlink.platform.logWarning
import io.github.valerait.ghostlink.ui.common.Avatar
import io.github.valerait.ghostlink.ui.common.BackButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------- Мой код ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyCodeScreen(onBack: () -> Unit, onScan: () -> Unit) {
    val me = remember { AppGraph.auth.requireUserId() }
    val user by remember { AppGraph.users.observeUser(me) }.collectAsStateWithLifecycle(null)
    val link = remember(me) { InviteLink.create(me) }
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000)
            copied = false
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Мой код") }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            user?.let {
                Avatar(it.name, it.id, size = 64.dp)
                Text(it.name, style = MaterialTheme.typography.titleLarge)
            }
            QrImage(link, Modifier.size(260.dp))
            Text(
                "Покажите этот код другу — он отсканирует его в своём Ghostlink, и у вас появится общий чат.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            OutlinedButton(onClick = {
                clipboard.setText(AnnotatedString(link))
                copied = true
            }, modifier = Modifier.fillMaxWidth()) { Text(if (copied) "Скопировано" else "Скопировать ссылку-приглашение") }
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Text("  Сканировать код друга")
            }
        }
    }
}

// ---------- Сканер ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(onBack: () -> Unit, onScanned: (ScannedCode) -> Unit) {
    var handled by remember { mutableStateOf(false) }
    var notOurs by remember { mutableStateOf(false) }
    LaunchedEffect(notOurs) {
        if (notOurs) {
            delay(2_000)
            notOurs = false
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Сканировать код") }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
            QrScanner(
                onScanned = { raw ->
                    if (handled) return@QrScanner
                    val code = ScannedCode.parse(raw)
                    if (code == null) {
                        notOurs = true
                    } else {
                        handled = true
                        onScanned(code)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                if (notOurs) "Это не код Ghostlink" else "Наведите камеру на код друга или на код входа с другого устройства",
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(20.dp),
            )
        }
    }
}

// ---------- Добавить друга ----------

sealed interface ConnectState {
    data object Loading : ConnectState
    data object Self : ConnectState
    data object NotFound : ConnectState
    data class Found(val user: User, val busy: Boolean = false, val error: String? = null) : ConnectState
}

class ConnectViewModel(private val friendId: String) : ViewModel() {
    private val me = AppGraph.auth.requireUserId()
    private val _state = MutableStateFlow<ConnectState>(ConnectState.Loading)
    val state = _state.asStateFlow()
    val chatReady = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            _state.value = if (friendId == me) {
                ConnectState.Self
            } else {
                try {
                    AppGraph.users.getUser(friendId)?.let { ConnectState.Found(it) } ?: ConnectState.NotFound
                } catch (e: Exception) {
                    logWarning("Connect", "Failed to load user", e)
                    ConnectState.NotFound
                }
            }
        }
    }

    fun connect() {
        val found = _state.value as? ConnectState.Found ?: return
        if (found.busy) return
        _state.value = found.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                chatReady.value = AppGraph.chats.startChat(me, friendId)
            } catch (e: Exception) {
                logWarning("Connect", "Failed to start chat", e)
                _state.update { (it as? ConnectState.Found)?.copy(busy = false, error = "Что-то пошло не так. Попробуйте ещё раз.") ?: it }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(userId: String, onBack: () -> Unit, onChatReady: (String) -> Unit, viewModel: ConnectViewModel = viewModel(key = userId) { ConnectViewModel(userId) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ready by viewModel.chatReady.collectAsStateWithLifecycle()
    LaunchedEffect(ready) { ready?.let(onChatReady) }

    Scaffold(topBar = { TopAppBar(title = { Text("Новый друг") }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (val s = state) {
                ConnectState.Loading -> CircularProgressIndicator()
                ConnectState.Self -> Message("Это ваш собственный код", "Покажите его другу — а сами отсканируйте его код.")
                ConnectState.NotFound -> Message("Код не найден", "Похоже, этот пользователь удалил аккаунт или код устарел.")
                is ConnectState.Found -> {
                    Avatar(s.user.name, s.user.id, size = 96.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(s.user.name, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        s.error ?: "Добавить в друзья? Вы сразу сможете переписываться.",
                        textAlign = TextAlign.Center,
                        color = if (s.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = viewModel::connect, enabled = !s.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        if (s.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Добавить и написать")
                    }
                }
            }
        }
    }
}

@Composable
internal fun Message(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}
