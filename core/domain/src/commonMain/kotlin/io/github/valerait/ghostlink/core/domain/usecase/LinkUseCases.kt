package io.github.valerait.ghostlink.core.domain.usecase

import io.github.valerait.ghostlink.core.domain.crypto.LinkCipher
import io.github.valerait.ghostlink.core.domain.repository.AuthRepository
import io.github.valerait.ghostlink.core.domain.repository.LinkRepository
import io.github.valerait.ghostlink.core.model.AccountKey
import io.github.valerait.ghostlink.core.model.GhostlinkError
import io.github.valerait.ghostlink.core.model.LINK_TTL
import io.github.valerait.ghostlink.core.model.LinkCode
import io.github.valerait.ghostlink.core.model.LinkRequest
import io.github.valerait.ghostlink.core.model.LinkRequestId
import io.github.valerait.ghostlink.core.model.Platform
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Instant

/** Как идёт вход на новом устройстве. */
sealed interface LinkState {
    /** QR с кодом входа; [expiresAt] — для обратного отсчёта. */
    data class ShowCode(
        val code: LinkCode,
        val expiresAt: Instant,
    ) : LinkState

    data object SigningIn : LinkState

    data object Expired : LinkState
}

/**
 * Новое устройство: временный вход → пара ключей → заявка → ждём, пока устройство с аккаунтом
 * положит в заявку зашифрованный ключ → входим этим ключом.
 * Заявка удаляется в любом случае: после входа, по истечении и при отмене сбора потока.
 */
class RequestDeviceLinkUseCase(
    private val auth: AuthRepository,
    private val links: LinkRepository,
    private val cipher: LinkCipher,
    private val clock: Clock,
) {
    operator fun invoke(
        deviceName: String,
        platform: Platform,
    ): Flow<LinkState> =
        flow {
            // Заявку нельзя создать без входа: временный анонимный аккаунт удалится после входа по ключу.
            if (auth.account.value == null) auth.signInAnonymously()
            val keyPair = cipher.newKeyPair()
            val id = links.createLinkRequest(deviceName, platform)
            var pending = true
            try {
                emit(LinkState.ShowCode(LinkCode(id, keyPair.publicKey), clock.now() + LINK_TTL))
                val payload = withTimeoutOrNull(LINK_TTL) { links.observeLinkRequest(id).mapNotNull { it?.payload }.first() }
                if (payload == null) {
                    emit(LinkState.Expired)
                    return@flow
                }
                emit(LinkState.SigningIn)
                val key = AccountKey(keyPair.decrypt(id, payload))
                // Удалить заявку может только её владелец — временный аккаунт, поэтому до входа по ключу.
                pending = false
                deleteQuietly(id)
                auth.signInWithKey(key)
            } finally {
                if (pending) deleteQuietly(id)
            }
        }

    private suspend fun deleteQuietly(id: LinkRequestId) {
        // Заявка истечёт и сама, так что ошибку удаления можно пропустить.
        withContext(NonCancellable) { runCatching { links.deleteLinkRequest(id) } }
    }
}

/** Устройство с аккаунтом подтверждает вход нового: шифрует ключ аккаунта публичным ключом из QR. */
class ApproveDeviceLinkUseCase(
    private val auth: AuthRepository,
    private val links: LinkRepository,
    private val cipher: LinkCipher,
    private val clock: Clock,
) {
    /** Заявка, которую ещё можно подтвердить; иначе [GhostlinkError.LinkExpired] или [GhostlinkError.LinkAlreadyApproved]. */
    suspend fun load(code: LinkCode): LinkRequest {
        val request =
            links
                .getLinkRequest(code.requestId)
                ?.takeUnless { it.payload == null && it.isExpired(clock.now()) }
                ?: throw GhostlinkError.LinkExpired()
        if (request.payload != null) throw GhostlinkError.LinkAlreadyApproved()
        return request
    }

    suspend operator fun invoke(code: LinkCode) {
        load(code)
        val key = auth.accountKey.value ?: auth.ensureAccountKey()
        links.approveLinkRequest(code.requestId, cipher.encrypt(code.publicKey, code.requestId, key.bytes))
    }
}
