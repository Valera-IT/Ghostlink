package io.github.valerait.ghostlink.core.data

import io.github.valerait.ghostlink.core.model.AccountKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecureAccountKeyStoreTest {
    private val store = TestSecureStore()
    private val keys = SecureAccountKeyStore(store)
    private val key = AccountKey.generate()

    @Test
    fun `key is returned only to its owner`() {
        keys.put("anya", key)
        assertContentEquals(key.bytes, keys.get("anya")?.bytes)
        assertNull(keys.get("boris"))
    }

    @Test
    fun `clear removes key and owner`() {
        keys.put("anya", key)
        keys.clear()
        assertNull(keys.get("anya"))
        assertTrue(store.values.isEmpty())
    }
}
