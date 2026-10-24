package io.github.valerait.ghostlink.core.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState

/** Приложение активно между UIApplicationDidBecomeActive и уходом в фон. Создавать на главном потоке. */
internal class IosAppLifecycle : AppLifecycle {
    private val _isForeground =
        MutableStateFlow(UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive)
    override val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    init {
        val center = NSNotificationCenter.defaultCenter
        val queue = NSOperationQueue.mainQueue
        center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, queue) { _ -> _isForeground.value = true }
        center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, queue) { _ -> _isForeground.value = false }
    }
}
