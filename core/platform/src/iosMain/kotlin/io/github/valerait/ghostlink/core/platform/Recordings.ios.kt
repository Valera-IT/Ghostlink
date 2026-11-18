@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
@file:Suppress("TooGenericExceptionCaught")

package io.github.valerait.ghostlink.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import io.github.valerait.ghostlink.core.model.Attachment
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.AVFoundation.AVAssetExportPresetLowQuality
import platform.AVFoundation.AVAssetExportSession
import platform.AVFoundation.AVAssetExportSessionStatusCompleted
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVFileTypeMPEG4
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVKit.AVPlayerViewController
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.CoreGraphics.CGImageRelease
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSLog
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UIKit.UIImage
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerMediaURL
import platform.UIKit.UIImagePickerControllerQualityTypeLow
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.math.pow

@Composable
actual fun rememberAudioRecorder(
    onRecorded: (Recording) -> Unit,
    onFailed: () -> Unit,
): AudioRecorder {
    val scope = rememberCoroutineScope()
    val recorded by rememberUpdatedState(onRecorded)
    val failed by rememberUpdatedState(onFailed)
    val recorder =
        remember {
            object : AudioRecorder {
                var wanted = false
                var media: AVAudioRecorder? = null
                var url: NSURL? = null
                var meter: Job? = null
                val waveform = mutableListOf<Int>()

                fun begin() {
                    if (!wanted || media != null) return
                    try {
                        val session = AVAudioSession.sharedInstance()
                        memScoped {
                            val error = alloc<ObjCObjectVar<NSError?>>()
                            check(session.setCategory(AVAudioSessionCategoryPlayAndRecord, error = error.ptr)) {
                                "Audio category: ${error.value?.localizedDescription} (${error.value?.code})"
                            }
                            check(session.setActive(true, error = error.ptr)) {
                                "Audio activation: ${error.value?.localizedDescription} (${error.value?.code})"
                            }
                        }
                        url = NSURL.fileURLWithPath(NSTemporaryDirectory() + NSUUID().UUIDString + ".m4a")
                        media =
                            memScoped {
                                val error = alloc<ObjCObjectVar<NSError?>>()
                                // Формат передаём явно; битрейт AAC выбирает системный кодировщик.
                                val format =
                                    AVAudioFormat(
                                        settings =
                                            mapOf<Any?, Any>(
                                                AVFormatIDKey to kAudioFormatMPEG4AAC.toLong(),
                                                AVSampleRateKey to 44100.0,
                                                AVNumberOfChannelsKey to 1,
                                            ),
                                    )
                                val native = AVAudioRecorder(uRL = url!!, format = format, error = error.ptr)
                                check(error.value == null) {
                                    "Audio creation: ${error.value?.localizedDescription} (${error.value?.code})"
                                }
                                native.apply {
                                    meteringEnabled = true
                                    check(prepareToRecord()) {
                                        "Audio prepare: settings=$settings, permission=${session.recordPermission}"
                                    }
                                    check(recordForDuration(120.0)) {
                                        "Audio start: permission=${session.recordPermission}"
                                    }
                                }
                            }
                        NSLog("I/Audio: Recording started")
                        waveform.clear()
                        meter =
                            scope.launch {
                                var elapsed = 0L
                                while (true) {
                                    delay(1000)
                                    elapsed += 1000
                                    media?.updateMeters()
                                    val power = media?.averagePowerForChannel(0u) ?: -160f
                                    waveform += (10.0.pow(power / 40.0) * 100).toInt().coerceIn(0, 100)
                                    if (elapsed >= Attachment.RECORDING_MAX_MS) {
                                        finish()
                                        break
                                    }
                                }
                            }
                    } catch (e: Exception) {
                        logWarning("Audio", "Can't record", e)
                        cancel()
                        failed()
                    }
                }

                override fun start() {
                    AudioPlayback.pause()
                    wanted = true
                    AVAudioSession.sharedInstance().requestRecordPermission { granted ->
                        dispatch_async(dispatch_get_main_queue()) {
                            if (granted) {
                                begin()
                            } else {
                                cancel()
                                failed()
                            }
                        }
                    }
                }

                override fun finish() {
                    wanted = false
                    val current = media ?: return
                    val duration = (current.currentTime * 1000).toLong().coerceAtMost(Attachment.RECORDING_MAX_MS)
                    current.stop()
                    try {
                        val bytes = checkNotNull(NSData.dataWithContentsOfURL(url!!)).toByteArray()
                        if (duration >= 300 && bytes.size <= Attachment.RECORDING_MAX_BYTES) {
                            NSLog("I/Audio: Recording ready, durationMs=$duration, bytes=${bytes.size}")
                            recorded(Recording(bytes, duration, waveform.toList()))
                        }
                    } catch (e: Exception) {
                        logWarning("Audio", "Can't finish recording", e)
                        failed()
                    } finally {
                        cancel()
                    }
                }

                override fun cancel() {
                    wanted = false
                    meter?.cancel()
                    media?.stop()
                    media = null
                    url?.let { NSFileManager.defaultManager.removeItemAtURL(it, null) }
                    url = null
                    AVAudioSession.sharedInstance().setActive(false, error = null)
                }
            }
        }
    DisposableEffect(recorder) { onDispose { recorder.cancel() } }
    return recorder
}

