package io.github.valerait.ghostlink.ui.group

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.valerait.ghostlink.core.domain.repository.AuthRepository
import io.github.valerait.ghostlink.core.domain.repository.ChatRepository
import io.github.valerait.ghostlink.core.domain.repository.UserRepository
import io.github.valerait.ghostlink.core.domain.repository.requireUserId
import io.github.valerait.ghostlink.core.domain.usecase.LeaveGroupUseCase
import io.github.valerait.ghostlink.core.model.Chat
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.GroupInfo
import io.github.valerait.ghostlink.core.model.User
import io.github.valerait.ghostlink.core.model.UserId
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

data class PickMembersState(
    val loading: Boolean = true,
    /** Добавляем в существующую группу; иначе создаём новую. */
    val addingToGroup: Boolean = false,
    /** Друзья — те, с кем есть личный чат; уже состоящие в группе не показываются. */
    val friends: List<User> = emptyList(),
    val selected: Set<UserId> = emptySet(),
    /** Сколько ещё можно выбрать с учётом лимита группы. */
    val slotsLeft: Int = GroupInfo.MAX_MEMBERS - 1,
    /** Группа создана или участники добавлены: экран закрывается и открывает этот чат. */
    val doneChatId: ChatId? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PickMembersViewModel(
    savedStateHandle: SavedStateHandle,
    auth: AuthRepository,
    users: UserRepository,
    private val chats: ChatRepository,
) : ViewModel() {
    private val groupId = savedStateHandle.get<String>("chatId")?.let(::ChatId)
    private val me = auth.requireUserId()
    private val selected = MutableStateFlow<Set<UserId>>(emptySet())
    private val done = MutableStateFlow<ChatId?>(null)

    private val allChats = chats.observeChats(me).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    private val existing = if (groupId == null) flowOf(listOf(me)) else chats.observeChat(groupId).map { it?.members.orEmpty() }
    private val friends =
        allChats
            .map { list ->
                list
                    .filter { !it.isGroup }
                    .map { it.partnerId(me) }
                    .filter { it != me }
                    .distinct()
                    .sortedBy { it.value }
            }.distinctUntilChanged()
            .flatMapLatest { users.observeUsers(it) }

    val state: StateFlow<PickMembersState> =
        combine(friends, existing, selected, done) { friends, existing, selected, done ->
            PickMembersState(
                loading = false,
                addingToGroup = groupId != null,
                friends = friends.values.filter { it.id !in existing }.sortedBy { it.name.lowercase() },
                selected = selected,
                slotsLeft = GroupInfo.MAX_MEMBERS - existing.size - selected.size,
                doneChatId = done,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickMembersState(addingToGroup = groupId != null))

    fun toggle(userId: UserId) {
        selected.value =
            when {
                userId in selected.value -> selected.value - userId
                state.value.slotsLeft > 0 -> selected.value + userId
                else -> selected.value
            }
    }

    fun create(name: String) {
        val title = name.trim().take(GroupInfo.NAME_MAX_LENGTH)
        if (title.isEmpty() || selected.value.isEmpty() || done.value != null) return
        viewModelScope.launch {
            done.value = chats.createGroup(me, title, selected.value.toList())
        }
    }

    fun addSelected() {
        val id = groupId ?: return
        if (selected.value.isEmpty()) return
        chats.addMembers(id, selected.value.toList())
        done.value = id
    }
}

data class GroupInfoState(
    val loading: Boolean = true,
    val chat: Chat? = null,
    /** Участники: админ первым, затем по имени. */
    val members: List<User> = emptyList(),
    val me: UserId = UserId(""),
    val isOwner: Boolean = false,
    /** Я больше не в группе — экран закрывается. */
    val left: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class GroupInfoViewModel(
    savedStateHandle: SavedStateHandle,
    auth: AuthRepository,
    users: UserRepository,
    private val chats: ChatRepository,
    private val leaveGroup: LeaveGroupUseCase,
) : ViewModel() {
    private val chatId = ChatId(checkNotNull(savedStateHandle["chatId"]))
    private val me = auth.requireUserId()

    // После выхода сервер перестаёт отдавать чат, поэтому закрываемся по своему действию, не дожидаясь снимка.
    private val leftByMe = MutableStateFlow(false)
    private val chat = chats.observeChat(chatId).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    private val people =
        chat
            .map { it?.members.orEmpty().sortedBy { id -> id.value } }
            .distinctUntilChanged()
            .flatMapLatest { users.observeUsers(it) }

    val state: StateFlow<GroupInfoState> =
        combine(chat, people, leftByMe) { chat, people, leftByMe ->
            val owner = chat?.group?.ownerId
            GroupInfoState(
                loading = false,
                chat = chat,
                members =
                    chat
                        ?.members
                        .orEmpty()
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

    fun remove(userId: UserId) {
        if (state.value.isOwner && userId != me) chats.removeMember(chatId, userId)
    }

    fun leave() {
        val chat = state.value.chat ?: return
        leaveGroup(chat, me)
        leftByMe.value = true
    }
}
