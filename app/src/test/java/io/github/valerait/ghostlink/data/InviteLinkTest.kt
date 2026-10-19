package io.github.valerait.ghostlink.data

import io.github.valerait.ghostlink.data.model.InviteLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InviteLinkTest {
    @Test
    fun `created link parses back to the same user id`() {
        val id = "Xk3pQ9aZbT2uV7wY1cD4eF5gH6i2"
        assertEquals(id, InviteLink.parse(InviteLink.create(id)))
    }

    @Test
    fun `tolerates surrounding whitespace and trailing slash`() {
        assertEquals("abc", InviteLink.parse("  ghostlink://add/abc/ \n"))
    }

    @Test
    fun `rejects foreign codes`() {
        assertNull(InviteLink.parse("https://example.com/add/abc"))
        assertNull(InviteLink.parse("WIFI:S:home;T:WPA;P:secret;;"))
        assertNull(InviteLink.parse(null))
    }

    @Test
    fun `rejects malformed ids`() {
        assertNull(InviteLink.parse("ghostlink://add/"))
        assertNull(InviteLink.parse("ghostlink://add/../users"))
        assertNull(InviteLink.parse("ghostlink://add/a b"))
    }
}
