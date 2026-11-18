package io.github.valerait.ghostlink.core.data

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FirestoreFlowsTest {
    @Test
    fun chatListenerResubscribesAfterTemporaryFailure() =
        runTest {
            var subscriptions = 0
            val history =
                flow {
                    if (subscriptions++ == 0) {
                        emit(listOf("old"))
                        throw FirebaseFirestoreException("offline", Code.UNAVAILABLE)
                    }
                    emit(listOf("old", "new"))
                }.retryChatListener("history").toList()

            assertEquals(2, subscriptions)
            assertEquals(listOf(listOf("old"), listOf("old", "new")), history)
        }

    @Test
    fun chatListenerDoesNotRetryPermissionDenied() =
        runTest {
            var subscriptions = 0
            val history =
                flow<List<String>> {
                    subscriptions++
                    throw FirebaseFirestoreException("denied", Code.PERMISSION_DENIED)
                }.retryChatListener("history").toList()

            assertEquals(1, subscriptions)
            assertEquals(emptyList(), history)
        }
}
