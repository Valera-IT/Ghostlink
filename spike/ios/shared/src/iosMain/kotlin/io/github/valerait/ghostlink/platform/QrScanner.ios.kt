package io.github.valerait.ghostlink.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
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
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

@Composable
actual fun QrScanner(onScanned: (String) -> Unit, modifier: Modifier) {
    val callback by rememberUpdatedState(onScanned)
    var denied by remember { mutableStateOf(false) }
    val camera = remember { CameraQrReader { callback(it) } }

    DisposableEffect(camera) {
        camera.start(onDenied = { denied = true })
        onDispose { camera.stop() }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        if (denied) {
            Text("Нет доступа к камере. Разрешите его в Настройках iOS → Ghostlink.", color = MaterialTheme.colorScheme.onSurface)
        } else {
            UIKitView(factory = { camera.view }, modifier = Modifier.matchParentSize())
        }
    }
}

/** Слой превью растягивается вместе с вью: у CALayer нет автолэйаута. */
private class PreviewView(val previewLayer: AVCaptureVideoPreviewLayer) : UIView(frame = CGRectZero.readValue()) {
    init {
        backgroundColor = UIColor.blackColor
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        previewLayer.frame = bounds
    }
}

private class MetadataDelegate(private val onCode: (String) -> Unit) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
    override fun captureOutput(output: AVCaptureOutput, didOutputMetadataObjects: List<*>, fromConnection: AVCaptureConnection) {
        val code = didOutputMetadataObjects.firstOrNull() as? AVMetadataMachineReadableCodeObject ?: return
        code.stringValue?.let(onCode)
    }
}

private class CameraQrReader(onCode: (String) -> Unit) {
    private val session = AVCaptureSession()
    private val delegate = MetadataDelegate(onCode)
    val view = PreviewView(AVCaptureVideoPreviewLayer(session = session).apply { videoGravity = AVLayerVideoGravityResizeAspectFill })
    private var configured = false

    fun start(onDenied: () -> Unit) {
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> run()
            AVAuthorizationStatusNotDetermined -> AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                dispatch_async(dispatch_get_main_queue()) { if (granted) run() else onDenied() }
            }
            else -> onDenied()
        }
    }

    fun stop() {
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            if (session.running) session.stopRunning()
        }
    }

    private fun run() {
        if (!configured && !configure()) return
        // startRunning блокирует поток — не на главном.
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            if (!session.running) session.startRunning()
        }
    }

    private fun configure(): Boolean {
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return false
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null) as? AVCaptureDeviceInput ?: return false
        if (!session.canAddInput(input)) return false
        session.addInput(input)
        val output = AVCaptureMetadataOutput()
        if (!session.canAddOutput(output)) return false
        session.addOutput(output)
        // Типы задаются только после addOutput, иначе список доступных пуст.
        output.setMetadataObjectsDelegate(delegate, dispatch_get_main_queue())
        output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
        configured = true
        return true
    }
}
