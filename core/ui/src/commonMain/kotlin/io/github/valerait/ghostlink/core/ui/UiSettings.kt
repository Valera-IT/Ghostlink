package io.github.valerait.ghostlink.core.ui

import io.github.valerait.ghostlink.core.designsystem.ThemeId
import kotlinx.coroutines.flow.StateFlow

// Настройки интерфейса живут только на этом устройстве; хранит их платформа (SharedPreferences, NSUserDefaults).

interface ThemeSettings {
    val theme: StateFlow<ThemeId>

    fun setTheme(theme: ThemeId)
}

/** Язык интерфейса. [SYSTEM] — русский на русском устройстве, иначе английский. */
enum class AppLanguage {
    SYSTEM,
    RUSSIAN,
    ENGLISH,
    ;

    companion object {
        fun parse(value: String?): AppLanguage = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

interface LanguageSettings {
    val language: StateFlow<AppLanguage>

    fun setLanguage(language: AppLanguage)
}
