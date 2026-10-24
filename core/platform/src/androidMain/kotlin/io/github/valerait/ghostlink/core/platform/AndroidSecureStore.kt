package io.github.valerait.ghostlink.core.platform

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Значения шифруются ключом AES из Android Keystore: сам AES-ключ не покидает
 * защищённое хранилище, а в SharedPreferences лежит только шифротекст.
 * Резервное копирование отключено в манифесте, так что секреты не уедут в облако.
 */
internal class AndroidSecureStore(
    private val context: Context,
) : SecureStore {
    private val prefs = context.getSharedPreferences("secure_store", Context.MODE_PRIVATE)

    init {
        migrateLegacyAccountKey()
    }

    override fun read(key: String): ByteArray? {
        val sealed = prefs.getString(key, null) ?: return null
        return try {
            val raw = Base64.decode(sealed, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, raw, 0, IV_SIZE))
            cipher.doFinal(raw, IV_SIZE, raw.size - IV_SIZE)
        } catch (e: GeneralSecurityException) {
            // Например, Keystore сбросили вместе с блокировкой экрана — значение не восстановить.
            Log.w(TAG, "Failed to read $key", e)
            null
        } catch (e: IllegalArgumentException) {
            // Испорченная строка Base64 или слишком короткий шифротекст.
            Log.w(TAG, "Failed to read $key", e)
            null
        }
    }

    override fun write(
        key: String,
        value: ByteArray,
    ) {
        prefs.edit().putString(key, seal(value)).apply()
    }

    override fun delete(key: String) {
        prefs.edit().remove(key).apply()
    }

    private fun seal(value: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value), Base64.NO_WRAP)
    }

    /**
     * До 0.5 ключ аккаунта лежал в своём файле: id пользователя открытым текстом, ключ — в том же
     * формате, что здесь. Переносим под имена, которые читает SecureAccountKeyStore из core:data.
     */
    private fun migrateLegacyAccountKey() {
        val legacy = context.getSharedPreferences("account_key", Context.MODE_PRIVATE)
        val user = legacy.getString("user", null) ?: return
        val editor = prefs.edit().putString("account_key.user", seal(user.encodeToByteArray()))
        legacy.getString("key", null)?.let { editor.putString("account_key.value", it) }
        editor.apply()
        legacy.edit().clear().apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply {
                init(
                    KeyGenParameterSpec
                        .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(KEY_BITS)
                        .build(),
                )
            }.generateKey()
    }

    private companion object {
        const val TAG = "SecureStore"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"

        // Алиас прежний: ключи, сохранённые до 0.5, расшифровываются тем же AES-ключом.
        const val ALIAS = "ghostlink_account_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
        const val KEY_BITS = 256
    }
}
