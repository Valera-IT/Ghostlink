package io.github.valerait.ghostlink.core.crypto

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256
import io.github.valerait.ghostlink.core.domain.crypto.LinkCipher
import io.github.valerait.ghostlink.core.domain.crypto.LinkKeyPair
import io.github.valerait.ghostlink.core.model.LinkRequestId

/**
 * Шифрование ключа аккаунта при входе на новом устройстве.
 *
 * Новое устройство показывает в QR свой публичный ключ P-256. Устройство с аккаунтом
 * создаёт одноразовую пару ключей, делает ECDH, выводит ключ AES через HKDF-SHA256
 * (salt — 32 нуля, info = "ghostlink-link-v1" || requestId) и шифрует AES-256-GCM с AAD = requestId:
 * шифротекст нельзя переложить в другую заявку.
 *
 * Формат payload: ephemeralPublicKey (65) || iv (12) || ciphertext+tag. Он совпадает со старым
 * Android-кодом на java.security, это проверяют test vectors.
 */
object LinkCrypto : LinkCipher {
    private const val POINT_SIZE = 65
    private const val IV_SIZE = 12
    private const val SALT_SIZE = 32
    private val INFO = "ghostlink-link-v1".encodeToByteArray()

    private val provider = CryptographyProvider.Default
    private val ecdh = provider.get(ECDH)
    private val hkdf = provider.get(HKDF)
    private val aesGcm = provider.get(AES.GCM)

    override fun newKeyPair(): LinkKeyPair = EcdhLinkKeyPair(ecdh.keyPairGenerator(EC.Curve.P256).generateKeyBlocking())

    override fun encrypt(
        recipientPublicKey: ByteArray,
        requestId: LinkRequestId,
        plaintext: ByteArray,
    ): ByteArray {
        val recipient = decodePublicKey(recipientPublicKey)
        val ephemeral = ecdh.keyPairGenerator(EC.Curve.P256).generateKeyBlocking()
        val key = deriveKey(ephemeral.privateKey, recipient, requestId)
        return encodePublicKey(ephemeral.publicKey) +
            key.cipher().encryptBlocking(plaintext, associatedData = requestId.value.encodeToByteArray())
    }

    /** Бросает исключение, если payload подделан или зашифрован не для этого ключа. */
    internal fun decrypt(
        privateKey: ECDH.PrivateKey,
        requestId: LinkRequestId,
        payload: ByteArray,
    ): ByteArray {
        require(payload.size > POINT_SIZE + IV_SIZE) { "Payload too short" }
        val sender = decodePublicKey(payload.copyOfRange(0, POINT_SIZE))
        val key = deriveKey(privateKey, sender, requestId)
        return key.cipher().decryptBlocking(
            payload.copyOfRange(POINT_SIZE, payload.size),
            associatedData = requestId.value.encodeToByteArray(),
        )
    }

    /** Несжатая точка 0x04 || X || Y — формат QR. */
    internal fun encodePublicKey(key: ECDH.PublicKey): ByteArray = key.encodeToByteArrayBlocking(EC.PublicKey.Format.RAW)

    internal fun decodePublicKey(raw: ByteArray): ECDH.PublicKey {
        require(raw.size == POINT_SIZE && raw[0] == 0x04.toByte()) { "Not an uncompressed P-256 point" }
        // Не все провайдеры проверяют точку сами, а точка вне кривой позволяет вытянуть закрытый ключ.
        require(P256.isOnCurve(raw)) { "Point is not on P-256" }
        return ecdh.publicKeyDecoder(EC.Curve.P256).decodeFromByteArrayBlocking(EC.PublicKey.Format.RAW, raw)
    }

    internal fun decodePrivateKey(der: ByteArray): ECDH.PrivateKey =
        ecdh.privateKeyDecoder(EC.Curve.P256).decodeFromByteArrayBlocking(EC.PrivateKey.Format.DER, der)

    private fun deriveKey(
        privateKey: ECDH.PrivateKey,
        publicKey: ECDH.PublicKey,
        requestId: LinkRequestId,
    ): AES.GCM.Key {
        val shared = privateKey.sharedSecretGenerator().generateSharedSecretToByteArrayBlocking(publicKey)
        val keyBytes =
            hkdf
                .secretDerivation(
                    digest = SHA256,
                    outputSize = 256.bits,
                    salt = ByteArray(SALT_SIZE),
                    info = INFO + requestId.value.encodeToByteArray(),
                ).deriveSecretToByteArrayBlocking(shared)
        return aesGcm.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, keyBytes)
    }
}

private class EcdhLinkKeyPair(
    private val keyPair: ECDH.KeyPair,
) : LinkKeyPair {
    override val publicKey: ByteArray = LinkCrypto.encodePublicKey(keyPair.publicKey)

    override fun decrypt(
        requestId: LinkRequestId,
        payload: ByteArray,
    ): ByteArray = LinkCrypto.decrypt(keyPair.privateKey, requestId, payload)
}
