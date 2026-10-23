package io.github.valerait.ghostlink.core.crypto

import io.github.valerait.ghostlink.core.model.LinkRequestId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails

/**
 * Шифротексты, сделанные другими реализациями, расшифровываются общим кодом на каждой платформе.
 * Так телефон со старой версией приложения может подтвердить вход на устройстве с новой, и наоборот:
 * формат на проводе не изменился. Закрытые ключи — PKCS#8 DER, plaintext — байты 0..24.
 */
class LinkCryptoVectorsTest {
    private val requestId = LinkRequestId("AbCdEf0123456789XyZw")
    private val plaintext = ByteArray(25) { it.toByte() }

    @Test
    fun decryptsPayloadMadeByOldAndroidCode() {
        // Старый LinkCrypto на java.security (app/data/link, удалён в 3.5).
        val key =
            LinkCrypto.decodePrivateKey(
                (
                    "3041020100301306072a8648ce3d020106082a8648ce3d0301070427302502010104205c53a94bab835b82" +
                        "6566fc76262499c09ed4c7eb6e543d7eb0457852b59f9a38"
                ).hexToByteArray(),
            )
        val payload =
            (
                "044aac3ebccc51b6fca0dacf7a8171b561331f9f107462f3e6de4621407241acac85246ac4b5a248d5083545c0e2cb39" +
                    "ee5bd7391da02e5beaaa5f019f6730dfde63075bf774475b97f74756bdfa1e10912513207ec8bf36d6132fc6cd4b92" +
                    "81849a8358045210908331d2dae40b8b18c1861a70abe3"
            ).hexToByteArray()

        assertContentEquals(plaintext, LinkCrypto.decrypt(key, requestId, payload))
    }

    @Test
    fun decryptsPayloadMadeOnIos() {
        // cryptography-kotlin с провайдером Apple на iOS-симуляторе (разведка, spike/ios).
        val key =
            LinkCrypto.decodePrivateKey(
                (
                    "308187020100301306072a8648ce3d020106082a8648ce3d030107046d306b020101042078c198b0670b25a1f6" +
                        "8b945b8adc2de3dbf30265e6e2554ccedcf231734c4f39a1440342000427a00aa50616c834074f2f52d28b474a" +
                        "99d27b97e2841708068a08ca5168d45845c978f1638939fee7fb2e4052757fe058eb1a2df3f8bae6c603d1fb56aa" +
                        "f1d2"
                ).hexToByteArray(),
            )
        val payload =
            (
                "04332a9217b77aaeaa80ca49fbf6feee30f3af2b736c3adc5f922190e80f9c669d752a8c2fba1562b6da0c17bf77f7df" +
                    "d43e24baa8367ed0f66ce076329a513dbf3cf1f2fe2554c535806e900a284051849a1e030c5e605c4615d1837f7674" +
                    "b14effa9f04aa9f639d9186755e9b5159a616c8ad321c9"
            ).hexToByteArray()

        assertContentEquals(plaintext, LinkCrypto.decrypt(key, requestId, payload))
        assertFails { LinkCrypto.decrypt(key, LinkRequestId("OtherRequest00000000"), payload) }
    }
}
