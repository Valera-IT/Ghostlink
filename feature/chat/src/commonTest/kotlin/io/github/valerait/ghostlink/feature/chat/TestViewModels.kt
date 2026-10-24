package io.github.valerait.ghostlink.feature.chat

import androidx.lifecycle.SavedStateHandle
import io.github.valerait.ghostlink.core.domain.usecase.DeleteMessageUseCase
import io.github.valerait.ghostlink.core.domain.usecase.EditMessageUseCase
import io.github.valerait.ghostlink.core.domain.usecase.LeaveGroupUseCase
import io.github.valerait.ghostlink.core.domain.usecase.ObserveChatMessagesUseCase
import io.github.valerait.ghostlink.core.domain.usecase.SendMessageUseCase
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.testing.FakeBackend
import kotlin.time.Clock

// ViewModel на FakeBackend с настоящими use case — так, как их собирает domainModule.

fun FakeBackend.chatViewModel(
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

fun FakeBackend.groupInfoViewModel(chatId: ChatId) =
    GroupInfoViewModel(SavedStateHandle(mapOf("chatId" to chatId.value)), this, this, this, LeaveGroupUseCase(this))
