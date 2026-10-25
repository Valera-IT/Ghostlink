package io.github.valerait.ghostlink.core.platform

import java.io.File

/** Папка кэша по обычаям ОС: Library/Caches на macOS, %LOCALAPPDATA% на Windows, ~/.cache на Linux. */
internal fun jvmCacheDirectory(): String {
    val home = System.getProperty("user.home")
    val os = System.getProperty("os.name").lowercase()
    val root =
        when {
            "mac" in os -> File(home, "Library/Caches")
            "win" in os -> File(System.getenv("LOCALAPPDATA") ?: home)
            else -> File(System.getenv("XDG_CACHE_HOME") ?: "$home/.cache")
        }
    return File(root, "Ghostlink").absolutePath
}