@Composable
actual fun rememberVideoRecorder(
    onRecorded: (Recording) -> Unit,
    onFailed: () -> Unit,
): () -> Unit {
    val recorded by rememberUpdatedState(onRecorded)
    val failed by rememberUpdatedState(onFailed)
    val delegate =
        remember {
            object : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
                override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                    picker.dismissViewControllerAnimated(true, null)
                }

                override fun imagePickerController(
                    picker: UIImagePickerController,
                    didFinishPickingMediaWithInfo: Map<Any?, *>,
                ) {
                    picker.dismissViewControllerAnimated(true, null)
                    val url = didFinishPickingMediaWithInfo[UIImagePickerControllerMediaURL] as? NSURL
                    if (url == null) {
                        failed()
                        return
                    }
                    val asset = AVURLAsset.URLAssetWithURL(url, null)
                    val output = NSURL.fileURLWithPath(NSTemporaryDirectory() + NSUUID().UUIDString + ".mp4")
                    val exporter = AVAssetExportSession(asset, AVAssetExportPresetLowQuality)
                    if (exporter == null) {
                        failed()
                        return
                    }
                    exporter.outputURL = output
                    exporter.outputFileType = AVFileTypeMPEG4
                    exporter.shouldOptimizeForNetworkUse = true
                    exporter.exportAsynchronouslyWithCompletionHandler {
                        val result =
                            runCatching {
                                check(exporter.status == AVAssetExportSessionStatusCompleted)
                                val duration = (CMTimeGetSeconds(asset.duration) * 1000).toLong()
                                require(duration in 1..Attachment.RECORDING_MAX_MS)
                                val data = checkNotNull(NSData.dataWithContentsOfURL(output))
                                require(data.length.toLong() in 1L..Attachment.RECORDING_MAX_BYTES.toLong())
                                val generator = AVAssetImageGenerator(asset).apply { appliesPreferredTrackTransform = true }
                                val cgImage = checkNotNull(generator.copyCGImageAtTime(CMTimeMake(0, 1), null, null))
                                try {
                                    val image = UIImage.imageWithCGImage(cgImage)
                                    val size = image.size.useContents { width.toInt() to height.toInt() }
                                    val thumbnail = checkNotNull(image.render(320, true).toJpeg(75))
                                    Recording(data.toByteArray(), duration, thumbnail = thumbnail, width = size.first, height = size.second)
                                } finally {
                                    CGImageRelease(cgImage)
                                }
                            }.onFailure { logWarning("Video", "Can't prepare video", it) }.getOrNull()
                        NSFileManager.defaultManager.removeItemAtURL(url, null)
                        NSFileManager.defaultManager.removeItemAtURL(output, null)
                        dispatch_async(dispatch_get_main_queue()) { if (result != null) recorded(result) else failed() }
                    }
                }
            }
        }
    return {
        if (!UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
            failed()
        } else {
            val picker =
                UIImagePickerController().apply {
                    sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                    mediaTypes = listOf("public.movie")
                    videoMaximumDuration = 120.0
                    videoQuality = UIImagePickerControllerQualityTypeLow
                    this.delegate = delegate
                }
            topViewController()?.presentViewController(picker, true, null) ?: failed()
        }
    }
}

@Composable
actual fun RecordingPlayer(
    bytes: ByteArray,
    video: Boolean,
    modifier: Modifier,
) {
    val url =
        remember(bytes) {
            NSURL.fileURLWithPath(NSTemporaryDirectory() + NSUUID().UUIDString + if (video) ".mp4" else ".m4a").also {
                check(bytes.toNSData().writeToURL(it, true))
            }
        }
    val player =
        remember(url) {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null)
            AVAudioSession.sharedInstance().setActive(true, error = null)
            AVPlayer(uRL = url)
        }
    UIKitViewController(factory = {
        AVPlayerViewController().apply {
            view.layer.cornerRadius = 110.0
            view.layer.masksToBounds = true
            videoGravity = AVLayerVideoGravityResizeAspectFill
            this.player = player
            player.play()
        }
    }, modifier = modifier)
    DisposableEffect(player) {
        onDispose {
            player.pause()
            NSFileManager.defaultManager.removeItemAtURL(url, null)
            AVAudioSession.sharedInstance().setActive(false, error = null)
        }
    }
}

@Composable
actual fun rememberAudioPlayer(bytes: ByteArray): AudioPlayer {
    val native = remember(bytes) { runCatching { platform.AVFAudio.AVAudioPlayer(bytes.toNSData(), null) }.getOrNull() }
    val player =
        remember(native) {
            object : AudioPlayer {
                override fun play() {
                    AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null)
                    AVAudioSession.sharedInstance().setActive(true, error = null)
                    native?.play()
                }

                override fun pause() {
                    native?.pause()
                }

                override fun seek(position: Long) {
                    native?.currentTime = position / 1000.0
                }

                override fun snapshot() =
                    AudioPlaybackState(
                        position = ((native?.currentTime ?: 0.0) * 1000).toLong(),
                        duration = ((native?.duration ?: 0.0) * 1000).toLong(),
                        playing = native?.playing == true,
                        failed = native == null,
                    )
            }
        }
    DisposableEffect(player) {
        onDispose {
            AudioPlayback.release(player)
            native?.stop()
        }
    }
    return player
}
