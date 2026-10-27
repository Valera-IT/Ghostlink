package io.github.valerait.ghostlink

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.getStackTraceAddresses
import kotlin.native.setUnhandledExceptionHook
import kotlin.native.terminateWithUnhandledException

/** Куда отдать необработанное исключение Kotlin перед падением: Crashlytics на стороне Swift. */
interface CrashReporter {
    fun recordFatal(
        name: String,
        reason: String,
        addresses: List<Long>,
    )
}

/** Перед падением от необработанного исключения Kotlin отдаёт его [reporter]. */
@OptIn(ExperimentalNativeApi::class)
internal fun reportUnhandledExceptions(reporter: CrashReporter) {
    setUnhandledExceptionHook { throwable ->
        val name = throwable::class.qualifiedName ?: "Throwable"
        // Верхние кадры — конструкторы самого исключения (Throwable#<init>, …), а по верхнему кадру
        // Crashlytics называет и группирует проблему: начинаем с места, где исключение бросили.
        val frames = throwable.getStackTrace()
        val skip = frames.indexOfFirst { frame -> "#<init>" !in frame || ("kfun:kotlin." !in frame && "kfun:$name#" !in frame) }
        reporter.recordFatal(
            name = name,
            reason = throwable.message.orEmpty(),
            addresses = throwable.getStackTraceAddresses().drop(skip.coerceAtLeast(0)),
        )
        terminateWithUnhandledException(throwable)
    }
}
