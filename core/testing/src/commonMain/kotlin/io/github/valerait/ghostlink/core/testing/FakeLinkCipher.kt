package io.github.valerait.ghostlink.core.testing

import io.github.valerait.ghostlink.core.domain.crypto.LinkCipher
import io.github.valerait.ghostlink.core.domain.crypto.LinkKeyPair
import io.github.valerait.ghostlink.core.model.LinkRequestId
import kotlin.random.Random

/**
 * Шифр без криптографии: payload — это публичный ключ, id заявки и открытый текст подряд.
 * Ничего не скрывает, но, как настоящий, не расшифровывается чужим ключом или для другой заявки.
 */
object FakeLinkCipher : LinkCipher {
    // Форма несжатой точки P-256 (0x04 || X || Y), иначе LinkCode не примет ключ из QR.
    private const val KEY_SIZE = 65
    private const val UNCOMPRESSED_POINT: Byte = 0x04

    override fun newKeyPair(): LinkKeyPair = KeyPair(Random.nextBytes(KEY_SIZE).also { it[0] = UNCOMPRESSED_POINT })

    override fun encrypt(
        recipientPublicKey: ByteArray,
        requestId: LinkRequestId,
        plaintext: ByteArray,
    ): ByteArray = header(recipientPublicKey, requestId) + plaintext

    private fun header(
        publicKey: ByteArray,
        requestId: LinkRequestId,
    ): ByteArray = publicKey + requestId.value.encodeToByteArray()

    private class KeyPair(
        override val publicKey: ByteArray,
    ) : LinkKeyPair {
        override fun decrypt(
            requestId: LinkRequestId,
            payload: ByteArray,
        ): ByteArray {
            val header = header(publicKey, requestId)
            require(payload.size >= header.size && payload.copyOfRange(0, header.size).contentEquals(header)) {
                "Payload is not for this key or request"
            }
            return payload.copyOfRange(header.size, payload.size)
        }
    }
}
