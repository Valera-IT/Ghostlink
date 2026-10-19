package io.github.valerait.ghostlink.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.BinarySize.Companion.bits

/**
 * Шифрование ключа аккаунта при входе на новом устройстве — порт android-версии на cryptography-kotlin.
 *
 * ECDH P-256 → HKDF-SHA256 (salt = 32 нуля, info = "ghostlink-link-v1" || requestId) → AES-256-GCM,
 * AAD = requestId. Формат payload: ephemeralPublicKey (65) || iv (12) || ciphertext+tag —
 * cryptography-kotlin сам кладёт iv перед шифротекстом, так что формат совпадает с Android.
 */
object LinkCrypto {
    private val provider = CryptographyProvider.Default
    private val ecdh = provider.get(ECDH)
    private val hkdf = provider.get(HKDF)
    private val aesGcm = provider.get(AES.GCM)
    private val INFO = "ghostlink-link-v1".encodeToByteArray()
    private const val POINT_SIZE = 65

    suspend fun generateKeyPair(): ECDH.KeyPair = ecdh.keyPairGenerator(EC.Curve.P256).generateKey()

    /** Несжатая точка 0x04 || X || Y. */
    suspend fun encodePublicKey(key: ECDH.PublicKey): ByteArray = key.encodeToByteArray(EC.PublicKey.Format.RAW)

    suspend fun encrypt(recipientPublicKey: ByteArray, requestId: String, plaintext: ByteArray): ByteArray {
        val recipient = decodePublicKey(recipientPublicKey)
        val ephemeral = generateKeyPair()
        val key = deriveKey(ephemeral.privateKey, recipient, requestId)
        return encodePublicKey(ephemeral.publicKey) +
            key.cipher().encrypt(plaintext, associatedData = requestId.encodeToByteArray())
    }

    /** Бросает исключение, если payload подделан или зашифрован не для этого ключа. */
    suspend fun decrypt(privateKey: ECDH.PrivateKey, requestId: String, payload: ByteArray): ByteArray {
        require(payload.size > POINT_SIZE + 12) { "Payload too short" }
        val sender = decodePublicKey(payload.copyOfRange(0, POINT_SIZE))
        val key = deriveKey(privateKey, sender, requestId)
        return key.cipher().decrypt(payload.copyOfRange(POINT_SIZE, payload.size), associatedData = requestId.encodeToByteArray())
    }

    // CryptoKit сам проверяет, что точка лежит на кривой.
    private suspend fun decodePublicKey(raw: ByteArray): ECDH.PublicKey {
        require(raw.size == POINT_SIZE && raw[0] == 0x04.toByte()) { "Not an uncompressed P-256 point" }
        return ecdh.publicKeyDecoder(EC.Curve.P256).decodeFromByteArray(EC.PublicKey.Format.RAW, raw)
    }

    private suspend fun deriveKey(privateKey: ECDH.PrivateKey, publicKey: ECDH.PublicKey, requestId: String): AES.GCM.Key {
        val shared = privateKey.sharedSecretGenerator().generateSharedSecretToByteArray(publicKey)
        val keyBytes = hkdf.secretDerivation(
            digest = SHA256,
            outputSize = 256.bits,
            salt = ByteArray(32),
            info = INFO + requestId.encodeToByteArray(),
        ).deriveSecretToByteArray(shared)
        return aesGcm.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, keyBytes)
    }
}
