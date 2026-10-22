package io.github.valerait.ghostlink.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import io.github.valerait.ghostlink.core.model.LINK_TTL
import io.github.valerait.ghostlink.core.model.LinkRequest
import io.github.valerait.ghostlink.core.model.LinkRequestId
import io.github.valerait.ghostlink.core.model.Platform
import io.github.valerait.ghostlink.data.repository.LinkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlin.time.Clock
import kotlin.time.Instant

class FirebaseLinkRepository(
    db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : LinkRepository {
    private val requests = db.collection("linkRequests")

    override suspend fun createLinkRequest(
        deviceName: String,
        platform: Platform,
    ): LinkRequestId {
        val owner = checkNotNull(auth.currentUser) { "Not signed in" }.uid
        val doc = requests.document()
        // expiresAt считает клиент, но правила проверяют его по серверному времени.
        // На него же можно повесить TTL-политику Firestore, чтобы брошенные заявки удалялись сами.
        doc
            .set(
                mapOf(
                    "owner" to owner,
                    "deviceName" to deviceName.take(60),
                    "platform" to platform.wireName,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "expiresAt" to (Clock.System.now() + LINK_TTL).toTimestamp(),
                ),
            ).await()
        return LinkRequestId(doc.id)
    }

    override suspend fun getLinkRequest(id: LinkRequestId): LinkRequest? =
        requests
            .document(id.value)
            .get()
            .await()
            .toLinkRequest()

    override fun observeLinkRequest(id: LinkRequestId): Flow<LinkRequest?> = requests.document(id.value).observe { it.toLinkRequest() }

    override suspend fun approveLinkRequest(
        id: LinkRequestId,
        payload: ByteArray,
    ) {
        val approver = checkNotNull(auth.currentUser) { "Not signed in" }.uid
        requests.document(id.value).update(mapOf("payload" to Blob.fromBytes(payload), "approvedBy" to approver)).await()
    }

    override suspend fun deleteLinkRequest(id: LinkRequestId) {
        requests.document(id.value).delete().await()
    }
}

private fun DocumentSnapshot.toLinkRequest(): LinkRequest? {
    if (!exists()) return null
    return LinkRequest(
        id = LinkRequestId(id),
        deviceName = getString("deviceName").orEmpty(),
        platform = platformOf(getString("platform")),
        expiresAt = getTimestamp("expiresAt")?.instant ?: Instant.DISTANT_PAST,
        payload = getBlob("payload")?.toBytes(),
    )
}
