package io.github.valerait.ghostlink.core.firebase

import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot

// GitLive не умеет поля bytes, поэтому payload заявки на вход читается и пишется через нативный SDK.

/** Подтверждает заявку: кладёт зашифрованный ключ аккаунта и uid подтвердившего. */
expect suspend fun DocumentReference.approveLinkRequest(
    payload: ByteArray,
    approvedBy: String,
)

expect fun DocumentSnapshot.linkPayload(): ByteArray?
