package io.github.valerait.ghostlink.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import io.github.valerait.ghostlink.MainActivity
import io.github.valerait.ghostlink.R
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.platform.DeepLinks

/**
 * Уведомление о новом сообщении из data-сообщения Worker (worker/src/push.js): одно на чат, новое сообщение
 * заменяет предыдущее, нажатие открывает чат через ghostlink://chat/<id>.
 */
class MessageNotifications(
    private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun show(data: Map<String, String>) {
        val chatId = data["chatId"]?.let(::ChatId) ?: return
        if (!canNotify()) return
        ensureChannel()
        val sender = data["senderName"].orEmpty()
        val chatName = data["chatName"].orEmpty()
        val text = preview(data["text"].orEmpty(), data["photo"] == "true")
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.ghost_icon_foreground))
                .setContentTitle(chatName.ifEmpty { sender })
                .setContentText(if (chatName.isEmpty()) text else context.getString(R.string.notification_group_line, sender, text))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setGroup(GROUP)
                .setAutoCancel(true)
                .setContentIntent(openChat(chatId))
                .build()
        @Suppress("MissingPermission") // проверено в canNotify(): areNotificationsEnabled учитывает и разрешение
        manager.notify(chatId.value, NOTIFICATION_ID, notification)
    }

    /** Чат открыт: его уведомление уже не нужно. */
    fun cancel(chatId: ChatId) = manager.cancel(chatId.value, NOTIFICATION_ID)

    private fun preview(
        text: String,
        photo: Boolean,
    ): String =
        when {
            !photo -> text
            text.isEmpty() -> context.getString(R.string.notification_photo)
            else -> context.getString(R.string.notification_photo_caption, text)
        }

    // До Android 13 разрешения нет, но пользователь мог выключить уведомления в настройках.
    private fun canNotify() = manager.areNotificationsEnabled()

    private fun ensureChannel() {
        val channel =
            NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_messages), NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun openChat(chatId: ChatId): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(DeepLinks.chatLink(chatId).toUri())
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            chatId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val CHANNEL_ID = "messages"
        const val GROUP = "messages"
        const val NOTIFICATION_ID = 1
    }
}
