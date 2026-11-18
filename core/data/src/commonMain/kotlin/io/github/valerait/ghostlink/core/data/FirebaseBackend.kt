package io.github.valerait.ghostlink.core.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import io.github.valerait.ghostlink.core.domain.repository.AuthRepository
import io.github.valerait.ghostlink.core.domain.repository.ChatRepository
import io.github.valerait.ghostlink.core.domain.repository.DeviceRepository
import io.github.valerait.ghostlink.core.domain.repository.LinkRepository
import io.github.valerait.ghostlink.core.domain.repository.MediaStore
import io.github.valerait.ghostlink.core.domain.repository.UserRepository
import io.github.valerait.ghostlink.core.platform.CacheDirectory
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.io.files.Path

/**
 * Все репозитории поверх Firebase-приложения по умолчанию. Приложение видит только интерфейсы core:domain,
 * типы GitLive наружу не выходят. FirebaseApp к этому моменту уже инициализирован платформой.
 *
 * @param scope живёт до конца процесса: в нём идут записи без ожидания (отправка, «прочитано», присутствие).
 * @param emulatorHost адрес локального Firebase Emulator; null — настоящий проект.
 * @param mediaWorkerUrl адрес Worker с доступом к фото чатов и push; null — фото и push не работают.
 */
class FirebaseBackend(
    keys: AccountKeyStore,
    scope: CoroutineScope,
    cacheDirectory: CacheDirectory,
    emulatorHost: String? = null,
    mediaWorkerUrl: String? = MEDIA_WORKER_URL,
) {
    private val firebaseAuth = Firebase.auth
    private val db = Firebase.firestore

    init {
        if (emulatorHost != null) {
            firebaseAuth.useEmulator(emulatorHost, AUTH_EMULATOR_PORT)
            db.useEmulator(emulatorHost, FIRESTORE_EMULATOR_PORT)
        }
    }

    private val http = HttpClient()
    private val idToken: suspend () -> String? = { firebaseAuth.currentUser?.getIdToken(false) }

    val auth: AuthRepository = FirebaseAuthRepository(firebaseAuth, keys, scope)
    val users: UserRepository = FirebaseUserRepository(db, scope)

    // Сообщений из эмулятора Worker не видит: там push не шлём.
    val chats: ChatRepository =
        FirebaseChatRepository(db, scope, WorkerPush(mediaWorkerUrl.takeIf { emulatorHost == null }, idToken, http))
    val devices: DeviceRepository = FirebaseDeviceRepository(db)
    val links: LinkRepository = FirebaseLinkRepository(db, firebaseAuth)
    val media: MediaStore =
        WorkerMediaStore(
            workerUrl = mediaWorkerUrl,
            idToken = idToken,
            http = http,
            cache = MediaCache(Path(cacheDirectory.path, "media")),
        )

    private companion object {
        // Порты из firebase.json.
        const val AUTH_EMULATOR_PORT = 9099
        const val FIRESTORE_EMULATOR_PORT = 8080
    }
}
