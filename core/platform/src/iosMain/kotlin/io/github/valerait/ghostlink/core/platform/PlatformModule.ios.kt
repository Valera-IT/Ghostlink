package io.github.valerait.ghostlink.core.platform

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

actual val platformModule =
    module {
        // Keychain — пункт 4.2.
        single<SecureStore> { InMemorySecureStore() }
        single<AppLifecycle> { IosAppLifecycle() }
        single { iosThisDevice() }
        singleOf(::DeepLinks)
    }
