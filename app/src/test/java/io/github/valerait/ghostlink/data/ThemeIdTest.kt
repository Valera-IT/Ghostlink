package io.github.valerait.ghostlink.data

import io.github.valerait.ghostlink.data.settings.ThemeId
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeIdTest {
    @Test
    fun `stored name parses back to the same theme`() {
        ThemeId.entries.forEach { assertEquals(it, ThemeId.parse(it.name)) }
    }

    @Test
    fun `missing or unknown value falls back to default`() {
        assertEquals(ThemeId.DEFAULT, ThemeId.parse(null))
        assertEquals(ThemeId.DEFAULT, ThemeId.parse("RETRO_WAVE"))
    }
}
