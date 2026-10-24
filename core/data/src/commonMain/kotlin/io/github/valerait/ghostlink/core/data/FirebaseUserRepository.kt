package io.github.valerait.ghostlink.core.data

import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FieldPath
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import io.github.valerait.ghostlink.core.domain.repository.UserRepository
import io.github.valerait.ghostlink.core.firebase.Collections
import io.github.valerait.ghostlink.core.firebase.ProfileWrite
import io.github.valerait.ghostlink.core.firebase.UserDto
import io.github.valerait.ghostlink.core.model.User
import io.github.valerait.ghostlink.core.model.UserId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class FirebaseUserRepository(
    db: FirebaseFirestore,
    private val scope: CoroutineScope,
) : UserRepository {
    private val users = db.collection(Collections.USERS)

    override fun observeUser(id: UserId): Flow<User?> =
        users
            .document(id.value)
            .snapshots
            .map { it.toUser() }
            .logErrors("users/$id")

    override fun observeUsers(ids: List<UserId>): Flow<Map<UserId, User>> {
        if (ids.isEmpty()) return flowOf(emptyMap())
        // whereIn принимает не больше 30 значений.
        val parts =
            ids.chunked(WHERE_IN_LIMIT).map { chunk ->
                users
                    .where { FieldPath.documentId inArray chunk.map { it.value } }
                    .snapshots
                    .map { qs -> qs.documents.mapNotNull { it.toUser() } }
                    .logErrors("users in $chunk")
            }
        return combine(parts) { lists -> lists.flatMap { it }.associateBy { it.id } }
    }

    override suspend fun getUser(id: UserId): User? = firebaseCall { users.document(id.value).get().toUser() }

    override suspend fun saveProfile(
        id: UserId,
        name: String,
    ) = firebaseCall {
        users.document(id.value).set(ProfileWrite(name, online = true, lastSeen = Timestamp.ServerTimestamp), merge = true)
    }

    override fun setOnline(
        id: UserId,
        online: Boolean,
    ) {
        // update, а не set: до онбординга профиля ещё нет, и создавать его без имени нельзя.
        scope.launchWrite("Presence", "Presence update") {
            users.document(id.value).updateFields {
                "online" to online
                "lastSeen" to FieldValue.serverTimestamp
            }
        }
    }

    private companion object {
        const val WHERE_IN_LIMIT = 30
    }
}

private fun DocumentSnapshot.toUser(): User? = if (exists) data<UserDto>(ESTIMATE).toUser(id) else null
