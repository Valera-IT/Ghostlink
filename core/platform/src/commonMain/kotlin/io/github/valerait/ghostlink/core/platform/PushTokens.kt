package io.github.valerait.ghostlink.core.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Токен этого устройства для push (FCM): его кладёт в реестр устройств DeviceRegistry, а Worker шлёт по нему
 * уведомления. null — push на этой платформе нет (демо, iOS без APNs, десктоп) или токен ещё не выдан.
 */
interface PushTokens {
    val token: StateFlow<String?>
}

/** Платформа без push. */
object NoPushTokens : PushTokens {
    override val token: StateFlow<String?> = MutableStateFlow(null)
}
