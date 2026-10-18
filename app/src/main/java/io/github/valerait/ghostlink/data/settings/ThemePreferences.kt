package io.github.valerait.ghostlink.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Тема оформления. Все темы тёмные, неоновые. */
enum class ThemeId {
    TOXIC_YELLOW,
    NEON_SUNSET,
    ICEWIRE,
    ACID_CIRCUIT,
    AMBER_ALERT,
    CUSTOM_SPECTRUM;

    companion object {
        val DEFAULT = TOXIC_YELLOW

        /** Неизвестное или пустое значение (например, тему удалили в новой версии) — тема по умолчанию. */
        fun parse(value: String?): ThemeId = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/** Выбранная тема. Хранится только на этом устройстве, как и в Telegram. */
@Singleton
class ThemePreferences @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _theme = MutableStateFlow(ThemeId.parse(prefs.getString(KEY_THEME, null)))
    val theme: StateFlow<ThemeId> = _theme.asStateFlow()

    fun setTheme(theme: ThemeId) {
        _theme.value = theme
        prefs.edit().putString(KEY_THEME, theme.name).apply()
    }

    private companion object {
        const val KEY_THEME = "theme"
    }
}
