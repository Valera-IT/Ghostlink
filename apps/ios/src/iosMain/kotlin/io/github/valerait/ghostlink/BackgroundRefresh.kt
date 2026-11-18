package io.github.valerait.ghostlink

import io.github.valerait.ghostlink.core.domain.usecase.CheckNewMessagesUseCase
import io.github.valerait.ghostlink.core.domain.usecase.NewMessageNotice
import io.github.valerait.ghostlink.core.platform.DeepLinks
import io.github.valerait.ghostlink.core.platform.logWarning
import io.github.valerait.ghostlink.core.ui.AppConfig
import io.github.valerait.ghostlink.core.ui.resources.Res
import io.github.valerait.ghostlink.core.ui.resources.chats_photo
import io.github.valerait.ghostlink.core.ui.resources.chats_photo_caption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.resources.getString
import org.koin.mp.KoinPlatform
import platform.Foundation.NSUserDefaults
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// Фоновое обновление iOS (этап 6.12): без Apple Developer нет APNs, и push до iPhone не доходит. Вместо него
// iOS иногда будит приложение (BGAppRefreshTask в GhostlinkApp.swift), и оно само смотрит, нет ли новых сообщений.

private const val KEY_NOTIFIED_UNTIL = "notifiedUntil"

/** На всё про всё iOS даёт около 30 секунд; потом задачу убивают. */
private val CHECK_TIMEOUT = 25.seconds

/** Приложение ушло в фон: всё, что пришло раньше, пользователь уже видел. */
@Suppress("unused")
fun appDidEnterBackground() = saveNotifiedUntil(Clock.System.now())

/**
 * iOS разбудила приложение: локальное уведомление о каждом чате с новым непрочитанным сообщением.
 * Ничего не бросает: Swift ждёт только окончания, а ошибка (нет сети) значит «в следующий раз».
 */
@Suppress("unused", "TooGenericExceptionCaught")
suspend fun checkNewMessages() {
    val koin = KoinPlatform.getKoin()
    if (koin.get<AppConfig>().demoMode) return
    try {
        withTimeout(CHECK_TIMEOUT) {
            val notices = koin.get<CheckNewMessagesUseCase>()(notifiedUntil())
            notices.forEach { post(it) }
            notices.maxOfOrNull { it.sentAt }?.let(::saveNotifiedUntil)
        }
    } catch (e: TimeoutCancellationException) {
        logWarning(TAG, "Background check timed out", e)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logWarning(TAG, "Background check failed", e)
    }
}

private suspend fun post(notice: NewMessageNotice) {
    val text =
        when {
            !notice.photo -> notice.text
            notice.text.isEmpty() -> getString(Res.string.chats_photo)
            else -> getString(Res.string.chats_photo_caption, notice.text)
        }
    val content =
        UNMutableNotificationContent().apply {
            setTitle(notice.title ?: "Ghostlink")
            setBody(notice.senderName?.let { "$it: $text" } ?: text)
            setSound(UNNotificationSound.defaultSound)
            setThreadIdentifier(notice.chatId.value)
            setUserInfo(mapOf(LINK_KEY to DeepLinks.chatLink(notice.chatId)))
        }
    // Идентификатор — чат: новое уведомление того же чата заменяет прежнее.
    val request = UNNotificationRequest.requestWithIdentifier(notice.chatId.value, content, trigger = null)
    UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request) { error ->
        if (error != null) logWarning(TAG, "Notification not posted: ${error.localizedDescription}")
    }
}

/** Ключ ссылки ghostlink://chat/… в userInfo уведомления: её открывает GhostlinkApp.swift по нажатию. */
const val LINK_KEY = "link"

private fun notifiedUntil(): Instant {
    val seconds = NSUserDefaults.standardUserDefaults.doubleForKey(KEY_NOTIFIED_UNTIL)
    return if (seconds > 0) Instant.fromEpochMilliseconds((seconds * MILLIS).toLong()) else Clock.System.now()
}

private fun saveNotifiedUntil(at: Instant) {
    NSUserDefaults.standardUserDefaults.setDouble(at.toEpochMilliseconds() / MILLIS, KEY_NOTIFIED_UNTIL)
}

private const val MILLIS = 1000.0
private const val TAG = "BackgroundRefresh"
