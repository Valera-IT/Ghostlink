package io.github.valerait.ghostlink.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import kotlin.time.Clock

/** Это устройство: постоянный id и название для списка «Устройства». */
expect object DeviceInfo {
    val id: String
    val name: String
    val platform: String

    /** Версия приложения для подвала настроек, например «0.1.0 (1)». */
    val appVersion: String
}

/** Ключ аккаунта в защищённом хранилище платформы (Keychain на iOS). */
expect object SecureStore {
    fun get(account: String): ByteArray?
    fun put(account: String, value: ByteArray)
    fun clear()
}

/** Небольшие настройки этого устройства (тема). */
expect object LocalSettings {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

/** Первый язык системы — двухбуквенный код, например "ru". */
expect val systemLanguageCode: String

expect fun logWarning(tag: String, message: String, error: Throwable? = null)

// Форматирование дат через системные форматтеры: в commonMain нет java.time.
// Названия дней и месяцев — на языке приложения (LanguageStore.code), а не системы.
expect fun formatTime(millis: Long): String
expect fun formatWeekday(millis: Long): String
expect fun formatDate(millis: Long, withYear: Boolean): String
expect fun formatShortDate(millis: Long): String

/** Номер дня в локальной зоне — чтобы сравнивать «сегодня/вчера». */
expect fun localEpochDay(millis: Long): Long

fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/**
 * Поле Blob в Firestore: GitLive не умеет байтовые поля, а правила требуют `payload is bytes`.
 * Поэтому payload заявки на вход читается и пишется через нативный SDK.
 */
expect suspend fun DocumentReference.approveWithPayload(payload: ByteArray, approvedBy: String)

expect fun DocumentSnapshot.payloadBytes(): ByteArray?

/** Фото профиля avatars/{uid}: { data: bytes, version } — тоже через нативный SDK. */
expect suspend fun DocumentReference.setAvatar(data: ByteArray, version: Long)

expect fun DocumentSnapshot.avatarData(): ByteArray?

/**
 * Версия фото в users/{uid}. Через нативный SDK: GitLive на iOS пишет Long не целым числом,
 * и правило `avatarVersion is int` запись отклоняет.
 */
expect suspend fun DocumentReference.setAvatarVersion(version: Long)

/** JPEG → картинка для Compose; null — не картинка. */
expect fun decodeImage(bytes: ByteArray): ImageBitmap?

/**
 * Системный выбор фото (PHPicker на iOS, диалог файла на десктопе) для аватара: фото обрезается по центру
 * в квадрат [User.AVATAR_SIZE_PX] и сжимается в JPEG не больше [User.AVATAR_MAX_BYTES].
 * Возвращает функцию, открывающую выбор; отмена ничего не вызывает.
 */
@Composable
expect fun rememberAvatarPicker(onPicked: (ByteArray) -> Unit, onFailed: () -> Unit): () -> Unit

/** Камера с распознаванием QR. [onScanned] может прийти несколько раз — дедупликация на вызывающем. */
@Composable
expect fun QrScanner(onScanned: (String) -> Unit, modifier: Modifier = Modifier)
