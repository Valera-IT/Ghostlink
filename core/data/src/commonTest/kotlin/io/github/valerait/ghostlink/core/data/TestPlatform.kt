package io.github.valerait.ghostlink.core.data

import io.github.valerait.ghostlink.core.platform.AppLifecycle
import io.github.valerait.ghostlink.core.platform.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow

class TestAppLifecycle(
    foreground: Boolean = true,
) : AppLifecycle {
    override val isForeground = MutableStateFlow(foreground)
}

class TestSecureStore : SecureStore {
    val values = mutableMapOf<String, ByteArray>()

    override fun read(key: String): ByteArray? = values[key]

    override fun write(
        key: String,
        value: ByteArray,
    ) {
        values[key] = value
    }

    override fun delete(key: String) {
        values.remove(key)
    }
}
