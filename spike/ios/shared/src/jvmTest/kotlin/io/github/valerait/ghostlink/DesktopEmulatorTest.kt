package io.github.valerait.ghostlink

import android.app.Application
import com.google.firebase.FirebasePlatform
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseApp
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.initialize
import io.github.valerait.ghostlink.crypto.LinkCrypto
import io.github.valerait.ghostlink.data.firebase.FirebaseAuthRepository
import io.github.valerait.ghostlink.data.firebase.FirestoreChatRepository
import io.github.valerait.ghostlink.data.firebase.FirestoreDeviceRepository
import io.github.valerait.ghostlink.data.firebase.FirestoreLinkRepository
import io.github.valerait.ghostlink.data.firebase.FirestoreUserRepository
import io.github.valerait.ghostlink.model.AccountKey
import io.github.valerait.ghostlink.model.Device
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.TimeSource

/**
 * Разведка 2b, пункт 8: GitLive на JVM (firebase-java-sdk) против эмулятора на сценарии этапа 4 —
 * десктоп входит в аккаунт телефона через заявку на привязку и переписывается в реальном времени.
 * Код репозиториев тот же, что работает на iOS. Правила — боевые `firestore.rules`.
 *
 * Запуск: `firebase emulators:exec --only auth,firestore "./gradlew :shared:jvmTest"` из корня репозитория.
 */
class DesktopEmulatorTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Одно «устройство» — отдельный FirebaseApp со своим входом. */
    private inner class Client(name: String) {
        val app: FirebaseApp = Firebase.initialize(Application(), options, name)
        val auth = Firebase.auth(app).apply { useEmulator(HOST, 9099) }
        val db = Firebase.firestore(app).apply { useEmulator(HOST, 8080) }
        val authRepo = FirebaseAuthRepository(auth, scope)
        val users = FirestoreUserRepository(db)
        val chats = FirestoreChatRepository(db, scope)
        val devices = FirestoreDeviceRepository(db)
        val links = FirestoreLinkRepository(db, auth)
    }

    @AfterTest
    fun tearDown() = scope.cancel()

    @Test
    fun desktopLinksToPhoneAndChatsInRealTime() = runBlocking {
        val phone = Client("phone")
        val friend = Client("friend")
        val desktop = Client("desktop")

        val phoneId = phone.authRepo.createAccount().id
        val phoneKey = phone.authRepo.accountKey.value!!
        phone.users.saveProfile(phoneId, "Phone")
        val friendId = friend.authRepo.createAccount().id
        friend.users.saveProfile(friendId, "Friend")

        // Десктоп: временный анонимный вход, заявка и публичный ключ (в жизни — в QR).
        desktop.authRepo.signInAnonymously()
        val keyPair = LinkCrypto.generateKeyPair()
        val requestId = desktop.links.createLinkRequest("Spike desktop", "macos")

        // Телефон: подтверждает заявку, шифруя свой ключ аккаунта (Blob через нативный SDK).
        val request = phone.links.getLinkRequest(requestId)!!
        val publicKey = LinkCrypto.encodePublicKey(keyPair.publicKey)
        phone.links.approveLinkRequest(requestId, LinkCrypto.encrypt(publicKey, requestId, phoneKey.bytes))
        assertEquals("Spike desktop", request.deviceName)

        // Десктоп: ждёт payload слушателем, расшифровывает и входит в тот же аккаунт.
        val payload = withTimeout(TIMEOUT) { desktop.links.observeLinkRequest(requestId).mapNotNull { it?.payload }.first() }
        val desktopAccount = desktop.authRepo.signInWithKey(AccountKey(LinkCrypto.decrypt(keyPair.privateKey, requestId, payload)))
        assertEquals(phoneId, desktopAccount.id)

        // Реестр устройств: телефон видит десктоп слушателем.
        desktop.devices.registerDevice(phoneId, Device("desktop-1", "Spike desktop", "macos"))
        withTimeout(TIMEOUT) { phone.devices.observeDevices(phoneId).first { list -> list.any { it.id == "desktop-1" } } }

        // Чат: друг пишет — десктоп получает слушателем, и обратно. Сообщения ждём уже с серверным временем.
        val chatId = friend.chats.startChat(friendId, phoneId)
        var mark = TimeSource.Monotonic.markNow()
        friend.chats.sendMessage(chatId, friendId, "Привет, десктоп")
        withTimeout(TIMEOUT) { desktop.chats.observeMessages(chatId).first { list -> list.any { it.text == "Привет, десктоп" && it.sentAt != null } } }
        println("friend → desktop: ${mark.elapsedNow()}")

        mark = TimeSource.Monotonic.markNow()
        desktop.chats.sendMessage(chatId, phoneId, "Привет с JVM")
        withTimeout(TIMEOUT) { friend.chats.observeMessages(chatId).first { list -> list.any { it.text == "Привет с JVM" && it.sentAt != null } } }
        println("desktop → friend: ${mark.elapsedNow()}")
    }

    private companion object {
        const val HOST = "127.0.0.1"
        const val TIMEOUT = 15_000L

        // demo-* проект эмулятор принимает без настоящих ключей.
        val options = FirebaseOptions(applicationId = "1:0:android:0", apiKey = "demo-key", projectId = "demo-ghostlink")

        init {
            System.setProperty("ghostlink.prefs", "io/github/valerait/ghostlink-test")
            val dbDir = Files.createTempDirectory("firebase-jvm").toFile()
            FirebasePlatform.initializeFirebasePlatform(
                object : FirebasePlatform() {
                    val storage = mutableMapOf<String, String>()
                    override fun store(key: String, value: String) = storage.set(key, value)
                    override fun retrieve(key: String) = storage[key]
                    override fun clear(key: String) { storage.remove(key) }
                    override fun log(msg: String) = Unit
                    override fun getDatabasePath(name: String) = File(dbDir, name)
                },
            )
        }
    }
}
