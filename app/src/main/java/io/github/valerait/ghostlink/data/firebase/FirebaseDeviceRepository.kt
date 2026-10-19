package io.github.valerait.ghostlink.data.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import io.github.valerait.ghostlink.data.model.Device
import io.github.valerait.ghostlink.data.repository.DeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class FirebaseDeviceRepository(
    private val db: FirebaseFirestore,
) : DeviceRepository {
    private fun devices(userId: String) = db.collection("users").document(userId).collection("devices")

    override fun observeDevices(userId: String): Flow<List<Device>> =
        devices(userId).observe { qs -> qs.documents.mapNotNull { it.toDevice() }.sortedByDescending { it.lastActiveAt } }

    override fun observeDevice(
        userId: String,
        deviceId: String,
    ): Flow<Device?> = devices(userId).document(deviceId).observe { it.toDevice() }

    override suspend fun registerDevice(
        userId: String,
        device: Device,
    ) {
        devices(userId)
            .document(device.id)
            .set(
                mapOf("name" to device.name, "platform" to device.platform, "lastActiveAt" to FieldValue.serverTimestamp()),
                SetOptions.merge(),
            ).await()
    }

    override suspend fun removeDevice(
        userId: String,
        deviceId: String,
    ) {
        devices(userId).document(deviceId).delete().await()
    }
}

private fun DocumentSnapshot.toDevice(): Device? {
    if (!exists()) return null
    return Device(
        id = id,
        name = getString("name").orEmpty(),
        platform = getString("platform").orEmpty(),
        lastActiveAt = getTimestamp("lastActiveAt", ServerTimestampBehavior.ESTIMATE)?.millis,
    )
}
