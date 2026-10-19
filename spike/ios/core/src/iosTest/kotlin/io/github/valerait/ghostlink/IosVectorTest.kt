package io.github.valerait.ghostlink

import dev.whyoleg.cryptography.algorithms.EC
import io.github.valerait.ghostlink.crypto.LinkCrypto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Генератор вектора «iOS → Android»: шифротекст от провайдера на iOS (CryptoKit/CommonCrypto).
 * Вывод вставлен в CrossPlatformCryptoTest.iosPayload и расшифровывается там кодом Android.
 */
class IosVectorTest {
    @Test
    fun printVector() = runTest {
        val pair = LinkCrypto.generateKeyPair()
        val payload = LinkCrypto.encrypt(LinkCrypto.encodePublicKey(pair.publicKey), "AbCdEf0123456789XyZw", ByteArray(25) { it.toByte() })
        println("IOS_PRIVATE_KEY=" + pair.privateKey.encodeToByteArray(EC.PrivateKey.Format.DER).toHexString())
        println("IOS_PAYLOAD=" + payload.toHexString())
    }
}
