@file:Suppress("MatchingDeclarationName") // файл назван по главному — rememberImagePicker

package io.github.valerait.ghostlink.core.platform

import androidx.compose.runtime.Composable

/**
 * Во что превратить выбранную фотографию: длинная сторона не больше [maxSidePx] (у квадрата — сторона),
 * [square] — обрезать по центру в квадрат, JPEG не больше [maxBytes].
 */
data class ImageRequest(
    val maxSidePx: Int,
    val maxBytes: Int,
    val square: Boolean = false,
)

/**
 * Системный выбор одной фотографии: Photo Picker на Android, PHPicker на iOS, диалог файла на десктопе.
 * Разрешение на доступ к галерее не нужно. Возвращает функцию, открывающую выбор. [onPicked] получает готовый JPEG
 * по [request], [onFailed] — если фото не удалось прочитать или сжать. Отмена выбора ничего не вызывает.
 */
@Composable
expect fun rememberImagePicker(
    request: ImageRequest,
    onPicked: (ByteArray) -> Unit,
    onFailed: () -> Unit,
): () -> Unit

/** Шкала качества JPEG, как у Bitmap.compress: 0–100. */
internal const val MAX_JPEG_QUALITY = 100

private val JPEG_QUALITIES = listOf(90, 80, 70, 60, 50)
private const val MIN_SIDE_PX = 64

/** Во сколько раз уменьшаем сторону, если даже низкое качество не влезло: на четверть. */
private const val SHRINK_NUMERATOR = 3
private const val SHRINK_DENOMINATOR = 4

/**
 * Первый JPEG, который влезает в [ImageRequest.maxBytes]: сначала понижаем качество, потом уменьшаем картинку на четверть.
 * [encode] рисует фото со стороной не больше sidePx и сжимает с качеством quality (0–100), null — не смог сжать.
 * Результат null — ничего не влезло.
 */
internal inline fun fitJpeg(
    request: ImageRequest,
    encode: (sidePx: Int, quality: Int) -> ByteArray?,
): ByteArray? {
    var side = request.maxSidePx
    while (side >= MIN_SIDE_PX) {
        for (quality in JPEG_QUALITIES) {
            val jpeg = encode(side, quality)
            if (jpeg != null && jpeg.size <= request.maxBytes) return jpeg
        }
        side = side * SHRINK_NUMERATOR / SHRINK_DENOMINATOR
    }
    return null
}

/** Размер картинки [width]×[height] после [request]: обрезка в квадрат и уменьшение до [sidePx], но не увеличение. */
internal fun fitSize(
    width: Int,
    height: Int,
    sidePx: Int,
    square: Boolean,
): Pair<Int, Int> {
    if (square) {
        val side = minOf(sidePx, width, height)
        return side to side
    }
    val scale = minOf(1.0, sidePx.toDouble() / maxOf(width, height))
    return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
}
