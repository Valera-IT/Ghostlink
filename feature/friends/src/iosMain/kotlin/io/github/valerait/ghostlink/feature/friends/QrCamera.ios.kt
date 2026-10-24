package io.github.valerait.ghostlink.feature.friends

import androidx.compose.runtime.Composable

// Камера появится вместе с core:platform (3.10) и приложениями для iOS (этап 5).
@Composable
internal actual fun QrCamera(
    onBarcode: (String) -> Unit,
    denied: @Composable (openSettings: () -> Unit) -> Unit,
    unavailable: @Composable () -> Unit,
    overlay: @Composable () -> Unit,
) = unavailable()
