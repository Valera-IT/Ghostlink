package io.github.valerait.ghostlink.core.data

import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FieldPath
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Source
import dev.gitlive.firebase.firestore.Timestamp
import io.github.valerait.ghostlink.core.domain.repository.UserRepository
import io.github.valerait.ghostlink.core.firebase.Collections
import io.github.valerait.ghostlink.core.firebase.ProfileWrite
import io.github.valerait.ghostlink.core.firebase.UserDto
import io.github.valerait.ghostlink.core.firebase.avatarData
import io.github.valerait.ghostlink.core.firebase.setAvatar
import io.github.valerait.ghostlink.core.model.User
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.core.platform.logWarning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class FirebaseUserRepository(
    db: FirebaseFirestore,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) : UserRepository {
    private val users = db.collection(Collections.USERS)
    private val avatars = db.collection(Collections.AVATARS)

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

    override suspend fun getAvatar(
        id: UserId,
        version: Long,
    ): ByteArray? {
        val ref = avatars.document(id.value)
        // Кэш Firestore на диске и есть кэш фото: с сервера качаем, только если там другая версия.
        val cached =
            try {
                ref.get(Source.CACHE)
            } catch (_: Exception) {
                null
            }
        val snapshot = cached?.takeIf { it.exists && it.version() == version } ?: firebaseCall { ref.get(Source.SERVER) }
        return if (snapshot.exists && snapshot.version() == version) snapshot.avatarData() else null
    }

    override suspend fun setAvatar(
        id: UserId,
        jpeg: ByteArray,
    ) {
        require(jpeg.isNotEmpty() && jpeg.size <= User.AVATAR_MAX_BYTES) { "Avatar is ${jpeg.size} bytes" }
        val version = clock.now().toEpochMilliseconds()
        // Сначала фото, потом версия в профиле: увидевший новую версию уже может его скачать.
        firebaseCall {
            avatars.document(id.value).setAvatar(jpeg, version)
            users.document(id.value).updateFields { "avatarVersion" to version }
        }
    }

    override suspend fun removeAvatar(id: UserId) =
        firebaseCall {
            users.document(id.value).updateFields { "avatarVersion" to FieldValue.delete }
            avatars.document(id.value).delete()
        }

    private companion object {
        const val WHERE_IN_LIMIT = 30
    }
}

private fun DocumentSnapshot.version(): Long? =
    runCatching { get<Long?>("version") }
        .onFailure { logWarning("Avatars", "Bad avatar version in ${reference.path}", it) }
        .getOrNull()

private fun DocumentSnapshot.toUser(): User? = if (exists) data<UserDto>(ESTIMATE).toUser(id) else null
