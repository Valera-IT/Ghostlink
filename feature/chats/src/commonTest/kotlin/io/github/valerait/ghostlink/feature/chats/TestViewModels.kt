package io.github.valerait.ghostlink.feature.chats

import io.github.valerait.ghostlink.core.domain.usecase.LeaveGroupUseCase
import io.github.valerait.ghostlink.core.domain.usecase.ObserveChatListUseCase
import io.github.valerait.ghostlink.core.testing.FakeBackend
import io.github.valerait.ghostlink.core.ui.AppConfig
import kotlin.time.Clock

// ViewModel на FakeBackend с настоящими use case — так, как их собирает domainModule.

fun FakeBackend.chatListViewModel(clock: Clock = Clock.System) =
    ChatListViewModel(this, ObserveChatListUseCase(this, this, clock), this, LeaveGroupUseCase(this), AppConfig(demoMode = false))
