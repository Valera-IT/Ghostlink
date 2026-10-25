package io.github.valerait.ghostlink.core.platform

import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/** Library/Caches приложения: не попадает в резервную копию, система чистит её сама. */
internal fun iosCachesDirectory(): String = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String
