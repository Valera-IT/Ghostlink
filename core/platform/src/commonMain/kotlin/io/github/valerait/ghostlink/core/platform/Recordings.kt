package io.github.valerait.ghostlink.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Локальная запись
не попадает в хранилище до подтверждения отправки. */
class Recording(
    val bytes: ByteArray,
    val duration: Long,
    val waveform: List<Int> = emptyList(),
    val thumbnail: ByteArray = byteArrayOf(),
    val width: Int = 1,
    val height: Int = 1,
)

interface AudioRecorder {
    fun start()

    fun finish()

    fun cancel()
}

@Composable
expect fun rememberAudioRecorder(
    onRecorded: (Recording) -> Unit,
    onFailed: () -> Unit,
): AudioRecorder

/** Системная камера с ограничением в 120 секунд
отмена ничего не отправляет. */
@Composable
expect fun rememberVideoRecorder(
    onRecorded: (Recording) -> Unit,
    onFailed: () -> Unit,
): () -> Unit

/** Проигрывание локального файла, освобождает проигрыватель при закрытии. */
@Composable
expect fun RecordingPlayer(
    bytes: ByteArray,
    video: Boolean,
    modifier: Modifier = Modifier,
)

/** Состояние встроенного аудиопроигрывателя
время в миллисекундах. */
data class AudioPlaybackState(
    val position: Long = 0,
    val duration: Long = 0,
    val playing: Boolean = false,
    val failed: Boolean = false,
)

interface AudioPlayer {
    fun play()

    fun pause()

    fun seek(position: Long)

    fun snapshot(): AudioPlaybackState
}

@Composable
expect fun rememberAudioPlayer(bytes: ByteArray): AudioPlayer

/** Одновременно играет одно голосовое. Вызывается на главном потоке. */
object AudioPlayback {
    private var current: AudioPlayer? = null

    fun play(player: AudioPlayer) {
        if (current !== player) current?.pause()
        current = player
        player.play()
    }

    fun release(player: AudioPlayer) {
        if (current === player) current = null
    }

    fun pause() = current?.pause()
}
