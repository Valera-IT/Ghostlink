package io.github.valerait.ghostlink.ui

import androidx.lifecycle.SavedStateHandle
import io.github.valerait.ghostlink.core.domain.usecase.ApproveDeviceLinkUseCase
import io.github.valerait.ghostlink.core.domain.usecase.CreateAccountUseCase
import io.github.valerait.ghostlink.core.domain.usecase.DeleteMessageUseCase
import io.github.valerait.ghostlink.core.domain.usecase.EditMessageUseCase
import io.github.valerait.ghostlink.core.domain.usecase.LeaveGroupUseCase
import io.github.valerait.ghostlink.core.domain.usecase.ObserveChatListUseCase
import io.github.valerait.ghostlink.core.domain.usecase.ObserveChatMessagesUseCase
import io.github.valerait.ghostlink.core.domain.usecase.RequestDeviceLinkUseCase
import io.github.valerait.ghostlink.core.domain.usecase.RestoreWithRecoveryCodeUseCase
import io.github.valerait.ghostlink.core.domain.usecase.SendMessageUseCase
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.data.demo.InMemoryBackend
import io.github.valerait.ghostlink.data.device.ThisDevice
import io.github.valerait.ghostlink.data.link.JvmLinkCipher
import io.github.valerait.ghostlink.di.AppConfig
import io.github.valerait.ghostlink.ui.chat.ChatViewModel
import io.github.valerait.ghostlink.ui.chats.ChatListViewModel
import io.github.valerait.ghostlink.ui.group.GroupInfoViewModel
import io.github.valerait.ghostlink.ui.link.LinkApproveViewModel
import io.github.valerait.ghostlink.ui.link.LinkReceiveViewModel
import io.github.valerait.ghostlink.ui.onboarding.OnboardingViewModel
import io.github.valerait.ghostlink.ui.recovery.RecoveryViewModel
import kotlin.time.Clock

// ViewModel на InMemoryBackend с настоящими use case — так, как их собирает UseCaseModule.

fun InMemoryBackend.chatViewModel(
    chatId: ChatId,
    clock: Clock = Clock.System,
) = ChatViewModel(
    SavedStateHandle(mapOf("chatId" to chatId.value)),
    this,
    this,
    ObserveChatMessagesUseCase(this, clock),
    this,
    SendMessageUseCase(this),
    EditMessageUseCase(this),
    DeleteMessageUseCase(this),
)

fun InMemoryBackend.chatListViewModel(clock: Clock = Clock.System) =
    ChatListViewModel(this, ObserveChatListUseCase(this, this, clock), this, LeaveGroupUseCase(this), AppConfig(demoMode = false))

fun InMemoryBackend.groupInfoViewModel(chatId: ChatId) =
    GroupInfoViewModel(SavedStateHandle(mapOf("chatId" to chatId.value)), this, this, this, LeaveGroupUseCase(this))

fun InMemoryBackend.linkReceiveViewModel(
    device: ThisDevice,
    clock: Clock = Clock.System,
) = LinkReceiveViewModel(RequestDeviceLinkUseCase(this, this, JvmLinkCipher, clock), device)

fun InMemoryBackend.linkApproveViewModel(
    code: String?,
    clock: Clock = Clock.System,
) = LinkApproveViewModel(SavedStateHandle(mapOf("code" to code)), ApproveDeviceLinkUseCase(this, this, JvmLinkCipher, clock))

fun InMemoryBackend.recoveryViewModel() = RecoveryViewModel(RestoreWithRecoveryCodeUseCase(this))

fun InMemoryBackend.onboardingViewModel(config: AppConfig) = OnboardingViewModel(CreateAccountUseCase(this, this), config)
