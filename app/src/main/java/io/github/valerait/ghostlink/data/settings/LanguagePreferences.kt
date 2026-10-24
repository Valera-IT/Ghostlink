package io.github.valerait.ghostlink.data.settings

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Язык интерфейса. [SYSTEM] — русский на русском устройстве, иначе английский. */
enum class AppLanguage {
    SYSTEM,
    RUSSIAN,
    ENGLISH,
    ;

    /**
     * Локаль, которую надо навязать ресурсам, или null — оставить системную.
     * Системную оставляем только для русского и английского: иначе Android нашёл бы русский
     * во втором языке системы, а другим языкам положен английский.
     */
    fun overrideFor(system: Locale): Locale? =
        when (this) {
            SYSTEM -> if (system.language in SUPPORTED) null else Locale.ENGLISH
            RUSSIAN -> Locale.forLanguageTag("ru")
            ENGLISH -> Locale.ENGLISH
        }

    companion object {
        private val SUPPORTED = setOf("ru", "en")

        fun parse(value: String?): AppLanguage = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/** Выбранный язык. Хранится только на этом устройстве, как и тема. */
class LanguagePreferences(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _language = MutableStateFlow(AppLanguage.parse(prefs.getString(KEY_LANGUAGE, null)))
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        _language.value = language
        prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
    }

    /** Контекст Activity с выбранным языком; заодно меняет Locale.getDefault() для форматтеров дат. */
    fun wrap(base: Context): Context {
        val system = Resources.getSystem().configuration.locales[0]
        val locale = language.value.overrideFor(system)
        Locale.setDefault(locale ?: system)
        if (locale == null) return base
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    private companion object {
        const val KEY_LANGUAGE = "language"
    }
}
