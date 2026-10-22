package io.github.valerait.ghostlink.core.model

import kotlin.jvm.JvmInline

// Идентификаторы — разные типы, чтобы UserId нельзя было передать вместо ChatId.

@JvmInline
value class UserId(
    val value: String,
) {
    override fun toString(): String = value
}

@JvmInline
value class ChatId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        /** У пары пользователей ровно один чат: id не зависит от того, кто кого добавил. */
        fun of(
            a: UserId,
            b: UserId,
        ): ChatId = if (a.value < b.value) ChatId("${a}_$b") else ChatId("${b}_$a")
    }
}

@JvmInline
value class MessageId(
    val value: String,
) {
    override fun toString(): String = value
}

@JvmInline
value class DeviceId(
    val value: String,
) {
    override fun toString(): String = value
}

@JvmInline
value class LinkRequestId(
    val value: String,
) {
    override fun toString(): String = value
}
