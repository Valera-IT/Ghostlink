package io.github.valerait.ghostlink.core.platform

import androidx.compose.runtime.Composable

// У десктопа сканера нет: он только показывает свой QR (этап 5).
@Composable
actual fun QrScanner(
    onBarcode: (String) -> Unit,
    denied: @Composable (openSettings: () -> Unit) -> Unit,
    unavailable: @Composable () -> Unit,
    overlay: @Composable () -> Unit,
) = unavailable()
