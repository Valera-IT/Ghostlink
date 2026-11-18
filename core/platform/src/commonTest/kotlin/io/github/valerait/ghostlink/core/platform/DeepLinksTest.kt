package io.github.valerait.ghostlink.core.platform

import io.github.valerait.ghostlink.core.model.ChatId
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

    @Test
    fun `notification opens its chat`() {
        val links = DeepLinks()
        links.open(DeepLinks.chatLink(ChatId("alice_bob")))
        assertEquals(ChatId("alice_bob"), links.pendingChat.value)
        assertNull(links.pendingInvite.value)

        links.chatHandled()
        assertNull(links.pendingChat.value)
    }

    @Test
    fun `malformed chat links are ignored`() {
        val links = DeepLinks()
        links.open("ghostlink://chat/")
        links.open("ghostlink://chat/../users")
        links.open("chat/alice_bob")
        assertNull(links.pendingChat.value)
    }
}
