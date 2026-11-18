@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.valerait.ghostlink.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.UIKit.UIImage
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject

@Composable
actual fun rememberPhotoCamera(
    requests: List<ImageRequest>,
    onPicked: (List<PickedImage>) -> Unit,
    onFailed: () -> Unit,
): (() -> Unit)? {
    val scope = rememberCoroutineScope()
    val picked by rememberUpdatedState(onPicked)
    val failed by rememberUpdatedState(onFailed)
    val delegate =
        remember(requests) {
            object : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
                override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                    picker.dismissViewControllerAnimated(true, null)
                }

                override fun imagePickerController(
                    picker: UIImagePickerController,
                    didFinishPickingMediaWithInfo: Map<Any?, *>,
                ) {
                    val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
                    picker.dismissViewControllerAnimated(true, null)
                    scope.launch {
                        val images =
                            withContext(Dispatchers.Default) {
                                runCatching {
                                    image?.let {
                                        val (width, height) =
                                            it.size.useContents {
                                                (width * it.scale).toInt() to (height * it.scale).toInt()
                                            }
                                        fitJpegs(
                                            requests,
                                            encode = { request, side, quality -> it.render(side, request.square).toJpeg(quality) },
                                            sizeOf = { request, side -> fitSize(width, height, side, request.square) },
                                        )
                                    }
                                }.onFailure { logWarning("PhotoCamera", "Can't prepare photo", it) }.getOrNull()
                            }
                        if (images != null) picked(images) else failed()
                    }
                }
            }
        }
    return {
        val source = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        if (!UIImagePickerController.isSourceTypeAvailable(source)) {
            failed()
        } else {
            val picker =
                UIImagePickerController().apply {
                    sourceType = source
                    mediaTypes = listOf("public.image")
                    modalPresentationStyle = UIModalPresentationFullScreen
                    this.delegate = delegate
                }
            topViewController()?.presentViewController(picker, true, null) ?: failed()
        }
    }
}
