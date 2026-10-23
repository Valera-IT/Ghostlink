package io.github.valerait.ghostlink.core.data.emulator

import android.app.Application
import com.google.firebase.FirebasePlatform
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.initialize
import io.github.valerait.ghostlink.core.crypto.LinkCrypto
import io.github.valerait.ghostlink.core.data.AccountKeyStore
import io.github.valerait.ghostlink.core.data.FirebaseAuthRepository
import io.github.valerait.ghostlink.core.data.FirebaseChatRepository
import io.github.valerait.ghostlink.core.data.FirebaseDeviceRepository
import io.github.valerait.ghostlink.core.data.FirebaseLinkRepository
import io.github.valerait.ghostlink.core.data.FirebaseUserRepository
import io.github.valerait.ghostlink.core.domain.usecase.ApproveDeviceLinkUseCase
import io.github.valerait.ghostlink.core.domain.usecase.RequestDeviceLinkUseCase
import io.github.valerait.ghostlink.core.model.AccountKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.setMain
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.time.Clock
import dev.gitlive.firebase.auth.auth as firebaseAuth

/**
 * Одно «устройство»: свой FirebaseApp со своим входом, репозитории core:data и use case поверх них.
 * Эмулятор запускает `firebase emulators:exec` с боевыми firestore.rules (см. README, раздел «Тесты»).
 */
internal class EmulatorClient(
    scope: CoroutineScope,
) {
    // Имя приложения уникально: эмулятор общий на все тесты, а FirebaseApp с тем же именем уже мог быть создан.
    private val app = Firebase.initialize(Application(), options, "client-${UUID.randomUUID()}")

    // Свойство auth ниже перекрыло бы расширение Firebase.auth, поэтому оно импортировано под другим именем.
    private val authClient = Firebase.firebaseAuth(app).apply { useEmulator(HOST, AUTH_PORT) }
    private val db = Firebase.firestore(app).apply { useEmulator(HOST, FIRESTORE_PORT) }

    val auth = FirebaseAuthRepository(authClient, InMemoryAccountKeyStore(), scope)
    val users = FirebaseUserRepository(db, scope)
    val chats = FirebaseChatRepository(db, scope)
    val devices = FirebaseDeviceRepository(db)
    val links = FirebaseLinkRepository(db, authClient)

    val requestLink = RequestDeviceLinkUseCase(auth, links, LinkCrypto, Clock.System)
    val approveLink = ApproveDeviceLinkUseCase(auth, links, LinkCrypto, Clock.System)

    companion object {
        private const val HOST = "127.0.0.1"
        private const val AUTH_PORT = 9099
        private const val FIRESTORE_PORT = 8080

        // demo-* проект эмулятор принимает без настоящих ключей.
        private val options = FirebaseOptions(applicationId = "1:0:android:0", apiKey = "demo-key", projectId = "demo-ghostlink")

        init {
            // FirebaseAuth из firebase-java-sdk запускает слушателей токена на Dispatchers.Main.
            // На десктопе это Swing (этап 4), в тестах — отдельный поток.
            @OptIn(ExperimentalCoroutinesApi::class)
            Dispatchers.setMain(
                Executors.newSingleThreadExecutor { Thread(it, "test-main").apply { isDaemon = true } }.asCoroutineDispatcher(),
            )

            // firebase-java-sdk хранит сессию входа и кэш через FirebasePlatform; в тестах — в памяти и во временной папке.
            val dbDir = Files.createTempDirectory("firebase-jvm").toFile()
            FirebasePlatform.initializeFirebasePlatform(
                object : FirebasePlatform() {
                    private val storage = mutableMapOf<String, String>()

                    override fun store(
                        key: String,
                        value: String,
                    ) = storage.set(key, value)

                    override fun retrieve(key: String) = storage[key]

                    override fun clear(key: String) {
                        storage.remove(key)
                    }

                    override fun log(msg: String) = Unit

                    override fun getDatabasePath(name: String) = File(dbDir, name)
                },
            )
        }
    }
}

private class InMemoryAccountKeyStore : AccountKeyStore {
    private var stored: Pair<String, AccountKey>? = null

    override fun get(userId: String): AccountKey? = stored?.takeIf { it.first == userId }?.second

    override fun put(
        userId: String,
        key: AccountKey,
    ) {
        stored = userId to key
    }

    override fun clear() {
        stored = null
    }
}
