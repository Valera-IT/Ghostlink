package io.github.valerait.ghostlink.core.platform

import androidx.compose.ui.window.DialogProperties

actual fun fullScreenDialogProperties(): DialogProperties =
    DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
