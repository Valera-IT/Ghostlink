package io.github.valerait.ghostlink.core.platform

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class InMemorySecureStoreTest {
    @Test
    fun `stored value is a copy`() {
        val store = InMemorySecureStore()
        val value = byteArrayOf(1, 2, 3)
        store.write("k", value)
        value[0] = 9
        store.read("k")!![1] = 9
        assertContentEquals(byteArrayOf(1, 2, 3), store.read("k"))
    }

    @Test
    fun `deleted value is gone`() {
        val store = InMemorySecureStore()
        store.write("k", byteArrayOf(1))
        store.delete("k")
        assertNull(store.read("k"))
    }
}
