package io.github.valerait.ghostlink.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// firebase-java-sdk рассылает смену токена слушателям (среди них Firestore) корутиной на Dispatchers.Main.
// Пустая задача на Main выполнится после неё, а Firestore к этому моменту уже поставит смену пользователя
// в свою очередь — раньше любой следующей записи.
internal actual suspend fun awaitAuthPropagation() = withContext(Dispatchers.Main) {}
