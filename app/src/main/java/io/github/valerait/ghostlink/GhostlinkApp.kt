package io.github.valerait.ghostlink

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GhostlinkApp : Application() {
    @Inject lateinit var presence: PresenceTracker

    @Inject lateinit var deviceRegistry: DeviceRegistry

    override fun onCreate() {
        super.onCreate()
        presence.start()
        deviceRegistry.start()
    }
}
