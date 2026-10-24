package io.github.valerait.ghostlink.core.data

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Репозитории на Firebase. Платформа даёт [AccountKeyStore] и CoroutineScope процесса
 * и до старта Koin инициализирует FirebaseApp.
 *
 * @param emulatorHost адрес локального Firebase Emulator; null — настоящий проект.
 */
fun dataModule(emulatorHost: String? = null): Module =
    module {
        single { FirebaseBackend(keys = get(), scope = get(), emulatorHost = emulatorHost) }
        single { get<FirebaseBackend>().auth }
        single { get<FirebaseBackend>().users }
        single { get<FirebaseBackend>().chats }
        single { get<FirebaseBackend>().devices }
        single { get<FirebaseBackend>().links }
    }
