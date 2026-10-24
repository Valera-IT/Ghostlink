package io.github.valerait.ghostlink

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valerait.ghostlink.core.model.InviteLink
import io.github.valerait.ghostlink.data.settings.LanguagePreferences
import io.github.valerait.ghostlink.data.settings.ThemePreferences
import io.github.valerait.ghostlink.ui.navigation.GhostlinkRoot
import io.github.valerait.ghostlink.ui.navigation.RootViewModel
import io.github.valerait.ghostlink.ui.theme.GhostlinkTheme
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val rootViewModel: RootViewModel by viewModel()
    private val themePreferences: ThemePreferences by inject()

    // Koin уже поднят в GhostlinkApp.onCreate — он идёт раньше создания Activity.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(get<LanguagePreferences>().wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Все темы тёмные — иконки системных панелей светлые и при светлой системе.
        val bars = SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleInvite(intent)
        val languagePreferences = get<LanguagePreferences>()
        val startLanguage = languagePreferences.language.value
        setContent {
            val theme by themePreferences.theme.collectAsStateWithLifecycle()
            val language by languagePreferences.language.collectAsStateWithLifecycle()
            // Ресурсы берут язык из контекста Activity, поэтому после смены языка пересоздаём её.
            LaunchedEffect(language) {
                if (language != startLanguage) recreate()
            }
            GhostlinkTheme(theme = theme) {
                GhostlinkRoot(rootViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleInvite(intent)
    }

    /** ghostlink://add/<id>, открытая из другого приложения (например, присланная ссылка). */
    private fun handleInvite(intent: Intent?) {
        InviteLink.parse(intent?.dataString)?.let(rootViewModel::onInvite)
    }
}
