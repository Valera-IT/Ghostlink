package io.github.valerait.ghostlink.app

import io.github.valerait.ghostlink.platform.LocalSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Тема оформления. Все темы тёмные, неоновые — те же, что на Android. */
enum class ThemeId(val title: String) {
    TOXIC_YELLOW("Toxic Yellow"),
    NEON_SUNSET("Neon Sunset"),
    ICEWIRE("Icewire"),
    ACID_CIRCUIT("Acid Circuit"),
    AMBER_ALERT("Amber Alert"),
    CUSTOM_SPECTRUM("Custom Spectrum");

    companion object {
        val DEFAULT = TOXIC_YELLOW

        fun parse(value: String?): ThemeId = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/** Выбранная тема. Хранится только на этом устройстве. */
object ThemeStore {
    private const val KEY = "theme"
    private val _theme = MutableStateFlow(ThemeId.parse(LocalSettings.getString(KEY)))
    val theme: StateFlow<ThemeId> = _theme.asStateFlow()

    fun set(theme: ThemeId) {
        _theme.value = theme
        LocalSettings.putString(KEY, theme.name)
    }
}
