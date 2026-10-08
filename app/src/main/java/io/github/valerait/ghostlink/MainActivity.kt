package io.github.valerait.ghostlink

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.github.valerait.ghostlink.data.model.InviteLink
import io.github.valerait.ghostlink.ui.navigation.GhostlinkRoot
import io.github.valerait.ghostlink.ui.navigation.RootViewModel
import io.github.valerait.ghostlink.ui.theme.GhostlinkTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val rootViewModel: RootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleInvite(intent)
        setContent {
            GhostlinkTheme {
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
