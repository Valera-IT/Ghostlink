package io.github.valerait.ghostlink

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valerait.ghostlink.core.data.DeviceRegistry
import io.github.valerait.ghostlink.core.data.PresenceTracker
import io.github.valerait.ghostlink.core.designsystem.GhostlinkTheme
import io.github.valerait.ghostlink.core.platform.DeepLinks
import io.github.valerait.ghostlink.core.ui.AppConfig
import io.github.valerait.ghostlink.core.ui.AvatarPhotos
import io.github.valerait.ghostlink.core.ui.LocalAvatarPhotos
import io.github.valerait.ghostlink.data.settings.LanguagePreferences
import io.github.valerait.ghostlink.data.settings.ThemePreferences
import io.github.valerait.ghostlink.di.appModules
import io.github.valerait.ghostlink.ui.navigation.GhostlinkRoot
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import platform.UIKit.UIViewController

// Точки входа для Swift (iosApp/iosApp/GhostlinkApp.swift): GhostlinkKt.start(…), GhostlinkKt.MainViewController(), …

/**
 * Поднимает граф приложения. Вызывать один раз, на главном потоке и после FirebaseApp.configure():
 * [demoMode] — в бандле нет GoogleService-Info.plist, тогда работаем на DemoBackend.
 */
fun start(
    demoMode: Boolean,
    versionName: String,
) {
    val koin = startKoin { modules(appModules(AppConfig(demoMode = demoMode, versionName = versionName))) }.koin
    koin.get<PresenceTracker>().start()
    koin.get<DeviceRegistry>().start()
}

/** Весь UI — Compose Multiplatform. */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController { App() }

/** Ссылка ghostlink://, которой открыли приложение (onOpenURL). */
@Suppress("unused")
fun openUrl(url: String) = KoinPlatform.getKoin().get<DeepLinks>().open(url)

@Composable
private fun App() {
    val themePreferences = koinInject<ThemePreferences>()
    val languagePreferences = koinInject<LanguagePreferences>()
    val theme by themePreferences.theme.collectAsStateWithLifecycle()
    val language by languagePreferences.language.collectAsStateWithLifecycle()
    // Строки Compose Resources читают язык (AppleLanguages) только при композиции, а окружение ресурсов
    // снаружи не подменить: после смены языка пересобираем UI целиком, и он открывается со списка чатов.
    key(language) {
        GhostlinkTheme(theme = theme) {
            CompositionLocalProvider(LocalAvatarPhotos provides koinInject<AvatarPhotos>()) {
                GhostlinkRoot(koinViewModel())
            }
        }
    }
}
