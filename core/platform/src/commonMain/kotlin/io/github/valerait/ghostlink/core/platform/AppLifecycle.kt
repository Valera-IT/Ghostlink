package io.github.valerait.ghostlink.core.platform

import kotlinx.coroutines.flow.StateFlow

/** Видно ли приложение пользователю: от этого зависят статус «в сети» и время активности устройства. */
interface AppLifecycle {
    val isForeground: StateFlow<Boolean>
}
