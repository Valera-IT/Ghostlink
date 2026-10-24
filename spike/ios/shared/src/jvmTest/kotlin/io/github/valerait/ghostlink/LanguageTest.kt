package io.github.valerait.ghostlink

import io.github.valerait.ghostlink.app.AppLanguage
import io.github.valerait.ghostlink.app.EnStrings
import io.github.valerait.ghostlink.app.RuStrings
import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageTest {
    @Test
    fun systemLanguageIsRussianOnlyOnRussianDevices() {
        assertEquals("ru", AppLanguage.SYSTEM.resolve("ru"))
        assertEquals("en", AppLanguage.SYSTEM.resolve("en"))
        assertEquals("en", AppLanguage.SYSTEM.resolve("de"))
    }

    @Test
    fun explicitChoiceWinsOverDeviceLanguage() {
        assertEquals("ru", AppLanguage.RUSSIAN.resolve("de"))
        assertEquals("en", AppLanguage.ENGLISH.resolve("ru"))
    }

    @Test
    fun unknownStoredValueMeansSystem() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.parse(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.parse("KLINGON"))
        AppLanguage.entries.forEach { assertEquals(it, AppLanguage.parse(it.name)) }
    }

    @Test
    fun pluralsOfMembers() {
        assertEquals(listOf("1 участник", "3 участника", "5 участников", "11 участников", "21 участник"), listOf(1, 3, 5, 11, 21).map(RuStrings.members))
        assertEquals(listOf("1 member", "3 members"), listOf(1, 3).map(EnStrings.members))
    }
}
