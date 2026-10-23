package io.github.valerait.ghostlink.data.firebase

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import io.github.valerait.ghostlink.core.domain.repository.UserRepository
import io.github.valerait.ghostlink.core.model.User
import io.github.valerait.ghostlink.core.model.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

class FirebaseUserRepository(
    db: FirebaseFirestore,
) : UserRepository {
    private val users = db.collection("users")

    override fun observeUser(id: UserId): Flow<User?> = users.document(id.value).observe { it.toUser() }

    override fun observeUsers(ids: List<UserId>): Flow<Map<UserId, User>> {
        if (ids.isEmpty()) return flowOf(emptyMap())
        // whereIn принимает не больше 30 значений.
        val parts =
            ids.chunked(30).map { chunk ->
                users.whereIn(FieldPath.documentId(), chunk.map { it.value }).observe { qs -> qs.documents.mapNotNull { it.toUser() } }
            }
        return combine(parts) { lists -> lists.flatMap { it }.associateBy { it.id } }
    }

    override suspend fun getUser(id: UserId): User? =
        users
            .document(id.value)
            .get()
            .await()
            .toUser()

    override suspend fun saveProfile(
        id: UserId,
        name: String,
    ) {
        users
            .document(id.value)
            .set(
                mapOf("name" to name, "online" to true, "lastSeen" to FieldValue.serverTimestamp()),
                SetOptions.merge(),
            ).await()
    }

    override fun setOnline(
        id: UserId,
        online: Boolean,
    ) {
        // update, а не set: до онбординга профиля ещё нет, и создавать его без имени нельзя.
        users
            .document(id.value)
            .update(mapOf("online" to online, "lastSeen" to FieldValue.serverTimestamp()))
            .addOnFailureListener { Log.w("Presence", "Failed to update presence", it) }
    }
}

private fun DocumentSnapshot.toUser(): User? {
    if (!exists()) return null
    return User(
        id = UserId(id),
        name = getString("name").orEmpty(),
        online = getBoolean("online") ?: false,
        lastSeen = getTimestamp("lastSeen", ServerTimestampBehavior.ESTIMATE)?.instant,
    )
}
