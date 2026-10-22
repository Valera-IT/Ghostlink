package io.github.valerait.ghostlink.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinkCodeTest {
    private val publicKey = ByteArray(65) { if (it == 0) 0x04 else (it * 7).toByte() }
    private val code = LinkCode(LinkRequestId("abcdefghijklmnop1234"), publicKey)

    @Test
    fun uriParsesBackToTheSameCode() {
        assertEquals(code, LinkCode.parse(code.toUri()))
    }

    @Test
    fun keyIsUrlSafeBase64WithoutPadding() {
        // Ровно так кодировал java.util.Base64.getUrlEncoder().withoutPadding(): старые QR должны читаться.
        val uri = code.toUri()
        val key = uri.substringAfter('.')
        assertEquals(87, key.length)
        assertEquals(false, key.any { it == '=' || it == '+' || it == '/' })
        assertEquals(code, LinkCode.parse("$uri="))
    }

    @Test
    fun rejectsWrongKeyOrId() {
        val key = code.toUri().substringAfter('.')
        assertNull(LinkCode.parse("ghostlink://link/short.$key"))
        assertNull(LinkCode.parse("ghostlink://link/abcdefghijklmnop1234.AAAA"))
        assertNull(LinkCode.parse("ghostlink://link/abcdefghijklmnop1234"))
        assertNull(LinkCode.parse("ghostlink://add/abc"))
    }

    @Test
    fun scannedCodeTellsInviteFromLink() {
        assertEquals(ScannedCode.Invite(UserId("abc")), ScannedCode.parse("ghostlink://add/abc"))
        assertEquals(ScannedCode.Link(code), ScannedCode.parse(code.toUri()))
        assertNull(ScannedCode.parse("hello"))
    }
}
