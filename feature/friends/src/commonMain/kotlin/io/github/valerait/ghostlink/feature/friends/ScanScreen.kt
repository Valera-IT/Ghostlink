package io.github.valerait.ghostlink.feature.friends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.valerait.ghostlink.core.model.ScannedCode
import io.github.valerait.ghostlink.core.ui.resources.Res
import io.github.valerait.ghostlink.core.ui.resources.action_back
import io.github.valerait.ghostlink.core.ui.resources.scan_camera_unavailable
import io.github.valerait.ghostlink.core.ui.resources.scan_hint
import io.github.valerait.ghostlink.core.ui.resources.scan_not_ghostlink
import io.github.valerait.ghostlink.core.ui.resources.scan_open_settings
import io.github.valerait.ghostlink.core.ui.resources.scan_permission_denied
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onScanned: (ScannedCode) -> Unit,
) {
    val currentOnScanned by rememberUpdatedState(onScanned)
    var done by remember { mutableStateOf(false) }
    var foreignCodeAt by remember { mutableLongStateOf(0L) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        QrCamera(
            onBarcode = { raw ->
                if (!done) {
                    val code = ScannedCode.parse(raw)
                    if (code != null) {
                        done = true
                        currentOnScanned(code)
                    } else {
                        foreignCodeAt = Clock.System.now().toEpochMilliseconds()
                    }
                }
            },
            denied = { openSettings -> CameraMessage(Res.string.scan_permission_denied, openSettings) },
            unavailable = { CameraMessage(Res.string.scan_camera_unavailable, onOpenSettings = null) },
        ) {
            Viewfinder()
            ScanHint(foreignCodeAt)
        }
        IconButton(
            onClick = onBack,
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
            modifier = Modifier.statusBarsPadding().padding(4.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
        }
    }
}

/**
 * Камера на весь экран с распознаванием QR. Сама просит разрешение: пока оно есть, поверх камеры
 * рисуется [overlay]; если отказали — [denied] с действием «открыть настройки»; где камеры нет — [unavailable].
 */
@Composable
internal expect fun QrCamera(
    onBarcode: (String) -> Unit,
    denied: @Composable (openSettings: () -> Unit) -> Unit,
    unavailable: @Composable () -> Unit,
    overlay: @Composable () -> Unit,
)

/** Подсказка внизу; пару секунд после чужого QR — «это не код Ghostlink». */
@Composable
private fun ScanHint(foreignCodeAt: Long) {
    var showForeignHint by remember { mutableStateOf(false) }
    LaunchedEffect(foreignCodeAt) {
        if (foreignCodeAt == 0L) return@LaunchedEffect
        showForeignHint = true
        delay(2_000)
        showForeignHint = false
    }

    Column(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White, shape = RoundedCornerShape(16.dp)) {
            Text(
                text = stringResource(if (showForeignHint) Res.string.scan_not_ghostlink else Res.string.scan_hint),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** Затемнение вокруг квадратной рамки прицела. */
@Composable
private fun Viewfinder() {
    val frameColor = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
    ) {
        val side = size.minDimension * 0.7f
        val topLeft = Offset((size.width - side) / 2, (size.height - side) / 2.4f)
        val radius = CornerRadius(32.dp.toPx())
        drawRect(Color.Black.copy(alpha = 0.55f))
        drawRoundRect(Color.Transparent, topLeft, Size(side, side), radius, blendMode = BlendMode.Clear)
        drawRoundRect(frameColor, topLeft, Size(side, side), radius, style = Stroke(width = 4.dp.toPx()))
    }
}

@Composable
private fun CameraMessage(
    text: StringResource,
    onOpenSettings: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.padding(8.dp))
        Text(
            text = stringResource(text),
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (onOpenSettings != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenSettings) { Text(stringResource(Res.string.scan_open_settings)) }
        }
    }
}
