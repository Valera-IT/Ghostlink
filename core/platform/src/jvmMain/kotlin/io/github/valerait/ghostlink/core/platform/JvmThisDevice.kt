package io.github.valerait.ghostlink.core.platform

import io.github.valerait.ghostlink.core.model.DeviceId
import io.github.valerait.ghostlink.core.model.Platform
import io.github.valerait.ghostlink.core.model.ThisDevice
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.UUID
import java.util.prefs.Preferences

/** Это устройство: id в настройках пользователя ОС, имя компьютера и ОС. */
internal fun jvmThisDevice(): ThisDevice {
    val prefs = Preferences.userRoot().node("io/github/valerait/ghostlink")
    val id =
        prefs.get(DEVICE_ID, null) ?: UUID.randomUUID().toString().replace("-", "").also {
            prefs.put(DEVICE_ID, it)
        }
    val osName = System.getProperty("os.name").orEmpty()
    val name =
        try {
            InetAddress.getLocalHost().hostName.removeSuffix(".local")
        } catch (_: UnknownHostException) {
            osName
        }
    return ThisDevice(DeviceId(id), name, jvmPlatform(osName))
}

internal fun jvmPlatform(osName: String): Platform =
    when {
        osName.startsWith("Mac", ignoreCase = true) -> Platform.MacOs
        osName.startsWith("Windows", ignoreCase = true) -> Platform.Windows
        osName.startsWith("Linux", ignoreCase = true) -> Platform.Linux
        else -> Platform.Unknown
    }

private const val DEVICE_ID = "device_id"
