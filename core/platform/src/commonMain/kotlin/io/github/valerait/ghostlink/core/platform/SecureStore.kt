package io.github.valerait.ghostlink.core.platform

/**
 * Маленькие секреты устройства (ключ аккаунта): Android Keystore, Keychain, хранилище паролей ОС.
 * Значения не уезжают в облачные резервные копии.
 */
interface SecureStore {
    fun read(key: String): ByteArray?

    fun write(
        key: String,
        value: ByteArray,
    )

    fun delete(key: String)
}

/** Пока у платформы нет настоящего хранилища: секреты живут до перезапуска приложения. */
internal class InMemorySecureStore : SecureStore {
    private val values = mutableMapOf<String, ByteArray>()

    override fun read(key: String): ByteArray? = values[key]?.copyOf()

    override fun write(
        key: String,
        value: ByteArray,
    ) {
        values[key] = value.copyOf()
    }

    override fun delete(key: String) {
        values.remove(key)
    }
}
