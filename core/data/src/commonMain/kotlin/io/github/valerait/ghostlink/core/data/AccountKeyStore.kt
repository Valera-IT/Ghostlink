package io.github.valerait.ghostlink.core.data

import io.github.valerait.ghostlink.core.model.AccountKey

/** Где устройство хранит ключ своего аккаунта. */
interface AccountKeyStore {
    fun get(userId: String): AccountKey?

    fun put(
        userId: String,
        key: AccountKey,
    )

    fun clear()
}
