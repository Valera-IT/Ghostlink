package io.github.valerait.ghostlink.core.platform

import androidx.compose.runtime.Composable

/**
 * Камера на весь экран с распознаванием QR. Сама просит разрешение: пока оно есть, поверх камеры
 * рисуется [overlay]; если отказали — [denied] с действием «открыть настройки»; где камеры нет — [unavailable].
 */
@Composable
expect fun QrScanner(
    onBarcode: (String) -> Unit,
    denied: @Composable (openSettings: () -> Unit) -> Unit,
    unavailable: @Composable () -> Unit,
    overlay: @Composable () -> Unit,
)
