package io.github.valerait.ghostlink

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import io.github.valerait.ghostlink.crypto.LinkCrypto
import kotlinx.coroutines.test.runTest
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

/**
 * Разведка 2b, пункт 9: все направления между текущим Android-кодом (java.security) и
 * cryptography-kotlin. Направление Android → iOS проверяет LinkCryptoCompatTest на симуляторе.
 */
class CrossPlatformCryptoTest {
    private val requestId = "AbCdEf0123456789XyZw"
    private val plaintext = ByteArray(25) { it.toByte() }

    // Из IosVectorTest на iOS-симуляторе: ключ PKCS#8 и payload для requestId выше.
    private val iosPrivateKey = "308187020100301306072a8648ce3d020106082a8648ce3d030107046d306b020101042078c198b0670b25a1f68b945b8adc2de3dbf30265e6e2554ccedcf231734c4f39a1440342000427a00aa50616c834074f2f52d28b474a99d27b97e2841708068a08ca5168d45845c978f1638939fee7fb2e4052757fe058eb1a2df3f8bae6c603d1fb56aaf1d2".hexToByteArray()
    private val iosPayload = "04332a9217b77aaeaa80ca49fbf6feee30f3af2b736c3adc5f922190e80f9c669d752a8c2fba1562b6da0c17bf77f7dfd43e24baa8367ed0f66ce076329a513dbf3cf1f2fe2554c535806e900a284051849a1e030c5e605c4615d1837f7674b14effa9f04aa9f639d9186755e9b5159a616c8ad321c9".hexToByteArray()

    @Test
    fun androidDecryptsPayloadMadeOnIos() {
        val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(iosPrivateKey))
        assertContentEquals(plaintext, AndroidLinkCrypto.decrypt(key, requestId, iosPayload))
    }

    @Test
    fun androidDecryptsPayloadMadeByKmpOnJvm() = runTest {
        val android = AndroidLinkCrypto.generateKeyPair()
        val payload = LinkCrypto.encrypt(AndroidLinkCrypto.encodePublicKey(android.public), requestId, plaintext)
        assertContentEquals(plaintext, AndroidLinkCrypto.decrypt(android.private, requestId, payload))
    }

    @Test
    fun kmpOnJvmDecryptsPayloadMadeOnAndroid() = runTest {
        val pair = LinkCrypto.generateKeyPair()
        val payload = AndroidLinkCrypto.encrypt(LinkCrypto.encodePublicKey(pair.publicKey), requestId, plaintext)
        assertContentEquals(plaintext, LinkCrypto.decrypt(pair.privateKey, requestId, payload))
    }

    @Test
    fun keysCrossBetweenImplementations() = runTest {
        // Закрытый ключ, созданный на iOS, читается и в cryptography-kotlin на JVM.
        val key = CryptographyProvider.Default.get(ECDH).privateKeyDecoder(EC.Curve.P256)
            .decodeFromByteArray(EC.PrivateKey.Format.DER, iosPrivateKey)
        assertContentEquals(plaintext, LinkCrypto.decrypt(key, requestId, iosPayload))
    }

    @Test
    fun tamperedPayloadIsRejectedByBoth() = runTest {
        val tampered = iosPayload.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }
        val androidKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(iosPrivateKey))
        assertFailsWith<Exception> { AndroidLinkCrypto.decrypt(androidKey, requestId, tampered) }
        assertFailsWith<Exception> { AndroidLinkCrypto.decrypt(androidKey, "OtherRequest0000000", iosPayload) }
    }
}
