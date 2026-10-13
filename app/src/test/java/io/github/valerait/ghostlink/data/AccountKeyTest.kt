package io.github.valerait.ghostlink.data

import io.github.valerait.ghostlink.data.model.AccountKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountKeyTest {

    @Test
    fun `recovery code round-trips`() {
        val key = AccountKey.generate()
        val code = key.toRecoveryCode()

        assertTrue(code, Regex("([0-9A-Z]{5}-){7}[0-9A-Z]{5}").matches(code))
        assertEquals(key, AccountKey.parseRecoveryCode(code))
    }

    @Test
    fun `recovery code tolerates case, spaces and look-alike letters`() {
        val key = AccountKey(ByteArray(AccountKey.SIZE) { it.toByte() })
        val messy = key.toRecoveryCode().lowercase().replace("-", " ").replace('0', 'o').replace('1', 'l')

        assertEquals(key, AccountKey.parseRecoveryCode("  $messy\n"))
    }

    @Test
    fun `garbage is not a recovery code`() {
        assertNull(AccountKey.parseRecoveryCode(""))
        assertNull(AccountKey.parseRecoveryCode("hello world"))
        assertNull(AccountKey.parseRecoveryCode(AccountKey.generate().toRecoveryCode().dropLast(5)))
        assertNull(AccountKey.parseRecoveryCode("UUUUU-UUUUU-UUUUU-UUUUU-UUUUU-UUUUU-UUUUU-UUUUU"))
    }

    @Test
    fun `email is a service address that never leaks the secret`() {
        val key = AccountKey.generate()

        assertTrue(key.email, Regex("[0-9a-f]{16}@ghostlink\\.invalid").matches(key.email))
        assertTrue(AccountKey.isServiceEmail(key.email))
        assertEquals(false, AccountKey.isServiceEmail("someone@gmail.com"))
        assertTrue(key.password.length >= 20)
    }

    @Test
    fun `rotation keeps the login and changes the secret`() {
        val key = AccountKey.generate()
        val rotated = key.rotated()

        assertEquals(key.email, rotated.email)
        assertNotEquals(key.password, rotated.password)
    }

    @Test
    fun `new secret for a known service email keeps the login`() {
        val key = AccountKey.generate()

        assertEquals(key.email, AccountKey.forEmail(key.email)?.email)
        assertNull(AccountKey.forEmail("someone@gmail.com"))
    }
}
