package io.github.valerait.ghostlink.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeIdTest {
    @Test
    fun storedNameParsesBackToTheSameTheme() {
        ThemeId.entries.forEach { assertEquals(it, ThemeId.parse(it.name)) }
    }

    @Test
    fun missingOrUnknownValueFallsBackToDefault() {
        assertEquals(ThemeId.DEFAULT, ThemeId.parse(null))
        assertEquals(ThemeId.DEFAULT, ThemeId.parse("RETRO_WAVE"))
    }
}
