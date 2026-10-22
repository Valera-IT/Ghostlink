package io.github.valerait.ghostlink.data.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import io.github.valerait.ghostlink.core.model.Device
import io.github.valerait.ghostlink.core.model.DeviceId
import io.github.valerait.ghostlink.core.model.UserId
import io.github.valerait.ghostlink.data.repository.DeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class FirebaseDeviceRepository(
    private val db: FirebaseFirestore,
) : DeviceRepository {
    private fun devices(userId: UserId) = db.collection("users").document(userId.value).collection("devices")

    override fun observeDevices(userId: UserId): Flow<List<Device>> =
        devices(userId).observe { qs -> qs.documents.mapNotNull { it.toDevice() }.sortedByDescending { it.lastActiveAt } }

    override fun observeDevice(
        userId: UserId,
        deviceId: DeviceId,
    ): Flow<Device?> = devices(userId).document(deviceId.value).observe { it.toDevice() }

    override suspend fun registerDevice(
        userId: UserId,
        device: Device,
    ) {
        devices(userId)
            .document(device.id.value)
            .set(
                mapOf("name" to device.name, "platform" to device.platform.wireName, "lastActiveAt" to FieldValue.serverTimestamp()),
                SetOptions.merge(),
            ).await()
    }

    override suspend fun removeDevice(
        userId: UserId,
        deviceId: DeviceId,
    ) {
        devices(userId).document(deviceId.value).delete().await()
    }
}

private fun DocumentSnapshot.toDevice(): Device? {
    if (!exists()) return null
    return Device(
        id = DeviceId(id),
        name = getString("name").orEmpty(),
        platform = platformOf(getString("platform")),
        lastActiveAt = getTimestamp("lastActiveAt", ServerTimestampBehavior.ESTIMATE)?.instant,
    )
}
