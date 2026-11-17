package io.github.valerait.ghostlink.feature.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.valerait.ghostlink.core.domain.repository.MediaStore
import io.github.valerait.ghostlink.core.domain.usecase.PhotoDraft
import io.github.valerait.ghostlink.core.model.Attachment
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.platform.AudioPlayback
import io.github.valerait.ghostlink.core.platform.RecordingPlayer
import io.github.valerait.ghostlink.core.platform.rememberAudioPlayer
import io.github.valerait.ghostlink.core.platform.rememberAudioRecorder
import io.github.valerait.ghostlink.core.platform.rememberVideoRecorder
import io.github.valerait.ghostlink.core.ui.resources.Res
import io.github.valerait.ghostlink.core.ui.resources.chat_photo
import io.github.valerait.ghostlink.core.ui.resources.chat_record_cancel
import io.github.valerait.ghostlink.core.ui.resources.chat_record_close
import io.github.valerait.ghostlink.core.ui.resources.chat_record_hold
import io.github.valerait.ghostlink.core.ui.resources.chat_record_pause
import io.github.valerait.ghostlink.core.ui.resources.chat_record_play
import io.github.valerait.ghostlink.core.ui.resources.chat_record_progress
import io.github.valerait.ghostlink.core.ui.resources.chat_record_unavailable
import io.github.valerait.ghostlink.core.ui.resources.chat_video
import io.github.valerait.ghostlink.core.ui.resources.chat_voice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun RecordingControls(
    onAttach: (PhotoDraft) -> Unit,
    onFailed: () -> Unit,
) {
    var holding by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    val recorder =
        rememberAudioRecorder(onRecorded = {
            holding = false
            onAttach(PhotoDraft(it.bytes, byteArrayOf(), 1, 1, "voice", it.duration, it.waveform))
        }, onFailed = {
            holding = false
            onFailed()
        })
    val video =
        rememberVideoRecorder(onRecorded = {
            onAttach(PhotoDraft(it.bytes, it.thumbnail, it.width, it.height, "video", it.duration))
        }, onFailed = onFailed)
    val lifecycle = koinInject<io.github.valerait.ghostlink.core.platform.AppLifecycle>()
    val foreground by lifecycle.isForeground.collectAsState()
    LaunchedEffect(foreground) {
        if (!foreground) {
            holding = false
            recorder.cancel()
        }
    }
    LaunchedEffect(holding) {
        elapsed = 0
        while (holding && elapsed < Attachment.RECORDING_MAX_MS) {
            delay(1000)
            elapsed += 1000
        }
        if (holding) {
            holding = false
            recorder.finish()
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).pointerInput(recorder) {
                detectTapGestures(onPress = {
                    holding = true
                    recorder.start()
                    try {
                        if (tryAwaitRelease()) recorder.finish() else recorder.cancel()
                    } finally {
                        holding = false
                    }
                })
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = stringResource(Res.string.chat_record_hold),
                tint = if (holding) MaterialTheme.colorScheme.error else LocalContentColor.current,
            )
        }
        Text(
            if (holding) recordingDuration(elapsed) else stringResource(Res.string.chat_record_hold),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
        if (holding) {
            IconButton(onClick = {
                holding = false
                recorder.cancel()
            }) {
                Icon(Icons.Default.Close, stringResource(Res.string.chat_record_cancel))
            }
        } else {
            IconButton(onClick = video) { Icon(Icons.Default.Videocam, stringResource(Res.string.chat_video)) }
        }
    }
}

