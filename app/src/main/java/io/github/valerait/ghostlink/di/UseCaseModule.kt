package io.github.valerait.ghostlink.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.valerait.ghostlink.core.domain.crypto.LinkCipher
import io.github.valerait.ghostlink.core.domain.repository.AuthRepository
import io.github.valerait.ghostlink.core.domain.repository.ChatRepository
import io.github.valerait.ghostlink.core.domain.repository.DeviceRepository
import io.github.valerait.ghostlink.core.domain.repository.LinkRepository
import io.github.valerait.ghostlink.core.domain.repository.UserRepository
import io.github.valerait.ghostlink.core.domain.usecase.AddFriendUseCase
import io.github.valerait.ghostlink.core.domain.usecase.ApproveDeviceLinkUseCase
import io.github.valerait.ghostlink.core.domain.usecase.CreateAccountUseCase
import io.github.valerait.ghostlink.core.domain.usecase.DeleteMessageUseCase
import io.github.valerait.ghostlink.core.domain.usecase.EditMessageUseCase
import io.github.valerait.ghostlink.core.domain.usecase.GetRecoveryCodeUseCase
import io.github.valerait.ghostlink.core.domain.usecase.LeaveGroupUseCase
import io.github.valerait.ghostlink.core.domain.usecase.ObserveChatListUseCase
import io.github.valerait.ghostlink.core.domain.usecase.ObserveChatMessagesUseCase
import io.github.valerait.ghostlink.core.domain.usecase.RenameProfileUseCase
import io.github.valerait.ghostlink.core.domain.usecase.RequestDeviceLinkUseCase
import io.github.valerait.ghostlink.core.domain.usecase.RestoreWithRecoveryCodeUseCase
import io.github.valerait.ghostlink.core.domain.usecase.SendMessageUseCase
import io.github.valerait.ghostlink.core.domain.usecase.SignOutEverywhereUseCase
import io.github.valerait.ghostlink.core.domain.usecase.SignOutUseCase
import io.github.valerait.ghostlink.data.link.JvmLinkCipher
import kotlin.time.Clock

/** Use case живут в KMP-модуле без javax.inject, поэтому собираются здесь (до перехода на Koin в 3.7). */
@Suppress("TooManyFunctions") // по функции на use case; модуль уйдёт вместе с Hilt в 3.7
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides
    fun provideLinkCipher(): LinkCipher = JvmLinkCipher

    @Provides
    fun provideCreateAccount(
        auth: AuthRepository,
        users: UserRepository,
    ) = CreateAccountUseCase(auth, users)

    @Provides
    fun provideRenameProfile(
        auth: AuthRepository,
        users: UserRepository,
    ) = RenameProfileUseCase(auth, users)

    @Provides
    fun provideRestoreWithRecoveryCode(auth: AuthRepository) = RestoreWithRecoveryCodeUseCase(auth)

    @Provides
    fun provideGetRecoveryCode(auth: AuthRepository) = GetRecoveryCodeUseCase(auth)

    @Provides
    fun provideSignOut(
        auth: AuthRepository,
        users: UserRepository,
        devices: DeviceRepository,
    ) = SignOutUseCase(auth, users, devices)

    @Provides
    fun provideSignOutEverywhere(
        auth: AuthRepository,
        devices: DeviceRepository,
    ) = SignOutEverywhereUseCase(auth, devices)

    @Provides
    fun provideRequestDeviceLink(
        auth: AuthRepository,
        links: LinkRepository,
        cipher: LinkCipher,
        clock: Clock,
    ) = RequestDeviceLinkUseCase(auth, links, cipher, clock)

    @Provides
    fun provideApproveDeviceLink(
        auth: AuthRepository,
        links: LinkRepository,
        cipher: LinkCipher,
        clock: Clock,
    ) = ApproveDeviceLinkUseCase(auth, links, cipher, clock)

    @Provides
    fun provideAddFriend(
        auth: AuthRepository,
        chats: ChatRepository,
    ) = AddFriendUseCase(auth, chats)

    @Provides
    fun provideObserveChatList(
        chats: ChatRepository,
        users: UserRepository,
        clock: Clock,
    ) = ObserveChatListUseCase(chats, users, clock)

    @Provides
    fun provideObserveChatMessages(
        chats: ChatRepository,
        clock: Clock,
    ) = ObserveChatMessagesUseCase(chats, clock)

    @Provides
    fun provideSendMessage(chats: ChatRepository) = SendMessageUseCase(chats)

    @Provides
    fun provideEditMessage(chats: ChatRepository) = EditMessageUseCase(chats)

    @Provides
    fun provideDeleteMessage(chats: ChatRepository) = DeleteMessageUseCase(chats)

    @Provides
    fun provideLeaveGroup(chats: ChatRepository) = LeaveGroupUseCase(chats)
}
