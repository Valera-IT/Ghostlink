@file:OptIn(ExperimentalForeignApi::class)

package io.github.valerait.ghostlink.core.platform

import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnAttributes
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Секреты в Keychain: по записи generic password на ключ. Доступны после первой разблокировки
 * и только на этом устройстве — не уезжают ни в iCloud Keychain, ни в резервную копию на новый телефон.
 */
internal class KeychainSecureStore : SecureStore {
    init {
        migrateSpikeAccountKey()
    }

    override fun read(key: String): ByteArray? =
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status =
                withQuery(
                    kSecAttrService to SERVICE,
                    kSecAttrAccount to key,
                    kSecReturnData to kCFBooleanTrue,
                    kSecMatchLimit to kSecMatchLimitOne,
                ) { SecItemCopyMatching(it, result.ptr) }
            if (status != errSecSuccess) {
                if (status != errSecItemNotFound) logWarning(TAG, "Can't read $key: OSStatus $status")
                return null
            }
            (CFBridgingRelease(result.value) as? NSData)?.toByteArray()
        }

    override fun write(
        key: String,
        value: ByteArray,
    ) {
        delete(key)
        val status =
            withQuery(
                kSecAttrService to SERVICE,
                kSecAttrAccount to key,
                kSecValueData to value.toNSData(),
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            ) { SecItemAdd(it, null) }
        if (status != errSecSuccess) logWarning(TAG, "Can't write $key: OSStatus $status")
    }

    override fun delete(key: String) {
        withQuery(kSecAttrService to SERVICE, kSecAttrAccount to key) { SecItemDelete(it) }
    }

    /**
     * Прототип из spike/ios с тем же bundle id хранил ключ аккаунта под своим сервисом: id пользователя —
     * в имени записи, сам ключ — в данных. Переносим под имена, которые читает SecureAccountKeyStore из core:data.
     */
    private fun migrateSpikeAccountKey() =
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status =
                withQuery(
                    kSecAttrService to SPIKE_SERVICE,
                    kSecReturnAttributes to kCFBooleanTrue,
                    kSecReturnData to kCFBooleanTrue,
                    kSecMatchLimit to kSecMatchLimitOne,
                ) { SecItemCopyMatching(it, result.ptr) }
            if (status != errSecSuccess) return@memScoped
            val item = CFBridgingRelease(result.value) as? Map<*, *> ?: return@memScoped
            val user = item[ATTR_ACCOUNT] as? String
            val key = (item[VALUE_DATA] as? NSData)?.toByteArray()
            if (user != null && key != null) {
                write(ACCOUNT_KEY_USER, user.encodeToByteArray())
                write(ACCOUNT_KEY_VALUE, key)
                // Не удалось записать — старую запись не трогаем: перенос повторится при следующем запуске.
                if (!key.contentEquals(read(ACCOUNT_KEY_VALUE))) return@memScoped
            }
            withQuery(kSecAttrService to SPIKE_SERVICE) { SecItemDelete(it) }
        }

    private companion object {
        const val TAG = "SecureStore"
        const val SERVICE = "io.github.valerait.ghostlink.secure_store"
        const val SPIKE_SERVICE = "io.github.valerait.ghostlink.accountKey"
        const val ACCOUNT_KEY_USER = "account_key.user"
        const val ACCOUNT_KEY_VALUE = "account_key.value"

        // Строковые значения kSecAttrAccount и kSecValueData: ими подписаны поля в словаре атрибутов записи.
        const val ATTR_ACCOUNT = "acct"
        const val VALUE_DATA = "v_Data"
    }
}

/**
 * Словарь запроса к Keychain с классом generic password. Значения — CF-ссылки или объекты Kotlin/Foundation
 * (строки, NSData), их мост в CF делает CFBridgingRetain. Словарь удерживает значения сам, поэтому наши
 * retain отпускаются сразу, а сам словарь — после [block].
 */
private fun <T> withQuery(
    vararg pairs: Pair<CFStringRef?, Any?>,
    block: (CFDictionaryRef?) -> T,
): T {
    val dict =
        CFDictionaryCreateMutable(null, (pairs.size + 1).convert(), kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
    CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
    pairs.forEach { (key, value) ->
        if (value is CPointer<*>) {
            CFDictionaryAddValue(dict, key, value.reinterpret<CPointed>())
        } else {
            val bridged = CFBridgingRetain(value)
            CFDictionaryAddValue(dict, key, bridged)
            CFRelease(bridged)
        }
    }
    return try {
        block(dict)
    } finally {
        CFRelease(dict)
    }
}
