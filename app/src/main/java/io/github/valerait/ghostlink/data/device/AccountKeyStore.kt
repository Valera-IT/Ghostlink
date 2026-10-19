package io.github.valerait.ghostlink.data.device

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import io.github.valerait.ghostlink.data.model.AccountKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Где устройство хранит ключ своего аккаунта. */
interface AccountKeyStore {
    fun get(userId: String): AccountKey?

    fun put(
        userId: String,
        key: AccountKey,
    )

    fun clear()
}

class InMemoryAccountKeyStore : AccountKeyStore {
    private var stored: Pair<String, AccountKey>? = null

    override fun get(userId: String): AccountKey? = stored?.takeIf { it.first == userId }?.second

    override fun put(
        userId: String,
        key: AccountKey,
    ) {
        stored = userId to key
    }

    override fun clear() {
        stored = null
    }
}

/**
 * Ключ аккаунта шифруется ключом AES из Android Keystore: сам AES-ключ не покидает
 * защищённое хранилище, а в SharedPreferences лежит только шифротекст.
 * Резервное копирование отключено в манифесте, так что ключ не уедет в облако.
 */
class AndroidAccountKeyStore(
    context: Context,
) : AccountKeyStore {
    private val prefs = context.getSharedPreferences("account_key", Context.MODE_PRIVATE)

    override fun get(userId: String): AccountKey? {
        if (prefs.getString(KEY_USER, null) != userId) return null
        val sealed = prefs.getString(KEY_VALUE, null) ?: return null
        return try {
            val raw = Base64.decode(sealed, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, raw, 0, IV_SIZE))
            AccountKey(cipher.doFinal(raw, IV_SIZE, raw.size - IV_SIZE))
        } catch (e: Exception) {
            // Например, Keystore сбросили вместе с блокировкой экрана — ключ не восстановить.
            Log.w(TAG, "Failed to read account key", e)
            null
        }
    }

    override fun put(
        userId: String,
        key: AccountKey,
    ) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val sealed = cipher.iv + cipher.doFinal(key.bytes)
        prefs
            .edit()
            .putString(KEY_USER, userId)
            .putString(KEY_VALUE, Base64.encodeToString(sealed, Base64.NO_WRAP))
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
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
                        .setKeySize(256)
                        .build(),
                )
            }.generateKey()
    }

    private companion object {
        const val TAG = "AccountKeyStore"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "ghostlink_account_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val KEY_USER = "user"
        const val KEY_VALUE = "key"
    }
}
