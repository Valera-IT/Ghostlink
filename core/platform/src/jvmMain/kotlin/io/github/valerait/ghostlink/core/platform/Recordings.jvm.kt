package io.github.valerait.ghostlink.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import java.awt.Desktop
import java.io.File

// Полноценное десктопное приложение ещё не реализовано (этап 5).
@Composable
actual fun rememberAudioRecorder(
    onRecorded: (Recording) -> Unit,
    onFailed: () -> Unit,
): AudioRecorder =
    object : AudioRecorder {
        override fun start() = onFailed()

        override fun finish() = Unit

        override fun cancel() = Unit
    }

@Composable
actual fun rememberVideoRecorder(
    onRecorded: (Recording) -> Unit,
    onFailed: () -> Unit,
): () -> Unit = onFailed

@Composable
actual fun RecordingPlayer(
    bytes: ByteArray,
    video: Boolean,
    modifier: Modifier,
) {
    val file =
        remember(bytes) {
            File.createTempFile("ghostlink-", if (video) ".mp4" else ".m4a").apply {
                writeBytes(bytes)
                deleteOnExit()
                Desktop.getDesktop().open(this)
            }
        }
    DisposableEffect(file) { onDispose { file.delete() } }
}

@Composable
actual fun rememberAudioPlayer(bytes: ByteArray): AudioPlayer =
    object : AudioPlayer {
        override fun play() = Unit

        override fun pause() = Unit

        override fun seek(position: Long) = Unit

        override fun snapshot() = AudioPlaybackState(failed = true)
    }
