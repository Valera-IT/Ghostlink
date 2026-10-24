package io.github.valerait.ghostlink.di

import io.github.valerait.ghostlink.BuildConfig
import io.github.valerait.ghostlink.DeviceRegistry
import io.github.valerait.ghostlink.PresenceTracker
import io.github.valerait.ghostlink.core.crypto.cryptoModule
import io.github.valerait.ghostlink.core.data.AccountKeyStore
import io.github.valerait.ghostlink.core.data.dataModule
import io.github.valerait.ghostlink.core.demo.demoModule
import io.github.valerait.ghostlink.core.domain.domainModule
import io.github.valerait.ghostlink.data.device.AndroidAccountKeyStore
import io.github.valerait.ghostlink.data.device.ThisDevice
import io.github.valerait.ghostlink.data.settings.LanguagePreferences
import io.github.valerait.ghostlink.data.settings.ThemePreferences
import io.github.valerait.ghostlink.ui.chat.ChatViewModel
import io.github.valerait.ghostlink.ui.chats.ChatListViewModel
import io.github.valerait.ghostlink.ui.connect.ConnectViewModel
import io.github.valerait.ghostlink.ui.devices.DevicesViewModel
import io.github.valerait.ghostlink.ui.group.GroupInfoViewModel
import io.github.valerait.ghostlink.ui.group.PickMembersViewModel
import io.github.valerait.ghostlink.ui.link.LinkApproveViewModel
import io.github.valerait.ghostlink.ui.link.LinkReceiveViewModel
import io.github.valerait.ghostlink.ui.mycode.MyCodeViewModel
import io.github.valerait.ghostlink.ui.navigation.RootViewModel
import io.github.valerait.ghostlink.ui.onboarding.OnboardingViewModel
import io.github.valerait.ghostlink.ui.recovery.RecoveryViewModel
import io.github.valerait.ghostlink.ui.settings.SettingsViewModel
import io.github.valerait.ghostlink.ui.settings.ThemeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Весь граф приложения. Бэкенд выбирается по [config]: демо или Firebase (настоящий проект либо эмулятор). */
fun appModules(config: AppConfig): List<Module> =
    listOf(
        appModule(config),
        domainModule,
        cryptoModule,
        if (config.demoMode) demoModule else dataModule(BuildConfig.FIREBASE_EMULATOR_HOST),
    )

private fun appModule(config: AppConfig) =
    module {
        single { config }
        // Записи без ожидания (отправка, «прочитано», присутствие, ответы бота) живут дольше экрана — до конца процесса.
        single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        single<AccountKeyStore> { AndroidAccountKeyStore(androidContext()) }
        single { ThisDevice.from(androidContext()) }
        singleOf(::ThemePreferences)
        singleOf(::LanguagePreferences)
        singleOf(::PresenceTracker)
        singleOf(::DeviceRegistry)

        viewModelOf(::RootViewModel)
        viewModelOf(::OnboardingViewModel)
        viewModelOf(::RecoveryViewModel)
        viewModelOf(::ChatListViewModel)
        viewModelOf(::ChatViewModel)
        viewModelOf(::ConnectViewModel)
        viewModelOf(::MyCodeViewModel)
        viewModelOf(::PickMembersViewModel)
        viewModelOf(::GroupInfoViewModel)
        viewModelOf(::DevicesViewModel)
        viewModelOf(::LinkReceiveViewModel)
        viewModelOf(::LinkApproveViewModel)
        viewModelOf(::SettingsViewModel)
        viewModelOf(::ThemeViewModel)
    }
