package io.github.valerait.ghostlink.core.data

import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import io.github.valerait.ghostlink.core.domain.repository.DeviceRepository
import io.github.valerait.ghostlink.core.firebase.Collections
import io.github.valerait.ghostlink.core.firebase.DeviceDto
import io.github.valerait.ghostlink.core.firebase.DeviceWrite
import io.github.valerait.ghostlink.core.model.Device
import io.github.valerait.ghostlink.core.model.DeviceId
import io.github.valerait.ghostlink.core.model.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FirebaseDeviceRepository(
    private val db: FirebaseFirestore,
) : DeviceRepository {
    private fun devices(userId: UserId) = db.collection(Collections.USERS).document(userId.value).collection(Collections.DEVICES)

    override fun observeDevices(userId: UserId): Flow<List<Device>> =
        devices(userId)
            .snapshots
            .map { qs -> qs.documents.mapNotNull { it.toDevice() }.sortedByDescending { it.lastActiveAt } }
            .logErrors("devices of $userId")

    override fun observeDevice(
        userId: UserId,
        deviceId: DeviceId,
    ): Flow<Device?> =
        devices(userId)
            .document(deviceId.value)
            .snapshots
            .map { it.toDevice() }
            .logErrors("device $deviceId")

    override suspend fun registerDevice(
        userId: UserId,
        device: Device,
    ) = firebaseCall {
        devices(userId)
            .document(device.id.value)
            .set(DeviceWrite(device.name, device.platform.wireName, Timestamp.ServerTimestamp, device.pushToken), merge = true) {
                encodeDefaults = false
            }
    }

    override suspend fun removeDevice(
        userId: UserId,
        deviceId: DeviceId,
    ) = firebaseCall { devices(userId).document(deviceId.value).delete() }
}

private fun DocumentSnapshot.toDevice(): Device? = if (exists) data<DeviceDto>(ESTIMATE).toDevice(id) else null
