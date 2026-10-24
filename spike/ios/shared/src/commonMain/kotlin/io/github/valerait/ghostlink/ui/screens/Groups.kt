package io.github.valerait.ghostlink.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.valerait.ghostlink.app.AppGraph
import io.github.valerait.ghostlink.app.strings
import io.github.valerait.ghostlink.data.leaveGroup
import io.github.valerait.ghostlink.data.requireUserId
import io.github.valerait.ghostlink.model.Chat
import io.github.valerait.ghostlink.model.GroupInfo
import io.github.valerait.ghostlink.model.User
import io.github.valerait.ghostlink.ui.common.Avatar
import io.github.valerait.ghostlink.ui.common.BackButton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ---------- Выбор участников ----------

data class PickMembersState(
    val loading: Boolean = true,
    /** Добавляем в существующую группу; иначе создаём новую. */
    val addingToGroup: Boolean = false,
    /** Друзья — те, с кем есть личный чат; уже состоящие в группе не показываются. */
    val friends: List<User> = emptyList(),
    val selected: Set<String> = emptySet(),
    /** Сколько ещё можно выбрать с учётом лимита группы. */
    val slotsLeft: Int = GroupInfo.MAX_MEMBERS - 1,
    /** Группа создана или участники добавлены: экран закрывается и открывает этот чат. */
    val doneChatId: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PickMembersViewModel(private val groupId: String?) : ViewModel() {
    private val me = AppGraph.auth.requireUserId()
    private val chats = AppGraph.chats
    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val done = MutableStateFlow<String?>(null)

    private val existing = if (groupId == null) flowOf(listOf(me)) else chats.observeChat(groupId).map { it?.members.orEmpty() }
    private val friends = chats.observeChats(me)
        .map { list -> list.filter { !it.isGroup }.map { it.partnerId(me) }.filter { it != me }.distinct().sorted() }
        .distinctUntilChanged()
        .flatMapLatest { AppGraph.users.observeUsers(it) }

    val state: StateFlow<PickMembersState> = combine(friends, existing, selected, done) { friends, existing, selected, done ->
        PickMembersState(
            loading = false,
            addingToGroup = groupId != null,
            friends = friends.values.filter { it.id !in existing }.sortedBy { it.name.lowercase() },
            selected = selected,
            slotsLeft = GroupInfo.MAX_MEMBERS - existing.size - selected.size,
            doneChatId = done,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickMembersState(addingToGroup = groupId != null))

    fun toggle(userId: String) {
        selected.value = when {
            userId in selected.value -> selected.value - userId
            state.value.slotsLeft > 0 -> selected.value + userId
            else -> selected.value
        }
    }

    fun create(name: String) {
        val title = name.trim().take(GroupInfo.NAME_MAX_LENGTH)
        if (title.isEmpty() || selected.value.isEmpty() || done.value != null) return
        viewModelScope.launch { done.value = chats.createGroup(me, title, selected.value.toList()) }
    }

    fun addSelected() {
        val id = groupId ?: return
        if (selected.value.isEmpty()) return
        chats.addMembers(id, selected.value.toList())
        done.value = id
    }
}

/** Создание группы (название + друзья) или добавление друзей в существующую группу [groupId]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickMembersScreen(
    groupId: String?,
    onBack: () -> Unit,
    onDone: (chatId: String) -> Unit,
    viewModel: PickMembersViewModel = viewModel(key = "pick-$groupId") { PickMembersViewModel(groupId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.doneChatId) { state.doneChatId?.let(onDone) }

    val canFinish = state.selected.isNotEmpty() && (state.addingToGroup || name.isNotBlank())
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.addingToGroup) strings.addMembers else strings.newGroup) },
                navigationIcon = { BackButton(onBack) },
            )
        },
        floatingActionButton = {
            if (canFinish) {
                ExtendedFloatingActionButton(
                    onClick = { if (state.addingToGroup) viewModel.addSelected() else viewModel.create(name) },
                    icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                    text = { Text(if (state.addingToGroup) strings.add else strings.create) },
                )
            }
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
            if (!state.addingToGroup) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(GroupInfo.NAME_MAX_LENGTH) },
                        label = { Text(strings.groupName) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
            item {
                Text(
                    strings.pickFriends(state.slotsLeft),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (!state.loading && state.friends.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            strings.noFriendsForGroup,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(state.friends, key = { it.id }) { friend ->
                val checked = friend.id in state.selected
                val enabled = checked || state.slotsLeft > 0
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { viewModel.toggle(friend.id) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Avatar(friend.name, friend.id, size = 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(friend.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Checkbox(checked = checked, onCheckedChange = { viewModel.toggle(friend.id) }, enabled = enabled)
                }
            }
        }
    }
}

// ---------- Карточка группы ----------

data class GroupInfoState(
    val loading: Boolean = true,
    val chat: Chat? = null,
    /** Участники: админ первым, затем по имени. */
    val members: List<User> = emptyList(),
    val me: String = "",
    val isOwner: Boolean = false,
    /** Я больше не в группе — экран закрывается. */
    val left: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class GroupInfoViewModel(private val chatId: String) : ViewModel() {
    private val me = AppGraph.auth.requireUserId()
    private val chats = AppGraph.chats

    // После выхода сервер перестаёт отдавать чат, поэтому закрываемся по своему действию, не дожидаясь снимка.
    private val leftByMe = MutableStateFlow(false)
    private val chat = chats.observeChat(chatId).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    private val people = chat.map { it?.members.orEmpty().sorted() }.distinctUntilChanged().flatMapLatest { AppGraph.users.observeUsers(it) }

    val state: StateFlow<GroupInfoState> = combine(chat, people, leftByMe) { chat, people, leftByMe ->
        val owner = chat?.group?.ownerId
        GroupInfoState(
            loading = false,
            chat = chat,
            members = chat?.members.orEmpty()
                .map { people[it] ?: User(it, "…") }
                .sortedWith(compareBy({ it.id != owner }, { it.name.lowercase() })),
            me = me,
            isOwner = owner == me,
            left = leftByMe || chat == null || me !in chat.members,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupInfoState(me = me))

    fun rename(name: String) {
        val title = name.trim().take(GroupInfo.NAME_MAX_LENGTH)
        if (title.isNotEmpty() && state.value.isOwner) chats.renameGroup(chatId, title)
    }

    fun remove(userId: String) {
        if (state.value.isOwner && userId != me) chats.removeMember(chatId, userId)
    }

    fun leave() {
        val chat = state.value.chat ?: return
        chats.leaveGroup(chat, me)
        leftByMe.value = true
    }
}

/** Карточка группы: участники, название; админ добавляет и удаляет участников, любой может выйти. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    chatId: String,
    onBack: () -> Unit,
    onAddMembers: () -> Unit,
    onLeft: () -> Unit,
    viewModel: GroupInfoViewModel = viewModel(key = "group-$chatId") { GroupInfoViewModel(chatId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var removingId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.left, state.loading) { if (!state.loading && state.left) onLeft() }
    val chat = state.chat
    val group = chat?.group ?: return

    Scaffold(topBar = { TopAppBar(title = { Text(strings.group) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(group.name, chat.id, size = 88.dp)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(group.name, style = MaterialTheme.typography.headlineSmall)
                        if (state.isOwner) {
                            IconButton(onClick = { renaming = true }) { Icon(Icons.Outlined.Edit, contentDescription = strings.renameGroup) }
                        }
                    }
                    Text(membersLabel(chat.members.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.isOwner && chat.members.size < GroupInfo.MAX_MEMBERS) {
                item { ActionRow(Icons.Outlined.PersonAdd, strings.addMembers, onClick = onAddMembers) }
            }
            items(state.members, key = { it.id }) { member ->
                MemberRow(
                    member = member,
                    isMe = member.id == state.me,
                    isOwner = member.id == group.ownerId,
                    onRemove = if (state.isOwner && member.id != state.me) ({ removingId = member.id }) else null,
                )
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                ActionRow(Icons.AutoMirrored.Outlined.Logout, strings.leaveGroup, color = MaterialTheme.colorScheme.error, onClick = { confirmLeave = true })
            }
        }
    }

    if (renaming) {
        RenameDialog(group.name, onConfirm = {
            viewModel.rename(it)
            renaming = false
        }, onDismiss = { renaming = false })
    }
    removingId?.let { id ->
        val name = state.members.firstOrNull { it.id == id }?.name.orEmpty()
        ConfirmDialog(
            title = strings.removeMemberTitle(name),
            confirm = strings.removeFromGroup,
            onConfirm = {
                viewModel.remove(id)
                removingId = null
            },
            onDismiss = { removingId = null },
        )
    }
    if (confirmLeave) {
        ConfirmDialog(
            title = strings.leaveGroupTitle,
            body = strings.leaveGroupBody(group.name),
            confirm = strings.leaveGroup,
            onConfirm = {
                confirmLeave = false
                viewModel.leave()
            },
            onDismiss = { confirmLeave = false },
        )
    }
}

@Composable
private fun MemberRow(member: User, isMe: Boolean, isOwner: Boolean, onRemove: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Avatar(member.name, member.id, size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (isMe) strings.meSuffix(member.name) else member.name, style = MaterialTheme.typography.titleMedium)
            if (isOwner) Text(strings.admin, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Outlined.PersonRemove, contentDescription = strings.removeFromGroup) }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, text: String, color: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(24.dp))
        Text(text, color = color, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun RenameDialog(current: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.renameGroup) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it.take(GroupInfo.NAME_MAX_LENGTH) }, singleLine = true, label = { Text(strings.groupName) })
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

@Composable
private fun ConfirmDialog(title: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, body: String? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = body?.let { { Text(it) } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}
