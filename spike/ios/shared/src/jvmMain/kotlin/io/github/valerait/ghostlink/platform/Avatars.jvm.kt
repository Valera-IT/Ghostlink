package io.github.valerait.ghostlink.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.firebase.firestore.Blob
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.android
import io.github.valerait.ghostlink.model.User
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.skia.Image
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual suspend fun DocumentReference.setAvatar(data: ByteArray, version: Long): Unit =
    suspendCancellableCoroutine { cont ->
        android.set(mapOf("data" to Blob.fromBytes(data), "version" to version)).addOnCompleteListener { task ->
            val error = task.exception
            if (error == null) cont.resume(Unit) else cont.resumeWithException(error)
        }
    }

actual suspend fun DocumentReference.setAvatarVersion(version: Long): Unit =
    suspendCancellableCoroutine { cont ->
        android.update(mapOf<String, Any>("avatarVersion" to version)).addOnCompleteListener { task ->
            val error = task.exception
            if (error == null) cont.resume(Unit) else cont.resumeWithException(error)
        }
    }

actual fun DocumentSnapshot.avatarData(): ByteArray? = android.getBlob("data")?.toBytes()

actual fun decodeImage(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

@Composable
actual fun rememberAvatarPicker(onPicked: (ByteArray) -> Unit, onFailed: () -> Unit): () -> Unit {
    val picked by rememberUpdatedState(onPicked)
    val failed by rememberUpdatedState(onFailed)
    return remember {
        {
            val dialog = FileDialog(null as Frame?).apply { isVisible = true }
            dialog.files.firstOrNull()?.let { file ->
                val jpeg = runCatching { file.toAvatarJpeg() }.onFailure { logWarning("AvatarPicker", "Can't read $file", it) }.getOrNull()
                if (jpeg != null) picked(jpeg) else failed()
            }
        }
    }
}

private fun File.toAvatarJpeg(): ByteArray? {
    val image = checkNotNull(ImageIO.read(this)) { "Not an image: $this" }
    var side = User.AVATAR_SIZE_PX
    while (side >= 64) {
        val square = image.renderSquare(side)
        for (quality in listOf(0.9f, 0.8f, 0.7f, 0.6f, 0.5f)) {
            val jpeg = square.toJpeg(quality)
            if (jpeg.size <= User.AVATAR_MAX_BYTES) return jpeg
        }
        side = side * 3 / 4
    }
    return null
}

private fun BufferedImage.renderSquare(sidePx: Int): BufferedImage {
    val crop = minOf(width, height)
    val target = minOf(sidePx, crop)
    val x = (width - crop) / 2
    val y = (height - crop) / 2
    return BufferedImage(target, target, BufferedImage.TYPE_INT_RGB).also { result ->
        result.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(this@renderSquare, 0, 0, target, target, x, y, x + crop, y + crop, Color.WHITE, null)
            dispose()
        }
    }
}

private fun BufferedImage.toJpeg(quality: Float): ByteArray {
    val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
    val out = ByteArrayOutputStream()
    ImageIO.createImageOutputStream(out).use { stream ->
        writer.output = stream
        val params = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = quality
        }
        writer.write(null, IIOImage(this, null, null), params)
        writer.dispose()
    }
    return out.toByteArray()
}
