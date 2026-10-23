package io.github.valerait.ghostlink.core.data

import dev.gitlive.firebase.auth.EmailAuthProvider
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseAuthInvalidCredentialsException
import dev.gitlive.firebase.auth.FirebaseAuthInvalidUserException
import dev.gitlive.firebase.auth.FirebaseUser
import io.github.valerait.ghostlink.core.domain.repository.AuthRepository
import io.github.valerait.ghostlink.core.model.Account
import io.github.valerait.ghostlink.core.model.AccountKey
import io.github.valerait.ghostlink.core.model.GhostlinkError
import io.github.valerait.ghostlink.core.model.UserId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Аккаунт — это Firebase Email/Password со служебной почтой и случайным паролем (см. [AccountKey]).
 * Так один аккаунт может войти на нескольких устройствах без сервера и без настоящей почты.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val keys: AccountKeyStore,
    scope: CoroutineScope,
) : AuthRepository {
    private val _account = MutableStateFlow(auth.currentUser?.toAccount())
    override val account: StateFlow<Account?> = _account.asStateFlow()

    private val _accountKey = MutableStateFlow(auth.currentUser?.let { keys.get(it.uid) })
    override val accountKey: StateFlow<AccountKey?> = _accountKey.asStateFlow()

    // ensureAccountKey зовут и реестр устройств, и экраны — ключ должен выдаваться один раз.
    private val keyMutex = Mutex()

    init {
        scope.launch { auth.authStateChanged.collect { refresh(it) } }
    }

    override suspend fun createAccount(): Account =
        firebaseCall {
            val key = AccountKey.generate()
            val user = checkNotNull(auth.createUserWithEmailAndPassword(key.email, key.password).user)
            keys.put(user.uid, key)
            checkNotNull(refresh(user))
        }

    override suspend fun signInWithKey(key: AccountKey): Account =
        firebaseCall {
            // Временный анонимный аккаунт нового устройства больше не нужен.
            auth.currentUser?.takeIf { it.isAnonymous }?.let { temp ->
                runCatching { temp.delete() }
            }
            val user =
                try {
                    checkNotNull(auth.signInWithEmailAndPassword(key.email, key.password).user)
                } catch (e: FirebaseAuthInvalidCredentialsException) {
                    throw GhostlinkError.InvalidAccountKey(e)
                } catch (e: FirebaseAuthInvalidUserException) {
                    throw GhostlinkError.InvalidAccountKey(e)
                }
            keys.put(user.uid, key)
            checkNotNull(refresh(user))
        }

    override suspend fun signInAnonymously(): Account = firebaseCall { checkNotNull(refresh(checkNotNull(auth.signInAnonymously().user))) }

    override suspend fun ensureAccountKey(): AccountKey =
        keyMutex.withLock {
            val user = requireUser()
            keys.get(user.uid)?.let { return it }

            val serviceEmail = user.email?.takeIf(AccountKey::isServiceEmail)
            val key =
                firebaseCall {
                    if (serviceEmail == null) {
                        // Анонимный аккаунт из старой версии: добавляем пароль, uid не меняется.
                        AccountKey.generate().also { user.linkWithCredential(EmailAuthProvider.credential(it.email, it.password)) }
                    } else {
                        // Пароль у аккаунта есть, но ключ на этом устройстве потерян (например, сброшен Keystore).
                        // Задаём новый: остальные устройства выйдут, зато ключ снова под контролем.
                        // Если вход был давно, Firebase попросит свежий вход и выбросит ошибку.
                        checkNotNull(AccountKey.forEmail(serviceEmail)).also { user.updatePassword(it.password) }
                    }
                }
            keys.put(user.uid, key)
            refresh(user)
            key
        }

    override suspend fun rotateKey(): AccountKey =
        keyMutex.withLock {
            val user = requireUser()
            val current = checkNotNull(keys.get(user.uid)) { "No account key on this device" }
            val next = current.rotated()
            firebaseCall {
                // Смена пароля требует недавнего входа; ключ у нас есть, так что входим им же.
                user.reauthenticate(EmailAuthProvider.credential(current.email, current.password))
                user.updatePassword(next.password)
            }
            keys.put(user.uid, next)
            _accountKey.value = next
            next
        }

    override suspend fun signOut() {
        keys.clear()
        firebaseCall { auth.signOut() }
        refresh(null)
    }

    private fun requireUser(): FirebaseUser = checkNotNull(auth.currentUser) { "Not signed in" }

    private fun refresh(user: FirebaseUser?): Account? {
        _account.value = user?.toAccount()
        _accountKey.value = user?.let { keys.get(it.uid) }
        return _account.value
    }
}

private fun FirebaseUser.toAccount() =
    Account(
        id = UserId(uid),
        isAnonymous = isAnonymous,
    )
