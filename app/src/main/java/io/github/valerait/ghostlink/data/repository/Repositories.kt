package io.github.valerait.ghostlink.data.repository

import io.github.valerait.ghostlink.data.model.Account
import io.github.valerait.ghostlink.data.model.Chat
import io.github.valerait.ghostlink.data.model.Message
import io.github.valerait.ghostlink.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val account: StateFlow<Account?>

    suspend fun signInAnonymously(): Account

    suspend fun signInWithGoogle(idToken: String): Account

    /** Превращает анонимный аккаунт в постоянный: uid и вся переписка сохраняются. */
    suspend fun linkGoogle(idToken: String)

    suspend fun signOut()
}

/** Google-аккаунт уже привязан к другому профилю Ghostlink. */
class AccountAlreadyLinkedException : Exception()

fun AuthRepository.requireUserId(): String = checkNotNull(account.value) { "Not signed in" }.id

interface UserRepository {
    fun observeUser(id: String): Flow<User?>

    fun observeUsers(ids: List<String>): Flow<Map<String, User>>

    suspend fun getUser(id: String): User?

    suspend fun saveProfile(id: String, name: String)

    fun setOnline(id: String, online: Boolean)
}

interface ChatRepository {
    fun observeChats(userId: String): Flow<List<Chat>>

    fun observeChat(chatId: String): Flow<Chat?>

    fun observeMessages(chatId: String): Flow<List<Message>>

    /** Не ждёт сервера: сообщение сразу появляется локально со статусом SENDING. */
    fun sendMessage(chatId: String, senderId: String, text: String)

    fun markRead(chatId: String, userId: String)

    /** Создаёт чат пары (если его ещё нет) и возвращает его id. */
    suspend fun startChat(userId: String, friendId: String): String
}
