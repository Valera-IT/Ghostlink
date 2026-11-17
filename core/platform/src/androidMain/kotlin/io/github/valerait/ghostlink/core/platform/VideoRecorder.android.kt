@file:Suppress("TooGenericExceptionCaught")

package io.github.valerait.ghostlink.core.platform

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Rational
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.valerait.ghostlink.core.model.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import androidx.camera.video.Recording as CameraRecording

@Composable
actual fun rememberVideoRecorder(
    onRecorded: (Recording) -> Unit,
    onFailed: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    var showing by remember { mutableStateOf(false) }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.values.all { it }) showing = true else onFailed()
        }
    if (showing) {
        VideoRecordingDialog(onRecorded = {
            showing = false
            onRecorded(it)
        }, onCancel = { showing = false }, onFailed = {
            showing =
                false

            onFailed()
        })
    }
    return {
        val required = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            showing = true
        } else {
            permission.launch(required)
        }
    }
}

/** CameraX пишет H.264 в MP4: формат не зависит от установленного приложения камеры. */
@Suppress("MissingPermission") // Оба разрешения проверены перед открытием диалога.
@Composable
private fun VideoRecordingDialog(onRecorded: (Recording) -> Unit, onCancel: () -> Unit, onFailed: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val recorded by rememberUpdatedState(onRecorded)
    val failed by rememberUpdatedState(onFailed)
    val view = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    var recording by remember { mutableStateOf<CameraRecording?>(null) }
    var seconds by remember { mutableStateOf(0L) }
    var finishing by remember { mutableStateOf(false) }
    DisposableEffect(view) {
        val file = File.createTempFile("video-", ".mp4", context.cacheDir)
        val future = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val preview = Preview.Builder().build().apply { surfaceProvider = view.surfaceProvider }
        val recorder =
            Recorder
                .Builder()
                .setQualitySelector(QualitySelector.from(Quality.SD, FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
                .setTargetVideoEncodingBitRate(600_000)
                .build()
        val capture = VideoCapture.withOutput(recorder)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        future.addListener({
            if (!disposed) {
                try {
                    val camera = future.get().also { provider = it }
                    val selector =
                        if (camera.hasCamera(
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                            )
                        ) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }
                    val group =
                        UseCaseGroup
                            .Builder()
                            .addUseCase(preview)
                            .addUseCase(capture)
                            .setViewPort(ViewPort.Builder(Rational(1, 1), view.display?.rotation ?: 0).build())
                            .build()
                    camera.bindToLifecycle(owner, selector, group)
                    val options =
                        FileOutputOptions
                            .Builder(file)
                            .setDurationLimitMillis(Attachment.RECORDING_MAX_MS)
                            .setFileSizeLimit(Attachment.RECORDING_MAX_BYTES.toLong())
                            .build()
                    recording =
                        recorder.prepareRecording(context, options).withAudioEnabled().start(executor) { event ->
                            if (!disposed) {
                                when (event) {
                                    is VideoRecordEvent.Status -> {
                                        seconds = event.recordingStats.recordedDurationNanos / 1_000_000_000
                                    }

                                    is VideoRecordEvent.Finalize -> {
                                        finishing = true
                                        if (event.error !in
                                            listOf(
                                                VideoRecordEvent.Finalize.ERROR_NONE,
                                                VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED,
                                                VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED,
                                            )
                                        ) {
                                            failed()
                                        } else {
                                            scope.launch {
                                                val result = withContext(Dispatchers.IO) { readVideo(file) }
                                                if (result != null) recorded(result) else failed()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                } catch (e: Exception) {
                    logWarning("Video", "Can't start camera", e)
                    failed()
                }
            }
        }, executor)
        onDispose {
            disposed = true
            recording?.close()
            provider?.unbind(preview, capture)
            file.delete()
        }
    }
    Dialog(onDismissRequest = onCancel) {
        Column(Modifier.background(androidx.compose.ui.graphics.Color.White).padding(8.dp)) {
            AndroidView(factory = { view }, modifier = Modifier.size(280.dp).clip(CircleShape))
            Row(Modifier.fillMaxWidth()) {
                BasicText("×", Modifier.clickable(onClick = onCancel).padding(24.dp))
                BasicText("${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}", Modifier.padding(24.dp))
                if (!finishing) {
                    BasicText(
                        "■",
                        Modifier
                            .clickable {
                                finishing = true
                                recording?.stop()
                            }.padding(24.dp),
                    )
                }
            }
        }
    }
}

private fun readVideo(file: File): Recording? =
    runCatching {
        require(file.length() in 1..Attachment.RECORDING_MAX_BYTES.toLong())
        val metadata = MediaMetadataRetriever()
        try {
            metadata.setDataSource(file.absolutePath)
            val duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            require(duration in 1..Attachment.RECORDING_MAX_MS)
            val frame = checkNotNull(metadata.getFrameAtTime(0))
            val side = minOf(frame.width, frame.height)
            val square = Bitmap.createBitmap(frame, (frame.width - side) / 2, (frame.height - side) / 2, side, side)
            val thumb = Bitmap.createScaledBitmap(square, Attachment.THUMB_SIDE_PX, Attachment.THUMB_SIDE_PX, true)
            val jpeg = ByteArrayOutputStream().also { thumb.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, it) }.toByteArray()
            val width = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
            val height = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
            Recording(file.readBytes(), duration, thumbnail = jpeg, width = width, height = height)
        } finally {
            metadata.release()
        }
    }.onFailure { logWarning("Video", "Can't read video", it) }.getOrNull()

private const val THUMBNAIL_QUALITY = 75
