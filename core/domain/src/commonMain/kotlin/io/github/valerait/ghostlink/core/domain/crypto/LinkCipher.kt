package io.github.valerait.ghostlink.core.domain.crypto

import io.github.valerait.ghostlink.core.model.LinkRequestId

/**
 * Шифрование ключа аккаунта при привязке устройства: ECDH P-256 → HKDF → AES-GCM.
 * Новое устройство создаёт пару ключей и показывает публичный в QR, устройство с аккаунтом шифрует им ключ.
 * [LinkRequestId] входит в вывод ключа и в AAD: шифротекст одной заявки не подойдёт к другой.
 */
interface LinkCipher {
    fun newKeyPair(): LinkKeyPair

    /** Публичный ключ — несжатая точка P-256 из QR. */
    fun encrypt(
        recipientPublicKey: ByteArray,
        requestId: LinkRequestId,
        plaintext: ByteArray,
    ): ByteArray
}

/** Пара ключей нового устройства; закрытый ключ не покидает объект. */
interface LinkKeyPair {
    val publicKey: ByteArray

    /** Бросает исключение, если payload подделан или зашифрован не для этого ключа. */
    fun decrypt(
        requestId: LinkRequestId,
        payload: ByteArray,
    ): ByteArray
}
