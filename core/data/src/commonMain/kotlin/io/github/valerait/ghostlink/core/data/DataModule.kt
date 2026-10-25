package io.github.valerait.ghostlink.core.data

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Репозитории на Firebase. Нужны platformModule из core:platform и CoroutineScope процесса;
 * FirebaseApp платформа инициализирует до старта Koin.
 *
 * @param emulatorHost адрес локального Firebase Emulator; null — настоящий проект.
 */
fun dataModule(emulatorHost: String? = null): Module =
    module {
        single<AccountKeyStore> { SecureAccountKeyStore(get()) }
        single { FirebaseBackend(keys = get(), scope = get(), cacheDirectory = get(), emulatorHost = emulatorHost) }
        single { get<FirebaseBackend>().auth }
        single { get<FirebaseBackend>().users }
        single { get<FirebaseBackend>().chats }
        single { get<FirebaseBackend>().devices }
        single { get<FirebaseBackend>().links }
        single { get<FirebaseBackend>().media }
    }

/** Адрес Worker из worker/README.md, раздел «Развёртывание»; null — фото в чатах не работают. */
internal val MEDIA_WORKER_URL: String? = "https://ghostlink.rangervalera.workers.dev"
