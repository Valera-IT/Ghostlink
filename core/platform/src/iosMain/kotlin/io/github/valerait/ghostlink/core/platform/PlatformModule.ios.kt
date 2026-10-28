package io.github.valerait.ghostlink.core.platform

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

actual val platformModule =
    module {
        single<SecureStore> { KeychainSecureStore() }
        single<AppLifecycle> { IosAppLifecycle() }
        single { iosThisDevice() }
        single<PushTokens> { NoPushTokens }
        singleOf(::DeepLinks)
        single { CacheDirectory(iosCachesDirectory()) }
    }
