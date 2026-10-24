package io.github.valerait.ghostlink.core.demo

import io.github.valerait.ghostlink.core.domain.repository.AuthRepository
import io.github.valerait.ghostlink.core.domain.repository.ChatRepository
import io.github.valerait.ghostlink.core.domain.repository.DeviceRepository
import io.github.valerait.ghostlink.core.domain.repository.LinkRepository
import io.github.valerait.ghostlink.core.domain.repository.UserRepository
import org.koin.dsl.binds
import org.koin.dsl.module

/** Все репозитории — один [DemoBackend]. Нужен CoroutineScope процесса: в нём отвечает бот. */
val demoModule =
    module {
        single { DemoBackend(scope = get(), clock = get()) } binds
            arrayOf(
                AuthRepository::class,
                UserRepository::class,
                ChatRepository::class,
                DeviceRepository::class,
                LinkRepository::class,
            )
    }
