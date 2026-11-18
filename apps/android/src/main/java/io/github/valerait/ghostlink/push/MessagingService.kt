package io.github.valerait.ghostlink.push

import android.content.Context
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.github.valerait.ghostlink.core.platform.AppLifecycle
import io.github.valerait.ghostlink.core.platform.PushTokens
import io.github.valerait.ghostlink.data.settings.LanguagePreferences
import org.koin.android.ext.android.get

/** Push от Worker. Пока приложение на экране, уведомлений нет: новое сообщение и так видно в списке и в чате. */
class MessagingService : FirebaseMessagingService() {
    // Уведомление на языке, выбранном в настройках приложения, а не системы.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(get<LanguagePreferences>().wrap(newBase))
    }

    override fun onNewToken(token: String) {
        (get<PushTokens>() as? FcmPushTokens)?.update(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (get<AppLifecycle>().isForeground.value) return
        MessageNotifications(this).show(message.data)
    }
}
