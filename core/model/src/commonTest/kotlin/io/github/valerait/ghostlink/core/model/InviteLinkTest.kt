package io.github.valerait.ghostlink.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InviteLinkTest {
    @Test
    fun createdLinkParsesBackToTheSameUserId() {
        val id = UserId("Xk3pQ9aZbT2uV7wY1cD4eF5gH6i2")
        assertEquals(id, InviteLink.parse(InviteLink.create(id)))
    }

    @Test
    fun toleratesSurroundingWhitespaceAndTrailingSlash() {
        assertEquals(UserId("abc"), InviteLink.parse("  ghostlink://add/abc/ \n"))
    }

    @Test
    fun rejectsForeignCodes() {
        assertNull(InviteLink.parse("https://example.com/add/abc"))
        assertNull(InviteLink.parse("WIFI:S:home;T:WPA;P:secret;;"))
        assertNull(InviteLink.parse(null))
    }

    @Test
    fun rejectsMalformedIds() {
        assertNull(InviteLink.parse("ghostlink://add/"))
        assertNull(InviteLink.parse("ghostlink://add/../users"))
        assertNull(InviteLink.parse("ghostlink://add/a b"))
    }
}
