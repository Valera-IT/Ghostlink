package io.github.valerait.ghostlink.core.data

import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import io.github.valerait.ghostlink.core.domain.repository.LinkRepository
import io.github.valerait.ghostlink.core.firebase.Collections
import io.github.valerait.ghostlink.core.firebase.LinkRequestDto
import io.github.valerait.ghostlink.core.firebase.LinkRequestWrite
import io.github.valerait.ghostlink.core.firebase.approveLinkRequest
import io.github.valerait.ghostlink.core.firebase.linkPayload
import io.github.valerait.ghostlink.core.model.LINK_TTL
import io.github.valerait.ghostlink.core.model.LinkRequest
import io.github.valerait.ghostlink.core.model.LinkRequestId
import io.github.valerait.ghostlink.core.model.Platform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class FirebaseLinkRepository(
    db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val clock: Clock = Clock.System,
) : LinkRepository {
    private val requests = db.collection(Collections.LINK_REQUESTS)

    override suspend fun createLinkRequest(
        deviceName: String,
        platform: Platform,
    ): LinkRequestId =
        firebaseCall {
            val owner = checkNotNull(auth.currentUser) { "Not signed in" }.uid
            val doc = requests.document
            // expiresAt считает клиент, но правила проверяют его по серверному времени.
            // На него же можно повесить TTL-политику Firestore, чтобы брошенные заявки удалялись сами.
            doc.set(
                LinkRequestWrite(
                    owner = owner,
                    deviceName = deviceName.take(DEVICE_NAME_LIMIT),
                    platform = platform.wireName,
                    createdAt = Timestamp.ServerTimestamp,
                    expiresAt = (clock.now() + LINK_TTL).toTimestamp(),
                ),
            )
            LinkRequestId(doc.id)
        }

    override suspend fun getLinkRequest(id: LinkRequestId): LinkRequest? =
        firebaseCall { requests.document(id.value).get().toLinkRequest() }

    override fun observeLinkRequest(id: LinkRequestId): Flow<LinkRequest?> =
        requests
            .document(id.value)
            .snapshots
            .map { it.toLinkRequest() }
            .logErrors("linkRequests/$id")

    override suspend fun approveLinkRequest(
        id: LinkRequestId,
        payload: ByteArray,
    ) = firebaseCall {
        val approver = checkNotNull(auth.currentUser) { "Not signed in" }.uid
        requests.document(id.value).approveLinkRequest(payload, approver)
    }

    override suspend fun deleteLinkRequest(id: LinkRequestId) = firebaseCall { requests.document(id.value).delete() }

    private companion object {
        // Столько же разрешают правила.
        const val DEVICE_NAME_LIMIT = 60
    }
}

private fun DocumentSnapshot.toLinkRequest(): LinkRequest? = if (exists) data<LinkRequestDto>().toLinkRequest(id, linkPayload()) else null
