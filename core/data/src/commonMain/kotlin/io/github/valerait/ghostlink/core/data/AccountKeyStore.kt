package io.github.valerait.ghostlink.core.data

import io.github.valerait.ghostlink.core.model.AccountKey

/** Где устройство хранит ключ своего аккаунта. Реализация платформенная: Android Keystore, Keychain. */
interface AccountKeyStore {
    fun get(userId: String): AccountKey?

    fun put(
        userId: String,
        key: AccountKey,
    )

    fun clear()
}
