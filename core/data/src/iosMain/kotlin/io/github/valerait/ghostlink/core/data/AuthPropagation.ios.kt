package io.github.valerait.ghostlink.core.data

// Нативный SDK сообщает Firestore о смене пользователя до того, как завершается вход.
internal actual suspend fun awaitAuthPropagation() = Unit
