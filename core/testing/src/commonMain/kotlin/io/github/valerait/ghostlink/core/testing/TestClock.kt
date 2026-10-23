package io.github.valerait.ghostlink.core.testing

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** Часы, которые идут только по команде теста. */
class TestClock(
    var current: Instant = START,
) : Clock {
    override fun now(): Instant = current

    fun advanceBy(duration: Duration) {
        current += duration
    }

    private companion object {
        val START = Instant.fromEpochMilliseconds(1_000_000)
    }
}
