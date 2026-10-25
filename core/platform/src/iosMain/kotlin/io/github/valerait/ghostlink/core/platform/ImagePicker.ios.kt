@file:OptIn(ExperimentalForeignApi::class)

package io.github.valerait.ghostlink.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
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

@Composable
actual fun rememberImagePicker(
    requests: List<ImageRequest>,
    onPicked: (List<PickedImage>) -> Unit,
    onFailed: () -> Unit,
): () -> Unit {
    val picked by rememberUpdatedState(onPicked)
    val failed by rememberUpdatedState(onFailed)
    // PHPicker держит делегата слабой ссылкой: живёт, пока жив экран.
    val delegate =
        remember(requests) {
            ImagePickerDelegate(requests, onPicked = { picked(it) }, onFailed = { failed() })
        }
    return remember(delegate) {
        {
            val config =
                PHPickerConfiguration().apply {
                    filter = PHPickerFilter.imagesFilter
                    selectionLimit = 1
                }
            val picker = PHPickerViewController(configuration = config).apply { this.delegate = delegate }
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

/** UTType.image: любая картинка. */
private const val IMAGE_TYPE = "public.image"

private class ImagePickerDelegate(
    private val requests: List<ImageRequest>,
    private val onPicked: (List<PickedImage>) -> Unit,
    private val onFailed: () -> Unit,
) : NSObject(),
    PHPickerViewControllerDelegateProtocol {
    override fun picker(
        picker: PHPickerViewController,
        didFinishPicking: List<*>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val provider = (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider ?: return
        // loadObjectOfClass(UIImage) из Kotlin не вызвать: класс там ждут как экземпляр протокола. Берём байты фото
        // (JPEG, HEIC, PNG) — UIImage(data) сам читает поворот из EXIF.
        // Обработчик приходит не на главном потоке: там и сжимаем, а результат отдаём на главный.
        provider.loadDataRepresentationForTypeIdentifier(IMAGE_TYPE) { data, error ->
            val image = data?.let { UIImage(data = it) }
            val images =
                image?.let {
                    val (width, height) = it.pixelSize()
                    fitJpegs(
                        requests,
                        encode = { request, side, quality -> it.render(side, request.square).toJpeg(quality) },
                        sizeOf = { request, side -> fitSize(width, height, side, request.square) },
                    )
                }
            if (images == null) logWarning("ImagePicker", "Can't read picked image: ${error?.localizedDescription}")
            dispatch_async(dispatch_get_main_queue()) { if (images != null) onPicked(images) else onFailed() }
        }
    }
}

/** Рисует фото в пикселях: обрезка в квадрат по центру, уменьшение, белый фон. drawInRect учитывает поворот фото. */
private fun UIImage.render(
    sidePx: Int,
    square: Boolean,
): UIImage {
    val (width, height) = size.useContents { (width * scale) to (height * scale) }
    val (targetWidth, targetHeight) = fitSize(width.toInt(), height.toInt(), sidePx, square)
    val k = if (square) targetWidth / minOf(width, height) else targetWidth / width
    val drawWidth = width * k
    val drawHeight = height * k
    val format =
        UIGraphicsImageRendererFormat.defaultFormat().apply {
            scale = 1.0
            opaque = true
        }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(targetWidth.toDouble(), targetHeight.toDouble()), format = format)
    return renderer.imageWithActions {
        UIColor.whiteColor.setFill()
        UIRectFill(CGRectMake(0.0, 0.0, targetWidth.toDouble(), targetHeight.toDouble()))
        drawInRect(CGRectMake((targetWidth - drawWidth) / 2, (targetHeight - drawHeight) / 2, drawWidth, drawHeight))
    }
}

/** Размер в пикселях с учётом поворота из EXIF: size у UIImage уже повёрнут. */
private fun UIImage.pixelSize(): Pair<Int, Int> = size.useContents { (width * scale).toInt() to (height * scale).toInt() }

private fun UIImage.toJpeg(quality: Int): ByteArray? = UIImageJPEGRepresentation(this, quality / MAX_JPEG_QUALITY.toDouble())?.toByteArray()

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
