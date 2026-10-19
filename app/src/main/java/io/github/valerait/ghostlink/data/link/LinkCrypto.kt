package io.github.valerait.ghostlink.data.link

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Шифрование ключа аккаунта при входе на новом устройстве.
 *
 * Новое устройство показывает в QR свой публичный ключ P-256. Устройство с аккаунтом
 * создаёт одноразовую пару ключей, делает ECDH, выводит ключ AES через HKDF-SHA256
 * и шифрует AES-GCM. id заявки входит и в HKDF, и в AAD: шифротекст нельзя
 * переложить в другую заявку.
 *
 * Формат payload: ephemeralPublicKey (65) || iv (12) || ciphertext+tag.
 */
object LinkCrypto {
    private const val CURVE = "secp256r1"
    private const val POINT_SIZE = 65
    private const val COORD_SIZE = 32
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    private val INFO = "ghostlink-link-v1".toByteArray()
    private val TWO = BigInteger.valueOf(2)

    private val params: ECParameterSpec by lazy {
        AlgorithmParameters
            .getInstance("EC")
            .apply { init(ECGenParameterSpec(CURVE)) }
            .getParameterSpec(ECParameterSpec::class.java)
    }

    fun generateKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec(CURVE)) }.generateKeyPair()

    /** Несжатая точка 0x04 || X || Y — переносимый формат для будущих iOS и десктопа. */
    fun encodePublicKey(key: PublicKey): ByteArray {
        val point = (key as ECPublicKey).w
        return byteArrayOf(0x04) + point.affineX.toFixed() + point.affineY.toFixed()
    }

    fun decodePublicKey(raw: ByteArray): PublicKey {
        require(raw.size == POINT_SIZE && raw[0] == 0x04.toByte()) { "Not an uncompressed P-256 point" }
        val x = BigInteger(1, raw.copyOfRange(1, 1 + COORD_SIZE))
        val y = BigInteger(1, raw.copyOfRange(1 + COORD_SIZE, POINT_SIZE))
        // KeyFactory не везде проверяет точку, а чужая точка вне кривой позволяет вытянуть закрытый ключ.
        val curve = params.curve
        val p = (curve.field as ECFieldFp).p
        require(x < p && y < p && y.modPow(TWO, p) == (x.pow(3) + curve.a * x + curve.b).mod(p)) { "Point is not on P-256" }
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), params))
    }

    fun encrypt(
        recipientPublicKey: ByteArray,
        requestId: String,
        plaintext: ByteArray,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        val ephemeral = generateKeyPair()
        val key = deriveKey(ephemeral.private, decodePublicKey(recipientPublicKey), requestId)
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)
        val cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
                updateAAD(requestId.toByteArray())
            }
        return encodePublicKey(ephemeral.public) + iv + cipher.doFinal(plaintext)
    }

    /** Бросает исключение, если payload подделан или зашифрован не для этого ключа. */
    fun decrypt(
        privateKey: PrivateKey,
        requestId: String,
        payload: ByteArray,
    ): ByteArray {
        require(payload.size > POINT_SIZE + IV_SIZE) { "Payload too short" }
        val sender = decodePublicKey(payload.copyOfRange(0, POINT_SIZE))
        val iv = payload.copyOfRange(POINT_SIZE, POINT_SIZE + IV_SIZE)
        val key = deriveKey(privateKey, sender, requestId)
        val cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
                updateAAD(requestId.toByteArray())
            }
        return cipher.doFinal(payload, POINT_SIZE + IV_SIZE, payload.size - POINT_SIZE - IV_SIZE)
    }

    private fun deriveKey(
        privateKey: PrivateKey,
        publicKey: PublicKey,
        requestId: String,
    ): ByteArray {
        val shared =
            KeyAgreement.getInstance("ECDH").run {
                init(privateKey)
                doPhase(publicKey, true)
                generateSecret()
            }
        return hkdfSha256(shared, salt = ByteArray(32), info = INFO + requestId.toByteArray(), length = 32)
    }

    /** RFC 5869; для 32 байт хватает одного блока expand. */
    private fun hkdfSha256(
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        require(length <= 32)
        val prk =
            Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(salt, "HmacSHA256"))
                doFinal(ikm)
            }
        return Mac
            .getInstance("HmacSHA256")
            .run {
                init(SecretKeySpec(prk, "HmacSHA256"))
                update(info)
                update(1.toByte())
                doFinal()
            }.copyOf(length)
    }

    private fun BigInteger.toFixed(): ByteArray {
        val raw = toByteArray()
        return when {
            raw.size == COORD_SIZE -> raw
            raw.size > COORD_SIZE -> raw.copyOfRange(raw.size - COORD_SIZE, raw.size)
            else -> ByteArray(COORD_SIZE - raw.size) + raw
        }
    }
}
