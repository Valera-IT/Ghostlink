package io.github.valerait.ghostlink.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountKeyTest {
    @Test
    fun recoveryCodeRoundTrips() {
        val key = AccountKey.generate()
        val code = key.toRecoveryCode()

        assertTrue(Regex("([0-9A-Z]{5}-){7}[0-9A-Z]{5}").matches(code), code)
        assertEquals(key, AccountKey.parseRecoveryCode(code))
    }

    @Test
    fun recoveryCodeToleratesCaseSpacesAndLookAlikeLetters() {
        val key = AccountKey(ByteArray(AccountKey.SIZE) { it.toByte() })
        val messy =
            key
                .toRecoveryCode()
                .lowercase()
                .replace("-", " ")
                .replace('0', 'o')
                .replace('1', 'l')

        assertEquals(key, AccountKey.parseRecoveryCode("  $messy\n"))
    }

    @Test
    fun garbageIsNotARecoveryCode() {
        assertNull(AccountKey.parseRecoveryCode(""))
        assertNull(AccountKey.parseRecoveryCode("hello world"))
        assertNull(AccountKey.parseRecoveryCode(AccountKey.generate().toRecoveryCode().dropLast(5)))
        assertNull(AccountKey.parseRecoveryCode("UUUUU-UUUUU-UUUUU-UUUUU-UUUUU-UUUUU-UUUUU-UUUUU"))
    }

    @Test
    fun emailIsAServiceAddressThatNeverLeaksTheSecret() {
        val key = AccountKey.generate()

        assertTrue(Regex("[0-9a-f]{16}@ghostlink\\.invalid").matches(key.email), key.email)
        assertTrue(AccountKey.isServiceEmail(key.email))
        assertEquals(false, AccountKey.isServiceEmail("someone@gmail.com"))
        assertTrue(key.password.length >= 20)
    }

    @Test
    fun rotationKeepsTheLoginAndChangesTheSecret() {
        val key = AccountKey.generate()
        val rotated = key.rotated()

        assertEquals(key.email, rotated.email)
        assertNotEquals(key.password, rotated.password)
    }

    @Test
    fun newSecretForAKnownServiceEmailKeepsTheLogin() {
        val key = AccountKey.generate()

        assertEquals(key.email, AccountKey.forEmail(key.email)?.email)
        assertNull(AccountKey.forEmail("someone@gmail.com"))
    }
}
