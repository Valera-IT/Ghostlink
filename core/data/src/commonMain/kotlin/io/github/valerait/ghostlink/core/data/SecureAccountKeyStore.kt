package io.github.valerait.ghostlink.core.data

import io.github.valerait.ghostlink.core.model.AccountKey
import io.github.valerait.ghostlink.core.platform.SecureStore

/** Ключ аккаунта в защищённом хранилище платформы вместе с id пользователя, которому он принадлежит. */
internal class SecureAccountKeyStore(
    private val store: SecureStore,
) : AccountKeyStore {
    override fun get(userId: String): AccountKey? {
        if (store.read(USER)?.decodeToString() != userId) return null
        return store.read(VALUE)?.let(::AccountKey)
    }

    override fun put(
        userId: String,
        key: AccountKey,
    ) {
        store.write(USER, userId.encodeToByteArray())
        store.write(VALUE, key.bytes)
    }

    override fun clear() {
        store.delete(USER)
        store.delete(VALUE)
    }

    private companion object {
        // Эти же имена знает перенос ключа из старых хранилищ: в AndroidSecureStore и KeychainSecureStore.
        const val USER = "account_key.user"
        const val VALUE = "account_key.value"
    }
}
