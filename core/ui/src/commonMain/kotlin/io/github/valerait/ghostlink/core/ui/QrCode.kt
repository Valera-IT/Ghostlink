package io.github.valerait.ghostlink.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.qrose.options.QrBrush
import io.github.alexzhirkevich.qrose.options.QrErrorCorrectionLevel
import io.github.alexzhirkevich.qrose.options.solid
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import io.github.valerait.ghostlink.core.designsystem.GhostLogo

private val QrInk = Color(0xFF0E0F14)

/** Белая карточка с QR-кодом и призраком в центре. */
@Composable
fun QrCard(
    content: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    // QR рисуем всегда тёмным по белому: камеры хуже читают инвертированные коды.
    // Коррекция ошибок M спокойно переживает «дырку» под призраком.
    val qr =
        rememberQrCodePainter(content) {
            errorCorrectionLevel = QrErrorCorrectionLevel.Medium
            colors { dark = QrBrush.solid(QrInk) }
        }
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth().aspectRatio(1f),
    ) {
        Box(Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
            Image(qr, contentDescription = contentDescription, modifier = Modifier.fillMaxSize())
            Surface(color = Color.White, shape = CircleShape, modifier = Modifier.size(56.dp)) {
                GhostLogo(size = 36.dp, tint = QrInk, modifier = Modifier.padding(10.dp))
            }
        }
    }
}
