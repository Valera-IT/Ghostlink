package io.github.valerait.ghostlink.core.model

/** Это устройство: постоянный id и название для списка «Устройства». Значение даёт платформа. */
data class ThisDevice(
    val id: DeviceId,
    val name: String,
    val platform: Platform,
)
