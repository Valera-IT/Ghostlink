package io.github.valerait.ghostlink.di

import io.github.valerait.ghostlink.core.crypto.cryptoModule
import io.github.valerait.ghostlink.core.data.DeviceRegistry
import io.github.valerait.ghostlink.core.data.PresenceTracker
import io.github.valerait.ghostlink.core.data.dataModule
import io.github.valerait.ghostlink.core.demo.demoModule
import io.github.valerait.ghostlink.core.domain.domainModule
import io.github.valerait.ghostlink.core.domain.repository.UserRepository
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.core.platform.platformModule
import io.github.valerait.ghostlink.core.ui.AppConfig
import io.github.valerait.ghostlink.core.ui.AvatarPhotos
import io.github.valerait.ghostlink.core.ui.LanguageSettings
import io.github.valerait.ghostlink.core.ui.ThemeSettings
import io.github.valerait.ghostlink.data.settings.LanguagePreferences
import io.github.valerait.ghostlink.data.settings.ThemePreferences
import io.github.valerait.ghostlink.feature.chat.chatModule
import io.github.valerait.ghostlink.feature.chats.chatsModule
import io.github.valerait.ghostlink.feature.devices.devicesModule
import io.github.valerait.ghostlink.feature.friends.friendsModule
import io.github.valerait.ghostlink.feature.link.linkModule
import io.github.valerait.ghostlink.feature.onboarding.onboardingModule
import io.github.valerait.ghostlink.feature.recovery.recoveryModule
import io.github.valerait.ghostlink.feature.settings.settingsModule
import io.github.valerait.ghostlink.ui.navigation.RootViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Весь граф приложения. Бэкенд выбирается по [config]: демо или Firebase. */
fun appModules(config: AppConfig): List<Module> =
    listOf(
        appModule(config),
        platformModule,
        domainModule,
        cryptoModule,
        if (config.demoMode) demoModule else dataModule(),
    ) + featureModules

private val featureModules =
    listOf(onboardingModule, chatsModule, chatModule, friendsModule, devicesModule, linkModule, recoveryModule, settingsModule)

private fun appModule(config: AppConfig) =
    module {
        single { config }
        // Записи без ожидания (отправка, «прочитано», присутствие, ответы бота) живут дольше экрана — до конца процесса.
        single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        singleOf(::ThemePreferences) { bind<ThemeSettings>() }
        singleOf(::LanguagePreferences) { bind<LanguageSettings>() }
        singleOf(::PresenceTracker)
        singleOf(::DeviceRegistry)
        single {
            val users = get<UserRepository>()
            AvatarPhotos { id, version -> users.getAvatar(UserId(id), version) }
        }

        viewModelOf(::RootViewModel)
    }
