package io.github.valerait.ghostlink.data.firebase

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import io.github.valerait.ghostlink.data.device.AccountKeyStore
import io.github.valerait.ghostlink.data.model.Account
import io.github.valerait.ghostlink.data.model.AccountKey
import io.github.valerait.ghostlink.data.repository.AuthRepository
import io.github.valerait.ghostlink.data.repository.InvalidAccountKeyException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Аккаунт — это Firebase Email/Password со служебной почтой и случайным паролем (см. [AccountKey]).
 * Так один аккаунт может войти на нескольких устройствах без сервера и без настоящей почты.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val keys: AccountKeyStore,
) : AuthRepository {
    private val _account = MutableStateFlow(auth.currentUser?.toAccount())
    override val account: StateFlow<Account?> = _account.asStateFlow()

    private val _accountKey = MutableStateFlow(auth.currentUser?.let { keys.get(it.uid) })
    override val accountKey: StateFlow<AccountKey?> = _accountKey.asStateFlow()

    // ensureAccountKey зовут и реестр устройств, и экраны — ключ должен выдаваться один раз.
    private val keyMutex = Mutex()

    init {
        auth.addAuthStateListener { refresh() }
    }

    override suspend fun createAccount(): Account {
        val key = AccountKey.generate()
        val user = checkNotNull(auth.createUserWithEmailAndPassword(key.email, key.password).await().user)
        keys.put(user.uid, key)
        return checkNotNull(refresh(user))
    }

    override suspend fun signInWithKey(key: AccountKey): Account {
        // Временный анонимный аккаунт нового устройства больше не нужен.
        auth.currentUser?.takeIf { it.isAnonymous }?.let { temp ->
            runCatching { temp.delete().await() }
        }
        val user =
            try {
                checkNotNull(auth.signInWithEmailAndPassword(key.email, key.password).await().user)
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                throw InvalidAccountKeyException(e)
            } catch (e: FirebaseAuthInvalidUserException) {
                throw InvalidAccountKeyException(e)
            }
        keys.put(user.uid, key)
        return checkNotNull(refresh(user))
    }

    override suspend fun signInAnonymously(): Account = checkNotNull(refresh(checkNotNull(auth.signInAnonymously().await().user)))

    override suspend fun ensureAccountKey(): AccountKey =
        keyMutex.withLock {
            val user = requireUser()
            keys.get(user.uid)?.let { return it }

            val serviceEmail = user.email?.takeIf(AccountKey::isServiceEmail)
            val key =
                if (serviceEmail == null) {
                    // Анонимный аккаунт из старой версии: добавляем пароль, uid не меняется.
                    AccountKey.generate().also {
                        user.linkWithCredential(EmailAuthProvider.getCredential(it.email, it.password)).await()
                    }
                } else {
                    // Пароль у аккаунта есть, но ключ на этом устройстве потерян (например, сброшен Keystore).
                    // Задаём новый: остальные устройства выйдут, зато ключ снова под контролем.
                    // Если вход был давно, Firebase попросит свежий вход и выбросит ошибку.
                    checkNotNull(AccountKey.forEmail(serviceEmail)).also { user.updatePassword(it.password).await() }
                }
            keys.put(user.uid, key)
            refresh()
            key
        }

    override suspend fun rotateKey(): AccountKey =
        keyMutex.withLock {
            val user = requireUser()
            val current = checkNotNull(keys.get(user.uid)) { "No account key on this device" }
            // Смена пароля требует недавнего входа; ключ у нас есть, так что входим им же.
            user.reauthenticate(EmailAuthProvider.getCredential(current.email, current.password)).await()
            val next = current.rotated()
            user.updatePassword(next.password).await()
            keys.put(user.uid, next)
            _accountKey.value = next
            next
        }

    override suspend fun signOut() {
        keys.clear()
        auth.signOut()
        refresh()
    }

    private fun requireUser(): FirebaseUser = checkNotNull(auth.currentUser) { "Not signed in" }

    private fun refresh(user: FirebaseUser? = auth.currentUser): Account? {
        _account.value = user?.toAccount()
        _accountKey.value = user?.let { keys.get(it.uid) }
        return _account.value
    }
}

private fun FirebaseUser.toAccount() =
    Account(
        id = uid,
        isAnonymous = isAnonymous,
    )
