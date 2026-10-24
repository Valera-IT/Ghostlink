package io.github.valerait.ghostlink.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.ios
import io.github.valerait.ghostlink.model.User
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.skia.Image
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIRectFill
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual suspend fun DocumentReference.setAvatar(data: ByteArray, version: Long): Unit =
    suspendCancellableCoroutine { cont ->
        ios.setData(mapOf<Any?, Any?>("data" to data.toNSData(), "version" to version)) { error ->
            if (error == null) cont.resume(Unit) else cont.resumeWithException(Exception(error.localizedDescription))
        }
    }

actual fun DocumentSnapshot.avatarData(): ByteArray? = (ios.valueForField("data") as? NSData)?.toByteArray()

actual fun decodeImage(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

@Composable
actual fun rememberAvatarPicker(onPicked: (ByteArray) -> Unit, onFailed: () -> Unit): () -> Unit {
    val picked by rememberUpdatedState(onPicked)
    val failed by rememberUpdatedState(onFailed)
    // PHPicker держит делегата слабой ссылкой: живёт, пока жив экран.
    val delegate = remember { AvatarPickerDelegate(onPicked = { picked(it) }, onFailed = { failed() }) }
    return remember(delegate) {
        {
            val config = PHPickerConfiguration().apply {
                filter = PHPickerFilter.imagesFilter
                selectionLimit = 1
            }
            val picker = PHPickerViewController(configuration = config).apply { this.delegate = delegate }
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

/** UTType.image: любая картинка — JPEG, HEIC, PNG. */
private const val IMAGE_TYPE = "public.image"

private class AvatarPickerDelegate(
    private val onPicked: (ByteArray) -> Unit,
    private val onFailed: () -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val provider = (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider ?: return
        // loadObjectOfClass(UIImage) из Kotlin не вызвать: класс там ждут как экземпляр протокола. Берём байты фото —
        // UIImage(data) сам читает поворот из EXIF. Обработчик приходит не на главном потоке: там и сжимаем.
        provider.loadDataRepresentationForTypeIdentifier(IMAGE_TYPE) { data, error ->
            val jpeg = data?.let { UIImage(data = it) }?.toAvatarJpeg()
            if (jpeg == null) logWarning("AvatarPicker", "Can't read picked image: ${error?.localizedDescription}")
            dispatch_async(dispatch_get_main_queue()) { if (jpeg != null) onPicked(jpeg) else onFailed() }
        }
    }
}

/** Первый JPEG, который влезает в лимит: сначала понижаем качество, потом уменьшаем сторону на четверть. */
private fun UIImage.toAvatarJpeg(): ByteArray? {
    var side = User.AVATAR_SIZE_PX
    while (side >= 64) {
        val square = renderSquare(side)
        for (quality in listOf(0.9, 0.8, 0.7, 0.6, 0.5)) {
            val jpeg = UIImageJPEGRepresentation(square, quality)?.toByteArray()
            if (jpeg != null && jpeg.size <= User.AVATAR_MAX_BYTES) return jpeg
        }
        side = side * 3 / 4
    }
    return null
}

/** Квадрат по центру со стороной [sidePx] пикселей на белом фоне; drawInRect учитывает поворот фото. */
private fun UIImage.renderSquare(sidePx: Int): UIImage {
    val (width, height) = size.useContents { (width * scale) to (height * scale) }
    val target = minOf(sidePx.toDouble(), width, height)
    val k = target / minOf(width, height)
    val format = UIGraphicsImageRendererFormat.defaultFormat().apply {
        scale = 1.0
        opaque = true
    }
    return UIGraphicsImageRenderer(size = CGSizeMake(target, target), format = format).imageWithActions {
        UIColor.whiteColor.setFill()
        UIRectFill(CGRectMake(0.0, 0.0, target, target))
        drawInRect(CGRectMake((target - width * k) / 2, (target - height * k) / 2, width * k, height * k))
    }
}

/** Верхний экран, поверх которого показать выбор фото. keyWindow в приложении со сценами бывает null — ищем окно сцены. */
private fun topViewController(): UIViewController? {
    val window =
        UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
            .let { windows -> windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull() }
    var controller = window?.rootViewController
    while (controller?.presentedViewController != null) controller = controller.presentedViewController
    return controller
}
