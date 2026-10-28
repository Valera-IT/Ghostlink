package io.github.valerait.ghostlink.push

import com.google.firebase.messaging.FirebaseMessaging
import io.github.valerait.ghostlink.core.platform.PushTokens
import io.github.valerait.ghostlink.core.platform.logWarning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Токен FCM: первый берём у FirebaseMessaging при старте, новые приходят в [MessagingService.onNewToken]. */
class FcmPushTokens : PushTokens {
    private val _token = MutableStateFlow<String?>(null)
    override val token: StateFlow<String?> = _token.asStateFlow()

    fun start() {
        FirebaseMessaging
            .getInstance()
            .token
            .addOnSuccessListener { _token.value = it }
            .addOnFailureListener { logWarning(TAG, "No FCM token", it) }
    }

    fun update(token: String) {
        _token.value = token
    }

    private companion object {
        const val TAG = "Push"
    }
}
