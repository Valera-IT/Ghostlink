@file:Suppress("TooGenericExceptionCaught", "DEPRECATION")

package io.github.valerait.ghostlink.core.platform

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import io.github.valerait.ghostlink.core.model.Attachment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.log10

@Composable
actual fun rememberAudioRecorder(
    onRecorded: (Recording) -> Unit,
    onFailed: () -> Unit,
): AudioRecorder {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorded by rememberUpdatedState(onRecorded)
    val failed by rememberUpdatedState(onFailed)
    val recorder =
        remember {
            object : AudioRecorder {
                var wanted = false
                var media: MediaRecorder? = null
                var file: File? = null
                var started = 0L
                var meter: Job? = null
                val waveform = mutableListOf<Int>()

                override fun start() {
                    wanted = true
                    if (media != null) return
                    try {
                        file = File.createTempFile("voice-", ".m4a", context.cacheDir)
                        media =
                            MediaRecorder().apply {
                                setAudioSource(MediaRecorder.AudioSource.MIC)
                                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                setAudioEncodingBitRate(24000)
                                setAudioSamplingRate(44100)
                                setMaxDuration(Attachment.RECORDING_MAX_MS.toInt())
                                setOnInfoListener { _, what, _ ->
                                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) finish()
                                }
                                setOutputFile(file!!.absolutePath)
                                prepare()
                                start()
                            }
                        started = SystemClock.elapsedRealtime()
                        waveform.clear()
                        meter =
                            scope.launch {
                                while (true) {
                                    delay(1000)
                                    waveform += (log10((media?.maxAmplitude ?: 0) + 1.0) * 22).toInt().coerceIn(0, 100)
                                    if (SystemClock.elapsedRealtime() - started >= Attachment.RECORDING_MAX_MS) {
                                        finish()
                                        break
                                    }
                                }
                            }
                    } catch (e: Exception) {
                        logWarning("Audio", "Recording failed", e)
                        cancel()
                        failed()
                    }
                }

                override fun finish() {
                    wanted = false
                    val current = media ?: return
                    media = null
                    meter?.cancel()
                    try {
                        current.stop()
                        val duration = (SystemClock.elapsedRealtime() - started).coerceAtMost(Attachment.RECORDING_MAX_MS)
                        val bytes = checkNotNull(file).readBytes()
                        if (duration >= 300 &&
                            bytes.size <= Attachment.RECORDING_MAX_BYTES
                        ) {
                            recorded(Recording(bytes, duration, waveform.toList()))
                        }
                    } catch (
                        e: Exception,
                    ) {
                        logWarning("Audio", "Can't finish recording", e)
                        failed()
                    } finally {
                        current.release()
                        file?.delete()
                        file = null
                    }
                }

                override fun cancel() {
                    wanted = false
                    meter?.cancel()
                    media?.run {
                        runCatching { stop() }
                        release()
                    }
                    media = null
                    file?.delete()
                    file = null
                }
            }
        }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && recorder.wanted) {
                recorder.start()
            } else if (!granted) {
                recorder.cancel()
                failed()
            }
        }
    DisposableEffect(recorder) { onDispose { recorder.cancel() } }
    return remember(recorder, permission) {
        object : AudioRecorder {
            override fun start() {
                recorder.wanted = true
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    recorder.start()
                } else {
                    permission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            override fun finish() = recorder.finish()

            override fun cancel() = recorder.cancel()
        }
    }
}

@Composable
actual fun RecordingPlayer(
    bytes: ByteArray,
    video: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val file = remember(bytes) { File.createTempFile("play-", if (video) ".mp4" else ".m4a", context.cacheDir).apply { writeBytes(bytes) } }
    if (video) {
        InlineVideoSurface(file, modifier)
    } else {
        val player =
            remember(file) {
                val candidate = MediaPlayer()
                runCatching {
                    candidate.setDataSource(file.absolutePath)
                    candidate.prepare()
                    candidate.start()
                    candidate
                }.getOrElse {
                    candidate.release()
                    logWarning("Audio", "Can't play recording", it)
                    null
                }
            }
        if (player == null) {
            BasicText("⚠", modifier)
            DisposableEffect(file) { onDispose { file.delete() } }
            return
        }
        var paused by remember(player) { mutableStateOf(false) }
        BasicText(
            if (paused) "▶" else "⏸",
            modifier.clickable {
                paused = !paused
                if (paused) player.pause() else player.start()
            },
        )
        DisposableEffect(player) {
            onDispose {
                player.release()
                file.delete()
            }
        }
    }
}

@Composable
actual fun rememberAudioPlayer(bytes: ByteArray): AudioPlayer {
    val context = LocalContext.current
    val file = remember(bytes) { File.createTempFile("voice-play-", ".m4a", context.cacheDir).apply { writeBytes(bytes) } }
    val native = remember(file) { MediaPlayer() }
    val player =
        remember(native) {
            object : AudioPlayer {
                var failed = false

                init {
                    try {
                        native.setDataSource(file.absolutePath)
                        native.prepare()
                    } catch (e: Exception) {
                        failed = true
                        logWarning("Audio", "Can't open voice message", e)
                    }
                    native.setOnErrorListener { _, _, _ ->
                        failed = true
                        true
                    }
                }

                override fun play() {
                    if (!failed) native.start()
                }

                override fun pause() {
                    if (!failed) native.pause()
                }

                override fun seek(position: Long) {
                    if (!failed) native.seekTo(position.toInt())
                }

                override fun snapshot(): AudioPlaybackState =
                    if (failed) {
                        AudioPlaybackState(failed = true)
                    } else {
                        AudioPlaybackState(native.currentPosition.toLong(), native.duration.toLong(), native.isPlaying)
                    }
            }
        }
    DisposableEffect(player) {
        onDispose {
            AudioPlayback.release(player)
            native.release()
            file.delete()
        }
    }
    return player
}
