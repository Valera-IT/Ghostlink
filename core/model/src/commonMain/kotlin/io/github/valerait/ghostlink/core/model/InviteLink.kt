package io.github.valerait.ghostlink.core.model

/** Ссылка-приглашение, которая зашита в QR-код: ghostlink://add/<userId>. */
object InviteLink {
    private const val PREFIX = "ghostlink://add/"
    private val USER_ID = Regex("[A-Za-z0-9_-]{1,128}")

    fun create(userId: UserId): String = PREFIX + userId.value

    /** Возвращает userId или null, если это не приглашение Ghostlink. */
    fun parse(raw: String?): UserId? =
        raw
            ?.trim()
            ?.takeIf { it.startsWith(PREFIX) }
            ?.removePrefix(PREFIX)
            ?.trimEnd('/')
            ?.takeIf { USER_ID.matches(it) }
            ?.let(::UserId)
}
