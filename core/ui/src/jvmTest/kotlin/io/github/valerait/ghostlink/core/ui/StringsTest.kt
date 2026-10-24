package io.github.valerait.ghostlink.core.ui

import io.github.valerait.ghostlink.core.ui.resources.Res
import io.github.valerait.ghostlink.core.ui.resources.action_scan_friend
import io.github.valerait.ghostlink.core.ui.resources.group_members
import io.github.valerait.ghostlink.core.ui.resources.link_receive_countdown
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Строки из Compose Resources: как они разбираются и что у каждой есть перевод. */
class StringsTest {
    private val systemLocale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(systemLocale)

    @Test
    fun androidStyleEscapesAndArgumentsAreResolved() =
        runBlocking {
            Locale.setDefault(Locale.ENGLISH)
            assertEquals("Scan a friend's code", getString(Res.string.action_scan_friend))
            assertEquals("Code expires in 4:07", getString(Res.string.link_receive_countdown, 4, "07"))
        }

    @Test
    fun russianPluralsUseFewAndMany() =
        runBlocking {
            Locale.setDefault(Locale.forLanguageTag("ru"))
            assertEquals("3 участника", getPluralString(Res.plurals.group_members, 3, 3))
            assertEquals("5 участников", getPluralString(Res.plurals.group_members, 5, 5))
        }

    @Test
    fun everyTranslatableStringHasARussianVersion() {
        val english = names("values").filterValues { it }.keys
        val russian = names("values-ru").keys
        assertTrue(english.isNotEmpty())
        assertEquals(emptySet(), english - russian, "no Russian translation")
        assertEquals(emptySet(), russian - english, "Russian string without an English one")
    }

    // Compose Resources не снимает Android-экранирование: «friend\'s» так и показался бы с косой чертой.
    @Test
    fun stringsHaveNoAndroidEscapes() {
        listOf("values", "values-ru").forEach { dir ->
            val text = File("src/commonMain/composeResources/$dir/strings.xml").readText()
            assertTrue("\\'" !in text && "\\\"" !in text, "$dir/strings.xml: write ' and \" without a backslash")
        }
    }

    /** name → translatable для всех string и plurals в файле. */
    private fun names(dir: String): Map<String, Boolean> {
        val file = File("src/commonMain/composeResources/$dir/strings.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        return listOf("string", "plurals")
            .flatMap { tag ->
                val nodes = doc.getElementsByTagName(tag)
                (0 until nodes.length).map { nodes.item(it).attributes }
            }.associate { it.getNamedItem("name").nodeValue to (it.getNamedItem("translatable")?.nodeValue != "false") }
    }
}
