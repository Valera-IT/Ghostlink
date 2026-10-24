package io.github.valerait.ghostlink.core.ui

import kotlin.time.Instant

/** Календарный день в часовом поясе устройства: [epochDay] — дни от 1970-01-01. */
data class LocalDay(
    val year: Int,
    val epochDay: Long,
)

expect fun Instant.localDay(): LocalDay

/** Форматирует по шаблону в стиле java.time («d MMMM», «EEE») на языке интерфейса. */
internal expect fun Instant.formatLocal(pattern: String): String
