package io.github.valerait.ghostlink.ui.devices

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.github.valerait.ghostlink.R

fun platformIcon(platform: String): ImageVector = when (platform) {
    "android" -> Icons.Outlined.PhoneAndroid
    "ios" -> Icons.Outlined.PhoneIphone
    "macos" -> Icons.Outlined.Laptop
    "windows", "linux" -> Icons.Outlined.Computer
    "web" -> Icons.Outlined.Language
    else -> Icons.Outlined.Devices
}

@Composable
fun platformLabel(platform: String): String = when (platform) {
    "android" -> "Android"
    "ios" -> "iOS"
    "macos" -> "macOS"
    "windows" -> "Windows"
    "linux" -> "Linux"
    "web" -> stringResource(R.string.platform_web)
    else -> stringResource(R.string.platform_unknown)
}
