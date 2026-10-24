package io.github.valerait.ghostlink.data

import io.github.valerait.ghostlink.data.settings.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class AppLanguageTest {
    @Test
    fun `system language keeps russian and english devices as they are`() {
        assertNull(AppLanguage.SYSTEM.overrideFor(Locale.forLanguageTag("ru-RU")))
        assertNull(AppLanguage.SYSTEM.overrideFor(Locale.forLanguageTag("en-GB")))
    }

    @Test
    fun `system language falls back to english for other languages`() {
        assertEquals(Locale.ENGLISH, AppLanguage.SYSTEM.overrideFor(Locale.forLanguageTag("de-DE")))
        assertEquals(Locale.ENGLISH, AppLanguage.SYSTEM.overrideFor(Locale.forLanguageTag("uk-UA")))
    }

    @Test
    fun `explicit choice wins over the device language`() {
        val german = Locale.forLanguageTag("de-DE")
        assertEquals("ru", AppLanguage.RUSSIAN.overrideFor(german)?.language)
        assertEquals("en", AppLanguage.ENGLISH.overrideFor(Locale.forLanguageTag("ru-RU"))?.language)
    }

    @Test
    fun `missing or unknown value means system language`() {
        AppLanguage.entries.forEach { assertEquals(it, AppLanguage.parse(it.name)) }
        assertEquals(AppLanguage.SYSTEM, AppLanguage.parse(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.parse("KLINGON"))
    }
}
