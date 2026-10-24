package io.github.valerait.ghostlink.core.platform

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
actual fun rememberImagePicker(
    request: ImageRequest,
    onPicked: (ByteArray) -> Unit,
    onFailed: () -> Unit,
): () -> Unit {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    val picked by rememberUpdatedState(onPicked)
    val failed by rememberUpdatedState(onFailed)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val jpeg =
                    withContext(Dispatchers.Default) {
                        runCatching { resolver.readJpeg(uri, request) }
                            .onFailure { logWarning("ImagePicker", "Can't read $uri", it) }
                            .getOrNull()
                    }
                if (jpeg != null) picked(jpeg) else failed()
            }
        }
    return remember(launcher) { { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
}

private fun ContentResolver.readJpeg(
    uri: Uri,
    request: ImageRequest,
): ByteArray? {
    val bitmap = decode(uri, request)
    return try {
        fitJpeg(request) { side, quality -> bitmap.render(side, request.square).toJpeg(quality) }
    } finally {
        bitmap.recycle()
    }
}

/** Декодирует сразу уменьшенным: 12-мегапиксельное фото целиком в памяти не нужно. */
private fun ContentResolver.decode(
    uri: Uri,
    request: ImageRequest,
): Bitmap {
    fun sampleSize(
        width: Int,
        height: Int,
    ): Int {
        val side = if (request.square) minOf(width, height) else maxOf(width, height)
        var sample = 1
        while (side / (sample * 2) >= request.maxSidePx) sample *= 2
        return sample
    }
    // ImageDecoder сам поворачивает фото по EXIF; на Android 8 фото с камеры может лечь боком.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(this, uri)) { decoder, info, _ ->
            decoder.setTargetSampleSize(sampleSize(info.size.width, info.size.height))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
    return checkNotNull(openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }) { "Not an image: $uri" }
}

/** Обрезка в квадрат по центру и уменьшение на белом фоне: у JPEG нет прозрачности. */
private fun Bitmap.render(
    sidePx: Int,
    square: Boolean,
): Bitmap {
    val (targetWidth, targetHeight) = fitSize(width, height, sidePx, square)
    val source =
        if (square) {
            val side = minOf(width, height)
            Rect((width - side) / 2, (height - side) / 2, (width + side) / 2, (height + side) / 2)
        } else {
            Rect(0, 0, width, height)
        }
    return Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also { result ->
        Canvas(result).apply {
            drawColor(Color.WHITE)
            drawBitmap(this@render, source, Rect(0, 0, targetWidth, targetHeight), Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }
}

private fun Bitmap.toJpeg(quality: Int): ByteArray =
    try {
        ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
    } finally {
        recycle()
    }
