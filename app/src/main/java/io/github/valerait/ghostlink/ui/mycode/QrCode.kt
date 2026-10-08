package io.github.valerait.ghostlink.ui.mycode

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Рисует QR-код; цвета передаются как ARGB. */
fun createQrBitmap(content: String, size: Int, foreground: Int, background: Int): ImageBitmap {
    val hints = mapOf(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) foreground else background }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}
