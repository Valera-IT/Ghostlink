package io.github.valerait.ghostlink.core.data

/**
 * Дожидается, пока Firestore узнает о новом пользователе после входа или выхода.
 * Запись, ушедшая раньше, попадёт в очередь прежнего пользователя и не подтвердится никогда.
 */
internal expect suspend fun awaitAuthPropagation()
