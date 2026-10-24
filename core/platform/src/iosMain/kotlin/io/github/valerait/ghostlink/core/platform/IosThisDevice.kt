package io.github.valerait.ghostlink.core.platform

import io.github.valerait.ghostlink.core.model.DeviceId
import io.github.valerait.ghostlink.core.model.Platform
import io.github.valerait.ghostlink.core.model.ThisDevice
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIDevice

/** Это устройство: постоянный id (живёт до удаления приложения) и имя, которое видно в «Устройствах». */
internal fun iosThisDevice(): ThisDevice {
    val defaults = NSUserDefaults.standardUserDefaults
    val id =
        defaults.stringForKey(DEVICE_ID) ?: NSUUID().UUIDString.replace("-", "").lowercase().also {
            defaults.setObject(it, DEVICE_ID)
        }
    // С iOS 16 без отдельного разрешения UIDevice.name — модель («iPhone»), а не имя, заданное владельцем.
    return ThisDevice(DeviceId(id), UIDevice.currentDevice.name, Platform.Ios)
}

private const val DEVICE_ID = "device_id"
