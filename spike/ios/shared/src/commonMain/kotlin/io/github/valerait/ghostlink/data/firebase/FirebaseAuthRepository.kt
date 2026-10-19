package io.github.valerait.ghostlink.data.firebase

import dev.gitlive.firebase.auth.EmailAuthProvider
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseAuthInvalidCredentialsException
import dev.gitlive.firebase.auth.FirebaseAuthInvalidUserException
import dev.gitlive.firebase.auth.FirebaseUser
import io.github.valerait.ghostlink.data.AuthRepository
import io.github.valerait.ghostlink.data.InvalidAccountKeyException
import io.github.valerait.ghostlink.model.Account
import io.github.valerait.ghostlink.model.AccountKey
import io.github.valerait.ghostlink.platform.SecureStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Аккаунт — Firebase Email/Password со служебной почтой и случайным паролем (см. [AccountKey]). */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    scope: CoroutineScope,
) : AuthRepository {

    private val _account = MutableStateFlow(auth.currentUser?.toAccount())
    override val account: StateFlow<Account?> = _account.asStateFlow()

    private val _accountKey = MutableStateFlow(auth.currentUser?.let { loadKey(it.uid) })
    override val accountKey: StateFlow<AccountKey?> = _accountKey.asStateFlow()

    private val keyMutex = Mutex()

    init {
        scope.launch { auth.authStateChanged.collect { refresh(it) } }
    }

    override suspend fun createAccount(): Account {
        val key = AccountKey.generate()
        val user = checkNotNull(auth.createUserWithEmailAndPassword(key.email, key.password).user)
        SecureStore.put(user.uid, key.bytes)
        return checkNotNull(refresh(user))
    }

    override suspend fun signInWithKey(key: AccountKey): Account {
        // Временный анонимный аккаунт нового устройства больше не нужен.
        auth.currentUser?.takeIf { it.isAnonymous }?.let { temp -> runCatching { temp.delete() } }
        val user = try {
            checkNotNull(auth.signInWithEmailAndPassword(key.email, key.password).user)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            throw InvalidAccountKeyException(e)
        } catch (e: FirebaseAuthInvalidUserException) {
            throw InvalidAccountKeyException(e)
        }
        SecureStore.put(user.uid, key.bytes)
        return checkNotNull(refresh(user))
    }

    override suspend fun signInAnonymously(): Account =
        checkNotNull(refresh(checkNotNull(auth.signInAnonymously().user)))

    override suspend fun ensureAccountKey(): AccountKey = keyMutex.withLock {
        val user = requireUser()
        loadKey(user.uid)?.let { return it }
        val serviceEmail = user.email?.takeIf(AccountKey::isServiceEmail)
        val key = if (serviceEmail == null) {
            AccountKey.generate().also { user.linkWithCredential(EmailAuthProvider.credential(it.email, it.password)) }
        } else {
            checkNotNull(AccountKey.forEmail(serviceEmail)).also { user.updatePassword(it.password) }
        }
        SecureStore.put(user.uid, key.bytes)
        refresh(user)
        key
    }

    override suspend fun rotateKey(): AccountKey = keyMutex.withLock {
        val user = requireUser()
        val current = checkNotNull(loadKey(user.uid)) { "No account key on this device" }
        user.reauthenticate(EmailAuthProvider.credential(current.email, current.password))
        val next = current.rotated()
        user.updatePassword(next.password)
        SecureStore.put(user.uid, next.bytes)
        _accountKey.value = next
        next
    }

    override suspend fun signOut() {
        SecureStore.clear()
        auth.signOut()
        refresh(null)
    }

    private fun requireUser(): FirebaseUser = checkNotNull(auth.currentUser) { "Not signed in" }

    private fun loadKey(uid: String): AccountKey? =
        SecureStore.get(uid)?.takeIf { it.size == AccountKey.SIZE }?.let(::AccountKey)

    private fun refresh(user: FirebaseUser?): Account? {
        _account.value = user?.toAccount()
        _accountKey.value = user?.let { loadKey(it.uid) }
        return _account.value
    }
}

private fun FirebaseUser.toAccount() = Account(id = uid, isAnonymous = isAnonymous, googleEmail = null)
