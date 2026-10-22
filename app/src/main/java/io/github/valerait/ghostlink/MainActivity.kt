package io.github.valerait.ghostlink

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.valerait.ghostlink.core.model.InviteLink
import io.github.valerait.ghostlink.data.settings.ThemePreferences
import io.github.valerait.ghostlink.ui.navigation.GhostlinkRoot
import io.github.valerait.ghostlink.ui.navigation.RootViewModel
import io.github.valerait.ghostlink.ui.theme.GhostlinkTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val rootViewModel: RootViewModel by viewModels()

    @Inject lateinit var themePreferences: ThemePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        // Все темы тёмные — иконки системных панелей светлые и при светлой системе.
        val bars = SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleInvite(intent)
        setContent {
            val theme by themePreferences.theme.collectAsStateWithLifecycle()
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
