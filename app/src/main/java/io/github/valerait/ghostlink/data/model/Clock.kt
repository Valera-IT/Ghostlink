package io.github.valerait.ghostlink.data.model

/** Текущее время в миллисекундах; в тестах подменяется. */
fun interface Clock {
    fun now(): Long

    companion object {
        val System = Clock(java.lang.System::currentTimeMillis)
    }
}
