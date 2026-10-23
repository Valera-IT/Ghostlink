package io.github.valerait.ghostlink.core.testing

import io.github.valerait.ghostlink.core.model.LinkRequestId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class FakeLinkCipherTest {
    private val request = LinkRequestId("request0000000000001")
    private val secret = byteArrayOf(1, 2, 3)

    @Test
    fun decryptsOnlyWithTheRecipientKeyAndTheSameRequest() {
        val recipient = FakeLinkCipher.newKeyPair()
        val payload = FakeLinkCipher.encrypt(recipient.publicKey, request, secret)

        assertContentEquals(secret, recipient.decrypt(request, payload))
        assertFailsWith<IllegalArgumentException> { FakeLinkCipher.newKeyPair().decrypt(request, payload) }
        assertFailsWith<IllegalArgumentException> { recipient.decrypt(LinkRequestId("request0000000000002"), payload) }
    }
}
