package io.github.valerait.ghostlink.model

/** Ссылка-приглашение, которая зашита в QR-код: ghostlink://add/<userId>. */
object InviteLink {
    private const val PREFIX = "ghostlink://add/"
    private val USER_ID = Regex("[A-Za-z0-9_-]{1,128}")

    fun create(userId: String): String = PREFIX + userId

    /** Возвращает userId или null, если это не приглашение Ghostlink. */
    fun parse(raw: String?): String? {
        val value = raw?.trim() ?: return null
        if (!value.startsWith(PREFIX)) return null
        return value.removePrefix(PREFIX).trimEnd('/').takeIf { USER_ID.matches(it) }
    }
}
