package io.github.valerait.ghostlink.core.ui

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import kotlin.time.Instant

actual fun Instant.localDay(): LocalDay {
    val calendar = NSCalendar.currentCalendar
    val date = toNSDate()
    val epoch = NSDate.dateWithTimeIntervalSince1970(0.0)
    val days = calendar.components(NSCalendarUnitDay, fromDate = calendar.startOfDayForDate(epoch), toDate = date, options = 0u).day
    return LocalDay(year = calendar.component(NSCalendarUnitYear, fromDate = date).toInt(), epochDay = days)
}

internal actual fun Instant.formatLocal(pattern: String): String =
    NSDateFormatter()
        .apply {
            locale = NSLocale.currentLocale
            dateFormat = pattern
        }.stringFromDate(toNSDate())

private fun Instant.toNSDate(): NSDate = NSDate.dateWithTimeIntervalSince1970(toEpochMilliseconds() / MILLIS_PER_SECOND)

private const val MILLIS_PER_SECOND = 1000.0
