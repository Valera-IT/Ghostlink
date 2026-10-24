package io.github.valerait.ghostlink.core.designsystem

/** Тема оформления. Все темы тёмные, неоновые. */
enum class ThemeId {
    TOXIC_YELLOW,
    NEON_SUNSET,
    ICEWIRE,
    ACID_CIRCUIT,
    AMBER_ALERT,
    CUSTOM_SPECTRUM,
    ;

    companion object {
        val DEFAULT = TOXIC_YELLOW

        /** Неизвестное или пустое значение (например, тему удалили в новой версии) — тема по умолчанию. */
        fun parse(value: String?): ThemeId = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
