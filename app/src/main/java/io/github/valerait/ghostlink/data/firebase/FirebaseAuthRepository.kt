package io.github.valerait.ghostlink.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import io.github.valerait.ghostlink.data.model.Account
import io.github.valerait.ghostlink.data.repository.AccountAlreadyLinkedException
import io.github.valerait.ghostlink.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(private val auth: FirebaseAuth) : AuthRepository {

    private val _account = MutableStateFlow(auth.currentUser?.toAccount())
    override val account: StateFlow<Account?> = _account.asStateFlow()

    init {
        auth.addAuthStateListener { _account.value = it.currentUser?.toAccount() }
    }

    override suspend fun signInAnonymously(): Account =
        checkNotNull(auth.signInAnonymously().await().user).toAccount().also { _account.value = it }

    override suspend fun signInWithGoogle(idToken: String): Account {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return checkNotNull(auth.signInWithCredential(credential).await().user).toAccount()
            .also { _account.value = it }
    }

    override suspend fun linkGoogle(idToken: String) {
        val user = checkNotNull(auth.currentUser) { "Not signed in" }
        try {
            user.linkWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        } catch (e: FirebaseAuthUserCollisionException) {
            throw AccountAlreadyLinkedException()
        }
        // AuthStateListener не срабатывает на привязку, обновляем вручную.
        _account.value = auth.currentUser?.toAccount()
    }

    override suspend fun signOut() {
        auth.signOut()
    }
}

private fun FirebaseUser.toAccount() = Account(
    id = uid,
    isAnonymous = isAnonymous,
    email = email ?: providerData.firstNotNullOfOrNull { it.email },
)
