package io.github.valerait.ghostlink.data

import io.github.valerait.ghostlink.data.link.LinkCrypto
import io.github.valerait.ghostlink.data.model.LinkCode
import io.github.valerait.ghostlink.data.model.ScannedCode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkCryptoTest {

    private val secret = "account key bytes".toByteArray()

    @Test
    fun `recipient decrypts what was encrypted for its public key`() {
        val recipient = LinkCrypto.generateKeyPair()
        val publicKey = LinkCrypto.encodePublicKey(recipient.public)

        val payload = LinkCrypto.encrypt(publicKey, "req1", secret)

        assertArrayEquals(secret, LinkCrypto.decrypt(recipient.private, "req1", payload))
    }

    @Test
    fun `payload is bound to its request id`() {
        val recipient = LinkCrypto.generateKeyPair()
        val payload = LinkCrypto.encrypt(LinkCrypto.encodePublicKey(recipient.public), "req1", secret)

        assertFails { LinkCrypto.decrypt(recipient.private, "req2", payload) }
    }

    @Test
    fun `another key cannot decrypt`() {
        val recipient = LinkCrypto.generateKeyPair()
        val payload = LinkCrypto.encrypt(LinkCrypto.encodePublicKey(recipient.public), "req1", secret)

        assertFails { LinkCrypto.decrypt(LinkCrypto.generateKeyPair().private, "req1", payload) }
    }

    @Test
    fun `tampered payload is rejected`() {
        val recipient = LinkCrypto.generateKeyPair()
        val payload = LinkCrypto.encrypt(LinkCrypto.encodePublicKey(recipient.public), "req1", secret)
        payload[payload.size - 1] = (payload.last() + 1).toByte()

        assertFails { LinkCrypto.decrypt(recipient.private, "req1", payload) }
    }

    @Test
    fun `public key survives the raw encoding`() {
        repeat(20) {
            val key = LinkCrypto.generateKeyPair().public
            val raw = LinkCrypto.encodePublicKey(key)
            assertEquals(65, raw.size)
            assertEquals(key, LinkCrypto.decodePublicKey(raw))
        }
    }

    @Test
    fun `point off the curve is rejected`() {
        val raw = LinkCrypto.encodePublicKey(LinkCrypto.generateKeyPair().public)
        raw[64] = (raw[64] + 1).toByte()

        assertFails { LinkCrypto.decodePublicKey(raw) }
    }

    @Test
    fun `link code round-trips through the uri`() {
        val code = LinkCode("AbCdEf0123456789XyZw", LinkCrypto.encodePublicKey(LinkCrypto.generateKeyPair().public))

        assertEquals(code, LinkCode.parse(code.toUri()))
        assertEquals(ScannedCode.Link(code), ScannedCode.parse(code.toUri()))
    }

    @Test
    fun `malformed link codes are ignored`() {
        val key = LinkCode("AbCdEf0123456789XyZw", LinkCrypto.encodePublicKey(LinkCrypto.generateKeyPair().public))
            .toUri().substringAfter('.')

        assertNull(LinkCode.parse("ghostlink://link/AbCdEf0123456789XyZw"))
        assertNull(LinkCode.parse("ghostlink://link/short.$key"))
        assertNull(LinkCode.parse("ghostlink://link/AbCdEf0123456789XyZw.AAAA"))
        assertNull(LinkCode.parse("https://example.com/AbCdEf0123456789XyZw.$key"))
        assertEquals(ScannedCode.Invite("abc"), ScannedCode.parse("ghostlink://add/abc"))
    }

    private fun assertFails(block: () -> Unit) {
        val failed = runCatching(block).isFailure
        assertTrue("Expected an exception", failed)
    }
}
