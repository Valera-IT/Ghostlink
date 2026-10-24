package io.github.valerait.ghostlink.di

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.valerait.ghostlink.BuildConfig
import io.github.valerait.ghostlink.core.data.FirebaseBackend
import io.github.valerait.ghostlink.core.demo.DemoBackend
import io.github.valerait.ghostlink.core.domain.repository.AuthRepository
import io.github.valerait.ghostlink.core.domain.repository.ChatRepository
import io.github.valerait.ghostlink.core.domain.repository.DeviceRepository
import io.github.valerait.ghostlink.core.domain.repository.LinkRepository
import io.github.valerait.ghostlink.core.domain.repository.UserRepository
import io.github.valerait.ghostlink.data.device.AndroidAccountKeyStore
import io.github.valerait.ghostlink.data.device.ThisDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton
import kotlin.time.Clock

data class AppConfig(
    /** Нет google-services.json — работаем на DemoBackend. */
    val demoMode: Boolean,
)

class Backend(
    val auth: AuthRepository,
    val users: UserRepository,
    val chats: ChatRepository,
    val devices: DeviceRepository,
    val links: LinkRepository,
)

@Module
@InstallIn(SingletonComponent::class)
object BackendModule {
    @Provides
    @Singleton
    fun provideAppConfig(
        @ApplicationContext context: Context,
    ): AppConfig {
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

    @Provides
    @Singleton
    fun provideBackend(
        @ApplicationContext context: Context,
        config: AppConfig,
    ): Backend {
        // Записи без ожидания (отправка, «прочитано», присутствие) живут дольше экрана — до конца процесса.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        return if (config.demoMode) {
            val demo = DemoBackend(scope)
            Backend(demo, demo, demo, demo, demo)
        } else {
            val firebase = FirebaseBackend(AndroidAccountKeyStore(context), scope, BuildConfig.FIREBASE_EMULATOR_HOST)
            Backend(firebase.auth, firebase.users, firebase.chats, firebase.devices, firebase.links)
        }
    }

    @Provides
    @Singleton
    fun provideThisDevice(
        @ApplicationContext context: Context,
    ): ThisDevice = ThisDevice.from(context)

    @Provides
    fun provideClock(): Clock = Clock.System

    @Provides
    fun provideAuthRepository(backend: Backend): AuthRepository = backend.auth

    @Provides
    fun provideUserRepository(backend: Backend): UserRepository = backend.users

    @Provides
    fun provideChatRepository(backend: Backend): ChatRepository = backend.chats

    @Provides
    fun provideDeviceRepository(backend: Backend): DeviceRepository = backend.devices

    @Provides
    fun provideLinkRepository(backend: Backend): LinkRepository = backend.links
}
