package io.github.valerait.ghostlink.core.platform

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

actual val platformModule =
    module {
        single<SecureStore> { AndroidSecureStore(androidContext()) }
        single<AppLifecycle> { AndroidAppLifecycle() }
        single { androidThisDevice(androidContext()) }
        singleOf(::DeepLinks)
    }