@Composable
internal fun RecordingLabel(
    type: String,
    duration: Long,
) {
    val label =
        stringResource(
            when (type) {
                "voice" -> Res.string.chat_voice
                "video" -> Res.string.chat_video
                else -> Res.string.chat_photo
            },
        )
    Text(if (duration > 0) "$label · ${recordingDuration(duration)}" else label)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun RecordingMessage(
    chatId: ChatId,
    attachment: Attachment,
    onLongPress: () -> Unit,
) {
    val media = koinInject<MediaStore>()
    val scope = rememberCoroutineScope()
    var bytes by remember(attachment.key) { mutableStateOf<ByteArray?>(null) }
    var loading by remember(attachment.key) { mutableStateOf(false) }
    var failed by remember(attachment.key) { mutableStateOf(false) }
    val load: () -> Unit = {
        if (!loading) {
            scope.launch {
                loading = true
                failed = false
                try {
                    if (bytes == null) bytes = media.download(chatId, attachment.key)
                    if (attachment.type == "video") {
                        AudioPlayback.pause()
                        activeVideo = attachment.key
                    }
                } catch (
                    e: CancellationException,
                ) {
                    throw e
                } catch (_: io.github.valerait.ghostlink.core.model.GhostlinkError) {
                    failed = true
                } finally {
                    loading = false
                }
            }
        }
    }
    Column(
        Modifier.width(240.dp).combinedClickable(onClick = {
            if (attachment.type == "video" ||
                bytes == null
            ) {
                load()
            }
        }, onLongClick = onLongPress),
    ) {
        if (attachment.type == "video") {
            VideoMessageBody(chatId, attachment, bytes, loading, load)
        } else if (bytes != null) {
            InlineVoice(bytes!!, attachment.duration)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = load, enabled = !loading) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.PlayArrow, stringResource(Res.string.chat_record_play))
                    }
                }
                VoiceWaveform(attachment.waveform, Modifier.weight(1f).height(32.dp))
                Text(recordingDuration(attachment.duration), style = MaterialTheme.typography.labelSmall)
            }
        }
        if (failed) Text(stringResource(Res.string.chat_record_unavailable), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun InlineVoice(
    bytes: ByteArray,
    duration: Long,
) {
    val player = rememberAudioPlayer(bytes)
    var state by remember(player) { mutableStateOf(player.snapshot()) }
    var seeking by remember(player) { mutableStateOf<Float?>(null) }
    val lifecycle = koinInject<io.github.valerait.ghostlink.core.platform.AppLifecycle>()
    val foreground by lifecycle.isForeground.collectAsState()
    LaunchedEffect(player) {
        activeVideo = null
        AudioPlayback.play(player)
        while (true) {
            state = player.snapshot()
            delay(100)
        }
    }
    LaunchedEffect(foreground) { if (!foreground) player.pause() }
    val progressDescription = stringResource(Res.string.chat_record_progress)
    val total = state.duration.takeIf { it > 0 } ?: duration
    val fraction = seeking ?: if (total > 0) (state.position.toFloat() / total).coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            if (state.playing) {
                player.pause()
            } else {
                activeVideo = null
                AudioPlayback.play(player)
            }
        }, enabled = !state.failed) {
            Icon(
                if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                stringResource(if (state.playing) Res.string.chat_record_pause else Res.string.chat_record_play),
            )
        }
        Column(Modifier.weight(1f)) {
            Slider(
                value = fraction,
                colors =
                    SliderDefaults.colors(
                        thumbColor = LocalContentColor.current,
                        activeTrackColor = LocalContentColor.current,
                        inactiveTrackColor = LocalContentColor.current.copy(alpha = 0.25f),
                    ),
                onValueChange = { seeking = it },
                onValueChangeFinished = {
                    seeking?.let { player.seek((it * total).toLong()) }
                    seeking = null
                },
                enabled = !state.failed,
                modifier = Modifier.semantics { contentDescription = progressDescription },
            )
            Text(
                "${recordingDuration((fraction * total).toLong())} / ${recordingDuration(total)}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
    if (state.failed) Text(stringResource(Res.string.chat_record_unavailable), style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun VoiceWaveform(
    waveform: List<Int>,
    modifier: Modifier,
) {
    val color = LocalContentColor.current
    Canvas(modifier) {
        val bars = waveform.ifEmpty { listOf(10) }
        bars.forEachIndexed { i, raw ->
            val x = (i + .5f) * size.width / bars.size
            val h = size.height * raw.coerceIn(5, 100) / 100
            drawLine(color, Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), strokeWidth = 2.dp.toPx())
        }
    }
}

@Composable
private fun InlineVideo(
    bytes: ByteArray,
    onStop: () -> Unit,
) {
    val lifecycle = koinInject<io.github.valerait.ghostlink.core.platform.AppLifecycle>()
    val foreground by lifecycle.isForeground.collectAsState()
    LaunchedEffect(foreground) { if (!foreground) onStop() }
    RecordingPlayer(bytes, video = true, modifier = Modifier.fillMaxSize())
}

@Suppress("MagicNumber") // Формат минут и секунд.
private fun recordingDuration(ms: Long): String {
    val seconds = ms / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

private var activeVideo by mutableStateOf<io.github.valerait.ghostlink.core.model.MediaKey?>(null)

@Composable
private fun VideoMessageBody(
    chatId: ChatId,
    attachment: Attachment,
    bytes: ByteArray?,
    loading: Boolean,
    onLoad: () -> Unit,
) {
    Box(Modifier.size(220.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
        if (bytes != null && activeVideo == attachment.key) {
            InlineVideo(bytes!!, onStop = { activeVideo = null })
        } else {
            ChatPhoto(chatId, attachment.thumbKey, Modifier.fillMaxSize())
            IconButton(onClick = {
                AudioPlayback.pause()
                onLoad()
            }) {
                Icon(
                    Icons.Default.PlayArrow,
                    stringResource(Res.string.chat_record_play),
                    tint = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
        if (loading) CircularProgressIndicator(Modifier.size(32.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(recordingDuration(attachment.duration), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        if (activeVideo == attachment.key && bytes != null) {
            IconButton(onClick = { activeVideo = null }) {
                Icon(Icons.Default.Close, stringResource(Res.string.chat_record_close))
            }
        }
    }
}
