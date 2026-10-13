package io.github.valerait.ghostlink.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import io.github.valerait.ghostlink.data.model.LINK_TTL_MS
import io.github.valerait.ghostlink.data.model.LinkRequest
import io.github.valerait.ghostlink.data.repository.LinkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.Date

class FirebaseLinkRepository(
    db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : LinkRepository {

    private val requests = db.collection("linkRequests")

    override suspend fun createLinkRequest(deviceName: String, platform: String): String {
        val owner = checkNotNull(auth.currentUser) { "Not signed in" }.uid
        val doc = requests.document()
        // expiresAt считает клиент, но правила проверяют его по серверному времени.
        // На него же можно повесить TTL-политику Firestore, чтобы брошенные заявки удалялись сами.
        doc.set(
            mapOf(
                "owner" to owner,
                "deviceName" to deviceName.take(60),
                "platform" to platform,
                "createdAt" to FieldValue.serverTimestamp(),
                "expiresAt" to Timestamp(Date(System.currentTimeMillis() + LINK_TTL_MS)),
            ),
        ).await()
        return doc.id
    }

    override suspend fun getLinkRequest(id: String): LinkRequest? = requests.document(id).get().await().toLinkRequest()

    override fun observeLinkRequest(id: String): Flow<LinkRequest?> = requests.document(id).observe { it.toLinkRequest() }

    override suspend fun approveLinkRequest(id: String, payload: ByteArray) {
        val approver = checkNotNull(auth.currentUser) { "Not signed in" }.uid
        requests.document(id).update(mapOf("payload" to Blob.fromBytes(payload), "approvedBy" to approver)).await()
    }

    override suspend fun deleteLinkRequest(id: String) {
        requests.document(id).delete().await()
    }
}

private fun DocumentSnapshot.toLinkRequest(): LinkRequest? {
    if (!exists()) return null
    return LinkRequest(
        id = id,
        deviceName = getString("deviceName").orEmpty(),
        platform = getString("platform").orEmpty(),
        expiresAt = getTimestamp("expiresAt")?.millis ?: 0L,
        payload = getBlob("payload")?.toBytes(),
    )
}
