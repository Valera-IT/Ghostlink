package io.github.valerait.ghostlink.core.platform

import androidx.compose.runtime.Composable

// Камера на AVFoundation — пункт 4.2.
@Composable
actual fun QrScanner(
    onBarcode: (String) -> Unit,
    denied: @Composable (openSettings: () -> Unit) -> Unit,
    unavailable: @Composable () -> Unit,
    overlay: @Composable () -> Unit,
) = unavailable()
