package io.github.valerait.ghostlink.data.settings

import android.content.Context
import io.github.valerait.ghostlink.core.designsystem.ThemeId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Выбранная тема. Хранится только на этом устройстве, как и в Telegram. */
class ThemePreferences(
    context: Context,
) {
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
