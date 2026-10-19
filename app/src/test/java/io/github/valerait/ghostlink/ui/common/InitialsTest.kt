package io.github.valerait.ghostlink.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialsTest {
    @Test
    fun `takes first letters of the first two words`() {
        assertEquals("ВМ", initials("Валера Махнович"))
        assertEquals("А", initials("аня"))
    }

    @Test
    fun `skips punctuation`() {
        assertEquals("ПK", initials("Призрак #Kq7x"))
        assertEquals("?", initials("  "))
    }
}
