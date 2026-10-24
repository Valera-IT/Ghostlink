package io.github.valerait.ghostlink.core.ui

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant
import kotlin.time.toJavaInstant

// Язык интерфейса Android-приложение кладёт в Locale.getDefault() (см. LanguagePreferences.wrap).

actual fun Instant.localDay(): LocalDay {
    val date = toJavaInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    return LocalDay(year = date.year, epochDay = date.toEpochDay())
}

internal actual fun Instant.formatLocal(pattern: String): String =
    toJavaInstant().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
