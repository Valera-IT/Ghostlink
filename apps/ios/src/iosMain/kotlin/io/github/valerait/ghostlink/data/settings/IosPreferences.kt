package io.github.valerait.ghostlink.data.settings

import io.github.valerait.ghostlink.core.designsystem.ThemeId
import io.github.valerait.ghostlink.core.ui.AppLanguage
import io.github.valerait.ghostlink.core.ui.LanguageSettings
import io.github.valerait.ghostlink.core.ui.ThemeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults

/** Выбранная тема. Хранится только на этом устройстве, как и в Telegram. */
class ThemePreferences : ThemeSettings {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val _theme = MutableStateFlow(ThemeId.parse(defaults.stringForKey(KEY_THEME)))
    override val theme: StateFlow<ThemeId> = _theme.asStateFlow()

    override fun setTheme(theme: ThemeId) {
        _theme.value = theme
        defaults.setObject(theme.name, KEY_THEME)
    }

    private companion object {
        const val KEY_THEME = "theme"
    }
}

/**
 * Выбранный язык. Compose Resources и форматтеры берут язык из AppleLanguages, поэтому выбор записывается
 * и туда: [SYSTEM][AppLanguage.SYSTEM] убирает ключ, и iOS снова отдаёт языки из настроек устройства.
 */
class LanguagePreferences : LanguageSettings {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val _language = MutableStateFlow(AppLanguage.parse(defaults.stringForKey(KEY_LANGUAGE)))
    override val language: StateFlow<AppLanguage> = _language.asStateFlow()

    init {
        applyToSystem(_language.value)
    }

    override fun setLanguage(language: AppLanguage) {
        defaults.setObject(language.name, KEY_LANGUAGE)
        applyToSystem(language)
        _language.value = language
    }

    private fun applyToSystem(language: AppLanguage) {
        when (val code = language.code) {
            null -> defaults.removeObjectForKey(KEY_APPLE_LANGUAGES)
            else -> defaults.setObject(listOf(code), KEY_APPLE_LANGUAGES)
        }
    }

    private companion object {
        const val KEY_LANGUAGE = "language"
        const val KEY_APPLE_LANGUAGES = "AppleLanguages"
    }
}

/** Код языка, который навязываем iOS, или null — оставить системный. Другим языкам Compose Resources дадут английский. */
private val AppLanguage.code: String?
    get() =
        when (this) {
            AppLanguage.SYSTEM -> null
            AppLanguage.RUSSIAN -> "ru"
            AppLanguage.ENGLISH -> "en"
        }
