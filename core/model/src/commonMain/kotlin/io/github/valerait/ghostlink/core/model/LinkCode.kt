package io.github.valerait.ghostlink.core.model

import kotlin.io.encoding.Base64

/**
 * Код входа на новом устройстве: ghostlink://link/<requestId>.<publicKey>.
 * Новое устройство показывает его, устройство с аккаунтом сканирует.
 * Публичный ключ берётся только из QR, а не из Firestore: подменить его через базу нельзя.
 */
data class LinkCode(
    val requestId: LinkRequestId,
    val publicKey: ByteArray,
) {
    fun toUri(): String = PREFIX + requestId.value + "." + base64.encode(publicKey)

    override fun equals(other: Any?): Boolean =
        other is LinkCode && other.requestId == requestId && other.publicKey.contentEquals(publicKey)

    override fun hashCode(): Int = 31 * requestId.hashCode() + publicKey.contentHashCode()

    companion object {
        private const val PREFIX = "ghostlink://link/"
        private val REQUEST_ID = Regex("[A-Za-z0-9]{16,64}")

        /** Несжатая точка P-256: 0x04 || X || Y. */
        private const val PUBLIC_KEY_SIZE = 65
        private const val UNCOMPRESSED_POINT: Byte = 0x04

        // Как java.util.Base64.getUrlEncoder().withoutPadding(): при чтении паддинг допускаем.
        private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

        fun parse(raw: String?): LinkCode? {
            val value = raw?.trim() ?: return null
            if (!value.startsWith(PREFIX)) return null
            val (id, key) = value.removePrefix(PREFIX).split('.').takeIf { it.size == 2 } ?: return null
            if (!REQUEST_ID.matches(id)) return null
            val publicKey = runCatching { base64.decode(key) }.getOrNull() ?: return null
            if (publicKey.size != PUBLIC_KEY_SIZE || publicKey[0] != UNCOMPRESSED_POINT) return null
            return LinkCode(LinkRequestId(id), publicKey)
        }
    }
}

/** Что можно отсканировать в Ghostlink. */
sealed interface ScannedCode {
    data class Invite(
        val userId: UserId,
    ) : ScannedCode

    data class Link(
        val code: LinkCode,
    ) : ScannedCode

    companion object {
        fun parse(raw: String?): ScannedCode? = InviteLink.parse(raw)?.let(::Invite) ?: LinkCode.parse(raw)?.let(::Link)
    }
}
