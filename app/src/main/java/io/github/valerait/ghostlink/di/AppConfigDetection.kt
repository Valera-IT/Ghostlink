package io.github.valerait.ghostlink.di

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.github.valerait.ghostlink.BuildConfig
import io.github.valerait.ghostlink.core.ui.AppConfig

/**
 * Нет google-services.json — работаем на DemoBackend.
 * Для эмулятора заодно инициализирует FirebaseApp: Koin создаёт бэкенд уже после этого.
 */
fun detectAppConfig(context: Context): AppConfig {
    if (BuildConfig.FIREBASE_EMULATOR_HOST != null) {
        initEmulatorApp(context)
        return AppConfig(demoMode = false, versionName = BuildConfig.VERSION_NAME)
    }
    return AppConfig(demoMode = FirebaseApp.getApps(context).isEmpty(), versionName = BuildConfig.VERSION_NAME)
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
