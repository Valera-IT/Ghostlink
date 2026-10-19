package io.github.valerait.ghostlink.ui.mycode

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.github.valerait.ghostlink.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val QrInk = Color(0xFF0E0F14)

/**
 * Белая карточка с QR-кодом и призраком в центре.
 *
 * `ProduceStateDoesNotAssignValue` — ложное срабатывание lint: value присваивается внутри лямбды
 * (до обновления Compose на этапе 3.1).
 */
@SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
fun QrCard(
    content: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    // QR рисуем всегда тёмным по белому: камеры хуже читают инвертированные коды.
    val qr by produceState<ImageBitmap?>(null, content) {
        value =
            withContext(Dispatchers.Default) {
                createQrBitmap(content, 720, QrInk.toArgb(), Color.White.toArgb())
            }
    }
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth().aspectRatio(1f),
    ) {
        Box(Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
            qr?.let { Image(it, contentDescription = contentDescription) }
            // Призрак в центре: коррекция ошибок M спокойно переживает такую «дырку».
            Surface(color = Color.White, shape = CircleShape, modifier = Modifier.size(56.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_ghost),
                    contentDescription = null,
                    tint = QrInk,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }
}

/** Рисует QR-код; цвета передаются как ARGB. */
fun createQrBitmap(
    content: String,
    size: Int,
    foreground: Int,
    background: Int,
): ImageBitmap {
    val hints =
        mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) foreground else background }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}
