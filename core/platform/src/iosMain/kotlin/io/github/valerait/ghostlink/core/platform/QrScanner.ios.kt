@file:OptIn(ExperimentalForeignApi::class)

package io.github.valerait.ghostlink.core.platform

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

/** Что показать вместо камеры, пока она не работает. */
private enum class CameraState { Asking, Running, Denied, Unavailable }

@Composable
actual fun QrScanner(
    onBarcode: (String) -> Unit,
    denied: @Composable (openSettings: () -> Unit) -> Unit,
    unavailable: @Composable () -> Unit,
    overlay: @Composable () -> Unit,
) {
    val currentOnBarcode by rememberUpdatedState(onBarcode)
    var state by remember { mutableStateOf(CameraState.Asking) }
    val camera = remember { CameraQrReader { currentOnBarcode(it) } }

    // Если доступ выдадут в настройках, iOS перезапустит приложение, и экран спросит заново.
    DisposableEffect(camera) {
        camera.start { state = it }
        onDispose { camera.stop() }
    }

    when (state) {
        CameraState.Asking -> {
            // Пока поверх экрана системный запрос доступа, показывать нечего.
        }

        CameraState.Running -> {
            UIKitView(factory = { camera.view }, modifier = Modifier.fillMaxSize())
            overlay()
        }

        CameraState.Denied -> {
            denied(::openAppSettings)
        }

        CameraState.Unavailable -> {
            unavailable()
        }
    }
}

private fun openAppSettings() {
    val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
    UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
}

/** Слой превью растягивается вместе с вью: у CALayer нет автолэйаута. */
private class PreviewView(
    private val previewLayer: AVCaptureVideoPreviewLayer,
) : UIView(frame = CGRectZero.readValue()) {
    init {
        backgroundColor = UIColor.blackColor
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        previewLayer.frame = bounds
    }
}

private class MetadataDelegate(
    private val onCode: (String) -> Unit,
) : NSObject(),
    AVCaptureMetadataOutputObjectsDelegateProtocol {
    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        val code = didOutputMetadataObjects.firstOrNull() as? AVMetadataMachineReadableCodeObject ?: return
        code.stringValue?.let(onCode)
    }
}

/** Задняя камера с распознаванием QR силами AVFoundation. Коды приходят на главном потоке. */
private class CameraQrReader(
    onCode: (String) -> Unit,
) {
    private val session = AVCaptureSession()
    private val delegate = MetadataDelegate(onCode)
    val view =
        PreviewView(AVCaptureVideoPreviewLayer(session = session).apply { videoGravity = AVLayerVideoGravityResizeAspectFill })
    private var configured = false

    fun start(onState: (CameraState) -> Unit) {
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> {
                onState(run())
            }

            AVAuthorizationStatusNotDetermined -> {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    dispatch_async(dispatch_get_main_queue()) { onState(if (granted) run() else CameraState.Denied) }
                }
            }

            // Denied или Restricted (родительский контроль): в обоих случаях помогут только настройки.
            else -> {
                onState(CameraState.Denied)
            }
        }
    }

    fun stop() {
        background { if (session.running) session.stopRunning() }
    }

    private fun run(): CameraState {
        // Камеры нет, например, в симуляторе.
        if (!configured && !configure()) return CameraState.Unavailable
        // startRunning блокирует поток, пока камера не заработает: не на главном.
        background { if (!session.running) session.startRunning() }
        return CameraState.Running
    }

    private fun configure(): Boolean {
        val input =
            AVCaptureDevice
                .defaultDeviceWithMediaType(AVMediaTypeVideo)
                ?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, null) }
        val output = AVCaptureMetadataOutput()
        if (input == null || !session.canAddInput(input) || !session.canAddOutput(output)) return false
        session.addInput(input)
        session.addOutput(output)
        // Типы кодов задаются только после addOutput, иначе список доступных пуст.
        output.setMetadataObjectsDelegate(delegate, dispatch_get_main_queue())
        output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
        configured = true
        return true
    }

    private fun background(block: () -> Unit) =
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u), block)
}
