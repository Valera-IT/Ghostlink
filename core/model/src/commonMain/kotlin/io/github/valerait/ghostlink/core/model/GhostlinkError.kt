package io.github.valerait.ghostlink.core.model

/**
 * Ошибки, которые пользователь может понять или исправить. Репозитории бросают их вместо исключений
 * бэкенда, а экран переводит в текст. Всё, что не удалось классифицировать, — [Unexpected].
 */
sealed class GhostlinkError(
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** Нет сети или сервер недоступен. */
    class Offline(
        cause: Throwable? = null,
    ) : GhostlinkError("Offline", cause)

    /** Ключ не подошёл: аккаунта нет или секрет уже сменили. */
    class InvalidAccountKey(
        cause: Throwable? = null,
    ) : GhostlinkError("Invalid account key", cause)

    /** Заявка на вход истекла или удалена. */
    class LinkExpired : GhostlinkError("Link request expired")

    /** Заявку на вход уже подтвердило другое устройство. */
    class LinkAlreadyApproved : GhostlinkError("Link request already approved")

    /** Правила базы не пустили: например, устройство удалили из аккаунта. */
    class PermissionDenied(
        cause: Throwable? = null,
    ) : GhostlinkError("Permission denied", cause)

    class Unexpected(
        cause: Throwable,
    ) : GhostlinkError(cause.message, cause)
}
