package io.github.valerait.ghostlink.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import io.github.valerait.ghostlink.core.crypto.cryptoModule
import io.github.valerait.ghostlink.core.demo.DemoBackend
import io.github.valerait.ghostlink.core.demo.demoModule
import io.github.valerait.ghostlink.core.domain.domainModule
import io.github.valerait.ghostlink.core.domain.repository.AuthRepository
import io.github.valerait.ghostlink.core.domain.repository.ChatRepository
import io.github.valerait.ghostlink.core.domain.repository.DeviceRepository
import io.github.valerait.ghostlink.core.domain.repository.LinkRepository
import io.github.valerait.ghostlink.core.domain.repository.UserRepository
import io.github.valerait.ghostlink.core.domain.usecase.ApproveDeviceLinkUseCase
import io.github.valerait.ghostlink.core.domain.usecase.ObserveChatListUseCase
import io.github.valerait.ghostlink.core.domain.usecase.SignOutUseCase
import io.github.valerait.ghostlink.core.model.DeviceId
import io.github.valerait.ghostlink.core.model.Platform
import io.github.valerait.ghostlink.core.ui.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.verify.verify

/**
 * Koin проверяет граф только в рантайме, поэтому ошибку в модулях ловим здесь, а не при запуске (ADR 0005).
 * verify() проверяет, что у каждого конструктора в графе есть всё нужное, ничего не создавая.
 */
@OptIn(KoinExperimentalAPI::class)
class KoinModulesTest {
    // verify() смотрит на конструктор каждого типа в графе, даже если объект создаёт лямбда.
    // Context и SavedStateHandle даёт платформа (androidContext() и навигация),
    // остальное — значения, которые лямбды вычисляют сами: AppConfig(demoMode, versionName), androidThisDevice(context),
    // загрузка фото в AvatarPhotos(load) — suspend-лямбда с двумя параметрами, то есть Function3.
    private val extraTypes =
        listOf(Context::class, SavedStateHandle::class, Boolean::class, String::class, DeviceId::class, Platform::class, Function3::class)

    @After
    fun tearDown() = stopKoin()

    @Test
    fun `demo graph is complete`() {
        module { includes(appModules(AppConfig(demoMode = true))) }.verify(extraTypes)
    }

    @Test
    fun `firebase graph is complete`() {
        module { includes(appModules(AppConfig(demoMode = false))) }.verify(extraTypes)
    }

    @Test
    fun `demo backend serves every repository from one instance`() {
        val scope = CoroutineScope(SupervisorJob())
        val koin =
            startKoin {
                modules(module { single { scope } }, domainModule, cryptoModule, demoModule)
            }.koin

        val demo = koin.get<DemoBackend>()
        assertSame(demo, koin.get<AuthRepository>())
        assertSame(demo, koin.get<UserRepository>())
        assertSame(demo, koin.get<ChatRepository>())
        assertSame(demo, koin.get<DeviceRepository>())
        assertSame(demo, koin.get<LinkRepository>())
        // Use case собираются из того же графа.
        koin.get<SignOutUseCase>()
        koin.get<ObserveChatListUseCase>()
        koin.get<ApproveDeviceLinkUseCase>()
        scope.cancel()
    }
}
