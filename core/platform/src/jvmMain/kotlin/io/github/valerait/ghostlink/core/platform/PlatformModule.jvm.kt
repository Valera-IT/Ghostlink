package io.github.valerait.ghostlink.core.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

actual val platformModule =
    module {
        // java-keyring — пункт 5.3.
        single<SecureStore> { InMemorySecureStore() }
        // Фокус окна подключим вместе с десктопным приложением (этап 5); пока окно считается видимым.
        single<AppLifecycle> {
            object : AppLifecycle {
                override val isForeground: StateFlow<Boolean> = MutableStateFlow(true)
            }
        }
        single { jvmThisDevice() }
        single<PushTokens> { NoPushTokens }
        singleOf(::DeepLinks)
        single { CacheDirectory(jvmCacheDirectory()) }
    }
