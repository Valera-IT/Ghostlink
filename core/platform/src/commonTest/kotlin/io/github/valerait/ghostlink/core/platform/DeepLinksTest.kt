package io.github.valerait.ghostlink.core.platform

import io.github.valerait.ghostlink.core.model.InviteLink
import io.github.valerait.ghostlink.core.model.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinksTest {
    @Test
    fun `invite link waits until it is handled`() {
        val links = DeepLinks()
        links.open(InviteLink.create(UserId("anya")))
        assertEquals(UserId("anya"), links.pendingInvite.value)

        links.inviteHandled()
        assertNull(links.pendingInvite.value)
    }

    @Test
    fun `foreign links are ignored`() {
        val links = DeepLinks()
        links.open(InviteLink.create(UserId("anya")))
        links.open("https://example.com")
        links.open(null)
        assertEquals(UserId("anya"), links.pendingInvite.value)
    }
}
