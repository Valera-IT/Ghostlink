package io.github.valerait.ghostlink

import android.app.Application
import io.github.valerait.ghostlink.core.data.DeviceRegistry
import io.github.valerait.ghostlink.core.data.PresenceTracker
import io.github.valerait.ghostlink.core.platform.PushTokens
import io.github.valerait.ghostlink.di.appModules
import io.github.valerait.ghostlink.di.detectAppConfig
import io.github.valerait.ghostlink.push.FcmPushTokens
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class GhostlinkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@GhostlinkApp)
            modules(appModules(detectAppConfig(this@GhostlinkApp)))
        }
        (get<PushTokens>() as? FcmPushTokens)?.start()
        get<PresenceTracker>().start()
        get<DeviceRegistry>().start()
    }
}
