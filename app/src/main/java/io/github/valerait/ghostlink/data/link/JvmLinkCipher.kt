package io.github.valerait.ghostlink.data.link

import io.github.valerait.ghostlink.core.domain.crypto.LinkCipher
import io.github.valerait.ghostlink.core.domain.crypto.LinkKeyPair
import io.github.valerait.ghostlink.core.model.LinkRequestId
import java.security.KeyPair

/** [LinkCipher] на java.security; на этапе 3.5 его заменит общий код на cryptography-kotlin. */
object JvmLinkCipher : LinkCipher {
    override fun newKeyPair(): LinkKeyPair = JvmLinkKeyPair(LinkCrypto.generateKeyPair())

    override fun encrypt(
        recipientPublicKey: ByteArray,
        requestId: LinkRequestId,
        plaintext: ByteArray,
    ): ByteArray = LinkCrypto.encrypt(recipientPublicKey, requestId.value, plaintext)
}

private class JvmLinkKeyPair(
    private val keyPair: KeyPair,
) : LinkKeyPair {
    override val publicKey: ByteArray = LinkCrypto.encodePublicKey(keyPair.public)

    override fun decrypt(
        requestId: LinkRequestId,
        payload: ByteArray,
    ): ByteArray = LinkCrypto.decrypt(keyPair.private, requestId.value, payload)
}
