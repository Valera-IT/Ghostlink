package io.github.valerait.ghostlink.core.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import io.github.valerait.ghostlink.core.model.GhostlinkError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

// Исключения GitLive на JVM — это классы firebase-java-sdk, поэтому их можно создать прямо в тесте.
class FirebaseErrorsTest {
    @Test
    fun networkAndUnavailableAreOffline() {
        assertIs<GhostlinkError.Offline>(FirebaseNetworkException("no network").toGhostlinkError())
        assertIs<GhostlinkError.Offline>(FirebaseFirestoreException("down", Code.UNAVAILABLE).toGhostlinkError())
        assertIs<GhostlinkError.Offline>(FirebaseFirestoreException("slow", Code.DEADLINE_EXCEEDED).toGhostlinkError())
    }

    @Test
    fun rulesRejectionIsPermissionDenied() {
        val denied = FirebaseFirestoreException("rules", Code.PERMISSION_DENIED)
        val error = assertIs<GhostlinkError.PermissionDenied>(denied.toGhostlinkError())
        assertSame(denied, error.cause)
        assertIs<GhostlinkError.PermissionDenied>(FirebaseFirestoreException("token", Code.UNAUTHENTICATED).toGhostlinkError())
    }

    @Test
    fun everythingElseIsUnexpected() {
        assertIs<GhostlinkError.Unexpected>(FirebaseFirestoreException("bug", Code.INTERNAL).toGhostlinkError())
        assertIs<GhostlinkError.Unexpected>(IllegalStateException("Not signed in").toGhostlinkError())
    }

    @Test
    fun ownErrorsAndCancellationPassThrough() {
        val expired = GhostlinkError.LinkExpired()
        assertSame(expired, expired.toGhostlinkError())
        val cancelled = CancellationException("left the screen")
        assertSame(cancelled, cancelled.toGhostlinkError())
    }

    @Test
    fun firebaseCallThrowsOnlyGhostlinkErrors() =
        runTest {
            assertFailsWith<GhostlinkError.PermissionDenied> {
                firebaseCall { throw FirebaseFirestoreException("rules", Code.PERMISSION_DENIED) }
            }
        }
}
