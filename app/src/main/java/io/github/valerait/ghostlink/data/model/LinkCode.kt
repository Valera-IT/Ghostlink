package io.github.valerait.ghostlink.data.model

import java.util.Base64

/**
 * Код входа на новом устройстве: ghostlink://link/<requestId>.<publicKey>.
 * Новое устройство показывает его, устройство с аккаунтом сканирует.
 * Публичный ключ берётся только из QR, а не из Firestore: подменить его через базу нельзя.
 */
data class LinkCode(val requestId: String, val publicKey: ByteArray) {

    fun toUri(): String = PREFIX + requestId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey)

    override fun equals(other: Any?): Boolean =
        other is LinkCode && other.requestId == requestId && other.publicKey.contentEquals(publicKey)

    override fun hashCode(): Int = 31 * requestId.hashCode() + publicKey.contentHashCode()

    companion object {
        private const val PREFIX = "ghostlink://link/"
        private val REQUEST_ID = Regex("[A-Za-z0-9]{16,64}")

        fun parse(raw: String?): LinkCode? {
            val value = raw?.trim() ?: return null
            if (!value.startsWith(PREFIX)) return null
            val (id, key) = value.removePrefix(PREFIX).split('.').takeIf { it.size == 2 } ?: return null
            if (!REQUEST_ID.matches(id)) return null
            val publicKey = runCatching { Base64.getUrlDecoder().decode(key) }.getOrNull() ?: return null
            // Несжатая точка P-256: 0x04 || X || Y.
            if (publicKey.size != 65 || publicKey[0] != 0x04.toByte()) return null
            return LinkCode(id, publicKey)
        }
    }
}

/** Что можно отсканировать в Ghostlink. */
sealed interface ScannedCode {
    data class Invite(val userId: String) : ScannedCode
    data class Link(val code: LinkCode) : ScannedCode

    companion object {
        fun parse(raw: String?): ScannedCode? =
            InviteLink.parse(raw)?.let(::Invite) ?: LinkCode.parse(raw)?.let(::Link)
    }
}
