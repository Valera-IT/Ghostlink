package io.github.valerait.ghostlink.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Color
import java.awt.FileDialog
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

@Composable
actual fun rememberImagePicker(
    requests: List<ImageRequest>,
    onPicked: (List<PickedImage>) -> Unit,
    onFailed: () -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    val picked by rememberUpdatedState(onPicked)
    val failed by rememberUpdatedState(onFailed)
    return remember(requests) {
        {
            val dialog =
                FileDialog(null as Frame?).apply {
                    setFilenameFilter { _, name ->
                        name.substringAfterLast('.').lowercase() in
                            IMAGE_EXTENSIONS
                    }
                }
            dialog.isVisible = true
            val file = dialog.files.firstOrNull()
            if (file != null) {
                scope.launch {
                    val images =
                        withContext(Dispatchers.IO) {
                            runCatching { readJpegs(file, requests) }
                                .onFailure { logWarning("ImagePicker", "Can't read $file", it) }
                                .getOrNull()
                        }
                    if (images != null) picked(images) else failed()
                }
            }
        }
    }
}

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp")

private fun readJpegs(
    file: File,
    requests: List<ImageRequest>,
): List<PickedImage>? {
    val image = checkNotNull(ImageIO.read(file)) { "Not an image: $file" }
    return fitJpegs(
        requests,
        encode = { request, side, quality -> image.render(side, request.square).toJpeg(quality) },
        sizeOf = { request, side -> fitSize(image.width, image.height, side, request.square) },
    )
}

private fun BufferedImage.render(
    sidePx: Int,
    square: Boolean,
): BufferedImage {
    val (targetWidth, targetHeight) = fitSize(width, height, sidePx, square)
    // Квадрат — середина фото по короткой стороне.
    val side = if (square) minOf(width, height) else 0
    val x = if (square) (width - side) / 2 else 0
    val y = if (square) (height - side) / 2 else 0
    val right = if (square) x + side else width
    val bottom = if (square) y + side else height
    return BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB).also { result ->
        result.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(this@render, 0, 0, targetWidth, targetHeight, x, y, right, bottom, Color.WHITE, null)
            dispose()
        }
    }
}

private fun BufferedImage.toJpeg(quality: Int): ByteArray {
    val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
    val out = ByteArrayOutputStream()
    ImageIO.createImageOutputStream(out).use { stream ->
        writer.output = stream
        val params =
            writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = quality / MAX_JPEG_QUALITY.toFloat()
            }
        writer.write(null, IIOImage(this, null, null), params)
        writer.dispose()
    }
    return out.toByteArray()
}
