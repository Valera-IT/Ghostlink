package io.github.valerait.ghostlink.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class InitialsTest {
    @Test
    fun takesFirstLettersOfTheFirstTwoWords() {
        assertEquals("ВМ", initials("Валера Махнович"))
        assertEquals("А", initials("аня"))
    }

    @Test
    fun skipsPunctuation() {
        assertEquals("ПK", initials("Призрак #Kq7x"))
        assertEquals("?", initials("  "))
    }
}
