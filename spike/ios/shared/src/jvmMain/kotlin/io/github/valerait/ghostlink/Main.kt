package io.github.valerait.ghostlink

import android.app.Application
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.google.firebase.FirebasePlatform
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.initialize
import io.github.valerait.ghostlink.app.App
import java.io.File
import java.util.prefs.Preferences

/**
 * Десктоп разведки: тот же Compose UI и те же репозитории, что на iOS. Только против эмулятора
 * (`firebase emulators:start --project demo-ghostlink`): настоящий проект и вход по QR — этап 4.
 */
fun main() {
    val dataDir = File(System.getProperty("user.home"), ".ghostlink-spike").apply { mkdirs() }
    // firebase-java-sdk хранит сессию входа через платформу; в разведке — в Preferences.
    val session = Preferences.userRoot().node("io/github/valerait/ghostlink/firebase")
    FirebasePlatform.initializeFirebasePlatform(
        object : FirebasePlatform() {
            override fun store(key: String, value: String) = session.put(key, value)
            override fun retrieve(key: String) = session.get(key, null)
            override fun clear(key: String) = session.remove(key)
            override fun log(msg: String) = Unit
            override fun getDatabasePath(name: String) = File(dataDir, name)
        },
    )
    Firebase.initialize(
        Application(),
        FirebaseOptions(applicationId = "1:0:android:0", apiKey = "demo-key", projectId = "demo-ghostlink"),
    )
    Firebase.auth.useEmulator("127.0.0.1", 9099)
    Firebase.firestore.useEmulator("127.0.0.1", 8080)

    application {
        Window(onCloseRequest = ::exitApplication, title = "Ghostlink", state = rememberWindowState(width = 420.dp, height = 800.dp)) {
            App()
        }
    }
}
