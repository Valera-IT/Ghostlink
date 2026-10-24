package io.github.valerait.ghostlink.feature.link

import androidx.lifecycle.SavedStateHandle
import io.github.valerait.ghostlink.core.domain.usecase.ApproveDeviceLinkUseCase
import io.github.valerait.ghostlink.core.domain.usecase.RequestDeviceLinkUseCase
import io.github.valerait.ghostlink.core.model.ThisDevice
import io.github.valerait.ghostlink.core.testing.FakeBackend
import io.github.valerait.ghostlink.core.testing.FakeLinkCipher
import kotlin.time.Clock

// ViewModel на FakeBackend с настоящими use case — так, как их собирает domainModule.

fun FakeBackend.linkReceiveViewModel(
    device: ThisDevice,
    clock: Clock = Clock.System,
) = LinkReceiveViewModel(RequestDeviceLinkUseCase(this, this, FakeLinkCipher, clock), device)

fun FakeBackend.linkApproveViewModel(
    code: String?,
    clock: Clock = Clock.System,
) = LinkApproveViewModel(SavedStateHandle(mapOf("code" to code)), ApproveDeviceLinkUseCase(this, this, FakeLinkCipher, clock))
