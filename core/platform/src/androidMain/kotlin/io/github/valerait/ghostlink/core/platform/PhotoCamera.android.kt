package io.github.valerait.ghostlink.core.platform

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
actual fun rememberPhotoCamera(
    requests: List<ImageRequest>,
    onPicked: (List<PickedImage>) -> Unit,
    onFailed: () -> Unit,
): (() -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picked by rememberUpdatedState(onPicked)
    val failed by rememberUpdatedState(onFailed)
    var file by remember { mutableStateOf<File?>(null) }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val captured = file
            file = null
            if (captured != null) {
                scope.launch {
                    val images =
                        withContext(Dispatchers.IO) {
                            try {
                                if (success) {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", captured)
                                    runCatching { context.contentResolver.readJpegs(uri, requests) }
                                        .onFailure { logWarning("PhotoCamera", "Can't prepare photo", it) }
                                        .getOrNull()
                                } else {
                                    null
                                }
                            } finally {
                                captured.delete()
                            }
                        }
                    if (images != null) {
                        picked(images)
                    } else if (success) {
                        failed()
                    }
                }
            }
        }
    val launchCamera: () -> Unit = {
        runCatching {
            file?.delete()
            val directory = File(context.cacheDir, "camera").apply { mkdirs() }
            val output = File.createTempFile("photo-", ".jpg", directory)
            file = output
            camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.photos", output))
        }.onFailure {
            file?.delete()
            file = null
            logWarning("PhotoCamera", "Can't open camera", it)
            failed()
        }
    }
    val currentLaunch by rememberUpdatedState(launchCamera)
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) currentLaunch() else failed()
        }
    DisposableEffect(Unit) { onDispose { file?.delete() } }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            permission.launch(Manifest.permission.CAMERA)
        }
    }
}
