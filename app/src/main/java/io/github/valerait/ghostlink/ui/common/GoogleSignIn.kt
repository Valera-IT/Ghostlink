package io.github.valerait.ghostlink.ui.common

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Показывает системный выбор Google-аккаунта и возвращает ID token для Firebase.
 * null — пользователь закрыл диалог. [context] должен быть Activity.
 */
suspend fun requestGoogleIdToken(
    context: Context,
    webClientId: String,
): String? {
    val request =
        GetCredentialRequest
            .Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()
    val credential =
        try {
            CredentialManager.create(context).getCredential(context, request).credential
        } catch (e: GetCredentialCancellationException) {
            return null
        }
    check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        "Unexpected credential type: ${credential.type}"
    }
    return GoogleIdTokenCredential.createFrom(credential.data).idToken
}
