package io.github.valerait.ghostlink.core.ui

/** Как запущено приложение. Определяет платформа при старте. */
data class AppConfig(
    /** Нет конфига Firebase — работаем на DemoBackend, экраны показывают плашку демо-режима. */
    val demoMode: Boolean,
    /** Версия для экрана настроек. */
    val versionName: String = "",
)
