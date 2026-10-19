package io.github.valerait.ghostlink

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import io.github.valerait.ghostlink.crypto.LinkCrypto
import io.github.valerait.ghostlink.model.AccountKey
import io.github.valerait.ghostlink.model.LinkCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LinkCryptoCompatTest {
    // Сгенерировано android-реализацией (java.security): ключ PKCS#8 и payload для requestId ниже.
    private val androidPrivateKey = "3041020100301306072a8648ce3d020106082a8648ce3d0301070427302502010104202d823ab982c53c34b62634e0c5b9867db52acf4e88f4840ca7fdb58faa2b1a03".hexToByteArray()
    private val androidPayload = "04e8848f86ebcb7ce192b6952405a28b2bbd9942691b745bc1fbb114cc83697dd3872c89fc3003da9c96fce03e4d9527d42c17f59f5f2045a9f15897e799f145461534d4979a97f28930257c746fac12842d44bd0951b006b556dd565a6edf9514684bff9dc6eaf4bbe07af5c26f9f8bcdb587b25f81".hexToByteArray()
    private val requestId = "AbCdEf0123456789XyZw"
    private val plaintext = ByteArray(25) { it.toByte() }

    @Test
    fun decryptsPayloadMadeOnAndroid() = runTest {
        val key = CryptographyProvider.Default.get(ECDH).privateKeyDecoder(EC.Curve.P256)
            .decodeFromByteArray(EC.PrivateKey.Format.DER, androidPrivateKey)
        assertContentEquals(plaintext, LinkCrypto.decrypt(key, requestId, androidPayload))
    }

    @Test
    fun roundTripAndBindingToRequestId() = runTest {
        val pair = LinkCrypto.generateKeyPair()
        val payload = LinkCrypto.encrypt(LinkCrypto.encodePublicKey(pair.publicKey), requestId, plaintext)
        assertContentEquals(plaintext, LinkCrypto.decrypt(pair.privateKey, requestId, payload))
        assertFailsWith<Throwable> { LinkCrypto.decrypt(pair.privateKey, "OtherRequest0000000", payload) }
    }

    @Test
    fun linkCodeMatchesAndroidFormat() = runTest {
        val pub = "040c343117874fedbd5e33ccffe3068015e85cb1dc2967bb038833aa539a421e9b5983656cca05ef5a86cf74cddd74756d249ed2a1cf393b925317adc42b86fe5d".hexToByteArray()
        val uri = LinkCode(requestId, pub).toUri()
        assertEquals(LinkCode(requestId, pub), LinkCode.parse(uri))
        assertEquals(false, uri.contains('='))
    }

    @Test
    fun accountKeyFormat() {
        val key = AccountKey(ByteArray(25) { it.toByte() })
        assertEquals("0001020304050607@ghostlink.invalid", key.email)
        assertEquals(key, AccountKey.parseRecoveryCode(key.toRecoveryCode().lowercase()))
        assertEquals(8, key.toRecoveryCode().split('-').size)
    }
}
