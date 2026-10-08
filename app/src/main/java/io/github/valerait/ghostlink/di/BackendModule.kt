package io.github.valerait.ghostlink.di

import android.annotation.SuppressLint
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.valerait.ghostlink.data.demo.InMemoryBackend
import io.github.valerait.ghostlink.data.firebase.FirebaseAuthRepository
import io.github.valerait.ghostlink.data.firebase.FirebaseChatRepository
import io.github.valerait.ghostlink.data.firebase.FirebaseUserRepository
import io.github.valerait.ghostlink.data.repository.AuthRepository
import io.github.valerait.ghostlink.data.repository.ChatRepository
import io.github.valerait.ghostlink.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

data class AppConfig(
    /** Нет google-services.json — работаем на InMemoryBackend. */
    val demoMode: Boolean,
    /** OAuth client id для входа через Google; null, если Google не настроен. */
    val webClientId: String?,
)

class Backend(val auth: AuthRepository, val users: UserRepository, val chats: ChatRepository)

@Module
@InstallIn(SingletonComponent::class)
object BackendModule {

    @SuppressLint("DiscouragedApi") // ресурс генерирует google-services, и его может не быть
    @Provides
    @Singleton
    fun provideAppConfig(@ApplicationContext context: Context): AppConfig {
        if (FirebaseApp.getApps(context).isEmpty()) return AppConfig(demoMode = true, webClientId = null)
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return AppConfig(demoMode = false, webClientId = if (resId != 0) context.getString(resId) else null)
    }

    @Provides
    @Singleton
    fun provideBackend(config: AppConfig): Backend =
        if (config.demoMode) {
            val demo = InMemoryBackend(CoroutineScope(SupervisorJob() + Dispatchers.Default))
            Backend(demo, demo, demo)
        } else {
            val db = FirebaseFirestore.getInstance()
            Backend(FirebaseAuthRepository(FirebaseAuth.getInstance()), FirebaseUserRepository(db), FirebaseChatRepository(db))
        }

    @Provides
    fun provideAuthRepository(backend: Backend): AuthRepository = backend.auth

    @Provides
    fun provideUserRepository(backend: Backend): UserRepository = backend.users

    @Provides
    fun provideChatRepository(backend: Backend): ChatRepository = backend.chats
}
