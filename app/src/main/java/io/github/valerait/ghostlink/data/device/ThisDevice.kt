package io.github.valerait.ghostlink.data.device

import android.content.Context
import android.os.Build
import android.provider.Settings
import io.github.valerait.ghostlink.core.model.DeviceId
import io.github.valerait.ghostlink.core.model.Platform
import java.util.UUID

/** Это устройство: постоянный id и название для списка «Устройства». */
data class ThisDevice(
    val id: DeviceId,
    val name: String,
    val platform: Platform,
) {
    companion object {
        fun from(context: Context): ThisDevice {
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
    }
}
