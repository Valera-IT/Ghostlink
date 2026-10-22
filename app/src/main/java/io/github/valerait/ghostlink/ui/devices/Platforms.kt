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
import io.github.valerait.ghostlink.core.model.Platform

fun platformIcon(platform: Platform): ImageVector =
    when (platform) {
        Platform.Android -> Icons.Outlined.PhoneAndroid
        Platform.Ios -> Icons.Outlined.PhoneIphone
        Platform.MacOs -> Icons.Outlined.Laptop
        Platform.Windows, Platform.Linux -> Icons.Outlined.Computer
        Platform.Web -> Icons.Outlined.Language
        Platform.Unknown -> Icons.Outlined.Devices
    }

@Composable
fun platformLabel(platform: Platform): String =
    when (platform) {
        Platform.Android -> "Android"
        Platform.Ios -> "iOS"
        Platform.MacOs -> "macOS"
        Platform.Windows -> "Windows"
        Platform.Linux -> "Linux"
        Platform.Web -> stringResource(R.string.platform_web)
        Platform.Unknown -> stringResource(R.string.platform_unknown)
    }
