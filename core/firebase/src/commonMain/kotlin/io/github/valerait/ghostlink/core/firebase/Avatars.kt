package io.github.valerait.ghostlink.core.firebase

import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot

// Фото профиля avatars/{uid}: { data: bytes, version }. Как и payload заявки на вход, bytes идёт через нативный SDK.

/** Записывает фото профиля целиком: JPEG и его версию. */
expect suspend fun DocumentReference.setAvatar(
    data: ByteArray,
    version: Long,
)

expect fun DocumentSnapshot.avatarData(): ByteArray?
