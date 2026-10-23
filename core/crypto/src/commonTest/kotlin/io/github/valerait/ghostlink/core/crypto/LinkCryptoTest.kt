package io.github.valerait.ghostlink.core.crypto

import io.github.valerait.ghostlink.core.model.LinkCode
import io.github.valerait.ghostlink.core.model.LinkRequestId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails

class LinkCryptoTest {
    private val requestId = LinkRequestId("AbCdEf0123456789XyZw")
    private val otherRequestId = LinkRequestId("OtherRequest00000000")
    private val secret = "account key bytes".encodeToByteArray()

    @Test
    fun recipientDecryptsWhatWasEncryptedForItsPublicKey() {
        val recipient = LinkCrypto.newKeyPair()
        val payload = LinkCrypto.encrypt(recipient.publicKey, requestId, secret)

        assertContentEquals(secret, recipient.decrypt(requestId, payload))
    }

    @Test
    fun payloadIsBoundToItsRequestId() {
        val recipient = LinkCrypto.newKeyPair()
        val payload = LinkCrypto.encrypt(recipient.publicKey, requestId, secret)

        assertFails { recipient.decrypt(otherRequestId, payload) }
    }

    @Test
    fun anotherKeyCannotDecrypt() {
        val payload = LinkCrypto.encrypt(LinkCrypto.newKeyPair().publicKey, requestId, secret)

        assertFails { LinkCrypto.newKeyPair().decrypt(requestId, payload) }
    }

    @Test
    fun tamperedPayloadIsRejected() {
        val recipient = LinkCrypto.newKeyPair()
        val payload = LinkCrypto.encrypt(recipient.publicKey, requestId, secret)
        payload[payload.lastIndex] = (payload.last() + 1).toByte()

        assertFails { recipient.decrypt(requestId, payload) }
        assertFails { recipient.decrypt(requestId, payload.copyOf(70)) }
    }

    @Test
    fun publicKeyIsAnUncompressedPointThatFitsTheLinkCode() {
        repeat(20) {
            val raw = LinkCrypto.newKeyPair().publicKey
            assertEquals(65, raw.size)
            assertEquals(0x04, raw[0].toInt())
            assertContentEquals(raw, LinkCrypto.encodePublicKey(LinkCrypto.decodePublicKey(raw)))
            val code = LinkCode(requestId, raw)
            assertEquals(code, LinkCode.parse(code.toUri()))
        }
    }

    @Test
    fun pointOffTheCurveIsRejected() {
        val raw = LinkCrypto.newKeyPair().publicKey
        raw[64] = (raw[64] + 1).toByte()

        assertFails { LinkCrypto.decodePublicKey(raw) }
        assertFails { LinkCrypto.encrypt(raw, requestId, secret) }
    }
}
