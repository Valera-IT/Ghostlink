package io.github.valerait.ghostlink.di

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.valerait.ghostlink.BuildConfig
import io.github.valerait.ghostlink.data.demo.InMemoryBackend
import io.github.valerait.ghostlink.data.device.AndroidAccountKeyStore
import io.github.valerait.ghostlink.data.device.ThisDevice
import io.github.valerait.ghostlink.data.firebase.FirebaseAuthRepository
import io.github.valerait.ghostlink.data.firebase.FirebaseChatRepository
import io.github.valerait.ghostlink.data.firebase.FirebaseDeviceRepository
import io.github.valerait.ghostlink.data.firebase.FirebaseLinkRepository
import io.github.valerait.ghostlink.data.firebase.FirebaseUserRepository
import io.github.valerait.ghostlink.data.model.Clock
import io.github.valerait.ghostlink.data.repository.AuthRepository
import io.github.valerait.ghostlink.data.repository.ChatRepository
import io.github.valerait.ghostlink.data.repository.DeviceRepository
import io.github.valerait.ghostlink.data.repository.LinkRepository
import io.github.valerait.ghostlink.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

data class AppConfig(
    /** Нет google-services.json — работаем на InMemoryBackend. */
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
        BuildConfig.FIREBASE_EMULATOR_HOST?.let { host ->
            connectToEmulator(context, host)
            return AppConfig(demoMode = false)
        }
        return AppConfig(demoMode = FirebaseApp.getApps(context).isEmpty())
    }

    /** Локальный Firebase Emulator: проект demo-* не требует ни google-services.json, ни настоящего проекта. */
    private fun connectToEmulator(
        context: Context,
        host: String,
    ) {
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
        FirebaseAuth.getInstance().useEmulator(host, 9099)
        FirebaseFirestore.getInstance().useEmulator(host, 8080)
    }

    @Provides
    @Singleton
    fun provideBackend(
        @ApplicationContext context: Context,
        config: AppConfig,
    ): Backend =
        if (config.demoMode) {
            val demo = InMemoryBackend(CoroutineScope(SupervisorJob() + Dispatchers.Default))
            Backend(demo, demo, demo, demo, demo)
        } else {
            val db = FirebaseFirestore.getInstance()
            val auth = FirebaseAuth.getInstance()
            Backend(
                auth = FirebaseAuthRepository(auth, AndroidAccountKeyStore(context)),
                users = FirebaseUserRepository(db),
                chats = FirebaseChatRepository(db),
                devices = FirebaseDeviceRepository(db),
                links = FirebaseLinkRepository(db, auth),
            )
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
