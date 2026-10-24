package io.github.valerait.ghostlink.core.platform

import android.content.Context
import android.os.Build
import android.provider.Settings
import io.github.valerait.ghostlink.core.model.DeviceId
import io.github.valerait.ghostlink.core.model.Platform
import io.github.valerait.ghostlink.core.model.ThisDevice
import java.util.UUID

/** Это устройство: постоянный id и название для списка «Устройства». */
internal fun androidThisDevice(context: Context): ThisDevice {
    val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)
    val id =
        prefs.getString("id", null) ?: UUID.randomUUID().toString().replace("-", "").also {
            prefs.edit().putString("id", it).apply()
        }
    // Имя, которое пользователь задал в настройках телефона, понятнее модели.
    val userName = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
    val model =
        if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) {
            Build.MODEL
        } else {
            "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        }
    return ThisDevice(DeviceId(id), userName?.takeIf { it.isNotBlank() } ?: model, Platform.Android)
}
