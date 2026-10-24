package io.github.valerait.ghostlink.platform

import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.ios
import io.github.valerait.ghostlink.app.LanguageStore
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSLog
import platform.Foundation.NSTimeZone
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.currentLocale
import platform.Foundation.preferredLanguages
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.UIKit.UIDevice
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual object DeviceInfo {
    actual val id: String by lazy {
        val defaults = NSUserDefaults.standardUserDefaults
        defaults.stringForKey("deviceId") ?: NSUUID().UUIDString.replace("-", "").lowercase().also {
            defaults.setObject(it, "deviceId")
        }
    }

    // С iOS 16 без особого entitlement name возвращает просто «iPhone» — для списка устройств этого хватает.
    actual val name: String get() = UIDevice.currentDevice.name

    actual val platform: String = "ios"

    actual val appVersion: String by lazy {
        val info = NSBundle.mainBundle.infoDictionary
        val version = info?.get("CFBundleShortVersionString") as? String ?: "?"
        val build = info?.get("CFBundleVersion") as? String
        if (build != null) "$version ($build)" else version
    }
}

actual object SecureStore {
    private const val SERVICE = "io.github.valerait.ghostlink.accountKey"

    actual fun get(account: String): ByteArray? = memScoped {
        val query = cfDictionary(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to cf(SERVICE),
            kSecAttrAccount to cf(account),
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne,
        )
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        if (status != errSecSuccess) return null
        (CFBridgingRelease(result.value) as? NSData)?.toByteArray()
    }

    actual fun put(account: String, value: ByteArray) {
        clear()
        val query = cfDictionary(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to cf(SERVICE),
            kSecAttrAccount to cf(account),
            kSecValueData to CFBridgingRetain(value.toNSData()),
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        )
        val status = SecItemAdd(query, null)
        CFRelease(query)
        if (status != errSecSuccess) logWarning("SecureStore", "SecItemAdd failed: $status")
    }

    /** На устройстве живёт ключ только одного аккаунта. */
    actual fun clear() {
        val query = cfDictionary(kSecClass to kSecClassGenericPassword, kSecAttrService to cf(SERVICE))
        SecItemDelete(query)
        CFRelease(query)
    }

    private fun cf(value: String): CFTypeRef? = CFBridgingRetain(value)

    // Словарь сам удерживает значения (kCFTypeDictionaryValueCallBacks), поэтому наши retain отпускаем.
    private fun cfDictionary(vararg pairs: Pair<CFStringRef?, CFTypeRef?>): CFMutableDictionaryRef? {
        val dict = CFDictionaryCreateMutable(null, pairs.size.convert(), kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        pairs.forEach { (key, value) -> CFDictionaryAddValue(dict, key, value) }
        return dict
    }
}

actual object LocalSettings {
    actual fun getString(key: String): String? = NSUserDefaults.standardUserDefaults.stringForKey(key)
    actual fun putString(key: String, value: String) = NSUserDefaults.standardUserDefaults.setObject(value, key)
}

// NSLocale.currentLocale подстроен под локализации бандла, а preferredLanguages — честный список из настроек iOS.
actual val systemLanguageCode: String
    get() = (NSLocale.preferredLanguages.firstOrNull() as? String)?.substringBefore('-') ?: "en"

actual fun logWarning(tag: String, message: String, error: Throwable?) {
    NSLog("[%@] %@ %@", tag, message, error?.toString() ?: "")
}

private fun date(millis: Long) = NSDate.dateWithTimeIntervalSince1970(millis / 1000.0)

// Форматтеры дорогие — кэшируем на каждый язык. Если язык приложения совпал с системным, берём
// системную локаль целиком: так сохраняется регион (например, en_GB).
private val formatters = mutableMapOf<String, NSDateFormatter>()

private fun formatter(template: String): NSDateFormatter {
    val language = LanguageStore.code
    return formatters.getOrPut("$language/$template") {
        NSDateFormatter().apply {
            locale = if (language == systemLanguageCode) NSLocale.currentLocale else NSLocale(localeIdentifier = language)
            setLocalizedDateFormatFromTemplate(template)
        }
    }
}

private val timeFormatter by lazy { NSDateFormatter().apply { dateFormat = "HH:mm" } }
private val shortDateFormatter by lazy { NSDateFormatter().apply { dateFormat = "dd.MM.yy" } }

actual fun formatTime(millis: Long): String = timeFormatter.stringFromDate(date(millis))
actual fun formatWeekday(millis: Long): String = formatter("EEE").stringFromDate(date(millis))
actual fun formatDate(millis: Long, withYear: Boolean): String =
    formatter(if (withYear) "dMMMMyyyy" else "dMMMM").stringFromDate(date(millis))
actual fun formatShortDate(millis: Long): String = shortDateFormatter.stringFromDate(date(millis))

actual fun localEpochDay(millis: Long): Long {
    val offset = NSTimeZone.localTimeZone.secondsFromGMTForDate(date(millis)) * 1000L
    return (millis + offset).floorDiv(86_400_000L)
}

actual suspend fun DocumentReference.approveWithPayload(payload: ByteArray, approvedBy: String) =
    suspendCancellableCoroutine { cont ->
        ios.updateData(mapOf<Any?, Any?>("payload" to payload.toNSData(), "approvedBy" to approvedBy)) { error ->
            if (error == null) cont.resume(Unit) else cont.resumeWithException(Exception(error.localizedDescription))
        }
    }

actual fun DocumentSnapshot.payloadBytes(): ByteArray? = (ios.valueForField("payload") as? NSData)?.toByteArray()

internal fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.convert())
}

internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply { usePinned { memcpy(it.addressOf(0), bytes, length) } }
}
