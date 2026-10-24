package io.github.valerait.ghostlink.di

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.github.valerait.ghostlink.BuildConfig

data class AppConfig(
    /** Нет google-services.json — работаем на DemoBackend. */
    val demoMode: Boolean,
) {
    companion object {
        /** Для эмулятора заодно инициализирует FirebaseApp: Koin создаёт бэкенд уже после этого. */
        fun detect(context: Context): AppConfig {
            if (BuildConfig.FIREBASE_EMULATOR_HOST != null) {
                initEmulatorApp(context)
                return AppConfig(demoMode = false)
            }
            return AppConfig(demoMode = FirebaseApp.getApps(context).isEmpty())
        }

        /** Локальный Firebase Emulator: проект demo-* не требует ни google-services.json, ни настоящего проекта. */
        private fun initEmulatorApp(context: Context) {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(
                    context,
                    FirebaseOptions
                        .Builder()
                        .setProjectId("demo-ghostlink")
                        .setApplicationId("1:000000000000:android:0000000000000000")
                        .setApiKey("emulator-api-key")
                        .build(),
                )
            }
        }
    }
}
