package io.github.valerait.ghostlink.data.model

import java.security.SecureRandom

/**
 * Ключ аккаунта — скрытые логин и пароль Firebase Email/Password.
 * Пользователь их не видит: устройства передают ключ друг другу по QR,
 * а сам пользователь может сохранить его как ключ восстановления.
 *
 * 25 случайных байт: первые 8 — логин (не меняется у аккаунта), остальные 17 — секрет.
 */
class AccountKey(
    bytes: ByteArray,
) {
    init {
        require(bytes.size == SIZE) { "Account key must be $SIZE bytes" }
    }

    private val data = bytes.copyOf()

    val bytes: ByteArray get() = data.copyOf()

    /** Служебная почта: домен .invalid зарезервирован и никуда не доставляет. */
    val email: String get() = loginToEmail(data.copyOfRange(0, LOGIN_SIZE))

    val password: String get() = Base32.encode(data.copyOfRange(LOGIN_SIZE, SIZE))

    /** Тот же логин с новым секретом: так «выход на всех устройствах» меняет только пароль. */
    fun rotated(random: SecureRandom = SecureRandom()): AccountKey =
        AccountKey(data.copyOfRange(0, LOGIN_SIZE) + ByteArray(SIZE - LOGIN_SIZE).also(random::nextBytes))

    /** XXXXX-XXXXX-…: 8 групп по 5 символов base32. */
    fun toRecoveryCode(): String = Base32.encode(data).chunked(GROUP).joinToString("-")

    override fun equals(other: Any?): Boolean = other is AccountKey && other.data.contentEquals(data)

    override fun hashCode(): Int = data.contentHashCode()

    override fun toString(): String = "AccountKey($email)"

    companion object {
        const val SIZE = 25
        private const val LOGIN_SIZE = 8
        private const val GROUP = 5
        private const val DOMAIN = "@ghostlink.invalid"

        fun generate(random: SecureRandom = SecureRandom()): AccountKey = AccountKey(ByteArray(SIZE).also(random::nextBytes))

        /** Новый секрет для аккаунта, у которого уже есть служебная почта (ключ на устройстве потерян). */
        fun forEmail(
            email: String,
            random: SecureRandom = SecureRandom(),
        ): AccountKey? {
            val login = emailToLogin(email) ?: return null
            return AccountKey(login + ByteArray(SIZE - LOGIN_SIZE).also(random::nextBytes))
        }

        fun isServiceEmail(email: String?): Boolean = email != null && emailToLogin(email) != null

        /** Терпим пробелы, дефисы, строчные буквы и похожие на цифры буквы. */
        fun parseRecoveryCode(raw: String): AccountKey? {
            val bytes = Base32.decode(raw.filterNot { it.isWhitespace() || it == '-' }) ?: return null
            return if (bytes.size == SIZE) AccountKey(bytes) else null
        }

        private fun loginToEmail(login: ByteArray): String = login.joinToString("") { "%02x".format(it) } + DOMAIN

        private fun emailToLogin(email: String): ByteArray? {
            if (!email.endsWith(DOMAIN)) return null
            val hex = email.removeSuffix(DOMAIN)
            if (hex.length != LOGIN_SIZE * 2 || !hex.all { it in '0'..'9' || it in 'a'..'f' }) return null
            return ByteArray(LOGIN_SIZE) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }
    }
}

/** Base32 Крокфорда: без I, L, O, U — код удобно читать и переписывать от руки. */
internal object Base32 {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                out.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) out.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return out.toString()
    }

    fun decode(text: String): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in text) {
            val value =
                when (val upper = c.uppercaseChar()) {
                    'O' -> 0
                    'I', 'L' -> 1
                    else -> ALPHABET.indexOf(upper).takeIf { it >= 0 } ?: return null
                }
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xFF)
                bits -= 8
            }
        }
        return out.toByteArray()
    }
}
