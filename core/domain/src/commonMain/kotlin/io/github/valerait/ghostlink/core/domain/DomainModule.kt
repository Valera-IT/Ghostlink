package io.github.valerait.ghostlink.core.domain

import io.github.valerait.ghostlink.core.domain.usecase.AddFriendUseCase
import io.github.valerait.ghostlink.core.domain.usecase.ApproveDeviceLinkUseCase
import io.github.valerait.ghostlink.core.domain.usecase.CreateAccountUseCase
import io.github.valerait.ghostlink.core.domain.usecase.DeleteMessageUseCase
import io.github.valerait.ghostlink.core.domain.usecase.EditMessageUseCase
import io.github.valerait.ghostlink.core.domain.usecase.GetRecoveryCodeUseCase
import io.github.valerait.ghostlink.core.domain.usecase.LeaveGroupUseCase
import io.github.valerait.ghostlink.core.domain.usecase.ObserveChatListUseCase
import io.github.valerait.ghostlink.core.domain.usecase.ObserveChatMessagesUseCase
import io.github.valerait.ghostlink.core.domain.usecase.PhotoSender
import io.github.valerait.ghostlink.core.domain.usecase.RenameProfileUseCase
import io.github.valerait.ghostlink.core.domain.usecase.RequestDeviceLinkUseCase
import io.github.valerait.ghostlink.core.domain.usecase.RestoreWithRecoveryCodeUseCase
import io.github.valerait.ghostlink.core.domain.usecase.SendMessageUseCase
import io.github.valerait.ghostlink.core.domain.usecase.SignOutEverywhereUseCase
import io.github.valerait.ghostlink.core.domain.usecase.SignOutUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * Use case и часы. Репозитории, MediaStore и [io.github.valerait.ghostlink.core.domain.crypto.LinkCipher] даёт бэкенд
 * и core:crypto, CoroutineScope процесса — приложение.
 */
val domainModule =
    module {
        single<Clock> { Clock.System }

        factoryOf(::CreateAccountUseCase)
        factoryOf(::RenameProfileUseCase)
        factoryOf(::RestoreWithRecoveryCodeUseCase)
        factoryOf(::GetRecoveryCodeUseCase)
        factoryOf(::SignOutUseCase)
        factoryOf(::SignOutEverywhereUseCase)
        factoryOf(::RequestDeviceLinkUseCase)
        factoryOf(::ApproveDeviceLinkUseCase)
        factoryOf(::AddFriendUseCase)
        factoryOf(::ObserveChatListUseCase)
        factoryOf(::ObserveChatMessagesUseCase)
        factoryOf(::SendMessageUseCase)
        factoryOf(::EditMessageUseCase)
        factoryOf(::DeleteMessageUseCase)
        factoryOf(::LeaveGroupUseCase)
        // Одна очередь неотправленных фото на процесс: чат видит свои, даже если его открыли заново.
        singleOf(::PhotoSender)
    }
